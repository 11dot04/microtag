package com.microtag.inspect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.R
import com.microtag.ui.FitText
import com.microtag.ui.TagColors
import kotlin.math.cos
import kotlin.math.sin

// ==========================================================
// POST. A black stamp with a postmark struck over its corner.
//   top      what and where, then a table with right-aligned labels
//   postmark a 24-spoke sunburst in a double ring, half off the card. It is the chakra and a cancellation mark at once;
//            one bold spoke per post office on the PIN (all of them bold from 24 up)
//   squares  one small square per office: yellow for head and sub offices, green for branch offices
//   PIN      in the style font, with the digits' meaning underneath: zone, sorting district, delivery office
// ==========================================================

/** Drop style.ttf into res/font and this resolves. */
internal val PostFont = FontFamily(Font(R.font.style))

private val Yellow = Color(0xFFD9B84A)
private val Green = Color(0xFF5F9F66)

@Composable
internal fun PostCard(c: CardCtx, modifier: Modifier) {
    val p = c.p
    val pin = p.heroText
    val rows = remember(p.rows) { p.rows.filter { it.second.isNotBlank() } }
    val total = p.nums.getOrNull(0)?.toInt() ?: 0
    val main = p.nums.getOrNull(1)?.toInt() ?: 0
    val branch = p.nums.getOrNull(2)?.toInt() ?: 0
    val flashing = c.flash == pin

    CardFrame(p, modifier, c.drag, Surface.Black, 400.dp) {
        CardHeader(c, arrowHere = true)
        Spacer(Modifier.height(12.dp))
        if (p.subText.isNotBlank()) {
            Text(
                p.subText, maxLines = 2,
                style = sans(22f, FontWeight.Bold, Paper.Ink).copy(lineHeight = 27.sp),
                modifier = Modifier.plainClick { c.onCopy(p.subText) }
            )
            Spacer(Modifier.height(14.dp))
        }

        // Labels on the right, values on the left of the gutter.
        rows.take(5).forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).plainClick { c.onCopy(v) }, verticalAlignment = Alignment.Top) {
                Text(
                    (if (k.equals("Post offices", true)) "Offices" else k).uppercase(),
                    modifier = Modifier.width(78.dp).padding(top = 3.dp),
                    style = sans(10f, FontWeight.Bold, Paper.Mute, 0.8f).copy(textAlign = TextAlign.End), maxLines = 1, softWrap = false
                )
                Spacer(Modifier.width(14.dp))
                Text(v, modifier = Modifier.weight(1f), style = sans(14f, FontWeight.Medium, TagColors.White).copy(lineHeight = 19.sp), maxLines = 4)
            }
        }
        Spacer(Modifier.height(14.dp))

        // The postmark behind, the squares and the PIN in front.
        Box(Modifier.fillMaxWidth().height(184.dp)) {
            Canvas(Modifier.fillMaxSize()) { drawSunburst(total) }
            Column(Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.End) {
                OfficeSquares(main, branch)
                Spacer(Modifier.height(10.dp))
                PinNumber(pin, if (flashing) c.accent else Color.White, Modifier.width(206.dp).plainClick { c.onCopy(pin) })
                if (pin.length == 6) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "ZONE ${pin[0]} · SORT ${pin.substring(0, 3)} · OFFICE ${pin.substring(3)}",
                        style = mono(8.5f, Paper.Mute, 0.8f), maxLines = 1, softWrap = false
                    )
                }
            }
        }
    }
}

/** The PIN in the style font; the zone digit takes the card's accent, the rest is white. */
@Composable
private fun PinNumber(pin: String, colour: Color, modifier: Modifier) {
    val zone = cardAccentForPin()
    val text: AnnotatedString = remember(pin, colour) {
        buildAnnotatedString {
            if (pin.length == 6) {
                withStyle(SpanStyle(color = if (colour == Color.White) zone else colour)) { append(pin[0]) }
                withStyle(SpanStyle(color = colour)) { append(pin.substring(1, 3)); append(" "); append(pin.substring(3)) }
            } else withStyle(SpanStyle(color = colour)) { append(pin) }
        }
    }
    FitText(
        text, TextStyle(fontFamily = PostFont, fontSize = 40.sp, color = colour).tight(1f),
        modifier, minSize = 18.sp, contentAlignment = Alignment.TopEnd
    )
}

private fun cardAccentForPin(): Color = Color(0xFFC98B77)

/** One square per office, in rows of up to twelve: yellow for head and sub offices, green for branch offices. */
@Composable
private fun OfficeSquares(main: Int, branch: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.End) {
        squareRow(main, Yellow)
        squareRow(branch, Green)
    }
}

@Composable
private fun squareRow(count: Int, colour: Color) {
    if (count <= 0) return
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(count.coerceAtMost(12)) { Box(Modifier.size(8.dp).background(colour)) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSunburst(offices: Int) {
    val centre = Offset(dp(26f), size.height - dp(4f))
    val r = dp(104f)
    val line = Paper.Line
    drawCircle(line.copy(alpha = 0.55f), r, centre, style = Stroke(dp(1.6f)))
    drawCircle(line.copy(alpha = 0.45f), r - dp(9f), centre, style = Stroke(dp(1f)))
    val n = offices.coerceIn(0, 24)
    // Spread the bold spokes round the dial: spoke k*24/n for each office k.
    val bold = if (offices >= 24) (0 until 24).toSet() else (0 until n).map { it * 24 / n }.toSet()
    for (i in 0 until 24) {
        val a = Math.toRadians(i * 15.0 - 90.0)
        val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
        val isBold = i in bold
        drawLine(
            line.copy(alpha = if (isBold) 0.9f else 0.4f),
            centre + dir * dp(11f), centre + dir * (r - dp(15f)),
            strokeWidth = if (isBold) dp(2.6f) else dp(0.9f)
        )
    }
    drawCircle(line.copy(alpha = 0.8f), dp(4f), centre)
}
