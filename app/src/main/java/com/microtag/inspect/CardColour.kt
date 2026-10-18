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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==========================================================
// COLOUR. Three round corners, a chamfered foot, a notch bitten out of the right edge.
//   top      the label, and a real Code 39 barcode of the hex
//   name     never broken mid-word: one size, the longest word fills the width
//   chips    three paint chips side by side: a lighter step, a darker step, the complement; each with its hex
//   gauge    a lightness bar, black to white through the colour, with a tick where this colour sits
//   middle   the share arrow, rgb and hsl, and the contrast verdict in the colour itself
//   foot     the hex, large, and its 24 bits underneath
// A colour too dark to show on black gets a card filled with itself and white ink.
// Nothing stretches: the card is exactly as tall as what is on it.
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
    val chipText = if (verdict.contains("black", ignoreCase = true)) Color.Black else Color.White
    val hexColour = if (c.flash == p.bigText) c.accent else ink
    val hex = p.bigText.removePrefix("#").uppercase()

    CardFrame(p, modifier, c.drag, surface, 340.dp, padded = false) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 22.dp, top = 20.dp, bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LabelPill(labelFor(p), if (filled) ink.copy(alpha = 0.9f) else pillTint(p))
                PinGlyph(c)
                Spacer(Modifier.weight(1f))
                if (hex.length == 6) Barcode(hex, ink.copy(alpha = 0.9f), Modifier.width(92.dp).height(26.dp))
            }
            Spacer(Modifier.height(14.dp))
            WordsName(p.heroText, ink, onClick = { c.onCopy(p.heroText) })
            Spacer(Modifier.height(16.dp))

            // Paint chips: lighter, darker, complement.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("LIGHTER", tint, ink, Modifier.weight(1f)) { c.onCopy(hexOf(tint)) }
                Chip("DARKER", shade, ink, Modifier.weight(1f)) { c.onCopy(hexOf(shade)) }
                Chip("OPPOSITE", comp, ink, Modifier.weight(1f)) { c.onCopy(hexOf(comp)) }
            }
            Spacer(Modifier.height(12.dp))
            LightnessGauge(colour, ink, Modifier.fillMaxWidth().height(30.dp))
            Spacer(Modifier.height(14.dp))

            // The arrow is as tall as the lines beside it.
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
            Spacer(Modifier.height(10.dp))

            // The hex, clear of the chamfer, with its bits underneath.
            Box(Modifier.fillMaxWidth().padding(start = 30.dp)) {
                HeroValue(
                    p.bigText, TextStyle(fontFamily = DisplayFont, fontSize = 62.sp, color = hexColour),
                    minSize = 30f, splitSuffix = false, onClick = { c.onCopy(p.bigText) }
                )
            }
            if (hex.length == 6) {
                val bits = remember(hex) { hex.chunked(2).joinToString(" ") { it.toInt(16).toString(2).padStart(8, '0') } }
                Text(
                    bits, modifier = Modifier.padding(start = 30.dp, top = 4.dp),
                    style = mono(9f, ink.copy(alpha = 0.5f), 0.6f), maxLines = 1, softWrap = false
                )
            }
        }
    }
}

private fun hexOf(c: Color): String =
    "#%02X%02X%02X".format((c.red * 255f + 0.5f).toInt(), (c.green * 255f + 0.5f).toInt(), (c.blue * 255f + 0.5f).toInt())

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

/** A paint chip: the swatch, with its name and hex printed on it in whichever of black or white reads. */
@Composable
private fun Chip(label: String, colour: Color, ink: Color, modifier: Modifier, onClick: () -> Unit) {
    val on = if (colour.luminance() > 0.45f) Color.Black else Color.White
    Column(
        modifier
            .height(58.dp)
            .background(colour)
            .border(1.dp, ink.copy(alpha = 0.3f))
            .plainClick(onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = mono(8f, on.copy(alpha = 0.8f), 1f, FontWeight.Bold), maxLines = 1, softWrap = false)
        Text(hexOf(colour).removePrefix("#"), style = mono(11f, on, 0.4f, FontWeight.Bold), maxLines = 1, softWrap = false)
    }
}

/** Black to white through the colour, with a tick and its percentage where this colour's lightness falls. */
@Composable
private fun LightnessGauge(colour: Color, ink: Color, modifier: Modifier) {
    val l = remember(colour) {
        val mx = maxOf(colour.red, colour.green, colour.blue)
        val mn = minOf(colour.red, colour.green, colour.blue)
        ((mx + mn) / 2f).coerceIn(0f, 1f)
    }
    val tm = rememberTextMeasurer()
    Canvas(modifier) {
        val barH = 8.dp.toPx()
        val top = 3.dp.toPx()
        drawRect(Brush.horizontalGradient(listOf(Color.Black, colour, Color.White)), Offset(0f, top), Size(size.width, barH))
        drawRect(ink.copy(alpha = 0.35f), Offset(0f, top), Size(size.width, barH), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        val x = (size.width * l).coerceIn(1.dp.toPx(), size.width - 1.dp.toPx())
        drawRect(ink, Offset(x - 1.dp.toPx(), top - 2.dp.toPx()), Size(2.dp.toPx(), barH + 6.dp.toPx()))
        val label = tm.measure("L ${(l * 100).toInt()}%", mono(9f, ink.copy(alpha = 0.7f), 0.6f), softWrap = false, maxLines = 1)
        val tx = (x - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
        drawText(label, topLeft = Offset(tx, barH + 5.dp.toPx()))
    }
}

/** Code 39 of [text]: five bars and four spaces per character, three of the nine wide. */
@Composable
private fun Barcode(text: String, ink: Color, modifier: Modifier) {
    val bars = remember(text) { code39(text) }
    Canvas(modifier) {
        val total = bars.sum()
        val unit = size.width / total
        var x = 0f
        bars.forEachIndexed { i, w ->
            val wpx = w * unit
            if (i % 2 == 0) drawRect(ink, Offset(x, 0f), Size(wpx, size.height))
            x += wpx
        }
    }
}

private val code39Table = mapOf(
    '0' to "nnnwwnwnn", '1' to "wnnwnnnnw", '2' to "nnwwnnnnw", '3' to "wnwwnnnnn", '4' to "nnnwwnnnw",
    '5' to "wnnwwnnnn", '6' to "nnwwwnnnn", '7' to "nnnwnnwnw", '8' to "wnnwnnwnn", '9' to "nnwwnnwnn",
    'A' to "wnnnnwnnw", 'B' to "nnwnnwnnw", 'C' to "wnwnnwnnn", 'D' to "nnnnwwnnw", 'E' to "wnnnwwnnn",
    'F' to "nnwnwwnnn", '*' to "nwnnwnwnn"
)

/** Element widths, alternating bar, space, bar...; characters are separated by a narrow space. */
private fun code39(text: String): List<Float> {
    val out = ArrayList<Float>()
    val full = "*" + text.uppercase().filter { it in code39Table } + "*"
    full.forEachIndexed { i, ch ->
        if (i > 0) out.add(1f)
        code39Table.getValue(ch).forEach { out.add(if (it == 'w') 2.5f else 1f) }
    }
    return out
}
