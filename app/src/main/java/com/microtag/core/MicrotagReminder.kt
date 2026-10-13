package com.microtag.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import com.microtag.R
import com.microtag.inspect.InspectPayload
import com.microtag.inspect.InspectDetailActivity
import java.util.Locale
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

    // ---- Source app helpers -------------------------------------------------------------------

    private val labelCache = ConcurrentHashMap<String, String>()

    /** The name the user sees under the app's icon, e.g. "Discord". Falls back to the package name. */
    fun appLabel(context: Context, packageName: String): String =
        labelCache.getOrPut(packageName) {
            try {
                val pm = context.packageManager
                val info = if (Build.VERSION.SDK_INT >= 33) {
                    pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION") pm.getApplicationInfo(packageName, 0)
                }
                pm.getApplicationLabel(info).toString().ifBlank { packageName }
            } catch (_: Exception) {
                packageName
            }
        }

    private fun launchPendingIntent(context: Context, packageName: String): PendingIntent? {
        val launch = try {
            context.packageManager.getLaunchIntentForPackage(packageName)
        } catch (_: Exception) {
            null
        } ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(
            context,
            packageName.hashCode(),
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ---- Action tinting -----------------------------------------------------------------------

    private const val DESTRUCTIVE_RED = 0xFFFF5A5A.toInt()
    private val destructiveTitles = setOf(
        "disconnect", "leave", "leave call", "hang up", "end call", "end", "decline", "reject", "delete", "sign out"
    )

    /** Only the label can be coloured. Everything else on the action is copied across unchanged. */
    private fun tintIfDestructive(action: Notification.Action): Notification.Action {
        val title = action.title?.toString() ?: return action
        if (title.trim().lowercase(Locale.ROOT) !in destructiveTitles) return action
        return try {
            val coloured = SpannableString(title).apply {
                setSpan(ForegroundColorSpan(DESTRUCTIVE_RED), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val b = Notification.Action.Builder(action.getIcon(), coloured, action.actionIntent)
            action.extras?.let { b.addExtras(it) }
            action.remoteInputs?.forEach { b.addRemoteInput(it) }
            b.setAllowGeneratedReplies(action.allowGeneratedReplies)
            b.setSemanticAction(action.semanticAction)
            b.setContextual(action.isContextual)
            if (Build.VERSION.SDK_INT >= 31) b.setAuthenticationRequired(action.isAuthenticationRequired)
            b.build()
        } catch (e: Exception) {
            Log.w(TAG, "Could not tint action '$title': ${e.message}")
            action
        }
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

    /**
     * A bare "copy me" payload (the OTP) copies on tap. Anything richer opens the overlay.
     * The overlay lives in its own task so tapping never drags the main app forward.
     */
    private fun payloadPendingIntent(context: Context, payload: InspectPayload, notificationId: Int): PendingIntent {
        if (payload.isCopyOnTap) {
            val copy = Intent(context, CapsuleCopyReceiver::class.java).apply {
                putExtra(CapsuleCopyReceiver.EXTRA_TEXT, payload.copyValue)
                putExtra(CapsuleCopyReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getBroadcast(
                context, notificationId + 2000, copy,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val open = Intent(context, InspectDetailActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(InspectDetailActivity.EXTRA_PAYLOAD, payload)
            putExtra(InspectDetailActivity.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getActivity(
            context, notificationId + 1000, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

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
        contentIntent: PendingIntent? = null,
        /** The app this capsule is about. Its name is shown on the card and tapping opens it. */
        sourcePackage: String? = null,
        /** The original notification's own tap action, so a tap lands where the source would have. */
        sourceIntent: PendingIntent? = null
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
                        chronometerTargetMillis, actions, contentIntent,
                        sourcePackage, sourceIntent
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

        val contentPendingIntent: PendingIntent? =
            contentIntent
                ?: detailPayload?.let { payloadPendingIntent(context, it, notificationId) }
                ?: sourceIntent
                ?: sourcePackage?.let { launchPendingIntent(context, it) }

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

        // Name the app the capsule is about, unless the title already says it.
        if (!sourcePackage.isNullOrBlank() && sourcePackage != context.packageName) {
            val label = appLabel(context, sourcePackage)
            if (label.isNotBlank() && !title.contains(label, ignoreCase = true)) builder.setSubText(label)
        }

        // Attach native actions (Pause, Next, Disconnect, etc.); destructive ones in red.
        actions.forEach { builder.addAction(tintIfDestructive(it)) }
        
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
