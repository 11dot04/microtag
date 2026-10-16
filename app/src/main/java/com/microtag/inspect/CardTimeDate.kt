package com.microtag.inspect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.microtag.ui.FitText
import com.microtag.ui.TagColors
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// ==========================================================
// TIME and DATE. Typographic: numerals carry the card, arrows are hairlines, captions are mono.
// ==========================================================

private fun part(p: InspectPayload, i: Int) = p.parts.getOrNull(i).orEmpty()

// ---------------------------------------------------------------- TIME

@Composable
internal fun TimeCard(c: CardCtx, modifier: Modifier) {
    val p = c.p
    val fromTime = part(p, 0).ifBlank { p.subText.removePrefix("From ") }
    val fromZone = part(p, 1)
    val fromDate = part(p, 2)
    val toTime = part(p, 3).ifBlank { p.heroText }
    val toZone = part(p, 4)
    val toDate = part(p, 5)
    val gap = part(p, 6)
    val dayNote = part(p, 7)
    val thatIs = p.rows.firstOrNull { it.first == "That is" }?.second.orEmpty()
    val note = p.rows.firstOrNull { it.first == "Note" }?.second.orEmpty()
    val heroColour = if (c.flash == p.copyValue) c.accent else TagColors.White

    CardFrame(p, modifier, c.drag, Surface.Black, 520.dp) {
        CardHeader(c, arrowHere = true)
        Spacer(Modifier.height(10.dp))
        val tm = rememberTextMeasurer()
        Canvas(Modifier.fillMaxWidth().height(100.dp)) { timeBars(p, c.accent, tm, fromZone, toZone) }
        Spacer(Modifier.height(10.dp))

        // The time as typed.
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(fromTime, style = sans(30f, FontWeight.Black, TagColors.White).tight(0.95f), maxLines = 1)
            if (fromZone.isNotBlank()) Text(fromZone, style = sans(13f, FontWeight.Medium, Paper.Ink.copy(alpha = 0.7f)), maxLines = 1, modifier = Modifier.padding(bottom = 3.dp))
            Spacer(Modifier.weight(1f))
            if (fromDate.isNotBlank()) Caption(fromDate, Modifier.padding(bottom = 4.dp))
        }

        // A hairline arrow, labelled with the gap between the two clocks.
        Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.width(18.dp).height(40.dp)) {
                val x = size.width / 2f
                drawLine(Paper.Ink.copy(alpha = 0.7f), Offset(x, dp(4f)), Offset(x, size.height - dp(3f)), strokeWidth = dp(1f))
                val h = Path().apply {
                    moveTo(x - dp(4f), size.height - dp(9f)); lineTo(x, size.height - dp(2f)); lineTo(x + dp(4f), size.height - dp(9f))
                }
                drawPath(h, Paper.Ink.copy(alpha = 0.7f), style = Stroke(dp(1f)))
            }
            Spacer(Modifier.width(6.dp))
            if (gap.isNotBlank()) Text(gap, style = mono(12f, c.accent, 0.8f, FontWeight.Bold), maxLines = 1)
        }

        // The answer: time big, zone small.
        val hero = buildAnnotatedString {
            append(toTime)
            if (toZone.isNotBlank()) withStyle(SpanStyle(fontSize = 0.34f.em)) { append(" $toZone") }
        }
        FitText(
            hero,
            style = TextStyle(fontFamily = DisplayFont, fontSize = 84.sp, color = heroColour).tight(0.94f),
            modifier = Modifier.fillMaxWidth().plainClick { c.onCopy(p.copyValue) },
            minSize = 30.sp
        )
        if (toDate.isNotBlank() || dayNote.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Caption(listOf(toDate, dayNote).filter { it.isNotBlank() }.joinToString(" · "), color = Paper.Ink.copy(alpha = 0.8f))
        }
        if (thatIs.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Caption(thatIs)
        }
        if (note.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Floating(note, c.accent)
        }
    }
}

