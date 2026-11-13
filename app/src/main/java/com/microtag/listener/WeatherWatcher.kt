package com.microtag.listener

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.microtag.core.MicrotagPrefs
import com.microtag.core.MicrotagReminder
import com.microtag.ui.TagKeys
import com.microtag.ui.TagSettings
import com.microtag.weather.Weather
import com.microtag.weather.WeatherEngine
import com.microtag.weather.WeatherFormat

/**
 * Posts the weather chip from the API on the dashboard's interval. The chip stays up for a while, then goes away and
 * comes back when the interval next lapses; it is not a permanent notification. Used unless the person chose to use the
 * system weather notification instead (TagSettings.weatherFromSystem), so there is only ever one source.
 */
object WeatherWatcher {
    private const val ID = 7_300_005
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var app: Context? = null
    @Volatile private var lastOkAt = 0L
    @Volatile private var intervalMs = 60 * 60 * 1000L

    /** Takes the chip down after its visible time. setTimeoutAfter alone is not honoured for a promoted, ongoing chip. */
    private val hide = Runnable { app?.let { MicrotagReminder.cancelCapsule(it, ID) } }

    private val tick = object : Runnable {
        override fun run() {
            val ctx = app ?: return
            if (!MicrotagPrefs(ctx).isAppEnabled(TagKeys.WEATHER) || TagSettings(ctx).weatherFromSystem) return
            show(ctx, force = false)
            handler?.postDelayed(this, intervalMs)
        }
    }

    /** Starts or stops to match the tile. Safe to call any time (tile toggled, interval or place changed). */
    @Synchronized
    fun refresh(context: Context) {
        val ctx = context.applicationContext
        app = ctx
        val on = MicrotagPrefs(ctx).isAppEnabled(TagKeys.WEATHER) && !TagSettings(ctx).weatherFromSystem
        intervalMs = (TagSettings(ctx).weatherIntervalMin.coerceAtLeast(15)) * 60_000L
        handler?.removeCallbacks(tick)
        handler?.removeCallbacks(hide)
        if (!on) {
            MicrotagReminder.cancelCapsule(ctx, ID)
            lastOkAt = 0L
            return
        }
        val h = handler ?: HandlerThread("microtag-weather").also { it.start() }.also { thread = it }.let { Handler(it.looper) }.also { handler = it }
        h.post { show(ctx, force = true); h.postDelayed(tick, intervalMs) }
    }

    @Synchronized
    fun stop() {
        handler?.removeCallbacks(tick)
        app?.let { MicrotagReminder.cancelCapsule(it, ID) }
        thread?.quitSafely(); thread = null; handler = null
        lastOkAt = 0L
    }

    /** True when the weather chip comes from Open-Meteo, so the weather app's own notification is not turned into a second one. */
    fun usesApi(context: Context): Boolean = !TagSettings(context.applicationContext).weatherFromSystem

    private fun show(ctx: Context, force: Boolean) {
        if (TagSettings(ctx).quietReason() != null) return
        val r = WeatherEngine.refresh(ctx, force)
        if (r !is WeatherEngine.Result.Ok) return
        lastOkAt = System.currentTimeMillis()
        post(ctx, r.weather, r.place?.name, r.fahrenheit)
    }

    private fun post(ctx: Context, w: Weather, place: String?, f: Boolean) {
        // Up for ten minutes, or half the interval if that is shorter; then it is cancelled here and the next tick brings it back.
        val visibleMs = minOf(10 * 60_000L, intervalMs / 2)
        handler?.removeCallbacks(hide)
        handler?.postDelayed(hide, visibleMs)
        MicrotagReminder.showCapsule(
            context = ctx,
            pillText = WeatherFormat.pill(w, f),
            title = WeatherFormat.title(w, place),
            content = WeatherFormat.content(w, f),
            notificationId = ID,
            timeoutSeconds = (visibleMs / 1000L).toInt(),
            iconName = com.microtag.weather.WeatherCodes.icon(w.code, w.isDay, w.windKmh),
            showAppName = false
        )
    }
}
