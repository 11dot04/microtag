package com.microtag.ui

import android.graphics.Paint
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

// ==========================================================
// THE MONOLITH
// The first screen is one flat poster. The count is a single huge numeral, bleeding off the right edge,
// over a dot-matrix skyline of today's activity (one column per hour). Scroll, and the numeral shrinks and docks
// into a compact counter while the tiles take over. A strip of tiles peeks below the poster so you know to scroll.
// Pausing floods the poster out to dark and turns the numeral into an outline.
// ==========================================================

private const val NUMERAL_LS = -0.02f   // em; tune to taste for the hero font

/** The hero numeral's typeface: res/font/style.ttf. Swap the file, never the name. */
private fun heroTypeface(context: android.content.Context): Typeface? = ResourcesCompat.getFont(context, R.font.style)
private val Mono = FontFamily.Monospace

private fun numeralInk(typeface: Typeface?, text: String): Rect {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = 100f
        letterSpacing = NUMERAL_LS
    }
    return Rect().also { p.getTextBounds(text, 0, text.length, it) }
}

private fun smooth(t: Float) = t * t * (3f - 2f * t)

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
    val bg by animateColorAsState(if (paused) TagColors.PausedPoster else TagColors.Lime, tween(500), label = "posterBg")
    val fg by animateColorAsState(if (paused) TagColors.White else TagColors.OnLime, tween(500), label = "posterFg")
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "dev"
    }
    val date = remember { SimpleDateFormat("dd.MM.yy", Locale.US).format(Date()) }

    BoxWithConstraints(Modifier.fillMaxWidth().height(height).background(bg)) {
        // The lit columns stop where the numeral stops, which leaves the space above it calm.
        val typeface = remember { heroTypeface(context) }
        val wPx = constraints.maxWidth.toFloat()
        val posterPx = constraints.maxHeight.toFloat()
        val litCap = remember(count, wPx, posterPx, typeface) {
            val ink = numeralInk(typeface, count.toString())
            val iw = (ink.right - ink.left).toFloat().coerceAtLeast(1f)
            val ih = (ink.bottom - ink.top).toFloat().coerceAtLeast(1f)
            min(1.0f * wPx / iw * 100f, posterPx * 0.56f / ih * 100f) * ih / 100f
        }
        DotField(count, hourly, paused, fg, litCap, active, Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(14.dp))
            Image(
                painter = painterResource(R.drawable.ic_tag_wordmark),
                contentDescription = "TAG",
                colorFilter = ColorFilter.tint(fg),
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
            .background(if (paused) TagColors.Lime else TagColors.Black)
            .clickable { view.tick(Tick.Press); onClick() }
            .padding(horizontal = padH),
        contentAlignment = Alignment.Center
    ) { content(if (paused) TagColors.OnLime else TagColors.Lime) }
}

