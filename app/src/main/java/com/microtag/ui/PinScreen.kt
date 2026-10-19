package com.microtag.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.R
import com.microtag.inspect.Pin
import com.microtag.inspect.PillConfig
import com.microtag.inspect.PillPrefs
import com.microtag.inspect.PinEngine
import com.microtag.inspect.PinLogic
import com.microtag.inspect.PinTileService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pin: what is pinned right now, how long a new pin lasts, and whether copying a code offers to pin it.
 * Pinning itself happens on the cards (the small pin by the label) and from the Quick Settings tile.
 */
@Composable
fun PinScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    remember { PillPrefs.attach(context) }
    // Bumped by every action on this screen; rebuilds the list and the pickers.
    var rev by remember { mutableIntStateOf(0) }
    val pins = remember(rev) { runCatching { PinEngine.list(context) }.getOrDefault(emptyList()).sortedByDescending { it.createdAt } }

    SubScreen(onBack, spacing = 10.dp) {
        Spacer(Modifier.height(12.dp))
        FitText(
            AnnotatedString("Pin"),
            TextStyle(
                fontFamily = Montserrat, fontWeight = FontWeight.Black, fontSize = 56.sp,
                lineHeight = 60.sp, letterSpacing = (-1.5).sp, color = TagColors.White
            ),
            Modifier.fillMaxWidth()
        )
        Text(
            "Keep a gate number, a locker code or a password on your status bar. Tap the pin on any card, or use the Pin clipboard tile in Quick Settings.",
            style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, color = TagColors.Outline)
        )
        Spacer(Modifier.height(6.dp))

        SectionLabel("Pinned now")
        if (pins.isEmpty()) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(TagColors.Surface).padding(18.dp)) {
                Text(
                    "Nothing pinned.",
                    style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TagColors.Outline)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                pins.forEach { pin ->
                    PinRow(
                        pin,
                        onUnpin = { view.tick(Tick.Press); PinEngine.unpin(context, pin.id); rev++ },
                        onExtend = { view.tick(Tick.Press); PinEngine.extend(context, pin.id, 60 * 60_000L); rev++ }
                    )
                }
            }
        }

        SectionLabel("Settings")
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(TagColors.Surface).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            listOf("PINEXPIRY", "PINPROMPT").mapNotNull { PillConfig.option(it) }.forEach { option ->
                PillPicker(option, remember(rev, option.key) { PillConfig.get(option.key) }) { choice ->
                    view.tick(Tick.On)
                    PillConfig.set(option.key, choice)
                    rev++
                }
            }
            Text(
                "A pin sits on the status bar for the first 30 minutes. If it lasts longer it carries on in the notification shade.",
                style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, color = TagColors.Outline)
            )
        }

        if (Build.VERSION.SDK_INT >= 33) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .border(1.5.dp, TagColors.Outline, CircleShape)
                    .clickable { view.tick(Tick.Press); requestTile(context) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    "Add the Quick Settings tile",
                    style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TagColors.White)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.2.sp, color = TagColors.Lime)
    )
}

@Composable
private fun PinRow(pin: Pin, onUnpin: () -> Unit, onExtend: () -> Unit) {
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val zone = remember { ZoneId.systemDefault() }
    val now = System.currentTimeMillis()
    val made = fmt.format(Instant.ofEpochMilli(pin.createdAt).atZone(zone))
    val sub = "Pinned $made · " + when {
        pin.expiresAt == 0L -> "until you unpin it"
        else -> {
            val mins = ((pin.expiresAt - now) / 60_000L).coerceAtLeast(0)
            if (mins >= 60) "${mins / 60} h ${mins % 60} min left" else "$mins min left"
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(TagColors.Surface).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            pin.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TagColors.White)
        )
        Text(sub, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = TagColors.Outline))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallPill("UNPIN", filled = true, onClick = onUnpin)
            if (pin.expiresAt != 0L) SmallPill("+1 HOUR", filled = false, onClick = onExtend)
        }
    }
}

@Composable
private fun SmallPill(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (filled) TagColors.Lime else TagColors.Black)
            .border(1.5.dp, if (filled) TagColors.Lime else TagColors.Outline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            style = TextStyle(
                fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp,
                color = if (filled) TagColors.OnLime else TagColors.White
            )
        )
    }
}

/** Android 13+: asks the system to offer adding the tile (one confirmation dialog). Quietly does nothing if it can't. */
private fun requestTile(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < 33) return
    runCatching {
        val sbm = context.getSystemService(StatusBarManager::class.java)
        sbm.requestAddTileService(
            ComponentName(context, PinTileService::class.java), "Pin clipboard",
            Icon.createWithResource(context, R.drawable.ic_pin), context.mainExecutor
        ) { }
    }
}
