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
        // PROCESS_TEXT comes from the selection toolbar; SEND comes from the share sheet, which nearly every app has.
        val text = (intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT))?.toString()
        if (!text.isNullOrBlank()) {
            InspectEngine.inspect(applicationContext, text, Source.SELECTION)
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
