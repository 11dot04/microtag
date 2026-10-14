package com.microtag.inspect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.R
import com.microtag.ui.FitText
import com.microtag.ui.Montserrat
import com.microtag.ui.TagColors
import com.microtag.ui.TagIcon
import com.microtag.ui.Tick
import com.microtag.ui.tick
import kotlin.math.abs

/**
 * Drop Display.ttf into res/font as display.ttf and this resolves. Swap the file to swap the typeface.
 */
val DisplayFont = FontFamily(Font(R.font.display))

/** Accent colour for a card. */
fun cardAccent(p: InspectPayload): Color {
    if (p.status == "ERROR") return Color(0xFFFF5A5A)
    if (p.status == "WARN") return Color(0xFFFFB74D)
    if (p.accent != 0) return Color(p.accent)
    return when (p.domain) {
        "CALC" -> Color(0xFFF9E800)
        "CONV" -> Color(0xFF4DD0E1)
        "MONEY" -> Color(0xFF69F0AE)
        "TIME" -> Color(0xFF8C9EFF)
        "DATE" -> Color(0xFFFFAB40)
        "URL" -> Color(0xFF6C97FF)
        "UPI" -> Color(0xFFB388FF)
        "IFSC" -> Color(0xFFFFD54F)
        "PIN" -> Color(0xFFFF8A65)
        "NUM" -> Color(0xFF80DEEA)
        "DEFINE" -> Color(0xFFF48FB1)
        "WIKI" -> Color(0xFFE8E8E8)
        "DIMS" -> Color(0xFF7AA2FF)
        else -> TagColors.Lime
    }
}

private fun labelFor(p: InspectPayload): String {
    val base = when (p.domain) {
        "COLOUR" -> "COLOUR"
        "CALC" -> "CALC"
        "CONV" -> "CONVERT"
        "MONEY" -> "MONEY"
        "TIME" -> "TIME"
        "DATE" -> "DATE"
        "URL" -> "LINK"
        "UPI" -> "UPI"
        "IFSC" -> "BANK"
        "PIN" -> "POST"
        "NUM" -> "NUMBER"
        "DEFINE" -> "DEFINE"
        "WIKI" -> "WIKI"
        "DIMS" -> "DIMENSIONS"
        else -> "TEXT"
    }
    return when (p.status) {
        "WARN" -> "$base · CAUTION"
        "ERROR" -> "$base · ERROR"
        else -> base
    }
}

/** Can the card's OPEN pill do something with this link? Web pages, and UPI payment links. */
fun canOpen(p: InspectPayload): Boolean =
    p.domain != "DIMS" && (p.url.startsWith("https://") || p.url.startsWith("http://") || p.url.startsWith("upi://"))

private fun openLabel(p: InspectPayload) = if (p.url.startsWith("upi://")) "PAY" else "OPEN"

/** Everything on the card as plain text, for the share arrow. */
fun InspectPayload.shareText(): String {
    val sb = StringBuilder()
    val hero = if (domain == "COLOUR") heroText else subText
    if (hero.isNotBlank() && hero != bigText) sb.append(hero).append('\n')
    sb.append(bigText).append('\n')
    val seen = HashSet<String>()
    for ((k, v) in rows) {
        if (v.isBlank() || !seen.add("$k$v")) continue
        sb.append(if (k.isBlank()) "• " else "$k: ").append(v).append('\n')
    }
    if (fullContent.isNotBlank()) sb.append('\n').append(fullContent).append('\n')
    if (url.isNotBlank() && !url.startsWith("upi://")) sb.append('\n').append(url)
    return sb.toString().trim()
}

/** Rows that repeat something already shown elsewhere on the card. */
private fun hiddenLabels(domain: String): Set<String> = when (domain) {
    "COLOUR" -> setOf("HEX", "Name", "Complement", "Readable with", "RGB", "HSL")
    "CALC" -> setOf("Expression", "Result")
    else -> emptySet()
}

private enum class ArrowSlot { Header, Hero, Actions }

/**
 * The expanded card. Shape and graphic depend on [InspectPayload.domain]. Everything on it that holds a value
 * copies it when tapped; the arrow shares the whole card as text.
 *
 * [dragModifier] is attached to the whole card, or to the header only for the scrolling Define card.
 */
