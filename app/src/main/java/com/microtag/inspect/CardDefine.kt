package com.microtag.inspect

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.ui.TagColors

// ==========================================================
// DEFINE. The senses scroll, grouped by part of speech, numbered, each example under its own sense.
// The headword stays pinned underneath as the footer, with the pronunciation and the sources beside it.
// Only the header strip drags the card away, so the list can scroll freely.
// ==========================================================

private class SenseUi(val text: String, val example: String)
private class Group(val pos: String, val senses: List<SenseUi>)

private fun groupRows(rows: List<Pair<String, String>>): List<Group> {
    val groups = ArrayList<Group>()
    var pos = ""
    var list = ArrayList<SenseUi>()
    fun flush() { if (list.isNotEmpty()) groups.add(Group(pos, list)); list = ArrayList() }
    var i = 0
    while (i < rows.size) {
        val (k, v) = rows[i]
        if (k == "Example") { i++; continue }
        if (k.isNotBlank()) { flush(); pos = k }
        val ex = rows.getOrNull(i + 1)?.takeIf { it.first == "Example" }?.second.orEmpty()
        list.add(SenseUi(v, ex))
        i++
    }
    flush()
    return groups
}

/** Fades the content out at whichever edge there is more to scroll to. */
private fun Modifier.fadeEdges(state: ScrollState, fade: Dp = 20.dp): Modifier =
    this.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val f = fade.toPx()
            if (state.value > 0) {
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = f),
                    size = Size(size.width, f), blendMode = BlendMode.DstIn
                )
            }
            if (state.value < state.maxValue) {
                drawRect(
                    Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - f, endY = size.height),
                    topLeft = Offset(0f, size.height - f), size = Size(size.width, f), blendMode = BlendMode.DstIn
                )
            }
        }

@Composable
internal fun DefineCard(c: CardCtx, modifier: Modifier) {
    val p = c.p
    val groups = remember(p.rows) { groupRows(p.rows) }
    val scroll = rememberScrollState()
    val heroColour = if (c.flash == p.copyValue) c.accent else TagColors.White

    CardFrame(p, modifier, c.drag, Surface.Black, 520.dp, swallowDrag = false) {
        // Only the header strip drags.
        Box(c.drag) { CardHeader(c, arrowHere = true) }
        Spacer(Modifier.height(8.dp))

        Column(Modifier.weight(1f, fill = false).fadeEdges(scroll).verticalScroll(scroll)) {
            Spacer(Modifier.height(6.dp))
            if (groups.isEmpty() && p.fullContent.isNotBlank()) {
                Text(p.fullContent, style = sans(14f, FontWeight.Medium, TagColors.White).copy(lineHeight = 20.sp))
            }
            groups.forEachIndexed { gi, g ->
                if (gi > 0) Spacer(Modifier.height(16.dp))
                Text(g.pos.uppercase(), style = mono(10f, c.accent, 1.4f, FontWeight.Bold), maxLines = 1)
                Spacer(Modifier.height(6.dp))
                g.senses.forEachIndexed { si, s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).plainClick { c.onCopy(s.text) }) {
                        Text(
                            "${si + 1}", style = mono(11f, Paper.Mute, 0f, FontWeight.Bold),
                            modifier = Modifier.width(22.dp).padding(top = 3.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(s.text, style = sans(14.5f, FontWeight.Medium, TagColors.White).copy(lineHeight = 20.sp))
                            if (s.example.isNotBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    s.example,
                                    style = sans(13f, FontWeight.Light, Paper.Ink.copy(alpha = 0.78f)).copy(lineHeight = 18.sp, fontStyle = FontStyle.Italic)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // The pinned footer: the word itself, then how it sounds and where the entries came from.
        Spacer(Modifier.height(8.dp))
        HeroValue(
            p.heroText, TextStyle(fontFamily = DisplayFont, fontSize = 72.sp, color = heroColour),
            minSize = 28f, splitSuffix = false, onClick = { c.onCopy(p.heroText) }
        )
        if (p.subText.isNotBlank() || p.caption.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (p.subText.isNotBlank()) {
                    Text(p.subText, style = sans(16f, FontWeight.Medium, Paper.Ink.copy(alpha = 0.85f)), maxLines = 1, modifier = Modifier.plainClick { c.onCopy(p.subText) })
                    Spacer(Modifier.width(10.dp))
                }
                if (p.caption.isNotBlank()) Caption(p.caption, Modifier.weight(1f, fill = false))
            }
        }
    }
}
