package com.microtag.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The divider between the main screen's numeral and the tiles, for palettes that have a highlight colour (Graphite's
 * orange). It is a ticker, so it works as a divider and says something: the running total, today so far, the peak
 * hour, and whether TAG is live. Scrolls like a marquee; stands still when motion is off.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InfoStrip(count: Int, hourly: IntArray, paused: Boolean, modifier: Modifier = Modifier) {
    val colour = TagColors.Highlight ?: return
    val today = hourly.sum()
    val peak = hourly.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.index
    val items = buildList {
        add("$count INTERCEPTED")
        add("TODAY $today")
        if (peak != null) add("PEAK %02d:00".format(peak))
        add(if (paused) "TAG PAUSED" else "TAG LIVE")
    }
    // Repeated so the line is always longer than the screen and the marquee always has somewhere to go.
    val line = List(4) { items.joinToString("   //   ") }.joinToString("   //   ")
    Box(
        modifier.fillMaxWidth().height(30.dp).background(colour),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            line,
            maxLines = 1,
            softWrap = false,
            overflow = if (TagMotion.enabled) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .then(if (TagMotion.enabled) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 0, repeatDelayMillis = 0, velocity = 32.dp) else Modifier),
            style = TextStyle(
                fontFamily = TagFonts.data, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                letterSpacing = 1.4.sp, color = TagColors.Black
            )
        )
    }
}
