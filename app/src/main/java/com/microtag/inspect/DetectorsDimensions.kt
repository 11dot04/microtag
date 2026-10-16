package com.microtag.inspect

import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * DIMENSIONS: "120 x 80 cm", "2.4 × 1.2 × 0.5 m", "6'2\" x 4'", "1920x1080", "4000 x 3000 px".
 * Two or three sizes joined by x / × / by. Shows area, volume, diagonal, aspect ratio, megapixels.
 * Needs a unit, or two whole numbers of at least 100 (read as pixels), so "3 x 4" stays maths.
 * The card draws these as a blueprint (nums = sizes in the unit shown, unit = that unit).
 */
internal object DimensionDetector {

    private const val N = """(\d+(?:[.,]\d+)?)"""
    private const val U = """(mm|cm|m|km|in|inch|inches|ft|feet|foot|px|pixels?|["”″']|)"""
    private const val SEP = """\s*(?:[x×X*]|\bby\b)\s*"""
    private val re = Regex("""(?i)^\s*$N\s*$U$SEP$N\s*$U(?:$SEP$N\s*$U)?\s*$""")

    private data class Unit(val label: String, val metres: Double, val pixels: Boolean = false)

    private fun unitOf(raw: String): Unit? = when (raw.lowercase(Locale.ROOT)) {
        "mm" -> Unit("mm", 0.001)
        "cm" -> Unit("cm", 0.01)
        "m" -> Unit("m", 1.0)
        "km" -> Unit("km", 1000.0)
        "in", "inch", "inches", "\"", "”", "″" -> Unit("in", 0.0254)
        "ft", "feet", "foot", "'" -> Unit("ft", 0.3048)
        "px", "pixel", "pixels" -> Unit("px", 1.0, true)
        else -> null
    }

    private val named = mapOf(
        "1280x720" to "HD 720p", "1920x1080" to "Full HD", "2560x1440" to "QHD 1440p",
        "3840x2160" to "4K UHD", "4096x2160" to "DCI 4K", "7680x4320" to "8K UHD",
        "1366x768" to "WXGA", "1440x900" to "WXGA+", "1920x1200" to "WUXGA",
        "2560x1600" to "WQXGA", "1024x768" to "XGA", "800x600" to "SVGA", "640x480" to "VGA",
        "1080x1920" to "Full HD portrait", "1080x2400" to "FHD+ phone", "1170x2532" to "iPhone 12/13/14",
        "1179x2556" to "iPhone 15/16", "1290x2796" to "iPhone Pro Max"
    )

    fun candidates(text: String, source: Source): List<Candidate> {
        // Reverse: a standard's name ("A3", "us letter", "1080p", "queen bed") gives its dimensions.
        if (source == Source.SELECTION) {
            DimensionStandards.find(text)?.let { std ->
                return listOf(Candidate("dimension-standard", 0.8) { buildStandard(std) })
            }
        }
        val m = re.matchEntire(text) ?: return emptyList()
        val g = m.groupValues
        val nums = listOf(g[1], g[3], g[5]).filter { it.isNotEmpty() }
        val units = listOf(g[2], g[4], g[6]).take(nums.size).filter { it.isNotEmpty() }.map { it.lowercase(Locale.ROOT) }
        val distinct = units.map { unitOf(it)?.label ?: return emptyList() }.toSet()
        if (distinct.size > 1) return emptyList()
        val unit = distinct.firstOrNull()?.let { l -> unitOf(l) }
        val values = nums.map { Fmt.parseNumber(it) ?: return emptyList() }
        if (values.any { it <= 0.0 }) return emptyList()
        if (unit == null) {
            // No unit: only read as pixels, two whole numbers, both sensible screen sizes.
            if (values.size != 2 || nums.any { it.contains('.') || it.contains(',') }) return emptyList()
            if (values.any { it < 100 || it > 20000 }) return emptyList()
        }
        val px = unit == null || unit.pixels
        if (px && values.size != 2) return emptyList()
        return listOf(Candidate("dimensions", if (unit == null) 0.95 else 0.8) { build(values, unit ?: Unit("px", 1.0, true), px) })
    }

    private fun buildStandard(std: DimensionStandards.Std): InspectResult? {
        val u = unitOf(std.unit) ?: return null
        val r = build(std.dims, u, std.px, matchStandard = false)
        val rows = mutableListOf("Standard" to std.label)
        rows.addAll(r.rows.filter { it.first != "Known as" })
        if (std.note.isNotBlank()) rows.add("Note" to std.note)
        return r.copy(title = std.name, subtitle = std.group, cardTitle = std.name, rows = rows)
    }

