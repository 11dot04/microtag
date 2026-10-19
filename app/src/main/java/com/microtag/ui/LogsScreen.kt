package com.microtag.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.microtag.R
import com.microtag.core.MicrotagLog
import com.microtag.core.MicrotagLog.Decision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val LogMono: FontFamily get() = TagFonts.data

internal class AppMeta(val label: String, val icon: ImageBitmap?)

private sealed interface LogRow {
    class Header(val label: String) : LogRow
    class Item(val entry: MicrotagLog.Entry) : LogRow
}

@Composable
internal fun rememberAppMeta(pkgs: List<String>): State<Map<String, AppMeta>> {
    val ctx = LocalContext.current
    return produceState<Map<String, AppMeta>>(emptyMap(), pkgs) {
        value = withContext(Dispatchers.Default) {
            val pm = ctx.packageManager
            pkgs.associateWith { pkg ->
                runCatching {
                    AppMeta(
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(),
                        pm.getApplicationIcon(pkg).toBitmap(72, 72).asImageBitmap()
                    )
                }.getOrElse { AppMeta(pkg.substringAfterLast('.'), null) }
            }
        }
    }
}

private fun dayLabel(ms: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ms }
    fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (same(now, then)) return "TODAY"
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (same(now, then)) return "YESTERDAY"
    return SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(ms)).uppercase()
}

@Composable
fun LogsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(MicrotagLog.snapshot()) }
    var recording by remember { mutableStateOf(MicrotagLog.enabled) }
    var query by remember { mutableStateOf("") }
    // Ignored and Unchanged drown the real entries, so they start hidden. "All" brings them back.
    var decisions by remember { mutableStateOf(Decision.values().toSet() - setOf(Decision.IGNORED, Decision.UNCHANGED)) }
    var apps by remember { mutableStateOf(emptySet<String>()) }
    var expanded by remember { mutableStateOf(emptySet<Int>()) }
    var armed by remember { mutableStateOf(false) }

    // Live: re-read the log while this screen is up.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            entries = MicrotagLog.snapshot()
        }
    }
    LaunchedEffect(armed) { if (armed) { delay(3000); armed = false } }

    val allPkgs = remember(entries.size) { entries.map { it.pkg }.distinct() }
    val meta by rememberAppMeta(allPkgs)
    val pkgCounts = remember(entries) { entries.groupingBy { it.pkg }.eachCount().toList().sortedByDescending { it.second } }

    val q = query.trim().lowercase()
    val filtered = entries.filter { e ->
        (decisions.isEmpty() || e.decision in decisions) &&
            (apps.isEmpty() || e.pkg in apps) &&
            (q.isEmpty() || listOf(e.title, e.text, e.pill ?: "", e.reason, e.path, meta[e.pkg]?.label ?: e.pkg)
                .any { it.lowercase().contains(q) })
    }
    val rows = remember(filtered) {
        buildList {
            var last = ""
            filtered.forEach { e ->
                val label = dayLabel(e.timeMs)
                if (label != last) { add(LogRow.Header(label)); last = label }
                add(LogRow.Item(e))
            }
        }
    }

    SubScreenLazy(
        onBack = onBack,
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingButton(filled = false, onClick = { share(context, MicrotagLog.asText(filtered)) }) { tint ->
                    TagIcon(R.drawable.ic_share, tint, size = 26.dp)
                }
                FloatingButton(filled = armed, accent = if (armed) TagColors.Signal else TagColors.Lime, onAccent = if (armed) TagColors.OnSignal else TagColors.White, onClick = {
                    if (armed) { MicrotagLog.clear(); entries = MicrotagLog.snapshot(); armed = false } else armed = true
                }) { tint -> TagIcon(R.drawable.ic_trash, tint, size = 26.dp) }
            }
        }
    ) {
        item { Spacer(Modifier.height(12.dp)) }
        item { MicroStrip(3, "Logs") }
        item {
            FitText(
                AnnotatedString("Logs"),
                TextStyle(
                    fontFamily = TagFonts.display, fontWeight = FontWeight.Black, fontSize = 56.sp,
                    lineHeight = 60.sp, letterSpacing = (-1.5).sp, color = TagColors.White,
                    fontSynthesis = TagFonts.NoSynthesis
                ),
                Modifier.fillMaxWidth()
            )
        }
        item {
            Text(
                if (armed) "Tap the bin again to clear everything."
                else "${filtered.size} of ${entries.size} entries" + if (decisions.isNotEmpty() && Decision.IGNORED !in decisions) "  ·  noise hidden" else "",
                style = TextStyle(
                    fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                    color = if (armed) TagColors.Signal else TagColors.Outline
                )
            )
        }
        item {
            // Recording switch, in the same grammar as Settings.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(TagColors.Surface)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        "Recording",
                        style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TagColors.White)
                    )
                    Text(
                        "Keeps the newest 500 entries.",
                        style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = TagColors.Outline)
                    )
                }
                TagSwitch(checked = recording, onChange = { recording = it; MicrotagLog.setEnabled(it) })
            }
        }
        item { SearchField(query) { query = it } }
        item {
            Row(
                Modifier.bleed(20.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip("All", decisions.isEmpty()) { decisions = emptySet() }
                Decision.values().forEach { d ->
                    FilterChip(d.name.lowercase().replaceFirstChar { it.uppercase() }, d in decisions) {
                        decisions = if (d in decisions) decisions - d else decisions + d
                    }
                }
            }
        }
        item {
            Row(
                Modifier.bleed(20.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip("All apps", apps.isEmpty()) { apps = emptySet() }
                pkgCounts.take(12).forEach { (pkg, n) ->
                    val m = meta[pkg]
                    FilterChip("${m?.label ?: pkg.substringAfterLast('.')}  $n", pkg in apps, icon = m?.icon) {
                        apps = if (pkg in apps) apps - pkg else apps + pkg
                    }
                }
            }
        }

        if (rows.isEmpty()) {
            item {
                Text(
                    if (entries.isEmpty()) "Nothing logged yet. Entries show up here as notifications are handled."
                    else "No entries match these filters.",
                    modifier = Modifier.padding(top = 24.dp),
                    style = TextStyle(
                        fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                        lineHeight = 21.sp, color = TagColors.Outline
                    )
                )
            }
        }

        items(
            rows,
            key = { r -> when (r) { is LogRow.Header -> "h:" + r.label; is LogRow.Item -> System.identityHashCode(r.entry) } },
            contentType = { r -> if (r is LogRow.Header) 0 else 1 }
        ) { row ->
            when (row) {
                is LogRow.Header -> MicroHeader(row.label, Modifier.padding(top = 14.dp, start = 4.dp))
                is LogRow.Item -> {
                    val id = System.identityHashCode(row.entry)
                    LogCard(row.entry, meta[row.entry.pkg], id in expanded) {
                        expanded = if (id in expanded) expanded - id else expanded + id
                    }
                }
            }
        }
    }
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Share log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .border(2.dp, TagColors.Outline, CircleShape)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = TagColors.White),
            cursorBrush = SolidColor(TagColors.Lime),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        "Search title, text or app",
                        style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = TagColors.Dim)
                    )
                }
                inner()
            }
        )
    }
}

