package com.microtag.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.normalized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** A Material shape morph drawn as a Compose Shape at the given progress (0 start, 1 end; a little past either overshoots). */
class MorphedShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cubics = morph.asCubics(progress)
        if (cubics.isNotEmpty()) {
            path.moveTo(cubics.first().anchor0X * size.width, cubics.first().anchor0Y * size.height)
            for (c in cubics) {
                path.cubicTo(
                    c.control0X * size.width, c.control0Y * size.height,
                    c.control1X * size.width, c.control1Y * size.height,
                    c.anchor1X * size.width, c.anchor1Y * size.height
                )
            }
            path.close()
        }
        return Outline.Generic(path)
    }
}

/**
 * An easter egg for the Material You palette: the Bluetooth tile is a circle until it is pressed, then it morphs
 * circle to cookie to gem and back round, on Expressive's spatial spring (which overshoots a little, like the real thing).
 * With any other palette it stays a plain circle.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class ShapeEgg(private val enabled: Boolean, private val scope: CoroutineScope) {
    private val shapes: List<RoundedPolygon> = listOf(MaterialShapes.Circle, MaterialShapes.Cookie9Sided, MaterialShapes.Gem).map { it.normalized() }
    private val morphs = HashMap<Pair<Int, Int>, Morph>()
    private var from by mutableIntStateOf(0)
    private var to by mutableIntStateOf(0)
    private val progress = Animatable(1f)

    private fun morph(a: Int, b: Int) = morphs.getOrPut(a to b) { Morph(shapes[a], shapes[b]) }

    val shape: Shape
        get() = if (!enabled) CircleShape else MorphedShape(morph(from, to), progress.value)

    fun press() {
        if (!enabled) return
        scope.launch {
            from = to
            to = (to + 1) % shapes.size
            progress.snapTo(0f)
            progress.animateTo(1f, TagMotion.spatial())
        }
    }
}

@Composable
fun rememberShapeEgg(enabled: Boolean): ShapeEgg {
    val scope = rememberCoroutineScope()
    return remember(enabled) { ShapeEgg(enabled, scope) }
}
