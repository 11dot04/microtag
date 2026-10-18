package com.microtag.listener

/**
 * Pulls a one-time code out of notification text. Pure Kotlin, so it is tested on the JVM.
 *
 * What changed from the earlier rule, which missed real codes:
 *  - Wording is matched as whole words and in more forms (authorization, confirmation, access, reset, 2-step,
 *    "do not share", Hindi), instead of a loose substring test.
 *  - Every candidate is scored; one disqualified number (an order id, a year) no longer ends the search.
 *  - A 4-digit code that happens to start with 19 or 20 ("OTP 2048") is accepted when the wording points at it.
 *  - Alphanumeric codes ("A7K9Q2") are found when they follow code / OTP / pin wording.
 *  - Phone numbers, percentages and amounts after the number are skipped.
 */
object OtpExtractor {

    /** Words that say a number is a login or verification code. */
    private val keyword = Regex(
        """(?i)\b(?:otp|o\.t\.p|one[- ]?time(?:[- ]?(?:password|passcode|pin|code))?|verification|verify|verifying|passcode|pass code|password|""" +
            """security code|auth(?:entication|orization|orisation|enticate)?(?: code| pin| key)?|login|log-?in|sign-?in|2fa|2-?step|two[- ]?factor|""" +
            """confirmation|confirm|access code|reset code|activation|validation|do not share|don'?t share|never share|pin|code|token|tpin)\b|ओटीपी|कोड"""
    )

    /** Wording strong enough to treat a notification from any app as a code message. */
    private val strongKeyword = Regex(
        """(?i)\b(?:otp|one[- ]?time[- ]?(?:password|passcode|pin|code)|verification code|security code|login code|log-?in code|""" +
            """auth(?:entication|orization|orisation)? code|confirmation code|passcode|2fa code|ओटीपी)\b|ओटीपी"""
    )

    // 4 to 8 digits, or 3+3 split by a space or hyphen, optionally Google's "G-" prefix. Not part of a longer number or a phone number.
    private val candidate = Regex("""(?<![\w.,+])(?:G-)?(\d{3}[ -]\d{3}|\d{4,8})(?!\w|[.,]\d)""")

    // A code with letters and digits, right after code / OTP / pin wording.
    private val alnum = Regex("""(?i:code|otp|pin|passcode|password|token)\s*(?:is|:|=|-|–)?\s*([A-Z0-9]{5,8})\b""")

    // Words just before a number that mean it is something else (amount, order, promo, postcode...).
    private val notCodeBefore = Regex(
        """(?i)(?:promo(?:tion)?|coupon|referral|discount|voucher|zip|postal|pin ?code|area code|order|tracking|booking|invoice|ref(?:erence)?|a/c|acct|account(?: no\.?| number)?|card|amount|amt|rs\.?|inr|usd|eur|₹|\$|€|£|balance|bal|call|dial|tel|phone|mobile|ph)[^\d]{0,12}$"""
    )
    private val notCodeAfter = Regex("""(?i)^\s*(?:/-|%|rs\b|inr\b|rupees?\b|usd\b|mah\b|km\b|kg\b|mb\b|gb\b)""")
    private val isJustBefore = Regex("""(?i)(?:\bis|:|=|-|–)\s*$""")
    private val saidIsCode = Regex("""(?i)^\s*(?:is|was)\s+(?:your|the|a|an|valid|one)|^\s*(?:to (?:verify|log|sign|confirm|complete|proceed|reset)|will expire|expires|valid for|for (?:login|log-in|verification|your))""")

    fun hasWording(text: String): Boolean = keyword.containsMatchIn(text)

    /** True when the text reads clearly like a code message whichever app it came from. */
    fun isCodeMessage(text: String): Boolean = strongKeyword.containsMatchIn(text) && extract(text) != null

    fun extract(text: String): String? {
        val words = keyword.findAll(text).map { it.range }.toList()
        if (words.isEmpty()) return null

        var best: String? = null
        var bestScore = 0
        for (m in candidate.findAll(text)) {
            val digits = m.groupValues[1].filter { it.isDigit() }
            if (digits.length !in 4..8) continue
            val start = m.range.first
            val end = m.range.last

            val before = text.substring(maxOf(0, start - 15), start)
            val after = text.substring(end + 1, minOf(text.length, end + 1 + 40))
            if (notCodeBefore.containsMatchIn(before)) continue
            if (notCodeAfter.containsMatchIn(after)) continue

            val wordBefore = words.any { it.last < start && start - it.last <= 30 }
            val wordAfter = words.any { it.first > end && it.first - end <= 30 }
            val said = saidIsCode.containsMatchIn(after)
            val justBefore = isJustBefore.containsMatchIn(before)

            // A bare 4-digit number that looks like a year is only believed when the wording points straight at it.
            val yearLike = digits.length == 4 && (digits.startsWith("19") || digits.startsWith("20"))
            if (yearLike && !(wordBefore || said || justBefore)) continue

            var score = 1
            if (wordBefore) score += 3
            if (wordAfter) score += 2
            if (said) score += 2
            if (justBefore) score += 2
            if (score > bestScore) {
                bestScore = score
                best = digits
            }
        }
        if (best != null) return best

        // Letters and digits together, only when the wording is right beside it.
        for (m in alnum.findAll(text)) {
            val code = m.groupValues[1]
            if (code.any { it.isDigit() } && code.any { it.isLetter() }) return code
        }
        return null
    }
}
