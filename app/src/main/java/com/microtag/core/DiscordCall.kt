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
import android.os.IBinder
import android.util.Log
import com.microtag.R
import java.util.Locale

/**
 * A Discord voice session shown as a real call: a CallStyle notification held by a foreground service, which the
 * system draws as the call card and the status bar chip (caller, running timer, Hang up).
 *
 * Nothing here is invented. Hang up, Mute and Deafen are Discord's own buttons, copied from its notification with
 * their own PendingIntents, so tapping one drives Discord itself. When Discord changes its notification (muted,
 * deafened, reconnecting) the card is updated in place. When Discord removes it, the service ends and the card goes.
 *
 * [show] returns false whenever this cannot be done (Android older than 12, no Disconnect button to use as Hang up,
 * the system refusing to start the service). The caller then falls back to the ordinary capsule.
 */
object DiscordCall {
    private const val TAG = "DiscordCall"
    private const val CHANNEL = "microtag_discord_call"

    private val hangUpWords = setOf("disconnect", "leave", "leave call", "hang up", "end call", "end")

    private enum class Kind { HANGUP, MUTE, DEAFEN, OTHER }

    @Volatile private var currentId = 0
    @Volatile private var startedAt = 0L
    @Volatile private var pending: Notification? = null
    @Volatile private var running = false

    /**
     * Posts or updates the call card for the capsule [id]. [name] is who or where the call is, [status] what the
     * pill would have said ("On VC", "Muted"), [source] Discord's own action buttons and [tap] what its notification opens.
     */
    fun show(
        context: Context, id: Int, name: String, status: String,
        source: List<Notification.Action>, tap: PendingIntent?
    ): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        val app = context.applicationContext
        val hangUp = source.firstOrNull { kind(it) == Kind.HANGUP }?.actionIntent ?: return false
        if (currentId != id) startedAt = System.currentTimeMillis() // a new call starts its own timer
        val n = try {
            build(app, name, status, source, hangUp, tap)
        } catch (e: Exception) {
            Log.w(TAG, "Could not build the call card: ${e.message}")
            return false
        }
        if (running && currentId == id) {
            app.getSystemService(NotificationManager::class.java).notify(id, n)
            return true
        }
        return try {
            currentId = id
            pending = n
            app.startForegroundService(Intent(app, DiscordCallService::class.java))
            true
        } catch (e: Exception) {
            // The system may refuse to start a foreground service from the background. Use the ordinary capsule.
            Log.w(TAG, "Could not start the call service: ${e.message}")
            currentId = 0; startedAt = 0L; pending = null
            false
        }
    }

    /** Ends the call card if [id] is the call it is showing. Safe to call for any capsule id. */
    fun endIf(context: Context, id: Int) {
        if (id != currentId) return
        currentId = 0; startedAt = 0L; pending = null
        context.applicationContext.stopService(Intent(context.applicationContext, DiscordCallService::class.java))
    }

    // ---- used by the service ----
    internal fun takePending(): Notification? = pending.also { pending = null }
    internal fun id(): Int = currentId
    internal fun setRunning(on: Boolean) { running = on }

    // ---- building ----

    private fun kind(a: Notification.Action): Kind {
        val t = a.title?.toString()?.trim()?.lowercase(Locale.ROOT) ?: return Kind.OTHER
        // "Unmute" and "Undeafen" contain the plain word, so the order matters little; the words are what count.
        return when {
            "deafen" in t -> Kind.DEAFEN
            "mute" in t -> Kind.MUTE
            t in hangUpWords -> Kind.HANGUP
            else -> Kind.OTHER
        }
    }

    private fun build(
        context: Context, name: String, status: String,
        source: List<Notification.Action>, hangUp: PendingIntent, tap: PendingIntent?
    ): Notification {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Discord call", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null); enableVibration(false)
                }
            )
        }
        val person = Person.Builder().setName(name.ifBlank { "Discord" }).setImportant(true).build()
        val b = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_capsule_chat)
            .setContentTitle(name.ifBlank { "Discord" })
            .setContentText(status)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true) // a mute or deafen updates the card without it popping up again
            .setUsesChronometer(true)
            .setWhen(startedAt)
            .setStyle(Notification.CallStyle.forOngoingCall(person, hangUp))
        tap?.let { b.setContentIntent(it) }
        // The card has room for the built-in Hang up and two more buttons: Mute and Deafen, as Discord labels them.
        listOfNotNull(
            source.firstOrNull { kind(it) == Kind.MUTE },
            source.firstOrNull { kind(it) == Kind.DEAFEN }
        ).forEach { b.addAction(copy(it)) }
        return b.build()
    }

    /** Discord's own button: its label and its PendingIntent, with no icon so the card shows the words alone. */
    private fun copy(a: Notification.Action): Notification.Action =
        Notification.Action.Builder(null as Icon?, a.title, a.actionIntent).build()
}

/** Holds the call card as a foreground service for as long as the Discord session lasts. */
class DiscordCallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = DiscordCall.takePending()
        val id = DiscordCall.id()
        if (n == null || id == 0) { stopSelf(); return START_NOT_STICKY }
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(id, n)
        }
        DiscordCall.setRunning(true)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        DiscordCall.setRunning(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
