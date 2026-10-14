package com.microtag.inspect

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.ui.Montserrat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ==========================================================
// CARD ART. One small drawing per kind of card, in the dashboard's language:
// hairlines, diagonal hatching, square caps. Where it can, the drawing is
// made from the card's own data (the clock shows the time, the barcode-ish
// square is derived from the UPI id, the blueprint uses the real sizes).
// ==========================================================

private val Ink = Color(0xFFF6F6F6)
private val Dim = Color(0xFF6E737C)

private fun DrawScope.px(v: Float) = v * density

/** Diagonal hatching inside [clip]. [rising] true draws "/" lines, false draws "\". */
private fun DrawScope.hatchIn(clip: Path, bounds: Rect, color: Color, gap: Float, width: Float, rising: Boolean) {
    clipPath(clip) {
        val h = bounds.height
        var x = bounds.left - h
        while (x < bounds.right + h) {
            if (rising) drawLine(color, Offset(x, bounds.bottom), Offset(x + h, bounds.top), strokeWidth = width)
            else drawLine(color, Offset(x, bounds.top), Offset(x + h, bounds.bottom), strokeWidth = width)
            x += gap
        }
    }
}

private fun ovalPath(cx: Float, cy: Float, rx: Float, ry: Float): Path =
    Path().apply { addOval(Rect(Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))) }

private fun rectPath(r: Rect): Path = Path().apply { addRect(r) }

@Composable
fun CardArt(p: InspectPayload, accent: Color, onCopy: (String) -> Unit, modifier: Modifier = Modifier) {
    when (p.domain) {
        "WIKI" -> WikiArt(p, accent, modifier)
        "COLOUR" -> ColourArt(p, accent, onCopy, modifier)
        "TEXT", "INFO", "" -> Unit
        else -> {
            val tm = rememberTextMeasurer()
            val height = when (p.domain) {
                "DIMS" -> 176.dp
                "DEFINE", "NUM" -> 84.dp
                "URL" -> 92.dp
                "CONV" -> 100.dp
                else -> 128.dp
            }
            Canvas(modifier.fillMaxWidth().height(height)) {
                when (p.domain) {
                    "CALC" -> calcArt(accent)
                    "CONV" -> convArt(accent)
                    "MONEY" -> moneyArt(accent)
                    "TIME" -> timeArt(p, accent)
                    "DATE" -> dateArt(p, accent)
                    "URL" -> urlArt(p, accent, tm)
                    "UPI" -> upiArt(p, accent)
                    "IFSC" -> ifscArt(accent)
                    "PIN" -> pinArt(accent)
                    "NUM" -> numArt(p, accent)
                    "DEFINE" -> defineArt(p, accent)
                    "DIMS" -> dimsArt(p, accent, tm)
                }
            }
        }
    }
}

// ---------------------------------------------------------- COLOUR

@Composable
private fun ColourArt(p: InspectPayload, accent: Color, onCopy: (String) -> Unit, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    val a = p.palette.getOrNull(0)?.let { Color(it) } ?: accent
    val b = p.palette.getOrNull(1)?.let { Color(it) } ?: Ink
    val comp = p.rows.firstOrNull { it.first == "Complement" }?.second.orEmpty()
    Box(modifier.fillMaxWidth().height(150.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val r = px(58f)
            val cy = size.height / 2f
            val ax = px(64f)
            val bx = ax + px(64f)
            val ringW = px(1.6f)
            // Colour circle: "/" hatching over a faint fill. Complement circle: "\" hatching. Where they overlap the lines cross.
            val ca = ovalPath(ax, cy, r, r)
            val cb = ovalPath(bx, cy, r, r)
            drawPath(ca, a.copy(alpha = 0.16f))
            hatchIn(ca, Rect(Offset(ax - r, cy - r), Size(r * 2, r * 2)), a, px(6f), px(1.6f), true)
            hatchIn(cb, Rect(Offset(bx - r, cy - r), Size(r * 2, r * 2)), b, px(6f), px(1.6f), false)
            drawCircle(a, r, Offset(ax, cy), style = Stroke(ringW))
            drawCircle(b, r, Offset(bx, cy), style = Stroke(ringW))

            if (comp.isNotBlank()) {
                val style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Ink, letterSpacing = 1.sp)
                val m = tm.measure(comp, style)
                val lx = size.width - px(30f)
                val py = size.height * 0.84f
                // Leader line from the complement circle to the vertical label.
                val ang = 38.0 * PI / 180.0
                val sx = bx + (r * cos(ang)).toFloat()
                val sy = cy + (r * sin(ang)).toFloat()
                val ex = lx + m.size.height / 2f
                val ey = py + px(8f)
                drawLine(Ink, Offset(sx, sy), Offset(ex, ey), strokeWidth = px(1f))
                drawCircle(Ink, px(2.5f), Offset(sx, sy))
                rotate(-90f, pivot = Offset(lx, py)) {
                    drawText(tm, comp, topLeft = Offset(lx, py), style = style)
                }
            }
        }
        if (comp.isNotBlank()) {
            Box(
                Modifier.align(Alignment.CenterEnd).width(52.dp).fillMaxHeight()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onCopy(comp) }
            )
        }
    }
}

