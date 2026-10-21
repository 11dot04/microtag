package com.microtag.ui

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path as NativePath
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.microtag.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.width
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs
// ==========================================================
// THE MONOLITH
// The count is one huge numeral set on its side, reading up the left edge of the page, with a couple of
// flat diagonal bands overprinted across its foot (where numeral and band overlap, the colours mix).
// A tiny annotation column and a vertical hour-by-hour readout sit to its right. There is no coloured slab:
// the poster is the page. Scroll, and the numeral swings upright and shrinks into the dock while the tiles take
// over. Pausing outlines the numeral.
// ==========================================================

private const val NUMERAL_LS = -0.02f   // em; tune to taste for the hero font

/** The hero numeral's typeface: res/font/style.ttf. Swap the file, never the name. */
private fun heroTypeface(context: android.content.Context): Typeface? = ResourcesCompat.getFont(context, R.font.style)
private val Mono: FontFamily get() = TagFonts.data

private fun numeralInk(typeface: Typeface?, text: String): Rect {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = 100f
        letterSpacing = NUMERAL_LS
    }
    return Rect().also { p.getTextBounds(text, 0, text.length, it) }
}

private fun smooth(t: Float) = t * t * (3f - 2f * t)

/** The numeral lies along the left edge: its length fills most of the poster, its thickness stays modest. */
private fun fitSize(
    l100: Float, h100: Float, wPx: Float, posterPx: Float, margin: Float, bottomMargin: Float, controlsBottom: Float
): Float {
    val availLen = min(posterPx * 0.62f, posterPx - bottomMargin - controlsBottom).coerceAtLeast(1f)
    val availThick = (wPx * 0.42f - margin).coerceAtLeast(1f)
    return min(availLen / l100 * 100f, availThick / h100 * 100f)
}

/** Where the numeral sits in the poster state (rotated -90°, starting at the bottom-left and reading upward). */
private class NumeralGeo(val size: Float, val capH: Float, val length: Float, val x0: Float, val y0: Float)

private fun numeralGeo(
    typeface: Typeface?, count: Int, wPx: Float, posterPx: Float, margin: Float, bottomMargin: Float, controlsBottom: Float
): NumeralGeo {
    val ink = numeralInk(typeface, count.toString())
    val l100 = (ink.right - ink.left).toFloat().coerceAtLeast(1f)
    val h100 = (ink.bottom - ink.top).toFloat().coerceAtLeast(1f)
    val size = fitSize(l100, h100, wPx, posterPx, margin, bottomMargin, controlsBottom)
    val capH = h100 * size / 100f
    return NumeralGeo(size, capH, l100 * size / 100f, margin + capH, posterPx - bottomMargin)
}

// ----------------------------------------------------------
// THE POSTER (scrolls with the page)
// ----------------------------------------------------------

