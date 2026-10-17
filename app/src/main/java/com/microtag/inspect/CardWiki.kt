package com.microtag.inspect

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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

/** "Swiss mathematician (1707–1783)" -> kicker "Swiss mathematician", years "1707–1783". Years only when they exist. */
internal fun splitYears(desc: String): Pair<String, String> {
    Regex("""\s*\((?:born\s+|b\.\s*)?(\d{3,4})(?:\s*[–-]\s*(\d{3,4}|present))?\)""", RegexOption.IGNORE_CASE).find(desc)?.let { m ->
        val years = m.groupValues[1] + if (m.groupValues[2].isNotEmpty()) "–" + m.groupValues[2] else ""
        return desc.removeRange(m.range).trim() to years
    }
    Regex("""^(\d{4})\s+(.+)$""").find(desc)?.let { return it.groupValues[2] to it.groupValues[1] }
    return desc to ""
}

/** "Swiss mathematician and physicist" -> "Swiss mathematician": the first clause, at most three words. */
internal fun shortKicker(desc: String): String {
    val first = desc.split(Regex("""\s*(?:,|;|\s[–—-]\s|\s+and\s+|\s+who\s+|\s+that\s+)""")).first().trim()
    return first.split(' ').filter { it.isNotEmpty() }.take(3).joinToString(" ")
}

/** The summary without its dates, and without the full name repeated at the start ("Euler was a Swiss..."). */
internal fun cleanBlurb(title: String, body: String, person: Boolean): String {
    var t = body.trim()
    Regex("""\s*\([^()]*\d{3,4}[^()]*\)""").find(t)?.let { if (it.range.first < 120) t = t.removeRange(it.range) }
    if (person && t.startsWith(title) && t.length > title.length) t = WikiPill.surname(title) + t.substring(title.length)
    return t
}

/** The staggered pair: ("1707", "–1783") for years, ("48.86° N", "2.35° E") for places, or null. */
internal fun staggerOf(years: String, fact: String): Pair<String, String>? {
    if (years.isNotBlank()) {
        val parts = years.split('–')
        val a = parts[0]
        val b = parts.getOrNull(1).orEmpty()
        return a to when {
            b.isBlank() -> ""
            b.equals("present", true) -> "–NOW"
            else -> "–$b"
        }
    }
    if (fact.contains('|')) {
        val f = fact.split('|')
        return f[0] to f.getOrElse(1) { "" }
    }
    return null
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
    val mainSrcW = (cw * visW / totW).toInt().coerceIn(1, cw - 1)
    drawImage(
        img, IntOffset(ox, oy), IntSize(mainSrcW, ch),
        IntOffset(left.roundToInt(), top.roundToInt()), IntSize(visW.roundToInt(), h.roundToInt()), colorFilter = filter
    )
    drawImage(
        img, IntOffset(ox + mainSrcW, oy), IntSize(cw - mainSrcW, ch),
        IntOffset(0, top.roundToInt()), IntSize((totW - visW).roundToInt().coerceAtLeast(1), h.roundToInt()), colorFilter = filter
    )
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
    val person = years.isNotBlank() || WikiPill.isPerson(p.subText)
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
        val bandLeft = fx(0.45f)
        val bandTotal = fx(0.635f)

        // The name and what it is: one block, one size.
        val baseStyle = sans(1f, FontWeight.Normal, Color.White, 0.4f)
        val widthPx = with(density) { fx(0.865f).toPx() }
        val blocks = if (desc.isBlank()) listOf(name) else listOf(name, desc)
        val fit = remember(blocks, widthPx, u) { fitWordBlocks(tm, blocks, baseStyle, widthPx, 46f * u, 15f * u, 6) }
        val lineStyle = baseStyle.copy(fontSize = fit.size.sp).tight(0.98f)
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

            // The summary, ragged-right with a first-line indent, and the big arrow beside it.
            val blurbTop = fy(0.125f)
            val blurbBottom = bandTop - fy(0.014f)
            val lineH = 16.5f * u
            val maxLines = floor((blurbBottom - blurbTop).value / lineH).toInt().coerceAtLeast(3)
            if (blurb.isNotBlank()) {
                Text(
                    blurb,
                    style = TextStyle(
                        fontFamily = Montserrat, fontWeight = FontWeight.Normal, fontSize = (12f * u).sp, lineHeight = lineH.sp,
                        color = Color.White.copy(alpha = 0.92f), textIndent = TextIndent(firstLine = (14f * u).sp),
                        lineBreak = LineBreak.Paragraph, hyphens = Hyphens.Auto
                    ),
                    maxLines = maxLines, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.offset(x = fx(0.37f), y = blurbTop).width(fx(0.56f)).plainClick { c.onCopy(blurb) }
                )
            }
            val arrowIcon = fx(0.19f)
            ShareArrowAt(c, Modifier.offset(x = fx(0.085f) - 7.dp, y = blurbBottom - arrowIcon - 14.dp), arrowIcon)

            // The title block, centred on the photo band.
            Box(Modifier.offset(y = bandTop).fillMaxWidth().height(bandH), contentAlignment = Alignment.CenterStart) {
                Column(Modifier.padding(start = fx(0.086f)).width(fx(0.865f))) {
                    Column(Modifier.width(nameWidth).plainClick { c.onCopy(p.title) }) {
                        nameLines.forEachIndexed { i, line ->
                            Text(
                                line, style = lineStyle.copy(color = heroColour), softWrap = false, maxLines = 1,
                                modifier = Modifier.align(if (i % 2 == 0) Alignment.Start else Alignment.End)
                            )
                        }
                    }
                    descLines.forEach { line ->
                        Text(line, style = lineStyle.copy(color = Color.White.copy(alpha = 0.74f)), softWrap = false, maxLines = 1)
                    }
                }
            }

            // The staggered pair: years, or coordinates. Absent data leaves the slot empty.
            if (stagger != null) {
                val sp = (w.value * 0.095f) * (if (years.isBlank()) 0.78f else 1f)
                val big = sans(sp, FontWeight.Bold, Color.White, -0.5f).tight(0.95f)
                Text(stagger.first, style = big, softWrap = false, maxLines = 1, modifier = Modifier.offset(x = fx(0.50f), y = fy(0.806f)))
                if (stagger.second.isNotEmpty()) {
                    Box(Modifier.offset(y = fy(0.868f)).fillMaxWidth().padding(end = fx(0.15f)), contentAlignment = Alignment.CenterEnd) {
                        Text(stagger.second, style = big, softWrap = false, maxLines = 1)
                    }
                }
            }

            // Source, bottom left.
            Column(Modifier.offset(x = fx(0.085f), y = fy(0.915f)), horizontalAlignment = Alignment.End) {
                val src = sans(12f * u, FontWeight.Light, Color(0xFF9A9A9A))
                Text("source", style = src)
                Text("[wikipedia]", style = src)
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
            sans(34f, FontWeight.Normal, Color.White, 0.4f).tight(1f),
            Modifier.fillMaxWidth(), minSize = 18.sp
        )
        Text("— DISAMBIGUATION", style = sans(13f, FontWeight.Normal, Color.White.copy(alpha = 0.7f), 1f))
        if (p.fullContent.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(p.fullContent, style = sans(13f, FontWeight.Normal, Color.White.copy(alpha = 0.85f)).copy(lineHeight = 19.sp))
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
