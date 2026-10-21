package com.microtag.inspect

/**
 * Moments: small collectibles the app notices by itself. Nothing is listed until the first one is found; after that a
 * quiet section appears and the rest stay as "???" until they happen. Pure logic, no Android.
 */
data class Moment(val id: String, val title: String, val pill: String, val blurb: String, val group: String)

data class Found(val first: Long, val times: Int)

object MomentsLogic {
    const val EPOCH = 1_800_000_000L
    val MILESTONES = listOf(100, 500, 1000, 2500, 5000, 10000)

    val ALL: List<Moment> = listOf(
        Moment("t1111", "Eleven eleven", "11:11", "Make a wish.", "On the clock"),
        Moment("t1221", "Mirror minute", "12:21", "The same both ways round.", "On the clock"),
        Moment("t1234", "Count up", "12:34", "One, two, three, four.", "On the clock"),
        Moment("t2222", "Twos", "22:22", "Four of the same, two apart.", "On the clock"),
        Moment("n100", "First hundred", "100", "A hundred things caught.", "A count"),
        Moment("n500", "Five hundred", "500", "Half a thousand.", "A count"),
        Moment("n1000", "One thousand", "1,000", "Four digits of caught things.", "A count"),
        Moment("n2500", "Twenty-five hundred", "2,500", "A quarter of ten thousand.", "A count"),
        Moment("n5000", "Five thousand", "5,000", "You really do use this.", "A count"),
        Moment("n10000", "Ten thousand", "10K", "Five digits.", "A count"),
        Moment("epoch", "1.8 billion", "1.8B", "The Unix clock reached 1,800,000,000.", "A very round second"),
        Moment("dot04", "Eleven dot oh four", "11.04", "You were here at 11:04 on the right day.", "Unmarked")
    )

    fun byId(id: String): Moment? = ALL.firstOrNull { it.id == id }

    /** Which time moments the wall clock is showing. [month] is 1-12. */
    fun clock(hour: Int, minute: Int, month: Int, day: Int): List<String> {
        val out = ArrayList<String>()
        if ((hour == 11 || hour == 23) && minute == 11) out.add("t1111")
        if (hour % 12 == 0 && minute == 21) out.add("t1221")
        if (hour % 12 == 0 && minute == 34) out.add("t1234")
        if (hour == 22 && minute == 22) out.add("t2222")
        // 11.04 read either way round: 11 April or 4 November, at 11:04.
        if (hour == 11 && minute == 4 && ((month == 4 && day == 11) || (month == 11 && day == 4))) out.add("dot04")
        return out
    }

    /** Every count milestone [total] has reached. */
    fun counts(total: Int): List<String> = MILESTONES.filter { total >= it }.map { "n$it" }

    fun epochReached(nowMs: Long): Boolean = nowMs / 1000L >= EPOCH

    /** Records [id]; true in the second slot when it was the first time. */
    fun record(s: Map<String, Found>, id: String, now: Long): Pair<Map<String, Found>, Boolean> {
        val old = s[id]
        return if (old == null) (s + (id to Found(now, 1))) to true
        else (s + (id to old.copy(times = old.times + 1))) to false
    }

    fun encode(s: Map<String, Found>): String =
        s.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value.first}:${it.value.times}" }

    fun decode(text: String?): Map<String, Found> {
        if (text.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, Found>()
        for (part in text.split(';')) {
            val f = part.split(':')
            if (f.size != 3) continue
            val t = f[1].toLongOrNull() ?: continue
            val n = f[2].toIntOrNull() ?: continue
            if (byId(f[0]) != null) out[f[0]] = Found(t, n)
        }
        return out
    }
}

/** Found moments, kept in the same store as the pill choices. */
object MomentsStore {
    private const val KEY = "MOMENTS"

    fun found(): Map<String, Found> = MomentsLogic.decode(PillConfig.store?.read(KEY))
    fun any(): Boolean = found().isNotEmpty()

    /** True when this was the first time [id] was found. */
    @Synchronized
    fun award(id: String, now: Long = System.currentTimeMillis()): Boolean {
        val (next, first) = MomentsLogic.record(found(), id, now)
        PillConfig.store?.write(KEY, MomentsLogic.encode(next))
        return first
    }
}
