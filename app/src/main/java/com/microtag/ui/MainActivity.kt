package com.microtag.ui

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import com.microtag.core.MicrotagPrefs
import com.microtag.rules.RuleEngine
import com.microtag.shizuku.ShizukuBridge

private enum class Screen { Onboarding, Dashboard, Inspect, Priority, Pin, Settings, Stats, Logs }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RuleEngine.init(applicationContext)
        ShizukuBridge.init(applicationContext)

        // Apply saved look-and-feel before the first frame, so there is no lime flash.
        val settings = TagSettings(applicationContext)
        TagColors.usePalette(applicationContext, settings.palette, settings.materialYou)
        TagColors.useMulticolour(settings.multicolour)
        TagMotion.enabled = !settings.reducedMotion
        TagMotion.haptics = settings.haptics

        setContent {
            TagTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val prefs = remember { MicrotagPrefs(applicationContext) }
                    var screen by remember {
                        mutableStateOf(if (prefs.hasCompletedOnboarding) Screen.Dashboard else Screen.Onboarding)
                    }

                    // Onboarding sits on the accent colour, and so does the top of the dashboard until you scroll.
                    var dashboardLight by remember { mutableStateOf(false) }
                    val lightBackdrop = when (screen) {
                        Screen.Onboarding -> TagColors.Lime.luminance() > 0.5f
                        Screen.Dashboard -> dashboardLight
                        // Sub-screens sit on the base colour, so a light palette needs dark bar icons.
                        else -> TagColors.IsLight
                    }
                    LaunchedEffect(lightBackdrop) {
                        val clear = AndroidColor.TRANSPARENT
                        val style = if (lightBackdrop) SystemBarStyle.light(clear, clear) else SystemBarStyle.dark(clear)
                        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    }

                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = {
                            // Each screen grows out of the thing that opened it, and shrinks back into it.
                            fun origin(screen: Screen) = when (screen) {
                                Screen.Stats -> TransformOrigin(0.15f, 0.78f)      // the numeral
                                Screen.Settings -> TransformOrigin(0.93f, 0.05f)   // the gear
                                Screen.Logs -> TransformOrigin(0.30f, 0.20f)       // the controls
                                Screen.Inspect -> TransformOrigin(0.80f, 0.72f)    // the Inspect tiles
                                Screen.Priority -> TransformOrigin(0.30f, 0.30f)   // the button on Inspect
                                Screen.Pin -> TransformOrigin(0.50f, 0.92f)        // the Pin tile, at the foot
                                else -> TransformOrigin.Center
                            }
                            if (targetState == Screen.Dashboard) {
                                (fadeIn(tween(260)) + scaleIn(
                                    initialScale = 0.94f,
                                    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
                                )) togetherWith (fadeOut(tween(160)) + scaleOut(
                                    targetScale = 0.25f,
                                    transformOrigin = origin(initialState),
                                    animationSpec = tween(320, easing = TagEase.Reveal)
                                ))
                            } else {
                                (fadeIn(tween(220)) + scaleIn(
                                    initialScale = 0.25f,
                                    transformOrigin = origin(targetState),
                                    animationSpec = tween(380, easing = TagEase.Reveal)
                                )) togetherWith fadeOut(tween(160))
                            }
                        },
                        label = "ScreenSwitch"
                    ) { s ->
                        when (s) {
                            Screen.Onboarding -> OnboardingScreen(onFinish = {
                                prefs.hasCompletedOnboarding = true
                                screen = Screen.Dashboard
                            })
                            Screen.Dashboard -> DashboardScreen(
                                prefs = prefs,
                                onOpenInspect = { screen = Screen.Inspect },
                                onOpenPin = { screen = Screen.Pin },
                                onOpenSettings = { screen = Screen.Settings },
                                onOpenStats = { screen = Screen.Stats },
                                onOpenLogs = { screen = Screen.Logs },
                                onBackdropChange = { dashboardLight = it }
                            )
                            Screen.Inspect -> InspectScreen(onBack = { screen = Screen.Dashboard }, onOpenPriority = { screen = Screen.Priority })
                            Screen.Priority -> PriorityScreen(onBack = { screen = Screen.Inspect })
                            // PinScreen isn't finished yet. Until it is, this placeholder keeps the build green and the
                            // Pin tile working. When it is ready, swap this one line for
                            // PinScreen(onBack = { screen = Screen.Dashboard }) and delete PinScreenPlaceholder below.
                            Screen.Pin -> PinScreenPlaceholder(onBack = { screen = Screen.Dashboard })
                            Screen.Stats -> StatsScreen(prefs = prefs, onBack = { screen = Screen.Dashboard })
                            Screen.Logs -> LogsScreen(onBack = { screen = Screen.Dashboard })
                            Screen.Settings -> SettingsScreen(
                                onBack = { screen = Screen.Dashboard },
                                // Replaying the intro never clears the saved "done" flag.
                                onReplayIntro = { screen = Screen.Onboarding }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Stand-in for the unfinished PinScreen. */
@androidx.compose.runtime.Composable
private fun PinScreenPlaceholder(onBack: () -> Unit) {
    SubScreen(onBack = onBack) {
        androidx.compose.material3.Text(
            "Pin",
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = TagFonts.display, fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                fontSize = androidx.compose.ui.unit.TextUnit(56f, androidx.compose.ui.unit.TextUnitType.Sp),
                color = TagColors.White, fontSynthesis = TagFonts.NoSynthesis
            )
        )
        androidx.compose.material3.Text(
            "Coming soon.",
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = Montserrat, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp),
                color = TagColors.Outline
            )
        )
    }
}
