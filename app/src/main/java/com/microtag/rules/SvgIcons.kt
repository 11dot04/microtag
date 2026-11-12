package com.microtag.rules

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.util.LruCache
import androidx.core.graphics.PathParser

/**
 * Icons dropped into assets/icons/<name>.svg, drawn at runtime: white on transparent, padded, square.
 * Same idea as the WDGTS glyph loader: a small regex parse of <svg>, <path>, <rect>, <circle>, <ellipse>
 * and <polygon>, Paths drawn straight onto a Canvas. Fill colours are ignored (the system tints chip icons);
 * stroked shapes are drawn as strokes. Groups, transforms, gradients and text are not supported.
 */
object SvgIcons {
    private class Glyph(val w: Float, val h: Float, val fills: List<Path>, val strokes: List<Pair<Path, Float>>)

    private val TAG = Regex("""<(svg|path|rect|circle|ellipse|polygon)\b([^>]*)>""")
    private val ATTR = Regex("""([a-zA-Z-]+)\s*=\s*"([^"]*)"""")
    private val cache = LruCache<String, Bitmap>(48)
    private val parsed = HashMap<String, Glyph?>()

    private fun num(s: String?): Float? = s?.removeSuffix("px")?.trim()?.toFloatOrNull()

    private fun parse(svg: String): Glyph? {
        var w: Float? = null
        var h: Float? = null
        val fills = ArrayList<Path>()
        val strokes = ArrayList<Pair<Path, Float>>()
        for (m in TAG.findAll(svg)) {
            val a = ATTR.findAll(m.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] }
            val path: Path? = when (m.groupValues[1]) {
                "svg" -> {
                    w = num(a["width"]); h = num(a["height"])
                    val vb = a["viewBox"]?.trim()?.split(Regex("[ ,]+"))
                    if ((w == null || h == null) && vb != null && vb.size == 4) { w = vb[2].toFloatOrNull(); h = vb[3].toFloatOrNull() }
                    null
                }
                "path" -> a["d"]?.let { d -> try { PathParser.createPathFromPathData(d) } catch (_: RuntimeException) { null } }
                "rect" -> {
                    val x = num(a["x"]) ?: 0f; val y = num(a["y"]) ?: 0f
                    val rw = num(a["width"]); val rh = num(a["height"])
                    if (rw == null || rh == null) null else {
                        val rx = num(a["rx"]) ?: 0f
                        Path().apply { addRoundRect(RectF(x, y, x + rw, y + rh), rx, num(a["ry"]) ?: rx, Path.Direction.CW) }
                    }
                }
                "circle" -> {
                    val r = num(a["r"])
                    if (r == null) null else Path().apply { addCircle(num(a["cx"]) ?: 0f, num(a["cy"]) ?: 0f, r, Path.Direction.CW) }
                }
                "ellipse" -> {
                    val rx = num(a["rx"]); val ry = num(a["ry"])
                    val cx = num(a["cx"]) ?: 0f; val cy = num(a["cy"]) ?: 0f
                    if (rx == null || ry == null) null else Path().apply { addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW) }
                }
                "polygon" -> {
                    val pts = a["points"]?.trim()?.split(Regex("[ ,]+"))?.mapNotNull { it.toFloatOrNull() }
                    if (pts == null || pts.size < 6) null else Path().apply {
                        moveTo(pts[0], pts[1])
                        for (i in 2 until pts.size - 1 step 2) lineTo(pts[i], pts[i + 1])
                        close()
                    }
                }
                else -> null
            }
            if (path == null) continue
            if (a["fill-rule"] == "evenodd") path.fillType = Path.FillType.EVEN_ODD
            val stroked = a["stroke"].let { it != null && it != "none" }
            val filled = a["fill"].let { it == null || it != "none" }
            if (stroked) strokes += path to (num(a["stroke-width"]) ?: 1f)
            if (!stroked || filled && a["fill"] != null) fills += path
        }
        val ww = w ?: return null
        val hh = h ?: return null
        return Glyph(ww, hh, fills, strokes)
    }

    private fun glyph(context: Context, name: String): Glyph? = synchronized(parsed) {
        parsed.getOrPut(name) {
            runCatching { context.assets.open("icons/$name.svg").bufferedReader().use { parse(it.readText()) } }.getOrNull()
        }
    }

    fun exists(context: Context, name: String): Boolean = glyph(context, name) != null

    /** A white, padded, square bitmap of the named icon, or null if there is no such asset. */
    fun bitmap(context: Context, name: String, size: Int = 96): Bitmap? {
        val key = "$name@$size"
        cache.get(key)?.let { return it }
        val g = glyph(context, name) ?: return null
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pad = size * 0.14f
        val s = (size - 2 * pad) / maxOf(g.w, g.h)
        c.translate(pad + ((size - 2 * pad) - g.w * s) / 2f, pad + ((size - 2 * pad) - g.h * s) / 2f)
        c.scale(s, s)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        for (p in g.fills) c.drawPath(p, fill)
        for ((p, w) in g.strokes) { stroke.strokeWidth = w; c.drawPath(p, stroke) }
        cache.put(key, bmp)
        return bmp
    }

    fun icon(context: Context, name: String, size: Int = 96): Icon? = bitmap(context, name, size)?.let { Icon.createWithBitmap(it) }
}
