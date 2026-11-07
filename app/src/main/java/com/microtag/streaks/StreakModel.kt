package com.microtag.streaks

import java.io.File

/**
 * Streaks, the right way. One reader for every app (no pattern per app), and a small book that remembers each app's
 * streak, so a chip appears when something changed (a new day, a risk, a loss) and not on every repeated reminder.
 * Pure Kotlin, tested on the JVM.
 */
enum class StreakKind {
    /** "12-day streak": the streak is this long now. */
    COUNT,
    /** "Don't lose your streak", "ends in 3h". */
    AT_RISK,
    /** "You lost your streak". */
    LOST,
    /** "Longest streak: 40". The number is the record, not today's streak. */
    RECORD,
    /** "Streak freeze used": not a streak length. */
    FREEZE
}

data class StreakEvent(val kind: StreakKind, val count: Int?, val hoursLeft: Int?)

object StreakParse {
    private val streakWord = Regex("""(?i)\bstreaks?\b|in a row|consecutive|🔥""")

    // "12-day streak", "12 day streak", "streak of 12 days", "streak: 12", "🔥 12", "12 🔥", "12 days in a row"
    private val counts = listOf(
        Regex("""(?i)(\d{1,5})\s*[- ]?\s*(?:day|days|d|week|weeks|wk)\s*[- ]?\s*streak"""),
        Regex("""(?i)streak\s*(?:of|is|at|:|=|-|–)?\s*(\d{1,5})\b"""),
        Regex("""🔥\s*(\d{1,5})"""),
        Regex("""(\d{1,5})\s*🔥"""),
        Regex("""(?i)(\d{1,5})\s*(?:days?|weeks?)\s*(?:in a row|straight|running)"""),
        Regex("""(?i)(?:on|reached|hit|extended to|now)\s+(?:a\s+)?(\d{1,5})\s*[- ]?\s*(?:days?|weeks?)\b""")
    )

    private val lost = Regex("""(?i)(?:lost|broke|broken|ended|reset|missed)\s+(?:your\s+|the\s+|a\s+)?(?:\d+[- ]?days?\s+)?streak|streak\s+(?:was\s+)?(?:lost|broken|ended|reset)|you broke""")
    private val risk = Regex("""(?i)at risk|in danger|don'?t (?:lose|break|miss)|about to (?:lose|end|break)|(?:ends?|expires?|ending|running out) (?:in|soon|tonight|today)|last chance|keep your streak|save your streak|protect your streak|still time|hours? left|before midnight|today to keep""")
    private val record = Regex("""(?i)(?:longest|best|record|personal best|all[- ]time|highest)\s+(?:current\s+)?streak|streak\s+record|new record""")
    private val freeze = Regex("""(?i)streak\s+(?:freeze|repair|shield|saver|protect)|freeze[sd]?\b|repair\b""")
    private val hours = listOf(
        Regex("""(?i)(\d{1,2})\s*(?:hours?|hrs?|h)\s*(?:left|remaining|to go)"""),
        Regex("""(?i)(?:ends?|expires?|ending|runs? out)\s+in\s+(\d{1,2})\s*(?:hours?|hrs?|h)\b""")
    )

    fun parse(title: String, text: String): StreakEvent? {
        val all = "$title. $text".replace(Regex("""\s+"""), " ")
        if (!streakWord.containsMatchIn(all)) return null
        val n = counts.asSequence().mapNotNull { it.find(all)?.groupValues?.get(1)?.toIntOrNull() }.firstOrNull()
        val h = hours.asSequence().mapNotNull { it.find(all)?.groupValues?.get(1)?.toIntOrNull() }.firstOrNull()
        return when {
            freeze.containsMatchIn(all) && !lost.containsMatchIn(all) && n == null -> StreakEvent(StreakKind.FREEZE, null, null)
            record.containsMatchIn(all) && !risk.containsMatchIn(all) -> StreakEvent(StreakKind.RECORD, n, null)
            lost.containsMatchIn(all) -> StreakEvent(StreakKind.LOST, n, null)
            risk.containsMatchIn(all) -> StreakEvent(StreakKind.AT_RISK, n, h)
            n != null -> StreakEvent(StreakKind.COUNT, n, null)
            else -> null
        }
    }
}

/** What to show: [pill] for the chip, [title] and [content] for the card, [urgent] for a longer stay. [day] keys the once-a-day count. */
data class StreakShow(val pill: String, val title: String, val content: String, val urgent: Boolean, val day: Long)

/** Days, with the day starting at [ROLLOVER_HOUR] (a late-night session still belongs to the day before). */
object StreakDays {
    const val ROLLOVER_HOUR = 4
    fun dayOf(nowMs: Long, zoneOffsetMs: Long): Long = Math.floorDiv(nowMs + zoneOffsetMs - ROLLOVER_HOUR * 3_600_000L, 86_400_000L)
}

/**
 * Per-app streak memory. Persisted through [Env] (SharedPreferences on Android, a map in tests).
 * A COUNT shows when the number changed or it is a new day; an AT_RISK shows at most twice a day and never after the app
 * already counted today; a LOST shows once; a RECORD only updates the best; a FREEZE is ignored.
 */
