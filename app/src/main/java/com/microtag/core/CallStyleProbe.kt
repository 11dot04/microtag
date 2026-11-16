package com.microtag.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.microtag.R

/**
 * A throwaway test: does the system treat a CallStyle notification from this app as a real call?
 *
 * It posts the same fake ongoing Discord call two ways, so the difference is visible:
 *   PLAIN  an ordinary notify() from the app, as the capsules are posted today.
 *   FGS    the same notification from a foreground service (type specialUse).
 * After posting it reads the notification back from the system and says what it found in a toast.
 *
 * What to look for on the phone: an ongoing-call chip with a running timer in the status bar, the call layout
 * (caller name, timer, Hang up button) in the shade and on the lock screen, and whether the ColorOS capsule shows.
 * Hang up (or the third Settings row) ends it. Delete this file, the service and the Settings block when done.
 */
object CallStyleProbe {
    const val ACTION_FGS = "com.microtag.callprobe.FGS"
    const val ACTION_STOP = "com.microtag.callprobe.STOP"
    const val ACTION_MUTE = "com.microtag.callprobe.MUTE"
    const val ACTION_DEAFEN = "com.microtag.callprobe.DEAFEN"
    internal const val ID = 7_300_900
    private const val CHANNEL = "microtag_callstyle_probe"
    private const val TAG = "CallProbe"

    @Volatile internal var muted = false
    @Volatile internal var deafened = false
    /** True while the probe notification is held by the foreground service. */
    @Volatile internal var foreground = false

    /** Variant A: a plain notify(), no foreground service. */
    fun postPlain(context: Context) {
        val app = context.applicationContext
        muted = false; deafened = false; foreground = false
        val n = build(app) ?: return unsupported(app)
        nm(app).notify(ID, n)
        report(app, "PLAIN")
    }

    /** Variant B: the same notification from a foreground service. */
    fun postForeground(context: Context) {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT < 31) return unsupported(app)
        muted = false; deafened = false
        app.startForegroundService(Intent(app, CallProbeService::class.java).setAction(ACTION_FGS))
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        nm(app).cancel(ID)
        app.stopService(Intent(app, CallProbeService::class.java))
    }

    internal fun build(context: Context): Notification? {
        if (Build.VERSION.SDK_INT < 31) return null
        val nm = nm(context)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "CallStyle test", NotificationManager.IMPORTANCE_HIGH))
        }
        val person = Person.Builder().setName("Discord test call").setImportant(true).build()
        val hangUp = PendingIntent.getService(
            context, 1,
            Intent(context, CallProbeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Discord's own buttons show the NEXT state ("Unmute"), so the probe does the same.
        fun act(code: Int, action: String, label: String) = Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_capsule_bolt), label,
            PendingIntent.getService(
                context, code, Intent(context, CallProbeService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        ).build()
        val state = when { deafened -> "Deafened"; muted -> "Muted"; else -> "Live" }
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_capsule_chat)
            .setContentTitle("Discord test call")
            .setContentText(state)
            .addAction(act(2, ACTION_MUTE, if (muted) "Unmute" else "Mute"))
            .addAction(act(3, ACTION_DEAFEN, if (deafened) "Undeafen" else "Deafen"))
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis())
            .setStyle(Notification.CallStyle.forOngoingCall(person, hangUp))
            .build()
    }

    /** Reads the notification back from the system and tells the person what it kept. */
    internal fun report(context: Context, variant: String) {
        // The system posts asynchronously, so look a moment later rather than straight away.
        Handler(Looper.getMainLooper()).postDelayed({ readBack(context, variant) }, 800)
    }

    private fun readBack(context: Context, variant: String) {
        val posted = nm(context).activeNotifications.firstOrNull { it.id == ID }?.notification
        val msg = if (posted == null) {
            "$variant: the system did not keep the notification"
        } else {
            val fgs = posted.flags and Notification.FLAG_FOREGROUND_SERVICE != 0
            val tmpl = posted.extras.getString(Notification.EXTRA_TEMPLATE)?.substringAfterLast('$') ?: "none"
            "$variant: fgs=$fgs template=$tmpl category=${posted.category} actions=${posted.actions?.size ?: 0}"
        }
        Log.i(TAG, msg)
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    private fun unsupported(context: Context) =
        Toast.makeText(context, "CallStyle needs Android 12 or newer", Toast.LENGTH_SHORT).show()

    private fun nm(context: Context) = context.getSystemService(NotificationManager::class.java)
}

/** Holds the probe notification as a foreground service, and ends it on Hang up. */
class CallProbeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == CallStyleProbe.ACTION_STOP) {
            CallStyleProbe.foreground = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            getSystemService(NotificationManager::class.java).cancel(CallStyleProbe.ID)
            stopSelf()
            return START_NOT_STICKY
        }
        if (action == CallStyleProbe.ACTION_MUTE || action == CallStyleProbe.ACTION_DEAFEN) {
            // Same as a real Discord button: flip the state and update the one notification in place.
            if (action == CallStyleProbe.ACTION_MUTE) CallStyleProbe.muted = !CallStyleProbe.muted
            else CallStyleProbe.deafened = !CallStyleProbe.deafened
            CallStyleProbe.build(this)?.let { getSystemService(NotificationManager::class.java).notify(CallStyleProbe.ID, it) }
            if (!CallStyleProbe.foreground) stopSelf()
            return START_NOT_STICKY
        }
        val n = CallStyleProbe.build(this)
        if (n == null) { stopSelf(); return START_NOT_STICKY }
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(CallStyleProbe.ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(CallStyleProbe.ID, n)
        }
        CallStyleProbe.foreground = true
        CallStyleProbe.report(this, "FGS")
        return START_NOT_STICKY
    }
}
