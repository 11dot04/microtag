package com.microtag.inspect

/** A pinned piece of text. [expiresAt] is 0 when the pin lasts until the user clears it. */
data class Pin(val id: Int, val text: String, val createdAt: Long, val expiresAt: Long)

/**
 * The rules of pinning, with no Android in them so they are tested on the JVM.
 *
 * A pin has two lengths. It LIVES for as long as the user chose (30 min to until cleared). It only sits on the
 * status bar as a chip for the first [CHIP_WINDOW_MS]; after that it carries on as an ordinary ongoing notification
 * in the shade. Android's guidance for Live Updates is short, pressing, user-started activity, so the chip is
 * kept short whatever the pin's own lifetime is.
 */
object PinLogic {
    const val CHIP_WINDOW_MS = 30 * 60_000L
    const val ID_BASE = 9100
    const val MAX_PINS = 6
    const val MAX_TEXT = 200

    class Expiry(val key: String, val label: String, val ms: Long)

    val expiries = listOf(
        Expiry("M30", "30 min", 30 * 60_000L),
        Expiry("H1", "1 hour", 60 * 60_000L),
        Expiry("H3", "3 hours", 3 * 60 * 60_000L),
        Expiry("CLEARED", "Until cleared", 0L)
    )

    fun expiryByKey(key: String): Expiry = expiries.firstOrNull { it.key == key } ?: expiries[1]

    fun expiresAt(now: Long, e: Expiry): Long = if (e.ms <= 0L) 0L else now + e.ms

    fun isExpired(p: Pin, now: Long): Boolean = p.expiresAt in 1..now

    fun inChipWindow(p: Pin, now: Long): Boolean = now - p.createdAt < CHIP_WINDOW_MS

    /** Adds [ms] to a timed pin. A pin that lasts until cleared has nothing to extend. */
    fun extend(p: Pin, now: Long, ms: Long): Pin =
        if (p.expiresAt == 0L) p else p.copy(expiresAt = maxOf(p.expiresAt, now) + ms)

    /** The text the chip carries: whitespace collapsed, at most 12 characters. */
    fun chipText(text: String): String {
        val t = text.trim().replace(Regex("\\s+"), " ")
        return if (t.length <= 12) t else t.take(11).trimEnd() + "…"
    }

    /** The first free notification slot, or the oldest pin's slot when all [MAX_PINS] are taken. */
    fun nextId(pins: List<Pin>): Int {
        val used = pins.map { it.id }.toSet()
        for (i in 0 until MAX_PINS) if (ID_BASE + i !in used) return ID_BASE + i
        return pins.minByOrNull { it.createdAt }?.id ?: ID_BASE
    }

    private val codeChars = Regex("""^[A-Za-z0-9][A-Za-z0-9 ._#/-]*$""")

    /**
     * Would copied text be worth offering to pin? A short code or label with both a letter and a digit
     * ("B2-41", "Gate 23", "AB1234"). Never plain numbers: those are OTPs, card or phone numbers.
     */
    fun looksPinnable(text: String): Boolean {
        val t = text.trim()
        if (t.length !in 3..20) return false
        if (!codeChars.matches(t)) return false
        if (t.split(' ').size > 2) return false
        if (t.none { it.isDigit() } || t.none { it.isLetter() }) return false
        if (t.contains("//") || t.contains("www.", true)) return false
        return true
    }

    // ---- storage format: one pin per line, "id|created|expires|text" with the text escaped ----

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n")

    private fun unesc(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'p' -> sb.append('|')
                    'n' -> sb.append('\n')
                    else -> sb.append(s[i + 1])
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    fun encode(pins: List<Pin>): String = pins.joinToString("\n") { "${it.id}|${it.createdAt}|${it.expiresAt}|${esc(it.text)}" }

    fun decode(raw: String): List<Pin> = raw.lines().mapNotNull { line ->
        val parts = line.split('|', limit = 4)
        if (parts.size < 4) return@mapNotNull null
        val id = parts[0].toIntOrNull() ?: return@mapNotNull null
        val c = parts[1].toLongOrNull() ?: return@mapNotNull null
        val e = parts[2].toLongOrNull() ?: return@mapNotNull null
        val text = unesc(parts[3])
        if (text.isBlank()) null else Pin(id, text, c, e)
    }
}
