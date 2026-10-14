package com.microtag.inspect

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// ==========================================================
// TICKET SHAPES. One small outline engine, one spec per kind of card.
// A spec lists the four corners (square, round, chamfer, scoop, step), any
// notches bitten out of the edges, and optional round punch-holes. All sizes
// are in dp. Every card kind gets a shape that hints at its data: a postage
// stamp for a PIN code, bank columns for an IFSC, a receipt tear for a sum.
// ==========================================================

enum class Cut { Square, Round, Chamfer, Scoop, Step }
enum class Bite { Semi, Tri, Rect, Slot, Ramp }
enum class Edge { Top, Right, Bottom, Left }

data class Corner(val cut: Cut = Cut.Square, val size: Float = 0f)

/** A notch in an edge. [at] is the centre as a fraction of the edge, [depth] is dp into the card (negative = outwards, avoid). */
data class Notch(val edge: Edge, val at: Float, val width: Float, val depth: Float, val kind: Bite = Bite.Semi)

/** A round hole punched through the card, centre as fractions of width and height. */
data class Punch(val x: Float, val y: Float, val radius: Float)

data class TicketSpec(
    val tl: Corner = Corner(),
    val tr: Corner = Corner(),
    val br: Corner = Corner(),
    val bl: Corner = Corner(),
    val notches: List<Notch> = emptyList(),
    val punches: List<Punch> = emptyList()
)

class TicketShape(private val spec: TicketSpec) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(buildTicketPath(spec, size.width, size.height, density.density))
}

private class V(val x: Float, val y: Float) {
    operator fun plus(o: V) = V(x + o.x, y + o.y)
    operator fun minus(o: V) = V(x - o.x, y - o.y)
    operator fun times(k: Float) = V(x * k, y * k)
}

private const val K = 0.5523f

fun buildTicketPath(spec: TicketSpec, w: Float, h: Float, dp: Float): Path {
    val verts = listOf(V(0f, 0f), V(w, 0f), V(w, h), V(0f, h))
    val corners = listOf(spec.tl, spec.tr, spec.br, spec.bl)
    val edges = listOf(Edge.Top, Edge.Right, Edge.Bottom, Edge.Left)
    val limit = min(w, h) / 2f
    fun extent(c: Corner): Float = if (c.cut == Cut.Square) 0f else min(c.size * dp, limit)

    fun dir(i: Int): V {
        val a = verts[i % 4]; val b = verts[(i + 1) % 4]
        val dx = b.x - a.x; val dy = b.y - a.y
        val len = sqrt(dx * dx + dy * dy)
        return V(dx / len, dy / len)
    }

    val path = Path()
    // Start just after the top-left corner, then go clockwise: edge, corner, edge, corner...
    val d0 = dir(0)
    val dLast = dir(3)
    val startC = extent(spec.tl)
    val start = verts[0] + d0 * startC
    path.moveTo(start.x, start.y)

    for (i in 0 until 4) {
        val a = verts[i]; val b = verts[(i + 1) % 4]
        val d = dir(i)
        val n = V(-d.y, d.x)
        val len = sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y))
        val segStart = extent(corners[i])
        val segEnd = len - extent(corners[(i + 1) % 4])

        // Notches on this edge, left to right.
        val here = spec.notches.filter { it.edge == edges[i] }.sortedBy { it.at }
        var cursor = segStart
        for (nt in here) {
            val nw = nt.width * dp
            val nd = nt.depth * dp
            var centre = len * nt.at
            centre = centre.coerceIn(max(cursor + nw / 2f, segStart + nw / 2f), segEnd - nw / 2f)
            if (centre - nw / 2f < cursor - 0.01f || centre + nw / 2f > segEnd + 0.01f) continue
            val s0 = centre - nw / 2f
            fun p(along: Float, depth: Float): V = a + d * (s0 + along) + n * depth
            val p0 = p(0f, 0f)
            path.lineTo(p0.x, p0.y)
            when (nt.kind) {
                Bite.Semi -> {
                    val c1 = p(0f, nd * 1.3333f); val c2 = p(nw, nd * 1.3333f); val e = p(nw, 0f)
                    path.cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)
                }
                Bite.Tri -> {
                    val m = p(nw / 2f, nd); val e = p(nw, 0f)
                    path.lineTo(m.x, m.y); path.lineTo(e.x, e.y)
                }
                Bite.Rect -> {
                    val q1 = p(0f, nd); val q2 = p(nw, nd); val e = p(nw, 0f)
                    path.lineTo(q1.x, q1.y); path.lineTo(q2.x, q2.y); path.lineTo(e.x, e.y)
                }
                Bite.Slot -> {
                    val q1 = p(nw * 0.15f, nd); val q2 = p(nw * 0.85f, nd); val e = p(nw, 0f)
                    path.lineTo(q1.x, q1.y); path.lineTo(q2.x, q2.y); path.lineTo(e.x, e.y)
                }
                Bite.Ramp -> {
                    val q = p(nw, nd); val e = p(nw, 0f)
                    path.lineTo(q.x, q.y); path.lineTo(e.x, e.y)
                }
            }
            cursor = centre + nw / 2f
        }

        // Run to the corner, then draw the corner.
        val vi = (i + 1) % 4
        val c = corners[vi]
        val ce = extent(c)
        val v = verts[vi]
        val d1 = d
        val d2 = dir(vi)
        val pre = v - d1 * ce
        val post = v + d2 * ce
        if (i == 3) {
            // The last corner is the top-left one; it ends at the start point.
            path.lineTo(pre.x, pre.y)
            cornerTo(path, c.cut, pre, post, v, d1, d2, ce)
        } else {
            path.lineTo(pre.x, pre.y)
            cornerTo(path, c.cut, pre, post, v, d1, d2, ce)
        }
    }
    path.close()

    // Punch-holes: even-odd fill makes them real holes for clipping, background and border.
    if (spec.punches.isNotEmpty()) {
        for (pc in spec.punches) {
            val r = pc.radius * dp
            path.addOval(
                androidx.compose.ui.geometry.Rect(
                    Offset(pc.x * w - r, pc.y * h - r), Size(r * 2f, r * 2f)
                )
            )
        }
        path.fillType = PathFillType.EvenOdd
    }
    return path
}

