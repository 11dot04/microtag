package com.microtag.ai

import com.microtag.listener.PillText
import com.microtag.rules.Redactor

/**
 * Shortens a notification for the pill with a model. Careful by design: it only ever sees redacted text, remembers
 * answers so the same notification never costs a second call, stops at an hourly limit, and gives up quickly.
 * Callers always have the local [PillText] result to show first; this only improves on it.
 */
class AiCondenser(
    private val completer: () -> Completer?,
    private val callsPerHour: () -> Int = { 30 },
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private val lock = Any()
    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 100
    }
    private val calls = ArrayDeque<Long>()

    companion object {
        const val SYSTEM = "You shorten a notification into the text of a tiny status-bar chip. Reply with ONLY the chip text: " +
            "no quotes, no explanation, one line. Keep the single most useful fact (an amount, a time, a count, a name). " +
            "Never invent anything that is not in the notification."

        fun prompt(app: String, title: String, text: String, limit: Int): String =
            "Maximum $limit characters.\nApp: $app\nTitle: ${Redactor.forModel(title)}\nText: ${Redactor.forModel(text)}"

        /** Makes a model reply safe to show: one line, no decoration, within the limit. */
        fun clean(reply: String, limit: Int): String? {
            val line = reply.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
            val s = line.trim('"', '\'', '`', '“', '”').trimEnd('.').trim()
            if (s.isEmpty()) return null
            return if (s.length <= limit) s else PillText.cut(s, limit)
        }
    }

    /** True when a call could be made right now. */
    fun available(): Boolean = completer() != null && withinLimit(peek = true)

    private fun withinLimit(peek: Boolean): Boolean = synchronized(lock) {
        val t = now()
        while (calls.isNotEmpty() && t - calls.first() > 3_600_000L) calls.removeFirst()
        val ok = calls.size < callsPerHour()
        if (ok && !peek) calls.addLast(t)
        ok
    }

    /** The model's short form, or null if it is off, limited, offline or said nothing usable. Blocking. */
    fun condense(app: String, title: String, text: String, limit: Int = PillText.LIMIT): String? {
        val c = completer() ?: return null
        val key = "$limit|${Redactor.forModel(title)}|${Redactor.forModel(text)}"
        synchronized(lock) { cache[key]?.let { return it } }
        if (!withinLimit(peek = false)) return null
        val r = c.complete(SYSTEM, prompt(app, title, text, limit), 60)
        val out = r.text?.let { clean(it, limit) } ?: return null
        synchronized(lock) { cache[key] = out }
        return out
    }
}
