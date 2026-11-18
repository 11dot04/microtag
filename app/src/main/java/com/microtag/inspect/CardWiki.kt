package com.microtag.inspect

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.ui.FitText
import com.microtag.ui.Montserrat
import com.microtag.ui.TagColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// ==========================================================
// WIKI. The hero card: a portrait poster.
//   - the photo is the backdrop, toned to one dim hue, bleeding off the right edge; the part that runs off
//     comes back as a seam on the left. It never reaches the bottom, and there is no frame or hatch.
//   - the name and what the person or place is are set as one block of light capitals at ONE size, chosen so
//     the longest word fills the width. Words are never split. The second line of the name hangs to the right.
//   - the years sit staggered low on the right (places show their coordinates there instead; with neither,
//     the slot is simply empty and nothing else moves)
//   - a very light scrim under the type; no Difference blend
//   - without a photo the same layout runs with a ghosted monogram where the photo would be
// ==========================================================

/** Line height for the title block: tighter the more lines there are. */
internal fun leadFor(lines: Int): Float = when {
    lines <= 2 -> 0.90f
    lines == 3 -> 0.86f
    lines == 4 -> 0.82f
    else -> 0.78f
}

/** The summary without its dates, and without the full name repeated at the start ("Euler was a Swiss..."). */
internal fun cleanBlurb(title: String, body: String, person: Boolean): String {
    var t = body.trim()
    Regex("""\s*\([^()]*\d{3,4}[^()]*\)""").find(t)?.let { if (it.range.first < 120) t = t.removeRange(it.range) }
    if (person && t.startsWith(title) && t.length > title.length) t = WikiPill.surname(title) + t.substring(title.length)
    return t
}

private class Duo(val hue: Float) {
    val dark = Color.hsv(hue, 0.62f, 0.085f)
    val light = Color.hsv(hue, 0.20f, 0.60f)
    val ghost = Color.hsv(hue, 0.38f, 0.24f)
    val tint = Color.hsv(hue, 0.30f, 0.66f)

    /** A gentle duotone: lower contrast and a dark ceiling, so white type sits on the photo without a fight. */
    fun filter(contrast: Float = 0.95f): ColorFilter {
        fun row(d: Float, l: Float): FloatArray {
            val k = contrast * (l - d)
            val off = ((0.5f - 0.5f * contrast) * (l - d) + d) * 255f
            return floatArrayOf(k * 0.2126f, k * 0.7152f, k * 0.0722f, 0f, off)
        }
        val r = row(dark.red, light.red); val g = row(dark.green, light.green); val b = row(dark.blue, light.blue)
        return ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(*r, *g, *b, 0f, 0f, 0f, 1f, 0f)))
    }
}

/** The photo's dominant hue, or a title-based fallback for photos without one. */
private fun dominantHue(b: Bitmap, fallback: Float): Float {
    val n = 24
    val s = Bitmap.createScaledBitmap(b, n, n, true)
    val px = IntArray(n * n)
    s.getPixels(px, 0, n, 0, 0, n, n)
    val hsv = FloatArray(3)
    var x = 0.0; var y = 0.0; var weight = 0.0
    for (c in px) {
        android.graphics.Color.colorToHSV(c, hsv)
        if (hsv[1] > 0.18f && hsv[2] > 0.2f) {
            val w = (hsv[1] * hsv[2]).toDouble()
            x += w * cos(Math.toRadians(hsv[0].toDouble())); y += w * sin(Math.toRadians(hsv[0].toDouble())); weight += w
        }
    }
    if (weight < n * n * 0.03 || hypot(x, y) < weight * 0.25) return fallback
    return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
}

private val fallbackHues = listOf(195f, 212f, 28f, 345f, 150f, 265f)

private fun initialsOf(title: String): String {
    val words = title.split(' ').filter { it.isNotEmpty() && it.lowercase() !in setOf("the", "a", "an", "of") }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> "${words[0].first()}${words[1].first()}".uppercase()
    }
}

