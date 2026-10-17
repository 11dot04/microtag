package com.microtag.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawOutline
import androidx.graphics.shapes.Morph
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.R
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Two pages. Page one asks for permissions. Page two explains, with a loop of tiles lifting
 * off and shrinking into the real camera cutout, that TAG will sometimes show up there.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val view = LocalView.current
    val density = LocalDensity.current
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val perms = rememberPermissions()
    var page by remember { mutableIntStateOf(0) }
    var cutout by remember { mutableStateOf<android.graphics.Rect?>(null) }

    // Where the camera actually is. The system reports the cutout rectangle(s); fall back to
    // top-centre, where almost every phone puts it.
    val cx: Dp = cutout?.let { with(density) { it.exactCenterX().toDp() } } ?: (screenW / 2)
    val cy: Dp = cutout?.let { with(density) { it.exactCenterY().toDp() } } ?: 26.dp
    val mirrored = cx > screenW / 2 + 40.dp

    BackHandler(enabled = page == 1) { page = 0 }

    Box(
        Modifier
            .fillMaxSize()
            .background(TagColors.Lime)
            .onGloballyPositioned {
                cutout = view.rootWindowInsets?.displayCutout?.boundingRects?.firstOrNull { r -> r.top <= 0 }
            }
    ) {
        AnimatedContent(
            targetState = page,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(tween(380)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(380)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(200)))
            },
            label = "onboardingPage"
        ) { p ->
            if (p == 0) PermissionPage(perms, cx, cy, mirrored, onNext = { page = 1 })
            else LiftPage(cx, cy, onFinish = onFinish)
        }
    }
}

// ==========================================================
// PAGE 1: PERMISSIONS
// ==========================================================

