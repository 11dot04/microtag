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
// COLOUR, the brutalist card. One square, drawn on a 949-unit grid so every piece sits where the concept puts it.
//   frame     a hairline frame inside the card: the label tab at top left, viewfinder ticks round the name zone,
//             a cut foot at bottom left. Three zones: name, middle band, lower section.
//   name zone the name, large and light, and the name, up to two lines
//   band      a real Code 39 barcode of the hex, a strip of glyphs, a wireframe globe, the share arrow's neighbour:
//             an asterisk with the rgb and hsl
//   lower     left: a field of triangles, the share arrow, and the contrast verdict in a dotted box filled with the colour.
//             right: a lightness gauge, the hex in the display face, the
//             complement as a swatch, and the 24 bits
// A colour too dark to show on black gets a card filled with itself, white ink, and white ink.
// ==========================================================

private const val G = 949f  // the grid width

@Composable
internal fun ColourCard(c: CardCtx, modifier: Modifier) {
    val p = c.p
    val colour = p.palette.getOrNull(0)?.let { Color(it) } ?: c.accent
    val comp = p.palette.getOrNull(1)?.let { Color(it) } ?: Paper.Ink
    val filled = Surface.isFilled(colour)
    val surface = Surface.forColour(colour)
    val ink = Color.White
    val rgb = p.rows.firstOrNull { it.first == "RGB" }?.second.orEmpty()
    val hsl = p.rows.firstOrNull { it.first == "HSL" }?.second.orEmpty()
    val verdict = p.rows.firstOrNull { it.first == "Readable with" }?.second.orEmpty()
    // "Black text suitable · 17.9:1" is set as two lines: the verdict, then the ratio.
    val verdictText = verdict.split(" · ").let { if (it.size == 2) "${it[0]},\n${it[1]} contrast" else verdict }
    val verdictInk = if (filled) ink else if (verdict.contains("black", ignoreCase = true)) Color.Black else Color.White
    val hex = p.bigText.removePrefix("#").uppercase()
    val hexColour = if (c.flash == p.bigText) c.accent else ink
    val compHex = hexOf(comp)
    val value = remember(hex) { hex.toLongOrNull(16)?.toInt() ?: 0 }
    val dec = remember(value) { value.toString().padStart(11, '0').take(11) }
    val left = "${dec.take(5)} ${dec.substring(5, 10)} ${dec[10]}"
    val right = remember(comp) { (hexOf(comp).removePrefix("#").toLongOrNull(16) ?: 0L).toString().padStart(11, '0').take(11).let { "${it.take(5)} ${it.substring(5, 10)} ${it[10]}" } }
    val bars = remember(hex) { code39(hex) }
    val tm = rememberTextMeasurer()
    val density = LocalDensity.current

    CardFrame(p, modifier, c.drag, surface, 380.dp, padded = false) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val u = maxWidth.value / G
            fun d(v: Float): Dp = (v * u).dp

            // ---- everything drawn: frame, ticks, dividers, bars, glyphs, gauge, swatches
            Canvas(Modifier.fillMaxSize()) {
                val k = size.width / G
                val hair = 2f * k
                val line = ink.copy(alpha = 0.92f)

                // The frame, then the label tab.
                val frame = Path().apply {
                    moveTo(235f * k, 43f * k)
                    lineTo(907f * k, 43f * k)
                    lineTo(907f * k, 868f * k)
                    quadraticTo(907f * k, 905f * k, 870f * k, 905f * k)
                    lineTo(132f * k, 905f * k)
                    lineTo(44f * k, 817f * k)
                    lineTo(44f * k, 84f * k)
                }
                drawPath(frame, line, style = Stroke(hair))
                // The label: a stadium. The frame's top and left lines leave it at its tangents.
                drawRoundRect(line, Offset(44f * k, 43f * k), Size(231f * k, 82f * k), CornerRadius(41f * k), style = Stroke(hair))

                // Viewfinder ticks round the name zone.
                tick(Offset(852f * k, 90f * k), Offset(887f * k, 90f * k), Offset(887f * k, 62f * k), line, hair)
                tick(Offset(92f * k, 415f * k), Offset(64f * k, 415f * k), Offset(64f * k, 445f * k), line, hair)
                tick(Offset(857f * k, 415f * k), Offset(887f * k, 415f * k), Offset(887f * k, 445f * k), line, hair)

                // Zone dividers.
                drawLine(line, Offset(44f * k, 455f * k), Offset(907f * k, 455f * k), hair)
                drawLine(line, Offset(44f * k, 605f * k), Offset(907f * k, 605f * k), hair)
                drawLine(line, Offset(352f * k, 605f * k), Offset(352f * k, 905f * k), hair)

                // Barcode of the hex. The number strips are the colour and its opposite in decimal.
                run {
                    val x0 = 64f * k; val w = 443f * k; val y0 = 467f * k; val h = 118f * k
                    val total = bars.sum(); val unit = w / total
                    var x = x0
                    bars.forEachIndexed { i, b ->
                        val bw = b * unit
                        if (i % 2 == 0) drawRect(ink, Offset(x, y0), Size(bw, h))
                        x += bw
                    }
                    drawRect(ink, Offset(92f * k, 566f * k), Size(150f * k, 24f * k))
                    drawRect(ink, Offset(330f * k, 566f * k), Size(150f * k, 24f * k))
                }

                // Glyph strip, then a wireframe globe.
                glyphStrip(523f * k, 467f * k, 197f * k, 35f * k, line, hair)
                globe(732f * k, 467f * k, 150f * k, 35f * k, line, hair)
                // The asterisk beside rgb / hsl.
                asterisk(Offset(549f * k, 565f * k), 28f * k, line, hair * 1.3f)

                // Triangles.
                for (r in 0 until 5) for (cc in 0 until 6) {
                    val x = 62f * k + cc * 23f * k
                    val y = 612f * k + r * 22f * k
                    drawPath(Path().apply {
                        moveTo(x + 9f * k, y); lineTo(x + 18f * k, y + 16f * k); lineTo(x, y + 16f * k); close()
                    }, ink)
                }

                // The verdict box: filled with the colour (or just outlined on a filled card), dotted, with a cut foot.
                val box = Path().apply {
                    moveTo(64f * k, 738f * k); lineTo(329f * k, 738f * k); lineTo(329f * k, 880f * k)
                    lineTo(127f * k, 880f * k); lineTo(64f * k, 817f * k); close()
                }
                if (!filled) drawPath(box, colour)
                drawPath(box, line, style = Stroke(hair, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * k, 5f * k))))

                // Lightness gauge: a white rule with a pointer where the colour sits.
                val gTop = 612f * k; val gH = 246f * k
                drawRect(line, Offset(372f * k, gTop), Size(10f * k, gH))
                run {
                    val y = gTop + (1f - lightness(colour)) * gH
                    drawPath(Path().apply {
                        moveTo(386f * k, y); lineTo(398f * k, y - 6f * k); lineTo(398f * k, y + 6f * k); close()
                    }, ink)
                }

                // The complement as a swatch.
                drawRoundRect(comp, Offset(785f * k, 785f * k), Size(102f * k, 100f * k), CornerRadius(28f * k))
            }

            // ---- text and things to tap, all on the same grid
            // Label, inside the tab.
            Box(Modifier.offset(d(44f), d(43f)).size(d(231f), d(82f)), contentAlignment = Alignment.Center) {
                Text(labelFor(p), style = sans(33f * u, FontWeight.Medium, ink, 1.4f), maxLines = 1)
            }
            Box(Modifier.offset(d(280f), d(46f))) { PinGlyph(c) }

            // The name: one size, never split inside a word, two lines if it can, tight leading.
            val nameStyle = sans(1f, FontWeight.Normal, ink)
            val spPx = density.density * density.fontScale
            val fit = remember(p.heroText, u) {
                fitWordBlocks(
                    tm, listOf(p.heroText), nameStyle, with(density) { d(780f).toPx() }, 128f * u, 48f * u, 2,
                    with(density) { d(262f).toPx() }, spPx, ::leadFor
                )
            }
            val nameLead = leadFor(fit.lines.firstOrNull().orEmpty().size)
            Column(Modifier.offset(d(88f), d(140f)).plainClick { c.onCopy(p.heroText) }) {
                fit.lines.firstOrNull().orEmpty().forEach { ln ->
                    TightLine(ln, nameStyle.copy(fontSize = fit.size.sp).tight(1f), nameLead)
                }
            }

            // Barcode numbers, printed on the white strips.
            Box(Modifier.offset(d(92f), d(568f)).width(d(150f))) {
                Text(left, style = mono(14f * u, Color.Black, 0.3f, FontWeight.Bold), maxLines = 1, softWrap = false)
            }
            Box(Modifier.offset(d(330f), d(568f)).width(d(150f))) {
                Text(right, style = mono(14f * u, Color.Black, 0.3f, FontWeight.Bold), maxLines = 1, softWrap = false)
            }

            // rgb and hsl, beside the asterisk.
            val longest = maxOf(rgb.length, hsl.length, 12)
            val lineSize = minOf(35f, 300f / (longest * 0.56f))
            Column(Modifier.offset(d(605f), d(529f))) {
                listOf(rgb, hsl).filter { it.isNotBlank() }.forEach { l ->
                    Text(
                        l, style = sans(lineSize * u, FontWeight.Medium, if (c.flash == l) c.accent else ink),
                        maxLines = 1, softWrap = false, modifier = Modifier.plainClick { c.onCopy(l) }
                    )
                }
            }

            // The share arrow, in the lower left.
            // ShareArrow centres its glyph in a larger box, so the box is placed from the glyph's centre.
            val arrowIcon = d(124f)
            val arrowBox = maxOf(48.dp, arrowIcon + 14.dp)
            Box(Modifier.offset(d(265f) - arrowBox / 2, d(668f) - arrowBox / 2)) { ShareArrow(c.onShare, ink, iconSize = arrowIcon) }

            // The contrast verdict.
            if (verdict.isNotBlank()) {
                Box(Modifier.offset(d(72f), d(748f)).width(d(246f)).plainClick { c.onCopy(verdict) }) {
                    Text(
                        verdictText, style = sans(25f * u, FontWeight.Bold, verdictInk).copy(textAlign = TextAlign.End, lineHeight = (31f * u).sp),
                        maxLines = 3, modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Gauge label: where the colour sits.
            Text(
                "L${(lightness(colour) * 100).toInt()}", style = mono(12f * u, ink.copy(alpha = 0.7f), 0.4f, FontWeight.Bold),
                modifier = Modifier.offset(d(386f), d(866f)), maxLines = 1, softWrap = false
            )

            // The hex: a small sans hash, the digits in the display face.
            Text("#", style = sans(54f * u, FontWeight.Bold, hexColour), modifier = Modifier.offset(d(414f), d(690f)))
            Box(Modifier.offset(d(462f), d(655f)).width(d(430f)).plainClick { c.onCopy(p.bigText) }) {
                FitText(
                    AnnotatedString(hex),
                    TextStyle(fontFamily = PostFont, fontSize = (118f * u).sp, color = hexColour),
                    Modifier.fillMaxWidth(), minSize = 30.sp
                )
            }

            // The complement.
            Column(
                Modifier.offset(d(500f), d(788f)).width(d(278f)).plainClick { c.onCopy(compHex) },
                horizontalAlignment = Alignment.End
            ) {
                Text(compHex, style = mono(16f * u, ink.copy(alpha = 0.85f), 0.4f), maxLines = 1, softWrap = false)
                Text("complementary", style = sans(16f * u, FontWeight.Light, ink.copy(alpha = 0.85f)), maxLines = 1, softWrap = false)
            }
            Box(Modifier.offset(d(785f), d(785f)).size(d(102f), d(100f)).plainClick { c.onCopy(compHex) })

            // The 24 bits.
            if (hex.length == 6) {
                val bits = remember(hex) { hex.chunked(2).joinToString(" ") { it.toInt(16).toString(2).padStart(8, '0') } }
                Text(
                    bits, style = mono(14f * u, ink.copy(alpha = 0.8f), 0.5f), maxLines = 1, softWrap = false,
                    modifier = Modifier.offset(d(440f), d(866f))
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

private fun DrawScope.asterisk(c: Offset, r: Float, colour: Color, w: Float) {
    for (i in 0 until 4) {
        val a = Math.toRadians(i * 45.0)
        val dx = (Math.cos(a) * r).toFloat(); val dy = (Math.sin(a) * r).toFloat()
        drawLine(colour, Offset(c.x - dx, c.y - dy), Offset(c.x + dx, c.y + dy), w)
    }
}

/** A box of four pictograms on a square grid (a crossed square, a six-spoke asterisk, a ringed dot, an up arrow) and a small pill. */
private fun DrawScope.glyphStrip(x: Float, y: Float, w: Float, h: Float, colour: Color, hair: Float) {
    val sw = hair * 1.1f
    drawRect(colour, Offset(x, y), Size(w, h), style = Stroke(hair))
    for (i in 1..4) drawLine(colour, Offset(x + h * i, y), Offset(x + h * i, y + h), hair * 0.8f)
    val r = h * 0.26f
    fun centre(i: Int) = Offset(x + h * i + h / 2f, y + h / 2f)
    // crossed square
    centre(0).let { c ->
        drawRect(colour, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(sw))
        drawLine(colour, Offset(c.x - r, c.y + r), Offset(c.x + r, c.y - r), sw)
    }
    // six-spoke asterisk
    centre(1).let { c ->
        for (i in 0 until 3) {
            val a = Math.toRadians(i * 60.0 + 90.0)
            val dx = (Math.cos(a) * r * 1.1f).toFloat(); val dy = (Math.sin(a) * r * 1.1f).toFloat()
            drawLine(colour, Offset(c.x - dx, c.y - dy), Offset(c.x + dx, c.y + dy), sw, StrokeCap.Round)
        }
    }
    // ringed dot
    centre(2).let { c ->
        drawCircle(colour, r, c, style = Stroke(sw))
        drawCircle(colour, r * 0.36f, c)
    }
    // up arrow
    centre(3).let { c ->
        drawLine(colour, Offset(c.x, c.y + r), Offset(c.x, c.y - r), sw, StrokeCap.Round)
        drawLine(colour, Offset(c.x - r * 0.8f, c.y - r * 0.1f), Offset(c.x, c.y - r), sw, StrokeCap.Round)
        drawLine(colour, Offset(c.x + r * 0.8f, c.y - r * 0.1f), Offset(c.x, c.y - r), sw, StrokeCap.Round)
    }
    // the pill, with a dot at its right end
    val px = x + 4 * h + h * 0.25f
    val pw = w - 4 * h - h * 0.5f
    drawRoundRect(colour, Offset(px, y + h * 0.27f), Size(pw, h * 0.46f), CornerRadius(h * 0.23f), style = Stroke(hair))
    drawCircle(colour, h * 0.1f, Offset(px + pw - h * 0.23f, y + h / 2f))
}

/** A wireframe globe seen as an ellipse: outline, two meridians, a centre line and the equator. */
private fun DrawScope.globe(x: Float, y: Float, w: Float, h: Float, colour: Color, hair: Float) {
    drawOval(colour, Offset(x, y), Size(w, h), style = Stroke(hair))
    drawOval(colour, Offset(x + w * 0.22f, y), Size(w * 0.56f, h), style = Stroke(hair * 0.8f))
    drawOval(colour, Offset(x + w * 0.38f, y), Size(w * 0.24f, h), style = Stroke(hair * 0.8f))
    drawLine(colour, Offset(x + w / 2f, y), Offset(x + w / 2f, y + h), hair * 0.8f)
    drawLine(colour, Offset(x, y + h / 2f), Offset(x + w, y + h / 2f), hair * 0.8f)
    drawLine(colour, Offset(x + w * 0.06f, y + h * 0.25f), Offset(x + w * 0.94f, y + h * 0.25f), hair * 0.6f)
    drawLine(colour, Offset(x + w * 0.06f, y + h * 0.75f), Offset(x + w * 0.94f, y + h * 0.75f), hair * 0.6f)
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