@Composable
fun InspectCard(
    p: InspectPayload,
    modifier: Modifier = Modifier,
    busy: String = "",
    flash: String = "",
    onCopy: (String) -> Unit = {},
    onShare: () -> Unit = {},
    onOpen: () -> Unit = {},
    onCheck: () -> Unit = {},
    onResolve: () -> Unit = {},
    onEvent: () -> Unit = {},
    dragModifier: Modifier = Modifier
) {
    val accent = cardAccent(p)
    val view = LocalView.current
    val shape = remember(p.domain) { CardShapes.shapeFor(p.domain) }
    val isColour = p.domain == "COLOUR"
    val isDefine = p.domain == "DEFINE"
    val big = p.bigText
    val copyValue = p.copyValue.ifBlank { big }
    val bigDisplay = if (p.domain == "CALC") "= $big" else big
    val hero = if (isColour) p.heroText else p.subText
    val showHero = hero.isNotBlank() && hero != big

    val actions = ArrayList<Pair<String, () -> Unit>>()
    if (p.resolveUrl.isNotBlank()) actions.add((if (busy == "RESOLVE") "…" else "RESOLVE") to onResolve)
    if (p.checkHost.isNotBlank()) actions.add((if (busy == "CHECK") "…" else "CHECK") to onCheck)
    if (p.eventId > 0L) actions.add("EVENT" to onEvent)

    // The share arrow lives somewhere different on different cards.
    val slots = ArrayList<ArrowSlot>().apply {
        add(ArrowSlot.Header)
        if (showHero) add(ArrowSlot.Hero)
        if (actions.isNotEmpty()) add(ArrowSlot.Actions)
    }
    val slot = slots[abs((p.domain + p.copyValue).hashCode()) % slots.size]
    val arrow: @Composable () -> Unit = { ShareArrow(onShare) }

    val maxH = (LocalConfiguration.current.screenHeightDp * 0.86f).dp
    val tear = CardShapes.hasTear(p.domain)

    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LabelPill(labelFor(p), accent)
            Spacer(Modifier.weight(1f))
            if (canOpen(p)) {
                OpenPill(openLabel(p), accent, onOpen)
                Spacer(Modifier.size(6.dp))
            }
            if (slot == ArrowSlot.Header) arrow()
        }
    }

    Column(
        modifier
            .widthIn(max = if (isColour) 320.dp else 520.dp)
            .fillMaxWidth()
            .heightIn(max = maxH)
            .clip(shape)
            .background(TagColors.Black.copy(alpha = 0.97f))
            .drawBehind {
                if (tear) {
                    // The perforation between the two side notches.
                    val y = size.height - CardShapes.TEAR_FROM_BOTTOM.dp.toPx()
                    drawLine(
                        Color.White.copy(alpha = 0.22f), Offset(26.dp.toPx(), y), Offset(size.width - 26.dp.toPx(), y),
                        strokeWidth = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))
                    )
                }
            }
            .border(1.dp, accent.copy(alpha = 0.28f), shape)
            // Taps on the card are the card's own; they must not fall through to a dismiss layer behind it.
            .pointerInput(Unit) { detectTapGestures { } }
            .then(if (isDefine) Modifier else dragModifier)
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 28.dp)
    ) {
        // Header. On the Define card only this strip drags, so the entries below can scroll.
        Box(if (isDefine) dragModifier else Modifier) { header() }
        Spacer(Modifier.height(12.dp))

        val body: @Composable () -> Unit = {
            if (showHero) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FitText(
                        AnnotatedString(hero),
                        style = TextStyle(
                            fontFamily = Montserrat,
                            fontWeight = if (isColour) FontWeight.Black else FontWeight.Medium,
                            fontSize = if (isColour) 36.sp else 17.sp,
                            lineHeight = if (isColour) 40.sp else 22.sp,
                            color = if (isColour) TagColors.White else TagColors.White.copy(alpha = 0.72f)
                        ),
                        modifier = Modifier.weight(1f),
                        minSize = if (isColour) 20.sp else 12.sp
                    )
                    if (slot == ArrowSlot.Hero) arrow()
                }
                Spacer(Modifier.height(10.dp))
            }

            if (hasArt(p.domain)) {
                CardArt(p, accent, onCopy)
                Spacer(Modifier.height(10.dp))
            }

            if (isColour) ColourDetails(p, flash, accent, onCopy)

            val hidden = hiddenLabels(p.domain)
            val rows = p.rows
                .filter { it.first !in hidden }
                .filter { !it.second.equals(hero, true) && !it.second.equals(big, true) }
                .let { if (isDefine) it else it.take(7) }
            rows.forEach { (k, v) -> DetailRow(k, v, flash == v, accent, onCopy) }

            if (p.fullContent.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    p.fullContent.take(if (isDefine) 4000 else 420),
                    style = TextStyle(
                        fontFamily = Montserrat, fontWeight = FontWeight.Light, fontSize = 13.sp, lineHeight = 19.sp,
                        color = TagColors.White.copy(alpha = 0.82f)
                    ),
                    maxLines = if (isDefine) Int.MAX_VALUE else 7,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.plainClick { onCopy(p.fullContent) }
                )
            }
        }

        if (isDefine) {
            // Entries can be many and long; scroll them rather than cut them.
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { body() }
        } else {
            body()
        }

        Spacer(Modifier.height(12.dp))
        if (actions.isNotEmpty() || slot == ArrowSlot.Actions) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.forEachIndexed { i, a ->
                    ActionPill(a.first, accent, filled = i == 0) {
                        view.tick(Tick.Press)
                        a.second()
                    }
                }
                Spacer(Modifier.weight(1f))
                if (slot == ArrowSlot.Actions) arrow()
            }
            Spacer(Modifier.height(6.dp))
        }

        // The big value: tap to copy. WIKI shows its name over the photo instead.
        if (p.domain != "WIKI") {
            FitText(
                AnnotatedString(bigDisplay),
                style = TextStyle(
                    fontFamily = DisplayFont, fontSize = if (isColour) 70.sp else 84.sp, lineHeight = if (isColour) 74.sp else 88.sp,
                    color = if (flash == copyValue) accent else TagColors.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = CardShapes.bigEndInset(p.domain).dp)
                    .plainClick { onCopy(copyValue) },
                minSize = 24.sp
            )
        } else if (slot == ArrowSlot.Actions || actions.isEmpty()) {
            // Nothing below the photo's text; keep the card from ending abruptly.
            Spacer(Modifier.height(2.dp))
        }
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun Modifier.plainClick(onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)

