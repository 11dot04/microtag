package com.microtag.inspect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.R
import com.microtag.ui.TagColors
import com.microtag.ui.Tick
import com.microtag.ui.tick
import kotlin.math.abs

/**
 * Drop Display.ttf into res/font as display.ttf and this resolves. Swap the file to swap the typeface.
 */
val DisplayFont = FontFamily(Font(R.font.display))

internal fun labelFor(p: InspectPayload): String {
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
        "PINNED" -> "PINNED"
        "FOOD" -> "FOOD"
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

internal fun openLabel(p: InspectPayload) = if (p.url.startsWith("upi://")) "PAY" else "OPEN"

/** Everything on the card as plain text, for the share arrow. */
fun InspectPayload.shareText(): String {
    val sb = StringBuilder()
    val hero = if (domain == "COLOUR") heroText else subText
    if (hero.isNotBlank() && hero != bigText) sb.append(hero).append('\n')
    sb.append(bigText).append('\n')
    parts.takeIf { domain == "CALC" }?.forEach { sb.append(it).append('\n') }
    if (fact.isNotBlank()) sb.append(fact).append('\n')
    val seen = HashSet<String>()
    for ((k, v) in rows) {
        if (v.isBlank() || !seen.add("$k$v")) continue
        sb.append(if (k.isBlank()) "• " else if (k == "Example") "  " else "$k: ").append(v).append('\n')
    }
    if (fullContent.isNotBlank()) sb.append('\n').append(fullContent).append('\n')
    if (url.isNotBlank() && !url.startsWith("upi://")) sb.append('\n').append(url)
    return sb.toString().trim()
}

/** Rows that repeat something already shown elsewhere on the card. */
private fun hiddenLabels(domain: String): Set<String> = when (domain) {
    "COLOUR" -> setOf("HEX", "Name", "Complement", "Readable with", "RGB", "HSL")
    "CALC" -> setOf("Expression", "Result")
    "TIME" -> setOf("From", "Local", "Day")
    "DATE" -> setOf("Date", "Relative", "Breakdown", "Week", "Day of year")
    else -> emptySet()
}

private enum class ArrowSlot { Header, Hero, Actions }

/** Everything a card body needs, so the bodies stay small. */
internal class CardCtx(
    val p: InspectPayload,
    val accent: Color,
    val busy: String,
    val flash: String,
    val onCopy: (String) -> Unit,
    val onShare: () -> Unit,
    val onOpen: () -> Unit,
    val onCheck: () -> Unit,
    val onResolve: () -> Unit,
    val onEvent: () -> Unit,
    val drag: Modifier,
    /** "pin", "unpin" or "extend". Null when the host can't pin (the demo cards): the pin glyph and the pin buttons then don't show. */
    val onPin: ((String) -> Unit)? = null
) {
    /** The same card context with another accent (the Wiki poster tints its OPEN pill to match its photo). */
    fun withAccent(a: Color) = CardCtx(p, a, busy, flash, onCopy, onShare, onOpen, onCheck, onResolve, onEvent, drag, onPin)

    fun actions(): List<Pair<String, () -> Unit>> {
        val out = ArrayList<Pair<String, () -> Unit>>()
        if (p.resolveUrl.isNotBlank()) out.add((if (busy == "RESOLVE") "…" else "RESOLVE") to onResolve)
        if (p.checkHost.isNotBlank()) out.add((if (busy == "CHECK") "…" else "CHECK") to onCheck)
        if (p.eventId > 0L) out.add("EVENT" to onEvent)
        return out
    }
}

/** The header row: category pill, OPEN / PAY, and (when it lives here) the share arrow. */
@Composable
internal fun CardHeader(c: CardCtx, arrowHere: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LabelPill(labelFor(c.p), pillTint(c.p))
        PinGlyph(c)
        Spacer(Modifier.weight(1f))
        if (canOpen(c.p)) {
            OpenPill(openLabel(c.p), c.accent, c.onOpen)
            Spacer(Modifier.size(14.dp))
        }
        if (arrowHere) ShareArrow(c.onShare)
    }
}