@Composable
private fun PermissionPage(perms: Permissions, cx: Dp, cy: Dp, mirrored: Boolean, onNext: () -> Unit) {
    val view = LocalView.current

    // The arrow drawable is 360x207dp with its tip at (199.3, 51.7). Park the tip just
    // below and to the side of the camera.
    val arrowW = 360.dp
    val arrowH = 207.dp
    val tipX = 199.3.dp
    val tipY = 51.7.dp
    val arrowLeft = if (!mirrored) cx + 22.dp - tipX else cx - 22.dp - (arrowW - tipX)
    val arrowTop = cy + 30.dp - tipY
    val contentTop = arrowTop + arrowH + 12.dp

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.ic_onboarding_arrow),
            contentDescription = null,
            colorFilter = ColorFilter.tint(TagColors.OnLime),
            modifier = Modifier
                .size(arrowW, arrowH)
                .offset { IntOffset(arrowLeft.roundToPx(), arrowTop.roundToPx()) }
                .graphicsLayer { scaleX = if (mirrored) -1f else 1f }
        )

        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 34.dp)
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(contentTop))
                FitText(
                    text = AnnotatedString("I’ll live here"),
                    style = TextStyle(
                        fontFamily = TagFonts.display, fontWeight = FontWeight.Black,
                        fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-1.5).sp, color = TagColors.OnLime,
                        fontSynthesis = TagFonts.NoSynthesis
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                )
                FitText(
                    text = AnnotatedString("if you let me"),
                    style = TextStyle(
                        fontFamily = Montserrat, fontWeight = FontWeight.Light,
                        fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp, color = TagColors.OnLime
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                )
                Spacer(Modifier.height(36.dp))

                TagGrid(gap = 8.dp) {
                    PermissionPill(
                        Modifier.cell(0, 0, 2, 1), TagShapes.JoinLeft, perms.hasListener,
                        "Notification", "Listener", TextAlign.End, perms::openListenerSettings
                    )
                    PermissionPill(
                        Modifier.cell(2, 0, 2, 1), TagShapes.JoinRight, perms.hasNotif,
                        "Post", "Notifications", TextAlign.Start, perms::requestNotifications
                    )

                    // Shizuku is optional: it lets TAG watch the clipboard in the background.
                    Box(
                        Modifier
                            .cell(1, 1, 3, 1)
                            .clip(TagShapes.Pill)
                            .border(2.dp, TagColors.OnLime, TagShapes.Pill)
                            .clickable { perms.requestShizuku() }
                            .padding(horizontal = 22.dp)
                    ) {
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            // Fixed 17sp like its siblings. The capsule is wide enough that it never needs to shrink.
                            Text(
                                "Shizuku",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                softWrap = false,
                                style = TextStyle(
                                    fontFamily = Montserrat, fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp, color = TagColors.OnLime
                                )
                            )
                            TagSwitch(
                                checked = perms.hasShizuku,
                                onChange = { perms.requestShizuku() },
                                trackOn = TagColors.Black, thumbOn = TagColors.Lime,
                                trackOff = TagColors.White, thumbOff = TagColors.Black
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            PageDots(0)
            AnimatedVisibility(
                visible = perms.hasListener,
                enter = fadeIn(tween(250)) + slideInVertically(tween(300)) { it / 2 }
            ) {
                BlackPill("Step inside") { view.tick(Tick.Confirm); onNext() }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Outlined until granted, then filled black with accent type. */
@Composable
private fun PermissionPill(
    modifier: Modifier,
    shape: Shape,
    granted: Boolean,
    bold: String,
    light: String,
    align: TextAlign,
    onClick: () -> Unit
) {
    val view = LocalView.current
    Box(
        modifier
            .clip(shape)
            .background(if (granted) TagColors.Black else TagColors.Lime)
            .border(2.dp, if (granted) TagColors.Black else TagColors.OnLime, shape)
            .clickable(enabled = !granted) { view.tick(Tick.Press); onClick() }
            .padding(horizontal = 20.dp),
        contentAlignment = if (align == TextAlign.End) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        TagLabel(
            bold, light,
            color = if (granted) TagColors.Lime else TagColors.OnLime,
            size = 17.sp, align = align
        )
    }
}

@Composable
private fun BlackPill(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(TagShapes.Pill)
            .background(TagColors.Black)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = TextStyle(
                fontFamily = Montserrat, fontWeight = FontWeight.Black,
                fontSize = 18.sp, color = TagColors.Lime
            )
        )
    }
}

@Composable
private fun PageDots(current: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(2) { i ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (i == current) 22.dp else 8.dp, 8.dp)
                    .clip(CircleShape)
                    .background(TagColors.OnLime.copy(alpha = if (i == current) 1f else 0.3f))
            )
        }
    }
}

// ==========================================================
// PAGE 2: "I'll bring some stuff up here too"
// The tiles settle at the bottom like sediment, bigger than before, and stay put. Tapping the
// button lifts them out one by one, tilts them level and shrinks each into the camera cutout.
// They are never black, so they may touch the button.
// ==========================================================

/**
 * shape: an index into [ExpressiveShapes.All]. fill: 0 paper, 1 outline, 2 and 3 take the palette's extra
 * colours (or tonal versions of the accent when it has none).
 * x is a fraction of the width; y is the centre's height above the screen bottom.
 */
private class Flyer(
    val icon: Int, val shape: Int, val fill: Int,
    val size: Dp, val xFrac: Float, val bottom: Dp, val rot: Float
)

// Two loose rows. Vertical neighbours never share a fill or a shape. The last tile is half off the edge.
private val Flyers = listOf(
    Flyer(R.drawable.ic_upi_rupee, shape = 0, fill = 0, size = 112.dp, xFrac = 0.14f, bottom = 190.dp, rot = -12f),
    Flyer(R.drawable.ic_otp, shape = 1, fill = 2, size = 104.dp, xFrac = 0.38f, bottom = 186.dp, rot = 9f),
    Flyer(R.drawable.ic_earbuds, shape = 2, fill = 3, size = 100.dp, xFrac = 0.62f, bottom = 188.dp, rot = -7f),
    Flyer(R.drawable.ic_song_next, shape = 3, fill = 1, size = 108.dp, xFrac = 0.87f, bottom = 192.dp, rot = 16f),
    Flyer(R.drawable.ic_print, shape = 4, fill = 3, size = 90.dp, xFrac = 0.27f, bottom = 266.dp, rot = 22f),
    Flyer(R.drawable.ic_weather, shape = 5, fill = 0, size = 94.dp, xFrac = 0.52f, bottom = 272.dp, rot = -18f),
    Flyer(R.drawable.ic_streak, shape = 6, fill = 2, size = 84.dp, xFrac = 0.76f, bottom = 262.dp, rot = 8f),
    Flyer(R.drawable.ic_download, shape = 7, fill = 1, size = 78.dp, xFrac = 0.03f, bottom = 250.dp, rot = 30f)
)

