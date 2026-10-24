package com.microtag.ui

import android.graphics.Path as NativePath

/**
 * The TAG mark: an abstract luggage tag cut from straight lines only. A chamfered head with a square punch, a stepped
 * notch bitten out of one edge, a long diagonal cut across the foot, and three label slots cut through the body.
 * No curves anywhere: brutalist, like the wordmark's flat-cut T and slanted A.
 *
 * Printed twice, slightly out of register, it is the Monolith's overprint. Alone it is the launcher icon
 * (see ic_launcher_foreground.xml, which is this same outline).
 *
 * Coordinates are in plate units: 1 wide, 1.9 tall, origin at the top-left.
 */
object TagMark {
    const val WIDTH = 1f
    const val HEIGHT = 1.9f

    /** The outline, clockwise from where the top-left chamfer ends. */
    private val outline = floatArrayOf(
        0.16f, 0.00f, 0.84f, 0.00f, 1.00f, 0.16f, 1.00f, 1.46f, 0.56f, 1.90f,
        0.00f, 1.90f, 0.00f, 0.60f, 0.12f, 0.60f, 0.12f, 0.44f, 0.00f, 0.44f, 0.00f, 0.16f
    )

    /** The punch: a square with one chamfered corner. */
    private val hole = floatArrayOf(0.46f, 0.14f, 0.66f, 0.14f, 0.66f, 0.34f, 0.42f, 0.34f, 0.42f, 0.18f)

    /** The label slots: two bars and a square. */
    private val slots = listOf(
        floatArrayOf(0.20f, 0.78f, 0.80f, 0.78f, 0.80f, 0.90f, 0.20f, 0.90f),
        floatArrayOf(0.20f, 1.02f, 0.62f, 1.02f, 0.62f, 1.14f, 0.20f, 1.14f),
        floatArrayOf(0.70f, 1.02f, 0.80f, 1.02f, 0.80f, 1.14f, 0.70f, 1.14f)
    )

    /** The punch's centre, in plate units. The tag's string hangs from here. */
    const val HOLE_X = 0.54f
    const val HOLE_Y = 0.24f

    /** Builds the plate into [path] (even-odd, so the punch and slots are cut out). */
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
        slots.forEach(::poly)
    }
}
