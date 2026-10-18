package com.microtag.listener

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.microtag.core.MicrotagPrefs
import com.microtag.core.MicrotagReminder
import com.microtag.ui.TagKeys
import com.microtag.ui.TagSettings

/**
 * Bluetooth connection and battery chips. Notifications from the Bluetooth app were never a dependable source,
 * so this listens for the system's own broadcasts: a device connecting or disconnecting, and an accessory
 * reporting its battery (earbuds, headphones, watches).
 *
 * Needs the BLUETOOTH_CONNECT permission on Android 12+: without it the device name is hidden and the
 * battery broadcast is not delivered. The dashboard tile asks for it; Shizuku grants it when available.
 *
 * On connect: the device name. If it reports a battery within [BATTERY_WINDOW_MS] the same chip becomes "name 80%".
 * Later battery changes only show when the battery is low (20, 15, 10 and 5 percent).
 */
object BluetoothWatcher {
    private const val ID = 7_300_003
    private const val ACTION_BATTERY = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    private const val EXTRA_BATTERY = "android.bluetooth.device.extra.BATTERY_LEVEL"
    private const val BATTERY_WINDOW_MS = 12_000L
    private const val DEBOUNCE_MS = 4_000L
    private val LOW_STEPS = intArrayOf(20, 15, 10, 5)

    private var registered = false
    private var app: Context? = null
    private val connectedAt = HashMap<String, Long>()
    private val lastBattery = HashMap<String, Int>()
    private var shownFor: String? = null

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Starts or stops listening to match the dashboard tile and the permission. Safe to call any time. */
    @Synchronized
    fun refresh(context: Context) {
        val ctx = context.applicationContext
        val want = MicrotagPrefs(ctx).isAppEnabled(TagKeys.BLUETOOTH) && hasPermission(ctx)
        if (want && !registered) {
            app = ctx
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(ACTION_BATTERY)
            }
            ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            registered = true
        } else if (!want && registered) {
            stop()
        }
    }

    @Synchronized
    fun stop() {
        val ctx = app ?: return
        if (registered) {
            try { ctx.unregisterReceiver(receiver) } catch (_: Exception) {}
            registered = false
        }
        MicrotagReminder.cancelCapsule(ctx, ID)
        connectedAt.clear()
        lastBattery.clear()
        shownFor = null
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val ctx = context.applicationContext
            val device = deviceOf(intent) ?: return
            val address = try { device.address } catch (_: SecurityException) { return }
            val now = System.currentTimeMillis()

            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    if (now - (connectedAt[address] ?: 0L) < DEBOUNCE_MS) return
                    connectedAt[address] = now
                    lastBattery.remove(address)
                    show(ctx, address, nameOf(device), "Connected", null)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    connectedAt.remove(address)
                    lastBattery.remove(address)
                    if (shownFor == address) {
                        MicrotagReminder.cancelCapsule(ctx, ID)
                        shownFor = null
                    }
                }
                ACTION_BATTERY -> {
                    val pct = intent.getIntExtra(EXTRA_BATTERY, -1)
                    if (pct !in 0..100) return
                    val previous = lastBattery[address]
                    lastBattery[address] = pct
                    val justConnected = now - (connectedAt[address] ?: 0L) < BATTERY_WINDOW_MS
                    val crossedLow = previous != null && LOW_STEPS.any { previous > it && pct <= it }
                    if (justConnected || crossedLow) {
                        show(ctx, address, nameOf(device), if (crossedLow) "Battery low" else "Connected", pct)
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun deviceOf(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as? BluetoothDevice

    private fun nameOf(device: BluetoothDevice): String = try {
        (if (Build.VERSION.SDK_INT >= 30) device.alias else null) ?: device.name ?: "Bluetooth"
    } catch (_: SecurityException) {
        "Bluetooth"
    }

    private fun show(ctx: Context, address: String, name: String, status: String, battery: Int?) {
        val prefs = MicrotagPrefs(ctx)
        if (!prefs.isAppEnabled(TagKeys.BLUETOOTH)) return
        if (TagSettings(ctx).quietReason() != null) return
        // Chip text is short: the name shrinks to leave room for the percentage.
        val pill = if (battery == null) name.take(10) else "${name.take(8)} $battery%"
        shownFor = address
        prefs.incrementInterceptCount()
        MicrotagReminder.showCapsule(
            context = ctx,
            pillText = pill,
            title = name,
            content = if (battery == null) status else "$status · battery $battery%",
            notificationId = ID,
            timeoutSeconds = 6,
            iconName = "ic_earbuds",
            contentIntent = settingsIntent(ctx),
            showAppName = false
        )
    }

    private fun settingsIntent(ctx: Context): PendingIntent? = try {
        PendingIntent.getActivity(
            ctx, 7104, Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    } catch (_: Exception) {
        null
    }
}