/** Draws the photo as one strip of [totW] x [h]: the first [visW] at [left], the remainder wrapped to the card's left edge. */
private fun DrawScope.photoStrip(img: ImageBitmap, left: Float, top: Float, visW: Float, totW: Float, h: Float, filter: ColorFilter) {
    val target = totW / h
    val ia = img.width.toFloat() / img.height
    val cw: Int
    val ch: Int
    if (ia > target) { ch = img.height; cw = (img.height * target).toInt() } else { cw = img.width; ch = (img.width / target).toInt() }
    val ox = (img.width - cw) / 2
    val oy = ((img.height - ch) * 0.1f).toInt()
    val mainSrcW = if (totW - visW < 1f) cw else (cw * visW / totW).toInt().coerceIn(1, cw - 1)
    drawImage(
        img, IntOffset(ox, oy), IntSize(mainSrcW, ch),
        IntOffset(left.roundToInt(), top.roundToInt()), IntSize(visW.roundToInt(), h.roundToInt()), colorFilter = filter
    )
    if (totW - visW < 1f) return   // full width: no seam to draw
    drawImage(
        img, IntOffset(ox + mainSrcW, oy), IntSize(cw - mainSrcW, ch),
        IntOffset(0, top.roundToInt()), IntSize((totW - visW).roundToInt().coerceAtLeast(1), h.roundToInt()), colorFilter = filter
    )
}

private val abbreviations = setOf(
    "mr", "mrs", "ms", "dr", "prof", "st", "jr", "sr", "vs", "etc", "inc", "ltd", "co", "no", "vol", "ca", "c", "approx", "e.g", "i.e", "u.s", "u.k", "gen", "col", "lt", "capt", "mt", "ft", "dept", "est"
)

/** Splits at the end of a sentence, but not after "Dr.", "St.", "U.S.", an initial like "J." or a number's point. */
internal fun splitSentences(text: String): List<String> {
    val out = ArrayList<String>()
    var start = 0
    val ender = Regex("""[.!?]["”')\]]*\s+(?=["“'(\[]?[A-Z0-9])""")
    for (m in ender.findAll(text)) {
        val before = text.substring(start, m.range.first + 1)
        val token = before.trimEnd('.', '!', '?', '"', '”', '\'', ')', ']').substringAfterLast(' ')
        val isAbbrev = m.value.startsWith(".") && (token.lowercase() in abbreviations || (token.length <= 2 && token.firstOrNull()?.isUpperCase() == true))
        if (isAbbrev) continue
        out.add(text.substring(start, m.range.first + m.value.trimEnd().length).trim())
        start = m.range.last + 1
    }
    if (start < text.length) out.add(text.substring(start).trim())
    return out.filter { it.isNotEmpty() }
}

/**
 * Splits a summary into what fits above the photo and what continues below it, at a sentence boundary: as many whole
 * sentences as fit in [maxLines] lines go above, the rest below. If even the first sentence does not fit, the line is
 * filled to the last whole word and the rest of that sentence continues below.
 */
internal fun splitBlurb(tm: androidx.compose.ui.text.TextMeasurer, text: String, style: TextStyle, widthPx: Int, maxLines: Int): Pair<String, String> {
    fun lines(t: String) = tm.measure(AnnotatedString(t), style, constraints = Constraints(maxWidth = widthPx)).lineCount
    if (lines(text) <= maxLines) return text to ""
    val sentences = splitSentences(text)
    var acc = ""
    var used = 0
    for (s in sentences) {
        val next = if (acc.isEmpty()) s else "$acc $s"
        if (lines(next) <= maxLines) { acc = next; used++ } else break
    }
    if (used > 0) return acc to sentences.drop(used).joinToString(" ")
    val words = text.split(' ')
    var cur = ""
    var n = 0
    for (w in words) {
        val next = if (cur.isEmpty()) w else "$cur $w"
        if (lines(next) <= maxLines) { cur = next; n++ } else break
    }
    return cur to words.drop(n).joinToString(" ")
}

/**
 * Two passages, both ending on a whole sentence: as many whole sentences as fit [linesA] lines go in the first, the next
 * ones that fit [linesB] lines go in the second, and whatever is left is dropped rather than cut mid-sentence. Only when
 * the very first sentence is longer than the first passage does it fall back to [splitBlurb] (filled to the last whole
 * word, the rest continuing in the second).
 */