// ---------------------------------------------------------- CALC: a key grid with one solid key

private fun DrawScope.calcArt(accent: Color) {
    val key = px(34f); val gap = px(8f)
    val total = key * 4 + gap * 3
    val x0 = size.width - total
    val y0 = (size.height - (key * 3 + gap * 2)) / 2f
    for (row in 0 until 3) for (col in 0 until 4) {
        val r = Rect(Offset(x0 + col * (key + gap), y0 + row * (key + gap)), Size(key, key))
        val equals = row == 2 && col == 3
        if (equals) {
            drawRect(accent, r.topLeft, r.size)
        } else {
            drawRect(Ink.copy(alpha = 0.28f), r.topLeft, r.size, style = Stroke(px(1f)))
            if ((row + col) % 2 == 0) hatchIn(rectPath(r), r, Ink.copy(alpha = 0.16f), px(5f), px(1f), true)
        }
    }
    // Paper tape running off to the left.
    val lens = listOf(0.5f, 0.32f, 0.44f, 0.24f)
    for (i in lens.indices) {
        val y = y0 + px(10f) + i * px(24f)
        drawLine(Ink.copy(alpha = 0.3f), Offset(0f, y), Offset((x0 - px(24f)) * lens[i], y), strokeWidth = px(1.5f), cap = StrokeCap.Square)
    }
}

// ---------------------------------------------------------- CONV: centimetre and inch rulers, aligned at each inch

private fun DrawScope.convArt(accent: Color) {
    val topY = px(26f); val botY = px(74f)
    val cm = px(10f); val inch = cm * 2.54f
    val x0 = px(6f)
    drawLine(Ink.copy(alpha = 0.5f), Offset(x0, topY), Offset(size.width, topY), strokeWidth = px(1f))
    drawLine(Ink.copy(alpha = 0.5f), Offset(x0, botY), Offset(size.width, botY), strokeWidth = px(1f))
    var i = 0
    while (x0 + i * cm < size.width) {
        val major = i % 5 == 0
        val h = if (major) px(14f) else px(7f)
        drawLine(Ink.copy(alpha = if (major) 0.8f else 0.4f), Offset(x0 + i * cm, topY), Offset(x0 + i * cm, topY - h), strokeWidth = px(1f))
        i++
    }
    i = 0
    while (x0 + i * inch / 4f < size.width) {
        val h = when {
            i % 4 == 0 -> px(14f)
            i % 2 == 0 -> px(10f)
            else -> px(6f)
        }
        drawLine(Ink.copy(alpha = if (i % 4 == 0) 0.8f else 0.4f), Offset(x0 + i * inch / 4f, botY), Offset(x0 + i * inch / 4f, botY + h), strokeWidth = px(1f))
        i++
    }
    // Each inch lines up with 2.54 cm.
    val dash = PathEffect.dashPathEffect(floatArrayOf(px(3f), px(3f)))
    var k = 1
    while (x0 + k * inch < size.width) {
        drawLine(accent, Offset(x0 + k * inch, topY), Offset(x0 + k * inch, botY), strokeWidth = px(1.2f), pathEffect = dash)
        drawCircle(accent, px(3f), Offset(x0 + k * inch, topY))
        drawCircle(accent, px(3f), Offset(x0 + k * inch, botY))
        k++
    }
}

// ---------------------------------------------------------- MONEY: two stacks of coins

