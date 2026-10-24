package com.microtag.ui

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.R
import com.microtag.inspect.PillPrefs
import com.microtag.inspect.PriorityLogic
import com.microtag.inspect.PriorityStore

/**
 * Inspect priority: when a piece of text could be read two ways and the scores are close, the one higher in this list wins.
 * Moving a row by hand locks it against the rows it passed. Picking "or read it as..." on a card quietly moves things too.
 */
@Composable
fun PriorityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    remember { PillPrefs.attach(context) }
    var rev by remember { mutableIntStateOf(0) }
    val state = remember(rev) { PriorityStore.state }

    SubScreen(onBack, spacing = 10.dp) {
        Spacer(Modifier.height(12.dp))
        FitText(
            AnnotatedString("Priority"),
            TextStyle(
                fontFamily = TagFonts.display, fontWeight = FontWeight.Black, fontSize = 56.sp,
                lineHeight = 60.sp, letterSpacing = (-1.5).sp, color = TagColors.White,
                fontSynthesis = TagFonts.NoSynthesis
            ),
            Modifier.fillMaxWidth()
        )
        Text(
            "Some text could be two things: a number that is also a word, a name that is also a food. When it is a close call, the one higher up wins. A clear match, like a unit or a UPI id, always wins on its own.",
            style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, color = TagColors.Outline)
        )
        Text(
            "Choosing “or read it as…” under a card nudges this list by itself after a couple of times. A row you move by hand stays where you put it.",
            style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, color = TagColors.Outline)
        )
        Spacer(Modifier.height(6.dp))

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            state.order.forEachIndexed { i, kind ->
                val pinned = state.locks.any { it.startsWith("$kind>") || it.endsWith(">$kind") }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(connectedShape(i, state.order.size))
                        .background(TagColors.Surface)
                        .padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${i + 1}".padStart(2, '0'),
                        style = TextStyle(fontFamily = TagFonts.mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TagColors.Outline)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        PriorityLogic.label(kind), modifier = Modifier.weight(1f),
                        style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TagColors.White)
                    )
                    if (pinned) {
                        Text(
                            "SET",
                            style = TextStyle(fontFamily = TagFonts.mono, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, color = TagColors.AccentText)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    // The two arrows are one connected pair: pressing one opens all of its corners out.
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Nudge(up = true, enabled = i > 0, left = true) { view.tick(Tick.Press); PriorityStore.move(kind, -1); rev++ }
                        Nudge(up = false, enabled = i < state.order.lastIndex, left = false) { view.tick(Tick.Press); PriorityStore.move(kind, 1); rev++ }
                    }
                }
            }
        }

        Box(
            Modifier
                .clip(CircleShape)
                .border(1.5.dp, TagColors.Outline, CircleShape)
                .clickable { view.tick(Tick.Press); PriorityStore.reset(); rev++ }
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                "Back to the default order",
                style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TagColors.White)
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Nudge(up: Boolean, enabled: Boolean, left: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val t = pressMorph(source)
    val shape = if (left) PressCornerShape(22.dp, 6.dp, 6.dp, 22.dp, t) else PressCornerShape(6.dp, 22.dp, 22.dp, 6.dp, t)
    Box(
        Modifier
            .size(44.dp)
            .clip(shape)
            .background(lerp(TagColors.Surface, TagColors.White, 0.08f))
            .alpha(if (enabled) 1f else 0.25f)
            .then(if (enabled) Modifier.clickable(interactionSource = source, indication = null, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        TagIcon(R.drawable.ic_chevron_right, TagColors.White, Modifier.rotate(if (up) -90f else 90f), 22.dp)
    }
}
