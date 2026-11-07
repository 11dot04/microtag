package com.microtag.rules

/**
 * Removes what should not leave the phone from a piece of notification text: codes, long numbers, phone numbers,
 * emails and the query part of links. Used before anything is sent to a model or put in a contribution.
 */
object Redactor {
    private val email = Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+""")
    private val url = Regex("""https?://[^\s/]+[^\s?]*(?:\?\S*)?""")
    private val phone = Regex("""(?<![\w])\+?\d[\d\s-]{8,}\d""")
    private val longDigits = Regex("""\d{4,}""")

    /** Digits of 4 or more become "••••"; everything else with a person in it is named for what it was. */
    fun redact(text: String): String {
        var s = text
        s = email.replace(s, "‹email›")
        s = url.replace(s) { m -> m.value.substringBefore('?').let { if (it.length > 60) it.take(60) + "…" else it } }
        s = phone.replace(s, "‹phone›")
        s = longDigits.replace(s, "••••")
        return s
    }

    /**
     * For a model: the shape stays (so a pattern written from it still fits the real text) but the content goes.
     * Digit runs of 6 or more (codes, card and phone numbers) become 7s, emails become user@example.com, links lose
     * their query. Amounts and short numbers stay, because the pill is often one of them.
     */
    fun forModel(text: String): String {
        var s = text
        s = email.replace(s, "user@example.com")
        s = url.replace(s) { m -> m.value.substringBefore('?').let { if (it.length > 60) it.take(60) else it } }
        s = phone.replace(s) { m -> m.value.map { if (it.isDigit()) '9' else it }.joinToString("") }
        s = Regex("""\d{6,}""").replace(s) { "7".repeat(it.value.length) }
        return s
    }
}
