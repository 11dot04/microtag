package com.microtag.inspect

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.microtag.core.CapsuleCopyReceiver
import com.microtag.core.MicrotagReminder

/**
 * The card shown when an Inspect pill is tapped. It runs in its own task (see the manifest), so it
 * opens over whatever screen the user is on and closing it returns there instead of to Microtag.
 *
 * Look: a floating glass card (translucent, tinted by the result's accent) over a blurred screen.
 * Each kind of result has its own accent; a colour result uses its own hue. Drag the handle down to dismiss.
 */
class InspectDetailActivity : Activity() {

    private val lime = Color.parseColor("#CCFF00")
    private val warn = Color.parseColor("#FFB74D")
    private val danger = Color.parseColor("#FF5A5A")
    private val grey = Color.parseColor("#9A9AA2")
    private val base = Color.parseColor("#0E0F12")

    private var card: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyBlur()
        render(intent, animate = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render(intent, animate = false)
    }

    private fun applyBlur() {
        var scrim = 0x40
        var blurred = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (wm.isCrossWindowBlurEnabled) {
                val lp = window.attributes
                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                lp.blurBehindRadius = 34
                window.attributes = lp
                blurred = true
            }
        }
        // No blur available (battery saver or an OEM setting): darken more so the card still reads.
        if (!blurred) scrim = 0x99
        window.setBackgroundDrawable(ColorDrawable(Color.argb(scrim, 0, 0, 0)))
    }

    @Suppress("DEPRECATION")
    private fun payloadFrom(i: Intent): InspectPayload? =
        if (Build.VERSION.SDK_INT >= 33) {
            i.getSerializableExtra(EXTRA_PAYLOAD, InspectPayload::class.java)
        } else {
            i.getSerializableExtra(EXTRA_PAYLOAD) as? InspectPayload
        }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun accentFor(p: InspectPayload): Int {
        if (p.status == "ERROR") return danger
        if (p.status == "WARN") return warn
        if (p.accent != 0) return p.accent
        return when (p.domain) {
            "CALC" -> Color.parseColor("#F9E800")
            "CONV" -> Color.parseColor("#4DD0E1")
            "MONEY" -> Color.parseColor("#69F0AE")
            "TIME" -> Color.parseColor("#8C9EFF")
            "DATE" -> Color.parseColor("#FFAB40")
            "URL" -> Color.parseColor("#6C97FF")
            else -> lime
        }
    }

    /** [fg] laid over [bg] at the given 0..255 strength. */
    private fun over(bg: Int, fg: Int, alpha: Int): Int {
        val a = alpha / 255f
        return Color.rgb(
            (Color.red(bg) * (1 - a) + Color.red(fg) * a).toInt(),
            (Color.green(bg) * (1 - a) + Color.green(fg) * a).toInt(),
            (Color.blue(bg) * (1 - a) + Color.blue(fg) * a).toInt()
        )
    }

    private fun render(i: Intent, animate: Boolean) {
        val payload = payloadFrom(i)
        val notifId = i.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (payload == null) {
            finish()
            return
        }
        // The pill has done its job once the card is open.
        if (notifId != -1) MicrotagReminder.cancelCapsule(applicationContext, notifId)
        show(payload, animate)
    }

    private fun show(payload: InspectPayload, animate: Boolean) {
        val accent = accentFor(payload)
        val root = FrameLayout(this).apply { setOnClickListener { finish() } }

        val cardView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(18))
            // Glass: mostly dark, a little see-through, with a wash of the accent at the top and a hairline edge.
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.argb(0xE0, Color.red(over(base, accent, 46)), Color.green(over(base, accent, 46)), Color.blue(over(base, accent, 46))),
                    Color.argb(0xD6, Color.red(base), Color.green(base), Color.blue(base))
                )
            ).apply {
                cornerRadius = dp(30).toFloat()
                setStroke(dp(1), Color.argb(0x26, 255, 255, 255))
            }
            isClickable = true
        }
        card = cardView

        // Drag handle: pull the card down to dismiss.
        val handleZone = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26))
        }
        handleZone.addView(View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.argb(0x55, 255, 255, 255))
                cornerRadius = dp(2).toFloat()
            }
        }, FrameLayout.LayoutParams(dp(38), dp(4), Gravity.CENTER))
        attachDrag(handleZone, cardView)
        cardView.addView(handleZone)

        if (payload.domain.isNotBlank()) {
            cardView.addView(TextView(this).apply {
                text = payload.domain
                textSize = 11f
                letterSpacing = 0.16f
                setTextColor(accent)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            })
        }

        if (payload.palette.size >= 2) {
            cardView.addView(
                SwatchStrip(this, payload.palette),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)).apply { topMargin = dp(10) }
            )
        }

        cardView.addView(TextView(this).apply {
            text = payload.heroText
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(0, dp(6), 0, 0)
            setTextIsSelectable(true)
        })
        if (payload.subText.isNotBlank()) {
            cardView.addView(TextView(this).apply {
                text = payload.subText
                textSize = 14f
                setTextColor(grey)
                setPadding(0, dp(2), 0, 0)
            })
        }

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, dp(6))
        }
        payload.rows.forEach { (label, value) ->
            val valueColor = when (label) {
                "Warning" -> danger
                "Note" -> warn
                else -> Color.WHITE
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(5), 0, dp(5))
            }
            row.addView(TextView(this).apply {
                text = label
                textSize = 13f
                setTextColor(grey)
                layoutParams = LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            row.addView(TextView(this).apply {
                text = value
                textSize = 14f
                setTextColor(valueColor)
                setLineSpacing(0f, 1.15f)
                setTextIsSelectable(true)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            column.addView(row)
        }
        if (payload.fullContent.isNotBlank()) {
            column.addView(TextView(this).apply {
                text = payload.fullContent
                textSize = 14f
                setTextColor(Color.parseColor("#E0E0E0"))
                setLineSpacing(0f, 1.3f)
                setTextIsSelectable(true)
            })
        }
        scroll.addView(column)
        cardView.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(10), 0, 0)
        }
        val copyValue = payload.copyValue
        if (copyValue.isNotBlank()) {
            buttons.addView(pill("COPY", accent, Color.BLACK) {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                CapsuleCopyReceiver.copy(this, copyValue)
                finish()
            })
        }
        val url = payload.url
        if (url.startsWith("https://") || url.startsWith("http://")) {
            buttons.addView(pill("OPEN", Color.argb(0x40, 255, 255, 255), Color.WHITE) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                }
                finish()
            })
        }
        if (payload.resolveUrl.isNotBlank()) {
            buttons.addView(pill("RESOLVE", Color.argb(0x40, 255, 255, 255), Color.WHITE) { v ->
                (v as TextView).text = "…"
                v.isEnabled = false
                Thread {
                    val r = LinkCheck.resolveShort(payload.resolveUrl)
                    val expanded = r.finalUrl?.let { UrlInspector.analyze(it) }
                    runOnUiThread {
                        if (isFinishing) return@runOnUiThread
                        if (expanded == null) {
                            show(payload.copy(resolveUrl = "", rows = payload.rows + ("Resolve" to "Couldn't expand it: ${r.problem ?: "unknown"}")), false)
                        } else {
                            val res = expanded.toResult().toPayload()
                            show(res.copy(rows = listOf("Expanded from" to payload.title) + res.rows), false)
                        }
                    }
                }.start()
            })
        }
        if (payload.checkHost.isNotBlank()) {
            buttons.addView(pill("CHECK", Color.argb(0x40, 255, 255, 255), Color.WHITE) { v ->
                (v as TextView).text = "…"
                v.isEnabled = false
                Thread {
                    val out = LinkCheck.check(payload.checkHost)
                    runOnUiThread {
                        if (isFinishing) return@runOnUiThread
                        val status = when {
                            out.status == Status.ERROR || payload.status == "ERROR" -> "ERROR"
                            out.status == Status.WARN || payload.status == "WARN" -> "WARN"
                            else -> payload.status
                        }
                        show(payload.copy(checkHost = "", status = status, rows = payload.rows + out.rows), false)
                    }
                }.start()
            })
        }
        buttons.addView(pill("DISMISS", Color.argb(0x26, 255, 255, 255), Color.parseColor("#D0D0D6")) { finish() })
        cardView.addView(buttons)

        root.addView(
            cardView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
                setMargins(dp(14), (resources.displayMetrics.heightPixels * 0.12f).toInt(), dp(14), dp(24))
            }
        )
        setContentView(root)

        if (animate) {
            cardView.alpha = 0f
            cardView.translationY = dp(56).toFloat()
            cardView.animate().alpha(1f).translationY(0f).setDuration(260)
                .setInterpolator(DecelerateInterpolator(1.6f)).start()
        }
    }

    private fun attachDrag(handle: View, card: View) {
        var downY = 0f
        handle.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downY = e.rawY; true }
                MotionEvent.ACTION_MOVE -> { card.translationY = maxOf(0f, e.rawY - downY); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dy = card.translationY
                    if (e.actionMasked == MotionEvent.ACTION_UP && dy > dp(110)) {
                        card.animate().translationY(card.height + dp(48).toFloat()).alpha(0f).setDuration(160)
                            .withEndAction { finishNoAnim() }.start()
                    } else {
                        ValueAnimator.ofFloat(dy, 0f).apply {
                            duration = 180
                            interpolator = DecelerateInterpolator()
                            addUpdateListener { card.translationY = it.animatedValue as Float }
                        }.start()
                    }
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun pill(label: String, bg: Int, fg: Int, onClick: (View) -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 11f
        letterSpacing = 0.08f
        gravity = Gravity.CENTER
        setTextColor(fg)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        background = GradientDrawable().apply {
            setColor(bg)
            cornerRadius = dp(19).toFloat()
        }
        setPadding(dp(18), 0, dp(18), 0)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply { marginStart = dp(8) }
        setOnClickListener(onClick)
    }

    /** A colour and its complement side by side, each labelled with its hex in a readable ink. */
    private class SwatchStrip(context: Context, private val colors: List<Int>) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f * resources.displayMetrics.scaledDensity
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        private val clip = Path()
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            val radius = 16f * resources.displayMetrics.density
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            clip.reset()
            clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            // The colour gets three parts, its complement one.
            val weights = if (colors.size == 2) floatArrayOf(3f, 1f) else FloatArray(colors.size) { 1f }
            val total = weights.sum()
            var x = 0f
            colors.forEachIndexed { index, c ->
                val w = width * weights[index] / total
                fill.color = c
                canvas.drawRect(x, 0f, x + w, height.toFloat(), fill)
                val lum = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0
                ink.color = if (lum > 0.6) Color.argb(0xCC, 0, 0, 0) else Color.argb(0xE6, 255, 255, 255)
                val label = String.format("#%06X", c and 0xFFFFFF)
                if (ink.measureText(label) < w - 16f) {
                    canvas.drawText(label, x + 14f * resources.displayMetrics.density, height - 12f * resources.displayMetrics.density, ink)
                }
                x += w
            }
            canvas.restore()
        }
    }

    private fun finishNoAnim() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    companion object {
        const val EXTRA_PAYLOAD = "EXTRA_PAYLOAD"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
    }
}
