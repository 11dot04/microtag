package com.microtag.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.drawText
import androidx.compose.runtime.rememberCoroutineScope
import com.microtag.R
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.microtag.core.MicrotagLog
import com.microtag.core.MicrotagPrefs
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

private class Session(val timeMs: Long, val pkg: String, val path: String, val day: Int, val hour: Int, val epochDay: Long)

private class StatsData(
    val sessions: List<Session>,
    val entries: List<MicrotagLog.Entry> 
)

private const val SESSION_GAP_MS = 30 * 60 * 1000L

private fun dayIndexOf(ms: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
}

private fun epochDayOf(ms: Long): Long = (ms + TimeZone.getDefault().getOffset(ms)) / 86_400_000L

private fun loadStats(): StatsData {
    val all = MicrotagLog.snapshot()
    val lastSeen = HashMap<String, Long>()
    val c = Calendar.getInstance()
    val sessions = ArrayList<Session>()
    all.filter { it.decision == MicrotagLog.Decision.PROMOTED }
        .sortedBy { it.timeMs }
        .forEach { e ->
            val previous = lastSeen[e.key]
            lastSeen[e.key] = e.timeMs
            if (previous == null || e.timeMs - previous > SESSION_GAP_MS) {
                c.timeInMillis = e.timeMs
                sessions.add(
                    Session(e.timeMs, e.pkg, e.path, (c.get(Calendar.DAY_OF_WEEK) + 5) % 7, c.get(Calendar.HOUR_OF_DAY), epochDayOf(e.timeMs))
                )
            }
        }
    return StatsData(sessions, all)
}

fun todayHourCounts(): IntArray {
    val out = IntArray(24)
    val today = epochDayOf(System.currentTimeMillis())
    loadStats().sessions.filter { it.epochDay == today }.forEach { out[it.hour]++ }
    return out
}

private val DayLabels = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

