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
        val pill = if (indianFirst) NumberWords.compactIndian(value) else NumberWords.compactInternational(value)
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
            copyText = words,
            iconName = "ic_equals",
            timeoutSeconds = 15
        )
    }
}
