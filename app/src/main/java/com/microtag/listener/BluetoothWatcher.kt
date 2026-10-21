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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.microtag.core.Interceptions
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
 * The pill has little room, so the two facts take turns instead of sharing it: first the short name (brand dropped,
 * see [BtName]), then, a few seconds later, the battery as its own "80%" chip. Later battery changes only show when
 * the battery is low (20, 15, 10 and 5 percent).
 */
object BluetoothWatcher {
    private const val ID = 7_300_003
    private const val ACTION_BATTERY = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    private const val EXTRA_BATTERY = "android.bluetooth.device.extra.BATTERY_LEVEL"
    private const val BATTERY_WINDOW_MS = 12_000L
    private const val DEBOUNCE_MS = 4_000L
    private const val NAME_HOLD_MS = 3_500L
    private val main = Handler(Looper.getMainLooper())
    private val pending = HashMap<String, Runnable>()
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
        pending.values.forEach { main.removeCallbacks(it) }
        pending.clear()
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
                    show(ctx, address, nameOf(device), null, low = false)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    connectedAt.remove(address)
                    lastBattery.remove(address)
                    pending.remove(address)?.let { main.removeCallbacks(it) }
                    Interceptions.end("bt:$address")
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
                    if (crossedLow) {
                        pending.remove(address)?.let { main.removeCallbacks(it) }
                        show(ctx, address, nameOf(device), pct, low = true)
                    } else if (justConnected) {
                        // Let the name be read first, then the battery takes the chip.
                        val wait = (NAME_HOLD_MS - (now - (connectedAt[address] ?: now))).coerceAtLeast(0L)
                        val name = nameOf(device)
                        pending.remove(address)?.let { main.removeCallbacks(it) }
                        val task = Runnable { pending.remove(address); show(ctx, address, name, pct, low = false) }
                        pending[address] = task
                        main.postDelayed(task, wait)
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

    /** [battery] null shows the name; otherwise the battery chip, titled with the name. */
    private fun show(ctx: Context, address: String, name: String, battery: Int?, low: Boolean) {
        val prefs = MicrotagPrefs(ctx)
        if (!prefs.isAppEnabled(TagKeys.BLUETOOTH)) return
        if (TagSettings(ctx).quietReason() != null) return
        val pill = if (battery == null) BtName.pill(name) else "$battery%"
        val full = BtName.clean(name)
        shownFor = address
        // One interception per connection: the name, the battery and any low warning are all part of it.
        val key = "bt:$address"
        Interceptions.touch(key)
        if (!Interceptions.isOpen(key)) {
            Interceptions.record(ctx, key, "com.android.bluetooth", "BLUETOOTH")
            MomentsWatcher.intercepted(ctx)
        }
        MicrotagReminder.showCapsule(
            context = ctx,
            pillText = pill,
            title = full,
            content = when {
                battery == null -> "Connected"
                low -> "Battery low · $battery%"
                else -> "Battery $battery%"
            },
            notificationId = ID,
            timeoutSeconds = if (battery == null) 4 else 6,
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
