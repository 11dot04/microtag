package com.microtag.core

import android.content.Context
import java.io.File

/**
 * The one place that says what an interception is and how many there have been (the definition is on [InterceptionBook]).
 * Records live in filesDir/interceptions.tsv; the carried-over base and the open sessions in a small preferences file.
 * The home screen's counter is kept equal to the total, so what it shows and what Stats shows can never disagree.
 */
object Interceptions {
    private const val PREFS = "microtag_interceptions"
    private const val LEGACY_PREFS = "microtag_prefs"
    private const val LEGACY_KEY = "intercept_count"

    @Volatile private var book: InterceptionBook? = null

    fun init(context: Context) {
        if (book != null) return
        synchronized(this) {
            if (book != null) return
            val ctx = context.applicationContext
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val legacy = ctx.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            book = InterceptionBook(object : InterceptionBook.Env {
                override val dir: File = ctx.filesDir
                override fun getInt(key: String, def: Int) = prefs.getInt(key, def)
                override fun putInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
                override fun getString(key: String) = prefs.getString(key, "").orEmpty()
                override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
                override fun getLegacyCount() = legacy.getInt(LEGACY_KEY, 0)
                override fun putLegacyCount(value: Int) { legacy.edit().putInt(LEGACY_KEY, value).apply() }
            })
        }
    }

    private fun b(): InterceptionBook = book ?: error("Interceptions.init was not called")

    fun migrated(context: Context): Boolean { init(context); return b().migrated() }
    fun migrate(context: Context, history: List<InterceptionBook.Record>) { init(context); b().migrate(history) }
    fun record(context: Context, key: String, pkg: String, path: String): Int { init(context); return b().record(key, pkg, path) }

    fun records(): List<InterceptionBook.Record> = book?.records().orEmpty()
    fun total(): Int = book?.total() ?: 0
    fun isOpen(key: String): Boolean = book?.isOpen(key) ?: false
    fun touch(key: String) { book?.touch(key) }
    fun end(key: String) { book?.end(key) }
    fun debounced(tag: String, ms: Long): Boolean = book?.debounced(tag, ms) ?: true
}
