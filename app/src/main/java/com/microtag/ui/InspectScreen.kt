package com.microtag.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.R
import com.microtag.inspect.InspectCard
import com.microtag.inspect.InspectPayload
import com.microtag.inspect.InspectSamples

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

// CRITICAL FIX: Restored static UI variables so ChipPreview doesn't trigger mass IO resolution
private class InspectTip(
    val icon: Int,
    val title: String,
    val blurb: String,
    val input: String,
    val output: String,
    val chipIcon: Int? = null,
    val glyph: String? = null,
    val swatch: Color? = null,
    val backwards: String? = null,
    val sample: (() -> InspectPayload)? = null
)

private class TipBlockSpec(val label: String, val tips: List<InspectTip>)

private val Blocks = listOf(
    TipBlockSpec(
        "Numbers",
        listOf(
            InspectTip(R.drawable.ic_inspect_calc, "Calculations", "Select a sum, percentages included. The card shows the working.", "567 + 15%", "652.05", glyph = "=", sample = InspectSamples::calc),
            InspectTip(R.drawable.ic_inspect_convert, "Units", "Select a measurement and see it in the other system.", "51 lb", "23.13 kg", glyph = "⇄", sample = InspectSamples::convert),
            InspectTip(R.drawable.ic_equals, "Numbers", "Select a big number for its words, and now and then a fact about it.", "3,100,000,000", "3.1 Arab", glyph = "=", backwards = "Select the words (three lakh fifty thousand) to get the digits.", sample = InspectSamples::number),
            InspectTip(R.drawable.ic_inspect_convert, "Dimensions", "Select sizes like 21 x 29.7 cm or 1920x1080. Standard sizes are named.", "21 x 29.7 cm", "A4 paper", glyph = "×", backwards = "Select a name (A3, us letter, 1080p, queen bed, credit card) to get its size.", sample = InspectSamples::dimensions)
        )
    ),
    TipBlockSpec(
        "Money and India",
        listOf(
            InspectTip(R.drawable.ic_inspect_convert, "Currency", "Select an amount and see it in your own currency.", "\$38", "3681", glyph = "₹", sample = InspectSamples::money),
            InspectTip(R.drawable.ic_upi_rupee, "UPI IDs", "Copy a UPI ID to see which app it belongs to, scan it as a QR, or jump straight to paying.", "ryan@oksbi", "Google Pay", glyph = "₹", sample = InspectSamples::upi),
            InspectTip(R.drawable.ic_upi_rupee, "Bank codes", "Select an IFSC code for the bank, branch and what it supports.", "SBIN0001234", "SBI", glyph = "#", sample = InspectSamples::bank),
            InspectTip(R.drawable.ic_inspect_convert, "PIN codes", "Select a six-digit PIN for the place it covers.", "400001", "Mumbai", glyph = "#", sample = InspectSamples::post)
        )
    ),
    TipBlockSpec(
        "Time",
        listOf(
            InspectTip(R.drawable.ic_calendar_countdown, "Time zones", "Select a time in any zone and see it on your clock, with the gap and the day change.", "3 PM EST", "1:30 am", chipIcon = R.drawable.ic_calendar_countdown, sample = InspectSamples::time),
            InspectTip(R.drawable.ic_calendar_countdown, "Dates", "Select a date and see how far away it is. The card draws the months in between.", "09 November.", "in 29d", chipIcon = R.drawable.ic_calendar_countdown, sample = InspectSamples::timespan)
        )
    ),
    TipBlockSpec(
        "Words and web",
        listOf(
            InspectTip(R.drawable.ic_inspect_define, "Definitions", "Select a word. Tap the chip for the full entry, with every meaning.", "raster", "scan lines", chipIcon = R.drawable.ic_inspect_define, sample = InspectSamples::define),
            InspectTip(R.drawable.ic_inspect_define, "Wikipedia", "Select a person, place or thing, even a long title, for the summary and a photo when there is one.", "Leonhard Euler", "Leonhard", chipIcon = R.drawable.ic_inspect_define, sample = InspectSamples::wiki),
            InspectTip(R.drawable.ic_inspect_convert, "Links", "Copy or select a link. Trackers are stripped, shortened links can be unrolled, and a check looks at the site.", "https://bit.ly/3xYzAb", "Short link", glyph = "!", sample = InspectSamples::link)
        )
    ),
    TipBlockSpec(
        "Colour",
        listOf(
            InspectTip(R.drawable.ic_inspect_colour, "Colours", "Select a colour code for the colour, its name and its complement.", "#CCFF00", "#CCFF00", swatch = Color(0xFFCCFF00), backwards = "Select a name (teal, forest green) to get the hex code.", sample = InspectSamples::colour)
        )
    )
)