@Composable
fun MonolithPoster(
    height: Dp,
    count: Int,
    hourly: IntArray,
    paused: Boolean,
    perms: Permissions,
    onTogglePause: () -> Unit,
    onStats: () -> Unit,
    onLogs: () -> Unit,
    active: Boolean = true
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val ink = TagColors.White
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "dev"
    }
    val date = remember { SimpleDateFormat("dd—MM—yy", Locale.US).format(Date()) }
    val today = remember(hourly) { hourly.sum() }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    BoxWithConstraints(Modifier.fillMaxWidth().height(height)) {
        val typeface = remember { heroTypeface(context) }
        val wPx = constraints.maxWidth.toFloat()
        val posterPx = constraints.maxHeight.toFloat()
        val margin = with(density) { 20.dp.toPx() }
        val bottomMargin = with(density) { 28.dp.toPx() }
        val controlsBottom = with(density) { (statusTop + 132.dp).toPx() }
        val geo = remember(count, wPx, posterPx, typeface, controlsBottom) {
            numeralGeo(typeface, count, wPx, posterPx, margin, bottomMargin, controlsBottom)
        }

        if (TagColors.MicroOn) PosterArt(geo, hourly, paused, active, margin, Modifier.fillMaxSize())

        // The annotation column, to the right of the numeral and level with its far end.
        // The hour ruler is a slim strip on the right edge (ticks reach 24dp, labels sit left of them).
        val axisX = wPx - margin - with(density) { 24.dp.toPx() }
        val metaX = geo.x0 + with(density) { 16.dp.toPx() }
        val metaTop = geo.y0 - geo.length
        val metaWidth = with(density) { (axisX - with(density) { 30.dp.toPx() } - metaX).coerceAtLeast(60f).toDp() }
        if (!TagColors.MicroOn) {
            // Micrographics off: one plain caption in place of the annotation column.
            Column(Modifier.offset { IntOffset(metaX.roundToInt(), metaTop.roundToInt()) }.width(metaWidth)) {
                Text("INTERCEPTIONS", maxLines = 1, softWrap = false, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp, color = ink))
                Text("so far", maxLines = 1, softWrap = false, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = TagColors.Outline))
            }
        } else Column(
            Modifier
                .offset { IntOffset(metaX.roundToInt(), metaTop.roundToInt()) }
                .width(metaWidth),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "—" + today.toString().padStart(2, '0'),
                modifier = Modifier.clearAndSetSemantics { },
                style = mono(16, ink, FontWeight.Bold, 0f)
            )
            MicroLabel("Today", color = TagColors.Outline)
            Spacer(Modifier.height(8.dp))
            MicroLabel("Interceptions", color = ink, bold = true, wrap = false)
            MicroLabel("So far", color = TagColors.Outline, wrap = false)
            Spacer(Modifier.height(8.dp))
            MicroLabel("( $date )", color = TagColors.Outline, wrap = false)
            MicroLabel("V$versionName", color = TagColors.Outline)
            MicroLabel("傍受", color = TagColors.White)
            if (paused) {
                Spacer(Modifier.height(6.dp))
                MicroLabel("Paused", color = TagColors.Lime, bold = true)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PixelGlyph(Pixel.Bell, size = 14.dp, color = TagColors.Outline)
                MicroGlyph(Micro.Slashes, size = 14.dp, color = TagColors.Outline)
                PixelGlyph(Pixel.Bolt, size = 14.dp, color = TagColors.Outline)
            }
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(14.dp))
            Image(
                painter = painterResource(R.drawable.ic_tag_wordmark),
                contentDescription = "TAG",
                colorFilter = ColorFilter.tint(ink),
                modifier = Modifier.height(30.dp).aspectRatio(778f / 293f)
            )

            AnimatedVisibility(
                visible = !perms.hasListener || !perms.hasNotif,
                enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    PermissionBanner(perms)
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                ControlSegment(TagShapes.JoinLeft, paused, onTogglePause) { tint ->
                    TagIcon(if (paused) R.drawable.ic_play else R.drawable.ic_pause, tint, size = 26.dp)
                }
                ControlSegment(RoundedCornerShape(6.dp), paused, onStats) { tint ->
                    TagIcon(R.drawable.ic_stats, tint, size = 26.dp)
                }
                ControlSegment(TagShapes.JoinRight, paused, onLogs, padH = 24.dp) { tint ->
                    Text(
                        "Logs",
                        style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Black, fontSize = 20.sp, color = tint)
                    )
                }
            }
        }
    }
}

private fun mono(size: Int, color: Color, weight: FontWeight, spacing: Float) = TextStyle(
    fontFamily = Mono, fontWeight = weight, fontSize = size.sp, lineHeight = (size + 4).sp,
    letterSpacing = spacing.sp, color = color
)

/** The poster has no slab behind it any more, so the buttons are raised surfaces; paused, the segments light up. */
@Composable
private fun ControlSegment(
    shape: Shape,
    paused: Boolean,
    onClick: () -> Unit,
    padH: Dp = 22.dp,
    content: @Composable (tint: Color) -> Unit
) {
    val view = LocalView.current
    Box(
        Modifier
            .height(54.dp)
            .clip(shape)
            .background(if (paused) TagColors.Lime else TagColors.Surface)
            .clickable { view.tick(Tick.Press); onClick() }
            .padding(horizontal = padH),
        contentAlignment = Alignment.Center
    ) { content(if (paused) TagColors.OnLime else TagColors.AccentText) }
}