private fun DrawScope.coinStack(cx: Float, baseY: Float, w: Float, count: Int, accent: Color) {
    val h = w * 0.3f
    val step = px(11f)
    for (i in 0 until count) {
        val cy = baseY - i * step
        val top = i == count - 1
        drawOval(Color.Black, Offset(cx - w / 2, cy - h / 2), Size(w, h))
        drawOval(if (top) accent else Ink.copy(alpha = 0.45f), Offset(cx - w / 2, cy - h / 2), Size(w, h), style = Stroke(px(1.4f)))
        if (top) {
            hatchIn(ovalPath(cx, cy, w / 2, h / 2), Rect(Offset(cx - w / 2, cy - h / 2), Size(w, h)), accent.copy(alpha = 0.5f), px(5f), px(1f), true)
            drawOval(accent, Offset(cx - w * 0.28f, cy - h * 0.28f), Size(w * 0.56f, h * 0.56f), style = Stroke(px(1f)))
        } else {
            // Edge of the coin.
            drawLine(Ink.copy(alpha = 0.3f), Offset(cx - w / 2, cy), Offset(cx - w / 2, cy + step), strokeWidth = px(1f))
            drawLine(Ink.copy(alpha = 0.3f), Offset(cx + w / 2, cy), Offset(cx + w / 2, cy + step), strokeWidth = px(1f))
        }
    }
}

private fun DrawScope.moneyArt(accent: Color) {
    val base = size.height - px(26f)
    coinStack(px(86f), base, px(112f), 5, accent)
    coinStack(px(196f), base, px(84f), 3, accent.copy(alpha = 0.8f))
    drawLine(Ink.copy(alpha = 0.25f), Offset(0f, base + px(14f)), Offset(size.width, base + px(14f)), strokeWidth = px(1f))
}

// ---------------------------------------------------------- TIME: a clock showing the converted time, and the day as 24 segments

private val timeRe = Regex("""(\d{1,2}):(\d{2})\s*(am|pm)?""", RegexOption.IGNORE_CASE)

private fun DrawScope.timeArt(p: InspectPayload, accent: Color) {
    val m = timeRe.find(p.title)
    var hh = m?.groupValues?.get(1)?.toIntOrNull() ?: 10
    val mm = m?.groupValues?.get(2)?.toIntOrNull() ?: 10
    val ap = m?.groupValues?.get(3)?.lowercase().orEmpty()
    if (ap == "pm" && hh < 12) hh += 12
    if (ap == "am" && hh == 12) hh = 0
    hh %= 24
    val r = px(52f); val cx = px(66f); val cy = size.height / 2f
    drawCircle(Ink.copy(alpha = 0.5f), r, Offset(cx, cy), style = Stroke(px(1.2f)))
    for (i in 0 until 60) {
        val a = i * 6.0 * PI / 180.0
        val major = i % 5 == 0
        val r1 = r - (if (major) px(9f) else px(4f))
        drawLine(
            Ink.copy(alpha = if (major) 0.85f else 0.35f),
            Offset(cx + (r1 * sin(a)).toFloat(), cy - (r1 * cos(a)).toFloat()),
            Offset(cx + ((r - px(1f)) * sin(a)).toFloat(), cy - ((r - px(1f)) * cos(a)).toFloat()),
            strokeWidth = px(1f)
        )
    }
    val minA = mm * 6.0 * PI / 180.0
    val hourA = ((hh % 12) * 30 + mm * 0.5) * PI / 180.0
    drawLine(Ink, Offset(cx, cy), Offset(cx + (r * 0.8 * sin(minA)).toFloat(), cy - (r * 0.8 * cos(minA)).toFloat()), strokeWidth = px(2f), cap = StrokeCap.Square)
    drawLine(accent, Offset(cx, cy), Offset(cx + (r * 0.52 * sin(hourA)).toFloat(), cy - (r * 0.52 * cos(hourA)).toFloat()), strokeWidth = px(3.5f), cap = StrokeCap.Square)
    drawCircle(accent, px(3.5f), Offset(cx, cy))

    // The day, 24 segments; the current hour is solid.
    val x0 = cx + r + px(36f)
    val segW = (size.width - x0 - px(4f)) / 24f
    val y = cy - px(16f)
    for (i in 0 until 24) {
        val rr = Rect(Offset(x0 + i * segW, y), Size(segW - px(1.5f), px(32f)))
        if (i == hh) drawRect(accent, rr.topLeft, rr.size)
        else {
            val night = i < 6 || i >= 20
            drawRect(Ink.copy(alpha = if (night) 0.1f else 0.3f), rr.topLeft, rr.size)
        }
    }
    drawLine(Ink.copy(alpha = 0.4f), Offset(x0, y + px(40f)), Offset(x0 + segW * 24, y + px(40f)), strokeWidth = px(1f))
    for (t in listOf(0, 6, 12, 18, 24)) {
        drawLine(Ink.copy(alpha = 0.4f), Offset(x0 + t * segW, y + px(40f)), Offset(x0 + t * segW, y + px(46f)), strokeWidth = px(1f))
    }
}

