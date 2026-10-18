package com.microtag.inspect

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.microtag.core.CapsuleCopyReceiver
import com.microtag.core.MicrotagReminder

/**
 * Pins: a piece of text kept on the status bar and in the shade for a while.
 *
 * The pin's list lives in SharedPreferences ([PinLogic] owns the format and the rules). Each pin is one ongoing
 * notification in a slot from [PinLogic.ID_BASE]. It shows as a status-bar chip for [PinLogic.CHIP_WINDOW_MS], then an
 * alarm re-posts it without the chip. The system removes it at its expiry; swiping it away unpins it for good.
 */
object PinEngine {
    private const val TAG = "MicrotagPin"
    private const val PREFS = "microtag_pins"
    private const val KEY = "pins"
    const val PROMPT_ID = 9005

    const val ACTION_PIN = "com.microtag.pin.PIN"
    const val ACTION_UNPIN = "com.microtag.pin.UNPIN"
    const val ACTION_EXTEND = "com.microtag.pin.EXTEND"
    const val ACTION_COPY = "com.microtag.pin.COPY"
    const val ACTION_DEMOTE = "com.microtag.pin.DEMOTE"
    const val EXTRA_ID = "pin_id"
    const val EXTRA_TEXT = "pin_text"

    private val lock = Any()
    private val promptedAt = HashMap<String, Long>()
    private var lastPromptAt = 0L

    // ---------------------------------------------------------------- storage

    private fun load(app: Context, now: Long = System.currentTimeMillis()): MutableList<Pin> =
        PinLogic.decode(app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: "")
            .filter { !PinLogic.isExpired(it, now) }
            .toMutableList()