internal fun splitBlurbWhole(
    tm: androidx.compose.ui.text.TextMeasurer, text: String, styleA: TextStyle, styleB: TextStyle,
    widthA: Int, widthB: Int, linesA: Int, linesB: Int
): Pair<String, String> {
    fun lines(t: String, st: TextStyle, wd: Int) = tm.measure(AnnotatedString(t), st, constraints = Constraints(maxWidth = wd)).lineCount
    val sentences = splitSentences(text)
    var a = ""
    var used = 0
    for (x in sentences) {
        val next = if (a.isEmpty()) x else "$a $x"
        if (lines(next, styleA, widthA) <= linesA) { a = next; used++ } else break
    }
    if (used == 0) return splitBlurb(tm, text, styleA, widthA, linesA)
    var b = ""
    if (linesB > 0) {
        for (x in sentences.drop(used)) {
            val next = if (b.isEmpty()) x else "$b $x"
            if (lines(next, styleB, widthB) <= linesB) b = next else break
        }
    }
    return a to b
}

/** Reads bottom to top: the text is laid out on its side, and takes the room a rotated line really takes. */
private fun Modifier.readsUpward(): Modifier = this.layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(placeable.height, placeable.width) {
        placeable.placeWithLayer(0, placeable.width) {
            rotationZ = -90f
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
        }
    }
}

private class Loaded(val bmp: Bitmap?)

@Composable
internal fun WikiCard(c: CardCtx, modifier: Modifier) {
    CardFrame(c.p, modifier, c.drag, Surface.Black, 520.dp, padded = false) {
        if (c.p.parts.isNotEmpty()) Disambiguation(c) else Poster(c)
    }
}

