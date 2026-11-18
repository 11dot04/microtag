package com.microtag.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The divider between the main screen's numeral and the tiles, for palettes that have a highlight colour (Graphite's
 * orange). Graphite's oddity: the strip is a product label. Everything it could say is already on the numeral, so it
 * says nothing useful, quietly: a line of regulatory-style text and a small conformity-style glyph on the right.
 * Static on purpose; no scrolling.
 */
@Composable
fun InfoStrip(modifier: Modifier = Modifier) {
    val colour = TagColors.Highlight ?: return
    val ink = TagColors.Black
    Row(
        modifier.fillMaxWidth().height(30.dp).background(colour).padding(horizontal = 20.dp).clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "REV 11.04  //  NO USER-SERVICEABLE PARTS",
            maxLines = 1,
            softWrap = false,
            style = TextStyle(
                fontFamily = TagFonts.data, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                letterSpacing = 1.2.sp, color = ink
            )
        )
        // A conformity-style mark: an open arc, then three bars. Reads as a stamp, claims nothing.
        Canvas(Modifier.size(width = 26.dp, height = 14.dp)) {
            val w = 1.8.dp.toPx()
            val h = size.height
            val stroke = Stroke(w, cap = StrokeCap.Butt)
            drawArc(ink, startAngle = 50f, sweepAngle = 260f, useCenter = false, topLeft = Offset(w / 2f, w / 2f), size = Size(h - w, h - w), style = stroke)
            val x0 = h + 3.dp.toPx()
            val x1 = size.width
            listOf(0.12f, 0.5f, 0.88f).forEach { f ->
                drawLine(ink, Offset(x0, h * f), Offset(if (f == 0.5f) x1 - 3.dp.toPx() else x1, h * f), w)
            }
            drawLine(ink, Offset(x0, h * 0.12f), Offset(x0, h * 0.88f), w)
        }
    }
}