/**
 * The micrographic furniture of the poster: registration marks around the numeral, and a vertical
 * hour-by-hour readout (hour 0 at the numeral's foot, 23 at the top, so time reads upward like the numeral).
 * Each row is up to eight dots; the current hour pulses.
 */
@Composable
private fun PosterArt(
    geo: NumeralGeo, hourly: IntArray, paused: Boolean, active: Boolean, marginPx: Float, modifier: Modifier
) {
    val ripple = remember { Animatable(1f) }
    val total = hourly.sum()
    var lastTotal by remember { mutableIntStateOf(total) }
    LaunchedEffect(total) {
        if (total != lastTotal) {
            lastTotal = total
            if (TagMotion.enabled) {
                ripple.snapTo(0f)
                ripple.animateTo(1f, tween(1300, easing = FastOutSlowInEasing))
            }
        }
    }
    val pulse by rememberInfiniteTransition(label = "nowPulse").animateFloat(
        initialValue = if (TagMotion.enabled) 0.35f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )
    val nowHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val labelPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

    Canvas(modifier) {
        val ink = TagColors.White
        val accent = TagColors.Lime
        val mark = TagColors.Outline
        val step = 14.dp.toPx()
        // A hairline ruler down the right margin. Each hour is one tick that grows leftward in proportion to
        // that hour's interceptions; the current hour's tick is the accent and pulses. Hour 0 is at the numeral's
        // foot and 23 at the top, so time reads upward like the numeral does.
        val lineX = size.width - marginPx
        val bottomY = geo.y0
        val maxHour = max(1, hourly.max())
        val minTick = 3.dp.toPx()
        val maxTick = 24.dp.toPx()
        val pulseNow = if (active) pulse else 1f
        val stroke = 1.2.dp.toPx()

        drawLine(mark.copy(alpha = 0.35f), Offset(lineX, bottomY + 4.dp.toPx()), Offset(lineX, bottomY - 23 * step - 4.dp.toPx()), stroke)
        for (h in 0 until 24) {
            val y = bottomY - h * step
            val dRow = abs(h - nowHour) / 24f
            val w = if (ripple.value < 1f) exp(-((dRow - ripple.value) * (dRow - ripple.value)) / 0.004f) else 0f
            val v = if (hourly[h] <= 0) 0f else hourly[h] / maxHour.toFloat()
            val len = (minTick + v * (maxTick - minTick)) * (1f + 0.6f * w)
            val isNow = h == nowHour
            val color = when {
                isNow -> accent.copy(alpha = pulseNow)
                v > 0f -> (if (paused) TagColors.Dim else ink).copy(alpha = 0.55f)
                else -> mark.copy(alpha = 0.28f)
            }
            drawLine(color, Offset(lineX, y), Offset(lineX - len - (if (isNow) 3.dp.toPx() else 0f), y), if (isNow) stroke * 1.6f else stroke)
        }
        labelPaint.textSize = 10.sp.toPx()
        labelPaint.textAlign = Paint.Align.RIGHT
        labelPaint.typeface = Typeface.MONOSPACE
        labelPaint.color = mark.toArgb()
        drawIntoCanvas { c ->
            for (h in listOf(0, 12, 23)) {
                c.nativeCanvas.drawText("%02d".format(h), lineX - maxTick - 10.dp.toPx(), bottomY - h * step + 4.dp.toPx(), labelPaint)
            }
        }

        // registration marks around the numeral's bounding box, and an arrow for the way it reads
        val left = marginPx
        val right = geo.x0
        val top = geo.y0 - geo.length
        val g = 9.dp.toPx()
        val sz = 9.dp.toPx()
        microGlyph(Micro.Cross, Offset(left - g, top - g), sz, mark)
        microGlyph(Micro.Cross, Offset(left - g, geo.y0 + g), sz, mark)
        microGlyph(Micro.Cross, Offset(right + g, geo.y0 + g), sz, mark)
        microGlyph(Micro.ArrowUp, Offset((left + right) / 2f, top - 16.dp.toPx()), 12.dp.toPx(), mark)
        microGlyph(Micro.Register, Offset(lineX - 8.dp.toPx(), bottomY - 23 * step - 22.dp.toPx()), 12.dp.toPx(), mark)
    }
}

