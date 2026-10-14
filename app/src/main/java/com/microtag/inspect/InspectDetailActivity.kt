package com.microtag.inspect

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.R
import com.microtag.core.CapsuleCopyReceiver
import com.microtag.core.MicrotagReminder
import com.microtag.ui.FitText
import com.microtag.ui.Montserrat
import com.microtag.ui.TagColors
import com.microtag.ui.TagMotion
import com.microtag.ui.TagSettings
import com.microtag.ui.TagTheme
import com.microtag.ui.Tick
import com.microtag.ui.tick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Drop Display.ttf into res/font (lowercase: display.ttf) and this resolves. Swap the file to swap the typeface. */
val DisplayFont = FontFamily(Font(R.font.display))

/**
 * The expanded card. Opens in its own task over a blurred backdrop.
 *
 *  - The big value at the bottom and every detail row copy their text when tapped.
 *  - Drag the card down to dismiss; tapping outside it dismisses too. There is no close button.
 *  - Each kind of card has its own outline (CardShapes) and drawing (CardArt).
 *  - The arrow in the corner is a quiet redirect: it opens the link or the calendar event, if there is one.
 */
class InspectDetailActivity : ComponentActivity() {

    private var payload by mutableStateOf<InspectPayload?>(null)
    private var busy by mutableStateOf("")
    private var firstShow = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                if (p != null) CardRoot(p)
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
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.argb(scrim, 0, 0, 0)))
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

    private fun isWeb(url: String) = url.startsWith("https://") || url.startsWith("http://")

    private fun openUrl(url: String) {
        if (!isWeb(url)) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
        finish()
    }

    private fun openEvent(id: Long) {
        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
        finish()
    }

    private fun share(text: String) {
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
        finish()
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

    // ------------------------------------------------------ the card

    @Composable
    private fun CardRoot(p: InspectPayload) {
        val accent = accentFor(p)
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

        val shape = remember(p.domain) { CardShapes.shapeFor(p.domain) }
        val isColour = p.domain == "COLOUR"
        val big = p.bigText
        val copyValue = p.copyValue.ifBlank { big }
        val hero = if (isColour) p.heroText else p.subText
        val openTarget: (() -> Unit)? = when {
            p.eventId > 0L -> ({ openEvent(p.eventId) })
            isWeb(p.url) && p.domain != "DIMS" -> ({ openUrl(p.url) })
            else -> null
        }

        Box(Modifier.fillMaxSize().plainClick(dismiss)) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 24.dp)
                    .offset { IntOffset(0, off.value.roundToInt()) }
                    .widthIn(max = 520.dp)
                    .clip(shape)
                    .background(TagColors.Black.copy(alpha = 0.97f))
                    .border(1.dp, accent.copy(alpha = 0.28f), shape)
                    // Taps on the card are the card's own; they must not fall through to the dismiss layer.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .pointerInput(Unit) {
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
                    .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 22.dp)
            ) {
                // Label and the quiet arrow.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LabelPill(labelFor(p), accent)
                    Spacer(Modifier.weight(1f))
                    ArrowGlyph(active = openTarget != null, onClick = { openTarget?.invoke() })
                }
                Spacer(Modifier.height(14.dp))

                if (hero.isNotBlank() && hero != big) {
                    FitText(
                        AnnotatedString(hero),
                        style = TextStyle(
                            fontFamily = Montserrat,
                            fontWeight = if (isColour) FontWeight.Black else FontWeight.Medium,
                            fontSize = if (isColour) 38.sp else 17.sp,
                            lineHeight = if (isColour) 42.sp else 22.sp,
                            color = if (isColour) TagColors.White else TagColors.White.copy(alpha = 0.72f)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        minSize = if (isColour) 22.sp else 12.sp
                    )
                }

                Spacer(Modifier.height(12.dp))
                CardArt(p, accent, onCopy)
                Spacer(Modifier.height(10.dp))

                // Detail rows. For a colour, the name, hex and complement live elsewhere on the card.
                val hidden = if (isColour) setOf("HEX", "Name", "Complement", "Readable with") else emptySet()
                p.rows.filter { it.first !in hidden }.take(7).forEach { (k, v) ->
                    DetailRow(k, v, flash == v, accent, onCopy)
                }
                if (isColour) {
                    p.rows.firstOrNull { it.first == "Readable with" }?.let { row ->
                        Spacer(Modifier.height(10.dp))
                        ReadableChip(row.second, p.palette.firstOrNull()?.let { Color(it) } ?: accent, onCopy)
                    }
                }
                if (p.fullContent.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.Text(
                        p.fullContent.take(420),
                        style = TextStyle(
                            fontFamily = Montserrat, fontWeight = FontWeight.Light, fontSize = 13.sp, lineHeight = 19.sp,
                            color = TagColors.White.copy(alpha = 0.82f)
                        ),
                        maxLines = 7,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.plainClick { onCopy(p.fullContent) }
                    )
                }

                Spacer(Modifier.height(14.dp))
                Actions(p, accent, copyValue)

                // The big value: tap to copy.
                Spacer(Modifier.height(4.dp))
                FitText(
                    AnnotatedString(big),
                    style = TextStyle(
                        fontFamily = DisplayFont, fontSize = 84.sp, lineHeight = 88.sp,
                        color = if (flash == copyValue) accent else TagColors.White
                    ),
                    modifier = Modifier.fillMaxWidth().plainClick { onCopy(copyValue) },
                    minSize = 24.sp
                )
            }
        }
    }

    @Composable
    private fun Actions(p: InspectPayload, accent: Color, copyValue: String) {
        val list = ArrayList<ActionSpec>()
        if (p.resolveUrl.isNotBlank()) list.add(ActionSpec(if (busy == "RESOLVE") "…" else "RESOLVE") { runResolve(p) })
        if (p.checkHost.isNotBlank()) list.add(ActionSpec(if (busy == "CHECK") "…" else "CHECK") { runCheck(p) })
        if (p.eventId > 0L) list.add(ActionSpec("EVENT") { openEvent(p.eventId) })
        if (isWeb(p.url) && p.domain != "DIMS") list.add(ActionSpec("OPEN") { openUrl(p.url) })
        list.add(ActionSpec("SHARE") { share(copyValue) })

        val view = LocalView.current
        val heights = listOf(40.dp, 34.dp, 44.dp, 36.dp, 38.dp)
        val padX = listOf(18.dp, 14.dp, 22.dp, 14.dp, 16.dp)
        val ys = listOf(0.dp, 9.dp, 2.dp, 11.dp, 4.dp)
        val shapes = remember {
            listOf<Shape>(
                CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp),
                RoundedCornerShape(50),
                TicketShape(
                    TicketSpec(notches = listOf(Notch(Edge.Left, 0.5f, 14f, 5f, Bite.Semi), Notch(Edge.Right, 0.5f, 14f, 5f, Bite.Semi)))
                ),
                RoundedCornerShape(topStart = 18.dp, bottomEnd = 18.dp, topEnd = 4.dp, bottomStart = 4.dp),
                CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp)
            )
        }
        val onAccent = if (accent.luminance() > 0.4f) Color.Black else Color.White
        Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            list.forEachIndexed { i, a ->
                val k = i % heights.size
                val primary = i == 0
                Box(
                    Modifier
                        .offset(y = ys[k])
                        .height(heights[k])
                        .clip(shapes[k])
                        .then(
                            if (primary) Modifier.background(accent)
                            else Modifier.border(1.dp, accent.copy(alpha = 0.7f), shapes[k])
                        )
                        .plainClick {
                            view.tick(Tick.Press)
                            a.onClick()
                        }
                        .padding(horizontal = padX[k]),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        a.label,
                        style = TextStyle(
                            fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.3.sp,
                            color = if (primary) onAccent else accent
                        )
                    )
                }
            }
        }
    }

    private class ActionSpec(val label: String, val onClick: () -> Unit)

    // ------------------------------------------------------ pieces

    @Composable
    private fun LabelPill(text: String, accent: Color) {
        Box(Modifier.border(1.dp, accent, RoundedCornerShape(50)).padding(horizontal = 13.dp, vertical = 5.dp)) {
            androidx.compose.material3.Text(
                text,
                style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.8.sp, color = accent)
            )
        }
    }

    /** A heavy, square-capped north-west arrow with a faint offset shadow. Deliberately quiet: not white on black. */
    @Composable
    private fun ArrowGlyph(active: Boolean, onClick: () -> Unit) {
        Box(
            Modifier.size(44.dp).then(if (active) Modifier.plainClick(onClick) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(24.dp)) {
                val sw = 3.2.dp.toPx()
                val c = Color(0xFFC3C9D4)
                val a = if (active) 0.55f else 0.34f
                fun glyph(dx: Float, dy: Float, color: Color) {
                    val t = 3.dp.toPx() + dx
                    val u = 3.dp.toPx() + dy
                    val e = 14.dp.toPx()
                    val f = 21.dp.toPx()
                    drawLine(color, Offset(t, u), Offset(t + e, u), strokeWidth = sw, cap = StrokeCap.Square)
                    drawLine(color, Offset(t, u), Offset(t, u + e), strokeWidth = sw, cap = StrokeCap.Square)
                    drawLine(color, Offset(t, u), Offset(t + f - 3.dp.toPx(), u + f - 3.dp.toPx()), strokeWidth = sw, cap = StrokeCap.Square)
                }
                glyph(2.dp.toPx(), 2.dp.toPx(), c.copy(alpha = 0.12f))
                glyph(0f, 0f, c.copy(alpha = a))
            }
        }
    }

    @Composable
    private fun DetailRow(label: String, value: String, flashing: Boolean, accent: Color, onCopy: (String) -> Unit) {
        Column(Modifier.fillMaxWidth().plainClick { onCopy(value) }) {
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
                androidx.compose.material3.Text(
                    label.uppercase(),
                    style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.2.sp, color = Color(0xFF6E737C)),
                    modifier = Modifier.padding(top = 3.dp).widthIn(min = 92.dp, max = 92.dp)
                )
                androidx.compose.material3.Text(
                    value,
                    style = TextStyle(
                        fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp,
                        color = if (flashing) accent else TagColors.White
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
        }
    }

    /** "Black text, 17.9:1", filled with the colour itself. */
    @Composable
    private fun ReadableChip(text: String, fill: Color, onCopy: (String) -> Unit) {
        val on = if (fill.luminance() > 0.4f) Color.Black else Color.White
        Box(
            Modifier
                .clip(CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp))
                .background(fill)
                .plainClick { onCopy(text) }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            androidx.compose.material3.Text(
                text,
                style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.4.sp, color = on)
            )
        }
    }

    @Composable
    private fun Modifier.plainClick(onClick: () -> Unit): Modifier =
        this.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)

    // ------------------------------------------------------ look

    private fun accentFor(p: InspectPayload): Color {
        if (p.status == "ERROR") return Color(0xFFFF5A5A)
        if (p.status == "WARN") return Color(0xFFFFB74D)
        if (p.accent != 0) return Color(p.accent)
        return when (p.domain) {
            "CALC" -> Color(0xFFF9E800)
            "CONV" -> Color(0xFF4DD0E1)
            "MONEY" -> Color(0xFF69F0AE)
            "TIME" -> Color(0xFF8C9EFF)
            "DATE" -> Color(0xFFFFAB40)
            "URL" -> Color(0xFF6C97FF)
            "UPI" -> Color(0xFFB388FF)
            "IFSC" -> Color(0xFFFFD54F)
            "PIN" -> Color(0xFFFF8A65)
            "NUM" -> Color(0xFF80DEEA)
            "DEFINE" -> Color(0xFFF48FB1)
            "WIKI" -> Color(0xFFE8E8E8)
            "DIMS" -> Color(0xFF7AA2FF)
            else -> TagColors.Lime
        }
    }

    private fun labelFor(p: InspectPayload): String {
        val base = when (p.domain) {
            "COLOUR" -> "COLOUR"
            "CALC" -> "CALC"
            "CONV" -> "CONVERT"
            "MONEY" -> "MONEY"
            "TIME" -> "TIME"
            "DATE" -> "DATE"
            "URL" -> "LINK"
            "UPI" -> "UPI"
            "IFSC" -> "BANK"
            "PIN" -> "POST"
            "NUM" -> "NUMBER"
            "DEFINE" -> "DEFINE"
            "WIKI" -> "WIKI"
            "DIMS" -> "DIMENSIONS"
            else -> "TEXT"
        }
        return when (p.status) {
            "WARN" -> "$base · CAUTION"
            "ERROR" -> "$base · ERROR"
            else -> base
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
