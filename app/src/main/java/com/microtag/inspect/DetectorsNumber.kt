package com.microtag.inspect

import java.util.Locale

// ==========================================================
// NUMBER: "123456" -> "one lakh twenty-three thousand four hundred fifty-six"
// Selection only, and only for numbers worth spelling out.
// ==========================================================

internal object NumberWords {
    private val ones = arrayOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
        "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"
    )
    private val tens = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    fun below100(n: Int): String = when {
        n < 20 -> ones[n]
        n % 10 == 0 -> tens[n / 10]
        else -> tens[n / 10] + "-" + ones[n % 10]
    }

    fun below1000(n: Int): String {
        val h = n / 100
        val r = n % 100
        return when {
            h == 0 -> below100(r)
            r == 0 -> ones[h] + " hundred"
            else -> ones[h] + " hundred " + below100(r)
        }
    }

    fun international(n: Long): String {
        if (n == 0L) return "zero"
        val scales = listOf(1_000_000_000_000L to "trillion", 1_000_000_000L to "billion", 1_000_000L to "million", 1_000L to "thousand")
        var rest = n
        val parts = ArrayList<String>()
        for ((size, name) in scales) {
            if (rest >= size) {
                parts.add(below1000((rest / size).toInt()) + " " + name)
                rest %= size
            }
        }
        if (rest > 0) parts.add(below1000(rest.toInt()))
        return parts.joinToString(" ")
    }

    fun indian(n: Long): String {
        if (n == 0L) return "zero"
        var rest = n
        val parts = ArrayList<String>()
        val crore = rest / 10_000_000L
        if (crore > 0) {
            parts.add((if (crore < 1000) below1000(crore.toInt()) else international(crore)) + " crore")
            rest %= 10_000_000L
        }
        val lakh = rest / 100_000L
        if (lakh > 0) {
            parts.add(below100(lakh.toInt()) + " lakh")
            rest %= 100_000L
        }
        val thousand = rest / 1000L
        if (thousand > 0) {
            parts.add(below100(thousand.toInt()) + " thousand")
            rest %= 1000L
        }
        if (rest > 0) parts.add(below1000(rest.toInt()))
        return parts.joinToString(" ")
    }

    fun fraction(digits: String): String = "point " + digits.map { ones[it - '0'] }.joinToString(" ")

    fun compactInternational(v: Double): String {
        val a = kotlin.math.abs(v)
        return when {
            a >= 1e12 -> Fmt.trimmed(v / 1e12, 2) + " T"
            a >= 1e9 -> Fmt.trimmed(v / 1e9, 2) + " B"
            a >= 1e6 -> Fmt.trimmed(v / 1e6, 2) + " M"
            a >= 1e3 -> Fmt.trimmed(v / 1e3, 2) + " K"
            else -> Fmt.trimmed(v, 2)
        }
    }

    fun compactIndian(v: Double): String {
        val a = kotlin.math.abs(v)
        return when {
            a >= 1e7 -> Fmt.trimmed(v / 1e7, 2) + " crore"
            a >= 1e5 -> Fmt.trimmed(v / 1e5, 2) + " lakh"
            a >= 1e3 -> Fmt.trimmed(v / 1e3, 2) + " K"
            else -> Fmt.trimmed(v, 2)
        }
    }
}

internal object NumberDetector {
    private val shape = Regex("""^\d[\d,\s]*(\.\d{1,6})?$""")

    fun candidates(text: String, source: Source, env: Env): List<Candidate> {
        if (source != Source.SELECTION) return emptyList()
        val t = text.trim()
        if (t.length > 24 || !shape.matches(t)) return emptyList()
        val bare = t.all { it.isDigit() }
        if (bare && (t.length >= 10 || t.startsWith("0"))) return emptyList()      // phone numbers, ids, codes
        if (bare && t.length == 4 && t.toInt() in 1900..2100) return emptyList()   // years
        val value = Fmt.parseNumber(t) ?: return emptyList()
        if (value < 1000 || value >= 1e15) return emptyList()
        return listOf(Candidate("number", 0.35) { build(t, value, env) })
    }

