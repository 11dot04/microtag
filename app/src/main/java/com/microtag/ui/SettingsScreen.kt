package com.microtag.ui

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.R
import java.util.Calendar

/** One row inside a block. `checked == null` means the row is a plain action. */
private class Item(
    val title: String,
    val sub: String,
    val checked: Boolean? = null,
    val onChange: (Boolean) -> Unit = {},
    val onClick: (() -> Unit)? = null,
    /** Shown in accent at the end of an action row instead of the chevron (a time, a choice). */
    val value: String? = null,
    /** Draw the value in the quiet outline colour instead of the accent (e.g. "Missing"). */
    val valueMuted: Boolean = false,
    /** A bracketed state tag under the description, e.g. "Granted" (shown only with micrographics on). */
    val tag: String? = null
)


private fun clock(context: Context, minuteOfDay: Int): String {
    val c = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(Calendar.MINUTE, minuteOfDay % 60)
    }
    return DateFormat.getTimeFormat(context).format(c.time)
}

private fun pickTime(context: Context, initial: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        context, { _, h, m -> onPicked(h * 60 + m) },
        initial / 60, initial % 60, DateFormat.is24HourFormat(context)
    ).show()
}

/**
 * Rows in a block interlock like the Simulate / Stats / Logs buttons on the dashboard,
 * only stacked: big radius on the block's outer corners, a tight 6dp where rows meet.
 */
