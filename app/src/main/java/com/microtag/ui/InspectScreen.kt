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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.microtag.inspect.InspectCard
import com.microtag.inspect.InspectPayload
import com.microtag.inspect.InspectSamples

private class InspectTip(
    val icon: Int,
    val title: String,
    val blurb: String,
    val input: String,
    val output: String,
    val chipIcon: Int? = null,
    val glyph: String? = null,
    val swatch: Color? = null,
    /** The expanded card this tip opens, shown for real so the showcase does the cards justice. */
    val sample: (() -> InspectPayload)? = null
)

private val Tips = listOf(
    InspectTip(
        R.drawable.ic_inspect_calc, "Calculations", "Select a sum and the answer lands in the status bar.",
        input = "345*73", output = "25185", glyph = "=", sample = InspectSamples::calc
    ),
    InspectTip(
        R.drawable.ic_inspect_calc, "Timespan", "Select a date and see how far away it is. The card draws the months in between.",
        input = "09 November.", output = "in 29d", chipIcon = R.drawable.ic_calendar_countdown, sample = InspectSamples::timespan
    ),
    InspectTip(
        R.drawable.ic_calendar_countdown, "Time zones", "Select a time in any zone and see it on your clock, with the gap drawn out.",
        input = "5 PM GMT", output = "10:30 pm", chipIcon = R.drawable.ic_calendar_countdown, sample = InspectSamples::time
    ),
    InspectTip(
        R.drawable.ic_inspect_convert, "Units", "Select a measurement and see it in the other system.",
        input = "51 lb", output = "23.13 kg", glyph = "⇄", sample = InspectSamples::convert
    ),
    InspectTip(
        R.drawable.ic_inspect_convert, "Currency", "Select an amount and see it in your own currency.",
        input = "\$38", output = "3681", glyph = "₹", sample = InspectSamples::money
    ),
    InspectTip(
        R.drawable.ic_inspect_colour, "Colour swatch", "Select a colour code and see the colour, its name and its complement.",
        input = "#CCFF00", output = "#CCFF00", swatch = Color(0xFFCCFF00), sample = InspectSamples::colour
    ),
    InspectTip(
        R.drawable.ic_inspect_colour, "Colour names", "Works backwards too: select a colour name and get its hex code.",
        input = "teal", output = "#008080", swatch = Color(0xFF008080), sample = InspectSamples::colour
    ),
    InspectTip(
        R.drawable.ic_inspect_define, "Definitions", "Select a word. Tap the chip for the full entry, with every meaning.",
        input = "raster", output = "scan lines", chipIcon = R.drawable.ic_inspect_define, sample = InspectSamples::define
    ),
    InspectTip(
        R.drawable.ic_inspect_define, "Wikipedia", "Select a person, place or thing and get the summary, with a photo when there is one.",
        input = "Alan Turing", output = "Alan", chipIcon = R.drawable.ic_inspect_define, sample = InspectSamples::wiki
    ),
    InspectTip(
        R.drawable.ic_inspect_convert, "Links", "Copy or select a link. Trackers are stripped, shortened links can be unrolled, and a check looks at the site.",
        input = "bit.ly/3xYzAb", output = "Short link", glyph = "!", sample = InspectSamples::link
    ),
    InspectTip(
        R.drawable.ic_upi_rupee, "UPI IDs", "Copy a UPI ID to see which app it belongs to, scan it as a QR, or jump straight to paying.",
        input = "ryan@oksbi", output = "Google Pay", glyph = "₹", sample = InspectSamples::upi
    ),
    InspectTip(
        R.drawable.ic_upi_rupee, "Bank codes", "Select an IFSC code for the bank, branch and what it supports.",
        input = "SBIN0001234", output = "SBI", glyph = "#", sample = InspectSamples::bank
    ),
    InspectTip(
        R.drawable.ic_inspect_convert, "PIN codes", "Select a six-digit PIN for the place it covers.",
        input = "400001", output = "Mumbai", glyph = "#", sample = InspectSamples::post
    ),
    InspectTip(
        R.drawable.ic_equals, "Numbers", "Select a big number for its words, or the words for the number: \"three lakh fifty thousand\".",
        input = "345464000", output = "34.5 cr", glyph = "=", sample = InspectSamples::number
    ),
    InspectTip(
        R.drawable.ic_inspect_convert, "Dimensions", "Select sizes like 120 x 80 cm or 1920x1080 for area, volume, ratio, and a blueprint.",
        input = "120 x 80 x 40 cm", output = "384 L", glyph = "×", sample = InspectSamples::dimensions
    )
)

@Composable
fun InspectScreen(onBack: () -> Unit) {
    SubScreen(onBack) {
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
        Tips.forEach { TipCard(it) }
    }
}

@Composable
private fun TipCard(tip: InspectTip) {
    var open by remember { mutableStateOf(false) }
    val view = LocalView.current
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
        if (tip.sample != null) {
            // The same card the chip opens, so Define, Wikipedia and the rest can be seen properly.
            Box(
                Modifier
                    .clip(CircleShape)
                    .border(1.5.dp, TagColors.Lime, CircleShape)
                    .clickable { view.tick(Tick.Press); open = !open }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    if (open) "HIDE CARD" else "SHOW CARD",
                    style = TextStyle(
                        fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                        letterSpacing = 1.4.sp, color = TagColors.Lime
                    )
                )
            }
            if (open) {
                val sample = remember(tip) { tip.sample.invoke() }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { InspectCard(sample) }
            }
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
