package com.microtag.rules

/**
 * Regexes typed by a person (or suggested by a model) run inside a notification listener, so they must never hang it.
 * Every match runs on a character budget: a pattern that backtracks without end is stopped, not waited for.
 * Pure Kotlin, tested on the JVM.
 */
object SafeRegex {
    const val MAX_PATTERN = 400
    private const val DEFAULT_BUDGET = 250_000L

    class TooSlow : RuntimeException("regex budget exceeded")

    private class Limited(private val base: CharSequence, private var budget: Long) : CharSequence {
        override val length: Int get() = base.length
        override fun get(index: Int): Char {
            if (--budget < 0) throw TooSlow()
            return base[index]
        }
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = base.subSequence(startIndex, endIndex)
        override fun toString() = base.toString()
    }

    sealed class Outcome {
        data class Found(val groups: List<String>) : Outcome()
        object None : Outcome()
        object Slow : Outcome()
    }

    fun compile(pattern: String, ignoreCase: Boolean = true): Result<Regex> = runCatching {
        require(pattern.isNotBlank()) { "The pattern is empty" }
        require(pattern.length <= MAX_PATTERN) { "The pattern is longer than $MAX_PATTERN characters" }
        if (ignoreCase) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
    }

    fun find(rx: Regex, text: String, budget: Long = DEFAULT_BUDGET): Outcome = try {
        val m = rx.find(Limited(text, budget))
        if (m == null) Outcome.None else Outcome.Found(m.groupValues)
    } catch (_: TooSlow) {
        Outcome.Slow
    }

    /** Groups in [pattern] (0 when it does not compile). */
    fun groupCount(pattern: String): Int = runCatching { java.util.regex.Pattern.compile(pattern).matcher("").groupCount() }.getOrDefault(0)

    /**
     * Android's regex engine copies the text before matching, so the budget above cannot stop it there. What protects
     * a phone is refusing the shapes that blow up: a repeated group that itself repeats, "(a+)+", or that chooses
     * between branches each time round, "(a|aa)+". Returns a sentence, or null when the shape is safe.
     */
    fun riskyShape(pattern: String): String? {
        class G { var inner = false }
        val stack = ArrayDeque<G>()
        var i = 0
        var inClass = false
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '\\' -> i++                      // skip the escaped character
                inClass -> if (c == ']') inClass = false
                c == '[' -> inClass = true
                c == '(' -> stack.addLast(G())
                c == '|' -> stack.lastOrNull()?.inner = true
                c == '*' || c == '+' -> stack.lastOrNull()?.inner = true
                c == '{' -> if (Regex("""^\{\d+,\d*\}""").containsMatchIn(pattern.substring(i))) stack.lastOrNull()?.inner = true
                c == ')' -> {
                    val g = stack.removeLastOrNull()
                    val next = pattern.getOrNull(i + 1)
                    val repeats = next == '*' || next == '+' || (next == '{' && Regex("""^\{\d+,\d*\}""").containsMatchIn(pattern.substring(i + 1)))
                    if (g != null && g.inner && repeats) return "This pattern repeats something that already repeats. Make it more specific."
                    // A finished group that repeats or branches counts as repeating for the group around it.
                    if (g != null && g.inner) stack.lastOrNull()?.inner = true
                }
            }
            i++
        }
        return null
    }

    /**
     * Full check for saving a rule: compiles, and survives text built to make bad patterns backtrack.
     * Returns null when fine, otherwise a sentence the user can read.
     */
    fun problem(pattern: String, ignoreCase: Boolean = true): String? {
        val rx = compile(pattern, ignoreCase).getOrElse { return it.message?.lineSequence()?.firstOrNull() ?: "This is not a valid pattern" }
        riskyShape(pattern)?.let { return it }
        val traps = listOf("a".repeat(40) + "!", "1".repeat(40) + "x", " ".repeat(60) + "z", "ab".repeat(50), ("word ".repeat(20)) + "!")
        for (t in traps) if (find(rx, t, 150_000L) == Outcome.Slow) return "This pattern can get stuck on some text. Make it more specific."
        return null
    }
}

/** Fills a pill template from match groups: "$1d" with group 1 = "12" gives "12d". No $ at all falls back to group 1. */
object RuleTemplate {
    fun expand(template: String, groups: List<String>): String {
        var out = template
        // Highest index first, so "$10" is never read as "$1" followed by "0".
        for (i in groups.indices.reversed()) out = out.replace("$$i", groups[i])
        if (out == template && groups.size > 1) out = groups[1]
        return out.trim()
    }

    /** The highest group number the template refers to. */
    fun maxRef(template: String): Int =
        Regex("""\$(\d+)""").findAll(template).map { it.groupValues[1].toInt() }.maxOrNull() ?: 0
}