@Composable
fun StatsScreen(prefs: MicrotagPrefs, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val data = remember { loadStats() }
    val today = remember { dayIndexOf(System.currentTimeMillis()) }
    val todayEpoch = remember { epochDayOf(System.currentTimeMillis()) }
    val nowHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    var selected by remember { mutableStateOf(setOf(today)) }
    var apps by remember { mutableStateOf(emptySet<String>()) }

    val byApp = remember(apps) { data.sessions.filter { apps.isEmpty() || it.pkg in apps } }
    val scoped = remember(selected, apps) { byApp.filter { it.day in selected } }
    val values = remember(scoped) { IntArray(24).also { v -> scoped.forEach { v[it.hour]++ } } }
    val total = scoped.size
    val peakHour = values.indices.maxByOrNull { values[it] }?.takeIf { values[it] > 0 }
    val heat = remember(byApp) { Array(7) { IntArray(24) }.also { g -> byApp.forEach { g[it.day][it.hour]++ } } }

    val rate = remember(selected, apps) {
        val rows = data.entries.filter { e ->
            (apps.isEmpty() || e.pkg in apps) && dayIndexOf(e.timeMs) in selected &&
                (e.decision == MicrotagLog.Decision.PROMOTED || e.decision == MicrotagLog.Decision.SKIPPED)
        }
        val promoted = rows.count { it.decision == MicrotagLog.Decision.PROMOTED }
        if (rows.isEmpty()) null else promoted * 100 / rows.size
    }
    val daysActive = remember(byApp) { byApp.map { it.epochDay }.toSet() }
    val streak = remember(daysActive) {
        var d = if (todayEpoch in daysActive) todayEpoch else todayEpoch - 1
        var n = 0
        while (d in daysActive) { n++; d-- }
        n
    }
    val best = remember(byApp) {
        byApp.groupingBy { it.epochDay }.eachCount().maxByOrNull { it.value }
    }
    val allPkgs = remember { data.sessions.map { it.pkg }.distinct() }
    val meta by rememberAppMeta(allPkgs)
    val topApps = remember { data.sessions.groupingBy { it.pkg }.eachCount().toList().sortedByDescending { it.second }.take(8) }

    SubScreen(
        onBack = onBack,
        action = {
            FloatingButton(filled = false, onClick = {
                val days = selected.sorted().joinToString(" ") { DayLabels[it] }
                scope.launch { StatsCard.share(context, days, total, values, peakHour, sourcesOf(context, scoped)) }
            }) { tint -> TagIcon(R.drawable.ic_share, tint, size = 26.dp) }
        }
    ) {
        Spacer(Modifier.height(12.dp))
        MicroStrip(2, "Stats", jp = "統計")
        FitText(
            AnnotatedString("Interceptions"),
            TextStyle(
                fontFamily = TagFonts.display, fontWeight = FontWeight.Black, fontSize = 48.sp,
                lineHeight = 52.sp, letterSpacing = (-1.2).sp, color = TagColors.White,
                fontSynthesis = TagFonts.NoSynthesis
            ),
            Modifier.fillMaxWidth()
        )

        DayChips(selected, today) { day ->
            selected = if (day in selected) (if (selected.size > 1) selected - day else selected) else selected + day
        }

        if (topApps.isNotEmpty()) {
            Row(
                Modifier.bleed(20.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip("All apps", apps.isEmpty()) { apps = emptySet() }
                topApps.forEach { (pkg, n) ->
                    val m = meta[pkg]
                    FilterChip("${m?.label ?: pkg.substringAfterLast('.')}  $n", pkg in apps, icon = m?.icon) {
                        apps = if (pkg in apps) apps - pkg else apps + pkg
                    }
                }
            }
        }

        HourChart(
            values = values,
            nowHour = if (today in selected) nowHour else null,
            dashFuture = selected == setOf(today),
            modifier = Modifier.fillMaxWidth().height(260.dp)
        )

        // Six equal tiles in one interlocking block, like the rows in Settings and Inspect.
        val tiles = listOf(
            Triple("SELECTED", total.toString(), when (selected.size) {
                1 -> DayLabels[selected.first()]
                7 -> "EVERY DAY"
                else -> "${selected.size} DAYS"
            }),
            Triple("PEAK HOUR", peakHour?.let { "%02d:00".format(it) } ?: "--", peakHour?.let { "${values[it]} AT PEAK" }),
            Triple("ALL TIME", prefs.interceptCount.toString(), "ALL APPS"),
            Triple("PROMOTED", rate?.let { "$it%" } ?: "--", "OF EVALUATED"),
            Triple("STREAK", if (streak > 0) "$streak d" else "--", "IN A ROW"),
            Triple("BEST DAY", best?.let { "${it.value}" } ?: "--", best?.let {
                SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(it.key * 86_400_000L)).uppercase()
            })
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            for (r in 0 until 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (c in 0 until 3) {
                        val (caption, value, sub) = tiles[r * 3 + c]
                        StatTile(caption, value, sub, cellShape(r, c, 2, 3), Modifier.weight(1f))
                    }
                }
            }
        }

        SectionLabel("WEEK AT A GLANCE", 1)
        Text(
            "Tap a row to look at just that day.",
            style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = TagColors.Outline)
        )
        Heatmap(heat, selected, Modifier.fillMaxWidth()) { day ->
            view.tick(Tick.Detent)
            selected = setOf(day)
        }

        SectionLabel("TIME OF DAY", 2)
        // A 24-hour clock of dots (micrographics); the bars below it stay.
        MicroClockRing(
            values, nowHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) },
            modifier = Modifier.fillMaxWidth().height(210.dp),
            centerBottom = peakHour?.let { "PEAK %02d".format(it) }
        )
        TimeOfDay(values)

        ByType(scoped)
        Sources(sourcesOf(context, scoped))
    }
}

private fun sourcesOf(context: android.content.Context, sessions: List<Session>): List<Pair<String, Int>> {
    val pm = context.packageManager
    return sessions.groupingBy { it.pkg }.eachCount().toList()
        .sortedByDescending { it.second }
        .take(5)
        .map { (pkg, n) ->
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                .getOrDefault(pkg.substringAfterLast('.'))
            label to n
        }
}