// ---------------------------------------------------------- DATE: the month as a grid, the day crosshaired

private val dayRe = Regex("""(\d{1,2})\s+[A-Za-z]{3}""")
private val weekdays = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

private fun DrawScope.dateArt(p: InspectPayload, accent: Color) {
    val src = p.subtitle.ifBlank { p.title }
    val day = dayRe.find(src)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 31) ?: 15
    val wd = weekdays.indexOfFirst { src.lowercase().contains(it) }.let { if (it < 0) 0 else it }
    val first = (((wd - (day - 1)) % 7) + 7) % 7
    val idx = first + day - 1
    val cell = px(20f); val gap = px(6f)
    val w = cell * 7 + gap * 6
    val x0 = size.width - w
    val y0 = (size.height - (cell * 6 + gap * 5)) / 2f
    val tcol = idx % 7; val trow = idx / 7
    // Crosshair through the chosen day.
    val cxm = x0 + tcol * (cell + gap) + cell / 2f
    val cym = y0 + trow * (cell + gap) + cell / 2f
    drawLine(accent.copy(alpha = 0.35f), Offset(cxm, 0f), Offset(cxm, size.height), strokeWidth = px(1f))
    drawLine(accent.copy(alpha = 0.35f), Offset(0f, cym), Offset(size.width, cym), strokeWidth = px(1f))
    for (d in 0 until 7) {
        drawRect(Ink.copy(alpha = 0.5f), Offset(x0 + d * (cell + gap), y0 - px(0f)), Size(cell, px(2f)))
    }
    for (n in 1..31) {
        val i = first + n - 1
        val col = i % 7; val row = i / 7
        val r = Rect(Offset(x0 + col * (cell + gap), y0 + row * (cell + gap)), Size(cell, cell))
        if (n == day) drawRect(accent, r.topLeft, r.size)
        else {
            drawRect(Color.Black, r.topLeft, r.size)
            drawRect(Ink.copy(alpha = if (col >= 5) 0.15f else 0.3f), r.topLeft, r.size, style = Stroke(px(1f)))
        }
    }
}

// ---------------------------------------------------------- URL: an address bar

private fun DrawScope.urlArt(p: InspectPayload, accent: Color, tm: TextMeasurer) {
    val bad = p.status != "OK"
    val bar = Rect(Offset(0f, px(8f)), Size(size.width, px(40f)))
    val line = if (bad) accent else Ink.copy(alpha = 0.5f)
    drawRoundRect(line, bar.topLeft, bar.size, androidx.compose.ui.geometry.CornerRadius(px(20f)), style = Stroke(px(1.3f)))
    // Lock, or a warning triangle.
    val ix = px(24f); val iy = bar.center.y
    if (bad) {
        val t = Path().apply {
            moveTo(ix, iy - px(8f)); lineTo(ix + px(8f), iy + px(7f)); lineTo(ix - px(8f), iy + px(7f)); close()
        }
        drawPath(t, accent, style = Stroke(px(1.5f)))
        drawLine(accent, Offset(ix, iy - px(2f)), Offset(ix, iy + px(2f)), strokeWidth = px(1.5f))
    } else {
        drawRect(accent, Offset(ix - px(7f), iy - px(2f)), Size(px(14f), px(10f)))
        drawArc(accent, 180f, 180f, false, Offset(ix - px(4.5f), iy - px(9f)), Size(px(9f), px(14f)), style = Stroke(px(1.6f)))
    }
    val label = p.title
    val style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Ink)
    drawText(
        tm, label, topLeft = Offset(px(46f), bar.center.y - px(9f)), style = style,
        overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1,
        size = Size(bar.width - px(62f), px(20f))
    )
    // scheme / host / path as three bars.
    val y = px(66f)
    val parts = listOf(0.18f to "SCHEME", 0.56f to "HOST", 0.26f to "PATH")
    var x = 0f
    val total = size.width - px(8f)
    val small = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 8.sp, color = Dim, letterSpacing = 1.sp)
    for ((i, part) in parts.withIndex()) {
        val w = total * part.first
        val r = Rect(Offset(x, y), Size(w, px(8f)))
        if (i == 1) drawRect(accent, r.topLeft, r.size)
        else hatchIn(rectPath(r), r, Ink.copy(alpha = 0.4f), px(4f), px(1f), true)
        drawText(tm, part.second, topLeft = Offset(x, y + px(10f)), style = small)
        x += w + px(4f)
    }
}