    private fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)

    private fun ratio(w: Double, h: Double): String {
        val a = Math.round(w); val b = Math.round(h)
        val isWhole = abs(w - a) < 1e-9 && abs(h - b) < 1e-9
        if (isWhole && a > 0 && b > 0) {
            val d = gcd(a, b)
            val x = a / d; val y = b / d
            if (x <= 50 && y <= 50) return "$x:$y"
        }
        // Decimal sizes: the nearest simple ratio, otherwise 1 : n.
        val r = w / h
        val common = listOf(1.0 to 1.0, 4.0 to 3.0, 3.0 to 2.0, 16.0 to 9.0, 16.0 to 10.0, 5.0 to 4.0, 21.0 to 9.0, 2.0 to 1.0, 3.0 to 1.0)
        val best = common.minByOrNull { abs(it.first / it.second - r) }!!
        if (abs(best.first / best.second - r) / r < 0.01) return "${best.first.toInt()}:${best.second.toInt()}"
        return if (r >= 1) "${Fmt.trimmed(r, 2)}:1" else "1:${Fmt.trimmed(1 / r, 2)}"
    }

    private fun build(v: List<Double>, u: Unit, px: Boolean, matchStandard: Boolean = true): InspectResult {
        val std = if (matchStandard) DimensionStandards.match(v, u.label) else null
        val w = v[0]; val h = v[1]
        val d = v.getOrNull(2)
        val rows = mutableListOf<Pair<String, String>>()
        val big: String
        var extra = ""
        if (px) {
            big = "${Fmt.trimmed(w, 0)} × ${Fmt.trimmed(h, 0)}"
            val key = "${Fmt.trimmed(w, 0)}x${Fmt.trimmed(h, 0)}"
            (std?.label ?: named[key])?.let { rows.add("Known as" to it); extra = it }
            rows.add("Aspect" to ratio(w, h))
            rows.add("Megapixels" to "${Fmt.trimmed(w * h / 1_000_000, 2)} MP")
            rows.add("Pixels" to Fmt.grouped(w * h, Locale.ROOT, 0))
            rows.add("Diagonal" to "${Fmt.trimmed(hypot(w, h), 0)} px")
            rows.add("Longest side" to "${Fmt.trimmed(maxOf(w, h), 0)} px")
        } else {
            val l = u.label
            big = v.joinToString(" × ") { Fmt.adaptive(it) } + " $l"
            if (std != null) rows.add("Standard" to std.label)
            rows.add("Area" to "${Fmt.adaptive(w * h)} $l²")
            if (d != null) {
                rows.add("Volume" to "${Fmt.adaptive(w * h * d)} $l³")
                val surface = 2 * (w * h + w * d + h * d)
                rows.add("Surface" to "${Fmt.adaptive(surface)} $l²")
                rows.add("Space diagonal" to "${Fmt.adaptive(sqrt(w * w + h * h + d * d))} $l")
            } else {
                rows.add("Perimeter" to "${Fmt.adaptive(2 * (w + h))} $l")
                rows.add("Diagonal" to "${Fmt.adaptive(hypot(w, h))} $l")
                rows.add("Aspect" to ratio(w, h))
            }
            // The same sizes in the other system.
            val metric = u.label in setOf("mm", "cm", "m", "km")
            val other = if (metric) Unit("in", 0.0254) else Unit("cm", 0.01)
            val k = u.metres / other.metres
            rows.add("In ${other.label}" to v.joinToString(" × ") { Fmt.adaptive(it * k) } + " ${other.label}")
            if (metric && u.label != "m" && u.label != "km") {
                // nothing more
            }
        }
        val sizePill = if (px) "${Fmt.trimmed(w, 0)}×${Fmt.trimmed(h, 0)}" else
            v.joinToString("×") { Fmt.adaptive(it) } + u.label
        val areaPill = if (px) sizePill else if (d != null) "${Fmt.adaptive(w * h * d)} ${u.label}³" else "${Fmt.adaptive(w * h)} ${u.label}²"
        val pill = when (PillConfig.DIMS) {
            "AREA" -> areaPill
            "STANDARD" -> std?.name ?: sizePill
            else -> sizePill
        }
        return InspectResult(
            domain = "DIMS",
            pill = pill,
            title = if (px) (extra.ifBlank { ratio(w, h) }) else
                if (d != null) "${Fmt.adaptive(w * h * d)} ${u.label}³" else "${Fmt.adaptive(w * h)} ${u.label}²",
            big = big,
            subtitle = when {
                std != null && !px -> std.label
                px -> (if (std != null) std.label + " · " else "") + "${Fmt.trimmed(w * h / 1_000_000, 2)} MP · ${ratio(w, h)}"
                d != null -> "Volume"
                else -> "Area"
            },
            cardTitle = "Dimensions",
            cardText = big,
            rows = rows,
            copyText = big,
            iconName = "ic_inspect_convert",
            nums = v,
            unit = u.label,
            timeoutSeconds = 20
        )
    }
}
