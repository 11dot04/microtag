package com.microtag.inspect

import android.app.Activity
import android.app.PendingIntent
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.microtag.R

/** Quick Settings tile: one tap pins whatever is on the clipboard. */
class PinTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = "Pin clipboard"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_pin)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        // A pin can hold something private, so on a locked phone the tile waits for the unlock first.
        if (isLocked) unlockAndRun { launch() } else launch()
    }

    private fun launch() {
        val intent = Intent(this, PinClipboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

/**
 * Android only lets an app read the clipboard while one of its windows has focus, so the tile opens this
 * invisible activity, reads the clipboard the moment it has focus, pins it and goes away.
 */
class PinClipboardActivity : Activity() {
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || done) return
        done = true
        val text = try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        } catch (_: Throwable) {
            null
        }
        val pinned = if (text.isNullOrBlank()) null else PinEngine.pin(applicationContext, text)
        Toast.makeText(this, if (pinned != null) "Pinned" else "Nothing on the clipboard to pin", Toast.LENGTH_SHORT).show()
        finish()
        overridePendingTransition(0, 0)
    }
}