@Composable
private fun Poster(c: CardCtx) {
    val p = c.p
    val loaded by produceState<Loaded?>(null, p.imageUrl) {
        value = if (p.imageUrl.isBlank()) Loaded(null)
        else Loaded(withContext(Dispatchers.IO) { runCatching { CardImages.load(p.imageUrl, 720) }.getOrNull() })
    }
    val photo = loaded?.bmp
    val waiting = loaded == null
    val fallbackHue = remember(p.title) { fallbackHues[abs(p.title.hashCode()) % fallbackHues.size] }
    val duo = remember(photo, fallbackHue) { Duo(photo?.let { dominantHue(it, fallbackHue) } ?: fallbackHue) }
    val image: ImageBitmap? = remember(photo) { photo?.asImageBitmap() }
    val filter = remember(duo) { duo.filter() }

    val (kicker, years) = remember(p.subText) { splitYears(p.subText) }
    val person = WikiPill.isPerson(p.subText)
    val blurb = remember(p.title, p.fullContent, person) { cleanBlurb(p.title, p.fullContent, person) }
    val stagger = remember(years, p.fact) { staggerOf(years, p.fact) }
    val name = remember(p.title) { p.title.uppercase() }
    val desc = remember(kicker) { shortKicker(kicker).uppercase().let { if (it.isBlank()) "" else "— $it" } }
    val heroColour = if (c.flash == p.title) c.accent else Color.White
    val ctx = remember(c, duo) { c.withAccent(duo.tint) }

    val tm = rememberTextMeasurer()
    val density = LocalDensity.current
    val screenH = LocalConfiguration.current.screenHeightDp

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth
        val h = minOf(w / 0.6f, (screenH * 0.84f).dp).coerceAtLeast(440.dp)
        val u = min(w.value / 340f, h.value / 567f)
        fun fx(f: Float): Dp = w * f
        fun fy(f: Float): Dp = h * f

        val bandTop = fy(0.346f)
        val bandH = fy(0.484f)
        // The photo keeps its own shape. A portrait or square photo bleeds off the right edge and comes back as a seam
        // on the left; a landscape one runs the full width as one piece. Never cropped to a portrait frame.
        val natural: Dp? = photo?.let { bandH * (it.width.toFloat() / it.height) }
        val fullWidth = natural != null && natural >= w * 0.9f
        val bandLeft = if (fullWidth) 0.dp else fx(0.45f)
        val bandTotal = when {
            natural == null -> fx(0.635f)
            fullWidth -> w
            else -> maxOf(natural, fx(0.635f))
        }

        // The name and what it is: one block, one size.
        val baseStyle = sans(1f, FontWeight.Normal, Color.White, 0.4f)
        val widthPx = with(density) { fx(0.865f).toPx() }
        val blocks = if (desc.isBlank()) listOf(name) else listOf(name, desc)
        val heightPx = with(density) { bandH.toPx() * 0.88f }
        val spPx = density.density * density.fontScale
        val fit = remember(blocks, widthPx, u, heightPx) {
            fitWordBlocks(tm, blocks, baseStyle, widthPx, 46f * u, 15f * u, 8, heightPx, spPx, ::leadFor)
        }
        val lead = leadFor(fit.lines.sumOf { it.size })
        val loose = baseStyle.copy(fontSize = fit.size.sp)
        val nameLines = fit.lines.getOrElse(0) { emptyList() }
        val descLines = fit.lines.getOrElse(1) { emptyList() }
        val nameWidth = remember(nameLines, fit.size) {
            val px = nameLines.maxOfOrNull { tm.measure(it, baseStyle.copy(fontSize = fit.size.sp), softWrap = false, maxLines = 1).size.width } ?: 0
            with(density) { px.toDp() }
        }

        Box(Modifier.fillMaxWidth().height(h)) {
            // The photo strip (or its ghost), then a very light scrim.
            Canvas(Modifier.fillMaxSize()) {
                val left = bandLeft.toPx(); val top = bandTop.toPx(); val bh = bandH.toPx()
                val tot = bandTotal.toPx(); val vis = size.width - left
                if (image != null) {
                    photoStrip(image, left, top, vis, tot, bh, filter)
                } else {
                    drawRect(duo.dark, Offset(left, top), Size(vis, bh))
                    drawRect(duo.dark, Offset(0f, top), Size(tot - vis, bh))
                    if (!waiting) {
                        val ini = initialsOf(p.title)
                        val style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Black, letterSpacing = (-4).sp)
                        var m = tm.measure(ini, style.copy(fontSize = (bh * 0.62f).toSp()), softWrap = false, maxLines = 1)
                        if (m.size.width > tot * 0.96f) {
                            m = tm.measure(ini, style.copy(fontSize = (bh * 0.62f * tot * 0.96f / m.size.width).toSp()), softWrap = false, maxLines = 1)
                        }
                        val tx = (tot - m.size.width) / 2f
                        val ty = top + (bh - m.size.height) / 2f
                        clipRect(left, top, left + vis, top + bh) { drawText(m, color = duo.ghost, topLeft = Offset(left + tx, ty)) }
                        clipRect(0f, top, tot - vis, top + bh) { drawText(m, color = duo.ghost, topLeft = Offset(tx - vis, ty)) }
                    }
                }
                val scrim = listOf(Color.Transparent, Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.2f), Color.Transparent)
                drawRect(Brush.verticalGradient(scrim, startY = top, endY = top + bh), Offset(left, top), Size(vis, bh))
                drawRect(Brush.verticalGradient(scrim, startY = top, endY = top + bh), Offset(0f, top), Size(tot - vis, bh))
            }

            // Header: category and OPEN.
            Box(Modifier.padding(start = fx(0.085f), end = fx(0.09f), top = fy(0.037f))) { CardHeader(ctx, arrowHere = false) }

            // ---- The summary: two passages, aligned, both ending on whole sentences.
            // The first runs from under the header down to the title block, riding the top of the photo. The second starts
            // under the title and runs toward the foot. The figures (years or coordinates) sit low on the left; when the
            // whole summary would not fit beside them they turn on their side so the second passage can have the full height.
            val lineH = 16.5f * u
            val blurbTop = fy(0.125f)
            val gap = fy(0.016f)
            val titleH = (fit.lines.sumOf { it.size } * fit.size * lead * density.fontScale).dp
            val bandBottom = bandTop + bandH
            val titleTop = bandTop + (bandH - titleH) / 2f
            val titleBottom = bandTop + (bandH + titleH) / 2f
            val arrowBottom = bandTop - fy(0.014f)
            val aBottom = maxOf(arrowBottom, titleTop - gap)
            val linesA = floor((aBottom - blurbTop).value / lineH).toInt().coerceAtLeast(3)
            val bTop = titleBottom + gap
            val footY = fy(0.968f)
            val linesBFull = floor((footY - bTop).value / lineH).toInt().coerceAtLeast(0)
            val linesBBeside = if (stagger == null) linesBFull else floor((fy(0.80f) - bTop).value / lineH).toInt().coerceAtLeast(0)
            val blurbStyle = TextStyle(
                fontFamily = Montserrat, fontWeight = FontWeight.Normal, fontSize = (12f * u).sp, lineHeight = lineH.sp,
                color = Paper.Ink.copy(alpha = 0.92f), textIndent = TextIndent(firstLine = (30f * u).sp),
                lineBreak = LineBreak.Paragraph, hyphens = Hyphens.Auto
            )
            val styleB = blurbStyle.copy(textIndent = null)
            val colW = with(density) { fx(0.56f).toPx() }.toInt()
            val squash = remember(blurb) { blurb.replace(Regex("\\s+"), " ").trim() }
            val beside = remember(squash, colW, linesA, linesBBeside, u) {
                splitBlurbWhole(tm, squash, blurbStyle, styleB, colW, colW, linesA, linesBBeside)
            }
            val allShownBeside = (beside.first + " " + beside.second).trim() == squash
            val vertical = stagger != null && !allShownBeside
            val (partA, partB) = if (!vertical) beside else remember(squash, colW, linesA, linesBFull, u) {
                splitBlurbWhole(tm, squash, blurbStyle, styleB, colW, colW, linesA, linesBFull)
            }
            val linesB = if (vertical) linesBFull else linesBBeside

            // A soft shade where the first passage rides the photo.
            val aUsed = remember(partA, colW, u) {
                tm.measure(AnnotatedString(partA), blurbStyle, constraints = Constraints(maxWidth = colW)).lineCount
            }.coerceAtMost(linesA)
            val aTextBottom = blurbTop + (lineH * aUsed).dp
            if (partA.isNotBlank() && aTextBottom > bandTop) {
                Box(
                    Modifier.offset(y = bandTop).fillMaxWidth().height(aTextBottom - bandTop + fy(0.02f))
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.5f), Color.Transparent)))
                )
            }
            if (partA.isNotBlank()) {
                Text(
                    partA, style = blurbStyle, maxLines = linesA, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.offset(x = fx(0.37f), y = blurbTop).width(fx(0.56f)).plainClick { c.onCopy(blurb) }
                )
            }
            val bOnPhoto = bTop < bandBottom - (lineH * 2f).dp
            if (partB.isNotBlank() && linesB > 0) {
                if (bOnPhoto) {
                    Box(
                        Modifier.offset(y = bTop - fy(0.035f)).fillMaxWidth().height(bandBottom - bTop + fy(0.035f))
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                    )
                }
                Text(
                    partB,
                    style = styleB.copy(color = if (bOnPhoto) Color.White.copy(alpha = 0.94f) else Paper.Ink.copy(alpha = 0.92f)),
                    maxLines = linesB, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.offset(x = fx(0.37f), y = bTop).width(fx(0.56f)).plainClick { c.onCopy(blurb) }
                )
            }
            val arrowIcon = fx(0.19f)
            ShareArrowAt(c, Modifier.offset(x = fx(0.085f) - 7.dp, y = arrowBottom - arrowIcon - 14.dp), arrowIcon)

            // The title block, centred on the photo band.
            Box(Modifier.offset(y = bandTop).fillMaxWidth().height(bandH), contentAlignment = Alignment.CenterStart) {
                Column(Modifier.padding(start = fx(0.086f)).width(fx(0.865f))) {
                    Column(Modifier.width(nameWidth).plainClick { c.onCopy(p.title) }) {
                        nameLines.forEachIndexed { i, line ->
                            TightLine(
                                line, loose.copy(color = heroColour), lead,
                                Modifier.align(if (i % 2 == 0) Alignment.Start else Alignment.End)
                            )
                        }
                    }
                    descLines.forEach { line ->
                        TightLine(line, loose.copy(color = Color.White.copy(alpha = 0.74f)), lead)
                    }
                }
            }

            // The staggered pair: years, or coordinates. Absent data leaves the slot empty. Horizontal when the summary
            // clears it; on its side along the left margin when the second passage needs the height.
            if (stagger != null) {
                val sp = (w.value * 0.095f) * (if (years.isBlank()) 0.78f else 1f)
                if (!vertical) {
                    val big = sans(sp, FontWeight.Bold, Paper.Ink, -0.5f).tight(0.95f)
                    // On the left, lined up with the headline: the first figure at the margin, the second stepped in under it.
                    Text(stagger.first, style = big, softWrap = false, maxLines = 1, modifier = Modifier.offset(x = fx(0.085f), y = fy(0.806f)))
                    if (stagger.second.isNotEmpty()) {
                        Text(stagger.second, style = big, softWrap = false, maxLines = 1, modifier = Modifier.offset(x = fx(0.17f), y = fy(0.868f)))
                    }
                } else {
                    val line = if (stagger.second.isEmpty()) stagger.first else if (years.isBlank()) stagger.first + "   " + stagger.second else stagger.first + stagger.second
                    val room = with(density) { (footY - bTop).toPx() }
                    var size = sp * 0.8f
                    var m = tm.measure(line, sans(size, FontWeight.Bold, Paper.Ink, -0.5f).tight(0.95f), softWrap = false, maxLines = 1)
                    if (m.size.width > room) {
                        size = (size * room / m.size.width).coerceAtLeast(9f)
                        m = tm.measure(line, sans(size, FontWeight.Bold, Paper.Ink, -0.5f).tight(0.95f), softWrap = false, maxLines = 1)
                    }
                    val len = with(density) { m.size.width.toDp() }
                    Text(
                        line, style = sans(size, FontWeight.Bold, Paper.Ink, -0.5f).tight(0.95f), softWrap = false, maxLines = 1,
                        modifier = Modifier.offset(x = fx(0.085f), y = footY - len).readsUpward()
                    )
                }
            }

        }
    }
}

