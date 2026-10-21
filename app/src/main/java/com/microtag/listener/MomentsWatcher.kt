package com.microtag.listener

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.microtag.core.MicrotagReminder
import com.microtag.inspect.MomentsLogic
import com.microtag.inspect.MomentsStore
import com.microtag.inspect.PillPrefs
import com.microtag.ui.TagSettings
import java.util.Calendar

/**
 * Notices moments: the minute tick for clock moments and the 1.8 billionth second, and every interception for the count
 * milestones. The first find posts a short capsule; later finds only count quietly.
 */
object MomentsWatcher {
    private const val ID = 7_300_004
    private var registered = false

    @Synchronized
    fun start(context: Context) {
        val ctx = context.applicationContext
        PillPrefs.attach(ctx)
        if (registered) return
        ContextCompat.registerReceiver(ctx, tick, IntentFilter(Intent.ACTION_TIME_TICK), ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
        check(ctx)
    }

    @Synchronized
    fun stop(context: Context) {
        if (!registered) return
        try { context.applicationContext.unregisterReceiver(tick) } catch (_: Exception) {}
        registered = false
    }

    /** Call once per interception. */
    fun intercepted(context: Context) {
        val ctx = context.applicationContext
        PillPrefs.attach(ctx)
        // The same number the home screen shows.
        val total = com.microtag.core.MicrotagPrefs(ctx).interceptCount
        val reached = MomentsLogic.counts(total)
        val fresh = reached.filter { it !in MomentsStore.found() }
        // Several can be reached at once the first time; each is recorded, the highest is announced.
        fresh.forEachIndexed { i, id -> found(ctx, id, announce = i == fresh.lastIndex) }
    }

    private val tick = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = check(context.applicationContext)
    }

    private fun check(ctx: Context) {
        PillPrefs.attach(ctx)
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        for (id in MomentsLogic.clock(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))) {
            found(ctx, id, announce = true)
        }
        if (MomentsLogic.epochReached(now) && "epoch" !in MomentsStore.found()) found(ctx, "epoch", announce = true)
    }

    private fun found(ctx: Context, id: String, announce: Boolean) {
        val first = MomentsStore.award(id)
        if (!first || !announce) return
        val m = MomentsLogic.byId(id) ?: return
        if (TagSettings(ctx).quietReason() != null) return
        MicrotagReminder.showCapsule(
            context = ctx,
            pillText = m.pill,
            title = m.title,
            content = m.blurb + " Moment found.",
            notificationId = ID,
            timeoutSeconds = 8,
            showAppName = false
        )
    }
}
