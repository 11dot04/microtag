package com.microtag.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.microtag.inspect.InspectCard
import com.microtag.inspect.InspectPayload
import com.microtag.inspect.InspectResult
import com.microtag.inspect.PillConfig
import com.microtag.inspect.PillPrefs
import com.microtag.inspect.InspectSamples

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

/** What a tip shows. The pill preview and the card both come from the one [InspectResult] this produces. */
private class InspectTip(
    val icon: Int,
    val title: String,
    val blurb: String,
    /** The sample key: the text that gets "selected" ([InspectSamples.input] turns a few keys into dated or local text). */
    val key: String,
    /** Set when the card also works the other way round; shown as a small label on the tip, not as a tip of its own. */
    val backwards: String? = null,
    /** Which [PillConfig] option the pill picker edits (null: nothing to pick). */
    val pill: String? = null,
    /** True when the sample calls the network. Those show an offline sample until the row is opened. */
    val network: Boolean = false,
    /** Builds the sample; the flag says whether it may go online. Null means: run the real detector on the text. */
    val make: ((Boolean) -> InspectResult?)? = null
)

private class TipBlockSpec(val label: String, val tips: List<InspectTip>)

private val Blocks = listOf(
    TipBlockSpec(
        "Numbers",
        listOf(
            InspectTip(R.drawable.ic_inspect_calc, "Calculations", "Select a sum, percentages included. The card shows the working.", "567 + 15%", pill = "CALC"),
            InspectTip(R.drawable.ic_inspect_convert, "Units", "Select a measurement and see it in the other system.", "51 lb"),
            InspectTip(
                R.drawable.ic_equals, "Numbers", "Select a big number for its words, and now and then a fact about it.", "3,100,000,000",
                backwards = "Select the words (three lakh fifty thousand) to get the digits.", pill = "NUMBER"
            ),
            InspectTip(
                R.drawable.ic_inspect_convert, "Dimensions", "Select sizes like 21 x 29.7 cm or 1920x1080. Standard sizes are named.", "21 x 29.7 cm",
                backwards = "Select a name (A3, us letter, 1080p, queen bed, credit card) to get its size.", pill = "DIMS"
            )
        )
    ),
    TipBlockSpec(
        "Money and India",
        listOf(
            InspectTip(R.drawable.ic_inspect_convert, "Currency", "Select an amount and see it in your own currency.", "money", pill = "MONEY"),
            InspectTip(R.drawable.ic_upi_rupee, "UPI IDs", "Copy a UPI ID to see which app it belongs to, scan it as a QR, or jump straight to paying.", "ryan@oksbi"),
            InspectTip(R.drawable.ic_upi_rupee, "Bank codes", "Select an IFSC code for the bank, branch and what it supports.", "SBIN0001234", pill = "IFSC", make = { InspectSamples.bank() }),
            InspectTip(R.drawable.ic_inspect_convert, "PIN codes", "Select a six-digit PIN for the place it covers.", "400001", make = { InspectSamples.post() })
        )
    ),
    TipBlockSpec(
        "Time",
        listOf(
            InspectTip(R.drawable.ic_calendar_countdown, "Time zones", "Select a time in any zone and see it on your clock, with the gap and the day change.", "3 PM EST", pill = "TIME"),
            InspectTip(R.drawable.ic_calendar_countdown, "Dates", "Select a date and see how far away it is. The card draws the months in between.", "date", pill = "DATE")
        )
    ),
    TipBlockSpec(
        "Words and web",
        listOf(
            InspectTip(R.drawable.ic_inspect_define, "Definitions", "Select a word. Tap the chip for the full entry, with every meaning.", "raster", pill = "DEFINE"),
            InspectTip(
                R.drawable.ic_inspect_define, "Wikipedia", "Select a person, place or thing, even a long title, for the summary and a photo when there is one.",
                "Leonhard Euler", pill = "WIKI", network = true, make = { live -> InspectSamples.wiki(live) }
            ),
            InspectTip(
                R.drawable.ic_inspect_define, "Reading time", "Select a longer passage for how long it takes to read or say aloud.",
                "Select a passage like this one, a few sentences long, and the card will estimate how long it takes to read it out loud, and how many words it holds.",
                pill = "TEXT"
            ),
            InspectTip(R.drawable.ic_inspect_convert, "Links", "Copy or select a link. Trackers are stripped, shortened links can be unrolled, and a check looks at the site.", "https://bit.ly/3xYzAb")
        )
    ),
    TipBlockSpec(
        "Colour",
        listOf(
            InspectTip(
                R.drawable.ic_inspect_colour, "Colours", "Select a colour code for the colour, its name and its complement.", "#CCFF00",
                backwards = "Select a name (teal, forest green) to get the hex code."
            )
        )
    )
)

