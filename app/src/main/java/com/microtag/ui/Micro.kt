package com.microtag.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ==========================================================
// MICROGRAPHICS
// Tiny technical marks: registration crosses, targets, scale ticks, status glyphs, mono annotations.
// They are decoration. They sit beside the real labels and never replace them, they carry no
// information a screen reader would need, and the text is never below 10sp.
//
// Everything here is drawn in code (no drawables), so it takes any colour and follows the palette.
// ==========================================================

enum class Micro {
    Cross, Register, Target, Asterisk, Diamond, ArrowRight, ArrowUp, Slashes,
    DotGrid, Warn, Power, Wave, Brackets, PlusCircle, Chevrons, Cancel
}

/**
 * Draws one glyph centred on [center], fitting a box of [size] px. [stroke] is the line weight in px.
 * Use it inside any Canvas / drawBehind.
 */
fun DrawScope.microGlyph(
    kind: Micro,
    center: Offset,
    size: Float,
    color: Color,
    stroke: Float = 1.25.dp.toPx()
) {
    val h = size / 2f
    val cx = center.x
    val cy = center.y
    val line = Stroke(width = stroke, cap = StrokeCap.Butt, join = StrokeJoin.Miter)
    val round = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun seg(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, Offset(cx + x1, cy + y1), Offset(cx + x2, cy + y2), stroke)

    when (kind) {
        Micro.Cross -> {
            seg(-h, 0f, h, 0f)
            seg(0f, -h, 0f, h)
        }
        Micro.Register -> {
            drawCircle(color, h * 0.55f, center, style = line)
            seg(-h, 0f, h, 0f)
            seg(0f, -h, 0f, h)
        }
        Micro.Target -> {
            drawCircle(color, h, center, style = line)
            drawCircle(color, h * 0.55f, center, style = line)
            drawCircle(color, stroke * 1.1f, center)
        }
        Micro.Asterisk -> {
            for (i in 0 until 4) {
                val a = i * PI.toFloat() / 4f
                seg(-cos(a) * h, -sin(a) * h, cos(a) * h, sin(a) * h)
            }
        }
        Micro.Diamond -> {
            val path = Path().apply {
                moveTo(cx, cy - h); lineTo(cx + h, cy); lineTo(cx, cy + h); lineTo(cx - h, cy); close()
            }
            drawPath(path, color, style = line)
        }
        Micro.ArrowRight -> {
            seg(-h, 0f, h, 0f)
            seg(h * 0.25f, -h * 0.6f, h, 0f)
            seg(h * 0.25f, h * 0.6f, h, 0f)
        }
        Micro.ArrowUp -> {
            seg(0f, h, 0f, -h)
            seg(-h * 0.6f, -h * 0.25f, 0f, -h)
            seg(h * 0.6f, -h * 0.25f, 0f, -h)
        }
        Micro.Slashes -> {
            for (i in -1..1) seg(i * h * 0.7f - h * 0.35f, h * 0.7f, i * h * 0.7f + h * 0.35f, -h * 0.7f)
        }
        Micro.DotGrid -> {
            val d = stroke * 0.9f
            for (r in -1..1) for (c in -1..1) drawCircle(color, d, Offset(cx + c * h * 0.7f, cy + r * h * 0.7f))
        }
        Micro.Warn -> {
            val path = Path().apply {
                moveTo(cx, cy - h); lineTo(cx + h, cy + h * 0.8f); lineTo(cx - h, cy + h * 0.8f); close()
            }
            drawPath(path, color, style = line)
            seg(0f, -h * 0.25f, 0f, h * 0.2f)
            drawCircle(color, stroke * 0.8f, Offset(cx, cy + h * 0.5f))
        }
        Micro.Power -> {
            drawArc(
                color, startAngle = -60f, sweepAngle = 300f, useCenter = false,
                topLeft = Offset(cx - h * 0.8f, cy - h * 0.8f), size = Size(h * 1.6f, h * 1.6f), style = round
            )
            seg(0f, -h, 0f, -h * 0.1f)
        }
        Micro.Wave -> {
            val path = Path().apply {
                moveTo(cx - h, cy)
                lineTo(cx - h * 0.45f, cy)
                lineTo(cx - h * 0.25f, cy - h * 0.8f)
                lineTo(cx + h * 0.05f, cy + h * 0.8f)
                lineTo(cx + h * 0.25f, cy)
                lineTo(cx + h, cy)
            }
            drawPath(path, color, style = line)
        }
        Micro.Brackets -> {
            val a = h * 0.45f
            for (sx in listOf(-1f, 1f)) for (sy in listOf(-1f, 1f)) {
                seg(sx * h, sy * h, sx * h, sy * (h - a))
                seg(sx * h, sy * h, sx * (h - a), sy * h)
            }
        }
        Micro.PlusCircle -> {
            drawCircle(color, h, center, style = line)
            seg(-h * 0.5f, 0f, h * 0.5f, 0f)
            seg(0f, -h * 0.5f, 0f, h * 0.5f)
        }
        Micro.Chevrons -> {
            for (i in 0..1) {
                val o = (i - 0.5f) * h * 0.9f
                seg(o - h * 0.25f, -h * 0.6f, o + h * 0.25f, 0f)
                seg(o + h * 0.25f, 0f, o - h * 0.25f, h * 0.6f)
            }
        }
        Micro.Cancel -> {
            drawRect(color, Offset(cx - h, cy - h), Size(size, size), style = line)
            seg(-h * 0.55f, -h * 0.55f, h * 0.55f, h * 0.55f)
            seg(-h * 0.55f, h * 0.55f, h * 0.55f, -h * 0.55f)
        }
    }
}

