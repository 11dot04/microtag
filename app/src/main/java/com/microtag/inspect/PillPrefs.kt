package com.microtag.inspect

import android.content.Context
import android.content.SharedPreferences

/** SharedPreferences behind [PillConfig]. Attached on first use by the engine and by the Inspect screen. */
internal object PillPrefs : PillStore {
    @Volatile private var prefs: SharedPreferences? = null

    fun attach(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("microtag_inspect_pills", Context.MODE_PRIVATE)
        PillConfig.store = this
    }

    override fun read(key: String): String? = prefs?.getString(key, null)

    override fun write(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }
}
