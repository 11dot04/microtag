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
import com.microtag.core.MicrotagPrefs
import com.microtag.rules.RuleEngine
import com.microtag.shizuku.ShizukuBridge

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RuleEngine.init(applicationContext)
        ShizukuBridge.init(applicationContext)

        setContent {
            TagTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val prefs = remember { MicrotagPrefs(applicationContext) }
                    var onboardingDone by remember { mutableStateOf(prefs.hasCompletedOnboarding) }

                    // Dashboard is black (light status icons); onboarding is lime (dark status icons).
                    LaunchedEffect(onboardingDone) {
                        val clear = AndroidColor.TRANSPARENT
                        enableEdgeToEdge(
                            statusBarStyle = if (onboardingDone) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear),
                            navigationBarStyle = if (onboardingDone) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear)
                        )
                    }

                    AnimatedContent(
                        targetState = onboardingDone,
                        transitionSpec = {
                            (fadeIn(tween(350)) + scaleIn(
                                initialScale = 0.94f,
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
                            )) togetherWith fadeOut(tween(200))
                        },
                        label = "ScreenSwitch"
                    ) { done ->
                        if (!done) {
                            OnboardingScreen(onFinish = {
                                prefs.hasCompletedOnboarding = true
                                onboardingDone = true
                            })
                        } else {
                            // The gear re-opens setup without wiping the saved flag.
                            DashboardScreen(prefs = prefs, onOpenSetup = { onboardingDone = false })
                        }
                    }
                }
            }
        }
    }
}
