package com.microtag.inspect

import java.time.Duration
import java.util.Currency
import java.util.Locale

// ==========================================================
// MONEY: "$1,299.50", "₹5 lakh", "100 EUR", "USD 20", "3.5k dollars"
// Converts into the user's own currency, plus USD, EUR and GBP.
// ==========================================================

internal object CurrencyDetector {

    private val symbols = linkedMapOf(
        "US$" to "USD", "NZ$" to "NZD", "HK$" to "HKD", "R$" to "BRL", "A$" to "AUD", "C$" to "CAD", "S$" to "SGD",
        "Rs." to "INR", "Rs" to "INR", "$" to "USD", "€" to "EUR", "£" to "GBP", "¥" to "JPY", "₹" to "INR",
        "₩" to "KRW", "₽" to "RUB", "₺" to "TRY", "₫" to "VND", "฿" to "THB", "₴" to "UAH", "₪" to "ILS",
        "₦" to "NGN", "₱" to "PHP"
    )

    // Words that are safe to read as a currency when someone selects them. "pound" is left out on purpose:
    // it is far more often a weight.
    private val words = mapOf(
        "rupee" to "INR", "rupees" to "INR", "rs" to "INR",
        "dollar" to "USD", "dollars" to "USD",
        "euro" to "EUR", "euros" to "EUR",
        "quid" to "GBP", "yen" to "JPY", "yuan" to "CNY", "rmb" to "CNY",
        "dirham" to "AED", "dirhams" to "AED",
        "ringgit" to "MYR", "baht" to "THB", "rouble" to "RUB", "roubles" to "RUB", "ruble" to "RUB", "rubles" to "RUB",
        "lira" to "TRY", "rupiah" to "IDR", "dong" to "VND", "shekel" to "ILS", "shekels" to "ILS",
        "naira" to "NGN", "taka" to "BDT"
    )

    private val lowercaseCodes = setOf("usd", "eur", "inr", "gbp", "jpy", "aud", "cad", "chf", "cny", "aed", "sgd")

    // Real ISO codes that are also everyday words. Only trusted when the user selected the text.
    private val wordyCodes = setOf("ALL", "TOP", "TRY", "MAD", "CUP", "PEN", "SOS", "BOB", "LAK", "MOP", "PAB")

    private val multipliers = mapOf(
        "k" to 1e3, "thousand" to 1e3,
        "m" to 1e6, "mn" to 1e6, "million" to 1e6,
        "b" to 1e9, "bn" to 1e9, "bil" to 1e9, "billion" to 1e9,
        "lakh" to 1e5, "lakhs" to 1e5, "lac" to 1e5, "lacs" to 1e5,
        "crore" to 1e7, "crores" to 1e7, "cr" to 1e7
    )

    private const val NUM = """(\d[\d.,]*)"""
    private const val MULT = """((?i:k|thousand|m|mn|million|b|bn|bil|billion|lakhs?|lacs?|crores?|cr))"""

    private val symAlt = symbols.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
    private val prefixSymbol = Regex("^($symAlt)\\s?$NUM(?:\\s?$MULT)?$")
    private val prefixCode = Regex("^([A-Za-z]{3})\\s?$NUM(?:\\s?$MULT)?$")
    private val suffix = Regex("^$NUM(?:\\s?$MULT)?\\s?([A-Za-z]+)$")

    private class Parsed(val amount: Double, val code: String, val symbol: String?, val score: Double)

    fun candidates(text: String, source: Source, env: Env): List<Candidate> {
        val p = parse(text, source) ?: return emptyList()
        return listOf(Candidate("money", p.score, slow = true) { build(p, env) })
    }

    private fun multiplier(raw: String?): Double =
        if (raw.isNullOrEmpty()) 1.0 else multipliers[raw.lowercase(Locale.ROOT)] ?: 1.0

    private fun validCode(code: String): Boolean = try {
        Currency.getInstance(code)
        true
    } catch (_: Exception) {
        false
    }

    /** A three-letter token becomes a currency code only if it is typed in capitals (or is a safe lowercase one). */
    private fun codeFrom(token: String, source: Source): String? {
        if (token.length == 3) {
            val upper = token.uppercase(Locale.ROOT)
            val isCaps = token == upper
            val okCase = isCaps || (source == Source.SELECTION && token.lowercase(Locale.ROOT) in lowercaseCodes)
            if (okCase && validCode(upper)) {
                if (upper in wordyCodes && source != Source.SELECTION) return null
                return upper
            }
        }
        if (source == Source.SELECTION) return words[token.lowercase(Locale.ROOT)]
        return null
    }