@Composable
private fun DayChips(selected: Set<Int>, today: Int, onToggle: (Int) -> Unit) {
    val view = LocalView.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayLabels.forEachIndexed { i, label ->
            val on = i in selected
            val scale by animateFloatAsState(
                if (on) 1f else 0.94f,
                spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "chip"
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(42.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(CircleShape)
                        .background(if (on) TagColors.Lime else TagColors.Black)
                        .border(2.dp, if (on) TagColors.Lime else TagColors.Outline, CircleShape)
                        .clickable {
                            view.tick(if (on) Tick.Off else Tick.On)
                            onToggle(i)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = TextStyle(
                            fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            color = if (on) TagColors.OnLime else TagColors.White
                        )
                    )
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (i == today) TagColors.Lime else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun HourChart(values: IntArray, nowHour: Int?, dashFuture: Boolean, modifier: Modifier) {
    val view = LocalView.current
    val measurer = rememberTextMeasurer()
    val anim = remember { List(24) { Animatable(0f) } }
    var scrub by remember { mutableIntStateOf(-1) }

    LaunchedEffect(values.toList()) {
        anim.forEachIndexed { i, a ->
            launch { a.animateTo(values[i].toFloat(), spring(dampingRatio = 0.75f, stiffness = 180f)) }
        }
    }

    Canvas(
        modifier
            .pointerInput(Unit) {
                fun hourAt(x: Float): Int {
                    val padL = 12.dp.toPx()
                    val cw = size.width - padL - 12.dp.toPx()
                    return ((x - padL) / cw * 24f).toInt().coerceIn(0, 23)
                }
                detectDragGestures(
                    onDragStart = { scrub = hourAt(it.x); view.tick(Tick.Detent) },
                    onDragEnd = { scrub = -1 },
                    onDragCancel = { scrub = -1 },
                    onDrag = { change, _ ->
                        change.consume()
                        val h = hourAt(change.position.x)
                        if (h != scrub) { scrub = h; view.tick(Tick.Detent) }
                    }
                )
            }
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        val padL = 12.dp.toPx()
        val padR = 12.dp.toPx()
        val top = 64.dp.toPx()
        val bottom = size.height - 30.dp.toPx()
        val cw = size.width - padL - padR
        val ch = bottom - top
        val vals = FloatArray(24) { anim[it].value }
        val maxV = max(1f, vals.max())

        fun px(h: Float) = padL + (h + 0.5f) / 24f * cw
        fun py(v: Float) = bottom - (v / maxV) * ch * 0.9f
        val accent = TagColors.Lime
        val labelStyle = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = TagColors.Outline)

        for (t in 0..24 step 4) {
            val x = padL + t / 24f * cw
            drawLine(TagColors.White.copy(alpha = 0.28f), Offset(x, top - 8.dp.toPx()), Offset(x, bottom), 1.5.dp.toPx())
            if (t < 24) {
                val text = measurer.measure("%02d:00".format(t), labelStyle)
                drawText(text, topLeft = Offset(x - text.size.width / 2f, bottom + 8.dp.toPx()))
            }
        }

        if (nowHour != null) {
            val l = padL + nowHour / 24f * cw
            val r = l + cw / 24f
            drawRect(accent.copy(alpha = 0.12f), Offset(l, top - 8.dp.toPx()), androidx.compose.ui.geometry.Size(r - l, bottom - top + 8.dp.toPx()))
            clipRect(l, top - 8.dp.toPx(), r, bottom) {
                var x = l - (bottom - top)
                while (x < r) {
                    drawLine(accent, Offset(x, bottom), Offset(x + (bottom - top), top - 8.dp.toPx()), 1.5.dp.toPx())
                    x += 6.dp.toPx()
                }
            }
        }

        val pts = List(24) { Offset(px(it.toFloat()), py(vals[it])) }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 0 until 23) {
                val p0 = pts[max(i - 1, 0)]
                val p1 = pts[i]
                val p2 = pts[i + 1]
                val p3 = pts[min(i + 2, 23)]
                cubicTo(
                    p1.x + (p2.x - p0.x) / 6f, (p1.y + (p2.y - p0.y) / 6f).coerceIn(top, bottom),
                    p2.x - (p3.x - p1.x) / 6f, (p2.y - (p3.y - p1.y) / 6f).coerceIn(top, bottom),
                    p2.x, p2.y
                )
            }
        }
        val splitX = if (dashFuture && nowHour != null) padL + (nowHour + 1) / 24f * cw else size.width
        clipRect(0f, 0f, splitX, size.height) {
            drawPath(path, TagColors.White, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (splitX < size.width) {
            clipRect(splitX, 0f, size.width, size.height) {
                drawPath(
                    path, accent,
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f)))
                )
            }
        }

        val focus = if (scrub >= 0) scrub else vals.indices.maxByOrNull { vals[it] } ?: 0
        val focusValue = vals[focus]
        if (scrub >= 0 || focusValue > 0.5f) {
            val fx = px(focus.toFloat())
            val fy = py(focusValue)
            if (scrub >= 0) {
                drawLine(TagColors.White, Offset(fx, top - 8.dp.toPx()), Offset(fx, bottom), 2.dp.toPx())
            } else {
                drawLine(
                    TagColors.White.copy(alpha = 0.7f), Offset(fx, fy), Offset(fx, top - 6.dp.toPx()), 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
            }
            drawCircle(TagColors.Black, 7.dp.toPx(), Offset(fx, fy))
            drawCircle(if (scrub >= 0) TagColors.White else accent, 5.dp.toPx(), Offset(fx, fy))

            val label = measurer.measure(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 30.sp, fontWeight = FontWeight.Medium)) { append(focusValue.toInt().toString()) }
                    withStyle(SpanStyle(fontSize = 11.sp)) { append(if (scrub >= 0) "  ${"%02d:00".format(scrub)}" else "  peak") }
                },
                TextStyle(fontFamily = Montserrat, color = TagColors.White)
            )
            val lx = (fx - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
            drawText(label, topLeft = Offset(lx, 0f))
        }
    }
}

