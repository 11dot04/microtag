package com.microtag.inspect

import org.json.JSONObject

// ==========================================================
// ENTITY: names, places, things. A short Wikipedia summary.
// Only after the dictionary has had its say, and only for selected text.
// ==========================================================

internal object EntityDetector {
    // No length cap: "Fear and Loathing in Las Vegas" is a perfectly good title. Sentences are filtered by shape instead.
    private val shape = Regex("""^[\p{L}\p{N}][\p{L}\p{N} .,'’&:–—!?()/-]+$""")
    private val small = setOf("and", "of", "in", "the", "a", "an", "for", "on", "to", "at", "de", "la", "le", "von", "van", "der", "du", "del", "vs", "with", "from", "by", "or", "as")

    fun candidates(text: String, source: Source): List<Candidate> {
        if (source != Source.SELECTION) return emptyList()
        val t = text.trim().replace(Regex("\\s+"), " ")
        if (t.length < 3 || !shape.matches(t)) return emptyList()
        val words = t.split(' ')
        // Reads like a title, not a sentence: most of the meaningful words are capitalised.
        if (words.size > 1) {
            val meaningful = words.filter { it.lowercase() !in small }
            if (meaningful.isEmpty() || meaningful.count { it[0].isUpperCase() || it[0].isDigit() } * 2 < meaningful.size) return emptyList()
            if (words.size > 3 && t.endsWith(".")) return emptyList()
        }
        // A lowercase phrase is ordinary speech, not a name. A single lowercase word may still be a proper noun.
        if (words.size > 1 && t == t.lowercase()) return emptyList()
        return listOf(Candidate("entity", 0.3, slow = true) { lookup(t) })
    }

    internal class Summary(val type: String, val title: String, val description: String, val extract: String, val url: String, val image: String = "", val lat: Double = Double.NaN, val lon: Double = Double.NaN)

    internal fun parse(body: String): Summary? = try {
        val o = JSONObject(body)
        val title = o.optString("title", "")
        if (title.isBlank()) null else Summary(
            o.optString("type", "standard"),
            title,
            o.optString("description", ""),
            o.optString("extract", ""),
            o.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page", "") ?: "",
            o.optJSONObject("thumbnail")?.optString("source", "") ?: "",
            o.optJSONObject("coordinates")?.optDouble("lat", Double.NaN) ?: Double.NaN,
            o.optJSONObject("coordinates")?.optDouble("lon", Double.NaN) ?: Double.NaN
        )
    } catch (_: Exception) {
        null
    }

    private fun lookup(text: String): InspectResult? {
        val title = Http.encode(text.replace(' ', '_'))
        val r = Http.get("https://en.wikipedia.org/api/rest_v1/page/summary/$title", 3500)
        if (!r.ok) return null
        val s = parse(r.body) ?: return null
        return build(s)
    }

    /** Whole words only, at least the first one: "Albert Einstein" gives "Albert". */
    internal fun fitWords(text: String, max: Int): String {
        val words = text.split(' ').filter { it.isNotEmpty() }
        val kept = ArrayList<String>()
        for (w in words) {
            if (kept.isNotEmpty() && (kept + w).joinToString(" ").length > max) break
            kept.add(w)
        }
        return kept.joinToString(" ")
    }

    internal fun build(s: Summary): InspectResult {
        val link = if (s.url.startsWith("https://")) s.url else ""
        if (s.type == "disambiguation") {
            val lines = s.extract.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            return InspectResult(
                domain = "WIKI", pill = "Many matches", title = s.title, subtitle = "Disambiguation",
                cardTitle = s.title, cardText = "This name can mean several things.",
                body = lines.firstOrNull().orEmpty().take(300), parts = lines.drop(1).take(12),
                copyText = s.title, openUrl = link,
                status = Status.WARN, timeoutSeconds = 20
            )
        }
        // The first two sentences are plenty for a card.
        val sentences = s.extract.split(Regex("(?<=[.!?])\\s+(?=[A-Z])"))
        val body = sentences.take(3).joinToString(" ").take(560)
        return InspectResult(
            domain = "WIKI",
            pill = WikiPill.pill(PillConfig.WIKI, s.title, s.description, ::fitWords),
            title = s.title,
            subtitle = s.description,
            cardTitle = s.title,
            cardText = s.description.ifBlank { body.take(80) },
            body = body,
            copyText = s.title,
            openUrl = link,
            imageUrl = s.image,
            // Places carry coordinates; the card shows them where a person would have their years.
            fact = if (!s.lat.isNaN() && !s.lon.isNaN())
                String.format(java.util.Locale.ROOT, "%.2f° %s|%.2f° %s", Math.abs(s.lat), if (s.lat >= 0) "N" else "S", Math.abs(s.lon), if (s.lon >= 0) "E" else "W")
            else "",
            timeoutSeconds = 25
        )
    }
}
