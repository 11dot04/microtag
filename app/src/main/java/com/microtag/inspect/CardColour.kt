package com.microtag.inspect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

// ==========================================================
// COLOUR. Three round corners, a chamfered foot, a notch bitten out of the right edge.
//   top left   the name, never broken mid-word: one size, the longest word fills the width
//   top right  a pinwheel of four quarter-discs: the colour, a lighter step, its complement, a darker step;
//              the complement's three bytes hang from its tip
//   middle     the share arrow, rgb and hsl, and under them the contrast verdict in the colour itself
//   foot       the hex, large
// A colour too dark to show on black gets a card filled with itself, white ink, and its own quarter drawn as an outline.
// ==========================================================

@Composable
internal fun ColourCard(c: CardCtx, modifier: Modifier) {
    val p = c.p
    val colour = p.palette.getOrNull(0)?.let { Color(it) } ?: c.accent
    val comp = p.palette.getOrNull(1)?.let { Color(it) } ?: Paper.Ink
    val tint = p.palette.getOrNull(2)?.let { Color(it) } ?: lerp(colour, Color.White, 0.38f)
    val shade = p.palette.getOrNull(3)?.let { Color(it) } ?: lerp(colour, Color.Black, 0.42f)
    val filled = Surface.isFilled(colour)
    val surface = Surface.forColour(colour)
    val ink = Color.White
    val rgb = p.rows.firstOrNull { it.first == "RGB" }?.second.orEmpty()
    val hsl = p.rows.firstOrNull { it.first == "HSL" }?.second.orEmpty()
    val verdict = p.rows.firstOrNull { it.first == "Readable with" }?.second.orEmpty()
    val compHex = p.rows.firstOrNull { it.first == "Complement" }?.second.orEmpty()
    val chipText = if (verdict.contains("black", ignoreCase = true)) Color.Black else Color.White
    val hexColour = if (c.flash == p.bigText) c.accent else ink

    CardFrame(p, modifier, c.drag, surface, 340.dp, padded = false) {
        Column(Modifier.fillMaxWidth().heightIn(min = 336.dp).padding(start = 24.dp, end = 22.dp, top = 20.dp, bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f).padding(end = 10.dp)) {
                    LabelPill(labelFor(p), if (filled) ink.copy(alpha = 0.9f) else pillTint(p))
                    Spacer(Modifier.height(14.dp))
                    WordsName(p.heroText, ink, onClick = { c.onCopy(p.heroText) })
                }
                Pinwheel(
                    quarters = listOf(colour, tint, comp, shade), outlined = if (filled) 0 else -1,
                    compHex = compHex, ink = ink, onCopy = { if (compHex.isNotBlank()) c.onCopy(compHex) }
                )
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(12.dp))

            // The arrow is as tall as the three lines beside it.
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShareArrow(c.onShare, ink, iconSize = 50.dp)
                Spacer(Modifier.width(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(rgb, hsl).filter { it.isNotBlank() }.forEach { line ->
                        Text(
                            line,
                            style = sans(14.5f, FontWeight.Medium, if (c.flash == line) c.accent else ink.copy(alpha = 0.94f)),
                            maxLines = 1, softWrap = false, modifier = Modifier.plainClick { c.onCopy(line) }
                        )
                    }
                    if (verdict.isNotBlank()) {
                        // A highlighter in the colour itself, set in the text colour that reads on it. A filled card outlines it instead.
                        Box(
                            Modifier
                                .then(if (filled) Modifier.border(1.dp, ink.copy(alpha = 0.7f)) else Modifier.background(colour))
                                .plainClick { c.onCopy(verdict) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(verdict, style = sans(12f, FontWeight.Medium, if (filled) ink else chipText), maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // The hex, clear of the chamfer.
            Box(Modifier.fillMaxWidth().padding(start = 30.dp)) {
                HeroValue(
                    p.bigText, TextStyle(fontFamily = DisplayFont, fontSize = 62.sp, color = hexColour),
                    minSize = 30f, splitSuffix = false, onClick = { c.onCopy(p.bigText) }
                )
            }
        }
    }
}

/** The name at one size: never split inside a word, never more than three lines. */
@Composable
private fun WordsName(name: String, ink: Color, onClick: () -> Unit) {
    val tm = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = sans(1f, FontWeight.Medium, ink)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxPx = with(density) { maxWidth.toPx() }
        val fit = remember(name, maxPx) { fitWordBlocks(tm, listOf(name), style, maxPx, 34f, 20f, 3) }
        Column(Modifier.plainClick(onClick)) {
            fit.lines.firstOrNull().orEmpty().forEach { line ->
                Text(line, style = style.copy(fontSize = fit.size.sp).tight(0.98f), softWrap = false, maxLines = 1)
            }
        }
    }
}

/**
 * Four quarter-discs turning round a common centre. The complement's bytes ("35 / CB / CB") hang below its
 * tip on a hairline, upright, so they read at any size.
 * [outlined] is the index of a quarter to draw as an outline only (the one that matches a filled card), or -1.
 */
@Composable
private fun Pinwheel(quarters: List<Color>, outlined: Int, compHex: String, ink: Color, onCopy: () -> Unit) {
    val q = 46.dp
    val bytes = compHex.removePrefix("#").chunked(2).take(3)
    Column(Modifier.width(q * 2)) {
        Canvas(Modifier.size(q * 2)) {
            val qp = q.toPx()
            drawPinwheel(qp, quarters, outlined, ink)
            if (bytes.isNotEmpty()) drawCircle(ink, 2.5.dp.toPx(), Offset(qp * 2f, qp * 2f))
        }
        if (bytes.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().plainClick(onCopy), horizontalArrangement = Arrangement.End) {
                Column(Modifier.padding(end = 6.dp, top = 4.dp), horizontalAlignment = Alignment.End) {
                    bytes.forEach { b -> Text(b, style = mono(12f, ink, 0.6f, FontWeight.Bold), maxLines = 1) }
                }
                Box(Modifier.width(1.dp).height(52.dp).background(ink))
            }
        }
    }
}

private fun DrawScope.drawPinwheel(q: Float, colours: List<Color>, outlined: Int, ink: Color) {
    // Each quarter is centred on a corner of its own cell, turning a quarter-turn from the one before.
    val origins = listOf(Offset(0f, 0f), Offset(q, 0f), Offset(q, q), Offset(0f, q))
    val centres = listOf(Offset(0f, q), Offset(0f, 0f), Offset(q, 0f), Offset(q, q))
    val starts = listOf(-90f, 0f, 90f, 180f)
    for (i in 0 until 4) {
        val ctr = origins[i] + centres[i]
        val a = Math.toRadians(starts[i].toDouble())
        val path = Path().apply {
            moveTo(ctr.x, ctr.y)
            lineTo(ctr.x + (cos(a) * q).toFloat(), ctr.y + (sin(a) * q).toFloat())
            arcTo(Rect(ctr.x - q, ctr.y - q, ctr.x + q, ctr.y + q), starts[i], 90f, false)
            close()
        }
        if (i == outlined) drawPath(path, ink.copy(alpha = 0.85f), style = Stroke(1.2.dp.toPx()))
        else drawPath(path, colours[i])
    }
}
