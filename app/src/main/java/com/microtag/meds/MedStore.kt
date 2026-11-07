package com.microtag.meds

import android.content.Context
import java.time.ZoneId

/** Process-wide access to the medicine book, and the one place that decides what to show. */
object MedStore {
    @Volatile private var book: MedBook? = null
    const val PREFS = "microtag_meds"
    const val KEY_REFILL_DAY = "refill_day"

    fun book(context: Context): MedBook =
        book ?: synchronized(this) { book ?: MedBook(context.applicationContext.filesDir).also { book = it } }

    fun zone(): ZoneId = ZoneId.systemDefault()

    /** Once a day at most: the medicines that are running low. */
    fun refillNotice(context: Context, nowMs: Long): List<Medication> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = MedLogic.today(nowMs, zone())
        if (p.getLong(KEY_REFILL_DAY, -1L) == today) return emptyList()
        val low = book(context).all().filter { MedLogic.refillDue(it) }
        if (low.isNotEmpty()) p.edit().putLong(KEY_REFILL_DAY, today).apply()
        return low
    }
}