/**
 * The card's body: surface, outline, shape and the dashed tear line.
 * [content] lays itself out inside; [padded] adds the usual card padding.
 */
@Composable
internal fun CardFrame(
    p: InspectPayload,
    modifier: Modifier,
    dragModifier: Modifier,
    surface: Color,
    maxWidth: Dp,
    padded: Boolean = true,
    swallowDrag: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = remember(p.domain) { CardShapes.shapeFor(p.domain) }
    val tear = CardShapes.hasTear(p.domain)
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.86f).dp
    val outline = if (surface.luminance() > 0.5f) Color.Black.copy(alpha = 0.4f) else TagColors.Outline
    Column(
        modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .heightIn(max = maxH)
            .clip(shape)
            .background(surface)
            .drawBehind {
                if (tear) {
                    // The perforation between the two side semicircles.
                    val y = size.height - CardShapes.TEAR_FROM_BOTTOM.dp.toPx()
                    drawLine(
                        Color.White.copy(alpha = 0.26f), Offset(26.dp.toPx(), y), Offset(size.width - 26.dp.toPx(), y),
                        strokeWidth = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))
                    )
                }
            }
            .then(if (CARD_OUTLINE_DP > 0f) Modifier.border(CARD_OUTLINE_DP.dp, outline, shape) else Modifier)
            // Taps on the card are the card's own; they must not fall through to a dismiss layer behind it.
            .pointerInput(Unit) { detectTapGestures { } }
            .then(if (swallowDrag) dragModifier else Modifier)
            .then(if (padded) Modifier.padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 28.dp) else Modifier),
        content = content
    )
}

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
    dragModifier: Modifier = Modifier,
    onPin: ((String) -> Unit)? = null
) {
    val c = CardCtx(p, cardAccent(p), busy, flash, onCopy, onShare, onOpen, onCheck, onResolve, onEvent, dragModifier, onPin)
    when (p.domain) {
        "PINNED" -> PinnedCard(c, modifier)
        "FOOD" -> FoodCard(c, modifier)
        "COLOUR" -> ColourCard(c, modifier)
        "PIN" -> PostCard(c, modifier)
        "WIKI" -> WikiCard(c, modifier)
        "TIME" -> TimeCard(c, modifier)
        "DATE" -> DateCard(c, modifier)
        "DEFINE" -> DefineCard(c, modifier)
        else -> StandardCard(c, modifier)
    }
}

// ---------------------------------------------------------------- the standard card

/** Hero type per card. URL and UPI are set in the sans face; the rest use the display font. */
private fun heroStyle(p: InspectPayload, color: Color) = when (p.domain) {
    "URL" -> sans(52f, FontWeight.Bold, color).copy(letterSpacing = (-1.2).sp)
    "UPI" -> sans(46f, FontWeight.Bold, color).copy(letterSpacing = (-1.0).sp)
    "IFSC" -> androidx.compose.ui.text.TextStyle(fontFamily = DisplayFont, fontSize = 84.sp, letterSpacing = 1.2.sp, color = color)
    else -> androidx.compose.ui.text.TextStyle(fontFamily = DisplayFont, fontSize = 84.sp, color = color)
}

