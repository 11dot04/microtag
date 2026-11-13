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

    /** Where the ink actually is, in the icon's own units. */
    private fun contentBounds(g: Glyph): RectF {
        val out = RectF(); var first = true
        val r = RectF()
        (g.fills + g.strokes.map { it.first }).forEach { p ->
            p.computeBounds(r, true)
            if (first) { out.set(r); first = false } else out.union(r)
        }
        return if (first) RectF(0f, 0f, g.w, g.h) else out
    }

    /**
     * A bitmap of the named icon cropped to where its ink is, so a small mark in a big viewBox fills the square.
     * Pass [together] to crop to the combined ink of several icons: pieces of one mark (the two halves of the TAG mark)
     * then keep their size and position relative to each other and fit back together.
     */
    fun cropped(context: Context, name: String, together: List<String> = listOf(name), size: Int = 128, tint: Int = Color.WHITE): Bitmap? {
        val key = "crop:$name:${together.joinToString("+")}@$size@$tint"
        cache.get(key)?.let { return it }
        val g = glyph(context, name) ?: return null
        val window = RectF(); var first = true
        for (n in together) {
            val b = glyph(context, n)?.let { contentBounds(it) } ?: continue
            if (first) { window.set(b); first = false } else window.union(b)
        }
        if (first) return null
        val bmp = draw(g, size, tint, window, 0.04f)
        cache.put(key, bmp)
        return bmp
    }

    fun croppedIcon(context: Context, name: String, together: List<String> = listOf(name), size: Int = 128, tint: Int = Color.WHITE): Icon? =
        cropped(context, name, together, size, tint)?.let { Icon.createWithBitmap(it) }

    /**
     * Several icons that are pieces of one mark, drawn together on one bitmap whose size and shape follow the mark
     * (not a square): the two halves of the TAG mark joined. [widthPx] is the width; the height follows the aspect.
     */
    fun joined(context: Context, names: List<String>, widthPx: Int, tint: Int = Color.WHITE): Bitmap? {
        val key = "joined:${names.joinToString("+")}@$widthPx@$tint"
        cache.get(key)?.let { return it }
        val glyphs = names.mapNotNull { glyph(context, it) }
        if (glyphs.isEmpty()) return null
        val window = RectF(); var first = true
        for (g in glyphs) { val b = contentBounds(g); if (first) { window.set(b); first = false } else window.union(b) }
        val s = widthPx / window.width()
        val bmp = Bitmap.createBitmap(widthPx, maxOf(1, (window.height() * s).toInt()), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(s, s)
        c.translate(-window.left, -window.top)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint; style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        for (g in glyphs) {
            for (p in g.fills) c.drawPath(p, fill)
            for ((p, w) in g.strokes) { stroke.strokeWidth = w; c.drawPath(p, stroke) }
        }
        cache.put(key, bmp)
        return bmp
    }

    /** The TAG mark, both halves joined. */
    fun tagMark(context: Context, widthPx: Int, tint: Int): Bitmap? = joined(context, listOf("tag_end", "tag_progress"), widthPx, tint)

    /** A white, padded, square bitmap of the named icon, or null if there is no such asset. */
    fun bitmap(context: Context, name: String, size: Int = 96, tint: Int = Color.WHITE): Bitmap? {
        val key = "$name@$size@$tint"
        cache.get(key)?.let { return it }
        val g = glyph(context, name) ?: return null
        val bmp = draw(g, size, tint, RectF(0f, 0f, g.w, g.h), 0.14f)
        cache.put(key, bmp)
        return bmp
    }

    /** Draws the part of the icon inside [window] into a square, padded by [padFraction], centred. */
    private fun draw(g: Glyph, size: Int, tint: Int, window: RectF, padFraction: Float): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pad = size * padFraction
        val s = (size - 2 * pad) / maxOf(window.width(), window.height())
        c.translate(pad + ((size - 2 * pad) - window.width() * s) / 2f, pad + ((size - 2 * pad) - window.height() * s) / 2f)
        c.scale(s, s)
        c.translate(-window.left, -window.top)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint; style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        for (p in g.fills) c.drawPath(p, fill)
        for ((p, w) in g.strokes) { stroke.strokeWidth = w; c.drawPath(p, stroke) }
        return bmp
    }

    fun icon(context: Context, name: String, size: Int = 96, tint: Int = Color.WHITE): Icon? = bitmap(context, name, size, tint)?.let { Icon.createWithBitmap(it) }
}
