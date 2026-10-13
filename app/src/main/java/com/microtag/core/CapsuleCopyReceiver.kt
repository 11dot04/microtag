package com.microtag.core

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import com.microtag.inspect.InspectEngine

/** Tap-to-copy for capsules whose whole point is one value (the OTP). Copies, then removes the capsule. */
class CapsuleCopyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (text.isNotBlank()) copy(context, text, sensitive = true)
        if (id != -1) MicrotagReminder.cancelCapsule(context, id)
    }

    companion object {
        const val EXTRA_TEXT = "text"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        /** Puts [text] on the clipboard and tells the clipboard watcher not to react to it. */
        fun copy(context: Context, text: String, sensitive: Boolean = false): Boolean {
            return try {
                InspectEngine.markSelfCopy(text)
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Microtag", text)
                if (sensitive && Build.VERSION.SDK_INT >= 33) {
                    clip.description.extras = PersistableBundle().apply {
                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                    }
                }
                cm.setPrimaryClip(clip)
                Toast.makeText(context.applicationContext, "Copied", Toast.LENGTH_SHORT).show()
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
