package com.microtag.inspect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.microtag.R
import com.microtag.ui.FitText
import com.microtag.ui.Montserrat
import com.microtag.ui.TagColors
import com.microtag.ui.TagIcon
import com.microtag.ui.Tick
import com.microtag.ui.tick

// ==========================================================
// SHARED PIECES for every card.
// ==========================================================

internal val Mono = FontFamily.Monospace

internal fun sans(size: Float, weight: FontWeight, color: Color, spacing: Float = 0f) =
    TextStyle(fontFamily = Montserrat, fontWeight = weight, fontSize = size.sp, letterSpacing = spacing.sp, color = color)

internal fun mono(size: Float, color: Color, spacing: Float = 0.8f, weight: FontWeight = FontWeight.Medium) =
    TextStyle(fontFamily = Mono, fontWeight = weight, fontSize = size.sp, letterSpacing = spacing.sp, color = color)

/**
 * Tight leading for big or wrapping type. The fonts carry a lot of built-in line height; this trims it to
 * [factor] times the font size and centres the glyphs in each line.
 */
@Suppress("DEPRECATION")
internal fun TextStyle.tight(factor: Float = 0.9f): TextStyle = copy(
    lineHeight = (fontSize.value * factor).sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

@Composable
internal fun Modifier.plainClick(onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)

// ---------------------------------------------------------------- drawing helpers

internal fun DrawScope.dp(v: Float) = v * density

internal fun DrawScope.hatchIn(clip: Path, bounds: Rect, color: Color, gap: Float, width: Float, rising: Boolean, phase: Float = 0f) {
    clipPath(clip) {
        val h = bounds.height
        var x = bounds.left - h + phase
        while (x < bounds.right + h) {
            if (rising) drawLine(color, Offset(x, bounds.bottom), Offset(x + h, bounds.top), strokeWidth = width)
            else drawLine(color, Offset(x, bounds.top), Offset(x + h, bounds.bottom), strokeWidth = width)
            x += gap
        }
    }
}

internal fun ovalPath(cx: Float, cy: Float, rx: Float, ry: Float): Path =
    Path().apply { addOval(Rect(Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))) }

internal fun rectPath(r: Rect): Path = Path().apply { addRect(r) }

/** One line of text on a canvas that never wraps. */
internal fun DrawScope.label(tm: TextMeasurer, text: String, at: Offset, style: TextStyle) {
    drawText(tm, text, topLeft = at, style = style, softWrap = false, maxLines = 1)
}

// ---------------------------------------------------------------- pills

/** The category pill. Always the same warm paper colour, so the accent is saved for what is interactive. */
@Composable
internal fun LabelPill(text: String, tint: Color) {
    Box(Modifier.border(1.dp, tint, RoundedCornerShape(50)).padding(horizontal = 13.dp, vertical = 5.dp)) {
        Text(text, style = sans(11f, FontWeight.Bold, if (tint == Paper.Line) Paper.Ink else tint, 1.8f), maxLines = 1)
    }
}

/** The OPEN / PAY pill. */
@Composable
internal fun OpenPill(text: String, accent: Color, onClick: () -> Unit) {
    val view = LocalView.current
    val on = if (accent.luminance() > 0.4f) Color.Black else Color.White
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(accent)
            .plainClick { view.tick(Tick.Press); onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(text, style = sans(11f, FontWeight.Bold, on, 1.6f), maxLines = 1)
    }
}

@Composable
internal fun ActionPill(text: String, accent: Color, filled: Boolean, onClick: () -> Unit) {
    val on = if (accent.luminance() > 0.4f) Color.Black else Color.White
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .height(36.dp)
            .clip(shape)
            .then(if (filled) Modifier.background(accent) else Modifier.border(1.dp, accent.copy(alpha = 0.7f), shape))
            .plainClick(onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = sans(11f, FontWeight.Bold, if (filled) on else accent, 1.3f), maxLines = 1)
    }
}

/**
 * The share arrow. Your drawable (res/drawable/ic_inspect_arrow), at higher contrast and with a 48dp touch area.
 * It shares the whole card as text.
 */
@Composable
internal fun ShareArrow(onShare: () -> Unit, tint: Color = Paper.Ink, iconSize: Dp = 26.dp) {
    val view = LocalView.current
    Box(
        Modifier.size(maxOf(48.dp, iconSize + 14.dp)).plainClick { view.tick(Tick.Press); onShare() },
        contentAlignment = Alignment.Center
    ) {
        TagIcon(R.drawable.ic_inspect_arrow, tint.copy(alpha = 0.92f), Modifier, iconSize)
    }
}

// ---------------------------------------------------------------- rows and captions

private val hyphenated = mapOf("international" to "Inter-\nnational")

/** Long links break only after / ? & = so they read as a path, and hyphens never split a word. */
internal fun breakLink(s: String): String {
    if (!(s.startsWith("http://") || s.startsWith("https://"))) return s
    val sb = StringBuilder()
    for ((i, c) in s.withIndex()) {
        sb.append(c)
        when (c) {
            '/', '?', '&', '=' -> if (!(c == '/' && i + 1 < s.length && s[i + 1] == '/') && !(c == '/' && i > 0 && s[i - 1] == '/')) sb.append('​')
            '-' -> sb.append('⁠')
        }
    }
    return sb.toString()
}

