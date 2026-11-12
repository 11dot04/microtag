package com.microtag.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Small print: the micrographics the app is dressed with. Tiny mono labels, dotted leaders, index numbers, tick
 * scales, bracket tags. They carry a little information each (which step, how many, where on a scale) and never
 * compete with the content. Colours come from the palette.
 */

private fun microStyle(color: Color, bold: Boolean = false, size: Int = 11) = TextStyle(
    fontFamily = TagFonts.data, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
    fontSize = size.sp, letterSpacing = 1.2.sp, color = color
)

/** A dotted line that fills the width it is given. */
@Composable
fun DottedLeader(modifier: Modifier = Modifier, color: Color = TagColors.Dim) {
    Canvas(modifier.height(2.dp)) {
        drawLine(
            color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.5.dp.toPx(),
            cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 5.dp.toPx()), 0f)
        )
    }
}

/** "[ TEXT ]" in tiny mono. */
@Composable
fun BracketTag(text: String, modifier: Modifier = Modifier, color: Color = TagColors.Outline) {
    Text("[ ${text.uppercase()} ]", modifier, style = microStyle(color))
}

/**
 * A section header with an index number and a dotted leader: "01  SAMPLE ........ 3 SEEN".
 * The number is in the accent text colour; the leader runs to an optional note on the right.
 */
@Composable
fun StepHeader(index: String, title: String, note: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(index, style = microStyle(TagColors.AccentText, bold = true, size = 12))
        Text(title.uppercase(), Modifier.padding(start = 10.dp), style = microStyle(TagColors.White, bold = true, size = 12))
        DottedLeader(Modifier.weight(1f).padding(horizontal = 10.dp))
        if (note != null) Text(note.uppercase(), style = microStyle(TagColors.Outline))
    }
}

/** A row of ticks like a ruler: [count] steps, every [majorEvery]th longer. */
@Composable
fun TickScale(modifier: Modifier = Modifier, count: Int = 24, majorEvery: Int = 6, color: Color = TagColors.Dim) {
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val w = 1.dp.toPx()
        for (i in 0..count) {
            val x = size.width * i / count
            val h = if (i % majorEvery == 0) size.height else size.height * 0.5f
            drawLine(color, Offset(x, size.height), Offset(x, size.height - h), w)
        }
    }
}

/** A label with a value pushed to the right, both tiny: "LENGTH ....... 12". */
@Composable
fun SpecLine(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = TagColors.White) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        Text(label.uppercase(), style = microStyle(TagColors.Outline))
        DottedLeader(Modifier.weight(1f).padding(horizontal = 8.dp))
        Text(value.uppercase(), style = microStyle(valueColor, bold = true))
    }
}
