package com.microtag.ui

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.em
import androidx.core.content.res.ResourcesCompat
import kotlin.math.roundToInt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import com.microtag.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import com.microtag.core.MicrotagPrefs
import com.microtag.core.MicrotagReminder
import com.microtag.listener.BluetoothWatcher
import com.microtag.listener.MicrotagLogActivity

// ==========================================================
// STATE
// ==========================================================

/** One on/off tile backed by MicrotagPrefs. */
@Stable
class PrefToggle(private val prefs: MicrotagPrefs, private val key: String) {
    var on by mutableStateOf(prefs.isAppEnabled(key))
        private set

    fun set(value: Boolean) {
        on = value
        prefs.setAppEnabled(key, value)
    }

    fun toggle() = set(!on)
    val state: TileState get() = if (on) TileState.On else TileState.Off
}

@Composable
fun rememberToggle(prefs: MicrotagPrefs, key: String): PrefToggle = remember(key) { PrefToggle(prefs, key) }

private enum class DashSheet { Streaks, Calendar }

// ==========================================================
// DASHBOARD
// Grid: 4 columns. 1x1 and 2x2 are squares, 2x1 is two cells wide.
// Every tile is placed by (col, row). Frames sit in the gutter.
// ==========================================================

@Composable
fun DashboardScreen(
    prefs: MicrotagPrefs,
    onOpenInspect: () -> Unit,
    onOpenPin: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenLogs: () -> Unit,
    onBackdropChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val settings = remember { TagSettings(context) }
    var count by remember { mutableIntStateOf(prefs.interceptCount) }
    // Pick up interceptions as they happen, so the numeral rolls and the dot field ripples live.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            count = prefs.interceptCount
        }
    }

    val discord = rememberToggle(prefs, MicrotagPrefs.KEY_DISCORD)
    val strava = rememberToggle(prefs, MicrotagPrefs.KEY_STRAVA)
    val streaks = rememberToggle(prefs, TagKeys.STREAKS)
    val weather = rememberToggle(prefs, TagKeys.WEATHER)
    val upi = rememberToggle(prefs, TagKeys.UPI)
    val otp = rememberToggle(prefs, MicrotagPrefs.KEY_OTP)
    val print = rememberToggle(prefs, TagKeys.PRINT)
    val download = rememberToggle(prefs, MicrotagPrefs.KEY_DOWNLOADS)
    val upload = rememberToggle(prefs, TagKeys.UPLOADS)
    val clipboard = rememberToggle(prefs, MicrotagPrefs.KEY_CLIPBOARD)
    val calendar = rememberToggle(prefs, TagKeys.CALENDAR)
    val bluetooth = rememberToggle(prefs, TagKeys.BLUETOOTH)
    val sound = rememberToggle(prefs, TagKeys.SOUND_MODE)
    val songNext = rememberToggle(prefs, TagKeys.SONG_NEXT)
    val ambient = rememberToggle(prefs, TagKeys.AMBIENT_MUSIC)
    val proton = rememberToggle(prefs, MicrotagPrefs.KEY_PROTON)

    var paused by remember { mutableStateOf(settings.paused) }
    // Pausing dims the tile section with a scrim that runs edge to edge, so the group frames in the gutter dim too.
    val dimAlpha by animateFloatAsState(
        if (paused) 0.62f else 0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "pausedDim"
    )
    var weatherOpen by remember { mutableStateOf(false) }
    var stravaOpen by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<DashSheet?>(null) }
    var weatherIdx by remember {
        mutableIntStateOf(WeatherSteps.indexOf(settings.weatherIntervalMin).coerceAtLeast(0))
    }
    var stravaMetric by remember { mutableStateOf(settings.stravaMetric) }
    var ringerModes by remember { mutableStateOf(settings.ringerModes) }
    val intervalLabels = remember { WeatherSteps.map(::intervalLabel) }

    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) calendar.set(true)
    }

    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) bluetooth.set(true)
    }

    val perms = rememberPermissions()
    val scroll = rememberScrollState()
    val hourly = remember(count) { todayHourCounts() }
    val togglePause: () -> Unit = {
        paused = !paused
        settings.paused = paused
        // Coming back on posts the same Live Update the old simulate button did.
        if (!paused) {
            MicrotagReminder.showCapsule(
                context = context,
                pillText = "TAG on",
                title = "TAG is back",
                content = "Interceptions resumed",
                notificationId = 9999,
                timeoutSeconds = 10,
                iconName = "ic_capsule_bolt"
            )
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(TagColors.Black)) {
        // The poster fills the screen except for a strip of tiles that peeks out below it.
        val posterH = (maxHeight - 150.dp).coerceAtLeast(520.dp)
        val density = LocalDensity.current
        val view = LocalView.current
        val posterPx = with(density) { posterH.toPx() }
        // The poster is fully collapsed when its bottom edge sits exactly under the dock bar.
        val dockBarPx = with(density) { (WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 48.dp).toPx() }
        val collapse = (posterPx - dockBarPx).coerceAtLeast(1f)
        val posterVisible by remember { derivedStateOf { scroll.value < posterPx } }

        // Detent: never rest half-collapsed. Past 40% it settles collapsed, before that it bounces back open.
        //
        // This used to work once and then stop. A finger release ends the drag a beat before the fling
        // starts, so the settle animation could be interrupted by that fling (or by the next touch).
        // An interrupted animateScrollTo throws a CancellationException, and thrown inside collect it
        // silently ended this whole effect, so no later scroll was ever settled. Now the settle waits
        // for the fling to claim the scroll, and an interruption only ends that one animation.
        LaunchedEffect(collapse) {
            snapshotFlow { scroll.isScrollInProgress }.collect { scrolling ->
                if (scrolling) return@collect
                delay(80)
                if (scroll.isScrollInProgress) return@collect   // the fling took over; settle when it ends
                val v = scroll.value
                val end = collapse.toInt()
                if (v in 1 until end) {
                    view.tick(Tick.Detent)
                    try {
                        scroll.animateScrollTo(
                            if (v < collapse * 0.4f) 0 else end,
                            spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                        )
                    } catch (e: CancellationException) {
                        // Interrupted by a new touch or fling: end this settle only. If the effect itself
                        // was cancelled (leaving the screen), this rethrows.
                        ensureActive()
                    }
                }
            }
        }

        // The poster is the page itself now (no accent slab), so the status bar icons follow the palette's base.
        val lightBackdrop = TagColors.IsLight
        LaunchedEffect(lightBackdrop) { onBackdropChange(lightBackdrop) }

      // No header band. As the poster docks, the content itself fades out under the status bar and the
      // numeral, so there is no solid strip and no edge where the header ends and the tiles begin.
      val fadePx = with(density) { 72.dp.toPx() }
      Column(
          Modifier
              .fillMaxSize()
              .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
              .drawWithContent {
                  drawContent()
                  val p = (scroll.value / collapse).coerceIn(0f, 1f)
                  if (p > 0f) {
                      val end = dockBarPx + fadePx
                      val hidden = Color.Black.copy(alpha = 1f - p)
                      drawRect(
                          Brush.verticalGradient(
                              colorStops = arrayOf(0f to hidden, (dockBarPx * 0.8f / end) to hidden, 1f to Color.Black),
                              startY = 0f, endY = end
                          ),
                          blendMode = BlendMode.DstIn
                      )
                  }
              }
              .verticalScroll(scroll)
      ) {
        MonolithPoster(
            height = posterH,
            count = count,
            hourly = hourly,
            paused = paused,
            perms = perms,
            onTogglePause = togglePause,
            onStats = onOpenStats,
            onLogs = onOpenLogs,
            active = posterVisible
        )
        Box {
        Column(Modifier.padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))

        // p glides 0 -> 1 as Weather opens. Everything below it follows the same number,
        // so the whole column moves as one piece instead of snapping.
        val p by animateFloatAsState(
            targetValue = if (weatherOpen) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "weatherOpen"
        )
        // Streaks drops a row first, then stretches sideways once it is clear of Strava.
        val widen = ((p - 0.85f) / 0.15f).coerceIn(0f, 1f)
        val b = 4f + p       // first Micro Tags row
        val b2 = b + 3f      // earbuds / sound mode
        val b3 = b2 + 2f     // music / VPN

        // Tones: 0 accent, 1 secondary, 2 tertiary. Neighbouring tiles never share one, so a palette with
        // three accents reads as a patchwork; a single-accent palette (Lime) looks exactly as before.
        TagGrid {
            // ---- rows 0-1: Discord + Inspect
            TagFrame("Inspect", TagColors.tone(2), Modifier.cell(2, 0, 2, 2, outset = 6.dp))
            TileTone(0) { BigTile(Modifier.cell(0, 0, 2, 2), R.drawable.ic_discord, "Discord", "CALLS", discord) }
            InspectTile(Modifier.cell(2, 0), R.drawable.ic_inspect_calc, onOpenInspect)
            InspectTile(Modifier.cell(3, 0), R.drawable.ic_inspect_colour, onOpenInspect)
            InspectTile(Modifier.cell(2, 1), R.drawable.ic_inspect_convert, onOpenInspect)
            InspectTile(Modifier.cell(3, 1), R.drawable.ic_inspect_define, onOpenInspect)

            // ---- weather (top) / streaks (under it) / strava
            TileTone(2) {
                WeatherTile(
                    Modifier.cell(0f, 2f, 2f, 1f + p),
                    // Pill when closed, settles to the standard tile radius as it opens.
                    RoundedCornerShape((100f + (26f - 100f) * p.coerceIn(0f, 1f)).dp),
                    weather, weatherOpen, weatherIdx, intervalLabels,
                    onIndex = { weatherIdx = it; settings.weatherIntervalMin = WeatherSteps[it] },
                    onOpenChange = { weatherOpen = it }
                )
            }
            TileTone(0) {
                PillTile(
                    Modifier.cell(0f, 3f + p, 2f + 2f * widen, 1f), null, "Streaks", null, streaks,
                    chevron = true, onChevron = { sheet = DashSheet.Streaks }
                )
            }
            TileTone(1) {
                StravaTile(
                    Modifier.cell(2, 2, 2, 2), strava, stravaOpen, { stravaOpen = !stravaOpen },
                    stravaMetric, { stravaMetric = it; settings.stravaMetric = it }
                )
            }

            // ---- Micro Tags: small, single-purpose chips
            TagFrame("Micro Tags", TagColors.Outline, Modifier.cell(0f, b, 4f, 3f, outset = 6.dp), labelBias = 0.55f)
            TileTone(1) { MicroTile(Modifier.cell(0f, b, 2f, 1f), R.drawable.ic_upi_rupee, "UPI", "Credit", upi) }
            TileTone(1, always = true) { IconTile(Modifier.cell(2f, b), R.drawable.ic_download, download) }
            TileTone(1, always = true) { IconTile(Modifier.cell(3f, b), R.drawable.ic_upload, upload) }
            TileTone(2) { MicroTile(Modifier.cell(0f, b + 1f, 2f, 1f), R.drawable.ic_otp, "OTP", "2FA", otp) }
            TileTone(1) { MicroTile(Modifier.cell(2f, b + 1f, 2f, 1f), R.drawable.ic_print, "Print", "in\nProgress", print, inline = true) }
            TileTone(0) { MicroTile(Modifier.cell(0f, b + 2f, 2f, 1f), R.drawable.ic_clipboard, "Clipboard", "Context", clipboard) }
            TileTone(2) {
                PillTile(
                    Modifier.cell(2f, b + 2f, 2f, 1f), R.drawable.ic_calendar_countdown, "Events", null, calendar,
                    shape = TagShapes.Rect, chevron = true,
                    onToggle = {
                        if (!calendar.on && !hasCalendarPermission(context)) {
                            calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                        } else calendar.toggle()
                    },
                    onChevron = { sheet = DashSheet.Calendar }
                )
            }

            // ---- earbuds + sound mode
            TileTone(1) {
                TagTile(
                    Modifier.cell(0f, b2, 2f, 2f), TagShapes.Circle, bluetooth.state,
                    onClick = {
                        if (!bluetooth.on && !BluetoothWatcher.hasPermission(context)) {
                            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        } else bluetooth.toggle()
                    }
                ) { fg ->
                    TagIcon(R.drawable.ic_earbuds, fg, Modifier.align(Alignment.Center), 84.dp)
                }
            }
            TileTone(0) { PillTile(Modifier.cell(2f, b2, 2f, 1f), R.drawable.ic_sound, "Sound", "Mode", sound) }
            RingerSelector(
                Modifier.cell(2f, b2 + 1f, 2f, 1f), enabled = sound.on, modes = ringerModes,
                onChange = { ringerModes = it; settings.ringerModes = it }
            )

            // ---- music pair (one interlocked shape) + VPN
            TileTone(2) { MusicPair(Modifier.cell(0f, b3, 2f, 2f), songNext, ambient) }
            TileTone(1) { BigTile(Modifier.cell(2f, b3, 2f, 2f), R.drawable.ic_vpn, "ProtonVPN", "SESSIONS", proton) }

            // ---- Pin: opens its own screen. Not a toggle; pinning is something you do, not a source.
            TileTone(0) { PinTile(Modifier.cell(0f, b3 + 2f, 4f, 1f), onOpenPin) }
        }
        Spacer(Modifier.height(40.dp))
        }
        // Pause scrim: matches the whole section, margins and gutters included.
        Box(Modifier.matchParentSize().graphicsLayer { alpha = dimAlpha }.background(TagColors.Black))
        }
        EasterEgg()
      }

      MonolithOverlay(
          count = count,
          paused = paused,
          scroll = scroll,
          posterHeight = posterH,
          collapseDistance = collapse,
          onStats = onOpenStats,
          onSettings = onOpenSettings,
          onTogglePause = togglePause
      )
    }

    when (sheet) {
        DashSheet.Streaks -> StreaksSheet(settings) { sheet = null }
        DashSheet.Calendar -> CalendarSheet(settings) { sheet = null }
        null -> Unit
    }
}

