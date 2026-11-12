package com.microtag.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape

/**
 * Container transform between a dashboard tile and the screen it opens: the tile's bounds grow into the screen's,
 * and back. Built on shared bounds (the container-transform pattern in the Compose docs), timed with
 * Material 3 Expressive's spatial spring so it moves like the rest of the app.
 *
 * MainActivity provides the two scopes. A tile and the screen it opens use the same key; where no match exists
 * (a screen opened from somewhere else) the content simply fades.
 */
internal val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
internal val LocalContentScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@Composable
internal fun Modifier.containerBounds(key: String?, shape: Shape): Modifier {
    if (key == null) return this
    val shared = LocalSharedScope.current ?: return this
    val visibility = LocalContentScope.current ?: return this
    return with(shared) {
        this@containerBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = visibility,
            enter = fadeIn(TagMotion.effects()),
            exit = fadeOut(TagMotion.fastEffects()),
            boundsTransform = BoundsTransform { _, _ -> TagMotion.spatial<Rect>() },
            clipInOverlayDuringTransition = OverlayClip(shape)
        )
    }
}