@Composable
private fun StandardCard(c: CardCtx, modifier: Modifier) {
    val p = c.p
    val view = LocalView.current
    val tear = CardShapes.hasTear(p.domain)
    val big = p.bigText
    val copyValue = p.copyValue.ifBlank { big }
    val bigDisplay = if (p.domain == "CALC") "= $big" else big
    val isReverseDims = p.domain == "DIMS" && p.rows.firstOrNull()?.first == "Standard"
    val hero = when {
        isReverseDims -> "${p.title} · ${p.subText}"
        else -> p.subText
    }
    val showHero = hero.isNotBlank() && hero != big
    val actions = c.actions()

    val slots = ArrayList<ArrowSlot>().apply {
        add(ArrowSlot.Header)
        if (showHero) add(ArrowSlot.Hero)
        if (actions.isNotEmpty()) add(ArrowSlot.Actions)
    }
    val slot = slots[abs((p.domain + p.copyValue).hashCode()) % slots.size]

    CardFrame(p, modifier, c.drag, Surface.Black, if (p.domain == "UPI") 400.dp else 520.dp) {
        CardHeader(c, slot == ArrowSlot.Header)
        Spacer(Modifier.height(12.dp))

        if (showHero) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    hero,
                    style = sans(if (p.domain == "CALC") 20f else 17f, FontWeight.Medium, TagColors.White.copy(alpha = 0.78f)).copy(lineHeight = 24.sp),
                    maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                if (slot == ArrowSlot.Hero) ShareArrow(c.onShare)
            }
            Spacer(Modifier.height(10.dp))
        }

        if (hasArt(p.domain)) {
            CardArt(p, c.accent, c.onCopy)
            Spacer(Modifier.height(10.dp))
        }

        val hidden = hiddenLabels(p.domain)
        val cap = if (p.domain == "CONV") 5 else 7
        val rows = p.rows
            .filter { it.first !in hidden }
            .filter { !it.second.equals(hero, true) && !it.second.equals(big, true) }
            .take(cap)
        rows.forEachIndexed { i, (k, v) ->
            // On tear cards the dashed line takes the place of the last separator.
            DetailRow(k, v, c.flash == v, c.accent, separator = !(tear && i == rows.lastIndex), onCopy = c.onCopy)
        }

        if (p.fullContent.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                p.fullContent.take(420),
                style = sans(13f, FontWeight.Light, TagColors.White.copy(alpha = 0.82f)).copy(lineHeight = 19.sp),
                maxLines = 7, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.plainClick { c.onCopy(p.fullContent) }
            )
        }

        if (p.domain == "CALC" && p.parts.isNotEmpty()) {
            // The working, like a tape: each step on its own line above the result.
            Spacer(Modifier.height(12.dp))
            p.parts.forEach { line ->
                Text(line, style = sans(15f, FontWeight.Medium, Paper.Ink.copy(alpha = 0.85f)).copy(lineHeight = 22.sp), maxLines = 1)
            }
        }

        if (actions.isNotEmpty() || slot == ArrowSlot.Actions) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.forEachIndexed { i, a ->
                    ActionPill(a.first, c.accent, filled = i == 0) { view.tick(Tick.Press); a.second() }
                }
                Spacer(Modifier.weight(1f))
                if (slot == ArrowSlot.Actions) ShareArrow(c.onShare)
            }
        }

        val heroColour = if (c.flash == copyValue) c.accent else TagColors.White
        val heroText: @Composable () -> Unit = {
            HeroValue(
                bigDisplay, heroStyle(p, heroColour),
                minSize = 24f, splitSuffix = p.domain != "CALC" && p.domain != "URL" && p.domain != "UPI",
                onClick = { c.onCopy(copyValue) }
            )
        }

        if (tear) {
            // A fixed-height slot under the tear line, so the line is always the same distance above the value.
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height((CardShapes.TEAR_FROM_BOTTOM - 28f).dp)) {
                Column(Modifier.fillMaxWidth().align(Alignment.BottomStart)) {
                    if (p.caption.isNotBlank()) {
                        Caption(p.caption, Modifier.padding(top = 10.dp, bottom = 4.dp))
                        Spacer(Modifier.weight(1f, fill = false))
                    }
                    heroText()
                }
            }
        } else {
            Spacer(Modifier.height(10.dp))
            heroText()
            if (p.fact.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Floating(p.fact, c.accent, onClick = { c.onCopy(p.fact) })
            }
            if (p.caption.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Caption(p.caption)
            }
        }
    }
}
