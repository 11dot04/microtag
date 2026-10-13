package com.microtag.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.microtag.R
import kotlinx.coroutines.CancellationException

/**
 * The frame every secondary screen lives in.
 *  - No top bar. Content scrolls edge to edge and simply fades out under the status bar.
 *  - A floating back button sits bottom-left, where the thumb is. An optional action floats bottom-right.
 *  - Predictive back: the whole screen shrinks and rounds off while the back gesture is held.
 */
@Composable
fun SubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 14.dp,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var gesture by remember { mutableFloatStateOf(0f) }
    var fromRight by remember { mutableStateOf(false) }
    val p by animateFloatAsState(gesture, spring(stiffness = Spring.StiffnessHigh), label = "predictiveBack")

    PredictiveBackHandler { events ->
        try {
            events.collect { e ->
                gesture = e.progress
                fromRight = e.swipeEdge == BackEventCompat.EDGE_RIGHT
            }
            onBack()
        } catch (c: CancellationException) {
            gesture = 0f
            throw c
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val s = 1f - 0.1f * p
                scaleX = s
                scaleY = s
                translationX = (if (fromRight) -1f else 1f) * p * 20.dp.toPx()
                shape = RoundedCornerShape((32f * p).dp)
                clip = true
            }
            .background(TagColors.Black)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            // Insets live inside the scroll, so content can slide under the bars instead of being cut.
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            content()
            Spacer(Modifier.height(96.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }

        // Fade under the status bar.
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(top + 18.dp)
                .background(Brush.verticalGradient(listOf(TagColors.Black, Color.Transparent)))
        )

        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        FloatingButton(
            Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = bottom + 16.dp),
            filled = true, onClick = onBack
        ) { tint -> TagIcon(R.drawable.ic_chevron_right, tint, Modifier.rotate(180f), 28.dp) }

        if (action != null) {
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = bottom + 16.dp)) { action() }
        }
    }
}

/** 56dp round button: accent-filled for the main one, black with an accent outline for extras. */
@Composable
fun FloatingButton(
    modifier: Modifier = Modifier,
    filled: Boolean,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit
) {
    val view = LocalView.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && TagMotion.enabled) 0.9f else 1f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "fab"
    )
    val tint = if (filled) TagColors.OnLime else TagColors.Lime
    Box(
        modifier
            .size(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (filled) TagColors.Lime else TagColors.Black)
            .border(2.dp, TagColors.Lime, CircleShape)
            .clickable(interactionSource = source, indication = null) { view.tick(Tick.Press); onClick() },
        contentAlignment = Alignment.Center
    ) { icon(tint) }
}
