package com.microtag.inspect

import org.json.JSONObject

// ==========================================================
// ENTITY: names, places, things. A short Wikipedia summary.
// Only after the dictionary has had its say, and only for selected text.
// ==========================================================

internal object EntityDetector {
    private val shape = Regex("""^[\p{L}][\p{L}\p{N} .,'’&-]{1,58}$""")

    fun candidates(text: String, source: Source): List<Candidate> {
        if (source != Source.SELECTION) return emptyList()
        val t = text.trim().replace(Regex("\\s+"), " ")
        if (t.length < 3 || !shape.matches(t)) return emptyList()
        val words = t.split(' ')
        if (words.size > 5) return emptyList()
        // A lowercase phrase is ordinary speech, not a name. A single lowercase word may still be a proper noun.
        if (words.size > 1 && t == t.lowercase()) return emptyList()
        return listOf(Candidate("entity", 0.3, slow = true) { lookup(t) })
    }

    internal class Summary(val type: String, val title: String, val description: String, val extract: String, val url: String)

    internal fun parse(body: String): Summary? = try {
        val o = JSONObject(body)
        val title = o.optString("title", "")
        if (title.isBlank()) null else Summary(
            o.optString("type", "standard"),
            title,
            o.optString("description", ""),
            o.optString("extract", ""),
            o.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page", "") ?: ""
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
            return InspectResult(
                domain = "WIKI", pill = "Many matches", title = s.title, subtitle = "Disambiguation",
                cardTitle = s.title, cardText = "This name can mean several things.",
                body = s.extract.take(300), copyText = s.title, openUrl = link,
                status = Status.WARN, timeoutSeconds = 20
            )
        }
        // The first two sentences are plenty for a card.
        val sentences = s.extract.split(Regex("(?<=[.!?])\\s+(?=[A-Z])"))
        val body = sentences.take(2).joinToString(" ").take(380)
        return InspectResult(
            domain = "WIKI",
            pill = fitWords(s.title, 14),
            title = s.title,
            subtitle = s.description,
            cardTitle = s.title,
            cardText = s.description.ifBlank { body.take(80) },
            body = body,
            copyText = s.title,
            openUrl = link,
            timeoutSeconds = 25
        )
    }
}