/** WCAG-style contrast between two colours, 1 (identical) to 21. */
private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

@Composable
private fun LiftPage(cx: Dp, cy: Dp, onFinish: () -> Unit) {
    val view = LocalView.current
    val density = LocalDensity.current
    val animate = TagMotion.enabled
    var launched by remember { mutableStateOf(false) }

    // 0 -> 1 as each tile drops into the pile; 0 -> 1 as each tile leaves it.
    val settle = remember { Flyers.map { Animatable(0f) } }
    val leave = remember { Flyers.map { Animatable(0f) } }
    // The highest tiles go first, so the pile empties from the top down.
    val order = remember { Flyers.sortedByDescending { it.bottom.value }.let { sorted -> Flyers.map { sorted.indexOf(it) } } }

    LaunchedEffect(Unit) {
        coroutineScope {
            Flyers.indices.forEach { i ->
                launch {
                    if (!animate) settle[i].snapTo(1f) else {
                        delay(120L + i * 70L)
                        settle[i].animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
                    }
                }
            }
        }
    }
    LaunchedEffect(launched) {
        if (!launched) return@LaunchedEffect
        if (!animate) { onFinish(); return@LaunchedEffect }
        coroutineScope {
            Flyers.indices.forEach { i ->
                launch {
                    delay(order[i] * 90L)
                    leave[i].animateTo(1f, tween(1150, easing = LinearEasing))
                }
            }
        }
        onFinish()
    }

    // Copy, dots and button get out of the way as soon as the tiles start to leave.
    val uiAlpha by animateFloatAsState(if (launched) 0f else 1f, tween(300), label = "uiAlpha")

    Box(Modifier.fillMaxSize()) {
        // The pile. Edge to edge, so "bottom" really is the bottom of the screen.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            val targetX = with(density) { cx.toPx() }
            val targetY = with(density) { cy.toPx() }
            Flyers.forEachIndexed { i, f ->
                SedimentTile(
                    f, i,
                    restX = f.xFrac * w,
                    restY = h - with(density) { f.bottom.toPx() },
                    targetX = targetX, targetY = targetY,
                    settle = settle[i], leave = leave[i]
                )
            }
        }

        // Copy sits above the pile, right-aligned, one fitted line at a time.
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = uiAlpha }
                .navigationBarsPadding()
                .padding(start = 34.dp, end = 34.dp, bottom = 330.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            val light = TextStyle(
                fontFamily = Montserrat, fontWeight = FontWeight.Light, fontSize = 28.sp,
                lineHeight = 32.sp, letterSpacing = (-0.6).sp, color = TagColors.OnLime
            )
            val heavy = TextStyle(
                fontFamily = TagFonts.display, fontWeight = FontWeight.Black, fontSize = 40.sp,
                lineHeight = 42.sp, letterSpacing = (-1).sp, color = TagColors.OnLime,
                fontSynthesis = TagFonts.NoSynthesis
            )
            listOf(
                "Sometimes when" to light, "I feel like it" to light,
                "I’ll bring" to heavy, "some stuff" to heavy, "up here too." to heavy
            ).forEach { (line, style) ->
                FitText(AnnotatedString(line), style, Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd)
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = uiAlpha }
                .navigationBarsPadding()
                .padding(horizontal = 34.dp)
        ) {
            PageDots(1)
            BlackPill("Got it") {
                if (!launched) {
                    view.tick(Tick.Confirm)
                    launched = true
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SedimentTile(
    f: Flyer, index: Int,
    restX: Float, restY: Float, targetX: Float, targetY: Float,
    settle: Animatable<Float, *>, leave: Animatable<Float, *>
) {
    val density = LocalDensity.current
    val sizePx = with(density) { f.size.toPx() }
    val drop = with(density) { 140.dp.toPx() }
    val sway = with(density) { 18.dp.toPx() }
    // Each tile is a morph: it blooms from a circle into its shape as it lands, then keeps slowly
    // drifting toward a neighbouring shape and back. One number, p, drives it: 0..1 is circle to
    // shape, 1..2 is shape to its partner.
    val polygons = ExpressiveShapes.All
    val own = polygons[f.shape % polygons.size]
    val bloom = remember(f.shape) { Morph(ExpressiveShapes.Circle, own) }
    val drift = remember(f.shape) { Morph(own, polygons[(f.shape + 3) % polygons.size]) }
    val idle by rememberInfiniteTransition(label = "drift$index").animateFloat(
        initialValue = 0f,
        targetValue = if (TagMotion.enabled) 1f else 0f,
        animationSpec = infiniteRepeatable(
            tween(3200 + index * 370, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "idle"
    )
    fun shapeNow(): Shape {
        val s = settle.value.coerceIn(0f, 1f)
        val p = s + idle * s.pow(8)          // the drift only wakes up once the tile has landed
        return if (p <= 1f) MorphShape(bloom, p) else MorphShape(drift, p - 1f)
    }

    // Colours are relative to the accent poster. Never the base colour: on the lime palette that is black.
    // A palette with supporting colours hands them out around the pile.
    val accent = TagColors.Lime
    val onAccent = TagColors.OnLime
    val extras = TagColors.Extras
    val bg: Color = when (f.fill) {
        0 -> TagColors.Paper
        1 -> Color.Transparent
        2 -> if (extras.isNotEmpty()) extras[index % extras.size] else lerp(accent, onAccent, 0.16f)
        else -> if (extras.isNotEmpty()) extras[(index + 1) % extras.size] else lerp(accent, TagColors.Paper, 0.5f)
    }
    val iconTint = if (f.fill == 1) onAccent else if (bg.luminance() > 0.45f) TagColors.PaperInk else TagColors.Paper
    // Tiles that are too close to the poster in brightness get a thin ring so their edge still reads.
    val ring = f.fill != 1 && contrast(bg, accent) < 1.5f

    Box(
        Modifier
            .size(f.size)
            .graphicsLayer {
                val s = settle.value                      // spring: may overshoot a little
                val u = leave.value
                val glide = u * u * (3f - 2f * u)          // sideways: smooth
                val lift = u.pow(2.2f)                     // upward: slow, then sucked in
                val dir = if (index % 2 == 0) -1f else 1f
                val wobble = sin(u * 2f * PI.toFloat() * 1.5f + index) * sway * (1f - u)
                translationX = restX + (targetX - restX) * glide + wobble - sizePx / 2f
                translationY = restY + (targetY - restY) * lift - sizePx / 2f - (1f - s) * drop
                val shrink = 1f - 0.95f * u.pow(1.5f)
                val appear = 0.8f + 0.2f * s.coerceIn(0f, 1f)
                scaleX = shrink * appear
                scaleY = shrink * appear
                // Rests at its own tilt, comes level on the way out, a touch wilder while dropping in.
                rotationZ = f.rot * (1f - u) + dir * 40f * (1f - s)
                alpha = s.coerceIn(0f, 1f) * (1f - ((u - 0.9f) / 0.1f).coerceIn(0f, 1f))
                clip = true
                shape = shapeNow()
            }
            .drawBehind {
                val outline = shapeNow().createOutline(size, layoutDirection, this)
                drawOutline(outline, color = bg)
                when {
                    f.fill == 1 -> drawOutline(outline, color = onAccent, style = Stroke(2.dp.toPx()))
                    ring -> drawOutline(outline, color = onAccent.copy(alpha = 0.55f), style = Stroke(2.dp.toPx()))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        TagIcon(f.icon, iconTint, size = 38.dp)
    }
}
