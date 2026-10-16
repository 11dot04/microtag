package com.microtag.inspect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.ui.Montserrat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ==========================================================
// CARD ART for the standard cards. Colour, time, date and wiki draw their own art in their own files.
//   dims   a blueprint of the sizes
//   upi    the real payment QR code, left-aligned
//   post   a postmark
// ==========================================================

private val Ink = Paper.Ink
private val Dim = Paper.Mute

private fun style(sp: Int, weight: FontWeight, color: Color, spacing: Float = 0f) =
    TextStyle(fontFamily = Montserrat, fontWeight = weight, fontSize = sp.sp, letterSpacing = spacing.sp, color = color)

/** Does this domain draw anything? The card skips the art slot entirely when it does not. */
fun hasArt(domain: String): Boolean = domain in setOf("DIMS", "UPI", "PIN")

@Composable
fun CardArt(p: InspectPayload, accent: Color, onCopy: (String) -> Unit, modifier: Modifier = Modifier) {
    when (p.domain) {
        "UPI" -> UpiArt(p, onCopy, modifier)
        "DIMS" -> {
            val tm = rememberTextMeasurer()
            Canvas(modifier.fillMaxWidth().height(176.dp)) { dimsArt(p, accent, tm) }
        }
        "PIN" -> Canvas(modifier.fillMaxWidth().height(116.dp)) { pinArt(accent) }
        else -> Unit
    }
}

// ---------------------------------------------------------- UPI: the real payment QR

