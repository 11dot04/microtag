package com.microtag.meds

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Keeps exactly one alarm set: the next moment something needs doing (see MedLogic.nextEvent).
 * Every change, boot, time or zone change, and app update just calls [reschedule].
 */
object MedScheduler {
    const val ACTION_FIRE = "com.microtag.meds.FIRE"

    fun reschedule(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val ctx = context.applicationContext
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = fireIntent(ctx)
        val book = MedStore.book(ctx)
        val next = MedLogic.nextEvent(book.all(), book.log(), nowMs, MedStore.zone())
        if (next == null) { am.cancel(pi); return }
        runCatching {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atMs, pi)
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atMs, pi)
        }.onFailure { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atMs, pi) }
    }

    private fun fireIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 7311, Intent(ctx, MedicationReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/** Boot, clock/zone change and app update all mean: work out the next alarm again. */
class MedBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MedScheduler.reschedule(context)
        MedicationReceiver.refresh(context)
    }
}
