package com.microtag.inspect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.microtag.core.MicrotagReminder

/** Buttons on a pin's notification, the "Pin it?" offer, and the alarm that ends a pin's chip window. */
class PinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val id = intent.getIntExtra(PinEngine.EXTRA_ID, -1)
        try {
            when (intent.action) {
                PinEngine.ACTION_PIN -> {
                    intent.getStringExtra(PinEngine.EXTRA_TEXT)?.let { PinEngine.pin(app, it) }
                    MicrotagReminder.cancelCapsule(app, PinEngine.PROMPT_ID)
                }
                PinEngine.ACTION_UNPIN -> PinEngine.unpin(app, id)
                PinEngine.ACTION_EXTEND -> PinEngine.extend(app, id, 60 * 60_000L)
                PinEngine.ACTION_COPY -> PinEngine.copyPin(app, id)
                PinEngine.ACTION_DEMOTE -> PinEngine.demote(app, id)
            }
        } catch (t: Throwable) {
            Log.w("MicrotagPin", "${intent.action} failed: ${t.message}")
        }
    }
}