private fun groupShape(index: Int, count: Int): Shape {
    val big = CornerSize(22.dp)
    val small = CornerSize(6.dp)
    val top = if (index == 0) big else small
    val bottom = if (index == count - 1) big else small
    return RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onReplayIntro: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { TagSettings(context) }
    val perms = rememberPermissions()

    var palette by remember { mutableStateOf(settings.palette) }
    var micro by remember { mutableStateOf(settings.micro) }
    var amoled by remember { mutableStateOf(settings.amoled) }
    var multicolour by remember { mutableStateOf(settings.multicolour) }
    var appearance by remember { mutableStateOf(settings.appearance) }
    var reducedMotion by remember { mutableStateOf(settings.reducedMotion) }
    var haptics by remember { mutableStateOf(settings.haptics) }
    var nightPause by remember { mutableStateOf(settings.nightPause) }
    var nightStart by remember { mutableStateOf(settings.nightStartMin) }
    var nightEnd by remember { mutableStateOf(settings.nightEndMin) }
    var batterySaver by remember { mutableStateOf(settings.pauseOnBatterySaver) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "dev"
    }

    SubScreen(onBack, spacing = 10.dp) {
        Spacer(Modifier.height(12.dp))
        MicroStrip(5, "Settings", jp = "設定")
        FitText(
            AnnotatedString("Settings"),
            TextStyle(
                fontFamily = TagFonts.display, fontWeight = FontWeight.Black, fontSize = 56.sp,
                lineHeight = 60.sp, letterSpacing = (-1.5).sp, color = TagColors.White,
                fontSynthesis = TagFonts.NoSynthesis
            ),
            Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))

        Block(
            "Permissions",
            listOf(
                Item("Notification listener", "Lets TAG see notifications. Opens system settings.", perms.hasListener, { perms.openListenerSettings() }, tag = if (perms.hasListener) "Granted" else "Missing"),
                Item("Post notifications", "Lets TAG show its chips.", perms.hasNotif, { perms.requestNotifications() }, tag = if (perms.hasNotif) "Granted" else "Missing"),
                Item("Calendar", "For the events countdown.", perms.hasCalendar, { perms.requestCalendar() }, tag = if (perms.hasCalendar) "Granted" else "Missing"),
                Item("Nearby devices", "Lets the Bluetooth chip read a device's name.", perms.hasNearby, { perms.requestNearby() }, tag = if (perms.hasNearby) "Granted" else "Missing"),
                Item("Shizuku", "Optional. Clipboard access in the background.", perms.hasShizuku, { perms.requestShizuku() }, tag = if (perms.hasShizuku) "Running" else "Off")
            ) + if (sensitiveNotificationsApply()) listOf(
                // State only, no switch: it is granted through Shizuku (or adb), not from this screen.
                Item(
                    "Read sensitive notifications",
                    when {
                        perms.hasSensitive -> "Granted through Shizuku. OTP codes are readable."
                        perms.hasShizuku -> "Not granted yet. Tap to copy the command that grants it."
                        else -> "Needs Shizuku. Grant Shizuku first, then tap here."
                    },
                    onClick = { if (perms.hasShizuku || perms.hasSensitive) perms.copySensitiveCommand() else perms.requestShizuku() },
                    value = if (perms.hasSensitive) "Granted" else "Missing",
                    valueMuted = !perms.hasSensitive
                )
            ) else emptyList()
        )
        Block(
            "Battery",
            buildList {
                add(Item("Pause at night", "No chips overnight. OTP codes still come through.", nightPause, {
                    nightPause = it
                    settings.nightPause = it
                }))
                if (nightPause) {
                    add(Item("Off from", "Tap to change.", onClick = {
                        pickTime(context, nightStart) { nightStart = it; settings.nightStartMin = it }
                    }, value = clock(context, nightStart)))
                    add(Item("Back on at", "Tap to change.", onClick = {
                        pickTime(context, nightEnd) { nightEnd = it; settings.nightEndMin = it }
                    }, value = clock(context, nightEnd)))
                }
                add(Item("Pause on Battery Saver", "No chips while Android's Battery Saver is on. OTP codes still come through.", batterySaver, {
                    batterySaver = it
                    settings.pauseOnBatterySaver = it
                }))
            }
        )
        if (nightPause) {
            MicroHoursBar(
                nightStart, nightEnd, Modifier.padding(horizontal = 4.dp),
                nowMin = remember { Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) } }
            )
        }
        PalettePicker(selected = palette) { p ->
            palette = p.id
            settings.palette = p.id
            TagColors.usePalette(context, p.id)
        }
        Block(
            "Look and feel",
            listOf(
                Item("Micrographics", "Tiny technical marks, rules and annotations around the app.", micro, {
                    micro = it
                    settings.micro = it
                    TagColors.useMicro(it)
                }),
                Item("AMOLED black", "Pure black base on dark palettes, so OLED pixels switch off.", amoled, {
                    amoled = it
                    settings.amoled = it
                    TagColors.useAmoled(it)
                }),
                Item(
                    "Appearance", "Every palette, on a light or dark base. Tap to change.",
                    onClick = {
                        val next = Appearance.fromId(appearance).next()
                        appearance = next.id
                        settings.appearance = next.id
                        TagColors.useAppearance(next.id)
                    },
                    value = Appearance.fromId(appearance).label
                ),
                Item("Multicolour tiles", "Give neighbouring tiles different accents. Needs a palette with more than one.", multicolour, {
                    multicolour = it
                    settings.multicolour = it
                    TagColors.useMulticolour(it)
                }),
                Item("Reduce motion", "Skip the springs, flood fills and entrances.", reducedMotion, {
                    reducedMotion = it
                    settings.reducedMotion = it
                    TagMotion.enabled = !it
                }),
                Item("Haptics", "Taps, toggles and dial detents.", haptics, {
                    haptics = it
                    settings.haptics = it
                    TagMotion.haptics = it
                })
            )
        )
        Block("Intro", listOf(Item("Replay intro", "Walk through the welcome screens again.", onClick = onReplayIntro)))
        Block("About", listOf(Item("TAG", "Version $version")))
    }
}

@Composable
private fun Block(label: String, items: List<Item>) {
    // Each block's switches wear their own accent: permissions the main one, battery the second, look and feel the third.
    val tone = when (label) { "Battery" -> 1; "Look and feel" -> 2; else -> 0 }
    val index = when (label) { "Permissions" -> 1; "Battery" -> 2; "Look and feel" -> 4; "Intro" -> 5; "About" -> 6; else -> null }
    TileTone(tone) { BlockBody(label, items, index) }
}

