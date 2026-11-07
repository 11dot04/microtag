package com.microtag.rules

/**
 * Builds a pattern from what the person selected, with no model involved: "this part of the notification".
 * The selection is generalised (digits become \d+, words stay as letters) and anchored by the words just before it,
 * so "Order arriving in 12 mins" with "12 mins" selected gives  arriving\s+in\s+(\d+\s+\p{L}+).
 * The result is checked on the sample it came from before it is offered. Pure Kotlin, tested on the JVM.
 */
object RuleSuggester {

    data class Suggestion(val regex: String, val pill: String, val explain: String)

    fun suggest(target: String, selections: List<String>): Suggestion? {
        val picks = selections.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(3)
        if (picks.isEmpty()) return null
        // In the order they appear.
        val located = picks.mapNotNull { s -> target.indexOf(s).takeIf { it >= 0 }?.let { it to s } }.sortedBy { it.first }
        if (located.size != picks.size) return null
        for ((i, p) in located.withIndex()) if (i > 0 && p.first < located[i - 1].first + located[i - 1].second.length) return null

        val sb = StringBuilder()
        var cursor = 0
        located.forEachIndexed { i, (start, sel) ->
            val before = target.substring(cursor, start)
            val ctx = if (i == 0) leftContext(target.substring(0, start)) else leftContext(before)
            if (i > 0) sb.append(".*?")
            sb.append(ctx)
            sb.append('(').append(generalise(sel)).append(')')
            cursor = start + sel.length
        }
        // A selection with nothing before it is anchored by the word after it.
        if (located.first().first == 0 || leftContext(target.substring(0, located.first().first)).isEmpty()) {
            val after = rightContext(target.substring(cursor))
            sb.append(after)
        }
        val regex = sb.toString()
        val rx = SafeRegex.compile(regex).getOrNull() ?: return null
        val found = SafeRegex.find(rx, target) as? SafeRegex.Outcome.Found ?: return null
        if (found.groups.drop(1) != located.map { it.second }) return null
        val pill = (1..located.size).joinToString(" ") { "$$it" }
        return Suggestion(regex, pill, "Matches ${located.size} part${if (located.size > 1) "s" else ""} of the notification and shows ${if (located.size > 1) "them" else "it"} as the pill.")
    }

    /** Up to two words before the selection, as a pattern, ending in the gap that separated them. */
    private fun leftContext(before: String): String {
        val tokens = before.trimEnd().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ""
        val tail = tokens.takeLast(2)
        val gap = if (before.last().isWhitespace()) """\s*""" else ""
        // A token with digits is probably a changing value (an id), so context stops short of it.
        val usable = tail.takeLastWhile { t -> t.none { it.isDigit() } }
        if (usable.isEmpty()) return ""
        return usable.joinToString("""\s+""") { escape(it) } + gap
    }

    private fun rightContext(after: String): String {
        val t = after.trimStart().split(Regex("""\s+""")).firstOrNull { it.isNotEmpty() } ?: return ""
        if (t.any { it.isDigit() }) return ""
        return """\s*""" + escape(t)
    }

    /** Digit runs become \d+ (decimals and thousands separators kept), letter runs \p{L}+, spaces \s+, the rest literal. */
    fun generalise(sel: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < sel.length) {
            val c = sel[i]
            when {
                c.isDigit() -> {
                    var j = i
                    while (j < sel.length && sel[j].isDigit()) j++
                    // 1,250.50 and 3.5 stay one number.
                    var more = true
                    while (more) {
                        more = false
                        if (j + 1 < sel.length && (sel[j] == '.' || sel[j] == ',') && sel[j + 1].isDigit()) {
                            j++
                            while (j < sel.length && sel[j].isDigit()) j++
                            more = true
                        }
                    }
                    out.append(if (sel.substring(i, j).any { !it.isDigit() }) """\d+(?:[.,]\d+)*""" else """\d+""")
                    i = j
                }
                c.isLetter() -> {
                    var j = i
                    while (j < sel.length && sel[j].isLetter()) j++
                    out.append("""\p{L}+""")
                    i = j
                }
                c.isWhitespace() -> {
                    var j = i
                    while (j < sel.length && sel[j].isWhitespace()) j++
                    out.append("""\s+""")
                    i = j
                }
                else -> { out.append(escape(c.toString())); i++ }
            }
        }
        return out.toString()
    }

    private fun escape(s: String): String = buildString { s.forEach { ch -> if (ch in "\\.[]{}()*+-?^$|") append('\\'); append(ch) } }

    /** Does [regex] give a non-empty pill on every one of [samples]? Returns the samples it missed. */
    fun missed(regex: String, pill: String, samples: List<String>, ignoreCase: Boolean = true): List<String> {
        val rx = SafeRegex.compile(regex, ignoreCase).getOrNull() ?: return samples
        return samples.filter { s ->
            val f = SafeRegex.find(rx, s) as? SafeRegex.Outcome.Found
            f == null || RuleTemplate.expand(pill, f.groups).isBlank()
        }
    }
}
