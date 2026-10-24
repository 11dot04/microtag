package com.microtag.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Refresh intervals, in minutes. Order = left to right along the wheel. */
val WeatherSteps = listOf(30, 60, 120, 180, 360)

fun intervalLabel(min: Int): String = if (min < 60) "${min}m" else "${min / 60}h"

/** Degrees of wheel between neighbouring marks. */
private const val TICK_DEG = 4f
/** Every ninth mark is a value (a long tick with a firm detent); the rest are only for feel. */
private const val MAJOR_EVERY = 9
/** Marks further than this from the needle are not drawn. */
private const val VISIBLE_DEG = 70f

/**
 * A wheel under a fixed needle. Drag along the arc and the marks turn beneath the needle; every mark that passes
 * gives a light tick and every value a firm one. Lifting your finger settles the wheel on the nearest value, and
 * tapping jumps to it. The value shows above the needle and rolls to the next as it changes.
 *
 * The wheel is far larger than the room it has: its centre is below the bottom edge, so a parent that clips it
 * shows only the top of the arc.
 */
@Composable
fun TagDial(
    steps: List<String>,
    index: Int,
    onIndex: (Int) -> Unit,
    ink: Color,
    cool: Color,
    warm: Color,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val n = steps.size
    val majorDeg = MAJOR_EVERY * TICK_DEG
    val travel = (n - 1) * majorDeg
    fun angleOfIndex(i: Int) = i * majorDeg
    fun nearest(a: Float) = (a / majorDeg).roundToInt().coerceIn(0, n - 1)

    val currentIndex by rememberUpdatedState(index)
    val callback by rememberUpdatedState(onIndex)

    // The wheel's position in degrees along its travel. One source of truth, moved by the finger or by a settle.
    var wheel by remember { mutableFloatStateOf(angleOfIndex(index)) }
    var dragging by remember { mutableStateOf(false) }
    var lastTick by remember { mutableIntStateOf(Int.MIN_VALUE) }
    var prevAngle by remember { mutableFloatStateOf(0f) }
    var settle by remember { mutableStateOf<Job?>(null) }
    var box by remember { mutableStateOf(IntSize.Zero) }

    fun settleTo(target: Float) {
        settle?.cancel()
        settle = scope.launch {
            Animatable(wheel).animateTo(target, spring(dampingRatio = 0.6f, stiffness = 380f)) { wheel = value }
        }
    }

    // A value changed from outside (not by this dial): follow it.
    LaunchedEffect(index) {
        if (!dragging && nearest(wheel) != index) settleTo(angleOfIndex(index))
    }

    // Geometry. The arc's top sits 40% down the box; its centre is R below that.
    fun radius(w: Float) = w * 0.62f
    fun arcTop(h: Float) = h * 0.40f

    /** The pointer's angle from straight up around the wheel's centre, clockwise positive, in degrees. */
    fun angleAt(p: Offset): Float {
        val cx = box.width / 2f
        val cy = arcTop(box.height.toFloat()) + radius(box.width.toFloat())
        return Math.toDegrees(atan2((p.x - cx).toDouble(), (cy - p.y).toDouble())).toFloat()
    }

    Box(modifier.onSizeChanged { box = it }) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(n) {
                    detectTapGestures { p ->
                        // the mark under the finger is the one at that angle from the needle
                        val target = (wheel + angleAt(p)).coerceIn(0f, travel)
                        val i = nearest(target)
                        view.tick(Tick.Detent)
                        callback(i)
                        settleTo(angleOfIndex(i))
                    }
                }
                .pointerInput(n) {
                    detectDragGestures(
                        onDragStart = { p ->
                            settle?.cancel()
                            dragging = true
                            prevAngle = angleAt(p)
                            lastTick = (wheel / TICK_DEG).roundToInt()
                        },
                        onDragEnd = {
                            dragging = false
                            view.tick(Tick.Confirm)
                            settleTo(angleOfIndex(nearest(wheel)))
                        },
                        onDragCancel = { dragging = false; settleTo(angleOfIndex(nearest(wheel))) },
                        onDrag = { change, _ ->
                            change.consume()
                            val a = angleAt(change.position)
                            var d = a - prevAngle
                            if (d > 180f) d -= 360f
                            if (d < -180f) d += 360f
                            prevAngle = a
                            // the wheel turns with the finger: marks move clockwise, so the position falls
                            wheel = (wheel - d).coerceIn(0f, travel)

                            val tickNow = (wheel / TICK_DEG).roundToInt()
                            if (tickNow != lastTick) {
                                val lo = minOf(lastTick, tickNow)
                                val hi = maxOf(lastTick, tickNow)
                                val crossedValue = (lo..hi).any { it % MAJOR_EVERY == 0 && it != lastTick }
                                view.tick(if (crossedValue) Tick.Detent else Tick.Minor)
                                lastTick = tickNow
                            }
                            val i = nearest(wheel)
                            if (i != currentIndex) callback(i)
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height
            val r = radius(w)
            val cx = w / 2f
            val top = arcTop(h)
            val cy = top + r

            // the marks, turning with the wheel
            val last = (n - 1) * MAJOR_EVERY
            for (j in 0..last) {
                val screen = j * TICK_DEG - wheel               // degrees from straight up, clockwise
                if (abs(screen) > VISIBLE_DEG) continue
                val major = j % MAJOR_EVERY == 0
                val len = (if (major) 17.dp else 10.dp).toPx()
                val rad = Math.toRadians(screen.toDouble())
                val dx = sin(rad).toFloat()
                val dy = -cos(rad).toFloat()
                val side = (abs(screen) / 55f).coerceIn(0f, 1f)
                val tint = lerp(ink, if (screen < 0f) cool else warm, side)
                val fade = 1f - (abs(screen) / VISIBLE_DEG).let { it * it }
                drawLine(
                    color = tint.copy(alpha = fade * (if (major) 1f else 0.8f)),
                    start = Offset(cx + dx * r, cy + dy * r),
                    end = Offset(cx + dx * (r - len), cy + dy * (r - len)),
                    strokeWidth = (if (major) 2.4.dp else 1.5.dp).toPx(),
                    cap = StrokeCap.Round
                )
            }

            // the needle: fixed at the top, fading as it runs down into the clip
            val nw = 4.dp.toPx()
            val nTop = top - 12.dp.toPx()
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(ink, ink.copy(alpha = 0f)), startY = nTop, endY = h),
                topLeft = Offset(cx - nw / 2f, nTop),
                size = Size(nw, h - nTop),
                cornerRadius = CornerRadius(nw / 2f)
            )
        }

        // The value, above the needle. It rolls up or down as it changes.
        AnimatedContent(
            targetState = index,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, (box.height * 0.40f - 12.dp.toPx() - 44.dp.toPx()).roundToInt().coerceAtLeast(0)) },
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically(tween(220)) { if (up) it else -it } + fadeIn(tween(160))) togetherWith
                    (slideOutVertically(tween(220)) { if (up) -it else it } + fadeOut(tween(120))) using
                    SizeTransform(clip = false)
            },
            label = "frequencyValue"
        ) { i ->
            Text(
                steps[i.coerceIn(0, n - 1)],
                style = TextStyle(
                    fontFamily = TagFonts.display, fontWeight = FontWeight.Light, fontSize = 34.sp,
                    color = ink, fontSynthesis = TagFonts.NoSynthesis
                )
            )
        }
    }
}
