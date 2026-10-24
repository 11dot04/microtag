package com.microtag.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.runtime.getValue
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
    if (!TagColors.MicroOn) return
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
    if (!TagColors.MicroOn) return
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

/** A tile's state as a bare dot: solid when on, a ring when off. No text, so it can't collide with the subtitle. */
@Composable
fun MicroStatus(on: Boolean, color: Color, modifier: Modifier = Modifier) {
    if (!TagColors.MicroOn) return
    Canvas(modifier.size(10.dp).clearAndSetSemantics { }) {
        val r = size.minDimension / 2.6f
        if (on) drawCircle(color, r) else drawCircle(color, r, style = Stroke(1.4.dp.toPx()))
    }
}

/**
 * A 24-cell strip for a daily window, one cell per hour, lit across the hours inside [startMin]..[endMin]
 * (minutes after midnight; the window may cross midnight). Hour marks sit underneath and the current hour is
 * picked out. Decoration with a job: it shows at a glance when the night pause is in force.
 */
@Composable
fun MicroHoursBar(startMin: Int, endMin: Int, modifier: Modifier = Modifier, nowMin: Int? = null) {
    val lit = TagColors.AccentText
    val off = TagColors.Outline
    val labelPaint = androidx.compose.runtime.remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    Canvas(modifier.fillMaxWidth().height(38.dp).clearAndSetSemantics { }) {
        val cells = 24
        val gap = 2.dp.toPx()
        val cell = (size.width - gap * (cells - 1)) / cells
        val barH = 12.dp.toPx()
        fun inWindow(minute: Int): Boolean = when {
            startMin == endMin -> false
            startMin < endMin -> minute in startMin until endMin
            else -> minute >= startMin || minute < endMin
        }
        for (h in 0 until cells) {
            val x = h * (cell + gap)
            val on = inWindow(h * 60 + 30)
            drawRect(if (on) lit else off.copy(alpha = 0.28f), Offset(x, 0f), Size(cell, barH))
        }
        if (nowMin != null) {
            val x = (nowMin / 60f) * (cell + gap)
            drawLine(TagColors.White, Offset(x, -2.dp.toPx()), Offset(x, barH + 2.dp.toPx()), 1.5.dp.toPx())
        }
        labelPaint.textSize = 10.sp.toPx()
        labelPaint.typeface = android.graphics.Typeface.MONOSPACE
        labelPaint.color = off.toArgb()
        drawIntoCanvas { c ->
            for (h in listOf(0, 6, 12, 18)) {
                labelPaint.textAlign = android.graphics.Paint.Align.LEFT
                c.nativeCanvas.drawText("%02d".format(h), h * (cell + gap), barH + 15.dp.toPx(), labelPaint)
            }
            labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
            c.nativeCanvas.drawText("24", size.width, barH + 15.dp.toPx(), labelPaint)
        }
    }
}

// ==========================================================
// PLAIN SECTION LABEL
// ==========================================================

/** A section heading: mono caps in the accent text colour. Nothing around it. */
@Composable
fun TagSectionLabel(label: String, modifier: Modifier = Modifier) {
    Text(
        label.uppercase(),
        modifier = modifier,
        style = TextStyle(
            fontFamily = TagFonts.mono, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            letterSpacing = 1.2.sp, color = TagColors.AccentText
        )
    )
}

// ==========================================================
// THE ELEMENTS
// A short list of marks taken from the micrographics reference: a status readout with a ring, a run / target /
// state block with two small pills, a ruled caption, interlocking rings, a boxed burst and bracketed tags.
// Each is used in one or two places only (the Monolith, the stats header, an empty state, the credits screen).
// ==========================================================

/** `SYSTM ○ ON` / `SYSTM ○ OFF`: bold mono with a ring between, lit in the accent when on. */
@Composable
fun MicroSysStatus(on: Boolean, modifier: Modifier = Modifier) {
    if (!TagColors.MicroOn) return
    val ink = TagColors.White
    val style = TextStyle(fontFamily = TagFonts.data, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ink)
    Row(
        modifier.clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("SYSTM", style = style, maxLines = 1, softWrap = false)
        Canvas(Modifier.size(14.dp)) {
            val r = size.minDimension / 2f - 1.dp.toPx()
            drawCircle(ink, r, style = Stroke(1.5.dp.toPx()))
            if (on) drawCircle(TagColors.Lime, r * 0.55f)
        }
        Text(if (on) "ON" else "OFF", style = style, maxLines = 1, softWrap = false)
    }
}