@Composable
private fun LabelPill(text: String, accent: Color) {
    Box(Modifier.border(1.dp, accent, RoundedCornerShape(50)).padding(horizontal = 13.dp, vertical = 5.dp)) {
        Text(
            text,
            style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.8.sp, color = accent),
            maxLines = 1
        )
    }
}

/** The OPEN / PAY pill, at the far end of the header from the label. */
@Composable
private fun OpenPill(text: String, accent: Color, onClick: () -> Unit) {
    val view = LocalView.current
    val on = if (accent.luminance() > 0.4f) Color.Black else Color.White
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(accent)
            .plainClick { view.tick(Tick.Press); onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.6.sp, color = on),
            maxLines = 1
        )
    }
}

@Composable
private fun ActionPill(text: String, accent: Color, filled: Boolean, onClick: () -> Unit) {
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
        Text(
            text,
            style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.3.sp, color = if (filled) on else accent),
            maxLines = 1
        )
    }
}

/**
 * The share arrow: a quiet, low-contrast glyph that shares the whole card as text.
 * The drawable is yours: res/drawable/ic_inspect_arrow.xml (a north-west arrow; it is tinted here).
 */
@Composable
private fun ShareArrow(onShare: () -> Unit) {
    val view = LocalView.current
    Box(
        Modifier.size(44.dp).plainClick { view.tick(Tick.Press); onShare() },
        contentAlignment = Alignment.Center
    ) {
        TagIcon(R.drawable.ic_inspect_arrow, Color(0xFFC3C9D4).copy(alpha = 0.6f), Modifier, 26.dp)
    }
}

private val hyphenated = mapOf("international" to "Inter-\nnational")

@Composable
private fun DetailRow(label: String, value: String, flashing: Boolean, accent: Color, onCopy: (String) -> Unit) {
    val shown = (hyphenated[label.lowercase()] ?: label).uppercase()
    Column(Modifier.fillMaxWidth().plainClick { onCopy(value) }) {
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.widthIn(min = 100.dp, max = 100.dp).padding(top = 3.dp)) {
                Text(
                    shown,
                    style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, color = Color(0xFF6E737C)),
                    softWrap = false
                )
            }
            Text(
                value,
                style = TextStyle(
                    fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp,
                    color = if (flashing) accent else TagColors.White
                ),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
    }
}

/** The colour card's loose pieces: an outlined box holding rgb and hsl, and the contrast chip filled with the colour. */
@Composable
private fun ColourDetails(p: InspectPayload, flash: String, accent: Color, onCopy: (String) -> Unit) {
    val rgb = p.rows.firstOrNull { it.first == "RGB" }?.second.orEmpty()
    val hsl = p.rows.firstOrNull { it.first == "HSL" }?.second.orEmpty()
    val readable = p.rows.firstOrNull { it.first == "Readable with" }?.second.orEmpty()
    val fill = p.palette.firstOrNull()?.let { Color(it) } ?: accent
    // The chip says which text colour reads on it; use that colour, not a guess.
    val on = if (readable.startsWith("Black", true)) Color.Black else Color.White
    val lines = listOf(rgb, hsl).filter { it.isNotBlank() }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (lines.isNotEmpty()) {
            Column(
                Modifier
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                lines.forEach { line ->
                    Text(
                        line,
                        style = TextStyle(
                            fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 12.sp,
                            color = if (flash == line) accent else TagColors.White
                        ),
                        maxLines = 1, softWrap = false,
                        modifier = Modifier.plainClick { onCopy(line) }
                    )
                }
            }
        }
        if (readable.isNotBlank()) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(fill)
                    .plainClick { onCopy(readable) }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(
                    readable.replace(", ", "\n"),
                    style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, color = on)
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}
