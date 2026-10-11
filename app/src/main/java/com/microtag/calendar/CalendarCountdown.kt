package com.microtag.calendar

import android.Manifest
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.util.Log
import androidx.core.content.ContextCompat
import com.microtag.core.MicrotagPrefs
import com.microtag.core.MicrotagReminder
import com.microtag.ui.TagKeys
import com.microtag.ui.TagSettings
import java.util.Date

/**
 * Countdown capsule driven by the calendar provider instead of calendar notifications.
 * Shows "in 15m", "in 14m" ... on the pill, a ticking countdown in the expanded card, and
 * switches to "Now" when the event starts, then clears itself.
 */
object CalendarCountdown {
    private const val TAG = "CalendarCountdown"
    private const val NOTIFICATION_ID = 7_300_001
    private const val GRACE_MS = 60_000L   // keep "Now" for a minute after the start
    private const val RECHECK_MS = 60_000L

    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var observer: ContentObserver? = null
    private var shownSignature: String? = null

    private val tick = Runnable { refresh() }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** True when this class (not the calendar app's notification) is what produces the capsule. */
    fun isActive(context: Context): Boolean =
        MicrotagPrefs(context).isAppEnabled(TagKeys.CALENDAR) && hasPermission(context)

    @Synchronized
    fun start(context: Context) {
        appContext = context.applicationContext
        refresh()
    }

    @Synchronized
    fun stop() {
        handler.removeCallbacks(tick)
        unregisterObserver()
        val ctx = appContext
        if (ctx != null) MicrotagReminder.cancelCapsule(ctx, NOTIFICATION_ID)
        shownSignature = null
    }

    /** Re-reads settings and the calendar, and re-posts only if something visible changed. */
    @Synchronized
    fun refresh() {
        val ctx = appContext ?: return
        handler.removeCallbacks(tick)

        if (!isActive(ctx)) {
            stop()
            return
        }
        registerObserver(ctx)

        val settings = TagSettings(ctx)
        val now = System.currentTimeMillis()
        val leadMs = settings.calendarLeadMin * 60_000L
        val event = try {
            nextEvent(ctx, now, leadMs, settings.calendarExcluded)
        } catch (e: Exception) {
            Log.w(TAG, "Calendar query failed: ${e.message}")
            null
        }

        if (event == null) {
            if (shownSignature != null) MicrotagReminder.cancelCapsule(ctx, NOTIFICATION_ID)
            shownSignature = null
            handler.postDelayed(tick, RECHECK_MS)
            return
        }

        val started = event.begin <= now
        val signature = "${event.id}|${event.begin}|${event.title}|${event.location}|$started"
        if (signature != shownSignature) {
            shownSignature = signature
            val timeText = DateFormat.getTimeFormat(ctx).format(Date(event.begin))
            MicrotagReminder.showCapsule(
                context = ctx,
                pillText = if (started) "Now" else "in {DUR}",
                title = event.title,
                content = if (event.location.isNotBlank()) "$timeText · ${event.location}" else "Starts at $timeText",
                notificationId = NOTIFICATION_ID,
                iconName = "ic_calendar_countdown",
                chronometerTargetMillis = if (started) null else event.begin,
                contentIntent = openEventIntent(ctx, event)
            )
        }

        // Look again at the minute, but sooner for the moment the event starts or leaves the grace period.
        val untilStart = event.begin - now
        val delay = when {
            started -> (event.begin + GRACE_MS - now).coerceIn(1_000L, RECHECK_MS)
            else -> (untilStart + 1_000L).coerceIn(1_000L, RECHECK_MS)
        }
        handler.postDelayed(tick, delay)
    }

    private data class Event(
        val id: Long,
        val begin: Long,
        val title: String,
        val location: String
    )

    private fun nextEvent(ctx: Context, now: Long, leadMs: Long, excluded: Set<String>): Event? {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - GRACE_MS)
            ContentUris.appendId(it, now + leadMs)
        }.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.STATUS
        )

        ctx.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val begin = c.getLong(1)
                // Instances overlap the window, so ongoing long events show up too; only starts count.
                if (begin < now - GRACE_MS || begin > now + leadMs) continue
                if (c.getInt(5) == 1) continue                                    // all-day
                if (excluded.contains(c.getLong(4).toString())) continue          // excluded calendar
                if (!c.isNull(6) && c.getInt(6) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                if (!c.isNull(7) && c.getInt(7) == CalendarContract.Events.STATUS_CANCELED) continue

                return Event(
                    id = c.getLong(0),
                    begin = begin,
                    title = c.getString(2)?.takeIf { it.isNotBlank() } ?: "Event",
                    location = c.getString(3).orEmpty()
                )
            }
        }
        return null
    }

    private fun openEventIntent(ctx: Context, event: Event): PendingIntent {
        val uri: Uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(
            ctx, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun registerObserver(ctx: Context) {
        if (observer != null) return
        val o = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                shownSignature = null // force a fresh post if the event was edited
                refresh()
            }
        }
        try {
            ctx.contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, o)
            observer = o
        } catch (e: Exception) {
            Log.w(TAG, "Could not observe calendar: ${e.message}")
        }
    }

    private fun unregisterObserver() {
        val o = observer ?: return
        try { appContext?.contentResolver?.unregisterContentObserver(o) } catch (_: Exception) {}
        observer = null
    }
}