/** Two 24-hour bars aligned on the same instant, with a NOW marker. */
private fun DrawScope.timeBars(p: InspectPayload, accent: Color, tm: TextMeasurer, srcZone: String, locZone: String) {
    val src = p.nums.getOrNull(0)?.toInt() ?: 600
    val loc = p.nums.getOrNull(1)?.toInt() ?: 600
    val nowDelta = p.nums.getOrNull(3)
    val w = size.width
    val cx = w / 2f
    val hourW = w / 24f
    val barH = dp(18f)
    val y1 = dp(22f)
    val y2 = dp(66f)
    val nowX = nowDelta?.let { cx - (it / 60.0 * hourW).toFloat() }

    fun bar(y: Float, minuteOfDay: Int) {
        val now = minuteOfDay / 60.0
        val first = floor(now).toInt() - 13
        for (h in first..first + 26) {
            val x = cx + ((h - now) * hourW).toFloat()
            val hod = ((h % 24) + 24) % 24
            val left = max(0f, x)
            val right = min(w, x + hourW)
            if (right <= left) continue
            val night = hod < 6 || hod >= 21
            drawRect(Paper.Ink.copy(alpha = if (night) 0.08f else 0.24f), Offset(left, y), Size(right - left - dp(1f), barH))
            if (hod == 0 && x in 0f..w) {
                drawLine(accent, Offset(x, y - dp(3f)), Offset(x, y + barH + dp(3f)), strokeWidth = dp(2f))
            }
            // Hour numbers stay clear of the dashed line and the NOW marker, so nothing overprints.
            val clearOfCentre = kotlin.math.abs(x - cx) > dp(16f)
            val clearOfNow = nowX == null || kotlin.math.abs(x - nowX) > dp(16f)
            if (hod % 3 == 0 && x + dp(2f) in 0f..(w - dp(14f)) && clearOfCentre && clearOfNow) {
                label(tm, hod.toString(), Offset(x + dp(2f), y + barH + dp(2f)), mono(8f, Paper.Mute, 0.2f, FontWeight.Bold))
            }
        }
    }
    bar(y1, src)
    bar(y2, loc)

    if (srcZone.isNotBlank()) label(tm, srcZone, Offset(0f, y1 - dp(15f)), mono(9f, Paper.Ink.copy(alpha = 0.75f), 1f, FontWeight.Bold))
    val locName = p.parts.getOrNull(4).orEmpty().ifBlank { "LOCAL" }
    label(tm, locName, Offset(0f, y2 - dp(15f)), mono(9f, Paper.Ink.copy(alpha = 0.75f), 1f, FontWeight.Bold))

    drawLine(accent, Offset(cx, y1 - dp(4f)), Offset(cx, y2 + barH + dp(4f)), strokeWidth = dp(1.2f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dp(3f), dp(3f))))
    drawCircle(accent, dp(3.5f), Offset(cx, y1 + barH / 2f))
    drawCircle(accent, dp(3.5f), Offset(cx, y2 + barH / 2f))

    if (nowX != null && nowX in dp(20f)..(w - dp(20f))) {
        drawLine(Color.White, Offset(nowX, y1 - dp(4f)), Offset(nowX, y2 + barH + dp(4f)), strokeWidth = dp(1f))
        label(tm, "NOW", Offset(nowX - dp(10f), y2 + barH + dp(5f)), mono(8f, Color.White, 0.8f, FontWeight.Bold))
    }
}

// ---------------------------------------------------------------- DATE