// ---------------------------------------------------------- UPI: a square pattern derived from the ID

private fun DrawScope.upiArt(p: InspectPayload, accent: Color) {
    val n = 11
    val cell = px(10.5f)
    val x0 = px(2f); val y0 = (size.height - cell * n) / 2f
    var seed = p.copyValue.hashCode().toLong() and 0xFFFFFFFFL
    if (seed == 0L) seed = 88172645L
    fun next(): Int {
        seed = seed xor (seed shl 13 and 0xFFFFFFFFL)
        seed = seed xor (seed shr 17)
        seed = seed xor (seed shl 5 and 0xFFFFFFFFL)
        return (seed and 0x7FFFFFFF).toInt()
    }
    fun finder(cx: Int, cy: Int) = cx in 0..2 && cy in 0..2
    for (y in 0 until n) for (x in 0 until n) {
        val inFinder = (x < 3 && y < 3) || (x >= n - 3 && y < 3) || (x < 3 && y >= n - 3)
        if (inFinder) continue
        if (next() % 100 < 46) {
            val r = Rect(Offset(x0 + x * cell, y0 + y * cell), Size(cell - px(1.5f), cell - px(1.5f)))
            if (next() % 5 == 0) drawRect(accent, r.topLeft, r.size) else drawRect(Ink.copy(alpha = 0.75f), r.topLeft, r.size)
        }
    }
    fun finderAt(fx: Int, fy: Int) {
        val o = Offset(x0 + fx * cell, y0 + fy * cell)
        drawRect(accent, o, Size(cell * 3 - px(1.5f), cell * 3 - px(1.5f)), style = Stroke(px(2f)))
        drawRect(accent, Offset(o.x + cell, o.y + cell), Size(cell - px(1.5f), cell - px(1.5f)))
    }
    finderAt(0, 0); finderAt(n - 3, 0); finderAt(0, n - 3)
    // Rate of "scan lines" to the right.
    val lx = x0 + cell * n + px(26f)
    for (i in 0 until 4) {
        drawLine(Ink.copy(alpha = 0.25f), Offset(lx, y0 + px(14f) + i * px(24f)), Offset(size.width - px(8f) - i * px(30f), y0 + px(14f) + i * px(24f)), strokeWidth = px(1.5f), cap = StrokeCap.Square)
    }
}

// ---------------------------------------------------------- IFSC: a bank front

private fun DrawScope.ifscArt(accent: Color) {
    val w = px(170f); val x0 = px(2f)
    val base = size.height - px(12f)
    val colTop = px(46f)
    val roof = Path().apply {
        moveTo(x0, px(40f)); lineTo(x0 + w / 2, px(8f)); lineTo(x0 + w, px(40f)); close()
    }
    drawPath(roof, accent, style = Stroke(px(1.6f)))
    hatchIn(roof, Rect(Offset(x0, px(8f)), Size(w, px(32f))), accent.copy(alpha = 0.5f), px(5f), px(1f), true)
    drawRect(accent, Offset(x0 - px(6f), px(42f)), Size(w + px(12f), px(4f)))
    val n = 5
    val cw = px(18f)
    val gap = (w - cw * n) / (n - 1)
    for (i in 0 until n) {
        val r = Rect(Offset(x0 + i * (cw + gap), colTop + px(6f)), Size(cw, base - colTop - px(14f)))
        drawRect(Ink.copy(alpha = 0.6f), r.topLeft, r.size, style = Stroke(px(1f)))
        hatchIn(rectPath(r), r, Ink.copy(alpha = 0.22f), px(4f), px(1f), false)
    }
    drawRect(Ink.copy(alpha = 0.8f), Offset(x0 - px(6f), base - px(8f)), Size(w + px(12f), px(4f)))
    drawRect(Ink.copy(alpha = 0.5f), Offset(x0 - px(14f), base - px(4f)), Size(w + px(28f), px(4f)))
}

