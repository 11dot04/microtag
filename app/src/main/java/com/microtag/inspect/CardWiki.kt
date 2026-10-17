package com.microtag.inspect

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.ui.TagColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ==========================================================
// WIKI. A poster, not a card: asymmetric, sans-serif, the title XOR-ed across the photo's edge.
//   - the photo keeps its shape, bleeds off the left or right edge, and is toned to one dominant hue (duotone)
//   - the body sits beside the photo (when it is short), below it, or above it; chosen from a hash of the title
//   - the title is placed by looking at the picture: the spot where Difference reads best
//   - no photo, no frame and no hatch: just type
// ==========================================================

private enum class Layout { Beside, Below, Above }

/** "Swiss mathematician (1707–1783)" -> kicker "Swiss mathematician", years "1707–1783". Years only when they exist. */
internal fun splitYears(desc: String): Pair<String, String> {
    Regex("""\s*\((?:born\s+|b\.\s*)?(\d{3,4})(?:\s*[–-]\s*(\d{3,4}|present))?\)""", RegexOption.IGNORE_CASE).find(desc)?.let { m ->
        val years = m.groupValues[1] + if (m.groupValues[2].isNotEmpty()) "–" + m.groupValues[2] else ""
        return desc.removeRange(m.range).trim() to years
    }
    Regex("""^(\d{4})\s+(.+)$""").find(desc)?.let { return it.groupValues[2] to it.groupValues[1] }
    return desc to ""
}

private class Tone(val dark: Color, val light: Color, val grid: FloatArray, val n: Int) {
    /** Mean distance from mid-grey over a region (fractions of the photo): high = very light or very dark = Difference reads. */
    fun score(l: Float, t: Float, r: Float, b: Float): Float {
        var sum = 0f; var count = 0
        for (gy in 0 until n) for (gx in 0 until n) {
            val cx = (gx + 0.5f) / n; val cy = (gy + 0.5f) / n
            if (cx in l..r && cy in t..b) { sum += abs(1f - 2f * grid[gy * n + gx]); count++ }
        }
        return if (count == 0) -1f else sum / count
    }

    fun filter(contrast: Float = 1.12f): ColorFilter {
        fun row(d: Float, l: Float): FloatArray {
            val k = contrast * (l - d)
            val off = ((0.5f - 0.5f * contrast) * (l - d) + d) * 255f
            return floatArrayOf(k * 0.2126f, k * 0.7152f, k * 0.0722f, 0f, off)
        }
        val r = row(dark.red, light.red); val g = row(dark.green, light.green); val b = row(dark.blue, light.blue)
        return ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(*r, *g, *b, 0f, 0f, 0f, 1f, 0f)))
    }
}

private fun analyse(b: Bitmap): Tone {
    val n = 24
    val s = Bitmap.createScaledBitmap(b, n, n, true)
    val px = IntArray(n * n)
    s.getPixels(px, 0, n, 0, 0, n, n)
    val grid = FloatArray(n * n)
    val hsv = FloatArray(3)
    var x = 0.0; var y = 0.0; var weight = 0.0
    for (i in px.indices) {
        val c = px[i]
        val r = ((c shr 16) and 0xFF) / 255f; val g = ((c shr 8) and 0xFF) / 255f; val bl = (c and 0xFF) / 255f
        grid[i] = 0.2126f * r + 0.7152f * g + 0.0722f * bl
        android.graphics.Color.colorToHSV(c, hsv)
        if (hsv[1] > 0.18f && hsv[2] > 0.2f) {
            val w = (hsv[1] * hsv[2]).toDouble()
            x += w * cos(Math.toRadians(hsv[0].toDouble())); y += w * sin(Math.toRadians(hsv[0].toDouble())); weight += w
        }
    }
    // A photo without a dominant hue falls back to a warm accent.
    val hue = if (weight < n * n * 0.03 || hypot(x, y) < weight * 0.25) 28f else ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    return Tone(Color.hsv(hue, 0.65f, 0.16f), Color.hsv(hue, 0.28f, 0.93f), grid, n)
}

private class Geo(
    val photo: Rect, val titleX: Float, val titleY: Float,
    val bodyX: Float, val bodyY: Float, val bodyW: Float, val areaH: Float
)

