package com.microtag.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star

/**
 * Material 3 Expressive style shapes, built on the stable androidx.graphics:graphics-shapes library
 * (the same engine material3's MaterialShapes uses), so no alpha dependency is needed and any two of
 * them can morph into each other.
 *
 * These are close to the official shapes, not copies. Every shape is one line, so tuning one by eye
 * (innerRadius = how deep the notches go, rounding = how soft the points are) is a one-number edit.
 * All of them are centred on 0,0 with radius 1.
 */
object ExpressiveShapes {
    val Circle: RoundedPolygon = RoundedPolygon.circle(numVertices = 8)
    val Sunny: RoundedPolygon = RoundedPolygon.star(8, innerRadius = 0.8f, rounding = CornerRounding(0.15f))
    val Cookie9: RoundedPolygon = RoundedPolygon.star(9, innerRadius = 0.8f, rounding = CornerRounding(0.5f))
    val Pentagon: RoundedPolygon = RoundedPolygon(numVertices = 5, rounding = CornerRounding(0.17f))
    val Flower: RoundedPolygon = RoundedPolygon.star(
        8, innerRadius = 0.6f, rounding = CornerRounding(0.5f), innerRounding = CornerRounding.Unrounded
    )
    val Puffy: RoundedPolygon = RoundedPolygon.star(
        6, innerRadius = 0.72f, rounding = CornerRounding(0.55f), innerRounding = CornerRounding(0.4f)
    )
    val SoftBurst: RoundedPolygon = RoundedPolygon.star(
        10, innerRadius = 0.65f, rounding = CornerRounding(0.1f), innerRounding = CornerRounding(0.2f)
    )
    val Cookie6: RoundedPolygon = RoundedPolygon.star(6, innerRadius = 0.75f, rounding = CornerRounding(0.5f))
    val Cookie12: RoundedPolygon = RoundedPolygon.star(12, innerRadius = 0.8f, rounding = CornerRounding(0.5f))

    /** The set the onboarding pile draws from, in order. */
    val All: List<RoundedPolygon> = listOf(Sunny, Cookie9, Pentagon, Flower, Puffy, SoftBurst, Cookie6, Cookie12)
}

/** A [Shape] that is `morph` at `progress` (0 = the first polygon, 1 = the second). */
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress.coerceIn(0f, 1f)).asComposePath()
        // Polygons live in -1..1; stretch that to the box.
        val matrix = Matrix()
        matrix.scale(size.width / 2f, size.height / 2f)
        matrix.translate(1f, 1f)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}