// ----------------------------------------------------------
// THE OVERLAY (fixed to the screen: bands, numeral, dock, gear)
// ----------------------------------------------------------

@Composable
fun MonolithOverlay(
    count: Int,
    paused: Boolean,
    scroll: ScrollState,
    posterHeight: Dp,
    collapseDistance: Float,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    onTogglePause: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val typeface = remember { heroTypeface(context) }

    // The numeral spins up from zero and lands with a detent (haptic tick plus a spring kick).
    // That runs on every launch and every return from the background, not just the first
    // composition. After that it rolls digit by digit.
    val spin = remember { Animatable(0f) }
    val kick = remember { Animatable(1f) }
    var spun by remember { mutableStateOf(!TagMotion.enabled) }
    var replays by remember { mutableIntStateOf(0) }
    DisposableEffect(view) {
        var stopped = false
        val owner = view.findViewTreeLifecycleOwner()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> stopped = true
                // Attaching to a started lifecycle replays ON_START once; only a real return counts.
                Lifecycle.Event.ON_START -> if (stopped) { stopped = false; replays++ }
                else -> Unit
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(replays) {
        // Reduced motion, or the poster is scrolled away (the docked counter shouldn't spin): just show the value.
        if (!TagMotion.enabled || scroll.value > collapseDistance * 0.5f) {
            spun = true
            return@LaunchedEffect
        }
        spun = false
        spin.snapTo(0f)
        spin.animateTo(1f, tween(1100, easing = TagEase.Reveal))
        spun = true
        view.tick(Tick.Press)
        kick.snapTo(1.07f)
        kick.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
    }
    val digits = (if (spun) count else (count * spin.value).roundToInt()).toString()
    var lastCount by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (spun && count > lastCount && TagMotion.enabled) {
            kick.snapTo(1.07f)
            kick.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
        }
        lastCount = count
    }
    val topDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth.toFloat()
        val posterPx = with(density) { posterHeight.toPx() }
        val margin = with(density) { 20.dp.toPx() }
        val bottomMargin = with(density) { 28.dp.toPx() }
        val statusTop = with(density) { topDp.toPx() }
        val controlsBottom = statusTop + with(density) { 132.dp.toPx() }
        val dockInk = with(density) { 30.dp.toPx() }
        val dockDist = collapseDistance

        // Odometer: digits that changed roll up, aligned from the right.
        var shown by remember { mutableStateOf(digits) }
        var previous by remember { mutableStateOf(digits) }
        val roll = remember { Animatable(1f) }
        LaunchedEffect(digits) {
            if (digits != shown) {
                previous = shown
                shown = digits
                if (spun && TagMotion.enabled) {
                    roll.snapTo(0f)
                    roll.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 180f))
                } else roll.snapTo(1f)
            }
        }

        // Everything on screen is drawn from `shown`, the same value the roll animation is keyed to.
        // The size comes from the final number so the numeral doesn't breathe while it spins up.
        val ink = remember(shown, typeface) { numeralInk(typeface, shown) }
        val inkSize = remember(count, typeface) { numeralInk(typeface, count.toString()) }
        val l100 = (inkSize.right - inkSize.left).toFloat().coerceAtLeast(1f)
        val h100 = (inkSize.bottom - inkSize.top).toFloat().coerceAtLeast(1f)
        val targetSize = fitSize(l100, h100, wPx, posterPx, margin, bottomMargin, controlsBottom)
        val sizePx by animateFloatAsState(
            targetValue = targetSize,
            animationSpec = if (TagMotion.enabled) spring(dampingRatio = 0.55f, stiffness = 140f) else snap(),
            label = "numeralSize"
        )

        fun dockProgress(): Float = smooth((scroll.value / dockDist).coerceIn(0f, 1f))

        val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
        val bandPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL } }
        val overlapPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL } }
        val band1 = remember { NativePath() }
        val band2 = remember { NativePath() }
        val glyph = remember { NativePath() }
        val tmp = remember { NativePath() }
        val matrix = remember { Matrix() }
        val accent = TagColors.Lime.toArgb()

        // Where the numeral is at dock progress p: its origin (the start of its baseline), turn and scale.
        // At p = 0 it lies on its side (-90°) at the foot of the poster; at p = 1 it is upright in the dock.
        class Pose(val ox: Float, val oy: Float, val angle: Float, val scale: Float)
        fun poseAt(p: Float, kickScale: Float): Pose {
            val capH = h100 * sizePx / 100f
            val x0 = margin + capH
            val y0 = posterPx - bottomMargin
            val dockX = margin
            val dockY = statusTop + with(density) { 12.dp.toPx() } + dockInk
            val sEnd = dockInk / capH
            return Pose(
                ox = x0 + (dockX - x0) * p,
                oy = y0 + (dockY - y0) * p,
                angle = -90f * (1f - p),
                scale = (1f + (sEnd - 1f) * p) * (1f + (kickScale - 1f) * (1f - p))
            )
        }

        // the bands and the numeral
        Canvas(Modifier.fillMaxSize()) {
            val p = dockProgress()
            val s0 = sizePx
            val capH = h100 * s0 / 100f
            val inkLeft = ink.left * s0 / 100f
            val pose = poseAt(p, kick.value)
            val rolling = roll.value < 1f

            // --- two flat diagonal bands at the foot of the poster; they scroll away with it
            val sy = -scroll.value.toFloat()
            val baseY = posterPx * 0.93f + sy
            fun band(path: NativePath, ax: Float, len: Float, bw: Float) {
                path.rewind()
                path.moveTo(ax, baseY)
                path.lineTo(ax + len, baseY - len)
                path.lineTo(ax + len + bw, baseY - len)
                path.lineTo(ax + bw, baseY)
                path.close()
            }
            band(band1, -wPx * 0.10f, wPx * 0.46f, wPx * 0.22f)
            band(band2, wPx * 0.18f, wPx * 0.30f, wPx * 0.09f)
            val bandAlpha = (1f - p) * (if (paused) 0.22f else 1f)
            val c1 = TagColors.Lime
            val c2 = if (TagColors.HasTones) TagColors.Secondary else TagColors.Lime.copy(alpha = 0.55f)

            drawIntoCanvas { canvas ->
                val n = canvas.nativeCanvas
                if (bandAlpha > 0.01f) {
                    bandPaint.color = c1.toArgb(); bandPaint.alpha = (c1.alpha * 255 * bandAlpha).toInt()
                    n.drawPath(band1, bandPaint)
                    bandPaint.color = c2.toArgb(); bandPaint.alpha = (c2.alpha * 255 * bandAlpha).toInt()
                    n.drawPath(band2, bandPaint)
                }

                paint.typeface = typeface
                paint.textSize = s0
                paint.letterSpacing = NUMERAL_LS
                val numeralColor = TagColors.White
                if (paused) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3.dp.toPx() / pose.scale
                    paint.color = accent
                } else {
                    paint.style = Paint.Style.FILL
                    paint.color = numeralColor.toArgb()
                }
                n.save()
                n.translate(pose.ox, pose.oy)
                n.rotate(pose.angle)
                n.scale(pose.scale, pose.scale)
                var x = -inkLeft
                val adv = capH * 1.25f
                shown.forEachIndexed { i, ch ->
                    val str = ch.toString()
                    val w = paint.measureText(str)
                    val oldIndex = previous.length - (shown.length - i)
                    val old = if (oldIndex >= 0) previous[oldIndex] else null
                    if (rolling && old != ch) {
                        val e = roll.value
                        n.save()
                        n.clipRect(x - 4f, -capH * 1.35f, x + w + 4f, capH * 0.35f)
                        val baseAlpha = paint.alpha
                        if (old != null) {
                            paint.alpha = (baseAlpha * (1f - e).coerceIn(0f, 1f)).toInt()
                            n.drawText(old.toString(), x, -e * adv, paint)
                        }
                        paint.alpha = (baseAlpha * e.coerceIn(0f, 1f)).toInt()
                        n.drawText(str, x, (1f - e) * adv, paint)
                        paint.alpha = baseAlpha
                        n.restore()
                    } else {
                        n.drawText(str, x, 0f, paint)
                    }
                    x += w
                }
                n.restore()

                // --- overprint: where numeral and band overlap, the two colours mix
                if (!paused && !rolling && bandAlpha > 0.05f) {
                    paint.style = Paint.Style.FILL
                    glyph.rewind()
                    paint.getTextPath(shown, 0, shown.length, -inkLeft, 0f, glyph)
                    matrix.reset()
                    matrix.postScale(pose.scale, pose.scale)
                    matrix.postRotate(pose.angle)
                    matrix.postTranslate(pose.ox, pose.oy)
                    glyph.transform(matrix)
                    fun overlap(band: NativePath, fill: Color) {
                        tmp.set(glyph)
                        if (!tmp.op(band, NativePath.Op.INTERSECT)) return
                        val mixed = if (TagColors.IsLight) {
                            // multiply, like ink printed over ink
                            Color(numeralColor.red * fill.red, numeralColor.green * fill.green, numeralColor.blue * fill.blue)
                        } else {
                            lerp(fill, numeralColor, 0.55f)
                        }
                        overlapPaint.color = mixed.toArgb()
                        overlapPaint.alpha = (255 * bandAlpha).toInt()
                        n.drawPath(tmp, overlapPaint)
                    }
                    overlap(band1, c1)
                    overlap(band2, c2)
                }
            }
        }

        // Tap the numeral for the stats. The hit box tracks the numeral (turn and all) through the dock animation.
        val lengthRatio = l100 / h100
        fun box(): IntArray {
            val pose = poseAt(dockProgress(), 1f)
            val capH = h100 * sizePx / 100f * pose.scale
            val len = capH * lengthRatio
            val a = Math.toRadians(pose.angle.toDouble())
            val ca = kotlin.math.cos(a).toFloat()
            val sa = kotlin.math.sin(a).toFloat()
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for ((lx, ly) in listOf(0f to 0f, len to 0f, 0f to -capH, len to -capH)) {
                val sx = pose.ox + lx * ca - ly * sa
                val sy = pose.oy + lx * sa + ly * ca
                minX = min(minX, sx); maxX = max(maxX, sx); minY = min(minY, sy); maxY = max(maxY, sy)
            }
            return intArrayOf(minX.roundToInt(), minY.roundToInt(), (maxX - minX).roundToInt().coerceAtLeast(1), (maxY - minY).roundToInt().coerceAtLeast(1))
        }
        Box(
            Modifier
                .offset { val b = box(); IntOffset(b[0], b[1]) }
                .layout { measurable, _ ->
                    val b = box()
                    val placeable = measurable.measure(Constraints.fixed(b[2], b[3]))
                    layout(b[2], b[3]) { placeable.place(0, 0) }
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    view.tick(Tick.Press)
                    onStats()
                }
        )

        // dock caption
        val dockInkW = with(density) { (lengthRatio * dockInk).toDp() }
        Text(
            "INTERCEPTIONS",
            modifier = Modifier
                .offset(x = 20.dp + dockInkW + 10.dp, y = topDp + 16.dp)
                .graphicsLayer { alpha = dockProgress() },
            style = mono(11, TagColors.White, FontWeight.Bold, 1f)
        )

        // gear, and the pause button once the poster has scrolled away
        val dockShown by remember { derivedStateOf { scroll.value > dockDist * 0.6f } }
        Row(
            Modifier.align(Alignment.TopEnd).padding(top = topDp + 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .graphicsLayer { alpha = dockProgress() }
                    .clip(CircleShape)
                    .clickable(enabled = dockShown) { view.tick(Tick.Press); onTogglePause() },
                contentAlignment = Alignment.Center
            ) {
                TagIcon(if (paused) R.drawable.ic_play else R.drawable.ic_pause, if (paused) TagColors.AccentText else TagColors.White, size = 24.dp)
            }
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable { view.tick(Tick.Press); onSettings() },
                contentAlignment = Alignment.Center
            ) { TagIcon(R.drawable.ic_settings, TagColors.White, size = 32.dp) }
        }
    }
}