@Composable
internal fun WikiCard(c: CardCtx, modifier: Modifier) {
    val p = c.p
    val bmp by produceState<Bitmap?>(null, p.imageUrl) {
        value = if (p.imageUrl.isBlank()) null else withContext(Dispatchers.IO) { CardImages.load(p.imageUrl, 720) }
    }
    val photo = bmp
    val hasPhoto = p.imageUrl.isNotBlank()
    val tone = remember(photo) { photo?.let { analyse(it) } }
    val (kicker, years) = remember(p.subText) { splitYears(p.subText) }
    val tm = rememberTextMeasurer()
    val density = LocalDensity.current.density
    val seed = abs(p.title.hashCode())
    val ratio = photo?.let { it.width.toFloat() / it.height.toFloat() }?.coerceIn(0.62f, 1.75f) ?: 1.3f
    val body = p.fullContent
    val heroColour = if (c.flash == p.title) c.accent else TagColors.White
    val titleStyle = sans(46f, FontWeight.Black, Color.White, -1.2f)

    CardFrame(p, modifier, c.drag, Surface.Black, 520.dp, padded = false) {
        Column(Modifier.padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 22.dp)) {
            CardHeader(c, arrowHere = true)
            Spacer(Modifier.height(14.dp))

            if (!hasPhoto) {
                // Type only: no frame, no hatch.
                if (kicker.isNotBlank()) Caption(kicker, color = Paper.Ink.copy(alpha = 0.8f), size = 10f)
                Spacer(Modifier.height(8.dp))
                AutoWrapText(p.title, titleStyle.copy(color = heroColour), maxLines = 4, minSize = 26f, leading = 0.9f, onClick = { c.onCopy(p.title) })
                if (years.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(years, style = sans(56f, FontWeight.Black, c.accent, -2f).tight(0.9f), maxLines = 1)
                }
                if (body.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        body, style = sans(14f, FontWeight.Light, TagColors.White.copy(alpha = 0.86f)).copy(lineHeight = 21.sp),
                        maxLines = 9, overflow = TextOverflow.Ellipsis, modifier = Modifier.plainClick { c.onCopy(body) }
                    )
                }
                if (p.parts.isNotEmpty()) {
                    // A disambiguation page: the choices, numbered.
                    Spacer(Modifier.height(14.dp))
                    p.parts.forEachIndexed { i, line ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).plainClick { c.onCopy(line) }) {
                            Text((i + 1).toString().padStart(2, '0'), style = mono(11f, c.accent, 0.5f, FontWeight.Bold), modifier = Modifier.width(28.dp).padding(top = 2.dp))
                            Text(line, style = sans(14f, FontWeight.Medium, TagColors.White).copy(lineHeight = 19.sp), modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    if (kicker.isNotBlank()) Caption(kicker, Modifier.weight(1f), Paper.Ink.copy(alpha = 0.8f), 10f) else Spacer(Modifier.weight(1f))
                    if (years.isNotBlank()) Text(years, style = sans(40f, FontWeight.Black, Paper.Ink, -1.5f).tight(0.9f), maxLines = 1)
                }
                Spacer(Modifier.height(12.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val w = maxWidth.value
                    val bleed = 26f
                    val bleedRight = (seed / 3) % 2 == 0
                    val shortBody = body.length <= 150
                    val layout = when (seed % 3) {
                        0 -> if (shortBody && ratio <= 1.25f) Layout.Beside else Layout.Below
                        1 -> Layout.Below
                        else -> Layout.Above
                    }
                    // Title: as big as fits in up to three lines.
                    val wPx = (w * density).toInt()
                    val titleLayout: TextLayoutResult = remember(p.title, wPx) {
                        var s = 46f
                        var r = tm.measure(p.title, titleStyle.copy(fontSize = s.sp).tight(0.9f), constraints = Constraints(maxWidth = wPx), maxLines = 3, overflow = TextOverflow.Clip)
                        while (s > 24f && r.hasVisualOverflow) {
                            s *= 0.93f
                            r = tm.measure(p.title, titleStyle.copy(fontSize = s.sp).tight(0.9f), constraints = Constraints(maxWidth = wPx), maxLines = 3, overflow = TextOverflow.Clip)
                        }
                        r
                    }
                    val titleW = titleLayout.size.width / density
                    val titleH = titleLayout.size.height / density

                    val photoW0 = if (layout == Layout.Beside) w * 0.5f else w * 0.82f
                    val photoH = min(photoW0 / ratio, 260f)
                    val photoW = photoH * ratio
                    val bodyLines = if (layout == Layout.Beside) 9 else 6
                    val bodyW0 = if (layout == Layout.Beside) max(w - photoW + bleed - 14f, 90f) else w
                    val bodyLayout = remember(body, bodyW0) {
                        tm.measure(
                            body, sans(13f, FontWeight.Light, Color.White).copy(lineHeight = 19.sp),
                            constraints = Constraints(maxWidth = (bodyW0 * density).toInt()), maxLines = bodyLines, overflow = TextOverflow.Ellipsis
                        )
                    }
                    val bodyH = if (body.isBlank()) 0f else bodyLayout.size.height / density

                    fun geo(inside: Float, alignRight: Boolean): Geo {
                        val px0 = if (bleedRight) w - photoW + bleed else -bleed
                        val tx = if (alignRight) w - titleW else 0f
                        return when (layout) {
                            Layout.Below -> {
                                val top = 10f
                                val ty = top + photoH - inside * titleH
                                val by = max(top + photoH, ty + titleH) + 14f
                                Geo(Rect(Offset(px0, top), Size(photoW, photoH)), tx, ty, 0f, by, w, by + bodyH + 2f)
                            }
                            Layout.Above -> {
                                val ty0 = bodyH + (if (bodyH > 0f) 14f else 0f)
                                val top = ty0 + (1f - inside) * titleH
                                Geo(Rect(Offset(px0, top), Size(photoW, photoH)), tx, ty0, 0f, 0f, w, top + photoH + 14f)
                            }
                            Layout.Beside -> {
                                val top = 10f
                                val ty = top + photoH - inside * titleH
                                val bx = if (bleedRight) 0f else photoW - bleed + 14f
                                Geo(Rect(Offset(px0, top), Size(photoW, photoH)), tx, ty, bx, top, bodyW0, max(ty + titleH, top + bodyH) + 4f)
                            }
                        }
                    }

                    // Choose the title spot where the picture is most extreme (very dark or very light).
                    var best = geo(0.5f, false)
                    var bestScore = -2f
                    if (tone != null) {
                        for (right in listOf(false, true)) for (inside in listOf(0.4f, 0.6f)) {
                            val g = geo(inside, right)
                            val l = (g.titleX - g.photo.left) / g.photo.width
                            val t = (g.titleY - g.photo.top) / g.photo.height
                            val r = (g.titleX + titleW - g.photo.left) / g.photo.width
                            val b = (g.titleY + titleH - g.photo.top) / g.photo.height
                            val sc = tone.score(l.coerceIn(0f, 1f), t.coerceIn(0f, 1f), r.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
                            if (sc > bestScore) { bestScore = sc; best = g }
                        }
                    } else best = geo(0.5f, false)

                    val accent = tone?.light ?: c.accent
                    val shape = CutCornerShape(topEnd = if (bleedRight) 0.dp else 26.dp, topStart = if (bleedRight) 26.dp else 0.dp)
                    Box(Modifier.fillMaxWidth().height(best.areaH.dp)) {
                        // Hatched block behind the photo, offset away from the bleed side and partly covered.
                        Canvas(Modifier.fillMaxSize()) {
                            val dx = if (bleedRight) -dp(14f) else dp(14f)
                            val r = Rect(
                                Offset(best.photo.left * density + dx, best.photo.top * density + dp(14f)),
                                Size(best.photo.width * density, best.photo.height * density)
                            )
                            hatchIn(rectPath(r), r, accent.copy(alpha = 0.55f), dp(5.5f), dp(1f), true)
                            drawRect(accent.copy(alpha = 0.85f), r.topLeft, r.size, style = Stroke(dp(1f)))
                        }
                        // Photo and title share one offscreen layer, so Difference sees the photo and the card behind it.
                        Box(
                            Modifier.fillMaxSize()
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    drawText(titleLayout, color = Color.White, topLeft = Offset(best.titleX * density, best.titleY * density), blendMode = BlendMode.Difference)
                                }
                        ) {
                            Box(Modifier.offset(best.photo.left.dp, best.photo.top.dp).size(best.photo.width.dp, best.photo.height.dp).clip(shape).background(Color(0xFF15171B))) {
                                if (photo != null) {
                                    Image(
                                        photo.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                                        colorFilter = tone?.filter(), modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Canvas(Modifier.fillMaxSize()) {
                                        val whole = Rect(Offset.Zero, size)
                                        hatchIn(rectPath(whole), whole, Paper.Ink.copy(alpha = 0.1f), dp(8f), dp(1f), false)
                                    }
                                }
                            }
                        }
                        // Tap the title to copy it.
                        Box(Modifier.offset(best.titleX.dp, best.titleY.dp).size(titleW.dp, titleH.dp).plainClick { c.onCopy(p.title) })
                        if (body.isNotBlank()) {
                            Text(
                                body,
                                style = sans(13f, FontWeight.Light, TagColors.White.copy(alpha = 0.86f)).copy(lineHeight = 19.sp),
                                maxLines = bodyLines, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.offset(best.bodyX.dp, best.bodyY.dp).width(best.bodyW.dp).plainClick { c.onCopy(body) }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Caption("SRC EN.WIKIPEDIA.ORG", color = Paper.Mute, size = 9f)
        }
    }
}