private val TipSurface = Color(0xFF15161A)

private fun tipShape(index: Int, count: Int): Shape {
    val big = CornerSize(22.dp)
    val small = CornerSize(6.dp)
    val top = if (index == 0) big else small
    val bottom = if (index == count - 1) big else small
    return RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
}

@Composable
fun InspectScreen(onBack: () -> Unit) {
    SubScreen(onBack, spacing = 10.dp) {
        Spacer(Modifier.height(12.dp))
        FitText(
            AnnotatedString("Inspect"),
            TextStyle(
                fontFamily = Montserrat, fontWeight = FontWeight.Black, fontSize = 56.sp,
                lineHeight = 60.sp, letterSpacing = (-1.5).sp, color = TagColors.White
            ),
            Modifier.fillMaxWidth()
        )
        Text(
            "Select text anywhere. The answer shows up in the status bar. Tap it for the full card.",
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
        Spacer(Modifier.height(6.dp))
        Blocks.forEach { TipBlock(it) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TipBlock(block: TipBlockSpec) {
    Text(
        block.label.uppercase(),
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        style = TextStyle(
            fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            letterSpacing = 1.2.sp, color = TagColors.Lime
        )
    )
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        block.tips.forEachIndexed { i, tip -> TipRow(tip, tipShape(i, block.tips.size)) }
    }
}

@Composable
private fun TipRow(tip: InspectTip, shape: Shape) {
    var open by remember { mutableStateOf(false) }
    val view = LocalView.current
    
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TipSurface)
            .clickable { view.tick(Tick.Press); open = !open }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(TagColors.White),
                contentAlignment = Alignment.Center
            ) { TagIcon(tip.icon, TagColors.Black, size = 22.dp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    tip.title,
                    style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TagColors.White)
                )
                Text(
                    tip.blurb,
                    style = TextStyle(
                        fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                        lineHeight = 18.sp, color = TagColors.Outline
                    )
                )
            }
            TagIcon(R.drawable.ic_chevron_right, TagColors.White, Modifier.rotate(if (open) 90f else 0f), 24.dp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TagColors.Lime)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    tip.input, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = TagColors.OnLime)
                )
            }
            TagIcon(R.drawable.ic_chevron_right, TagColors.Outline, size = 20.dp)
            ChipPreview(tip)
        }
        if (tip.backwards != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .border(1.dp, TagColors.Outline, CircleShape)
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        "↔ BACKWARDS",
                        style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 1.1.sp, color = TagColors.White)
                    )
                }
                Text(
                    tip.backwards,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, color = TagColors.Outline)
                )
            }
        }
        
        AnimatedVisibility(visible = open) {
            Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                // CRITICAL FIX: Data resolution is safely isolated inside if(open) again
                if (tip.sample != null) {
                    val sample = remember(tip) { tip.sample.invoke() }
                    InspectCard(sample)
                } else {
                    Text(
                        "Nothing to show offline.",
                        style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = TagColors.Outline)
                    )
                }
            }
        }
    }
}

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
            else -> Box(Modifier.size(8.dp).clip(CircleShape).background(TagColors.White))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            tip.output, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TagColors.White)
        )
    }
}
