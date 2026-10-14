package com.microtag.inspect

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
// CARD ART. Only the cards that can actually show something get a drawing:
//   colour   the colour and its complement, hatched
//   date     the months between now and the date, as proportional lines
//   time     the same instant on two clocks, aligned
//   dims     a blueprint of the sizes
//   upi      the real payment QR code
//   post     a postmark
//   wiki     the photo
// ==========================================================

private val Ink = Color(0xFFF6F6F6)
private val Dim = Color(0xFF6E737C)

private fun DrawScope.px(v: Float) = v * density

private fun DrawScope.hatchIn(
    clip: Path, bounds: Rect, color: Color, gap: Float, width: Float, rising: Boolean, phase: Float = 0f
) {
    clipPath(clip) {
        val h = bounds.height
        var x = bounds.left - h + phase
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

/** One line of text on the canvas that never wraps. */
private fun DrawScope.label(tm: TextMeasurer, text: String, at: Offset, style: TextStyle) {
    drawText(tm, text, topLeft = at, style = style, softWrap = false, maxLines = 1)
}

private fun style(sp: Int, weight: FontWeight, color: Color, spacing: Float = 0f) =
    TextStyle(fontFamily = Montserrat, fontWeight = weight, fontSize = sp.sp, color = color, letterSpacing = spacing.sp)

/** Does this domain draw anything? The card skips the art slot entirely when it does not. */
fun hasArt(domain: String): Boolean = domain in setOf("COLOUR", "DATE", "TIME", "DIMS", "UPI", "PIN", "WIKI")

@Composable
fun CardArt(p: InspectPayload, accent: Color, onCopy: (String) -> Unit, modifier: Modifier = Modifier) {
    when (p.domain) {
        "WIKI" -> WikiArt(p, accent, onCopy, modifier)
        "COLOUR" -> ColourArt(p, accent, onCopy, modifier)
        "UPI" -> UpiArt(p, onCopy, modifier)
        "DATE" -> DateArt(p, accent, modifier)
        "TIME" -> {
            val tm = rememberTextMeasurer()
            Canvas(modifier.fillMaxWidth().height(128.dp)) { timeArt(p, accent, tm) }
        }
        "DIMS" -> {
            val tm = rememberTextMeasurer()
            Canvas(modifier.fillMaxWidth().height(176.dp)) { dimsArt(p, accent, tm) }
        }
        "PIN" -> Canvas(modifier.fillMaxWidth().height(116.dp)) { pinArt(accent) }
        else -> Unit
    }
}

// ---------------------------------------------------------- COLOUR: two hatched circles and a leader to the complement

@Composable
private fun ColourArt(p: InspectPayload, accent: Color, onCopy: (String) -> Unit, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    val a = p.palette.getOrNull(0)?.let { Color(it) } ?: accent
    val b = p.palette.getOrNull(1)?.let { Color(it) } ?: Ink
    val comp = p.rows.firstOrNull { it.first == "Complement" }?.second.orEmpty()
    Box(modifier.fillMaxWidth().height(118.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val r = px(46f)
            val cy = size.height / 2f
            val ax = px(50f)
            val bx = ax + px(50f)
            val gap = px(4f)
            val ca = ovalPath(ax, cy, r, r)
            val cb = ovalPath(bx, cy, r, r)
            // Same direction, offset by half a gap: where the circles overlap the two sets of lines interleave.
            hatchIn(ca, Rect(Offset(ax - r, cy - r), Size(r * 2, r * 2)), a, gap, px(1.1f), true, 0f)
            hatchIn(cb, Rect(Offset(bx - r, cy - r), Size(r * 2, r * 2)), b, gap, px(1.1f), true, gap / 2f)
            drawCircle(a, r, Offset(ax, cy), style = Stroke(px(1.2f)))
            drawCircle(b, r, Offset(bx, cy), style = Stroke(px(1.2f)))

            if (comp.isNotBlank()) {
                val st = style(12, FontWeight.Bold, Ink, 1f)
                val m = tm.measure(comp, st, softWrap = false, maxLines = 1)
                val lx = size.width - px(30f)
                val py = size.height - px(8f)
                val ang = 32.0 * PI / 180.0
                val sx = bx + (r * cos(ang)).toFloat()
                val sy = cy + (r * sin(ang)).toFloat()
                drawLine(Ink, Offset(sx, sy), Offset(lx + m.size.height / 2f, py + px(2f)), strokeWidth = px(1f))
                drawCircle(Ink, px(2.5f), Offset(sx, sy))
                rotate(-90f, pivot = Offset(lx, py)) { label(tm, comp, Offset(lx, py), st) }
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

// ---------------------------------------------------------- DATE: the months in between, as proportional lines

@Composable
private fun DateArt(p: InspectPayload, accent: Color, modifier: Modifier) {
    val rows = remember(p.nums) {
        if (p.nums.size >= 2) Timespan.rows(p.nums[0].toLong(), p.nums[1].toLong()) else emptyList()
    }
    if (rows.isEmpty()) return
    val tm = rememberTextMeasurer()
    val rowH = 16.dp
    Canvas(modifier.fillMaxWidth().height(rowH * rows.size + 8.dp)) {
        val labelW = px(50f)
        val tail = px(40f)
        val maxLen = size.width - labelW - tail
        val rh = px(16f)
        for ((i, r) in rows.withIndex()) {
            val y = px(4f) + i * rh + rh / 2f
            val hot = r.first || r.last
            label(tm, r.label, Offset(0f, y - px(6f)), style(9, FontWeight.Bold, if (hot) accent else Dim, 1f))
            // The full length of the month, faint, then the part the span covers.
            drawLine(Ink.copy(alpha = 0.12f), Offset(labelW, y), Offset(labelW + maxLen, y), strokeWidth = px(1f))
            val x0 = labelW + maxLen * r.offset / r.capacity
            val x1 = x0 + maxLen * r.days / r.capacity
            drawLine(if (hot) accent else Ink.copy(alpha = 0.8f), Offset(x0, y), Offset(x1, y), strokeWidth = px(4f), cap = StrokeCap.Square)
            if (r.last) drawCircle(accent, px(4.5f), Offset(x1, y))
            label(tm, "${r.days}d", Offset(labelW + maxLen + px(10f), y - px(6f)), style(9, FontWeight.Medium, Dim))
        }
    }
}

// ---------------------------------------------------------- TIME: one instant on two clocks, aligned

private fun DrawScope.timeArt(p: InspectPayload, accent: Color, tm: TextMeasurer) {
    val src = p.nums.getOrNull(0)?.toInt() ?: 600
    val loc = p.nums.getOrNull(1)?.toInt() ?: 600
    val w = size.width
    val cx = w / 2f
    val hourW = w / 24f
    val barH = px(20f)
    val y1 = px(22f)
    val y2 = px(80f)

    fun bar(y: Float, minuteOfDay: Int) {
        val now = minuteOfDay / 60.0
        val first = Math.floor(now).toInt() - 13
        for (h in first..first + 26) {
            val x = cx + ((h - now) * hourW).toFloat()
            val hod = ((h % 24) + 24) % 24
            val left = max(0f, x)
            val right = min(w, x + hourW)
            if (right <= left) continue
            val night = hod < 6 || hod >= 21
            drawRect(Ink.copy(alpha = if (night) 0.08f else 0.24f), Offset(left, y), Size(right - left - px(1f), barH))
            if (hod == 0 && x in 0f..w) {
                drawLine(accent, Offset(x, y - px(4f)), Offset(x, y + barH + px(4f)), strokeWidth = px(2f))
            }
            if (hod % 3 == 0 && x + px(2f) in 0f..(w - px(14f))) {
                label(tm, hod.toString(), Offset(x + px(2f), y + barH + px(3f)), style(8, FontWeight.Bold, Dim))
            }
        }
    }
    bar(y1, src)
    bar(y2, loc)

    // Which clock is which, and the gap.
    label(tm, p.subtitle.removePrefix("From ").ifBlank { "FROM" }, Offset(0f, y1 - px(17f)), style(10, FontWeight.Bold, Ink.copy(alpha = 0.7f), 0.5f))
    label(tm, p.title, Offset(0f, y2 - px(17f)), style(10, FontWeight.Bold, Ink.copy(alpha = 0.7f), 0.5f))
    drawLine(accent, Offset(cx, y1 - px(6f)), Offset(cx, y2 + barH + px(6f)), strokeWidth = px(1.5f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(px(3f), px(3f))))
    drawCircle(accent, px(4f), Offset(cx, y1 + barH / 2f))
    drawCircle(accent, px(4f), Offset(cx, y2 + barH / 2f))
    val gap = Timespan.gapText(Timespan.gapMinutes(src, loc))
    val gm = tm.measure(gap, style(11, FontWeight.Black, accent, 0.5f), softWrap = false, maxLines = 1)
    val gy = (y1 + barH + y2) / 2f - gm.size.height / 2f + px(2f)
    drawRect(Color.Black, Offset(cx + px(8f), gy - px(1f)), Size(gm.size.width + px(8f), gm.size.height + px(2f)))
    label(tm, gap, Offset(cx + px(12f), gy), style(11, FontWeight.Black, accent, 0.5f))
}

// ---------------------------------------------------------- UPI: the real payment QR

@Composable
private fun UpiArt(p: InspectPayload, onCopy: (String) -> Unit, modifier: Modifier) {
    val qr = remember(p.url) { if (p.url.isBlank()) null else QrCode.encode(p.url) } ?: return
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.width(188.dp).height(188.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onCopy(p.copyValue) }
        ) {
            val quiet = 2
            val cell = size.width / (qr.size + quiet * 2)
            // A white tile with dark modules: the polarity scanners expect.
            drawRoundRect(Color.White, Offset.Zero, size, CornerRadius(px(10f)))
            for (y in 0 until qr.size) for (x in 0 until qr.size) {
                if (qr.isDark(x, y)) {
                    drawRect(
                        Color.Black,
                        Offset((x + quiet) * cell, (y + quiet) * cell),
                        Size(cell + 0.6f, cell + 0.6f)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------- PIN: a postmark with wavy cancellation lines

private fun DrawScope.pinArt(accent: Color) {
    val cx = px(62f); val cy = size.height / 2f; val r = px(46f)
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

private fun DrawScope.dimLine(a: Offset, b: Offset, text: String, tm: TextMeasurer, color: Color, vertical: Boolean) {
    drawLine(color, a, b, strokeWidth = px(1f))
    val len = kotlin.math.sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y))
    if (len < px(1f)) return
    val ux = (b.x - a.x) / len; val uy = (b.y - a.y) / len
    arrowHead(a, -ux, -uy, color)
    arrowHead(b, ux, uy, color)
    val st = style(10, FontWeight.Bold, Ink, 0.5f)
    val m = tm.measure(text, st, softWrap = false, maxLines = 1)
    val mx = (a.x + b.x) / 2f; val my = (a.y + b.y) / 2f
    if (vertical) {
        rotate(-90f, pivot = Offset(mx, my)) {
            drawRect(Color.Black, Offset(mx - m.size.width / 2f - px(4f), my - m.size.height / 2f - px(1f)), Size(m.size.width + px(8f), m.size.height + px(2f)))
            label(tm, text, Offset(mx - m.size.width / 2f, my - m.size.height / 2f), st)
        }
    } else {
        drawRect(Color.Black, Offset(mx - m.size.width / 2f - px(4f), my - m.size.height / 2f - px(1f)), Size(m.size.width + px(8f), m.size.height + px(2f)))
        label(tm, text, Offset(mx - m.size.width / 2f, my - m.size.height / 2f), st)
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
    val gridColor = Color(0xFF7AA2FF)
    var gx = 0f
    while (gx <= size.width) {
        val major = ((gx / minor).toInt() % 5) == 0
        drawLine(gridColor.copy(alpha = if (major) 0.16f else 0.07f), Offset(gx, 0f), Offset(gx, size.height), strokeWidth = px(1f))
        gx += minor
    }
    var gy = 0f
    while (gy <= size.height) {
        val major = ((gy / minor).toInt() % 5) == 0
        drawLine(gridColor.copy(alpha = if (major) 0.16f else 0.07f), Offset(0f, gy), Offset(size.width, gy), strokeWidth = px(1f))
        gy += minor
    }

    val padL = px(34f); val padR = px(48f); val padT = px(22f); val padB = px(40f)
    val depthK = if (d != null) 0.32f else 0f
    val availW = size.width - padL - padR
    val availH = size.height - padT - padB
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
    val dy = front.bottom + px(18f)
    drawLine(Ink.copy(alpha = 0.4f), Offset(front.left, front.bottom + px(3f)), Offset(front.left, dy + px(5f)), strokeWidth = px(1f))
    drawLine(Ink.copy(alpha = 0.4f), Offset(front.right, front.bottom + px(3f)), Offset(front.right, dy + px(5f)), strokeWidth = px(1f))
    dimLine(Offset(front.left, dy), Offset(front.right, dy), lab(w), tm, dim, false)
    val dx = front.left - px(18f)
    drawLine(Ink.copy(alpha = 0.4f), Offset(front.left - px(3f), front.top), Offset(dx - px(5f), front.top), strokeWidth = px(1f))
    drawLine(Ink.copy(alpha = 0.4f), Offset(front.left - px(3f), front.bottom), Offset(dx - px(5f), front.bottom), strokeWidth = px(1f))
    dimLine(Offset(dx, front.top), Offset(dx, front.bottom), lab(h), tm, dim, true)
    if (d != null) {
        val sx = front.right + px(14f); val sy = front.top
        dimLine(Offset(sx, sy), Offset(sx + o, sy - o), lab(d), tm, dim, false)
    }
    label(tm, if (d != null) "DRAWN TO PROPORTION · 3D" else "DRAWN TO PROPORTION · 2D",
        Offset(size.width - px(168f), size.height - px(14f)), style(8, FontWeight.Bold, Dim, 1.2f))
}

// ---------------------------------------------------------- WIKI: the photo at its own proportions, the name XOR-ed across it

private fun cutPoly(r: Rect, c: Float): Path = Path().apply {
    moveTo(r.left, r.top); lineTo(r.right - c, r.top); lineTo(r.right, r.top + c)
    lineTo(r.right, r.bottom); lineTo(r.left + c, r.bottom); lineTo(r.left, r.bottom - c); close()
}

@Composable
private fun WikiArt(p: InspectPayload, accent: Color, onCopy: (String) -> Unit, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(null, p.imageUrl) {
        value = if (p.imageUrl.isBlank()) null else withContext(Dispatchers.IO) { CardImages.load(p.imageUrl, 720) }
    }
    val tm = rememberTextMeasurer()
    // The photo keeps its own shape (very wide or very tall ones are eased toward the middle).
    val ratio = bmp?.let { it.width.toFloat() / it.height.toFloat() }?.coerceIn(0.62f, 1.75f) ?: 1.3f
    val title = p.title
    Box(modifier.fillMaxWidth().padding(end = 12.dp, bottom = 12.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                // The hatched shadow behind the photo, offset down and to the right.
                .drawWithContent {
                    val s = 12.dp.toPx()
                    val r = Rect(Offset(s, s), size)
                    val poly = cutPoly(r, 34.dp.toPx())
                    clipPath(poly) {
                        var x = r.left - r.height
                        while (x < r.right + r.height) {
                            drawLine(accent.copy(alpha = 0.6f), Offset(x, r.bottom), Offset(x + r.height, r.top), strokeWidth = 1.2.dp.toPx())
                            x += 5.dp.toPx()
                        }
                    }
                    drawPath(poly, accent.copy(alpha = 0.9f), style = Stroke(1.2.dp.toPx()))
                    drawContent()
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onCopy(title) }
        ) {
            // Photo and the name share one offscreen layer so the XOR-style blend only sees the photo.
            Box(
                Modifier.fillMaxSize()
                    .clip(CutCornerShape(topEnd = 34.dp, bottomStart = 34.dp))
                    .background(Color(0xFF15171B))
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        // The name, in two stacked lines, drawn with Difference: it inverts whatever photo is under it.
                        val words = title.split(' ').filter { it.isNotBlank() }
                        val lines = if (words.size <= 1) listOf(title) else {
                            val half = (words.size + 1) / 2
                            listOf(words.take(half).joinToString(" "), words.drop(half).joinToString(" "))
                        }
                        val maxW = size.width - 28.dp.toPx()
                        var sp = 54f
                        var layouts = lines.map { tm.measure(it, style(sp.toInt(), FontWeight.Normal, Color.White).copy(fontFamily = DisplayFont), softWrap = false, maxLines = 1) }
                        while (sp > 20f && layouts.maxOf { it.size.width } > maxW) {
                            sp *= 0.93f
                            layouts = lines.map { tm.measure(it, style(sp.toInt(), FontWeight.Normal, Color.White).copy(fontFamily = DisplayFont), softWrap = false, maxLines = 1) }
                        }
                        val total = layouts.sumOf { it.size.height }
                        var y = size.height - total - 12.dp.toPx()
                        for (l in layouts) {
                            drawText(l, color = Color.White, topLeft = Offset(14.dp.toPx(), y), blendMode = BlendMode.Difference)
                            y += l.size.height
                        }
                    }
            ) {
                val b = bmp
                if (b != null) {
                    Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Canvas(Modifier.fillMaxSize()) {
                        val whole = Rect(Offset.Zero, size)
                        hatchIn(rectPath(whole), whole, Ink.copy(alpha = 0.10f), px(8f), px(1f), false)
                    }
                }
            }
        }
    }
}
