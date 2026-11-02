package com.microtag.ui

import android.graphics.Matrix
import android.graphics.Path as NativePath

/**
 * The TAG mark: two interlocked straight-cut pieces that read as an angular A / delta. A tall arm on the right
 * with a stepped notch, and a long foot sweeping left with a chamfered tab, split by a thin diagonal gap.
 * No curves anywhere.
 *
 * Printed twice, slightly out of register, it is the Monolith's overprint. Alone it is the launcher icon
 * (see ic_launcher_foreground.xml, which is this same outline).
 *
 * The outline is stored in its original 1210-unit tracing space and normalised in [buildPath], so the
 * plate is 1 wide and [HEIGHT] tall with its origin at the top-left.
 */
object TagMark {
    const val WIDTH = 1f
    const val HEIGHT = 0.763f   // 544 / 713, the traced artwork's aspect ratio

    /** Where the tag's hairline "string" leaves the plate: the apex of the right arm, in plate units. */
    const val ANCHOR_X = 0.475f
    const val ANCHOR_Y = 0.01f

    // Tracing-space bounds of the artwork (both pieces together).
    private const val SRC_LEFT = 285f
    private const val SRC_TOP = 299f
    private const val SRC_WIDTH = 713f

    // Each piece is x,y pairs relative to its own tracing-space origin.
    private val _armOrigin = intArrayOf(604, 299)
    private val _arm = intArrayOf(
        0, 0, 41, 0, 46, 5, 63, 31, 76, 51, 89, 71, 102, 91, 115, 111, 130, 134, 143, 154,
        156, 174, 169, 194, 182, 214, 187, 222, 187, 224, 178, 230, 188, 246, 191, 250, 191, 252,
        202, 254, 210, 257, 223, 277, 236, 297, 249, 317, 262, 337, 275, 357, 288, 377, 301, 397,
        314, 417, 329, 440, 340, 457, 355, 480, 368, 500, 381, 520, 394, 540, 394, 541, 315, 542,
        167, 542, 152, 519, 139, 499, 126, 479, 111, 456, 104, 445, 104, 443, 142, 442, 210, 442,
        197, 422, 182, 399, 171, 382, 156, 359, 143, 339, 130, 319, 117, 299, 104, 279, 91, 259,
        78, 239, 65, 219, 50, 196, 37, 176, 24, 156, 21, 151, 19, 150, 17, 155, 3, 177,
        -11, 199, -14, 198, -28, 176, -46, 149, -56, 133, -71, 110, -72, 107, -56, 82, -42, 60,
        -28, 38, -12, 13, -5, 2
    )

    private val _footOrigin = intArrayOf(477, 427)
    private val _foot = intArrayOf(
        0, 0, 41, 0, 48, 9, 58, 25, 75, 51, 88, 71, 99, 88, 101, 91, 100, 96, 84, 121,
        70, 143, 54, 168, 40, 190, 26, 212, 10, 237, -4, 259, -20, 284, -34, 306, -40, 316,
        50, 314, 65, 291, 76, 274, 87, 257, 98, 240, 109, 223, 114, 215, 128, 214, 158, 214,
        166, 225, 176, 241, 191, 264, 204, 284, 217, 304, 223, 314, 170, 315, 158, 333, 147, 350,
        136, 367, 123, 387, 112, 404, 105, 415, 6, 416, -162, 416, -167, 411, -185, 384, -192, 373,
        -192, 366, -176, 341, -160, 316, -146, 294, -132, 272, -116, 247, -102, 225, -86, 200,
        -72, 178, -56, 153, -42, 131, -26, 106, -23, 101, -57, 101, -57, 1
    )

    private fun addPiece(path: NativePath, pts: IntArray, origin: IntArray) {
        val ox = origin[0].toFloat()
        val oy = origin[1].toFloat()
        path.moveTo(ox + pts[0], oy + pts[1])
        var i = 2
        while (i < pts.size) {
            path.lineTo(ox + pts[i], oy + pts[i + 1])
            i += 2
        }
        path.close()
    }

    /** Builds the plate into [path]. Non-zero fill: the shape's own gaps are real gaps, nothing is punched out. */
    fun buildPath(path: NativePath) {
        path.rewind()
        path.fillType = NativePath.FillType.WINDING
        addPiece(path, _arm, _armOrigin)
        addPiece(path, _foot, _footOrigin)
        val m = Matrix()
        m.postTranslate(-SRC_LEFT, -SRC_TOP)
        m.postScale(1f / SRC_WIDTH, 1f / SRC_WIDTH)
        path.transform(m)
    }
}
