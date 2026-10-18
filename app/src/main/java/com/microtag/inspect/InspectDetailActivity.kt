package com.microtag.inspect

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.microtag.core.CapsuleCopyReceiver
import com.microtag.core.MicrotagReminder
import com.microtag.ui.TagColors
import com.microtag.ui.TagMotion
import com.microtag.ui.TagSettings
import com.microtag.ui.TagTheme
import com.microtag.ui.Tick
import com.microtag.ui.tick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The expanded card (see InspectCard.kt for the card itself). Opens in its own task over a blurred backdrop.
 *
 *  - Drag the card down to dismiss; tapping outside it dismisses too. There is no close button.
 *  - The arrow on the card shares everything on it as text; OPEN / PAY, CHECK, RESOLVE and EVENT are pills.
 */
class InspectDetailActivity : ComponentActivity() {

    private var payload by mutableStateOf<InspectPayload?>(null)
    private var busy by mutableStateOf("")
    private var firstShow = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent bars: the blurred app behind shows through, not a black strip.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        // This activity can start without MainActivity ever running, so it applies the look-and-feel itself.
        val settings = TagSettings(applicationContext)
        TagColors.useMaterialYou(applicationContext, settings.materialYou)
        TagMotion.enabled = !settings.reducedMotion
        TagMotion.haptics = settings.haptics
        applyBlur()
        accept(intent)
        setContent {
            TagTheme {
                val p = payload
                if (p != null) CardHost(p)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        accept(intent)
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
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.argb(scrim, 0, 0, 0)))
    }

    @Suppress("DEPRECATION")
    private fun payloadFrom(i: Intent): InspectPayload? =
        if (Build.VERSION.SDK_INT >= 33) {
            i.getSerializableExtra(EXTRA_PAYLOAD, InspectPayload::class.java)
        } else {
            i.getSerializableExtra(EXTRA_PAYLOAD) as? InspectPayload
        }

    private fun accept(i: Intent) {
        val p = payloadFrom(i)
        val notifId = i.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (p == null) {
            finish()
            return
        }
        // The pill has done its job once the card is open.
        if (notifId != -1) MicrotagReminder.cancelCapsule(applicationContext, notifId)
        firstShow = payload == null
        payload = p
    }

    // ------------------------------------------------------ actions

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // No UPI app (or browser) to handle it: nothing to open.
            return
        }
        finish()
    }

    private fun openEvent(id: Long) {
        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            return
        }
        finish()
    }

    private fun share(p: InspectPayload) {
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, p.shareText())
            }
            startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            return
        }
        finish()
    }

    /** The pin glyph on any card, and the buttons on a pinned card. */
    private fun pinAction(p: InspectPayload, action: String) {
        val app = applicationContext
        when (action) {
            "pin" -> PinEngine.pin(app, p.copyValue.ifBlank { p.bigText })
            "unpin" -> {
                p.unit.toIntOrNull()?.let { PinEngine.unpin(app, it) }
                finish()
            }
            "extend" -> {
                val id = p.unit.toIntOrNull() ?: return
                val updated = PinEngine.extend(app, id, 60 * 60_000L) ?: return
                payload = p.copy(nums = listOf(updated.createdAt.toDouble(), updated.expiresAt.toDouble()))
            }
        }
    }

    private fun runResolve(p: InspectPayload) {
        if (busy.isNotEmpty()) return
        busy = "RESOLVE"
        Thread {
            val r = LinkCheck.resolveShort(p.resolveUrl)
            val expanded = r.finalUrl?.let { UrlInspector.analyze(it) }
            runOnUiThread {
                busy = ""
                if (isFinishing) return@runOnUiThread
                payload = if (expanded == null) {
                    p.copy(resolveUrl = "", rows = p.rows + ("Resolve" to "Couldn't expand it: ${r.problem ?: "unknown"}"))
                } else {
                    val res = expanded.toResult().toPayload()
                    res.copy(rows = listOf("Expanded from" to p.title) + res.rows)
                }
            }
        }.start()
    }

    private fun runCheck(p: InspectPayload) {
        if (busy.isNotEmpty()) return
        busy = "CHECK"
        Thread {
            val out = LinkCheck.check(p.checkHost)
            runOnUiThread {
                busy = ""
                if (isFinishing) return@runOnUiThread
                val status = when {
                    out.status == Status.ERROR || p.status == "ERROR" -> "ERROR"
                    out.status == Status.WARN || p.status == "WARN" -> "WARN"
                    else -> p.status
                }
                payload = p.copy(checkHost = "", status = status, rows = p.rows + out.rows)
            }
        }.start()
    }

    // ------------------------------------------------------ the host: backdrop, drag, copy feedback

    @Composable
    private fun CardHost(p: InspectPayload) {
        val view = LocalView.current
        val scope = rememberCoroutineScope()
        val animate = remember { TagMotion.enabled && firstShow }
        val off = remember { Animatable(if (animate) 900f else 0f) }
        var flash by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            if (off.value != 0f) off.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
        }
        LaunchedEffect(flash) {
            if (flash.isNotEmpty()) {
                delay(900)
                flash = ""
            }
        }

        val dismiss: () -> Unit = {
            scope.launch {
                off.animateTo(2400f, tween(if (TagMotion.enabled) 170 else 1))
                finish()
            }
        }
        val onCopy: (String) -> Unit = { t ->
            if (t.isNotBlank()) {
                CapsuleCopyReceiver.copy(this@InspectDetailActivity, t)
                view.tick(Tick.Confirm)
                flash = t
            }
        }
        val drag = Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = {
                    scope.launch {
                        if (off.value > 140.dp.toPx()) {
                            off.animateTo(2400f, tween(150))
                            finish()
                        } else {
                            off.animateTo(0f, spring(dampingRatio = 0.8f))
                        }
                    }
                },
                onDragCancel = { scope.launch { off.animateTo(0f, spring(dampingRatio = 0.8f)) } },
                onVerticalDrag = { change, dy ->
                    change.consume()
                    scope.launch { off.snapTo((off.value + dy).coerceAtLeast(0f)) }
                }
            )
        }

        Box(
            Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { dismiss() }
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .systemBarsPadding()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 12.dp)
                    .offset { IntOffset(0, off.value.roundToInt()) }
            ) {
                InspectCard(
                    p = p,
                    busy = busy,
                    flash = flash,
                    onCopy = onCopy,
                    onShare = { share(p) },
                    onOpen = { openLink(p.url) },
                    onCheck = { runCheck(p) },
                    onResolve = { runResolve(p) },
                    onEvent = { openEvent(p.eventId) },
                    dragModifier = drag,
                    onPin = { action -> pinAction(p, action) }
                )
            }
        }
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
