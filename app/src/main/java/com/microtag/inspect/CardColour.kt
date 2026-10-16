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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ==========================================================
// COLOUR. Near-square, three round corners and a chamfered foot.
//   left   name (wraps), share arrow and the loose rgb / hsl lines, a highlighter chip for contrast
//   right  two hatched discs (the colour big, its complement smaller and lower) and a leader to the complement's hex
//   foot   the hex, large
// Very dark colours put the card on mid-grey with black ink so they can still be seen.
// ==========================================================

private fun lift(c: Color, surfaceGrey: Boolean): Color =
    if (!surfaceGrey && c.luminance() < 0.15f) lerp(c, Color.White, 0.38f) else c

@Composable
internal fun ColourCard(c: CardCtx, modifier: Modifier) {
    val p = c.p
    val colour = p.palette.getOrNull(0)?.let { Color(it) } ?: c.accent
    val comp = p.palette.getOrNull(1)?.let { Color(it) } ?: Paper.Ink
    val grey = Surface.isGrey(colour)
    val surface = Surface.forColour(colour)
    val ink = if (grey) Color.Black else Color.White
    val rgb = p.rows.firstOrNull { it.first == "RGB" }?.second.orEmpty()
    val hsl = p.rows.firstOrNull { it.first == "HSL" }?.second.orEmpty()
    val readable = p.rows.firstOrNull { it.first == "Readable with" }?.second.orEmpty()
    val compHex = p.rows.firstOrNull { it.first == "Complement" }?.second.orEmpty()
    val chipText = if (readable.startsWith("Black", true)) Color.Black else Color.White
    val footH = 76.dp

    CardFrame(p, modifier, c.drag, surface, 320.dp, padded = false) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(0.94f)) {
            val artW = 112.dp
            val artH = maxHeight - footH

            // Right: the two discs and the leader.
            ColourArtColumn(
                colour = lift(colour, grey), comp = lift(comp, grey), compHex = compHex, ink = ink,
                modifier = Modifier.align(Alignment.TopEnd).width(artW).height(artH).padding(end = 22.dp, top = 20.dp),
                onCopy = { if (compHex.isNotBlank()) c.onCopy(compHex) }
            )

            // Left: name, arrow beside the details, contrast chip.
            Column(
                Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 20.dp, end = artW + 10.dp).height(artH)
            ) {
                LabelPill(labelFor(p), if (grey) Color.Black.copy(alpha = 0.7f) else pillTint(p))
                Spacer(Modifier.height(14.dp))
                AutoWrapText(
                    p.heroText, sans(34f, FontWeight.Medium, ink), maxLines = 3, minSize = 18f,
                    modifier = Modifier.fillMaxWidth(), leading = 0.98f, onClick = { c.onCopy(p.heroText) }
                )
                Spacer(Modifier.height(6.dp))
                // The arrow sits directly under the name, in a fixed slot, beside the details.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(48.dp).height(48.dp)) { ShareArrow(c.onShare, ink) }
                    Spacer(Modifier.width(2.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf(rgb, hsl).filter { it.isNotBlank() }.forEach { line ->
                            Text(
                                line,
                                style = sans(12f, FontWeight.Medium, if (c.flash == line) c.accent else ink.copy(alpha = 0.88f)),
                                maxLines = 1, softWrap = false, modifier = Modifier.plainClick { c.onCopy(line) }
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (readable.isNotBlank()) {
                    // A highlighter: sharp corners, filled with the colour itself, the text colour that reads on it.
                    Box(
                        Modifier
                            .background(colour)
                            .then(if (grey || colour.luminance() < 0.15f) Modifier.border(0.75.dp, ink.copy(alpha = 0.45f)) else Modifier)
                            .plainClick { c.onCopy(readable) }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(readable.replace(", ", " · "), style = sans(11f, FontWeight.Bold, chipText), maxLines = 1)
                    }
                }
            }

            // Foot: the hex, large, starting past the chamfer.
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(footH).padding(start = 32.dp, end = 22.dp, bottom = 16.dp), contentAlignment = Alignment.BottomStart) {
                HeroValue(
                    p.bigText, TextStyle(fontFamily = DisplayFont, fontSize = 60.sp, color = if (c.flash == p.bigText) c.accent else ink),
                    minSize = 28f, splitSuffix = false, onClick = { c.onCopy(p.bigText) }
                )
            }
        }
    }
}

@Composable
private fun ColourArtColumn(
    colour: Color, comp: Color, compHex: String, ink: Color, modifier: Modifier, onCopy: () -> Unit
) {
    val tm = rememberTextMeasurer()
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val r1 = dp(44f)
            val r2 = r1 * 0.7f
            val c1 = Offset(size.width * 0.46f, r1 + dp(2f))
            val c2 = Offset(c1.x - dp(8f), c1.y + r1 + r2 - dp(14f))
            val pitch = dp(5.5f)
            val line = dp(1f)
            // Same direction, half a pitch apart: where the discs overlap their lines interleave instead of piling up.
            hatchIn(ovalPath(c1.x, c1.y, r1, r1), Rect(Offset(c1.x - r1, c1.y - r1), Size(r1 * 2, r1 * 2)), colour, pitch, line, true, 0f)
            hatchIn(ovalPath(c2.x, c2.y, r2, r2), Rect(Offset(c2.x - r2, c2.y - r2), Size(r2 * 2, r2 * 2)), comp, pitch, line, true, pitch / 2f)

            if (compHex.isNotBlank()) {
                // Leader: a dot on the complement, a jog, then straight down beside the vertical hex.
                val ang = 38.0 * PI / 180.0
                val dot = Offset(c2.x + (r2 * cos(ang)).toFloat(), c2.y + (r2 * sin(ang)).toFloat())
                val xr = size.width - dp(7f)
                val jog = dp(12f)
                var fs = 13
                var st = sans(fs.toFloat(), FontWeight.Bold, ink, 1f)
                var m = tm.measure(compHex, st, softWrap = false, maxLines = 1)
                val bottom = size.height - dp(4f)
                val topLimit = dot.y + jog + dp(14f)
                // Size the vertical hex to fit, never wrap or clip.
                while (fs > 8 && bottom - m.size.width < topLimit) {
                    fs -= 1
                    st = sans(fs.toFloat(), FontWeight.Bold, ink, 1f)
                    m = tm.measure(compHex, st, softWrap = false, maxLines = 1)
                }
                val textTop = bottom - m.size.width
                val hair = dp(1f)
                drawLine(ink, dot, Offset(xr - jog, dot.y), strokeWidth = hair)
                drawLine(ink, Offset(xr - jog, dot.y), Offset(xr, dot.y + jog), strokeWidth = hair)
                drawLine(ink, Offset(xr, dot.y + jog), Offset(xr, textTop - dp(6f)), strokeWidth = hair)
                drawCircle(ink, dp(2.5f), dot)
                val pivot = Offset(xr - m.size.height / 2f, bottom)
                rotate(-90f, pivot = pivot) { label(tm, compHex, pivot, st) }
            }
        }
        if (compHex.isNotBlank()) {
            Box(Modifier.align(Alignment.BottomEnd).width(44.dp).fillMaxHeight(0.62f).plainClick(onCopy))
        }
    }
}
