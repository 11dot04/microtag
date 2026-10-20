package com.microtag.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Refresh intervals, in minutes. Order = clockwise around the dial. */
val WeatherSteps = listOf(30, 60, 120, 180, 360)

fun intervalLabel(min: Int): String = if (min < 60) "${min}m" else "${min / 60}h"

/** Fine marks between each pair of detents. */
private const val MINOR_PER_STEP = 4
/** The dial sweeps this far either side of straight up. Small enough that a dial clipped at its middle shows every step. */
private const val HALF_SWEEP = 80f

/**
 * A knob with detents and a scale. The big marks are the values; the small marks between them are only for
 * feel. Dragging runs the pointer through every mark with a light tick, a firm tick lands on each value, and
 * lifting your finger snaps to the nearest value with a confirm. Tapping jumps straight to a value.
 *
 * It is drawn as a full circle with its centre on the canvas centre, so a parent can push it down and clip it
 * to show only its top half (the label sits above the centre for that reason).
 */
@Composable
fun TagDial(
    steps: List<String>,
    index: Int,
    onIndex: (Int) -> Unit,
    body: Color,
    face: Color,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val n = steps.size
    val k = MINOR_PER_STEP + 1                       // marks per step, counting the big one
    val marks = (n - 1) * k                           // index of the last mark
    val currentIndex by rememberUpdatedState(index)
    val callback by rememberUpdatedState(onIndex)

    fun angleOfMark(m: Float) = -HALF_SWEEP + 2f * HALF_SWEEP * m / marks
    fun angleOfIndex(i: Int) = angleOfMark((i * k).toFloat())

    // -1 when no finger is down; otherwise the finger's position in marks (a float, so the knob follows it).
    var dragMark by remember { mutableFloatStateOf(-1f) }
    var lastMark by remember { mutableIntStateOf(-1) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    val snapped by animateFloatAsState(
        targetValue = angleOfIndex(index),
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 450f),
        label = "dialSweep"
    )
    val sweep = if (dragMark >= 0f) angleOfMark(dragMark) else snapped

    fun markAt(p: Offset, size: IntSize): Float {
        val dx = p.x - size.width / 2f
        val dy = size.height / 2f - p.y
        val deg = Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())).toFloat()
        return (deg.coerceIn(-HALF_SWEEP, HALF_SWEEP) + HALF_SWEEP) / (2f * HALF_SWEEP) * marks
    }

    fun indexOfMark(m: Float): Int = ((m + k / 2f) / k).toInt().coerceIn(0, n - 1)

    /** Move the finger to [m]: a tick for every mark passed (a firm one if it was a value), then the value itself. */
    fun moveTo(m: Float) {
        dragMark = m
        val mi = m.roundToInt().coerceIn(0, marks)
        if (mi != lastMark) {
            val crossedValue = if (lastMark < 0) false else {
                val lo = minOf(lastMark, mi)
                val hi = maxOf(lastMark, mi)
                (lo..hi).any { it % k == 0 && it != lastMark }
            }
            if (lastMark >= 0) view.tick(if (crossedValue) Tick.Detent else Tick.Minor)
            lastMark = mi
        }
        val i = indexOfMark(m)
        if (i != currentIndex) callback(i)
    }

    Box(modifier.onSizeChanged { boxSize = it }, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(n) {
                    detectTapGestures { p ->
                        val i = indexOfMark(markAt(p, size))
                        if (i != currentIndex) {
                            view.tick(Tick.Detent)
                            callback(i)
                        }
                    }
                }
                .pointerInput(n) {
                    detectDragGestures(
                        onDragStart = { p ->
                            val m = markAt(p, size)
                            lastMark = m.roundToInt().coerceIn(0, marks)
                            dragMark = m
                        },
                        onDragEnd = {
                            dragMark = -1f
                            lastMark = -1
                            view.tick(Tick.Confirm)
                        },
                        onDragCancel = { dragMark = -1f; lastMark = -1 },
                        onDrag = { change, _ -> change.consume(); moveTo(markAt(change.position, size)) }
                    )
                }
        ) {
            val c = center
            val r = size.minDimension / 2f
            // the scale: a long mark for every value, short marks between
            for (m in 0..marks) {
                val a = Math.toRadians(angleOfMark(m.toFloat()).toDouble() - 90.0)
                val isValue = m % k == 0
                val selected = isValue && m / k == currentIndex
                val r1 = r * (if (isValue) 0.80f else 0.88f)
                val r2 = r * (if (selected) 1f else if (isValue) 0.93f else 0.95f)
                drawLine(
                    color = if (isValue) body else body.copy(alpha = 0.55f),
                    start = Offset(c.x + cos(a).toFloat() * r1, c.y + sin(a).toFloat() * r1),
                    end = Offset(c.x + cos(a).toFloat() * r2, c.y + sin(a).toFloat() * r2),
                    strokeWidth = (if (selected) 4.dp else if (isValue) 2.dp else 1.2.dp).toPx(),
                    cap = StrokeCap.Round
                )
            }
            drawCircle(body, radius = r * 0.70f, center = c)
            val a = Math.toRadians(sweep.toDouble() - 90.0)
            drawCircle(
                color = face,
                radius = r * 0.08f,
                center = Offset(c.x + cos(a).toFloat() * r * 0.57f, c.y + sin(a).toFloat() * r * 0.57f)
            )
        }
        // The value sits above the knob's centre, so it stays in view when the lower half is clipped away.
        FitText(
            text = AnnotatedString(steps[index]),
            style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Black, fontSize = 40.sp, color = face),
            modifier = Modifier
                .fillMaxSize(0.5f)
                .offset { IntOffset(0, -(boxSize.height * 0.16f).roundToInt()) },
            contentAlignment = Alignment.Center
        )
    }
}
