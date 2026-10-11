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
import com.microtag.core.MicrotagLog
import com.microtag.core.MicrotagPrefs
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

// ==========================================================
// DATA
// Built from the promotion log. The log keeps the newest 500 entries and each entry is one
// promotion (repeats collapse into it), so this is a recent-activity view rather than lifetime stats.
// ==========================================================

private class StatsData(
    val grid: Array<IntArray>,                 // [day Mon..Sun][hour 0..23]
    val perPackage: Map<String, IntArray>      // package -> counts per day
)

private fun dayIndexOf(ms: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7   // Monday = 0 ... Sunday = 6
}

private fun loadStats(): StatsData {
    val grid = Array(7) { IntArray(24) }
    val perPackage = HashMap<String, IntArray>()
    val c = Calendar.getInstance()
    MicrotagLog.snapshot()
        .filter { it.decision == MicrotagLog.Decision.PROMOTED }
        .forEach { e ->
            c.timeInMillis = e.timeMs
            val day = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
            grid[day][c.get(Calendar.HOUR_OF_DAY)]++
            perPackage.getOrPut(e.pkg) { IntArray(7) }[day]++
        }
    return StatsData(grid, perPackage)
}

private val DayLabels = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

@Composable
fun StatsScreen(prefs: MicrotagPrefs, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val data = remember { loadStats() }
    val today = remember { dayIndexOf(System.currentTimeMillis()) }
    val nowHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    var selected by remember { mutableStateOf(setOf(today)) }

    val values = remember(selected) { IntArray(24) { h -> selected.sumOf { d -> data.grid[d][h] } } }
    val total = values.sum()
    val peakHour = values.indices.maxByOrNull { values[it] }?.takeIf { values[it] > 0 }

    Column(Modifier.fillMaxSize().background(TagColors.Black).statusBarsPadding()) {
        BackBar(onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FitText(
                AnnotatedString("Interceptions"),
                TextStyle(
                    fontFamily = Montserrat, fontWeight = FontWeight.Black, fontSize = 48.sp,
                    lineHeight = 52.sp, letterSpacing = (-1.2).sp, color = TagColors.White
                ),
                Modifier.fillMaxWidth()
            )

            DayChips(selected, today) { day ->
                // Always keep at least one day on.
                selected = if (day in selected) (if (selected.size > 1) selected - day else selected) else selected + day
            }

            HourChart(
                values = values,
                nowHour = if (today in selected) nowHour else null,
                dashFuture = selected == setOf(today),
                modifier = Modifier.fillMaxWidth().height(260.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("SELECTED", total.toString(), Modifier.weight(1f))
                StatTile("PEAK HOUR", peakHour?.let { "%02d:00".format(it) } ?: "--", Modifier.weight(1f))
                StatTile("ALL TIME", prefs.interceptCount.toString(), Modifier.weight(1f))
            }

            Sources(data, selected)
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ==========================================================
// DAY CHIPS
// ==========================================================

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
                // A small dot marks today.
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

// ==========================================================
// CHART
// Vertical hour lines, a smooth curve through every hour, a hatched band on the current hour,
// a dashed curve for hours still to come, and a drag-to-scrub readout with detent haptics.
// ==========================================================

@Composable
private fun HourChart(values: IntArray, nowHour: Int?, dashFuture: Boolean, modifier: Modifier) {
    val view = LocalView.current
    val measurer = rememberTextMeasurer()
    val anim = remember { List(24) { Animatable(0f) } }
    var scrub by remember { mutableIntStateOf(-1) }

    // Each hour springs to its new value, so switching days morphs the curve.
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

        // hour lines and labels, every four hours
        for (t in 0..24 step 4) {
            val x = padL + t / 24f * cw
            drawLine(TagColors.White.copy(alpha = 0.28f), Offset(x, top - 8.dp.toPx()), Offset(x, bottom), 1.5.dp.toPx())
            if (t < 24) {
                val text = measurer.measure("%02d:00".format(t), labelStyle)
                drawText(text, topLeft = Offset(x - text.size.width / 2f, bottom + 8.dp.toPx()))
            }
        }

        // hatched band on the current hour
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

        // the curve: Catmull-Rom turned into cubic segments
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

        // the callout: the peak by default, the touched hour while scrubbing
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

// ==========================================================
// SUMMARY
// ==========================================================

@Composable
private fun StatTile(caption: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(TagShapes.Rect)
            .border(2.dp, TagColors.Outline, TagShapes.Rect)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            caption,
            style = TextStyle(
                fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                letterSpacing = 1.sp, color = TagColors.Outline
            )
        )
        Spacer(Modifier.height(4.dp))
        FitText(
            AnnotatedString(value),
            TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Black, fontSize = 26.sp, color = TagColors.White)
        )
    }
}

@Composable
private fun Sources(data: StatsData, selected: Set<Int>) {
    val context = LocalContext.current
    val pm = context.packageManager
    val rows = remember(selected) {
        data.perPackage
            .map { (pkg, days) -> pkg to selected.sumOf { days[it] } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(5)
            .map { (pkg, n) ->
                val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                    .getOrDefault(pkg.substringAfterLast('.'))
                Triple(label, n, pkg)
            }
    }
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
        Text(
            "TOP SOURCES",
            style = TextStyle(
                fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                letterSpacing = 1.2.sp, color = TagColors.Lime
            )
        )
        rows.forEach { (label, n, _) ->
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
                Box(
                    Modifier
                        .fillMaxWidth(n / maxN.toFloat())
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(TagColors.Lime)
                )
            }
        }
    }
}
