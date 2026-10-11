package com.microtag.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.microtag.R
import com.microtag.inspect.InspectPayload
import com.microtag.inspect.ProcessTextActivity
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

object MicrotagReminder {
    private const val TAG = "MicrotagReminder"
    private const val CHANNEL_ID = "microtag_live_capsules_v3"
    private const val CHANNEL_NAME = "Live Status Capsules"

    // Android 16 (API 36) Live Update extras
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
    private const val EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText"

    // Engine to manage per-minute chip updates for calendar/events without IPC spam
    private val handler = Handler(Looper.getMainLooper())
    private val tickerTasks = ConcurrentHashMap<Int, Runnable>()

    fun cancelTicker(notificationId: Int) {
        tickerTasks.remove(notificationId)?.let { handler.removeCallbacks(it) }
    }

    /** Removes a capsule and stops its minute ticker. */
    fun cancelCapsule(context: Context, notificationId: Int) {
        cancelTicker(notificationId)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)
    }

    /** 15 -> "15m", 60 -> "1h", 75 -> "1h 15m". */
    private fun formatMinutes(mins: Int): String = when {
        mins < 60 -> "${mins}m"
        mins % 60 == 0 -> "${mins / 60}h"
        else -> "${mins / 60}h ${mins % 60}m"
    }

    fun InspectPayload(
        domain: String = "",
        title: String = "",
        subtitle: String = "",
        fullContent: String = "",
        copyText: String = "",
        primaryText: String = "",
        secondaryText: String = "",
        actionType: String = "COPY",
        copyContent: String = "",
        extraData: String = ""
    ): InspectPayload = com.microtag.inspect.InspectPayload(
        domain = domain,
        title = title,
        subtitle = subtitle,
        fullContent = fullContent,
        copyText = copyText,
        primaryText = primaryText,
        secondaryText = secondaryText,
        actionType = actionType,
        copyContent = copyContent,
        extraData = extraData
    )

    fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Live Updates and ongoing activity status capsules"
            setShowBadge(true)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun showCapsule(
        context: Context,
        pillText: String,
        title: String,
        content: String,
        notificationId: Int,
        timeoutSeconds: Int = 0,
        iconName: String? = null,
        customIcon: Icon? = null,
        detailPayload: InspectPayload? = null,
        chronometerTargetMillis: Long? = null,
        actions: List<Notification.Action> = emptyList(),
        contentIntent: PendingIntent? = null
    ) {
        createNotificationChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. Process the Per-Minute Ticker Template
        cancelTicker(notificationId)
        var currentPillText = pillText

        val hasToken = pillText.contains("{MINS}") || pillText.contains("{DUR}")
        val countdownActive = chronometerTargetMillis != null &&
            chronometerTargetMillis > System.currentTimeMillis()

        if (chronometerTargetMillis != null && hasToken) {
            val diffMillis = chronometerTargetMillis - System.currentTimeMillis()

            if (diffMillis > 0) {
                // Round up so 14m 10s shows as "in 15m"
                val minsLeft = ceil(diffMillis / 60000.0).toInt()
                currentPillText = pillText
                    .replace("{MINS}", minsLeft.toString())
                    .replace("{DUR}", formatMinutes(minsLeft))

                // The shown value drops at the moment diffMillis falls to (minsLeft - 1) minutes.
                val delayToNextMinute = diffMillis - (minsLeft - 1) * 60000L + 50L
                val task = Runnable {
                    showCapsule(
                        context, pillText, title, content, notificationId,
                        timeoutSeconds, iconName, customIcon, detailPayload,
                        chronometerTargetMillis, actions, contentIntent
                    )
                }
                tickerTasks[notificationId] = task
                handler.postDelayed(task, delayToNextMinute)
            } else {
                currentPillText = "Now"
            }
        }

        // 2. Build the Notification Intents
        val dismissIntent = Intent(context, CapsuleDismissReceiver::class.java).apply {
            putExtra("notification_id", notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentPendingIntent = contentIntent ?: detailPayload?.let { payload ->
            val tapIntent = Intent(context, ProcessTextActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_PAYLOAD", payload)
            }
            PendingIntent.getActivity(
                context,
                notificationId + 1000,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val finalIcon = customIcon ?: run {
            val resId = resolveDrawable(context, iconName)
            Icon.createWithResource(context, resId)
        }

        // 3. Construct Android 16 Native Builder
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(finalIcon)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_WORKOUT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)

        // Let the expanded card run the live per-second ticking chronometer 
        if (countdownActive && chronometerTargetMillis != null) {
            builder.setWhen(chronometerTargetMillis)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }

        // Attach native actions (Pause, Next, Disconnect, etc.)
        actions.forEach { builder.addAction(it) }
        
        // Provide a fallback dismiss button if no actions were mapped
        if (actions.isEmpty()) {
            builder.addAction(
                Notification.Action.Builder(null, "Dismiss", dismissPendingIntent).build()
            )
        }

        if (contentPendingIntent != null) {
            builder.setContentIntent(contentPendingIntent)
        }

        if (timeoutSeconds > 0) {
            builder.setTimeoutAfter(timeoutSeconds * 1000L)
        }

        // 4. Attach Live Status Promotion
        applyShortCriticalText(builder, currentPillText)
        applyPromotedOngoing(builder)
        builder.extras.putString("oplus.liveNotificationType", "capsule")

        manager.notify(notificationId, builder.build())
    }

    /**
     * Sets the status-bar pill text. The platform setter takes a String (not a CharSequence), so the
     * old reflection lookup with CharSequence.class failed silently and the system fell back to the
     * notification title. The extra is written directly as well, so the pill text is set even if the
     * setter can't be found.
     */
    private fun applyShortCriticalText(builder: Notification.Builder, text: String) {
        builder.extras.putString(EXTRA_SHORT_CRITICAL_TEXT, text)

        val applied = listOf<Class<*>>(String::class.java, CharSequence::class.java).any { type ->
            try {
                Notification.Builder::class.java
                    .getMethod("setShortCriticalText", type)
                    .invoke(builder, text)
                true
            } catch (_: NoSuchMethodException) {
                false
            } catch (e: Exception) {
                Log.w(TAG, "setShortCriticalText(${type.simpleName}) failed: ${e.message}")
                false
            }
        }
        Log.d(TAG, "pill='$text' via ${if (applied) "setter+extra" else "extra only"}")
    }

    private fun applyPromotedOngoing(builder: Notification.Builder) {
        builder.extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
        try {
            Notification.Builder::class.java
                .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                .invoke(builder, true)
        } catch (_: NoSuchMethodException) {
            // Extra above is enough
        } catch (e: Exception) {
            Log.w(TAG, "setRequestPromotedOngoing failed: ${e.message}")
        }
    }

    private fun resolveDrawable(context: Context, name: String?): Int {
        if (name.isNullOrBlank()) return R.drawable.ic_capsule_bolt
        val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
        return if (resId != 0) resId else R.drawable.ic_capsule_bolt
    }
}
