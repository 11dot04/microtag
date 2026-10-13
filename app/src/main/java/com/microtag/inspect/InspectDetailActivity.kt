package com.microtag.inspect

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.microtag.core.CapsuleCopyReceiver
import com.microtag.core.MicrotagReminder

/**
 * The card shown when an Inspect pill is tapped. It runs in its own task (see the manifest), so it
 * opens over whatever screen the user is on and closing it returns there instead of to Microtag.
 * This is the interim View-based version; the Compose glass sheet replaces it later.
 */
class InspectDetailActivity : Activity() {

    private val lime = Color.parseColor("#CCFF00")
    private val warn = Color.parseColor("#FFB74D")
    private val danger = Color.parseColor("#FF5A5A")
    private val grey = Color.parseColor("#8E8E93")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        applyBlur()
        render(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render(intent)
    }

    private fun applyBlur() {
        var scrim = 0x4D
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (wm.isCrossWindowBlurEnabled) {
                val lp = window.attributes
                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                lp.blurBehindRadius = 40
                window.attributes = lp
            } else {
                scrim = 0x99 // no blur available (battery saver, OEM setting): darken more instead
            }
        } else {
            scrim = 0x99
        }
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.argb(scrim, 0, 0, 0)))
    }

    @Suppress("DEPRECATION")
    private fun payloadFrom(i: Intent): InspectPayload? =
        if (Build.VERSION.SDK_INT >= 33) {
            i.getSerializableExtra(EXTRA_PAYLOAD, InspectPayload::class.java)
        } else {
            i.getSerializableExtra(EXTRA_PAYLOAD) as? InspectPayload
        }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun render(i: Intent) {
        val payload = payloadFrom(i)
        val notifId = i.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (payload == null) {
            finish()
            return
        }
        // The pill has done its job once the card is open.
        if (notifId != -1) MicrotagReminder.cancelCapsule(applicationContext, notifId)

        val statusColor = when (payload.status) {
            "WARN" -> warn
            "ERROR" -> danger
            else -> lime
        }

        val root = FrameLayout(this).apply { setOnClickListener { finish() } }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F2101114"))
                cornerRadius = dp(28).toFloat()
            }
            isClickable = true
        }

        // Small domain label, then the hero value and the grey source line.
        if (payload.domain.isNotBlank()) {
            card.addView(TextView(this).apply {
                text = payload.domain
                textSize = 11f
                letterSpacing = 0.14f
                setTextColor(statusColor)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            })
        }
        card.addView(TextView(this).apply {
            text = payload.heroText
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(0, dp(4), 0, 0)
            setTextIsSelectable(true)
        })
        if (payload.subText.isNotBlank()) {
            card.addView(TextView(this).apply {
                text = payload.subText
                textSize = 13f
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
        card.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(10), 0, 0)
        }
        val copyValue = payload.copyValue
        if (copyValue.isNotBlank()) {
            buttons.addView(pill("COPY", lime, Color.BLACK) {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                CapsuleCopyReceiver.copy(this, copyValue)
                finish()
            })
        }
        val url = payload.url
        if (url.startsWith("https://") || url.startsWith("http://")) {
            buttons.addView(pill("OPEN", Color.parseColor("#2A2B30"), Color.WHITE) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                }
                finish()
            })
        }
        buttons.addView(pill("DISMISS", Color.parseColor("#222226"), Color.parseColor("#CCCCCC")) { finish() })
        card.addView(buttons)

        root.removeAllViews()
        root.addView(
            card,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
                setMargins(dp(14), (resources.displayMetrics.heightPixels * 0.12f).toInt(), dp(14), dp(24))
            }
        )
        setContentView(root)
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
            cornerRadius = dp(18).toFloat()
        }
        setPadding(dp(18), 0, dp(18), 0)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply { marginStart = dp(8) }
        setOnClickListener(onClick)
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
