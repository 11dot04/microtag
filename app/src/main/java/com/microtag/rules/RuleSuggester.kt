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

    // ---------------------------------------------------------------- smart presets

    /** A ready-made rule for one notification: what it would show, and the pattern, pill and field that make it. */
    data class Preset(val label: String, val regex: String, val pill: String, val field: UserRule.Field, val explain: String, val value: String)

    private const val MAX_PRESETS = 6

    private class Kind(
        val label: String, val regex: String, val pill: String = "$1",
        val field: UserRule.Field = UserRule.Field.BOTH, val explain: String
    )

    /** Words that say a thing's state: the pill shows whichever one the notification used. */
    private const val STATUS_WORDS = "out for delivery|delivered|arriving|arrived|picked up|on the way|shipped|dispatched|preparing|" +
        "confirmed|cancell?ed|delayed|completed|failed|approved|declined|ready|connected|disconnected|paused|started|finished"

    // Order is the order they are offered in when nothing else ranks them.
    private val byContent = listOf(
        Kind("Status", """\b($STATUS_WORDS)\b""", explain = "Shows the state the notification names."),
        Kind("Amount", """((?:₹|Rs\.?|INR|USD|\$|€|£)\s?\d[\d,]*(?:\.\d+)?)""", explain = "Shows the money amount, with its currency."),
        Kind("Time left", """(\d+\s?(?:min(?:ute)?s?|hrs?|hours?|secs?|seconds?|days?))\b""", explain = "Shows how long, as the notification writes it."),
        Kind("Time of day", """(\d{1,2}:\d{2}(?:\s?[AaPp]\.?[Mm]\.?)?)""", explain = "Shows the clock time."),
        Kind("Percentage", """(\d{1,3}\s?%)""", explain = "Shows the percentage."),
        Kind("Distance", """(\d+(?:\.\d+)?\s?(?:km|kms|mi|miles?|meters?|metres?))\b""", explain = "Shows the distance."),
        Kind("Temperature", """(-?\d+(?:\.\d+)?\s?°\s?[CFcf]?)""", explain = "Shows the temperature."),
        Kind("Score", """(?<![\d:/-])(\d{1,3})\s?[-–]\s?(\d{1,3})(?![\d:/-])""", "$1-$2", explain = "Shows the two numbers as a score."),
        Kind("Step (x/y)", """(?<![\d/])(\d{1,4})\s?/\s?(\d{1,4})(?![\d/])""", "$1/$2", explain = "Shows how far along it is."),
        Kind("Count", """(\d+)\s+(?:new\s+|unread\s+)?(?:messages?|notifications?|emails?|mails?|items?|orders?|updates?|alerts?|reminders?|photos?|files?)""", explain = "Shows how many."),
        Kind("Name before the colon", """^([^:]{2,24}):""", field = UserRule.Field.TITLE, explain = "Shows the name the title starts with, up to the colon.")
    )

    /** What the system's own category says the notification is about: those presets come first. */
    private val categoryBoost = mapOf(
        "navigation" to listOf("Time left", "Distance"),
        "transport" to listOf("Time left", "Distance", "Status"),
        "progress" to listOf("Percentage", "Step (x/y)"),
        "email" to listOf("Count"),
        "status" to listOf("Status"),
        "event" to listOf("Time of day", "Status"),
        "reminder" to listOf("Time of day"),
        "workout" to listOf("Time left", "Distance"),
        "promo" to listOf("Amount")
    )

    private fun targetOf(field: UserRule.Field, title: String, text: String, shape: NotificationShape): String = when (field) {
        UserRule.Field.TITLE -> title
        UserRule.Field.TEXT -> text
        UserRule.Field.BOTH -> "$title $text".trim()
        UserRule.Field.SENDER -> shape.sender
        UserRule.Field.CONVERSATION -> shape.conversation
    }

    /** One preset, but only if it really gives something on this notification. */
    private fun check(label: String, regex: String, pill: String, field: UserRule.Field, explain: String, title: String, text: String, shape: NotificationShape): Preset? {
        val target = targetOf(field, title, text, shape)
        if (target.isBlank()) return null
        val rx = SafeRegex.compile(regex, true).getOrNull() ?: return null
        val found = SafeRegex.find(rx, target) as? SafeRegex.Outcome.Found ?: return null
        val value = RuleTemplate.expand(pill, found.groups).trim()
        if (value.isBlank()) return null
        return Preset(label, regex, pill, field, explain, value.take(36))
    }

    /**
     * Ready-made rules for this notification, most useful first. They come from two places: how it was posted
     * (a messenger's sender and group, a call's caller, a media track) and what its words contain (an amount, a time,
     * a percentage, a status). Each one is run on the notification itself and dropped unless it produces something,
     * so nothing is offered that would not work.
     */
    fun presets(title: String, text: String, shape: NotificationShape = NotificationShape.NONE): List<Preset> {
        val out = ArrayList<Preset>()
        fun add(p: Preset?) { if (p != null && out.none { it.label == p.label }) out.add(p) }
        fun now(label: String, regex: String, field: UserRule.Field, explain: String) =
            add(check(label, regex, "$1", field, explain, title, text, shape))

        // 1. By how it was posted.
        when (shape.template) {
            "MessagingStyle" -> {
                now("Who sent it", "(.+)", UserRule.Field.SENDER, "Shows the name of whoever sent the latest message.")
                if (shape.group) now("Group name", "(.+)", UserRule.Field.CONVERSATION, "Shows the name of the group chat.")
                now("Latest message", "(.+)", UserRule.Field.TEXT, "Shows the latest message, cut to fit.")
            }
            "CallStyle" -> now("Caller", "(.+)", UserRule.Field.SENDER, "Shows who is calling, or who the call is with.")
            "MediaStyle" -> {
                now("Track", "(.+)", UserRule.Field.TITLE, "Shows the track or video title.")
                now("Artist", "(.+)", UserRule.Field.TEXT, "Shows the artist or channel.")
            }
            "InboxStyle", "BigTextStyle", "BigPictureStyle" -> now("Headline", "(.+)", UserRule.Field.TITLE, "Shows the headline.")
        }

        // 2. By what the words say. A category the system gave puts its likeliest presets first.
        val boost = categoryBoost[shape.category.lowercase()].orEmpty()
        byContent.sortedBy { if (it.label in boost) 0 else 1 }.forEach { k ->
            add(check(k.label, k.regex, k.pill, k.field, k.explain, title, text, shape))
        }

        // 3. Always something to start from.
        now("Whole title", "(.+)", UserRule.Field.TITLE, "Shows the title, cut to fit.")
        now("First sentence", "^(.+?)(?:[.!?](?=\\s|$)|$)", UserRule.Field.TEXT, "Shows the first sentence of the text.")

        return out.take(MAX_PRESETS)
    }

    /** Does [regex] give a non-empty pill on every one of [samples]? Returns the samples it missed. */
    fun missed(regex: String, pill: String, samples: List<String>, ignoreCase: Boolean = true): List<String> {
        val rx = SafeRegex.compile(regex, ignoreCase).getOrNull() ?: return samples
        return samples.filter { s ->
            val f = SafeRegex.find(rx, s) as? SafeRegex.Outcome.Found
            f == null || RuleTemplate.expand(pill, f.groups).isBlank()
        }
    }
}