private val TipSurface = Color(0xFF15161A)

/** Rows in a block interlock like the ones on the Settings screen: big radius outside, a tight 6dp where they meet. */
private fun tipShape(index: Int, count: Int): Shape {
    val big = CornerSize(22.dp)
    val small = CornerSize(6.dp)
    val top = if (index == 0) big else small
    val bottom = if (index == count - 1) big else small
    return RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
}

@Composable
fun InspectScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    remember { PillPrefs.attach(context) }
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
    val text = remember(tip) { InspectSamples.input(tip.key) }
    // Picking another pill text bumps this, which rebuilds the sample.
    var rev by remember { mutableIntStateOf(0) }
    // One result feeds both the chip and the card. It is built off the main thread and can never throw into the UI;
    // samples that go online show their offline version until the row is opened.
    val online = open || !tip.network
    val result by produceState<InspectResult?>(null, tip, rev, online) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (tip.make != null) tip.make.invoke(online) else InspectSamples.resolve(text)
            }.getOrNull()
        }
    }
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
            // The selection, highlighted the way the system highlights selected text.
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TagColors.Lime)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = TagColors.OnLime)
                )
            }
            TagIcon(R.drawable.ic_chevron_right, TagColors.Outline, size = 20.dp)
            ChipPreview(result)
        }
        if (tip.backwards != null) {
            // Works in reverse too: a small label on the tip rather than a tip of its own.
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
        val option = tip.pill?.let { PillConfig.option(it) }
        if (open && option != null) {
            PillPicker(option, remember(rev) { PillConfig.get(option.key) }) { choice ->
                view.tick(Tick.On)
                PillConfig.set(option.key, choice)
                rev++
            }
        }
        AnimatedVisibility(visible = open) {
            Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                val r = result
                if (r != null) InspectCard(r.toPayload())
                else Text(
                    "Nothing to show offline.",
                    style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = TagColors.Outline)
                )
            }
        }
    }
}

/** "PILL SHOWS" and the choices for one card; the pick is stored and the chip above updates at once. */
@Composable
private fun PillPicker(option: PillConfig.Option, current: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "PILL SHOWS",
            style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.2.sp, color = TagColors.Outline)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            option.choices.forEach { ch ->
                val on = ch.value == current
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(if (on) TagColors.Lime else TagColors.Black)
                        .border(1.5.dp, if (on) TagColors.Lime else TagColors.Outline, CircleShape)
                        .clickable { onPick(ch.value) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        ch.label,
                        style = TextStyle(
                            fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            color = if (on) TagColors.OnLime else TagColors.White
                        )
                    )
                }
            }
        }
    }
}

/** A stand-in for the status-bar chip, built from the same result as the card: its pill text, and its icon or colour swatch. */
@Composable
private fun ChipPreview(result: InspectResult?) {
    val context = LocalContext.current
    Row(
        Modifier
            .clip(CircleShape)
            .background(TagColors.Black)
            .border(2.dp, TagColors.Outline, CircleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (result == null) {
            Text("…", style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TagColors.Outline))
            return@Row
        }
        val icon = remember(result.iconName) {
            result.iconName?.let { context.resources.getIdentifier(it, "drawable", context.packageName) } ?: 0
        }
        when {
            result.swatch != null -> Box(Modifier.size(16.dp).clip(CircleShape).background(Color(result.swatch!!)))
            icon != 0 -> TagIcon(icon, TagColors.White, size = 18.dp)
            else -> Box(Modifier.size(8.dp).clip(CircleShape).background(TagColors.White))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            result.pill, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TagColors.White)
        )
    }
}
