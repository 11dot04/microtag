package com.microtag.inspect

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Nearest colour name from a bundled list (res/raw/colour_names.txt, one "rrggbb Name" per line;
 * names from meodai/color-names, MIT). Matching is done in CIE Lab so "close" means close to the eye.
 * The list is loaded once by the app (see [load]); until then names are simply absent.
 */
object ColourNames {

    private class Entry(val name: String, val rgb: Int, val l: Double, val a: Double, val b: Double)

    @Volatile private var entries: List<Entry> = emptyList()
    @Volatile private var byName: Map<String, Int> = emptyMap()

    val isLoaded: Boolean get() = entries.isNotEmpty()

    /** Parses the asset lines. Safe to call repeatedly; the first non-empty load wins. */
    fun load(lines: Sequence<String>) {
        if (entries.isNotEmpty()) return
        val out = ArrayList<Entry>(5000)
        for (raw in lines) {
            val line = raw.trim()
            if (line.length < 8 || line[6] != ' ') continue
            val rgb = line.substring(0, 6).toIntOrNull(16) ?: continue
            val lab = lab((rgb shr 16) and 255, (rgb shr 8) and 255, rgb and 255)
            out.add(Entry(line.substring(7).trim(), rgb, lab[0], lab[1], lab[2]))
        }
        entries = out
        byName = out.associate { normalise(it.name) to it.rgb }
    }

    internal fun normalise(s: String): String =
        s.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()

    /** The colour (0xRRGGBB) for an exact name from the list, or null. */
    fun find(name: String): Int? = byName[normalise(name)]

    /** The closest name, or null when the list is not loaded. */
    fun nearest(r: Int, g: Int, b: Int): String? {
        val list = entries
        if (list.isEmpty()) return null
        val t = lab(r, g, b)
        var best: Entry? = null
        var bestD = Double.MAX_VALUE
        for (e in list) {
            val d = (e.l - t[0]).pow(2) + (e.a - t[1]).pow(2) + (e.b - t[2]).pow(2)
            if (d < bestD) { bestD = d; best = e }
        }
        return best?.name
    }

    internal fun lab(r: Int, g: Int, b: Int): DoubleArray {
        fun lin(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val lr = lin(r); val lg = lin(g); val lb = lin(b)
        val x = (0.4124564 * lr + 0.3575761 * lg + 0.1804375 * lb) / 0.95047
        val y = 0.2126729 * lr + 0.7151522 * lg + 0.0721750 * lb
        val z = (0.0193339 * lr + 0.1191920 * lg + 0.9503041 * lb) / 1.08883
        fun f(t: Double) = if (t > 0.008856) Math.cbrt(t) else 7.787 * t + 16.0 / 116
        val fx = f(x); val fy = f(y); val fz = f(z)
        return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    @Suppress("unused")
    internal fun distance(a: DoubleArray, b: DoubleArray) =
        sqrt((a[0] - b[0]).pow(2) + (a[1] - b[1]).pow(2) + (a[2] - b[2]).pow(2))
}