class StreakBook(private val env: Env) {
    interface Env {
        fun get(key: String): String?
        fun put(key: String, value: String)
    }

    private class State(
        var count: Int = 0, var best: Int = 0, var lastDay: Long = Long.MIN_VALUE,
        var shownDay: Long = Long.MIN_VALUE, var shownCount: Int = -1, var risksToday: Int = 0, var riskDay: Long = Long.MIN_VALUE,
        var lostDay: Long = Long.MIN_VALUE
    ) {
        fun encode() = listOf(count, best, lastDay, shownDay, shownCount, risksToday, riskDay, lostDay).joinToString(",")
        companion object {
            fun decode(s: String?): State {
                val f = s?.split(',') ?: return State()
                if (f.size != 8) return State()
                return State(f[0].toIntOrNull() ?: 0, f[1].toIntOrNull() ?: 0, f[2].toLongOrNull() ?: Long.MIN_VALUE,
                    f[3].toLongOrNull() ?: Long.MIN_VALUE, f[4].toIntOrNull() ?: -1, f[5].toIntOrNull() ?: 0,
                    f[6].toLongOrNull() ?: Long.MIN_VALUE, f[7].toLongOrNull() ?: Long.MIN_VALUE)
            }
        }
    }

    companion object {
        const val MAX_RISKS_PER_DAY = 2
        private fun key(pkg: String) = "streak:$pkg"
    }

    private val lock = Any()

    fun onEvent(pkg: String, ev: StreakEvent, nowMs: Long, zoneOffsetMs: Long): StreakShow? = synchronized(lock) {
        val today = StreakDays.dayOf(nowMs, zoneOffsetMs)
        val s = State.decode(env.get(key(pkg)))
        val shown: StreakShow? = when (ev.kind) {
            StreakKind.FREEZE -> null
            StreakKind.RECORD -> { ev.count?.let { s.best = maxOf(s.best, it) }; null }
            StreakKind.COUNT -> {
                val c = ev.count ?: return@synchronized null
                val reset = c < s.count && c <= 1 && s.count > 1
                val same = c == s.shownCount && s.shownDay == today
                val fresh = s.lastDay != today || c != s.count
                s.count = c; s.best = maxOf(s.best, c); s.lastDay = today
                if (same && !fresh) null else {
                    s.shownDay = today; s.shownCount = c
                    StreakShow(
                        pill = "${c}d", title = if (reset) "New streak" else "$c-day streak",
                        content = bestLine(s) , urgent = false, day = today
                    )
                }
            }
            StreakKind.AT_RISK -> {
                // The app already counted today: a risk notice now is stale.
                if (s.lastDay == today) null else {
                    if (s.riskDay != today) { s.riskDay = today; s.risksToday = 0 }
                    if (s.risksToday >= MAX_RISKS_PER_DAY) null else {
                        s.risksToday++
                        val c = ev.count ?: s.count.takeIf { it > 0 && s.lastDay >= today - 1 }
                        val left = ev.hoursLeft
                        val pill = when {
                            c != null && left != null -> "${c}d · ${left}h"
                            c != null -> "${c}d !"
                            left != null -> "${left}h left"
                            else -> "Streak !"
                        }
                        StreakShow(pill, "Streak at risk", listOfNotNull(c?.let { "$it days" }, left?.let { "ends in ${it}h" }, bestLine(s).takeIf { s.best > 0 }).joinToString(" · ").ifBlank { "Do it today to keep it" }, urgent = true, day = today)
                    }
                }
            }
            StreakKind.LOST -> {
                if (s.lostDay == today) null else {
                    val was = ev.count ?: s.count
                    s.best = maxOf(s.best, was); s.count = 0; s.lostDay = today
                    StreakShow(if (was > 0) "Lost ${was}d" else "Streak lost", "Streak lost", if (was > 0) "It was $was days. Best: ${s.best}" else "Start again today", urgent = false, day = today)
                }
            }
        }
        env.put(key(pkg), s.encode())
        shown
    }

    /** The streak as last seen for [pkg]: its length and best, or null when nothing was seen. */
    fun peek(pkg: String): Pair<Int, Int>? = synchronized(lock) {
        val s = State.decode(env.get(key(pkg)))
        if (s.best == 0 && s.count == 0) null else s.count to s.best
    }

    private fun bestLine(s: State) = if (s.best > s.count) "Best: ${s.best} days" else if (s.count > 0 && s.best == s.count && s.count >= 7) "Your longest yet" else "Keep it going"
}

/** Plain files for tests and for Android alike: one tiny file per key. */
class FileStreakEnv(private val dir: File) : StreakBook.Env {
    private fun f(key: String) = File(dir, key.replace(Regex("""[^\w.]"""), "_") + ".streak")
    override fun get(key: String): String? = runCatching { f(key).takeIf { it.exists() }?.readText() }.getOrNull()
    override fun put(key: String, value: String) { runCatching { f(key).writeText(value) } }
}