@Composable
internal fun FilterChip(text: String, selected: Boolean, icon: ImageBitmap? = null, onClick: () -> Unit) {
    val view = LocalView.current
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (selected) TagColors.Lime else TagColors.Black)
            .border(2.dp, if (selected) TagColors.Lime else TagColors.Outline, CircleShape)
            .clickable { view.tick(if (selected) Tick.Off else Tick.On); onClick() }
            .padding(start = if (icon != null) 8.dp else 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text, maxLines = 1,
            style = TextStyle(
                fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                color = if (selected) TagColors.OnLime else TagColors.White
            )
        )
    }
}

@Composable
private fun LogCard(e: MicrotagLog.Entry, meta: AppMeta?, expanded: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val time = remember(e.timeMs) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(e.timeMs)) }
    val iconRes = remember(e.icon) { e.icon?.let { context.resources.getIdentifier(it, "drawable", context.packageName) } ?: 0 }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(TagColors.Surface)
            .drawBehind {
                // status stripe: accent for promoted, a faint grey for everything else
                // custom rules take the secondary accent, fallbacks the tertiary, parser captures the main one
                val stripe = when (e.path) { "RULE" -> 1; "FALLBACK" -> 2; else -> 0 }
                drawRect(
                    if (e.decision == Decision.PROMOTED) TagColors.tone(stripe) else TagColors.Outline.copy(alpha = 0.35f),
                    size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)
                )
            }
            .clickable(onClick = onToggle)
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
            .padding(start = 18.dp, top = 14.dp, end = 14.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(TagColors.Black), contentAlignment = Alignment.Center) {
                if (meta?.icon != null) Image(bitmap = meta.icon, contentDescription = null, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    e.title.ifBlank { meta?.label ?: e.pkg }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TagColors.White)
                )
                if (e.text.isNotBlank()) {
                    Text(
                        e.text, maxLines = if (expanded) 8 else 2, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                            lineHeight = 18.sp, color = TagColors.Outline
                        )
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(time, style = TextStyle(fontFamily = LogMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = TagColors.Outline))
                if (e.repeats > 1) {
                    Text("×${e.repeats}", style = TextStyle(fontFamily = LogMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TagColors.AccentText))
                }
            }
        }

        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DecisionTag(e.decision)
            if (!e.pill.isNullOrBlank()) PillChip(iconRes, e.pill!!)
        }

        if (expanded) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Detail("APP", meta?.label ?: e.pkg)
                Detail("PATH", e.path)
                Detail("REASON", e.reason)
                if (e.meta.isNotBlank()) Detail("META", e.meta)
                Detail("KEY", e.key)
            }
        }
    }
}

@Composable
private fun DecisionTag(d: Decision) {
    val promoted = d == Decision.PROMOTED
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (promoted) TagColors.Lime else Color.Transparent)
            .border(2.dp, if (promoted) TagColors.Lime else TagColors.Outline, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            d.name,
            style = TextStyle(
                fontFamily = LogMono, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp,
                color = if (promoted) TagColors.OnLime else TagColors.Outline
            )
        )
    }
}

/** What went into the status bar chip, drawn like the chip. */
@Composable
private fun PillChip(iconRes: Int, text: String) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(TagColors.Black)
            .border(2.dp, TagColors.Outline, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != 0) {
            Image(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(14.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(TagColors.White))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TagColors.White)
        )
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row {
        Text(
            label,
            modifier = Modifier.width(64.dp),
            style = TextStyle(fontFamily = LogMono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TagColors.Dim)
        )
        Text(
            value, modifier = Modifier.weight(1f),
            style = TextStyle(fontFamily = LogMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, color = TagColors.Outline)
        )
    }
}
