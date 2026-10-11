package com.microtag.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.R

/** Back arrow row shared by the secondary screens. */
@Composable
fun BackBar(onBack: () -> Unit) {
    val view = LocalView.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable { view.tick(Tick.Press); onBack() },
            contentAlignment = Alignment.Center
        ) { TagIcon(R.drawable.ic_chevron_right, TagColors.White, Modifier.rotate(180f), 28.dp) }
    }
}

private class InspectTip(
    val icon: Int,
    val title: String,
    val blurb: String,
    val input: String,
    val output: String,
    val chipIcon: Int? = null,
    val glyph: String? = null,
    val swatch: Color? = null
)

private val Tips = listOf(
    InspectTip(
        R.drawable.ic_inspect_calc, "Calculations", "Select a sum and the answer lands in the status bar.",
        input = "345*73", output = "25185", glyph = "="
    ),
    InspectTip(
        R.drawable.ic_inspect_calc, "Timespan", "Select a date and see how far away it is.",
        input = "09 November.", output = "in 29d", chipIcon = R.drawable.ic_calendar_countdown
    ),
    InspectTip(
        R.drawable.ic_inspect_convert, "Conversions", "Select an amount and see it in your own currency or unit.",
        input = "\$38", output = "3681", glyph = "₹"
    ),
    InspectTip(
        R.drawable.ic_inspect_colour, "Colour swatch", "Select a colour code and preview the colour right in the chip.",
        input = "#CCFF00", output = "#CCFF00", swatch = Color(0xFFCCFF00)
    ),
    InspectTip(
        R.drawable.ic_inspect_define, "Definitions", "Select a word. Tap the chip for the full entry.",
        input = "dream", output = "sweven", chipIcon = R.drawable.ic_inspect_define
    )
)

@Composable
fun InspectScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(TagColors.Black)
            .statusBarsPadding()
    ) {
        BackBar(onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FitText(
                AnnotatedString("Inspect"),
                TextStyle(
                    fontFamily = Montserrat, fontWeight = FontWeight.Black, fontSize = 56.sp,
                    lineHeight = 60.sp, letterSpacing = (-1.5).sp, color = TagColors.White
                ),
                Modifier.fillMaxWidth()
            )
            Text(
                "Select text anywhere. The answer shows up in the status bar.",
                style = TextStyle(
                    fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 16.sp,
                    lineHeight = 22.sp, color = TagColors.Outline
                )
            )
            Box(
                Modifier
                    .clip(CircleShape)
                    .border(2.dp, TagColors.Lime, CircleShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    "ALWAYS ON",
                    style = TextStyle(
                        fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        letterSpacing = 1.sp, color = TagColors.Lime
                    )
                )
            }
            Spacer(Modifier.height(4.dp))
            Tips.forEach { TipCard(it) }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TipCard(tip: InspectTip) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(TagShapes.Rect)
            .border(2.dp, TagColors.Outline, TagShapes.Rect)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(TagColors.White),
                contentAlignment = Alignment.Center
            ) { TagIcon(tip.icon, TagColors.Black, size = 28.dp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tip.title,
                    style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TagColors.White)
                )
                Text(
                    tip.blurb,
                    style = TextStyle(
                        fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                        lineHeight = 18.sp, color = TagColors.Outline
                    )
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // The selection, highlighted the way the system highlights selected text.
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TagColors.Lime)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    tip.input, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = TagColors.OnLime)
                )
            }
            TagIcon(R.drawable.ic_chevron_right, TagColors.Outline, size = 20.dp)
            ChipPreview(tip)
        }
    }
}

/** A stand-in for the status bar chip: black pill, hairline outline, icon and text. */
@Composable
private fun ChipPreview(tip: InspectTip) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(TagColors.Black)
            .border(2.dp, TagColors.Outline, CircleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            tip.swatch != null -> Box(Modifier.size(16.dp).clip(CircleShape).background(tip.swatch))
            tip.chipIcon != null -> TagIcon(tip.chipIcon, TagColors.White, size = 18.dp)
            tip.glyph != null -> Text(
                tip.glyph,
                style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TagColors.White)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            tip.output, maxLines = 1,
            style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TagColors.White)
        )
    }
}
