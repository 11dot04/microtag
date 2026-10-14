package com.microtag.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Our own bottom sheet. The stock ModalBottomSheet lifts off the bottom edge when its list is
 * scrolled, which lets the dashboard show through under it. This one is bolted to the bottom,
 * its black runs under the gesture bar, and only the handle drags it away.
 */
@Composable
private fun TagSheet(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var shown by remember { mutableStateOf(false) }
    val drag = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown = true }

    fun close() {
        if (!shown) return
        shown = false
        scope.launch { delay(230); onDismiss() }
    }

    Dialog(
        onDismissRequest = ::close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            window?.isNavigationBarContrastEnforced = false
        }
        val scrim by animateFloatAsState(if (shown) 0.6f else 0f, tween(220), label = "scrim")
        val noRipple = remember { MutableInteractionSource() }
        val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.88f
        val threshold = with(density) { 110.dp.toPx() }
        val topShape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrim))
                .clickable(interactionSource = noRipple, indication = null) { close() }
        ) {
            AnimatedVisibility(
                visible = shown,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it },
                exit = slideOutVertically(tween(220)) { it }
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .offset { IntOffset(0, drag.value.roundToInt()) }
                        .clip(topShape)
                        .background(TagColors.Black)
                        .border(2.dp, TagColors.Outline, topShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                ) {
                    // Handle: the only draggable part, so the list below scrolls freely.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { d ->
                                    scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(0f)) }
                                },
                                onDragStopped = { velocity ->
                                    if (drag.value > threshold || velocity > 1800f) close()
                                    else scope.launch { drag.animateTo(0f, spring(dampingRatio = 0.7f)) }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(width = 44.dp, height = 5.dp)
                                .clip(CircleShape)
                                .background(TagColors.Outline)
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .padding(horizontal = 24.dp)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp)
                    ) {
                        TagLabel(title, subtitle, TagColors.White, size = 26.sp)
                        Spacer(Modifier.height(18.dp))
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun TagChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) TagColors.Lime else TagColors.Black)
            .border(2.dp, if (selected) TagColors.Lime else TagColors.Outline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text,
            style = TextStyle(
                fontFamily = Montserrat,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (selected) TagColors.OnLime else TagColors.White
            )
        )
    }
}

// ==========================================================
// STREAKS: which apps to watch
// ==========================================================

private class AppEntry(val label: String, val pkg: String, val icon: ImageBitmap)

@Composable
private fun rememberLaunchableApps(): State<List<AppEntry>> {
    val ctx = LocalContext.current
    return produceState<List<AppEntry>>(emptyList()) {
        value = withContext(Dispatchers.Default) {
            val pm = ctx.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
                .filter { it.activityInfo.packageName != ctx.packageName }
                .map {
                    AppEntry(
                        label = it.loadLabel(pm).toString(),
                        pkg = it.activityInfo.packageName,
                        icon = it.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
                    )
                }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
        }
    }
}

@Composable
fun StreaksSheet(settings: TagSettings, onDismiss: () -> Unit) {
    val apps by rememberLaunchableApps()
    var selected by remember { mutableStateOf(settings.streakApps) }

    TagSheet("Streaks", "pick the apps to watch", onDismiss) {
        if (apps.isEmpty()) {
            Text(
                "Loading your apps",
                style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Light, fontSize = 15.sp, color = TagColors.Outline)
            )
        }
        LazyColumn(
            Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(apps, key = { it.pkg }) { app ->
                val on = app.pkg in selected
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        bitmap = app.icon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    )
                    Text(
                        app.label,
                        modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                        maxLines = 1,
                        style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = TagColors.White)
                    )
                    TagSwitch(checked = on, onChange = { v ->
                        selected = if (v) selected + app.pkg else selected - app.pkg
                        settings.streakApps = selected
                    })
                }
            }
        }
    }
}

// ==========================================================
// CALENDAR: lead time and which calendars count
// ==========================================================

private class CalEntry(val id: String, val name: String, val account: String, val color: Int)

private fun loadCalendars(ctx: Context): List<CalEntry> = try {
    ctx.contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR
        ),
        null, null,
        "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
    )?.use { c ->
        buildList {
            while (c.moveToNext()) {
                add(CalEntry(c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getInt(3)))
            }
        }
    } ?: emptyList()
} catch (_: SecurityException) {
    emptyList()
}

private val LeadTimes = listOf(5, 15, 30, 60, 120)

fun hasCalendarPermission(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

@Composable
fun CalendarSheet(settings: TagSettings, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasCalendarPermission(ctx)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var lead by remember { mutableIntStateOf(settings.calendarLeadMin) }
    var excluded by remember { mutableStateOf(settings.calendarExcluded) }
    val calendars by produceState(emptyList<CalEntry>(), granted) {
        value = if (granted) withContext(Dispatchers.IO) { loadCalendars(ctx) } else emptyList()
    }

    TagSheet("Countdown", "starts before each event", onDismiss) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LeadTimes.forEach { m ->
                TagChip(intervalLabel(m), selected = lead == m) {
                    lead = m
                    settings.calendarLeadMin = m
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        if (!granted) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(TagColors.Lime)
                    .clickable { permission.launch(Manifest.permission.READ_CALENDAR) }
                    .padding(horizontal = 22.dp, vertical = 14.dp)
            ) {
                TagLabel("Allow calendar access", color = TagColors.OnLime, size = 15.sp)
            }
        } else {
            LazyColumn(
                Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(calendars, key = { it.id }) { cal ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(cal.color)))
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(
                                cal.name, maxLines = 1,
                                style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = TagColors.White)
                            )
                            Text(
                                cal.account, maxLines = 1,
                                style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Light, fontSize = 12.sp, color = TagColors.Outline)
                            )
                        }
                        TagSwitch(checked = cal.id !in excluded, onChange = { on ->
                            excluded = if (on) excluded - cal.id else excluded + cal.id
                            settings.calendarExcluded = excluded
                        })
                    }
                }
            }
        }
    }
}
