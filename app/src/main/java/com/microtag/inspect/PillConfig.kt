package com.microtag.inspect

/** Where chosen pill styles live. The app backs this with SharedPreferences (PillPrefs); tests leave it empty. */
internal interface PillStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
}

/**
 * What the status-bar pill says, one word per card. The defaults are here; the Inspect screen lets the user pick
 * another choice per card, and the pick is stored. Adding a choice is one line in [options] plus its branch in the detector.
 * (The pill text itself is built by each detector from the value returned here.)
 */
internal object PillConfig {
    class Choice(val value: String, val label: String)
    class Option(val key: String, val default: String, val choices: List<Choice>, val heading: String = "PILL SHOWS")

    @Volatile var store: PillStore? = null

    val options: List<Option> = listOf(
        // WIKI: CONTEXT = "mathematician" / "in Spain", SURNAME = last name (short name for places), TITLE = the article name.
        Option("WIKI", "CONTEXT", listOf(Choice("CONTEXT", "Context"), Choice("SURNAME", "Surname"), Choice("TITLE", "Title"))),
        // TIME: TIME = "00:30 +1d", TIME_ZONE = "00:30 IST", DAY = "tomorrow".
        Option("TIME", "TIME", listOf(Choice("TIME", "Time"), Choice("TIME_ZONE", "Time + zone"), Choice("DAY", "Day"))),
        // DATE: RELATIVE = "in 136d", DATE = "28 Feb".
        Option("DATE", "RELATIVE", listOf(Choice("RELATIVE", "Days away"), Choice("DATE", "Date"))),
        // MONEY: LOCAL = "₹1,660", BOTH = "$20 = ₹1,660".
        Option("MONEY", "LOCAL", listOf(Choice("LOCAL", "Local"), Choice("BOTH", "Both"))),
        // NUMBER: COMPACT = "3.1B", GROUPED = "3,100,000,000".
        Option("NUMBER", "COMPACT", listOf(Choice("COMPACT", "Short"), Choice("GROUPED", "Full"))),
        // DIMS: SIZE = "210×297mm", AREA = "623.7 cm²", STANDARD = "A4" when the size is a standard.
        Option("DIMS", "SIZE", listOf(Choice("SIZE", "Size"), Choice("AREA", "Area"), Choice("STANDARD", "Standard"))),
        // TEXT: READ = "22s read", WORDS = "64 words".
        Option("TEXT", "READ", listOf(Choice("READ", "Read time"), Choice("WORDS", "Words"))),
        // CALC: RESULT = "652.05", EQUATION = "567+15%=652.05".
        Option("CALC", "RESULT", listOf(Choice("RESULT", "Result"), Choice("EQUATION", "Equation"))),
        // IFSC: BANK = "HDFC Bank", CODE = "HDFC0000001", BRANCH = "Mumbai".
        Option("IFSC", "BANK", listOf(Choice("BANK", "Bank"), Choice("CODE", "Code"), Choice("BRANCH", "Branch"))),
        // DEFINE: GLOSS = a short gist of the meaning, IPA = how it sounds, WORD = the word itself.
        Option("DEFINE", "GLOSS", listOf(Choice("GLOSS", "Gist"), Choice("IPA", "Sound"), Choice("WORD", "Word"))),
        // PINPROMPT: OFF = never, ON = offer a "Pin it?" pill when a short code is copied (a pill you didn't ask for, so opt-in).
        Option("PINPROMPT", "OFF", listOf(Choice("OFF", "Off"), Choice("ON", "Offer to pin")), heading = "WHEN YOU COPY A CODE"),
        // PINEXPIRY: how long a new pin lasts. The status-bar chip only ever shows for the first 30 minutes (see PinLogic).
        Option("PINEXPIRY", "H1", listOf(Choice("M30", "30 min"), Choice("H1", "1 hour"), Choice("H3", "3 hours"), Choice("CLEARED", "Until cleared")), heading = "A PIN LASTS")
    )

    private val byKey = options.associateBy { it.key }

    fun option(key: String): Option? = byKey[key]

    fun get(key: String): String {
        val o = byKey[key] ?: return ""
        val v = try { store?.read(key) } catch (_: Exception) { null }
        return if (v != null && o.choices.any { it.value == v }) v else o.default
    }

    fun set(key: String, value: String) {
        try { store?.write(key, value) } catch (_: Exception) { }
    }

    val WIKI: String get() = get("WIKI")
    val TIME: String get() = get("TIME")
    val DATE: String get() = get("DATE")
    val MONEY: String get() = get("MONEY")
    val NUMBER: String get() = get("NUMBER")
    val DIMS: String get() = get("DIMS")
    val TEXT: String get() = get("TEXT")
    val CALC: String get() = get("CALC")
    val IFSC: String get() = get("IFSC")
    val DEFINE: String get() = get("DEFINE")
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
