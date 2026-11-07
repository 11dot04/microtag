package com.microtag.meds

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.microtag.core.MicrotagReminder
import com.microtag.listener.PillText

/**
 * Shows the chip when a dose is due and handles its buttons (Taken, Snooze 10 min, Skip).
 * Medication chips are never counted as interceptions and ignore quiet hours: a reminder you muted is a missed dose.
 */
class MedicationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val now = System.currentTimeMillis()
        val book = MedStore.book(ctx)
        val zone = MedStore.zone()
        val key = intent.getStringExtra(EXTRA_DOSE)
        if (key != null) {
            val dose = MedLogic.between(book.all(), now - 2 * 86_400_000L, now + 86_400_000L, zone).firstOrNull { it.key == key }
            if (dose != null) when (intent.action) {
                ACTION_TAKEN -> book.mark(dose, DoseState.TAKEN, now)
                ACTION_SKIP -> book.mark(dose, DoseState.SKIPPED, now)
                ACTION_SNOOZE -> book.mark(dose, DoseState.SNOOZED, now, now + MedLogic.SNOOZE_MIN * 60_000L)
            }
        }
        // Doses that ran out of time become MISSED in the log, so history stays right even if the screen is never opened.
        val log = book.log()
        for (d in MedLogic.between(book.all(), now - 2 * 86_400_000L, now, zone))
            if (log[d.key] == null && MedLogic.status(d, log, now, zone) == DoseStatus.MISSED) book.mark(d, DoseState.MISSED, now)
        refresh(ctx, now)
        MedScheduler.reschedule(ctx, now)
    }

    companion object {
        const val ACTION_TAKEN = "com.microtag.meds.TAKEN"
        const val ACTION_SKIP = "com.microtag.meds.SKIP"
        const val ACTION_SNOOZE = "com.microtag.meds.SNOOZE"
        const val EXTRA_DOSE = "dose"
        private const val ID_DUE = 0x4D3D0
        private const val ID_REFILL = 0x4D3D1

        /** Brings the chip in line with what is due now: shows it, updates it, or takes it down. */
        fun refresh(context: Context, nowMs: Long = System.currentTimeMillis()) {
            val ctx = context.applicationContext
            val book = MedStore.book(ctx)
            val zone = MedStore.zone()
            val due = MedLogic.pending(book.all(), book.log(), nowMs, zone)
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (due.isEmpty()) nm.cancel(ID_DUE)
            else {
                val first = due.first()
                val actions = listOf(
                    action(ctx, "Taken", ACTION_TAKEN, first.key, 1),
                    action(ctx, "Snooze", ACTION_SNOOZE, first.key, 2),
                    action(ctx, "Skip", ACTION_SKIP, first.key, 3)
                )
                MicrotagReminder.showCapsule(
                    context = ctx, pillText = MedLogic.pill(due, PillText.LIMIT), title = MedLogic.title(due),
                    content = MedLogic.content(due, zone), notificationId = ID_DUE, timeoutSeconds = 0,
                    iconName = icon(ctx), actions = actions, secret = due.any { it.med.discreet }
                )
            }
            val low = MedStore.refillNotice(ctx, nowMs)
            if (low.isNotEmpty()) MicrotagReminder.showCapsule(
                context = ctx, pillText = "Refill", title = "Running low",
                content = low.joinToString(", ") { it.name + " · " + (MedLogic.daysOfSupply(it) ?: 0) + " d left" },
                notificationId = ID_REFILL, timeoutSeconds = 20, iconName = icon(ctx), promote = false,
                secret = low.any { it.discreet }
            )
        }

        /** ic_medication when the app has that drawable, otherwise the generic bolt. */
        private fun icon(ctx: Context): String =
            if (ctx.resources.getIdentifier("ic_medication", "drawable", ctx.packageName) != 0) "ic_medication" else "ic_capsule_bolt"

        private fun action(ctx: Context, label: String, act: String, key: String, rc: Int): Notification.Action {
            val pi = PendingIntent.getBroadcast(
                ctx, rc, Intent(ctx, MedicationReceiver::class.java).setAction(act).putExtra(EXTRA_DOSE, key),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return Notification.Action.Builder(null as Icon?, label, pi).build()
        }
    }
}