/** 24 columns, one per hour. Lit dots rise from the bottom in proportion to that hour's interceptions. */
@Composable
private fun DotField(count: Int, hourly: IntArray, paused: Boolean, fg: Color, litCapPx: Float, active: Boolean, modifier: Modifier) {
    val ripple = remember { Animatable(1f) }
    var lastCount by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (count != lastCount) {
            lastCount = count
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

    Canvas(modifier) {
        val cols = 24
        val margin = 20.dp.toPx()
        val bottomPad = 28.dp.toPx()
        val step = (size.width - 2 * margin) / (cols - 1)
        val rows = ((size.height - bottomPad - 24.dp.toPx()) / step).toInt().coerceAtLeast(4)
        val maxHour = max(1, hourly.max())
        val capRows = min(rows * 0.82f, max(2f, litCapPx / step))
        val pulseNow = if (active) pulse else 1f
        val dot = 3.dp.toPx()
        val originX = margin + nowHour * step
        val originY = size.height - bottomPad
        val maxR = hypot(max(originX, size.width - originX), originY)
        val base = fg.copy(alpha = 0.14f)
        val lit = if (paused) TagColors.Lime else fg

        for (c in 0 until cols) {
            val litRows = if (hourly[c] <= 0) 0 else max(1, (hourly[c] / maxHour.toFloat() * capRows).roundToInt())
            val x = margin + c * step
            for (r in 0 until rows) {
                val y = size.height - bottomPad - r * step
                var s = dot
                var color = base
                if (r < litRows) { color = lit.copy(alpha = 0.95f); s = dot * 1.25f }
                if (c == nowHour && r == max(litRows - 1, 0)) {
                    // the current hour's marker
                    color = if (paused) TagColors.Lime.copy(alpha = pulseNow) else fg.copy(alpha = pulseNow)
                    s = dot * 1.7f
                }
                if (ripple.value < 1f) {
                    val d = hypot(x - originX, y - originY) / maxR
                    val w = exp(-((d - ripple.value) * (d - ripple.value)) / 0.004f)
                    s *= 1f + 1.4f * w
                    color = color.copy(alpha = min(1f, color.alpha + 0.6f * w))
                }
                drawRect(color, Offset(x - s / 2, y - s / 2), Size(s, s))
            }
        }
    }
}

// ----------------------------------------------------------
// THE OVERLAY (fixed to the screen: numeral, dock bar, gear)
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
    val captionMeta = remember {
        val v = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "dev"
        SimpleDateFormat("dd.MM.yy", Locale.US).format(Date()) + "  —  V" + v
    }

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
    val posterFg = if (paused) TagColors.White else TagColors.OnLime

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth.toFloat()
        val posterPx = with(density) { posterHeight.toPx() }
        val margin = with(density) { 20.dp.toPx() }
        val bottomMargin = with(density) { 28.dp.toPx() }
        val statusTop = with(density) { topDp.toPx() }
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

        // The numeral's ink box at 100px, then sized so it fills the width (and a little more) or 56% of the poster.
        // Size from the final number so the numeral doesn't breathe while it spins up.
        // Everything on screen is drawn from `shown`, the same value the roll animation is keyed to.
        // Drawing `digits` put the new number on screen for one frame before the roll began.
        val ink = remember(shown, typeface) { numeralInk(typeface, shown) }
        val inkSize = remember(count, typeface) { numeralInk(typeface, count.toString()) }
        val inkW100 = (inkSize.right - inkSize.left).toFloat().coerceAtLeast(1f)
        val inkH100 = (inkSize.bottom - inkSize.top).toFloat().coerceAtLeast(1f)
        val fitW = 1.0f * wPx / inkW100 * 100f
        val fitH = posterPx * 0.56f / inkH100 * 100f
        val sizePx by animateFloatAsState(
            targetValue = min(fitW, fitH),
            animationSpec = if (TagMotion.enabled) spring(dampingRatio = 0.55f, stiffness = 140f) else snap(),
            label = "numeralSize"
        )

        fun dockProgress(): Float = smooth((scroll.value / dockDist).coerceIn(0f, 1f))

        val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
        val captionPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
        val accent = TagColors.Lime.toArgb()
        val onPoster = posterFg.toArgb()
        val white = TagColors.White.toArgb()

        // Dock bar: solid where the poster collapses beneath it, then a short fade.
        val barH = topDp + 72.dp
        val solid = ((topDp + 48.dp).value / barH.value).coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(barH)
                .graphicsLayer { alpha = smooth(((scroll.value / dockDist - 0.45f) / 0.55f).coerceIn(0f, 1f)) }
                .background(Brush.verticalGradient(0f to TagColors.Black, solid to TagColors.Black, 1f to Color.Transparent))
        )

        // the numeral
        Canvas(Modifier.fillMaxSize()) {
            val p = dockProgress()
            val s0 = sizePx
            val inkH = inkH100 * s0 / 100f
            val inkLeft = ink.left * s0 / 100f
            val sEnd = dockInk / inkH
            val sc = (1f + (sEnd - 1f) * p) * (1f + (kick.value - 1f) * (1f - p))
            val baseline = (posterPx - bottomMargin) + ((statusTop + 12.dp.toPx() + dockInk) - (posterPx - bottomMargin)) * p

            drawIntoCanvas { canvas ->
                val n = canvas.nativeCanvas

                // The caption sits directly on the numeral and fades as the numeral docks.
                val capAlpha = (1f - p * 3f).coerceIn(0f, 1f)
                if (capAlpha > 0f) {
                    val top = baseline - inkH * sc
                    val sz = 11.sp.toPx()
                    val gap = 14.dp.toPx()
                    captionPaint.textSize = sz
                    captionPaint.letterSpacing = 0.08f
                    captionPaint.style = Paint.Style.FILL
                    captionPaint.color = if (paused) white else onPoster
                    captionPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    captionPaint.alpha = (255 * capAlpha).toInt()
                    n.drawText("INTERCEPTIONS SO FAR", margin, top - gap, captionPaint)
                    captionPaint.typeface = Typeface.MONOSPACE
                    captionPaint.alpha = (150 * capAlpha).toInt()
                    n.drawText(captionMeta, margin, top - gap - sz * 1.5f, captionPaint)
                    if (paused) {
                        captionPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                        captionPaint.color = accent
                        captionPaint.alpha = (255 * capAlpha).toInt()
                        n.drawText("PAUSED", margin, top - gap - sz * 3f, captionPaint)
                    }
                }
                paint.typeface = typeface
                paint.textSize = s0
                paint.letterSpacing = NUMERAL_LS
                if (paused) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3.dp.toPx() / sc
                    paint.color = accent
                } else {
                    paint.style = Paint.Style.FILL
                    paint.color = lerp(Color(onPoster), Color(white), p).toArgb()
                }
                n.save()
                n.translate(margin, baseline)
                n.scale(sc, sc)
                var x = -inkLeft
                val rolling = roll.value < 1f
                val adv = inkH * 1.25f
                shown.forEachIndexed { i, ch ->
                    val str = ch.toString()
                    val w = paint.measureText(str)
                    val oldIndex = previous.length - (shown.length - i)
                    val old = if (oldIndex >= 0) previous[oldIndex] else null
                    if (rolling && old != ch) {
                        val e = roll.value
                        n.save()
                        n.clipRect(x - 4f, -inkH * 1.35f, x + w + 4f, inkH * 0.35f)
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
            }
        }

        // Tap the numeral for the stats. The hit box tracks the numeral through the dock animation.
        val inkWBox = inkW100 / inkH100
        fun box(): IntArray {
            val p = dockProgress()
            val s0 = sizePx
            val inkH = inkH100 * s0 / 100f
            val sEnd = dockInk / inkH
            val sc = 1f + (sEnd - 1f) * p
            val baseline = (posterPx - bottomMargin) + ((statusTop + 12f * density.density + dockInk) - (posterPx - bottomMargin)) * p
            val w = min(inkWBox * inkH * sc, wPx - margin).coerceAtLeast(1f)
            val h = (inkH * sc).coerceAtLeast(1f)
            return intArrayOf(margin.roundToInt(), (baseline - h).roundToInt(), w.roundToInt(), h.roundToInt())
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
        val dockInkW = with(density) { (inkWBox * dockInk).toDp() }
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
                TagIcon(if (paused) R.drawable.ic_play else R.drawable.ic_pause, if (paused) TagColors.Lime else TagColors.White, size = 24.dp)
            }
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable { view.tick(Tick.Press); onSettings() },
                contentAlignment = Alignment.Center
            ) {
                // Two layers cross-fade with the dock, so the gear is readable on both the poster and the black bar.
                Box(Modifier.graphicsLayer { alpha = 1f - dockProgress() }) { TagIcon(R.drawable.ic_settings, posterFg, size = 32.dp) }
                Box(Modifier.graphicsLayer { alpha = dockProgress() }) { TagIcon(R.drawable.ic_settings, TagColors.White, size = 32.dp) }
            }
        }
    }
}