// ---------------------------------------------------------- PIN: a postmark with wavy cancellation lines

private fun DrawScope.pinArt(accent: Color) {
    val cx = px(62f); val cy = size.height / 2f; val r = px(48f)
    drawCircle(accent, r, Offset(cx, cy), style = Stroke(px(1.6f)))
    drawCircle(accent, r - px(8f), Offset(cx, cy), style = Stroke(px(1f)))
    val inner = ovalPath(cx, cy, r - px(8f), r - px(8f))
    hatchIn(inner, Rect(Offset(cx - r, cy - r), Size(r * 2, r * 2)), accent.copy(alpha = 0.35f), px(5f), px(1f), true)
    drawLine(accent, Offset(cx - r + px(12f), cy), Offset(cx + r - px(12f), cy), strokeWidth = px(1.2f))
    for (i in 0 until 4) {
        val y = cy - px(27f) + i * px(18f)
        val path = Path()
        var x = cx + r + px(10f)
        path.moveTo(x, y)
        val end = size.width - px(4f)
        while (x < end) {
            x += px(3f)
            path.lineTo(x, y + sin((x - cx) / px(7f)) * px(4f))
        }
        drawPath(path, Ink.copy(alpha = 0.5f), style = Stroke(px(1.3f)))
    }
}

// ---------------------------------------------------------- NUM: one bar per digit

private fun DrawScope.numArt(p: InspectPayload, accent: Color) {
    val digits = p.big.ifBlank { p.title }.filter { it.isDigit() }.take(16).ifBlank { "0" }
    val n = digits.length
    val gap = px(5f)
    val bw = min(px(26f), (size.width - gap * (n - 1)) / n)
    val maxD = digits.maxOf { it }
    val base = size.height - px(6f)
    for ((i, ch) in digits.withIndex()) {
        val hgt = px(8f) + (ch - '0') / 9f * (size.height - px(20f))
        val r = Rect(Offset(i * (bw + gap), base - hgt), Size(bw, hgt))
        if (ch == maxD) drawRect(accent, r.topLeft, r.size)
        else {
            drawRect(Ink.copy(alpha = 0.55f), r.topLeft, r.size, style = Stroke(px(1f)))
            hatchIn(rectPath(r), r, Ink.copy(alpha = 0.22f), px(4f), px(1f), true)
        }
    }
    drawLine(Ink.copy(alpha = 0.4f), Offset(0f, base), Offset(size.width, base), strokeWidth = px(1f))
}

// ---------------------------------------------------------- DEFINE: the word as a sound wave

private fun DrawScope.defineArt(p: InspectPayload, accent: Color) {
    val word = p.big.ifBlank { p.title }.lowercase().filter { it.isLetter() }.ifBlank { "a" }
    val bars = 44
    val gap = px(3f)
    val bw = (size.width - gap * (bars - 1)) / bars
    val mid = size.height / 2f
    for (i in 0 until bars) {
        val c = word[i * word.length / bars]
        val envelope = sin(PI * (i + 0.5) / bars).toFloat()
        val v = ((c.code * 7 + i * 13) % 10) / 10f
        val h = px(4f) + (0.25f + 0.75f * v) * envelope * (size.height * 0.46f)
        val focus = abs(i - bars / 2) <= 1
        drawRect(
            if (focus) accent else Ink.copy(alpha = 0.55f),
            Offset(i * (bw + gap), mid - h), Size(bw, h * 2)
        )
    }
}

// ---------------------------------------------------------- DIMS: a blueprint