@Composable
internal fun DetailRow(label: String, value: String, flashing: Boolean, accent: Color, separator: Boolean, onCopy: (String) -> Unit) {
    val shown = (hyphenated[label.lowercase()] ?: label).uppercase()
    Column(Modifier.fillMaxWidth().plainClick { onCopy(value) }) {
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.widthIn(min = 100.dp, max = 100.dp).padding(top = 3.dp)) {
                Text(shown, style = sans(10f, FontWeight.Bold, Paper.Mute, 1f), softWrap = false)
            }
            Text(
                breakLink(value),
                style = sans(14f, FontWeight.Medium, if (flashing) accent else TagColors.White).copy(lineHeight = 19.sp),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        if (separator) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
    }
}

/** A tiny mono caption. */
@Composable
internal fun Caption(text: String, modifier: Modifier = Modifier, color: Color = Paper.Mute, size: Float = 9.5f) {
    Text(text.uppercase(), modifier, style = mono(size, color, 1.1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** A floating line of text with a small diamond, for facts and notes. No box, no row. */
@Composable
internal fun Floating(text: String, accent: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier.then(if (onClick != null) Modifier.plainClick(onClick) else Modifier),
        verticalAlignment = Alignment.Top
    ) {
        Text("◆", style = sans(8f, FontWeight.Bold, accent), modifier = Modifier.padding(top = 5.dp, end = 8.dp))
        Text(text, style = sans(13f, FontWeight.Medium, TagColors.White.copy(alpha = 0.82f)).copy(lineHeight = 18.sp))
    }
}

// ---------------------------------------------------------------- hero values

/**
 * "1,234 g" becomes a big "1,234" and a small "g": a trailing unit (up to four characters, no digits) is set
 * at 42% of the value's size. Returns the text unchanged when there is no such suffix.
 */
internal fun heroWithSuffix(text: String, suffixAt: Float = 0.42f): AnnotatedString {
    val i = text.lastIndexOf(' ')
    val suffix = if (i > 0) text.substring(i + 1) else ""
    val unit = suffix.isNotEmpty() && suffix.length <= 4 && suffix.none { it.isDigit() } && suffix.any { it.isLetter() || it == '²' || it == '³' }
    return buildAnnotatedString {
        if (unit) {
            append(text.substring(0, i))
            withStyle(SpanStyle(fontSize = suffixAt.em)) { append(" $suffix") }
        } else append(text)
    }
}

/** Big tap-to-copy value in the card's display type. */
@Composable
internal fun HeroValue(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    minSize: Float = 24f,
    splitSuffix: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    FitText(
        if (splitSuffix) heroWithSuffix(text) else AnnotatedString(text),
        style = style.tight(0.94f),
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.plainClick(onClick) else Modifier),
        minSize = minSize.sp
    )
}

/**
 * Text that wraps to a few lines and shrinks only when it has to: the biggest size from [style] down to
 * [minSize] at which [text] fits in [maxLines].
 */
@Composable
internal fun AutoWrapText(
    text: String,
    style: TextStyle,
    maxLines: Int,
    minSize: Float,
    modifier: Modifier = Modifier,
    leading: Float = 0.94f,
    onClick: (() -> Unit)? = null
) {
    val tm = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val maxW = constraints.maxWidth
        val size = remember(text, maxW, style) {
            var s = style.fontSize.value
            while (s > minSize) {
                val r = tm.measure(
                    text, style.copy(fontSize = s.sp).tight(leading),
                    overflow = TextOverflow.Clip, maxLines = maxLines, constraints = Constraints(maxWidth = maxW)
                )
                if (!r.hasVisualOverflow) break
                s *= 0.93f
            }
            maxOf(s, minSize)
        }
        Text(
            text, style = style.copy(fontSize = size.sp).tight(leading), maxLines = maxLines, overflow = TextOverflow.Ellipsis,
            modifier = if (onClick != null) Modifier.plainClick(onClick) else Modifier
        )
    }
}

// ---------------------------------------------------------------- words that never break

/** Lines of words per block, all at one font size. */
internal class WordFit(val size: Float, val lines: List<List<String>>)

/**
 * Sets blocks of words at ONE size: the biggest from [maxSp] down to [minSp] at which the longest word fits the
 * width and the blocks wrap into at most [maxLines] lines. Words are never split; every block starts a new line.
 * Join words that must stay together with a no-break space.
 */
internal fun fitWordBlocks(
    tm: TextMeasurer, blocks: List<String>, style: TextStyle, maxWidthPx: Float,
    maxSp: Float, minSp: Float, maxLines: Int
): WordFit {
    val words = blocks.map { b -> b.split(' ').filter { it.isNotEmpty() } }
    fun w(t: String, s: Float): Float = tm.measure(t, style.copy(fontSize = s.sp), softWrap = false, maxLines = 1).size.width.toFloat()
    fun wrap(s: Float): List<List<String>> = words.map { ws ->
        val lines = ArrayList<String>()
        var cur = ""
        for (word in ws) {
            val tryLine = if (cur.isEmpty()) word else "$cur $word"
            if (cur.isNotEmpty() && w(tryLine, s) > maxWidthPx) { lines.add(cur); cur = word } else cur = tryLine
        }
        if (cur.isNotEmpty()) lines.add(cur)
        lines
    }
    var s = maxSp
    while (true) {
        val longest = words.flatten().maxOfOrNull { w(it, s) } ?: 0f
        if (longest <= maxWidthPx) {
            val l = wrap(s)
            if (l.sumOf { it.size } <= maxLines || s <= minSp) return WordFit(s, l)
        } else if (s <= minSp) return WordFit(s, wrap(s))
        s = maxOf(minSp, s * 0.96f)
    }
}
