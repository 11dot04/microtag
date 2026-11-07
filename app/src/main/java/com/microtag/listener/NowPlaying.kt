package com.microtag.listener

/**
 * Songs recognised by the phone itself (Ambient Music Mod, a Pixel's Now Playing, Sound Search, Shazam).
 * Pure Kotlin, tested on the JVM. These apps do not agree on a layout, so every common one is read:
 *   "Now Playing: Song by Artist"   "Song by Artist"   title "Now Playing" + text "Song by Artist"
 *   title "Song" + text "Artist"    "Song - Artist"    "Song · Artist" / "— " / "| " / "• "
 * The earlier rule only handled "Now Playing <song>" in the title, so anything else produced nothing.
 */
object NowPlaying {

    data class Track(val song: String, val artist: String) {
        val line: String get() = if (artist.isBlank()) song else "$song · $artist"
    }

    private val sourcePackages = listOf("ambientmusic", "intelligence.sense", "soundsearch", "shazam", "musicrecognition")
    private val googleAs = Regex("""^com\.google\.android\.as(\.|$)""")
    private val channelHint = Regex("""(?i)now[ _-]?playing|ambient|sound[ _-]?search|music[ _-]?recogni|song[ _-]?id""")

    /** Is this notification from something that recognises music? System Intelligence also posts other things, so it needs the channel. */
    fun isSource(pkg: String, channelId: String?, title: String = ""): Boolean {
        val p = pkg.lowercase()
        if (sourcePackages.any { it in p }) return true
        if (channelId != null && channelHint.containsMatchIn(channelId)) return true
        if (googleAs.containsMatchIn(p)) return markerStart.containsMatchIn(title)
        return false
    }

    // "Now Playing", "Now playing:", "Playing", "Recognised", "Identified", "Heard", with or without a note icon.
    private val marker = """(?:[♪♫🎵🎶]\s*)?(?:now\s+playing|playing|recogni[sz]ed|identified|heard|listening to|song detected)"""
    private val markerStart = Regex("""(?i)^\s*$marker\b""")
    private val markerStrip = Regex("""(?i)^\s*$marker\s*[:\-–—]?\s*""")
    private val bareNote = Regex("""^\s*[♪♫🎵🎶]\s*""")

    // Statuses and housekeeping from these apps that are not songs.
    private val notASong = Regex(
        """(?i)running in the background|ambient music mod\b|searching|listening\.\.\.|listening…|no match|couldn'?t (?:recogni|identify|find)|""" +
            """not recogni[sz]ed|tap to|tap here|turn on|enable|permission|sound search is|microphone|battery|update available|history|favou?rites"""
    )

    fun parse(title: String, text: String, subText: String = ""): Track? {
        val t = title.trim(); val x = text.trim(); val s = subText.trim()
        if (t.isEmpty() && x.isEmpty()) return null
        if (notASong.containsMatchIn("$t $x")) return null

        val titleIsMarker = markerStart.containsMatchIn(t) && markerStrip.replace(t, "").isBlank()
        val body = when {
            titleIsMarker -> x
            markerStart.containsMatchIn(t) -> markerStrip.replace(t, "")
            else -> bareNote.replace(t, "")
        }.trim()
        if (body.isEmpty()) return null

        // 1. A separate artist line: the title is the song, whatever dashes it has ("Song - Remastered 2011").
        val separate = !titleIsMarker && x.isNotEmpty() && !markerStart.containsMatchIn(x)
        // 2. Otherwise the body carries both: "Song by Artist", "Song - Artist", "Song · Artist".
        if (!separate) splitBoth(body)?.let { return tidy(it.first, it.second) }

        // 3. Title is the song and the text is the artist.
        val other = when {
            titleIsMarker -> s
            else -> x.takeIf { it.isNotEmpty() && !markerStart.containsMatchIn(it) } ?: s
        }
        val artist = other.removePrefix("by ").removePrefix("By ").trim()
        return tidy(body, if (artist.equals(body, ignoreCase = true)) "" else artist)
    }

    private val byWord = Regex("""(?i)\s+by\s+""")
    private val seps = listOf(" · ", " • ", " — ", " – ", " | ", " - ")

    private fun splitBoth(body: String): Pair<String, String>? {
        // The last " by " splits "Stand by Me by Ben E. King" correctly.
        val m = byWord.findAll(body).lastOrNull()
        if (m != null && m.range.first > 0 && m.range.last < body.length - 1) {
            return body.substring(0, m.range.first) to body.substring(m.range.last + 1)
        }
        for (sep in seps) {
            val i = body.indexOf(sep)
            if (i > 0) return body.substring(0, i) to body.substring(i + sep.length)
        }
        return null
    }

    private fun tidy(song: String, artist: String): Track? {
        val s = cleanSong(song)
        if (s.isBlank()) return null
        return Track(s, artist.trim().trim('"', '\'', '“', '”'))
    }

    private val brackets = Regex("""\s*[\(\[][^)\]]*[\)\]]""")
    private val tail = Regex("""(?i)\s*[-–—]\s*(?:remaster(?:ed)?(?:\s*\d{4})?|\d{4}\s*remaster(?:ed)?|live|single version|radio edit|from\s+.+|original motion picture.*)$""")

    /** "Tum Hi Ho (From "Aashiqui 2")" -> "Tum Hi Ho"; "Song - Remastered 2011" -> "Song"; "(feat. X)" goes. */
    fun cleanSong(raw: String): String {
        var s = raw.trim().trim('"', '“', '”')
        s = brackets.replace(s, "").trim()
        s = tail.replace(s, "").trim()
        return s.ifBlank { raw.trim() }
    }

    /** The pill: the song, cut at a word if it is too long. */
    fun pill(track: Track, limit: Int = 14): String = PillText.cut(track.song, limit)
}
