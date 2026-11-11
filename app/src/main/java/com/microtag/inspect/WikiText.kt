package com.microtag.inspect

/** The text half of the Wiki card: what the thing is, and its dates. Pure, so it is tested on the JVM. */

private val parenYears = Regex("""\s*\((?:born\s+|b\.\s*)?(\d{3,4})(?:\s*[–-]\s*(\d{3,4}|present))?\)""", RegexOption.IGNORE_CASE)

// "from 1939 to 1945", "between 1939 and 1945", "1939–1945", "in 1939–45". The second year may be two digits.
private val rangeYears = Regex(
    """\s*\b(?:(?:from|between|during|in|of|circa|c\.)\s+)?(\d{3,4})\s*(?:to|until|till|through|and|[–—-])\s*(\d{2,4})\b""",
    RegexOption.IGNORE_CASE
)
private val leadingYear = Regex("""^(?:c\.\s*)?(\d{4})\s+(.+)$""")
private val trailingYear = Regex("""\s+(?:in|since|from|of|circa|c\.)\s+(\d{3,4})$""", RegexOption.IGNORE_CASE)

/**
 * "Swiss mathematician (1707–1783)" -> kicker "Swiss mathematician", years "1707–1783".
 * "global conflict from 1939 to 1945" -> "global conflict", "1939–1945". "1971 novel by X" -> "novel by X", "1971".
 * Years only when they exist.
 */
internal fun splitYears(desc: String): Pair<String, String> {
    parenYears.find(desc)?.let { m ->
        val years = m.groupValues[1] + if (m.groupValues[2].isNotEmpty()) "–" + m.groupValues[2] else ""
        return tidy(desc.removeRange(m.range)) to years
    }
    rangeYears.find(desc)?.let { m ->
        val a = m.groupValues[1]
        var b = m.groupValues[2]
        if (b.length == 2) b = a.take(a.length - 2) + b
        val from = a.toInt()
        val to = b.toIntOrNull()
        if (to != null && to > from && to - from < 1000) return tidy(desc.removeRange(m.range)) to "$a–$b"
    }
    leadingYear.find(desc)?.let { return it.groupValues[2] to it.groupValues[1] }
    trailingYear.find(desc)?.let { return tidy(desc.removeRange(it.range)) to it.groupValues[1] }
    return desc to ""
}

private fun tidy(s: String): String = s.replace(Regex("""\s{2,}"""), " ").trim().trimEnd(',', ';', '–', '-', ' ')

private val danglers = setOf("in", "of", "the", "a", "an", "and", "for", "to", "on", "at", "by", "from", "with")

private val breakers = setOf("in", "of", "from", "since", "during", "at", "on", "for", "to", "with", "by", "under", "over", "between", "near")
private val participles = setOf("produced", "made", "known", "born", "founded", "created", "built", "used", "found", "located", "situated", "based", "named", "called", "published", "written", "released", "developed", "established")

/**
 * "Swiss mathematician and physicist" -> "Swiss mathematician"; "novel by Hunter S. Thompson" -> "novel";
 * "Mauryan emperor from 269 BCE" -> "Mauryan emperor"; "Enzyme produced by liver" -> "Enzyme".
 * Cuts only at phrase boundaries: backs off before a preposition (and the number or date after it),
 * drops trailing participles and joining words, keeps at most five words.
 */
internal fun shortKicker(desc: String): String {
    val first = desc.split(Regex("""\s*(?:,|;|\s[–—-]\s|\s+and\s+|\s+who\s+|\s+that\s+|\s+which\s+|\s+by\s+)""")).first().trim()
    val all = first.split(' ').filter { it.isNotEmpty() }
    val cut = all.indexOfFirst { it.lowercase() in breakers }.let { if (it <= 0) all.size else it }
    val ws = all.take(cut).take(5).toMutableList()
    while (ws.size > 1 && (ws.last().lowercase() in danglers || ws.last().lowercase() in participles || ws.last().any { it.isDigit() })) ws.removeAt(ws.size - 1)
    if (ws.size == all.size) return ws.joinToString(" ")
    // "Hill station in India": a short place phrase after the cut stays when the head is tiny and the tail has no number.
    val rest = all.drop(cut)
    return if (ws.size <= 2 && rest.size in 2..3 && rest.none { w -> w.any { it.isDigit() } }) (ws + rest).joinToString(" ") else ws.joinToString(" ")
}

/** The staggered pair: ("1707", "–1783") for years, ("48.86° N", "2.35° E") for places, or null. */
internal fun staggerOf(years: String, fact: String): Pair<String, String>? {
    if (years.isNotBlank()) {
        val parts = years.split('–')
        val a = parts[0]
        val b = parts.getOrNull(1).orEmpty()
        return a to when {
            b.isBlank() -> ""
            b.equals("present", true) -> "–NOW"
            else -> "–$b"
        }
    }
    if (fact.contains('|')) {
        val f = fact.split('|')
        return f[0] to f.getOrElse(1) { "" }
    }
    return null
}