/** A small outlined pill, optionally lit. */
@Composable
private fun MicroPill(lit: Boolean, modifier: Modifier = Modifier) {
    val ink = TagColors.White
    val accent = TagColors.Lime
    Canvas(modifier.size(width = 22.dp, height = 9.dp)) {
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
        if (lit) drawRoundRect(accent, cornerRadius = r)
        drawRoundRect(if (lit) accent else ink.copy(alpha = 0.7f), cornerRadius = r, style = Stroke(1.2.dp.toPx()))
    }
}

/** `RUN` with two pills, then `TARGET:` and `STATE:` lines. */
@Composable
fun MicroRunBlock(running: Boolean, target: String, modifier: Modifier = Modifier) {
    if (!TagColors.MicroOn) return
    Column(modifier.clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MicroLabel("Run", color = TagColors.White, bold = true, wrap = false)
            MicroPill(running)
            MicroPill(false)
        }
        MicroLabel("Target: $target", wrap = false)
        MicroLabel("State: " + (if (running) "Active" else "Paused"), wrap = false)
    }
}

/** A hairline, a bold title and one line of figures underneath. */
@Composable
fun MicroLayerCaption(title: String, line: String, modifier: Modifier = Modifier) {
    if (!TagColors.MicroOn) return
    Column(modifier.clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Canvas(Modifier.fillMaxWidth().height(1.dp)) {
            drawLine(TagColors.Outline.copy(alpha = 0.6f), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
        }
        Spacer(Modifier.height(3.dp))
        MicroLabel(title, color = TagColors.White, bold = true, wrap = false)
        MicroLabel(line, wrap = false)
    }
}

/** Two interlocking rings, a dotted ring and an arrow in a row. */
@Composable
fun MicroRings(modifier: Modifier = Modifier) {
    if (!TagColors.MicroOn) return
    val mark = TagColors.White.copy(alpha = 0.8f)
    Canvas(modifier.size(width = 84.dp, height = 20.dp).clearAndSetSemantics { }) {
        val r = 7.dp.toPx()
        val cy = size.height / 2f
        val st = Stroke(1.3.dp.toPx())
        drawCircle(mark, r, Offset(10.dp.toPx(), cy), style = st)
        drawCircle(mark, r, Offset(19.dp.toPx(), cy), style = st)
        // the dotted ring
        val dc = Offset(44.dp.toPx(), cy)
        for (i in 0 until 12) {
            val a = i * PI.toFloat() / 6f
            drawCircle(mark, 0.9.dp.toPx(), Offset(dc.x + cos(a) * r, dc.y + sin(a) * r))
        }
        // the arrow
        microGlyph(Micro.ArrowRight, Offset(70.dp.toPx(), cy), 14.dp.toPx(), mark, 1.3.dp.toPx())
    }
}

/** A square with one chamfered corner, holding an eight-ray burst inside a ring. */
@Composable
fun MicroBurstBox(modifier: Modifier = Modifier, size: Dp = 64.dp) {
    if (!TagColors.MicroOn) return
    val mark = TagColors.White
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val w = this.size.width
        val cut = w * 0.22f
        val frame = Path().apply {
            moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, w - cut); lineTo(w - cut, w); lineTo(0f, w); close()
        }
        drawPath(frame, mark.copy(alpha = 0.8f), style = Stroke(1.3.dp.toPx(), join = StrokeJoin.Miter))
        val c = Offset(w * 0.46f, w * 0.46f)
        drawCircle(mark.copy(alpha = 0.7f), w * 0.30f, c, style = Stroke(1.dp.toPx()))
        microGlyph(Micro.Asterisk, c, w * 0.50f, TagColors.Lime, 1.4.dp.toPx())
    }
}

/** A bracketed tag: `[ ? ]`. */
@Composable
fun MicroBracket(text: String, modifier: Modifier = Modifier, color: Color = TagColors.Outline) {
    MicroLabel("[ $text ]", modifier, color, wrap = false)
}

/** Katakana set upright, one character per line, like the reference's vertical status word. */
@Composable
fun MicroKatakana(text: String, modifier: Modifier = Modifier, color: Color = TagColors.Outline) {
    if (!TagColors.MicroOn) return
    Column(modifier.clearAndSetSemantics { }, horizontalAlignment = Alignment.CenterHorizontally) {
        text.forEach { ch ->
            Text(
                ch.toString(),
                style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 13.sp, color = color)
            )
        }
    }
}