    private fun save(app: Context, pins: List<Pin>) {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, PinLogic.encode(pins)).apply()
    }

    fun list(context: Context): List<Pin> = synchronized(lock) { load(context.applicationContext) }

    // ---------------------------------------------------------------- actions

    /** Pins [text] for [expiry] (the chosen default when null). Pinning the same text again starts it afresh. */
    fun pin(context: Context, text: String, expiry: PinLogic.Expiry? = null): Pin? {
        val app = context.applicationContext
        PillPrefs.attach(app)
        val t = text.trim().take(PinLogic.MAX_TEXT)
        if (t.isEmpty()) return null
        val now = System.currentTimeMillis()
        val e = expiry ?: PinLogic.expiryByKey(PillConfig.get("PINEXPIRY"))
        val made = synchronized(lock) {
            val pins = load(app, now)
            pins.filter { it.text == t }.forEach { stop(app, it.id) }
            pins.removeAll { it.text == t }
            val id = PinLogic.nextId(pins)
            pins.removeAll { it.id == id }
            val p = Pin(id, t, now, PinLogic.expiresAt(now, e))
            pins.add(p)
            save(app, pins)
            p
        }
        post(app, made)
        return made
    }

    fun unpin(context: Context, id: Int) {
        val app = context.applicationContext
        synchronized(lock) {
            val pins = load(app)
            pins.removeAll { it.id == id }
            save(app, pins)
        }
        stop(app, id)
    }

    /** Adds [ms] to a timed pin and returns it as it is now. */
    fun extend(context: Context, id: Int, ms: Long): Pin? {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val updated = synchronized(lock) {
            val pins = load(app, now)
            val i = pins.indexOfFirst { it.id == id }
            if (i < 0) return@synchronized null
            val p = PinLogic.extend(pins[i], now, ms)
            pins[i] = p
            save(app, pins)
            p
        }
        if (updated != null) post(app, updated)
        return updated
    }

    fun copyPin(context: Context, id: Int) {
        val app = context.applicationContext
        val p = list(app).firstOrNull { it.id == id } ?: return
        InspectEngine.markSelfCopy(p.text)
        CapsuleCopyReceiver.copy(app, p.text)
    }

    /** The chip window is over: carry on as an ordinary notification. */
    fun demote(context: Context, id: Int) {
        val app = context.applicationContext
        val p = list(app).firstOrNull { it.id == id } ?: return
        post(app, p)
    }

    /** After a reboot or a rebind, put every live pin back. Pins the user swiped away are already gone from the list. */
    fun restore(context: Context) {
        val app = context.applicationContext
        try {
            list(app).forEach { post(app, it) }
        } catch (t: Throwable) {
            Log.w(TAG, "restore failed: ${t.message}")
        }
    }

    // ---------------------------------------------------------------- the "Pin it?" offer

    /** Offers to pin a copied code. Opt-in, rare: never twice in 20 s, never the same text twice in 10 min. */
    fun offerPin(context: Context, text: String) {
        val app = context.applicationContext
        PillPrefs.attach(app)
        if (PillConfig.get("PINPROMPT") != "ON") return
        val t = text.trim()
        if (InspectEngine.isSelfCopy(t) || !PinLogic.looksPinnable(t)) return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (now - lastPromptAt < 20_000L) return
            if (promptedAt[t]?.let { now - it < 600_000L } == true) return
            lastPromptAt = now
            promptedAt[t] = now
            promptedAt.entries.removeAll { now - it.value > 600_000L }
        }
        val pinIntent = receiverIntent(app, ACTION_PIN, t.hashCode(), text = t)
        MicrotagReminder.showCapsule(
            context = app, pillText = "Pin it?", title = "Pin “${t.take(30)}”?",
            content = "Keeps it on your status bar for a while.", notificationId = PROMPT_ID, timeoutSeconds = 10,
            iconName = "ic_pin", contentIntent = pinIntent,
            actions = listOf(Notification.Action.Builder(null, "Pin", pinIntent).build()), secret = true
        )
    }

    // ---------------------------------------------------------------- notification plumbing

    fun payloadFor(pin: Pin) = InspectPayload(
        domain = "PINNED", title = pin.text, copyText = pin.text, big = pin.text,
        nums = listOf(pin.createdAt.toDouble(), pin.expiresAt.toDouble()), unit = pin.id.toString()
    )

    private fun post(app: Context, pin: Pin) {
        val now = System.currentTimeMillis()
        val remaining = if (pin.expiresAt == 0L) 0 else ((pin.expiresAt - now) / 1000L).toInt().coerceAtLeast(1)
        val chip = PinLogic.inChipWindow(pin, now)
        val open = Intent(app, InspectDetailActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(InspectDetailActivity.EXTRA_PAYLOAD, payloadFor(pin))
        }
        // No EXTRA_NOTIFICATION_ID: opening the card must not take the pin down.
        val openPi = PendingIntent.getActivity(app, pin.id + 3000, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        MicrotagReminder.showCapsule(
            context = app, pillText = PinLogic.chipText(pin.text), title = "Pinned", content = pin.text,
            notificationId = pin.id, timeoutSeconds = remaining, iconName = "ic_pin", contentIntent = openPi,
            actions = listOf(
                action(app, "Copy", ACTION_COPY, pin.id),
                action(app, "Unpin", ACTION_UNPIN, pin.id)
            ),
            promote = chip, secret = true, deleteIntent = receiverIntent(app, ACTION_UNPIN, pin.id + 5000, id = pin.id)
        )
        if (chip) scheduleDemote(app, pin)
    }

    private fun stop(app: Context, id: Int) {
        MicrotagReminder.cancelCapsule(app, id)
        try {
            (app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(receiverIntent(app, ACTION_DEMOTE, id + 6000, id = id))
        } catch (_: Throwable) {
        }
    }

    private fun scheduleDemote(app: Context, pin: Pin) {
        try {
            val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val at = pin.createdAt + PinLogic.CHIP_WINDOW_MS + 1000L
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, receiverIntent(app, ACTION_DEMOTE, pin.id + 6000, id = pin.id))
        } catch (t: Throwable) {
            Log.w(TAG, "could not schedule demote: ${t.message}")
        }
    }

    private fun action(app: Context, label: String, action: String, id: Int): Notification.Action =
        Notification.Action.Builder(null, label, receiverIntent(app, action, id + 1000 * (action.hashCode() and 7), id = id)).build()

    private fun receiverIntent(app: Context, action: String, requestCode: Int, id: Int = -1, text: String? = null): PendingIntent {
        val i = Intent(app, PinReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id)
        if (text != null) i.putExtra(EXTRA_TEXT, text)
        return PendingIntent.getBroadcast(app, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
