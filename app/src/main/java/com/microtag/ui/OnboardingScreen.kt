package com.microtag.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.microtag.R
import rikka.shizuku.Shizuku

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasListener by remember { mutableStateOf(checkListenerPermission(context)) }
    var hasNotif by remember { mutableStateOf(checkPostNotifPermission(context)) }
    var hasShizuku by remember { mutableStateOf(checkShizukuPermission()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasListener = checkListenerPermission(context)
                hasNotif = checkPostNotifPermission(context)
                hasShizuku = checkShizukuPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasNotif = ok
        if (!ok) {
            // Denied for good: the system won't ask again, so send them to the settings page.
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // Where the camera actually is. The system reports the cutout rectangle(s); fall back to
    // top-centre, which is where almost every phone puts it.
    val density = LocalDensity.current
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    var cutout by remember { mutableStateOf<android.graphics.Rect?>(null) }
    val cx: Dp = cutout?.let { with(density) { it.exactCenterX().toDp() } } ?: (screenW / 2)
    val cy: Dp = cutout?.let { with(density) { it.exactCenterY().toDp() } } ?: 26.dp
    // A cutout on the right half means the arrow comes in from the left instead.
    val mirrored = cx > screenW / 2 + 40.dp

    // The arrow drawable is 360x207dp with its tip at (199.3, 51.7). Park the tip just
    // below and to the side of the camera.
    val arrowW = 360.dp
    val arrowH = 207.dp
    val tipX = 199.3.dp
    val tipY = 51.7.dp
    val gapX = 22.dp
    val gapY = 30.dp
    val arrowLeft = if (!mirrored) cx + gapX - tipX else cx - gapX - (arrowW - tipX)
    val arrowTop = cy + gapY - tipY
    val contentTop = arrowTop + arrowH + 12.dp

    Box(
        Modifier
            .fillMaxSize()
            .background(TagColors.Lime)
            .onGloballyPositioned {
                cutout = view.rootWindowInsets?.displayCutout?.boundingRects?.firstOrNull { r -> r.top <= 0 }
            }
    ) {
        Image(
            painter = painterResource(R.drawable.ic_onboarding_arrow),
            contentDescription = null,
            modifier = Modifier
                .size(arrowW, arrowH)
                .offset { IntOffset(arrowLeft.roundToPx(), arrowTop.roundToPx()) }
                .graphicsLayer { scaleX = if (mirrored) -1f else 1f }
        )

        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 34.dp)
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(contentTop))
                FitText(
                    text = AnnotatedString("I’ll live here"),
                    style = TextStyle(
                        fontFamily = Montserrat, fontWeight = FontWeight.Black,
                        fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-1.5).sp, color = TagColors.Black
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                )
                FitText(
                    text = AnnotatedString("if you let me"),
                    style = TextStyle(
                        fontFamily = Montserrat, fontWeight = FontWeight.Light,
                        fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp, color = TagColors.Black
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                )
                Spacer(Modifier.height(36.dp))

            TagGrid(gap = 8.dp) {
                PermissionPill(
                    Modifier.cell(0, 0, 2, 1), TagShapes.JoinLeft, hasListener,
                    "Notification", "Listener", TextAlign.End
                ) {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                PermissionPill(
                    Modifier.cell(2, 0, 2, 1), TagShapes.JoinRight, hasNotif,
                    "Post", "Notifications", TextAlign.Start
                ) { notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }

                // Shizuku is optional: it unlocks zero-click clipboard capture.
                Box(
                    Modifier
                        .cell(2, 1, 2, 1)
                        .clip(TagShapes.Pill)
                        .border(2.dp, TagColors.Black, TagShapes.Pill)
                        .clickable { requestShizuku(context) }
                        .padding(horizontal = 22.dp)
                ) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Shizuku",
                            modifier = Modifier.weight(1f),
                            style = TextStyle(
                                fontFamily = Montserrat, fontWeight = FontWeight.Bold,
                                fontSize = 17.sp, color = TagColors.Black
                            )
                        )
                        TagSwitch(
                            checked = hasShizuku,
                            onChange = { requestShizuku(context) },
                            trackOn = TagColors.Black, thumbOn = TagColors.Lime,
                            trackOff = TagColors.White, thumbOff = TagColors.Black
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        AnimatedVisibility(
            visible = hasListener,
            enter = fadeIn(tween(250)) + slideInVertically(tween(300)) { it / 2 }
        ) {
            Box(
                Modifier
                    .padding(vertical = 16.dp)
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(TagShapes.Pill)
                    .background(TagColors.Black)
                    .clickable { view.tick(Tick.Confirm); onFinish() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Step inside",
                    style = TextStyle(
                        fontFamily = Montserrat, fontWeight = FontWeight.Black,
                        fontSize = 18.sp, color = TagColors.Lime
                    )
                )
            }
        }
        }
    }
}

private fun requestShizuku(context: android.content.Context) {
    try {
        if (Shizuku.pingBinder()) Shizuku.requestPermission(1001)
        else Toast.makeText(context, "Start Shizuku first", Toast.LENGTH_SHORT).show()
    } catch (_: Throwable) {
        Toast.makeText(context, "Shizuku isn't installed", Toast.LENGTH_SHORT).show()
    }
}

/** Outlined until granted, then filled black with lime type. */
@Composable
private fun PermissionPill(
    modifier: Modifier,
    shape: Shape,
    granted: Boolean,
    bold: String,
    light: String,
    align: TextAlign,
    onClick: () -> Unit
) {
    val view = LocalView.current
    Box(
        modifier
            .clip(shape)
            .background(if (granted) TagColors.Black else TagColors.Lime)
            .border(2.dp, TagColors.Black, shape)
            .clickable(enabled = !granted) { view.tick(Tick.Press); onClick() }
            .padding(horizontal = 20.dp),
        contentAlignment = if (align == TextAlign.End) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        TagLabel(
            bold, light,
            color = if (granted) TagColors.Lime else TagColors.Black,
            size = 17.sp, align = align
        )
    }
}

// ==========================================================
// PERMISSION HELPERS (unchanged from the previous MainActivity)
// ==========================================================

fun checkListenerPermission(context: android.content.Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(context.packageName)
}

fun checkPostNotifPermission(context: android.content.Context): Boolean =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else true

fun checkShizukuPermission(): Boolean = try {
    Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
} catch (_: Exception) {
    false
}
