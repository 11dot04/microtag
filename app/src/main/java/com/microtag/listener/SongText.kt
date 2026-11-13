package com.microtag.listener

/**
 * Makes song titles as short as they can honestly be, with plain rules and no AI: a new song every few minutes
 * would mean a request every few minutes. Strips what is only a version label or credit, and when a title is
 * still too long for the chip, keeps the part before a subtitle.
 *
 *   "Tum Hi Ho (From "Aashiqui 2")"      -> "Tum Hi Ho"
 *   "Blinding Lights - Remastered 2020"  -> "Blinding Lights"
 *   "Song ft. Someone Else"              -> "Song"
 *   "03. Song Name - Radio Edit"         -> "Song Name"
 *   "Artist - Song Name"  (artist known) -> "Song Name"
 *   "Song Name: The Subtitle Of It"      -> "Song Name"   (only when it has to be shorter)
 */
object SongText {
    private val brackets = Regex("""\s*[\(\[][^)\]]*[\)\]]""")
    private val leadingNumber = Regex("""^\s*\d{1,3}\s*[.\-–)]\s+""")
    private val featInline = Regex("""(?i)\s+(?:feat\.?|ft\.?|featuring|prod\.?(?:\s+by)?)\s+.+$""")
    private val officialTail = Regex("""(?i)\s*(?:official\s+)?(?:music\s+)?(?:video|audio|lyric\s+video|lyrics|visuali[sz]er|hd|4k)$""")
    private val dashTail = Regex(
        """(?i)\s*[-–—]\s*(?:""" +
            """(?:\d{4}\s*)?re-?master(?:ed)?(?:\s*(?:version|edit))?(?:\s*\d{4})?""" +
            """|live(?:\s+(?:at|from|in)\b.*)?""" +
            """|(?:single|album|radio|extended|original|explicit|clean|mono|stereo|short|club|acoustic)(?:\s+(?:version|edit|mix))?""" +
            """|(?:\w+\s+)?(?:remix|edit|mix|version)""" +
            """|from\s+.+|original motion picture.*|official.*|lyrics?.*""" +
            """|(?:feat\.?|ft\.?|featuring)\s+.+""" +
            """)$"""
    )
    private val subtitle = Regex("""\s*[:|/]\s*|\s+[-–—]\s+""")

    fun clean(raw: String, artist: String = ""): String {
        var s = raw.trim().trim('"', '“', '”')
        s = brackets.replace(s, "").trim()
        s = leadingNumber.replace(s, "")
        // "Artist - Song" and "Song - Artist" when the artist is known.
        val a = artist.trim()
        if (a.length >= 2) {
            s = Regex("""(?i)^${Regex.escape(a)}\s*[-–—:]\s*""").replace(s, "")
            s = Regex("""(?i)\s*[-–—]\s*${Regex.escape(a)}$""").replace(s, "")
        }
        // Version labels and credits can stack ("- Remastered 2011 - Radio Edit"): peel them off a few times.
        repeat(3) {
            val before = s
            s = dashTail.replace(s, "").trim()
            s = featInline.replace(s, "").trim()
            s = officialTail.replace(s, "").trim()
            if (s == before) return@repeat
        }
        s = s.trim().trimEnd('-', '–', '—', ':', ',', '.', ' ')
        return s.replace(Regex("""\s+"""), " ").ifBlank { raw.trim() }
    }

    /** The song as it goes on the chip: cleaned, then shortened at a subtitle or a whole word if it is still too long. */
    fun pill(raw: String, artist: String = "", limit: Int = 14): String {
        val s = clean(raw, artist)
        if (s.length <= limit) return s
        val head = s.split(subtitle).firstOrNull()?.trim().orEmpty()
        val shorter = if (head.length >= 3) head else s
        if (shorter.length <= limit) return shorter
        // "The Night We Met" is no worse as "Night We Met" when space is tight.
        val noArticle = shorter.replace(Regex("""(?i)^(?:the|a|an)\s+(?=\S)"""), "")
        return PillText.cut(noArticle, limit)
    }
}