// ==========================================================
// HEADER + HERO
// ==========================================================

/** Shown while the listener or notification permission is off, because TAG does nothing without them. */
@Composable
internal fun PermissionBanner(perms: Permissions) {
    val noListener = !perms.hasListener
    val title = if (noListener) "I can’t see a thing" else "I can’t speak up"
    val sub = if (noListener) "Notification access is off. Tap to turn it on." else "Allow notifications so my chips can show."
    TagTile(
        Modifier.fillMaxWidth().height(84.dp),
        TagShapes.Rect,
        TileState.Signal,
        onClick = { if (noListener) perms.openListenerSettings() else perms.requestNotifications() }
    ) { fg ->
        Row(
            Modifier.fillMaxSize().padding(start = 20.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    title,
                    style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = fg)
                )
                Text(
                    sub, maxLines = 2,
                    style = TextStyle(
                        fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                        lineHeight = 17.sp, color = fg
                    )
                )
            }
            TagIcon(R.drawable.ic_chevron_right, fg, size = 26.dp)
        }
    }
}

// ==========================================================
// TILES
// ==========================================================

@Composable
private fun BigTile(modifier: Modifier, icon: Int, title: String, sub: String, t: PrefToggle) {
    TagTile(modifier, TagShapes.Big, t.state, t::toggle) { fg ->
        TagIcon(icon, fg, Modifier.align(Alignment.TopStart).padding(20.dp), 32.dp)
        MicroStatus(t.on, fg.copy(alpha = 0.7f), Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 24.dp))
        BigLabel(title, sub, fg, Modifier.align(Alignment.BottomEnd).padding(20.dp))
    }
}

