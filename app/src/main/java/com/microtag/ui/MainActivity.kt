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

private enum class Screen { Onboarding, Dashboard, Inspect, Settings }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RuleEngine.init(applicationContext)
        ShizukuBridge.init(applicationContext)

        // Apply saved look-and-feel before the first frame, so there is no lime flash.
        val settings = TagSettings(applicationContext)
        TagColors.useMaterialYou(applicationContext, settings.materialYou)
        TagMotion.enabled = !settings.reducedMotion
        TagMotion.haptics = settings.haptics

        setContent {
            TagTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val prefs = remember { MicrotagPrefs(applicationContext) }
                    var screen by remember {
                        mutableStateOf(if (prefs.hasCompletedOnboarding) Screen.Dashboard else Screen.Onboarding)
                    }

                    // Onboarding sits on the accent colour; everything else is black.
                    val lightBackdrop = screen == Screen.Onboarding && TagColors.Lime.luminance() > 0.5f
                    LaunchedEffect(lightBackdrop) {
                        val clear = AndroidColor.TRANSPARENT
                        val style = if (lightBackdrop) SystemBarStyle.light(clear, clear) else SystemBarStyle.dark(clear)
                        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    }

                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = {
                            (fadeIn(tween(350)) + scaleIn(
                                initialScale = 0.94f,
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
                            )) togetherWith fadeOut(tween(200))
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
                                onOpenSettings = { screen = Screen.Settings }
                            )
                            Screen.Inspect -> InspectScreen(onBack = { screen = Screen.Dashboard })
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
