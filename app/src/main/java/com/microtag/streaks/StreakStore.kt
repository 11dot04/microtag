package com.microtag.streaks

import android.content.Context
import java.util.TimeZone

/** The streak book on Android: one preferences file, one entry per app. */
object StreakStore {
    private const val PREFS = "microtag_streaks"
    @Volatile private var book: StreakBook? = null

    private fun book(context: Context): StreakBook {
        book?.let { return it }
        synchronized(this) {
            book?.let { return it }
            val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val b = StreakBook(object : StreakBook.Env {
                override fun get(key: String): String? = sp.getString(key, null)
                override fun put(key: String, value: String) { sp.edit().putString(key, value).apply() }
            })
            book = b
            return b
        }
    }

    /** What the listener calls for a notification from a picked streak app. Null means: nothing new, show nothing. */
    fun onNotification(context: Context, pkg: String, title: String, text: String, now: Long = System.currentTimeMillis()): StreakShow? {
        val ev = StreakParse.parse(title, text) ?: return null
        return book(context).onEvent(pkg, ev, now, TimeZone.getDefault().getOffset(now).toLong())
    }

    /** True when the text reads as a streak notification at all, so the caller can tell "nothing new" from "not a streak". */
    fun reads(title: String, text: String): Boolean = StreakParse.parse(title, text) != null

    fun peek(context: Context, pkg: String): Pair<Int, Int>? = book(context).peek(pkg)
}
