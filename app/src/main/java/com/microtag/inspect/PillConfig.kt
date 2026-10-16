package com.microtag.inspect

/**
 * What the status-bar pill says, one word per card. Change a value here and rebuild; nothing else moves.
 * (The dictionary has its own switch, DEFINE_PILL_STYLE_DEFAULT in DictionaryLookup.kt.)
 */
internal object PillConfig {
    /** WIKI: TITLE = the article name, SURNAME = last name for people / short name for places, CONTEXT = "mathematician", "in Spain". */
    const val WIKI = "CONTEXT"
    /** TIME: TIME = "00:30 +1d", TIME_ZONE = "00:30 IST", DAY = "tomorrow". */
    const val TIME = "TIME"
    /** DATE: RELATIVE = "in 136d", DATE = "28 Feb". */
    const val DATE = "RELATIVE"
    /** MONEY: LOCAL = "₹1,660", BOTH = "$20 = ₹1,660". */
    const val MONEY = "LOCAL"
    /** NUMBER: COMPACT = "3.1B", GROUPED = "3,100,000,000". */
    const val NUMBER = "COMPACT"
    /** DIMS: SIZE = "210×297mm", AREA = "623.7 cm²", STANDARD = "A4" when the size is a standard. */
    const val DIMS = "SIZE"
    /** TEXT: READ = "22s read", WORDS = "64 words". */
    const val TEXT = "READ"
    /** CALC: RESULT = "652.05", EQUATION = "567+15%=652.05". */
    const val CALC = "RESULT"
    /** IFSC: BANK = "HDFC Bank", CODE = "HDFC0000001", BRANCH = "Mumbai". */
    const val IFSC = "BANK"
}

/** Pill text for a Wikipedia article. Pure, so it is tested. */
internal object WikiPill {
    private val personMark = Regex("""\((?:born|b\.)\s|\(\d{3,4}\s*[–-]\s*\d{0,4}\)|\(c\.\s*\d""", RegexOption.IGNORE_CASE)
    private val suffixes = setOf("jr", "jr.", "sr", "sr.", "ii", "iii", "iv", "v", "phd", "md")
    private val placeHeads = setOf(
        "capital", "city", "town", "village", "municipality", "commune", "region", "province", "state", "county",
        "district", "island", "river", "mountain", "lake", "country", "territory", "borough", "suburb", "peninsula", "archipelago"
    )
    private val placeIn = Regex("""\b(?:in|of)\s+((?:the\s+)?[A-Z][\p{L}.'’-]*(?:\s+[A-Z][\p{L}.'’-]*)?)""")

    fun isPerson(description: String): Boolean = personMark.containsMatchIn(description)

    fun surname(title: String): String {
        val clean = title.substringBefore('(').trim().trimEnd(',')
        val words = clean.split(' ').filter { it.isNotEmpty() }.toMutableList()
        while (words.size > 1 && words.last().lowercase() in suffixes) words.removeAt(words.size - 1)
        return words.lastOrNull() ?: clean
    }

    /** "mathematician", "novel", "in Spain": one word of context from the article's short description. */
    fun context(description: String): String? {
        val d = description.replace(Regex("""\s*\([^)]*\)"""), "").trim()
        if (d.isEmpty()) return null
        val head = d.split(Regex("""\s+(?:and|of|in|by|from|who|that|which|for|at|on)\s+|,|;|\s+–\s+""")).first().trim()
        val headWords = head.split(' ').filter { it.isNotEmpty() }
        val last = headWords.lastOrNull()?.lowercase() ?: return null
        if (last in placeHeads) {
            placeIn.find(d)?.let { return "in " + it.groupValues[1].trim() }
        }
        return last.takeIf { it.length >= 3 }
    }

    fun pill(style: String, title: String, description: String, fit: (String, Int) -> String): String = when (style) {
        "TITLE" -> fit(title, 14)
        "SURNAME" -> if (isPerson(description)) surname(title) else fit(title.substringBefore(',').substringBefore('(').trim(), 14)
        else -> context(description) ?: fit(title, 14)
    }
}
