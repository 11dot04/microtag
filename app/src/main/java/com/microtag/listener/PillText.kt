package com.microtag.listener

/**
 * Fitting words into a pill. The pill has room for a dozen characters or so, so text is condensed by meaning
 * (the figure that matters), not cut at an arbitrary character. Pure Kotlin, tested on the JVM. The AI condenser
 * (see AiCondenser) is the optional upgrade of [condense]; this is what runs with it off and while it is thinking.
 */
object PillText {
    const val LIMIT = 12

    /** Whole words that fit; a single word that is too long is cut with an ellipsis. */
    fun cut(text: String, limit: Int = LIMIT): String {
        val s = text.trim().replace(Regex("""\s+"""), " ")
        if (s.length <= limit) return s
        val words = s.split(' ')
        val out = StringBuilder()
        for (w in words) {
            val next = if (out.isEmpty()) w else "$out $w"
            if (next.length > limit) break
            out.clear(); out.append(next)
        }
        if (out.isNotEmpty()) return out.toString()
        return s.take((limit - 1).coerceAtLeast(1)).trimEnd() + "…"
    }

    // The figure that matters, most useful first.
    private val figures = listOf(
        Regex("""(?:₹|Rs\.?\s?|INR\s?|\$|€|£)\s?\d[\d,]*(?:\.\d+)?|\d[\d,]*(?:\.\d+)?\s?(?:rupees|INR|USD)\b"""),
        Regex("""(?i)\b\d{1,2}:\d{2}\s?(?:am|pm)?"""),
        Regex("""-?\d+(?:\.\d+)?\s?%"""),
        Regex("""(?i)\b\d+\s?(?:mins?|minutes?|hrs?|hours?|days?|secs?|seconds?)\b"""),
        Regex("""-?\d+\s?°[CFcf]?"""),
        Regex("""(?i)\b\d+\s+(?:new|unread|missed|messages?|items?|orders?|likes?|alerts?)\b""")
    )
    private val figureLabels = Regex("""(?i)\b(?:due|paid|left|new|unread|off|eta|credited|debited|received|sent|away|late|delay(?:ed)?)\b""")

    private val filler = setOf(
        "hi", "hey", "hello", "dear", "your", "you", "yours", "has", "have", "had", "been", "is", "are", "was", "were", "the", "a", "an",
        "just", "please", "kindly", "will", "would", "to", "of", "for", "on", "at", "in", "with", "and", "this", "that", "it", "its",
        "now", "so", "we", "our", "us", "get", "got", "can", "could", "may", "from"
    )

    /** The short form of a notification, at most [limit] characters. */
    fun condense(title: String, text: String, limit: Int = LIMIT): String {
        val body = "$title. $text".replace(Regex("""\s+"""), " ").trim()
        for (rx in figures) {
            val m = rx.find(body) ?: continue
            val fig = m.value.trim()
            if (fig.length > limit) return cut(fig, limit)
            // A label that sits next to it ("due", "left") makes it mean something, if it still fits.
            val label = figureLabels.find(body)?.value?.lowercase()
            if (label != null && !fig.lowercase().contains(label) && "$fig $label".length <= limit) return "$fig $label"
            return fig
        }
        val pick = significant(text).ifBlank { significant(title) }.ifBlank { text.ifBlank { title } }
        return cut(pick, limit)
    }

    private fun significant(s: String): String =
        s.split(Regex("""[\s,.;:!?]+""")).filter { it.isNotBlank() && it.lowercase() !in filler }.joinToString(" ")
}