/** Corner radius is big only where the tile sits on the outside of the block, tight (6dp) where tiles meet. */
private fun cellShape(row: Int, col: Int, rows: Int, cols: Int): Shape {
    val big = CornerSize(22.dp)
    val small = CornerSize(6.dp)
    return RoundedCornerShape(
        topStart = if (row == 0 && col == 0) big else small,
        topEnd = if (row == 0 && col == cols - 1) big else small,
        bottomEnd = if (row == rows - 1 && col == cols - 1) big else small,
        bottomStart = if (row == rows - 1 && col == 0) big else small
    )
}

@Composable
private fun StatTile(caption: String, value: String, sub: String?, shape: Shape, modifier: Modifier) {
    Column(
        modifier
            .height(92.dp)          // fixed, so every tile is the same size whatever it holds
            .clip(shape)
            .background(TagColors.Surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            caption,
            style = TextStyle(
                fontFamily = TagFonts.mono, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                letterSpacing = 1.sp, color = TagColors.Outline
            )
        )
        FitText(
            AnnotatedString(value),
            TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Black, fontSize = 28.sp, color = TagColors.White)
        )
        Text(
            sub ?: " ",
            maxLines = 1,
            style = TextStyle(
                fontFamily = TagFonts.mono, fontWeight = FontWeight.Medium, fontSize = 10.sp,
                letterSpacing = 0.8.sp, color = TagColors.AccentText
            )
        )
    }
}

@Composable
private fun SectionLabel(text: String, index: Int? = null) {
    MicroHeader(text, Modifier.padding(top = 8.dp), index)
}

@Composable
private fun Heatmap(grid: Array<IntArray>, selected: Set<Int>, modifier: Modifier, onDay: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    val maxV = max(1, grid.maxOf { row -> row.max() })
    BoxWithConstraints(modifier) {
        val gap = 3.dp
        val labelW = 26.dp
        val cell = (maxWidth - labelW - gap * 23) / 24
        val height = (cell + gap) * 7 + 22.dp
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(cell) {
                    detectTapGestures { o ->
                        val row = (o.y / (cell + gap).toPx()).toInt()
                        if (row in 0..6) onDay(row)
                    }
                }
        ) {
            val g = gap.toPx()
            val lw = labelW.toPx()
            val c = cell.toPx()
            val accent = TagColors.patch(1)
            val label = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = TagColors.Outline)
            for (r in 0 until 7) {
                val y = r * (c + g)
                val t = measurer.measure(DayLabels[r], label)
                drawText(t, topLeft = Offset(0f, y + (c - t.size.height) / 2f))
                for (h in 0 until 24) {
                    val v = grid[r][h]
                    val intensity = if (v == 0) 0f else (v / maxV.toFloat()).coerceIn(0f, 1f)
                    val base = 0.09f + 0.91f * Math.pow(intensity.toDouble(), 0.7).toFloat()
                    val alpha = if (r in selected) base else base * 0.4f
                    drawRoundRect(
                        color = if (v == 0) TagColors.White.copy(alpha = if (r in selected) 0.10f else 0.05f) else accent.copy(alpha = alpha),
                        topLeft = Offset(lw + h * (c + g), y),
                        size = androidx.compose.ui.geometry.Size(c, c),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                    )
                }
            }
            for (h in listOf(0, 6, 12, 18)) {
                val t = measurer.measure("%02d".format(h), label)
                drawText(t, topLeft = Offset(lw + h * (c + g), 7 * (c + g) + 4.dp.toPx()))
            }
        }
    }
}

