package com.microtag.inspect

import java.util.Locale
import kotlin.math.roundToInt

/**
 * The reverse of the colour card: select a colour NAME and get a hex code.
 * "orange", "baby blue", "light green", "Acid Green". Plain names give the usual web shade
 * (what people mean by "red"); everything else comes from the bundled name list.
 */
internal object ColourNameDetector {

    private val css: Map<String, Int> = """
        black 000000|silver c0c0c0|gray 808080|grey 808080|white ffffff|maroon 800000|red ff0000|purple 800080|fuchsia ff00ff|magenta ff00ff|
        green 008000|lime 00ff00|olive 808000|yellow ffff00|navy 000080|blue 0000ff|teal 008080|aqua 00ffff|cyan 00ffff|orange ffa500|
        aliceblue f0f8ff|antiquewhite faebd7|aquamarine 7fffd4|azure f0ffff|beige f5f5dc|bisque ffe4c4|blanchedalmond ffebcd|blueviolet 8a2be2|
        brown a52a2a|burlywood deb887|cadetblue 5f9ea0|chartreuse 7fff00|chocolate d2691e|coral ff7f50|cornflowerblue 6495ed|cornsilk fff8dc|
        crimson dc143c|darkblue 00008b|darkcyan 008b8b|darkgoldenrod b8860b|darkgray a9a9a9|darkgreen 006400|darkkhaki bdb76b|
        darkmagenta 8b008b|darkolivegreen 556b2f|darkorange ff8c00|darkorchid 9932cc|darkred 8b0000|darksalmon e9967a|
        darkseagreen 8fbc8f|darkslateblue 483d8b|darkslategray 2f4f4f|darkturquoise 00ced1|darkviolet 9400d3|deeppink ff1493|
        deepskyblue 00bfff|dimgray 696969|dodgerblue 1e90ff|firebrick b22222|floralwhite fffaf0|forestgreen 228b22|gainsboro dcdcdc|
        ghostwhite f8f8ff|gold ffd700|goldenrod daa520|greenyellow adff2f|honeydew f0fff0|hotpink ff69b4|indianred cd5c5c|indigo 4b0082|
        ivory fffff0|khaki f0e68c|lavender e6e6fa|lavenderblush fff0f5|lawngreen 7cfc00|lemonchiffon fffacd|lightblue add8e6|
        lightcoral f08080|lightcyan e0ffff|lightgoldenrodyellow fafad2|lightgray d3d3d3|lightgreen 90ee90|lightpink ffb6c1|
        lightsalmon ffa07a|lightseagreen 20b2aa|lightskyblue 87cefa|lightslategray 778899|lightsteelblue b0c4de|lightyellow ffffe0|
        limegreen 32cd32|linen faf0e6|mediumaquamarine 66cdaa|mediumblue 0000cd|mediumorchid ba55d3|mediumpurple 9370db|
        mediumseagreen 3cb371|mediumslateblue 7b68ee|mediumspringgreen 00fa9a|mediumturquoise 48d1cc|mediumvioletred c71585|
        midnightblue 191970|mintcream f5fffa|mistyrose ffe4e1|moccasin ffe4b5|navajowhite ffdead|oldlace fdf5e6|olivedrab 6b8e23|
        orangered ff4500|orchid da70d6|palegoldenrod eee8aa|palegreen 98fb98|paleturquoise afeeee|palevioletred db7093|papayawhip ffefd5|
        peachpuff ffdab9|peru cd853f|pink ffc0cb|plum dda0dd|powderblue b0e0e6|rosybrown bc8f8f|royalblue 4169e1|saddlebrown 8b4513|
        salmon fa8072|sandybrown f4a460|seagreen 2e8b57|seashell fff5ee|sienna a0522d|skyblue 87ceeb|slateblue 6a5acd|slategray 708090|
        snow fffafa|springgreen 00ff7f|steelblue 4682b4|tan d2b48c|thistle d8bfd8|tomato ff6347|turquoise 40e0d0|violet ee82ee|
        wheat f5deb3|whitesmoke f5f5f5|yellowgreen 9acd32
    """.trimIndent().replace("\n", "").split('|').filter { it.isNotBlank() }.associate {
        val (n, h) = it.trim().split(' ')
        n to h.toInt(16)
    }

    private val shades = mapOf("light" to 0.18, "pale" to 0.28, "dark" to -0.18, "deep" to -0.14, "bright" to 0.0, "vivid" to 0.0)
    private val filler = setOf("colour", "color", "shade", "of", "the", "a")

    private val wordsOnly = Regex("""^[A-Za-z][A-Za-z' \-]{1,28}$""")

    fun candidates(text: String, source: Source): List<Candidate> {
        if (source != Source.SELECTION) return emptyList()
        val t = text.trim()
        if (!wordsOnly.matches(t)) return emptyList()
        val words = ColourNames.normalise(t).split(' ').filter { it !in filler }
        if (words.isEmpty() || words.size > 4) return emptyList()
        val key = words.joinToString(" ")

        // 1. A plain colour word: the shade everyone means.
        css[key.replace(" ", "")]?.let { rgb ->
            return listOf(Candidate("colour-name", if (words.size == 1) 0.55 else 0.7) { make(rgb, title(key), "The usual web shade") })
        }
        // 2. An exact name from the big list ("Acid Green", "Baby Blue").
        ColourNames.find(key)?.let { rgb ->
            return listOf(Candidate("colour-name", 0.7) { make(rgb, title(key), "From the colour name list") })
        }
        // 3. "light green", "dark red": a plain colour word with a shade word in front.
        if (words.size == 2 && shades.containsKey(words[0])) {
            val base = css[words[1]]
            if (base != null) {
                val rgb = shade(base, words[0], shades.getValue(words[0]))
                return listOf(Candidate("colour-name", 0.6) { make(rgb, title(key), "A $key, estimated") })
            }
        }
        return emptyList()
    }

    private fun title(key: String) = key.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase(Locale.ROOT) } }

    private fun shade(rgb: Int, word: String, dl: Double): Int {
        val r = (rgb shr 16) and 255; val g = (rgb shr 8) and 255; val b = rgb and 255
        val (h, s, l) = ColourDetector.rgbToHsl(r, g, b)
        var ns = s / 100.0
        var nl = l / 100.0 + dl
        if (word == "bright" || word == "vivid") ns = (ns + 0.15).coerceAtMost(1.0)
        if (word == "pale") ns *= 0.8
        nl = nl.coerceIn(0.05, 0.95)
        val c = ColourDetector.hslToRgb(h.toDouble(), ns, nl)
        return (c[0] shl 16) or (c[1] shl 8) or c[2]
    }

    private fun make(rgb: Int, name: String, note: String): InspectResult {
        val r = ColourDetector.build(intArrayOf((rgb shr 16) and 255, (rgb shr 8) and 255, rgb and 255, 255), name)
        return r.copy(rows = r.rows + ("Note" to note), subtitle = name)
    }
}