@Composable
private fun ShareArrowAt(c: CardCtx, modifier: Modifier, icon: Dp) {
    Box(modifier) { ShareArrow(c.onShare, Color(0xFFA6A6A6), iconSize = icon) }
}

/** "Mercury" can mean several things: the name, then the choices, numbered. */
@Composable
private fun Disambiguation(c: CardCtx) {
    val p = c.p
    Column(Modifier.padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 26.dp)) {
        CardHeader(c, arrowHere = true)
        Spacer(Modifier.height(14.dp))
        FitText(
            AnnotatedString(p.title.uppercase()),
            sans(34f, FontWeight.Normal, Paper.Ink, 0.4f).tight(1f),
            Modifier.fillMaxWidth(), minSize = 18.sp
        )
        Text("— DISAMBIGUATION", style = sans(13f, FontWeight.Normal, Paper.Ink.copy(alpha = 0.7f), 1f))
        if (p.fullContent.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(p.fullContent, style = sans(13f, FontWeight.Normal, Paper.Ink.copy(alpha = 0.85f)).copy(lineHeight = 19.sp))
        }
        Spacer(Modifier.height(12.dp))
        p.parts.forEachIndexed { i, line ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).plainClick { c.onCopy(line) }) {
                Text(
                    (i + 1).toString().padStart(2, '0'), style = mono(11f, c.accent, 0.5f, FontWeight.Bold),
                    modifier = Modifier.width(28.dp).padding(top = 2.dp)
                )
                Text(line, style = sans(14f, FontWeight.Medium, TagColors.White).copy(lineHeight = 19.sp), modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(14.dp))
        Caption("source [wikipedia]", color = Paper.Mute, size = 9f)
    }
}