@Composable
private fun TimeOfDay(values: IntArray) {
    val buckets = listOf("NIGHT" to 0..5, "MORNING" to 6..11, "AFTERNOON" to 12..17, "EVENING" to 18..23)
    val counts = buckets.map { (_, range) -> range.sumOf { values[it] } }
    val total = counts.sum()
    if (total == 0) {
        Text(
            "No activity on these days yet.",
            style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TagColors.Outline)
        )
        return
    }
    val alphas = listOf(0.35f, 0.6f, 1f, 0.8f)
    val visible = counts.indices.filter { counts[it] > 0 }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(38.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            visible.forEachIndexed { i, idx ->
                val big = CornerSize(14.dp)
                val small = CornerSize(4.dp)
                val shape = RoundedCornerShape(
                    topStart = if (i == 0) big else small, bottomStart = if (i == 0) big else small,
                    topEnd = if (i == visible.lastIndex) big else small, bottomEnd = if (i == visible.lastIndex) big else small
                )
                Box(
                    Modifier
                        .weight(counts[idx].toFloat())
                        .fillMaxHeight()
                        .clip(shape)
                        .background(TagColors.series(idx, alphas[idx]))
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            buckets.forEachIndexed { i, (name, _) ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(TagColors.series(i, alphas[i])))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            name,
                            style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp, color = TagColors.Outline)
                        )
                    }
                    Text(
                        "${counts[i] * 100 / total}%",
                        style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Black, fontSize = 18.sp, color = TagColors.White)
                    )
                }
            }
        }
    }
}

@Composable
private fun ByType(sessions: List<Session>) {
    val rows = remember(sessions) {
        sessions.groupingBy { it.path }.eachCount().toList().sortedByDescending { it.second }.take(5)
    }
    if (rows.isEmpty()) return
    val maxN = rows.maxOf { it.second }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("BY TYPE", 3)
        rows.forEach { (path, n) ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        path.replace('_', ' ').replace('-', ' ').replaceFirstChar { it.uppercase() },
                        maxLines = 1, modifier = Modifier.weight(1f),
                        style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = TagColors.White)
                    )
                    Text(
                        n.toString(),
                        style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TagColors.White)
                    )
                }
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth(n / maxN.toFloat()).height(4.dp).clip(CircleShape).background(TagColors.White))
            }
        }
    }
}

@Composable
private fun Sources(rows: List<Pair<String, Int>>) {
    if (rows.isEmpty()) {
        Text(
            "Nothing logged on these days yet. Promotions show up here as they happen.",
            style = TextStyle(
                fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                lineHeight = 20.sp, color = TagColors.Outline
            )
        )
        return
    }
    val maxN = rows.maxOf { it.second }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("TOP SOURCES", 4)
        Text(
            "One notification is one interception, however many times it updates.",
            style = TextStyle(
                fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 12.sp,
                lineHeight = 16.sp, color = TagColors.Outline
            )
        )
        rows.forEachIndexed { rank, (label, n) ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label, maxLines = 1, modifier = Modifier.weight(1f),
                        style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = TagColors.White)
                    )
                    Text(
                        n.toString(),
                        style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TagColors.White)
                    )
                }
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth(n / maxN.toFloat()).height(4.dp).clip(CircleShape).background(TagColors.patch(rank)))
            }
        }
    }
}