private fun cornerTo(path: Path, cut: Cut, pre: V, post: V, v: V, d1: V, d2: V, c: Float) {
    when (cut) {
        Cut.Square -> {
            path.lineTo(v.x, v.y)
        }
        Cut.Round -> {
            val c1 = pre + d1 * (K * c)
            val c2 = post - d2 * (K * c)
            path.cubicTo(c1.x, c1.y, c2.x, c2.y, post.x, post.y)
        }
        Cut.Chamfer -> path.lineTo(post.x, post.y)
        Cut.Scoop -> {
            val c1 = pre + d2 * (K * c)
            val c2 = post - d1 * (K * c)
            path.cubicTo(c1.x, c1.y, c2.x, c2.y, post.x, post.y)
        }
        Cut.Step -> {
            val q = pre + d2 * c
            path.lineTo(q.x, q.y)
            path.lineTo(post.x, post.y)
        }
    }
}

// ==========================================================
// One spec per kind of card.
// ==========================================================

object CardShapes {

    private fun row(edge: Edge, count: Int, from: Float, to: Float, width: Float, depth: Float, kind: Bite): List<Notch> =
        (0 until count).map { i ->
            val f = if (count == 1) (from + to) / 2f else from + (to - from) * i / (count - 1)
            Notch(edge, f, width, depth, kind)
        }