    private fun parse(text: String, source: Source): Parsed? {
        val t = text.trim()
        if (t.length > 40) return null

        prefixSymbol.find(t)?.let { m ->
            val sym = m.groupValues[1]
            val value = Fmt.parseNumber(m.groupValues[2]) ?: return null
            return make(value * multiplier(m.groupValues[3]), symbols[sym] ?: return null, sym, 0.92)
        }
        prefixCode.find(t)?.let { m ->
            val code = codeFrom(m.groupValues[1], source)
            val value = Fmt.parseNumber(m.groupValues[2])
            if (code != null && value != null) return make(value * multiplier(m.groupValues[3]), code, null, 0.85)
        }
        suffix.find(t)?.let { m ->
            val code = codeFrom(m.groupValues[3], source) ?: return null
            val value = Fmt.parseNumber(m.groupValues[1]) ?: return null
            val isWord = m.groupValues[3].length != 3 || m.groupValues[3] in words
            return make(value * multiplier(m.groupValues[2]), code, null, if (isWord) 0.8 else 0.85)
        }
        return null
    }

    private fun make(amount: Double, code: String, symbol: String?, score: Double): Parsed? {
        if (!(amount > 0.0) || amount > 1e15) return null
        return Parsed(amount, code, symbol, score)
    }

    private fun localCode(locale: Locale): String = try {
        Currency.getInstance(locale).currencyCode
    } catch (_: Exception) {
        "USD"
    }

    private fun age(ms: Long): String {
        val d = Duration.ofMillis(ms.coerceAtLeast(0))
        return when {
            d.toMinutes() < 2 -> "just now"
            d.toHours() < 1 -> "${d.toMinutes()} min ago"
            d.toDays() < 1 -> "${d.toHours()} h ago"
            else -> "${d.toDays()} d ago"
        }
    }

    private fun build(p: Parsed, env: Env): InspectResult? {
        val table = env.rates?.table()
        val inputText = Fmt.money(p.amount, p.code, env.locale)
        if (table == null) {
            return InspectResult(
                domain = "MONEY", pill = "No rates", title = inputText, cardTitle = "Currency",
                cardText = "Couldn't load exchange rates. Check your connection and try again.",
                subtitle = p.code, iconName = "ic_inspect_convert", status = Status.ERROR
            )
        }
        val srcRate = table.perUsd[p.code] ?: return null

        val local = localCode(env.locale)
        val targets = listOf(local, "USD")
            .filter { it != p.code && table.perUsd.containsKey(it) }
            .distinct()
            .take(4)
        if (targets.isEmpty()) return null

        fun convert(to: String): Double {
            val r = table.perUsd[to] ?: return Double.NaN
            return p.amount / srcRate * r
        }

        val primary = targets.first()
        val primaryValue = convert(primary)
        if (primaryValue.isNaN()) return null
        val pill = Fmt.money(primaryValue, primary, env.locale)

        val rows = mutableListOf<Pair<String, String>>()
        targets.forEach { t ->
            val v = convert(t)
            if (!v.isNaN()) rows.add("In $t" to Fmt.money(v, t, env.locale))
        }
        val unitRate = (table.perUsd[primary] ?: 1.0) / srcRate
        rows.add("Rate" to "1 ${p.code} = ${Fmt.adaptive(unitRate)} $primary")
        val ageMs = env.clock.millis() - table.fetchedAtMillis
        rows.add("Updated" to age(ageMs) + if (table.offlineCopy) " (offline copy)" else "")
        when (p.symbol) {
            "$" -> rows.add("Note" to "\$ read as US dollars")
            "¥" -> rows.add("Note" to "¥ read as Japanese yen (the yuan uses ¥ too)")
        }

        return InspectResult(
            domain = "MONEY",
            pill = pill,
            title = pill,
            subtitle = inputText,
            cardTitle = "Currency",
            cardText = "$inputText = $pill",
            rows = rows,
            copyText = pill,
            iconName = if (primary == "INR") "ic_upi_rupee" else "ic_inspect_convert",
            timeoutSeconds = 20,
            status = if (Duration.ofMillis(ageMs).toDays() >= 3) Status.WARN else Status.OK
        )
    }
}
