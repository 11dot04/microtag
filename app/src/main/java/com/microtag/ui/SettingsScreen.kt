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
import androidx.compose.foundation.layout.Column
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
    val value: String? = null
)

private val RowSurface = Color(0xFF15161A)

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

    var materialYou by remember { mutableStateOf(settings.materialYou) }
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
        FitText(
            AnnotatedString("Settings"),
            TextStyle(
                fontFamily = Montserrat, fontWeight = FontWeight.Black, fontSize = 56.sp,
                lineHeight = 60.sp, letterSpacing = (-1.5).sp, color = TagColors.White
            ),
            Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))

        Block(
            "Permissions",
            listOf(
                Item("Notification listener", "Lets TAG see notifications. Opens system settings.", perms.hasListener, { perms.openListenerSettings() }),
                Item("Post notifications", "Lets TAG show its chips.", perms.hasNotif, { perms.requestNotifications() }),
                Item("Calendar", "For the events countdown.", perms.hasCalendar, { perms.requestCalendar() }),
                Item("Shizuku", "Optional. Clipboard access in the background.", perms.hasShizuku, { perms.requestShizuku() })
            )
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
        Block(
            "Look and feel",
            listOf(
                Item("Material You", "Swap the lime for your system accent colour.", materialYou, {
                    materialYou = it
                    settings.materialYou = it
                    TagColors.useMaterialYou(context, it)
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
    Text(
        label.uppercase(),
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        style = TextStyle(
            fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            letterSpacing = 1.2.sp, color = TagColors.Lime
        )
    )
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
            .background(RowSurface)
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
        }
        when {
            item.checked != null -> TagSwitch(checked = item.checked, onChange = item.onChange)
            item.onClick != null && item.value != null -> Text(
                item.value,
                style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Black, fontSize = 17.sp, color = TagColors.Lime)
            )
            item.onClick != null -> TagIcon(R.drawable.ic_chevron_right, TagColors.White, size = 24.dp)
        }
    }
}