private fun DrawScope.arrowHead(tip: Offset, dx: Float, dy: Float, color: Color) {
    val s = px(6f)
    val nx = -dy; val ny = dx
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x - dx * s * 1.6f + nx * s * 0.55f, tip.y - dy * s * 1.6f + ny * s * 0.55f)
        lineTo(tip.x - dx * s * 1.6f - nx * s * 0.55f, tip.y - dy * s * 1.6f - ny * s * 0.55f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.dimLine(a: Offset, b: Offset, label: String, tm: TextMeasurer, color: Color, vertical: Boolean) {
    drawLine(color, a, b, strokeWidth = px(1f))
    val len = kotlin.math.sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y))
    if (len < px(1f)) return
    val ux = (b.x - a.x) / len; val uy = (b.y - a.y) / len
    arrowHead(a, -ux, -uy, color)
    arrowHead(b, ux, uy, color)
    val style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Ink, letterSpacing = 0.5.sp)
    val m = tm.measure(label, style)
    val mx = (a.x + b.x) / 2f; val my = (a.y + b.y) / 2f
    if (vertical) {
        rotate(-90f, pivot = Offset(mx, my)) {
            drawRect(Color.Black, Offset(mx - m.size.width / 2f - px(4f), my - m.size.height / 2f - px(1f)), Size(m.size.width + px(8f), m.size.height + px(2f)))
            drawText(tm, label, topLeft = Offset(mx - m.size.width / 2f, my - m.size.height / 2f), style = style)
        }
    } else {
        drawRect(Color.Black, Offset(mx - m.size.width / 2f - px(4f), my - m.size.height / 2f - px(1f)), Size(m.size.width + px(8f), m.size.height + px(2f)))
        drawText(tm, label, topLeft = Offset(mx - m.size.width / 2f, my - m.size.height / 2f), style = style)
    }
}

private fun DrawScope.dimsArt(p: InspectPayload, accent: Color, tm: TextMeasurer) {
    val nums = p.nums
    if (nums.size < 2) return
    val w = nums[0]; val h = nums[1]
    val d = nums.getOrNull(2)
    val unit = p.unit
    fun lab(v: Double) = Fmt.adaptive(v) + (if (unit.isBlank()) "" else " $unit")

    // Grid paper.
    val minor = px(12f)
    var gx = 0f
    while (gx <= size.width) {
        val major = ((gx / minor).toInt() % 5) == 0
        drawLine(Color(0xFF7AA2FF).copy(alpha = if (major) 0.16f else 0.07f), Offset(gx, 0f), Offset(gx, size.height), strokeWidth = px(1f))
        gx += minor
    }
    var gy = 0f
    while (gy <= size.height) {
        val major = ((gy / minor).toInt() % 5) == 0
        drawLine(Color(0xFF7AA2FF).copy(alpha = if (major) 0.16f else 0.07f), Offset(0f, gy), Offset(size.width, gy), strokeWidth = px(1f))
        gy += minor
    }

    // Fit the front face; leave room for the dimension lines and the oblique depth.
    val padL = px(34f); val padR = px(48f); val padT = px(22f); val padB = px(40f)
    val depthK = if (d != null) 0.32f else 0f
    val availW = size.width - padL - padR
    val availH = size.height - padT - padB
    // front face fw x fh, depth offset = depthK * min(fw, fh)
    val ratio = (h / w).toFloat()
    var fw = availW / (1f + depthK * min(1f, ratio))
    var fh = fw * ratio
    val off = { depthK * min(fw, fh) }
    if (fh + off() > availH) {
        fh = availH / (1f + depthK * min(1f, 1f / ratio.coerceAtLeast(0.01f)))
        fw = fh / ratio
    }
    fw = max(fw, px(20f)); fh = max(fh, px(12f))
    val o = off()
    val left = padL
    val top = padT + o
    val front = Rect(Offset(left, top), Size(fw, fh))
    val line = accent

    if (d != null) {
        val back = Rect(Offset(left + o, top - o), Size(fw, fh))
        drawRect(line.copy(alpha = 0.55f), back.topLeft, back.size, style = Stroke(px(1f)))
        drawLine(line.copy(alpha = 0.55f), front.topLeft, back.topLeft, strokeWidth = px(1f))
        drawLine(line.copy(alpha = 0.55f), front.topRight, back.topRight, strokeWidth = px(1f))
        drawLine(line.copy(alpha = 0.55f), front.bottomRight, back.bottomRight, strokeWidth = px(1f))
    }
    hatchIn(rectPath(front), front, line.copy(alpha = 0.18f), px(6f), px(1f), true)
    drawRect(line, front.topLeft, front.size, style = Stroke(px(2f)))
    if (d == null) {
        drawLine(line.copy(alpha = 0.6f), front.topLeft, front.bottomRight, strokeWidth = px(1f),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(px(5f), px(4f))))
    }

    val dim = Ink.copy(alpha = 0.85f)
    // Width: below the front face, with extension lines.
    val dy = front.bottom + px(18f)
    drawLine(Ink.copy(alpha = 0.4f), Offset(front.left, front.bottom + px(3f)), Offset(front.left, dy + px(5f)), strokeWidth = px(1f))
    drawLine(Ink.copy(alpha = 0.4f), Offset(front.right, front.bottom + px(3f)), Offset(front.right, dy + px(5f)), strokeWidth = px(1f))
    dimLine(Offset(front.left, dy), Offset(front.right, dy), lab(w), tm, dim, false)
    // Height: to the left of the front face.
    val dx = front.left - px(18f)
    drawLine(Ink.copy(alpha = 0.4f), Offset(front.left - px(3f), front.top), Offset(dx - px(5f), front.top), strokeWidth = px(1f))
    drawLine(Ink.copy(alpha = 0.4f), Offset(front.left - px(3f), front.bottom), Offset(dx - px(5f), front.bottom), strokeWidth = px(1f))
    dimLine(Offset(dx, front.top), Offset(dx, front.bottom), lab(h), tm, dim, true)
    // Depth: along the oblique edge, up and to the right.
    if (d != null) {
        val sx = front.right + px(14f); val sy = front.top - px(0f)
        dimLine(Offset(sx, sy), Offset(sx + o, sy - o), lab(d), tm, dim, false)
    }
    // Small title tag in the corner, like a drafting sheet.
    val tag = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 8.sp, color = Dim, letterSpacing = 1.2.sp)
    drawText(tm, if (d != null) "SCALE 1:1  ·  3D" else "SCALE 1:1  ·  2D", topLeft = Offset(size.width - px(110f), size.height - px(14f)), style = tag)
}