    private fun build(raw: String, value: Double, env: Env): InspectResult {
        val whole = value.toLong()
        val fracDigits = if (raw.contains('.')) raw.substringAfter('.').filter { it.isDigit() } else ""
        val frac = if (fracDigits.isNotEmpty() && fracDigits.any { it != '0' }) " " + NumberWords.fraction(fracDigits) else ""
        val intl = NumberWords.international(whole) + frac
        val ind = NumberWords.indian(whole) + frac
        val indianFirst = env.locale.country.equals("IN", ignoreCase = true)

        val words = if (indianFirst) ind else intl
        val pill = if (PillConfig.NUMBER == "GROUPED") Fmt.grouped(value, env.locale)
            else if (indianFirst) NumberWords.compactIndian(value) else NumberWords.compactInternational(value)
        val rows = mutableListOf("In words" to words.replaceFirstChar { it.uppercase(Locale.ROOT) })
        if (ind != intl) {
            if (indianFirst) rows.add("International" to intl) else rows.add("Indian" to ind)
        }
        rows.add("Grouped" to Fmt.grouped(value, env.locale))
        rows.add("Compact" to (if (indianFirst) NumberWords.compactInternational(value) else NumberWords.compactIndian(value)))

        return InspectResult(
            domain = "NUM",
            pill = pill,
            title = Fmt.grouped(value, env.locale),
            subtitle = "Number in words",
            cardTitle = "Number",
            cardText = words,
            rows = rows,
            copyText = Fmt.plain(value),
            iconName = "ic_equals",
            timeoutSeconds = 15,
            fact = NumberFacts.find(value, env.locale).orEmpty()
        )
    }
}

/** The reverse of the number card: "three lakh fifty thousand", "two hundred and five", "1.5 million" give digits. */
internal object NumberFromWordsDetector {
    private val small = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
        "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"
    ).withIndex().associate { it.value to it.index.toLong() }
    private val tens = mapOf(
        "twenty" to 20L, "thirty" to 30L, "forty" to 40L, "fourty" to 40L, "fifty" to 50L,
        "sixty" to 60L, "seventy" to 70L, "eighty" to 80L, "ninety" to 90L
    )
    private val scales = mapOf(
        "thousand" to 1_000L, "lakh" to 100_000L, "lakhs" to 100_000L, "lac" to 100_000L, "lacs" to 100_000L,
        "crore" to 10_000_000L, "crores" to 10_000_000L, "million" to 1_000_000L, "billion" to 1_000_000_000L,
        "trillion" to 1_000_000_000_000L
    )
    private val digitScale = Regex("""^(\d+(?:\.\d+)?)\s*(thousand|lakh|lakhs|lac|lacs|crore|crores|million|billion|trillion)$""", RegexOption.IGNORE_CASE)

    fun candidates(text: String, source: Source, env: Env): List<Candidate> {
        if (source != Source.SELECTION) return emptyList()
        val t = text.trim().trimEnd('.')
        if (t.isEmpty() || t.length > 90) return emptyList()
        digitScale.matchEntire(t)?.let { m ->
            val n = m.groupValues[1].toDoubleOrNull() ?: return emptyList()
            val v = n * (scales[m.groupValues[2].lowercase(Locale.ROOT)] ?: return emptyList())
            if (v >= 1e15) return emptyList()
            return listOf(Candidate("number-words", 0.6) { build(t, Math.round(v), env) })
        }
        val parsed = parse(t) ?: return emptyList()
        val tokens = t.split(Regex("""[\s\-,]+""")).filter { it.isNotEmpty() && !it.equals("and", true) }
        val score = if (tokens.size >= 2) 0.65 else 0.35
        return listOf(Candidate("number-words", score) { build(t, parsed, env) })
    }

    /** Words to a number, or null if anything in the text is not a number word. */
    internal fun parse(text: String): Long? {
        val tokens = text.lowercase(Locale.ROOT).split(Regex("""[\s\-,]+""")).filter { it.isNotEmpty() && it != "and" }
        if (tokens.isEmpty()) return null
        var total = 0L
        var current = 0L
        var seen = false
        for (tok in tokens) {
            val s = small[tok]
            val tn = tens[tok]
            val sc = scales[tok]
            when {
                s != null -> { current += s; seen = true }
                tn != null -> { current += tn; seen = true }
                tok == "hundred" -> { current = (if (current == 0L) 1 else current) * 100; seen = true }
                sc != null -> { total += (if (current == 0L) 1 else current) * sc; current = 0; seen = true }
                else -> return null
            }
        }
        val v = total + current
        return if (seen && v < 1_000_000_000_000_000L) v else null
    }

    private fun build(original: String, value: Long, env: Env): InspectResult {
        val indian = Fmt.grouped(value.toDouble(), Locale("en", "IN"), 0)
        val intl = Fmt.grouped(value.toDouble(), Locale.US, 0)
        val local = Fmt.grouped(value.toDouble(), env.locale, 0)
        val rows = mutableListOf("Digits" to value.toString())
        if (indian != intl) {
            rows.add("Indian" to indian)
            rows.add("International" to intl)
        }
        rows.add("Compact" to NumberWords.compactInternational(value.toDouble()))
        return InspectResult(
            domain = "NUM",
            pill = local,
            title = local,
            subtitle = original.replaceFirstChar { it.uppercase(Locale.ROOT) },
            cardTitle = "Number",
            cardText = "$original = $local",
            rows = rows,
            copyText = value.toString(),
            iconName = "ic_equals",
            timeoutSeconds = 15,
            fact = NumberFacts.find(value.toDouble(), env.locale).orEmpty()
        )
    }
}