@Composable
private fun UpiArt(p: InspectPayload, onCopy: (String) -> Unit, modifier: Modifier) {
    val qr = remember(p.url) { if (p.url.isBlank()) null else QrCode.encode(p.url) } ?: return
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Canvas(
            Modifier.width(204.dp).height(204.dp)
                .plainClick { onCopy(p.copyValue) }
        ) {
            val quiet = 2
            val cell = size.width / (qr.size + quiet * 2)
            // A white tile with dark modules: the polarity scanners expect.
            drawRoundRect(Color.White, Offset.Zero, size, CornerRadius(dp(10f)))
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
    val cx = dp(62f); val cy = size.height / 2f; val r = dp(46f)
    drawCircle(accent, r, Offset(cx, cy), style = Stroke(dp(1.6f)))
    drawCircle(accent, r - dp(8f), Offset(cx, cy), style = Stroke(dp(1f)))
    val inner = ovalPath(cx, cy, r - dp(8f), r - dp(8f))
    hatchIn(inner, Rect(Offset(cx - r, cy - r), Size(r * 2, r * 2)), accent.copy(alpha = 0.35f), dp(5f), dp(1f), true)
    drawLine(accent, Offset(cx - r + dp(12f), cy), Offset(cx + r - dp(12f), cy), strokeWidth = dp(1.2f))
    for (i in 0 until 4) {
        val y = cy - dp(27f) + i * dp(18f)
        val path = Path()
        var x = cx + r + dp(10f)
        path.moveTo(x, y)
        val end = size.width - dp(4f)
        while (x < end) {
            x += dp(3f)
            path.lineTo(x, y + sin((x - cx) / dp(7f)) * dp(4f))
        }
        drawPath(path, Ink.copy(alpha = 0.5f), style = Stroke(dp(1.3f)))
    }
}

// ---------------------------------------------------------- DIMS: a blueprint

private fun DrawScope.arrowHead(tip: Offset, dx: Float, dy: Float, color: Color) {
    val s = dp(6f)
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
    drawLine(color, a, b, strokeWidth = dp(1f))
    val len = kotlin.math.sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y))
    if (len < dp(1f)) return
    val ux = (b.x - a.x) / len; val uy = (b.y - a.y) / len
    arrowHead(a, -ux, -uy, color)
    arrowHead(b, ux, uy, color)
    val st = style(10, FontWeight.Bold, Ink, 0.5f)
    val m = tm.measure(text, st, softWrap = false, maxLines = 1)
    val mx = (a.x + b.x) / 2f; val my = (a.y + b.y) / 2f
    if (vertical) {
        rotate(-90f, pivot = Offset(mx, my)) {
            drawRect(Color.Black, Offset(mx - m.size.width / 2f - dp(4f), my - m.size.height / 2f - dp(1f)), Size(m.size.width + dp(8f), m.size.height + dp(2f)))
            label(tm, text, Offset(mx - m.size.width / 2f, my - m.size.height / 2f), st)
        }
    } else {
        drawRect(Color.Black, Offset(mx - m.size.width / 2f - dp(4f), my - m.size.height / 2f - dp(1f)), Size(m.size.width + dp(8f), m.size.height + dp(2f)))
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
    val minor = dp(12f)
    val gridColor = accent
    var gx = 0f
    while (gx <= size.width) {
        val major = ((gx / minor).toInt() % 5) == 0
        drawLine(gridColor.copy(alpha = if (major) 0.16f else 0.07f), Offset(gx, 0f), Offset(gx, size.height), strokeWidth = dp(1f))
        gx += minor
    }
    var gy = 0f
    while (gy <= size.height) {
        val major = ((gy / minor).toInt() % 5) == 0
        drawLine(gridColor.copy(alpha = if (major) 0.16f else 0.07f), Offset(0f, gy), Offset(size.width, gy), strokeWidth = dp(1f))
        gy += minor
    }

    val padL = dp(34f); val padR = dp(48f); val padT = dp(22f); val padB = dp(40f)
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
    fw = max(fw, dp(20f)); fh = max(fh, dp(12f))
    val o = off()
    val left = padL
    val top = padT + o
    val front = Rect(Offset(left, top), Size(fw, fh))
    val line = accent

    if (d != null) {
        val back = Rect(Offset(left + o, top - o), Size(fw, fh))
        drawRect(line.copy(alpha = 0.55f), back.topLeft, back.size, style = Stroke(dp(1f)))
        drawLine(line.copy(alpha = 0.55f), front.topLeft, back.topLeft, strokeWidth = dp(1f))
        drawLine(line.copy(alpha = 0.55f), front.topRight, back.topRight, strokeWidth = dp(1f))
        drawLine(line.copy(alpha = 0.55f), front.bottomRight, back.bottomRight, strokeWidth = dp(1f))
    }
    hatchIn(rectPath(front), front, line.copy(alpha = 0.18f), dp(6f), dp(1f), true)
    drawRect(line, front.topLeft, front.size, style = Stroke(dp(2f)))
    if (d == null) {
        drawLine(line.copy(alpha = 0.6f), front.topLeft, front.bottomRight, strokeWidth = dp(1f),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dp(5f), dp(4f))))
    }

    val dim = Ink.copy(alpha = 0.85f)
    val dy = front.bottom + dp(18f)
    drawLine(Ink.copy(alpha = 0.4f), Offset(front.left, front.bottom + dp(3f)), Offset(front.left, dy + dp(5f)), strokeWidth = dp(1f))
    drawLine(Ink.copy(alpha = 0.4f), Offset(front.right, front.bottom + dp(3f)), Offset(front.right, dy + dp(5f)), strokeWidth = dp(1f))
    dimLine(Offset(front.left, dy), Offset(front.right, dy), lab(w), tm, dim, false)
    val dx = front.left - dp(18f)
    drawLine(Ink.copy(alpha = 0.4f), Offset(front.left - dp(3f), front.top), Offset(dx - dp(5f), front.top), strokeWidth = dp(1f))
    drawLine(Ink.copy(alpha = 0.4f), Offset(front.left - dp(3f), front.bottom), Offset(dx - dp(5f), front.bottom), strokeWidth = dp(1f))
    dimLine(Offset(dx, front.top), Offset(dx, front.bottom), lab(h), tm, dim, true)
    if (d != null) {
        val sx = front.right + dp(14f); val sy = front.top
        dimLine(Offset(sx, sy), Offset(sx + o, sy - o), lab(d), tm, dim, false)
    }
    label(tm, if (d != null) "DRAWN TO PROPORTION · 3D" else "DRAWN TO PROPORTION · 2D",
        Offset(size.width - dp(168f), size.height - dp(14f)), style(8, FontWeight.Bold, Dim, 1.2f))
}