// ---------------------------------------------------------- WIKI: a photo in a cut frame with a hatched shadow

@Composable
private fun WikiArt(p: InspectPayload, accent: Color, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(null, p.imageUrl) {
        value = if (p.imageUrl.isBlank()) null else withContext(Dispatchers.IO) { CardImages.load(p.imageUrl, 720) }
    }
    val tm = rememberTextMeasurer()
    Box(modifier.fillMaxWidth().height(176.dp)) {
        // Offset hatched frame behind the photo.
        Canvas(Modifier.fillMaxSize()) {
            val shift = px(10f)
            val fw = size.width - px(40f)
            val fh = size.height - shift
            val r = Rect(Offset(shift, shift), Size(fw, fh))
            val clip = Path().apply {
                moveTo(r.left, r.top); lineTo(r.right - px(40f), r.top); lineTo(r.right, r.top + px(40f))
                lineTo(r.right, r.bottom); lineTo(r.left + px(40f), r.bottom); lineTo(r.left, r.bottom - px(40f)); close()
            }
            hatchIn(clip, r, accent.copy(alpha = 0.55f), px(5f), px(1.2f), true)
            drawPath(clip, accent, style = Stroke(px(1.2f)))
            val style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 9.sp, color = Dim, letterSpacing = 3.sp)
            val label = "WIKIPEDIA"
            val m = tm.measure(label, style)
            val lx = size.width - px(20f)
            val ly = size.height / 2f + m.size.width / 2f
            rotate(-90f, pivot = Offset(lx, ly)) { drawText(tm, label, topLeft = Offset(lx, ly), style = style) }
        }
        Box(
            Modifier.padding(end = 40.dp, bottom = 10.dp).fillMaxSize()
                .clip(CutCornerShape(topEnd = 40.dp, bottomStart = 40.dp))
                .background(Color(0xFF15171B))
        ) {
            val b = bmp
            if (b != null) {
                Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    val whole = Rect(Offset.Zero, size)
                    hatchIn(rectPath(whole), whole, Ink.copy(alpha = 0.08f), px(8f), px(1f), false)
                    val letter = p.title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "W"
                    val style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Black, fontSize = 72.sp, color = Ink.copy(alpha = 0.25f))
                    val m = tm.measure(letter, style)
                    drawText(tm, letter, topLeft = Offset((size.width - m.size.width) / 2f, (size.height - m.size.height) / 2f), style = style)
                }
            }
        }
    }
}
