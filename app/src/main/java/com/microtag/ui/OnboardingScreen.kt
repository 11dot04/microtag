package com.microtag.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
                        fontFamily = Montserrat, fontWeight = FontWeight.Black,
                        fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-1.5).sp, color = TagColors.OnLime
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
// Tiles rise, tilt level, shrink and vanish into the camera cutout. Staggered, looping.
// ==========================================================

/** shape: 0 circle, 1 squircle, 2 hexagon, 3 leaf, 4 cut corners, 5 pill. fill: 0 white, 1 outline, 2 tonal, 3 light. */
private class Flyer(
    val icon: Int, val xFrac: Float, val shape: Int, val fill: Int,
    val w: Dp = 64.dp, val h: Dp = 64.dp
)

// Neighbours (by xFrac) never share a shape or a fill. No black bases: they vanished under the
// copy and clashed with the black button.
private val Flyers = listOf(
    Flyer(R.drawable.ic_upi_rupee, 0.16f, shape = 0, fill = 0),
    Flyer(R.drawable.ic_streak, 0.27f, shape = 4, fill = 2),
    Flyer(R.drawable.ic_otp, 0.38f, shape = 5, fill = 3, w = 84.dp, h = 48.dp),
    Flyer(R.drawable.ic_earbuds, 0.50f, shape = 3, fill = 1),
    Flyer(R.drawable.ic_song_next, 0.60f, shape = 2, fill = 0, w = 70.dp, h = 62.dp),
    Flyer(R.drawable.ic_print, 0.71f, shape = 1, fill = 2),
    Flyer(R.drawable.ic_weather, 0.82f, shape = 0, fill = 1, w = 58.dp, h = 58.dp),
    Flyer(R.drawable.ic_download, 0.90f, shape = 3, fill = 3)
)

private val Hexagon = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.25f, 0f); lineTo(w * 0.75f, 0f); lineTo(w, h * 0.5f)
    lineTo(w * 0.75f, h); lineTo(w * 0.25f, h); lineTo(0f, h * 0.5f); close()
}

private fun flyerShape(kind: Int): Shape = when (kind) {
    0 -> CircleShape
    1 -> RoundedCornerShape(22.dp)
    2 -> Hexagon
    3 -> RoundedCornerShape(topStart = 30.dp, topEnd = 8.dp, bottomEnd = 30.dp, bottomStart = 8.dp)
    4 -> CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp)
    else -> RoundedCornerShape(percent = 50)
}

@Composable
private fun LiftPage(cx: Dp, cy: Dp, onFinish: () -> Unit) {
    val view = LocalView.current
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize()) {
        // Tiles first, so the copy sits on top of them.
        // Clipped just above the button, so the tiles look like they come out of it.
        BoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 96.dp).clipToBounds()) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            val targetX = with(density) { cx.toPx() }
            val targetY = with(density) { cy.toPx() }
            Flyers.forEachIndexed { i, f ->
                FlyingTile(f, i, startX = f.xFrac * w, startY = h + 40f, targetX = targetX, targetY = targetY)
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 34.dp)
        ) {
            Spacer(Modifier.weight(1f))
            // Hand-broken lines, each one fitted so nothing ever wraps mid-phrase.
            val light = TextStyle(
                fontFamily = Montserrat, fontWeight = FontWeight.Light, fontSize = 28.sp,
                lineHeight = 32.sp, letterSpacing = (-0.6).sp, color = TagColors.OnLime
            )
            val heavy = TextStyle(
                fontFamily = Montserrat, fontWeight = FontWeight.Black, fontSize = 40.sp,
                lineHeight = 42.sp, letterSpacing = (-1).sp, color = TagColors.OnLime
            )
            listOf(
                "Sometimes when" to light, "I feel like it" to light,
                "I’ll bring" to heavy, "some stuff" to heavy, "up here too." to heavy
            ).forEach { (line, style) ->
                FitText(AnnotatedString(line), style, Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd)
            }
            Spacer(Modifier.weight(0.7f))
            PageDots(1)
            BlackPill("Got it") { view.tick(Tick.Confirm); onFinish() }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FlyingTile(f: Flyer, index: Int, startX: Float, startY: Float, targetX: Float, targetY: Float) {
    val density = LocalDensity.current
    val halfW = with(density) { f.w.toPx() / 2f }
    val halfH = with(density) { f.h.toPx() / 2f }
    val sway = with(density) { 16.dp.toPx() }

    // FastForward offsets make later tiles start mid-flight, so the screen is never empty.
    val p by rememberInfiniteTransition(label = "lift$index").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(index * 650, StartOffsetType.FastForward)
        ),
        label = "p"
    )

    val shape = flyerShape(f.shape)
    val ink = TagColors.OnLime
    val (base, outlined) = when (f.fill) {
        0 -> TagColors.White to false
        1 -> Color.Transparent to true
        2 -> lerp(TagColors.Lime, ink, 0.16f) to false
        else -> lerp(TagColors.Lime, TagColors.White, 0.5f) to false
    }

    Box(
        Modifier
            .size(f.w, f.h)
            .graphicsLayer {
                val u = p
                val glide = u * u * (3f - 2f * u)              // sideways: smooth
                val lift = u.pow(2.2f)                          // upward: slow, then gets sucked in
                val dir = if (index % 2 == 0) -1f else 1f
                val wobble = sin(u * 2f * PI.toFloat() * 1.5f + index) * sway * (1f - u)
                translationX = startX + (targetX - startX) * glide + wobble - halfW
                translationY = startY + (targetY - startY) * lift - halfH
                val intro = 0.7f + 0.3f * min(1f, u / 0.1f)
                val s = (1f - 0.94f * u.pow(1.6f)) * intro
                scaleX = s
                scaleY = s
                rotationZ = dir * 16f * (1f - u)
                alpha = min(1f, u / 0.12f) * (1f - ((u - 0.9f) / 0.1f).coerceIn(0f, 1f))
            }
            .clip(shape)
            .background(base)
            .then(if (outlined) Modifier.border(2.dp, ink, shape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        TagIcon(f.icon, ink, size = 30.dp)
    }
}
