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
        // Material You used to be a switch laid over any palette. It is a palette of its own now.
        if (settings.materialYou) { settings.palette = "material"; settings.materialYou = false }
        TagColors.usePalette(applicationContext, settings.palette)
        TagColors.useMulticolour(settings.multicolour)
        TagColors.useAppearance(settings.appearance)
        TagColors.useMicro(settings.micro)
        TagColors.useAmoled(settings.amoled)
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
                            Screen.Pin -> PinScreen(onBack = { screen = Screen.Dashboard })
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
