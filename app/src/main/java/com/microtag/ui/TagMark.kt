package com.microtag.ui

import android.graphics.Path as NativePath

/**
 * The TAG mark: a heavy, brutalist, stencil-cut 'A'.
 * Composed of thick geometric strokes with a distinctive notch in the left leg 
 * and a disconnected right foot. The crossbar is formed purely by negative space.
 *
 * Coordinates are normalized to a 1x1 plate, origin at top-left.
 */
object TagMark {
    const val WIDTH = 1f
    const val HEIGHT = 1f

    /** The outer heavy stroke of the A, traced clockwise from the bottom left corner. */
    private val outline = floatArrayOf(
        0.187f, 0.707f, // Bottom Left
        0.306f, 0.424f, // Inner Left notch bottom
        0.260f, 0.424f, // Inner Left notch top
        0.260f, 0.344f, // Top left notch edge
        0.316f, 0.344f, // Top left start of diagonal
        0.505f, 0.121f, // Apex Left
        0.528f, 0.121f, // Apex Right
        0.758f, 0.407f, // Right edge notch top
        0.741f, 0.426f, // Right edge notch depth
        0.760f, 0.449f, // Right edge notch bottom
        0.835f, 0.707f, // Bottom Right
        0.584f, 0.707f, // Bottom Right inner
        0.525f, 0.609f, // Crossbar right cut
        0.418f, 0.609f, // Crossbar bottom edge
        0.395f, 0.707f  // Bottom Left inner
    )

    /** The negative space triangle cutting through the centre. */
    private val hole = floatArrayOf(
        0.413f, 0.416f, // Left corner
        0.516f, 0.288f, // Top apex
        0.655f, 0.527f, // Right corner
        0.536f, 0.527f, // Step in
        0.490f, 0.600f, // Drop down to crossbar
        0.320f, 0.600f  // Cut left
    )

    /** Builds the plate into [path] (even-odd, so the negative space is punched out). */
    fun buildPath(path: NativePath) {
        path.rewind()
        path.fillType = NativePath.FillType.EVEN_ODD
        fun poly(pts: FloatArray) {
            path.moveTo(pts[0], pts[1])
            var i = 2
            while (i < pts.size) {
                path.lineTo(pts[i], pts[i + 1])
                i += 2
            }
            path.close()
        }
        poly(outline)
        poly(hole)
    }
}