@Composable
private fun BigLabel(title: String, sub: String, fg: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        FitText(
            AnnotatedString(title),
            TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = fg)
        )
        FitText(
            AnnotatedString(sub),
            TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.3.sp, color = fg)
        )
    }
}

/** Label left, icon right. The Micro Tags look. */
@Composable
private fun MicroTile(
    modifier: Modifier, icon: Int, bold: String, light: String?, t: PrefToggle,
    inline: Boolean = false, size: TextUnit = 17.sp
) {
    TagTile(modifier, TagShapes.Rect, t.state, t::toggle) { fg ->
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            TagLabel(bold, light, fg, Modifier.weight(1f), size, inline = inline)
            TagIcon(icon, fg, size = 28.dp)
        }
    }
}

/** Opens the Pin screen. */
@Composable
private fun PinTile(modifier: Modifier, onClick: () -> Unit) {
    TagTile(modifier, TagShapes.Pill, TileState.Static, onClick) { fg ->
        Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            TagIcon(R.drawable.ic_pin, fg, size = 28.dp)
            Spacer(Modifier.width(10.dp))
            TagLabel("Pin", "Keep a code up", fg, Modifier.weight(1f))
        }
    }
}

@Composable
private fun IconTile(modifier: Modifier, icon: Int, t: PrefToggle) {
    TagTile(modifier, TagShapes.Rect, t.state, t::toggle) { fg ->
        TagIcon(icon, fg, Modifier.align(Alignment.Center), 30.dp)
    }
}