    fun specFor(domain: String): TicketSpec = when (domain) {
        // The concept card: soft top, chamfered foot, a bite out of the right edge.
        "COLOUR" -> TicketSpec(
            tl = Corner(Cut.Round, 30f), tr = Corner(Cut.Round, 30f), bl = Corner(Cut.Chamfer, 46f),
            notches = listOf(Notch(Edge.Right, 0.56f, 44f, 15f, Bite.Semi))
        )
        // A till receipt: torn bottom edge.
        "CALC" -> TicketSpec(
            tl = Corner(Cut.Chamfer, 20f), tr = Corner(Cut.Chamfer, 20f),
            notches = row(Edge.Bottom, 9, 0.08f, 0.92f, 20f, 9f, Bite.Tri)
        )
        // A ruler laid across: two opposed corners cut, a notch each side of the middle.
        "CONV" -> TicketSpec(
            tl = Corner(Cut.Round, 28f), br = Corner(Cut.Round, 28f),
            tr = Corner(Cut.Chamfer, 28f), bl = Corner(Cut.Chamfer, 28f),
            notches = listOf(Notch(Edge.Left, 0.5f, 36f, 12f, Bite.Semi), Notch(Edge.Right, 0.5f, 36f, 12f, Bite.Semi))
        )
        // A banknote: scooped corners, a coin-sized bite at each side.
        "MONEY" -> TicketSpec(
            tl = Corner(Cut.Scoop, 22f), tr = Corner(Cut.Scoop, 22f), br = Corner(Cut.Scoop, 22f), bl = Corner(Cut.Scoop, 22f),
            notches = listOf(Notch(Edge.Left, 0.42f, 40f, 14f, Bite.Semi), Notch(Edge.Right, 0.42f, 40f, 14f, Bite.Semi))
        )
        // A watch: very round, with a crown on the top edge.
        "TIME" -> TicketSpec(
            tl = Corner(Cut.Round, 44f), tr = Corner(Cut.Round, 44f), br = Corner(Cut.Round, 44f), bl = Corner(Cut.Round, 44f),
            notches = listOf(Notch(Edge.Top, 0.84f, 30f, 10f, Bite.Rect), Notch(Edge.Right, 0.5f, 22f, 8f, Bite.Semi))
        )
        // A desk calendar: binder slots on top, a curled corner.
        "DATE" -> TicketSpec(
            bl = Corner(Cut.Round, 12f), br = Corner(Cut.Scoop, 34f),
            notches = listOf(Notch(Edge.Top, 0.22f, 26f, 11f, Bite.Slot), Notch(Edge.Top, 0.78f, 26f, 11f, Bite.Slot))
        )
        // A browser tab with a pointer bitten into the right edge.
        "URL" -> TicketSpec(
            tl = Corner(Cut.Round, 28f), tr = Corner(Cut.Round, 28f), br = Corner(Cut.Chamfer, 36f),
            notches = listOf(Notch(Edge.Right, 0.5f, 40f, 22f, Bite.Tri), Notch(Edge.Bottom, 0.3f, 34f, 10f, Bite.Ramp))
        )
        // A scan target: crisp corners, tiny rectangular registration bites.
        "UPI" -> TicketSpec(
            tl = Corner(Cut.Round, 8f), tr = Corner(Cut.Round, 8f), bl = Corner(Cut.Round, 8f), br = Corner(Cut.Chamfer, 52f),
            notches = listOf(Notch(Edge.Top, 0.16f, 26f, 10f, Bite.Rect), Notch(Edge.Left, 0.18f, 26f, 10f, Bite.Rect))
        )
        // A bank front: pitched roof, columns along the base.
        "IFSC" -> TicketSpec(
            tl = Corner(Cut.Chamfer, 64f), tr = Corner(Cut.Chamfer, 64f),
            notches = row(Edge.Bottom, 4, 0.2f, 0.8f, 36f, 12f, Bite.Slot)
        )
        // A postage stamp: perforated on all four sides.
        "PIN" -> TicketSpec(
            notches = row(Edge.Top, 11, 0.07f, 0.93f, 14f, 6f, Bite.Semi) +
                row(Edge.Bottom, 11, 0.07f, 0.93f, 14f, 6f, Bite.Semi) +
                row(Edge.Left, 7, 0.1f, 0.9f, 14f, 6f, Bite.Semi) +
                row(Edge.Right, 7, 0.1f, 0.9f, 14f, 6f, Bite.Semi)
        )
        // A bar chart's teeth along the top.
        "NUM" -> TicketSpec(
            tl = Corner(Cut.Round, 24f), bl = Corner(Cut.Chamfer, 30f),
            notches = row(Edge.Top, 6, 0.3f, 0.95f, 24f, 8f, Bite.Ramp)
        )
        // A book page: stitched spine, curled foot.
        "DEFINE" -> TicketSpec(
            tl = Corner(Cut.Round, 18f), tr = Corner(Cut.Round, 6f), br = Corner(Cut.Chamfer, 44f),
            notches = row(Edge.Left, 3, 0.25f, 0.75f, 20f, 7f, Bite.Slot)
        )
        // A framed photo with a thumb notch.
        "WIKI" -> TicketSpec(
            tr = Corner(Cut.Round, 58f), br = Corner(Cut.Scoop, 36f), tl = Corner(Cut.Chamfer, 14f),
            notches = listOf(Notch(Edge.Left, 0.34f, 38f, 12f, Bite.Semi))
        )
        // A drafting sheet: a clipped corner, a title block stepped out of the foot, registration ticks.
        "DIMS" -> TicketSpec(
            tl = Corner(Cut.Chamfer, 24f), br = Corner(Cut.Step, 56f),
            notches = row(Edge.Top, 3, 0.25f, 0.75f, 6f, 6f, Bite.Rect)
        )
        else -> TicketSpec(
            tl = Corner(Cut.Round, 26f), tr = Corner(Cut.Round, 26f), bl = Corner(Cut.Round, 26f), br = Corner(Cut.Chamfer, 32f),
            notches = listOf(Notch(Edge.Right, 0.5f, 32f, 12f, Bite.Semi))
        )
    }

    fun shapeFor(domain: String): Shape = TicketShape(specFor(domain))
}
