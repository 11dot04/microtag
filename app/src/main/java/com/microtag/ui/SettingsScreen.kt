package com.microtag.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.R

@Composable
fun SettingsScreen(onBack: () -> Unit, onReplayIntro: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val settings = remember { TagSettings(context) }
    val perms = rememberPermissions()

    var materialYou by remember { mutableStateOf(settings.materialYou) }
    var reducedMotion by remember { mutableStateOf(settings.reducedMotion) }
    var haptics by remember { mutableStateOf(settings.haptics) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "dev"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(TagColors.Black)
            .statusBarsPadding()
    ) {
        BackBar(onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FitText(
                AnnotatedString("Settings"),
                TextStyle(
                    fontFamily = Montserrat, fontWeight = FontWeight.Black, fontSize = 56.sp,
                    lineHeight = 60.sp, letterSpacing = (-1.5).sp, color = TagColors.White
                ),
                Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))

            Section("Permissions")
            SettingRow(
                "Notification listener", "Lets TAG see notifications. Opens system settings.",
                checked = perms.hasListener, onChange = { perms.openListenerSettings() }
            )
            SettingRow(
                "Post notifications", "Lets TAG show its chips.",
                checked = perms.hasNotif, onChange = { perms.requestNotifications() }
            )
            SettingRow(
                "Calendar", "For the events countdown.",
                checked = perms.hasCalendar, onChange = { perms.requestCalendar() }
            )
            SettingRow(
                "Shizuku", "Optional. Clipboard access in the background.",
                checked = perms.hasShizuku, onChange = { perms.requestShizuku() }
            )

            Spacer(Modifier.height(8.dp))
            Section("Look and feel")
            SettingRow(
                "Material You", "Swap the lime for your system accent colour.",
                checked = materialYou,
                onChange = {
                    materialYou = it
                    settings.materialYou = it
                    TagColors.useMaterialYou(context, it)
                }
            )
            SettingRow(
                "Reduce motion", "Skip the springs, flood fills and entrances.",
                checked = reducedMotion,
                onChange = {
                    reducedMotion = it
                    settings.reducedMotion = it
                    TagMotion.enabled = !it
                }
            )
            SettingRow(
                "Haptics", "Taps, toggles and dial detents.",
                checked = haptics,
                onChange = {
                    haptics = it
                    settings.haptics = it
                    TagMotion.haptics = it
                }
            )

            Spacer(Modifier.height(8.dp))
            Section("Intro")
            ActionRow("Replay intro", "Walk through the welcome screens again.", onReplayIntro)

            Spacer(Modifier.height(8.dp))
            Section("About")
            Text(
                "TAG  ·  version $version",
                style = TextStyle(
                    fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                    color = TagColors.Outline
                ),
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Section(label: String) {
    Text(
        label.uppercase(),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        style = TextStyle(
            fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            letterSpacing = 1.2.sp, color = TagColors.Lime
        )
    )
}

@Composable
private fun RowShell(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(TagShapes.Rect)
            .border(2.dp, TagColors.Outline, TagShapes.Rect)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) { content() }
}

@Composable
private fun RowText(title: String, sub: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            title,
            style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TagColors.White)
        )
        Text(
            sub,
            style = TextStyle(
                fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                lineHeight = 18.sp, color = TagColors.Outline
            )
        )
    }
}

@Composable
private fun SettingRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    RowShell {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowText(title, sub, Modifier.weight(1f).padding(end = 14.dp))
            TagSwitch(checked = checked, onChange = onChange)
        }
    }
}

@Composable
private fun ActionRow(title: String, sub: String, onClick: () -> Unit) {
    val view = LocalView.current
    RowShell(Modifier.clickable { view.tick(Tick.Press); onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowText(title, sub, Modifier.weight(1f).padding(end = 14.dp))
            TagIcon(R.drawable.ic_chevron_right, TagColors.White, size = 24.dp)
        }
    }
}
