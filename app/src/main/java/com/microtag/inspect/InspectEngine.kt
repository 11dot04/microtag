package com.microtag.inspect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.microtag.R
import com.microtag.core.MicrotagLog
import com.microtag.core.MicrotagLog.Decision
import com.microtag.core.MicrotagPrefs
import com.microtag.core.MicrotagReminder
import java.io.File
import java.time.Clock
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Turns a piece of text (selected, or copied) into a pill and card. Everything runs on its own
 * threads, so no activity has to stay open while a lookup is in flight. The notification listener
 * keeps the process alive; that is what lets the lookup finish after the toolbar activity closes.
 */
object InspectEngine {
    private const val TAG = "MicrotagInspect"
    private const val SELECTION_ID = 9003
    private const val CLIPBOARD_ID = 9004
    private const val SELF_COPY_MS = 10_000L

    private val pool = Executors.newFixedThreadPool(2) { r -> Thread(r, "inspect-engine").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private val selfCopies = ConcurrentHashMap<String, Long>()
    private val dictionary = DictionaryLookup()

    @Volatile private var rates: CurrencyRates? = null

    /** Call when the app itself puts text on the clipboard so the watcher doesn't inspect it. */
    fun markSelfCopy(text: String) {
        val now = System.currentTimeMillis()
        selfCopies[text.trim()] = now
        selfCopies.entries.removeAll { now - it.value > SELF_COPY_MS }
    }

    fun isSelfCopy(text: String): Boolean {
        val at = selfCopies[text.trim()] ?: return false
        return System.currentTimeMillis() - at < SELF_COPY_MS
    }

    private fun ratesFor(context: Context): CurrencyRates {
        rates?.let { return it }
        synchronized(this) {
            rates?.let { return it }
            val r = CurrencyRates(File(context.cacheDir, "fx_rates.json"))
            rates = r
            return r
        }
    }

    /** The colour-name list is a raw resource; load it once, on the worker thread. */
    private fun loadColourNames(app: Context) {
        if (ColourNames.isLoaded) return
        try {
            app.resources.openRawResource(R.raw.colour_names).bufferedReader().useLines { ColourNames.load(it) }
        } catch (_: Exception) {
        }
    }

    fun inspect(context: Context, rawText: String, source: Source) {
        val app = context.applicationContext
        pool.execute {
            try {
                loadColourNames(app)
                run(app, rawText, source)
            } catch (t: Throwable) {
                Log.w(TAG, "inspect failed: ${t.message}", t)
            }
        }
    }

    private fun run(app: Context, rawText: String, source: Source) {
        val text = Detectors.normalize(rawText)
        if (text.isEmpty()) return
        val id = if (source == Source.SELECTION) SELECTION_ID else CLIPBOARD_ID
        val env = Env(Locale.getDefault(), Clock.systemDefaultZone(), ratesFor(app), dictionary)

        val candidates = Detectors.candidates(text, source, env)
        if (candidates.isEmpty()) {
            if (source == Source.SELECTION) toast(app, "Nothing to inspect here")
            return
        }

        // Selected text gets a placeholder pill straight away when the answer may need the network.
        var showedLoading = false
        if (source == Source.SELECTION && candidates.first().slow) {
            MicrotagReminder.showCapsule(
                context = app, pillText = "…", title = "Looking up", content = text.take(80),
                notificationId = id, timeoutSeconds = 12, iconName = "ic_inspect_define"
            )
            showedLoading = true
        }

        var result: InspectResult? = null
        var used = ""
        for (c in candidates) {
            val r = try {
                c.resolve()
            } catch (t: Throwable) {
                Log.w(TAG, "${c.name} failed: ${t.message}")
                null
            }
            if (r != null) {
                result = r
                used = c.name
                break
            }
        }

        if (result == null || (source == Source.CLIPBOARD && result.status == Status.ERROR)) {
            if (showedLoading) MicrotagReminder.cancelCapsule(app, id)
            if (source == Source.SELECTION) toast(app, "Nothing to inspect here")
            log(app, source, Decision.SKIPPED, "no candidate produced a result", null, null, text)
            return
        }

        MicrotagPrefs(app).incrementInterceptCount()
        MicrotagReminder.showCapsule(
            context = app,
            pillText = result.pill,
            title = result.cardTitle,
            content = result.cardText,
            notificationId = id,
            timeoutSeconds = result.timeoutSeconds,
            iconName = result.iconName,
            customIcon = result.swatch?.let { swatchIcon(it) },
            detailPayload = result.toPayload()
        )
        log(app, source, Decision.PROMOTED, "$used -> ${result.domain}", result.pill, result.iconName, text)
    }

    private fun toast(app: Context, message: String) {
        main.post { Toast.makeText(app, message, Toast.LENGTH_SHORT).show() }
    }

    private fun log(app: Context, source: Source, decision: Decision, reason: String, pill: String?, icon: String?, text: String) {
        try {
            MicrotagLog.add(
                key = "inspect:${source.name.lowercase(Locale.ROOT)}",
                pkg = app.packageName,
                path = "INSPECT",
                decision = decision,
                reason = reason,
                pill = pill,
                icon = icon,
                title = if (source == Source.SELECTION) "Selection" else "Clipboard",
                text = text.take(80),
                meta = ""
            )
        } catch (_: Throwable) {
        }
    }

    /** A round swatch of the colour, used as the pill icon for colour results. */
    private fun swatchIcon(argb: Int): Icon {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = argb or (0xFF shl 24)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = 0x66FFFFFF
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, paint)
        return Icon.createWithBitmap(bmp)
    }
}
