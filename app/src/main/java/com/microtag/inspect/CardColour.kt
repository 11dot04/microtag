package com.microtag.inspect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.ui.FitText

// ==========================================================
// COLOUR. The colour is the card.
//   field     a flat block of the colour, full bleed, stepped like a Nezu chart: it runs the whole width at the top and
//             steps in on the right, leaving a notch for the complement as a second, smaller block (Mosaic: colour as the object).
//             On it, in whichever ink contrasts: the label pill, tiny mono spec lines (hex, rgb, hsl), crop marks, the share arrow,
//             and the name set large, tight and ragged-left (Aurora).
//   lower     the theme's own surface and ink (they follow light, dark and AMOLED): a lightness ruler with a pointer,
//             the 24 bits, a pill with the contrast verdict, a small Code 39 strip of the hex (Laneige label, Descentro), and the
//             hex itself as the one big numeral.
// Drawn on a 949-unit square so every piece sits where it is put.
// ==========================================================

private const val G = 949f  // the grid width

@Composable
internal fun ColourCard(c: CardCtx, modifier: Modifier) {
    val p = c.p
    val colour = p.palette.getOrNull(0)?.let { Color(it) } ?: c.accent
    val comp = p.palette.getOrNull(1)?.let { Color(it) } ?: Paper.Ink
    // Ink on the colour field: chosen for contrast against the colour itself. Everything else follows the theme.
    val onField = Surface.inkOn(colour)
    val ink = Paper.Ink
    val mute = Paper.Mute
    val rule = Paper.Line
    val rgb = p.rows.firstOrNull { it.first == "RGB" }?.second.orEmpty()
    val hsl = p.rows.firstOrNull { it.first == "HSL" }?.second.orEmpty()
    val verdict = p.rows.firstOrNull { it.first == "Readable with" }?.second.orEmpty()
    // "Black text suitable · 17.9:1" is set on one line, in capitals.
    val verdictText = verdict.uppercase()
    val hex = p.bigText.removePrefix("#").uppercase()
    val hexColour = if (c.flash == p.bigText) c.accent else ink
    val compHex = hexOf(comp)
    val bars = remember(hex) { code39(hex) }
    val tm = rememberTextMeasurer()
    val density = LocalDensity.current
    val light = lightness(colour)

    CardFrame(p, modifier, c.drag, Surface.Black, 380.dp, padded = false) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val u = maxWidth.value / G
            fun d(v: Float): Dp = (v * u).dp

            // ---- everything drawn: the field, marks, ruler, barcode, complement
            Canvas(Modifier.fillMaxSize()) {
                val k = size.width / G
                val hair = 2f * k
                val fieldInk = onField.copy(alpha = 0.9f)

                // The field: full width on top, stepping in on the right.
                val field = Path().apply {
                    moveTo(0f, 0f); lineTo(G * k, 0f); lineTo(G * k, 470f * k)
                    lineTo(640f * k, 470f * k); lineTo(640f * k, 600f * k); lineTo(0f, 600f * k); close()
                }
                drawPath(field, colour)
                // A colour close to the card's own surface would vanish: give the block a faint edge.
                drawPath(field, rule.copy(alpha = 0.35f), style = Stroke(hair))

                // Crop marks round the name zone.
                val m = 36f * k; val l = 26f * k
                tick(Offset(m + l, 150f * k), Offset(m, 150f * k), Offset(m, 150f * k + l), fieldInk, hair)
                tick(Offset(G * k - m - l, 150f * k), Offset(G * k - m, 150f * k), Offset(G * k - m, 150f * k + l), fieldInk, hair)
                tick(Offset(m + l, 570f * k), Offset(m, 570f * k), Offset(m, 570f * k - l), fieldInk, hair)
                tick(Offset(640f * k - m - l, 570f * k), Offset(640f * k - m, 570f * k), Offset(640f * k - m, 570f * k - l), fieldInk, hair)

                // Label pill, outlined.
                drawRoundRect(fieldInk, Offset(40f * k, 38f * k), Size(214f * k, 66f * k), CornerRadius(33f * k), style = Stroke(hair))

                // The complement, as its own block in the notch.
                drawRect(comp, Offset(790f * k, 490f * k), Size(119f * k, 100f * k))
                drawRect(rule.copy(alpha = 0.35f), Offset(790f * k, 490f * k), Size(119f * k, 100f * k), style = Stroke(hair))

                // Lightness ruler: 50 steps, every fifth longer, every tenth longest; a pointer where the colour sits.
                val x0 = 44f * k; val x1 = 905f * k; val yBase = 700f * k
                for (i in 0..50) {
                    val x = x0 + (x1 - x0) * i / 50f
                    val h = (if (i % 10 == 0) 62f else if (i % 5 == 0) 40f else 24f) * k
                    drawLine(rule, Offset(x, yBase), Offset(x, yBase - h), hair)
                }
                val px = x0 + (x1 - x0) * light
                drawPath(Path().apply {
                    moveTo(px, yBase + 6f * k); lineTo(px - 9f * k, yBase + 22f * k); lineTo(px + 9f * k, yBase + 22f * k); close()
                }, ink)

                // A small Code 39 strip of the hex.
                run {
                    val bx = 44f * k; val bw = 380f * k; val by = 846f * k; val bh = 56f * k
                    val total = bars.sum(); val unit = bw / total
                    var x = bx
                    bars.forEachIndexed { i, b ->
                        val w = b * unit
                        if (i % 2 == 0) drawRect(ink, Offset(x, by), Size(w, bh))
                        x += w
                    }
                }

                // The verdict pill, outlined.
                drawRoundRect(rule, Offset(44f * k, 750f * k), Size(380f * k, 56f * k), CornerRadius(28f * k), style = Stroke(hair))
                drawCircle(colour, 11f * k, Offset(72f * k, 778f * k))
                drawCircle(rule, 11f * k, Offset(72f * k, 778f * k), style = Stroke(hair))
            }

            // ---- on the field
            Box(Modifier.offset(d(40f), d(38f)).size(d(214f), d(66f)), contentAlignment = Alignment.Center) {
                Text(labelFor(p), style = sans(30f * u, FontWeight.Medium, onField, 1.4f), maxLines = 1)
            }
            Box(Modifier.offset(d(264f), d(40f))) { PinGlyph(c) }
            // Tiny mono spec lines, Nezu style: hex, rgb, hsl.
            Column(Modifier.offset(d(44f), d(174f))) {
                listOf("#$hex", rgb, hsl).filter { it.isNotBlank() }.forEach { l ->
                    Text(
                        l.uppercase(), style = mono(17f * u, if (c.flash == l) c.accent else onField.copy(alpha = 0.8f), 0.6f),
                        maxLines = 1, softWrap = false, modifier = Modifier.plainClick { c.onCopy(l) }
                    )
                }
            }
            // The share arrow, large, top right.
            val arrowIcon = d(104f)
            val arrowBox = maxOf(48.dp, arrowIcon + 14.dp)
            Box(Modifier.offset(d(850f) - arrowBox / 2, d(92f) - arrowBox / 2)) { ShareArrow(c.onShare, onField, iconSize = arrowIcon) }

            // The name: big, tight, ragged-left, bottom-aligned in the field.
            val nameStyle = sans(1f, FontWeight.Bold, onField)
            val spPx = density.density * density.fontScale
            val fit = remember(p.heroText, u) {
                fitWordBlocks(
                    tm, listOf(p.heroText), nameStyle, with(density) { d(560f).toPx() }, 124f * u, 40f * u, 2,
                    with(density) { d(250f).toPx() }, spPx, ::leadFor
                )
            }
            val nameLead = leadFor(fit.lines.firstOrNull().orEmpty().size)
            Box(Modifier.offset(d(44f), d(300f)).size(d(560f), d(250f)).plainClick { c.onCopy(p.heroText) }, contentAlignment = Alignment.BottomStart) {
                Column {
                    fit.lines.firstOrNull().orEmpty().forEach { ln ->
                        TightLine(ln, nameStyle.copy(fontSize = fit.size.sp).tight(1f), nameLead)
                    }
                }
            }

            // ---- the notch: the complement
            Column(
                Modifier.offset(d(650f), d(500f)).width(d(130f)).plainClick { c.onCopy(compHex) },
                horizontalAlignment = Alignment.End
            ) {
                Text("COMPLEMENT", style = mono(12f * u, mute, 0.8f), maxLines = 1, softWrap = false)
                Text(compHex, style = mono(15f * u, ink, 0.5f), maxLines = 1, softWrap = false)
            }

            // ---- lower section, in the theme's ink
            Text("L${(light * 100).toInt()}", style = mono(14f * u, ink, 0.6f, FontWeight.Bold), modifier = Modifier.offset(d(44f), d(618f)), maxLines = 1, softWrap = false)
            Text("DARK", style = mono(11f * u, mute, 1f), modifier = Modifier.offset(d(44f), d(722f)), maxLines = 1, softWrap = false)
            Text("LIGHT", style = mono(11f * u, mute, 1f), modifier = Modifier.offset(d(858f), d(722f)), maxLines = 1, softWrap = false)

            if (verdict.isNotBlank()) {
                Box(Modifier.offset(d(96f), d(750f)).size(d(320f), d(56f)).plainClick { c.onCopy(verdict) }, contentAlignment = Alignment.CenterStart) {
                    Text(verdictText, style = mono(15f * u, ink, 0.5f, FontWeight.Bold), maxLines = 1, softWrap = false)
                }
            }
            if (hex.length == 6) {
                val bits = remember(hex) { hex.chunked(2).joinToString(" ") { it.toInt(16).toString(2).padStart(8, '0') } }
                Text(bits, style = mono(13f * u, mute, 0.5f), modifier = Modifier.offset(d(44f), d(818f)), maxLines = 1, softWrap = false)
            }

            // The hex: the one big numeral, in the display face.
            Text("#", style = sans(46f * u, FontWeight.Bold, hexColour), modifier = Modifier.offset(d(452f), d(770f)))
            Box(Modifier.offset(d(496f), d(740f)).width(d(409f)).plainClick { c.onCopy(p.bigText) }) {
                FitText(
                    AnnotatedString(hex),
                    TextStyle(fontFamily = PostFont, fontSize = (150f * u).sp, color = hexColour),
                    Modifier.fillMaxWidth(), minSize = 30.sp
                )
            }
        }
    }
}

private fun hexOf(c: Color): String =
    "#%02X%02X%02X".format((c.red * 255f + 0.5f).toInt(), (c.green * 255f + 0.5f).toInt(), (c.blue * 255f + 0.5f).toInt())

private fun lightness(c: Color): Float {
    val mx = maxOf(c.red, c.green, c.blue)
    val mn = minOf(c.red, c.green, c.blue)
    return ((mx + mn) / 2f).coerceIn(0f, 1f)
}

// ---------------------------------------------------------------- drawing pieces

private fun DrawScope.tick(a: Offset, corner: Offset, b: Offset, colour: Color, w: Float) {
    drawPath(Path().apply { moveTo(a.x, a.y); lineTo(corner.x, corner.y); lineTo(b.x, b.y) }, colour, style = Stroke(w))
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
