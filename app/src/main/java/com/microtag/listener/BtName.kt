package com.microtag.listener

/**
 * Short, readable device names for a status-bar pill. Pure Kotlin, tested on the JVM.
 *  - The brand goes ("TRIGGR Rizz" -> "Rizz", "OnePlus Nord Buds 3r" -> "Buds 3r").
 *  - Filler words go ("Wireless", "Bluetooth", "Headphones").
 *  - If it still does not fit, whole words are dropped from the front, never a word cut in half
 *    (unless one word alone is too long).
 */
object BtName {
    const val FIT = 10

    private val brands = setOf(
        "oneplus", "nord", "triggr", "boat", "sony", "jbl", "realme", "samsung", "galaxy", "apple", "bose", "noise",
        "oppo", "xiaomi", "redmi", "mi", "poco", "boult", "ptron", "nothing", "google", "pixel", "anker", "soundcore",
        "skullcandy", "jabra", "sennheiser", "beats", "marshall", "huawei", "honor", "vivo", "iqoo", "motorola", "moto",
        "lenovo", "amazfit", "fitbit", "garmin", "fire-boltt", "fireboltt", "zebronics", "zeb", "harman", "kardon",
        "bang", "olufsen", "b&o", "technics", "panasonic", "philips", "skullcandy", "oraimo", "ant", "audio", "mivi",
        "ubon", "portronics", "infinity", "tribit", "edifier", "qcy", "haylou", "baseus", "ugreen", "cmf", "tozo"
    )
    private val filler = setOf("wireless", "bluetooth", "headphones", "headphone", "earbuds", "earphones", "speaker", "neckband", "bt", "le")
    // Brand words that are also part of a product name only when they are all that is left.
    private val splitter = Regex("""[\s_]+""")

    fun clean(raw: String): String {
        val original = raw.trim()
        if (original.isEmpty()) return "Bluetooth"
        val words = original.replace(Regex("""\([^)]*\)"""), " ").split(splitter).filter { it.isNotBlank() }
        if (words.isEmpty()) return original
        var rest = words
        // Leading brand words, as long as something is left.
        while (rest.size > 1 && rest.first().lowercase().trim('-', '.', ',') in brands) rest = rest.drop(1)
        val noFiller = rest.filter { it.lowercase() !in filler }
        if (noFiller.isNotEmpty()) rest = noFiller
        return rest.joinToString(" ")
    }

    /** [clean], then fitted to [limit] characters by dropping leading words. */
    fun pill(raw: String, limit: Int = FIT): String {
        var words = clean(raw).split(' ').filter { it.isNotEmpty() }
        while (words.size > 1 && words.joinToString(" ").length > limit) words = words.drop(1)
        val s = words.joinToString(" ")
        // A lone number or a scrap is no name: keep the first word of the cleaned name instead.
        val first = clean(raw).split(' ').first()
        val scrap = s.length < 4 || s.all { it.isDigit() }
        val out = if (scrap && first.length >= 4) first else s
        return if (out.length <= limit) out else out.take(limit)
    }
}