@Composable
private fun InspectTile(modifier: Modifier, icon: Int, onClick: () -> Unit) {
    TagTile(modifier, TagShapes.Rect, TileState.Static, onClick) { fg ->
        TagIcon(icon, fg, Modifier.align(Alignment.Center), 32.dp)
    }
}

/** Optional icon, label, optional chevron. Tapping the body toggles; tapping the chevron opens options. */
@Composable
private fun PillTile(
    modifier: Modifier, icon: Int?, bold: String, light: String?, t: PrefToggle,
    shape: androidx.compose.ui.graphics.Shape = TagShapes.Pill,
    size: TextUnit = 17.sp,
    chevron: Boolean = false,
    onToggle: () -> Unit = t::toggle,
    onChevron: () -> Unit = {}
) {
    TagTile(modifier, shape, t.state, onToggle) { fg ->
        Row(
            Modifier.fillMaxSize().padding(start = if (icon == null) 26.dp else 16.dp, end = if (chevron) 6.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                TagIcon(icon, fg, size = 28.dp)
                Spacer(Modifier.width(10.dp))
            }
            TagLabel(bold, light, fg, Modifier.weight(1f), size)
            if (chevron) ChevronButton(false, fg, onClick = onChevron)
        }
    }
}

/** One tile for both states. Its bounds glide; only the content swaps. */
@Composable
private fun WeatherTile(
    modifier: Modifier, shape: androidx.compose.ui.graphics.Shape, t: PrefToggle, open: Boolean, index: Int, labels: List<String>,
    onIndex: (Int) -> Unit, onOpenChange: (Boolean) -> Unit
) {
    TagTile(modifier, shape, t.state, t::toggle) { fg ->
        val face = if (t.on) TagColors.tone(LocalTileTone.current) else TagColors.Black
        AnimatedContent(
            targetState = open,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { fadeIn(tween(200, delayMillis = 80)) togetherWith fadeOut(tween(80)) },
            label = "weatherContent"
        ) { isOpen ->
            if (!isOpen) {
                Row(
                    Modifier.fillMaxSize().padding(start = 26.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TagLabel("Weather", null, fg, Modifier.weight(1f))
                    ChevronButton(false, fg) { onOpenChange(true) }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(start = 18.dp, top = 2.dp, bottom = 12.dp, end = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TagLabel("Weather", null, fg, Modifier.weight(1f), 15.sp)
                        ChevronButton(true, fg) { onOpenChange(false) }
                    }
                    // The dial is the content: as large as the tile allows.
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TagDial(labels, index, onIndex, body = fg, face = face, modifier = Modifier.fillMaxHeight().aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StravaTile(
    modifier: Modifier, t: PrefToggle, open: Boolean, onOpen: () -> Unit,
    metric: String, onMetric: (String) -> Unit
) {
    TagTile(modifier, TagShapes.Big, t.state, t::toggle) { fg ->
        val face = if (t.on) TagColors.tone(LocalTileTone.current) else TagColors.Black
        if (!open) {
            TagIcon(R.drawable.ic_strava, fg, Modifier.align(Alignment.TopStart).padding(20.dp), 32.dp)
            MicroStatus(t.on, fg.copy(alpha = 0.7f), Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 24.dp))
            BigLabel("Strava", "WORKOUTS", fg, Modifier.align(Alignment.BottomEnd).padding(20.dp))
        } else {
            // Heading and chevron share one row, and the chips split whatever height is left,
            // so nothing can overlap or run off the tile at any screen size.
            Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 14.dp, bottom = 14.dp)) {
                Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.CenterStart) {
                    TagLabel("Chip", "shows", fg, Modifier.padding(end = 40.dp), 14.sp, inline = true)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    val view = LocalView.current
                    listOf("pace" to "Pace", "time" to "Time", "distance" to "Distance").forEach { (key, label) ->
                        val selected = key == metric
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(CircleShape)
                                .background(if (selected) fg else Color.Transparent)
                                .border(2.dp, fg, CircleShape)
                                .clickable { view.tick(Tick.Press); onMetric(key) },
                            contentAlignment = Alignment.Center
                        ) {
                            FitText(
                                AnnotatedString(label),
                                TextStyle(
                                    fontFamily = Montserrat, fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp, color = if (selected) face else fg
                                ),
                                Modifier.padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            )
                        }
                    }
                }
            }
        }
        ChevronButton(open, fg, Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp), onClick = onOpen)
    }
}

/** Song Up Next and Ambient Music Mod: two tiles that meet with no gap and read as one shape. */
@Composable
private fun MusicPair(modifier: Modifier, song: PrefToggle, ambient: PrefToggle) {
    Column(modifier) {
        TagTile(Modifier.weight(1f).fillMaxWidth(), TagShapes.CapRight, song.state, song::toggle) { fg ->
            Row(Modifier.fillMaxSize().padding(start = 22.dp, end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                TagLabel("Song", "Up Next", fg, Modifier.weight(1f), size = 16.sp)
                TagIcon(R.drawable.ic_song_next, fg, size = 28.dp)
            }
        }
        TagTile(Modifier.weight(1f).fillMaxWidth(), TagShapes.CapLeft, ambient.state, ambient::toggle) { fg ->
            Row(Modifier.fillMaxSize().padding(start = 24.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                TagIcon(R.drawable.ic_ambient_music, fg, size = 28.dp)
                Spacer(Modifier.width(12.dp))
                TagLabel("Ambient", "Music Mod", fg, Modifier.weight(1f), size = 16.sp)
            }
        }
    }
}

/**
 * Doubles as the "modes to show" picker for Sound Mode.
 * A dot is a mode that stays quiet; a lime glyph is a mode that announces itself.
 */
@Composable
private fun RingerSelector(modifier: Modifier, enabled: Boolean, modes: Set<String>, onChange: (Set<String>) -> Unit) {
    val view = LocalView.current
    TagTile(modifier, TagShapes.Pill, TileState.Off) { fg ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 8.dp).graphicsLayer { alpha = if (enabled) 1f else 0.4f }
        ) {
            listOf(
                "ring" to R.drawable.ic_ringer_ring,
                "vibrate" to R.drawable.ic_ringer_vibrate,
                "silent" to R.drawable.ic_ringer_silent
            ).forEach { (key, icon) ->
                val on = key in modes
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable { view.tick(if (on) Tick.Off else Tick.On); onChange(if (on) modes - key else modes + key) },
                    contentAlignment = Alignment.Center
                ) {
                    if (on) TagIcon(icon, TagColors.Lime, size = 28.dp)
                    else Box(Modifier.size(10.dp).clip(CircleShape).background(fg))
                }
            }
        }
    }
}
