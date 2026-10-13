package com.microtag.inspect

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Entry for the text-selection toolbar. It shows nothing and closes at once; the engine carries on in
 * the background and posts the pill. Because it never stays on screen, it can't pull the app forward.
 */
class ProcessTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        if (!text.isNullOrBlank()) {
            InspectEngine.inspect(applicationContext, text, Source.SELECTION)
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
