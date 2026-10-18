package com.microtag.inspect

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.microtag.ui.TagColors

// ==========================================================
// PALETTE. Muted and tonal, grouped by family:
//   numbers  steel blues     (CALC, CONV, DIMS, NUM)
//   money    sage greens     (MONEY, IFSC, UPI)
//   time     warm ambers     (TIME, DATE)
//   words    dusty tones     (URL, DEFINE, WIKI, TEXT)
//   post     clay            (PIN)
// Every label pill is the same warm paper colour; the accent shows up in the OPEN pill, the art and the flash.
// ==========================================================

/**
 * Neutral pills and quiet text. These are read when a card is drawn, never cached, so they follow the
 * palette: on Lime they are the original warm off-whites, on any other palette they are tints of that
 * palette's ink on its base.
 */
object Paper {
    private val lime get() = TagColors.Id == "lime"
    val Ink: Color get() = if (lime) Color(0xFFE6E1D6) else lerp(TagColors.White, TagColors.Black, 0.08f)
    val Line: Color get() = if (lime) Color(0xFFCFCABF) else lerp(TagColors.White, TagColors.Black, 0.20f)
    val Mute: Color get() = if (lime) Color(0xFF8E8A82) else lerp(TagColors.White, TagColors.Black, 0.52f)
    val Hair: Color get() = if (lime) Color(0x16FFFFFF) else TagColors.White.copy(alpha = 0.09f)
}

val WarnCoral = Color(0xFFE2865F)
val ErrorRed = Color(0xFFD9625C)

/** Outline drawn around every card, like the TAG sheets. Set to 0 to turn it off. */
const val CARD_OUTLINE_DP = 1.5f

/** Accent colour for a card. */
fun cardAccent(p: InspectPayload): Color {
    if (p.status == "ERROR") return ErrorRed
    if (p.status == "WARN") return WarnCoral
    if (p.accent != 0) return Color(p.accent)
    return when (p.domain) {
        "CALC" -> Color(0xFF7F9FB5)
        "CONV" -> Color(0xFF78A5AE)
        "DIMS" -> Color(0xFF8AA4C8)
        "NUM" -> Color(0xFF93A9BC)
        "MONEY" -> Color(0xFF8CAA8F)
        "IFSC" -> Color(0xFF9DB38A)
        "UPI" -> Color(0xFF7FB0A5)
        "TIME" -> Color(0xFFD2A76C)
        "DATE" -> Color(0xFFCC8F63)
        "URL" -> Color(0xFF9A9BC8)
        "DEFINE" -> Color(0xFFC79AA3)
        "WIKI" -> Color(0xFFD9D3C6)
        "PIN" -> Color(0xFFC98B77)
        "TEXT" -> Color(0xFFB8B2A6)
        "COLOUR" -> Color(0xFFE3DED4)
        else -> Color(0xFFE3DED4)
    }
}

/** The colour of the label pill: neutral paper, or the status tint on warnings and errors. */
fun pillTint(p: InspectPayload): Color = when (p.status) {
    "ERROR" -> ErrorRed
    "WARN" -> WarnCoral
    else -> Paper.Line
}

/**
 * The card's surface: the palette's base colour. Except for the colour card on a colour that would sink
 * into it (under about 3:1): that card is filled with the colour itself instead.
 *
 * [Black] is a getter on purpose. It used to be a val, which captured whatever palette was active the first
 * time a card was drawn and kept it for the life of the process, so changing the theme only recoloured the text.
 */
internal object Surface {
    val Black: Color get() = TagColors.Black.copy(alpha = 0.97f)

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    /** True when [c] has under about 3:1 against the card surface. On black that is the old "dark colour" test. */
    fun isFilled(c: Color): Boolean = contrast(c, TagColors.Black) < 3.2f

    fun forColour(c: Color): Color = if (isFilled(c)) c.copy(alpha = 1f) else Black

    /** The ink that reads on a filled colour card: the palette's light or dark end, whichever contrasts more. */
    fun inkOn(fill: Color): Color {
        val a = TagColors.White
        val b = TagColors.Black
        return if (contrast(fill, a) >= contrast(fill, b)) a else b
    }
}