@Composable
internal fun DateCard(c: CardCtx, modifier: Modifier) {
    val p = c.p
    val heroColour = if (c.flash == p.copyValue) c.accent else TagColors.White
    CardFrame(p, modifier, c.drag, Surface.Black, 520.dp) {
        CardHeader(c, arrowHere = true)
        Spacer(Modifier.height(14.dp))
        if (p.parts.size >= 6) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                DateStamp(part(p, 0), part(p, 1), part(p, 2), "", c.accent, Alignment.Start)
                HairArrow(part(p, 6), Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 2.dp))
                DateStamp(part(p, 3), part(p, 4), part(p, 5), part(p, 7), c.accent, Alignment.End)
            }
            Spacer(Modifier.height(14.dp))
        }
        DateGantt(p, c.accent)
        Spacer(Modifier.height(12.dp))
        HeroValue(p.heroText, TextStyle(fontFamily = DisplayFont, fontSize = 84.sp, color = heroColour), minSize = 28f, onClick = { c.onCopy(p.copyValue) })
        if (p.caption.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Caption(p.caption)
        }
        p.rows.firstOrNull { it.first == "Note" }?.let {
            Spacer(Modifier.height(10.dp))
            Floating(it.second, c.accent)
        }
    }
}

/** "10.15" with the month in the accent and the day in white, then a tiny weekday (and year) under it. */
@Composable
private fun DateStamp(month: String, day: String, weekday: String, year: String, accent: Color, side: Alignment.Horizontal) {
    Column(horizontalAlignment = side) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = accent)) { append(month) }
                withStyle(SpanStyle(color = Paper.Mute)) { append(".") }
                withStyle(SpanStyle(color = TagColors.White)) { append(day) }
            },
            style = sans(34f, FontWeight.Black, TagColors.White, -0.5f).tight(0.95f), maxLines = 1
        )
        Caption(listOf(weekday, year).filter { it.isNotBlank() }.joinToString(" "))
    }
}

/** A hairline arrow pointing right, with a label above it. */
@Composable
private fun HairArrow(label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (label.isNotBlank()) {
            Text(
                label, style = sans(10.5f, FontWeight.Medium, Paper.Ink.copy(alpha = 0.8f)).copy(lineHeight = 13.sp, textAlign = TextAlign.Center),
                maxLines = 3
            )
        }
        Spacer(Modifier.height(5.dp))
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val y = size.height / 2f
            drawLine(Paper.Ink.copy(alpha = 0.7f), Offset(0f, y), Offset(size.width, y), strokeWidth = dp(1f))
            val h = Path().apply {
                moveTo(size.width - dp(7f), y - dp(4f)); lineTo(size.width, y); lineTo(size.width - dp(7f), y + dp(4f))
            }
            drawPath(h, Paper.Ink.copy(alpha = 0.7f), style = Stroke(dp(1f)))
        }
    }
}

/** The months in between, as proportional lines. */
@Composable
private fun DateGantt(p: InspectPayload, accent: Color) {
    val rows = remember(p.nums) {
        if (p.nums.size >= 2) Timespan.rows(p.nums[0].toLong(), p.nums[1].toLong()) else emptyList()
    }
    if (rows.isEmpty()) return
    val tm = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(16.dp * rows.size + 8.dp)) {
        val labelW = dp(56f)
        val tail = dp(40f)
        val maxLen = size.width - labelW - tail
        val rh = dp(16f)
        for ((i, r) in rows.withIndex()) {
            val y = dp(4f) + i * rh + rh / 2f
            val hot = r.first || r.last
            label(tm, r.label, Offset(0f, y - dp(6f)), mono(9f, if (hot) accent else Paper.Mute, 0.6f, FontWeight.Bold))
            drawLine(Paper.Ink.copy(alpha = 0.12f), Offset(labelW, y), Offset(labelW + maxLen, y), strokeWidth = dp(1f))
            val x0 = labelW + maxLen * r.offset / r.capacity
            val x1 = x0 + maxLen * r.days / r.capacity
            drawLine(if (hot) accent else Paper.Ink.copy(alpha = 0.8f), Offset(x0, y), Offset(x1, y), strokeWidth = dp(4f), cap = StrokeCap.Square)
            if (r.last) drawCircle(accent, dp(4.5f), Offset(x1, y))
            label(tm, "${r.days}d", Offset(labelW + maxLen + dp(10f), y - dp(6f)), mono(9f, Paper.Mute, 0.4f))
        }
    }
}