/**
 * A ruler: [count] ticks starting at [from], every [step] px, running along y if [vertical] else along x.
 * Every [major]th tick is twice as long. [dir] sets which side the ticks grow toward (1 or -1).
 */
fun DrawScope.microTicks(
    from: Offset,
    count: Int,
    step: Float,
    vertical: Boolean,
    color: Color,
    length: Float = 4.dp.toPx(),
    major: Int = 6,
    dir: Float = 1f,
    stroke: Float = 1.dp.toPx()
) {
    for (i in 0 until count) {
        val len = if (i % major == 0) length * 2f else length
        if (vertical) {
            val y = from.y + i * step
            drawLine(color, Offset(from.x, y), Offset(from.x + dir * len, y), stroke)
        } else {
            val x = from.x + i * step
            drawLine(color, Offset(x, from.y), Offset(x, from.y + dir * len), stroke)
        }
    }
}

/** One glyph as a composable. */
@Composable
fun MicroGlyph(kind: Micro, modifier: Modifier = Modifier, size: Dp = 14.dp, color: Color = TagColors.Outline) {
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        microGlyph(kind, Offset(this.size.width / 2f, this.size.height / 2f), this.size.minDimension * 0.8f, color)
    }
}

/** Small uppercase mono annotation: decorative, hidden from screen readers, never below 10sp. */
@Composable
fun MicroLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TagColors.Outline,
    bold: Boolean = false,
    size: Int = 10,
    wrap: Boolean = true
) {
    Text(
        text.uppercase(),
        modifier = modifier.clearAndSetSemantics { },
        softWrap = wrap,
        maxLines = if (wrap) Int.MAX_VALUE else 1,
        style = TextStyle(
            fontFamily = TagFonts.data,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            fontSize = size.coerceAtLeast(10).sp,
            lineHeight = (size.coerceAtLeast(10) + 4).sp,
            letterSpacing = 0.6.sp,
            color = color
        )
    )
}

/**
 * A section heading in the micrographic style: an optional two-digit index, the label, then a hairline
 * that runs out to the edge and ends in a registration cross. The label stays readable text; the index
 * and the rule are decoration.
 */
@Composable
fun MicroHeader(label: String, modifier: Modifier = Modifier, index: Int? = null, color: Color = TagColors.AccentText) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (index != null) {
            Text(
                "%02d".format(index),
                modifier = Modifier.clearAndSetSemantics { },
                style = TextStyle(
                    fontFamily = TagFonts.data, fontWeight = FontWeight.Medium, fontSize = 10.sp,
                    letterSpacing = 0.6.sp, color = TagColors.Outline
                )
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label.uppercase(),
            style = TextStyle(
                fontFamily = TagFonts.mono, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                letterSpacing = 1.2.sp, color = color
            )
        )
        Spacer(Modifier.width(10.dp))
        Canvas(Modifier.weight(1f).height(10.dp).clearAndSetSemantics { }) {
            val y = size.height / 2f
            val tail = 9.dp.toPx()
            drawLine(TagColors.Outline.copy(alpha = 0.45f), Offset(0f, y), Offset(size.width - tail, y), 1.dp.toPx())
            microGlyph(Micro.Cross, Offset(size.width - tail / 2f, y), 7.dp.toPx(), TagColors.Outline)
        }
    }
}

/** The line above a screen title: a cross, "TAG / NAME" on the left, the screen's number on the right. */
@Composable
fun MicroStrip(code: Int, name: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MicroGlyph(Micro.Cross, size = 10.dp)
        MicroLabel("TAG / $name")
        Canvas(Modifier.weight(1f).height(1.dp).clearAndSetSemantics { }) {
            drawLine(TagColors.Outline.copy(alpha = 0.3f), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
        }
        MicroLabel("—" + code.toString().padStart(2, '0'), color = TagColors.White, bold = true)
    }
}

/** A tiny ON / OFF readout with a dot (solid when on, a ring when off) for the corner of a tile. */
@Composable
fun MicroStatus(on: Boolean, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(8.dp).clearAndSetSemantics { }) {
            if (on) drawCircle(color, size.minDimension / 2.4f)
            else drawCircle(color, size.minDimension / 2.4f, style = Stroke(1.2.dp.toPx()))
        }
        MicroLabel(if (on) "On" else "Off", color = color)
    }
}