@Composable
private fun BlockBody(label: String, items: List<Item>, index: Int?) {
    MicroHeader(label, Modifier.padding(start = 4.dp, top = 8.dp), index)
    Column(
        Modifier.then(
            if (TagMotion.enabled) Modifier.animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
            else Modifier
        ),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        items.forEachIndexed { i, item -> SettingRow(item, groupShape(i, items.size)) }
    }
}

@Composable
private fun SettingRow(item: Item, shape: Shape) {
    val view = LocalView.current
    val interactive = item.checked != null || item.onClick != null
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TagColors.Surface)
            .then(
                if (interactive) Modifier.clickable {
                    if (item.checked != null) item.onChange(!item.checked) else {
                        view.tick(Tick.Press)
                        item.onClick?.invoke()
                    }
                } else Modifier
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 14.dp)) {
            Text(
                item.title,
                style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TagColors.White)
            )
            Text(
                item.sub,
                style = TextStyle(
                    fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                    lineHeight = 18.sp, color = TagColors.Outline
                )
            )
            if (item.tag != null && TagColors.MicroOn) {
                MicroTag(
                    item.tag,
                    Modifier.padding(top = 4.dp),
                    color = if (item.tag == "Granted" || item.tag == "Running") TagColors.AccentText else TagColors.Outline
                )
            }
        }
        when {
            item.checked != null -> TagSwitch(checked = item.checked, onChange = item.onChange)
            item.onClick != null && item.value != null -> Text(
                item.value,
                style = TextStyle(
                    fontFamily = Montserrat, fontWeight = FontWeight.Black, fontSize = 17.sp,
                    color = if (item.valueMuted) TagColors.Outline else TagColors.AccentText
                )
            )
            item.onClick != null -> TagIcon(R.drawable.ic_chevron_right, TagColors.White, size = 24.dp)
        }
    }
}

/**
 * The palettes in a Material 3 multi-browse carousel: one big card, a medium one and a sliver
 * that squeezes in from the edge as you scroll. Each card is a strip of the palette's colours
 * (like a paint chip), with its name and an "Aa" in its own display face. Tapping applies it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PalettePicker(selected: String, onPick: (TagPalette) -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    MicroHeader("Palette", Modifier.padding(start = 4.dp, top = 8.dp), 3)
    val state = rememberCarouselState { TagPalettes.All.size }
    HorizontalMultiBrowseCarousel(
        state = state,
        preferredItemWidth = 210.dp,
        modifier = Modifier.fillMaxWidth().height(224.dp),
        itemSpacing = 6.dp,
        contentPadding = PaddingValues(0.dp)
    ) { i ->
        val p = TagColors.preview(TagPalettes.All[i])
        val on = p.id == selected
        val display = remember(p.id) { p.display?.let { TagFonts.family(context, it) } ?: Montserrat }
        val corner = RoundedCornerShape(28.dp)
        Box(
            Modifier
                .fillMaxSize()
                .maskClip(corner)
                .background(p.base)
                .clickable { view.tick(Tick.On); onPick(p) }
        ) {
            Column(Modifier.fillMaxSize().padding(10.dp)) {
                // The paint chip: surface, ink, a wide accent, then whatever extra colours the palette has.
                Row(
                    Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp))
                ) {
                    listOf(p.surface to 1f, p.ink to 1f, p.accent to 2.4f).plus(p.supporting.map { it to 1f }).plus(p.paper to 1f)
                        .forEach { (c, w) -> Box(Modifier.weight(w).fillMaxHeight().background(c)) }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        p.name,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = p.ink)
                    )
                    Text(
                        "Aa",
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(
                            fontFamily = display, fontWeight = FontWeight.Black, fontSize = 24.sp,
                            color = p.ink, fontSynthesis = TagFonts.NoSynthesis
                        )
                    )
                }
                MicroLabel(
                    "#%06X".format(p.accent.toArgb() and 0xFFFFFF),
                    modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                    color = p.ink.copy(alpha = 0.55f), wrap = false
                )
            }
            if (on) {
                // The chosen palette wears a ring in its own ink.
                Box(Modifier.fillMaxSize().border(3.dp, p.ink, corner))
            }
        }
    }
}
