package com.microtag.inspect

import androidx.compose.ui.graphics.Color
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

/** Warm off-white used for neutral pills and quiet text. */
object Paper {
    val Ink = Color(0xFFE6E1D6)
    val Line = Color(0xFFCFCABF)
    val Mute = Color(0xFF8E8A82)
    val Hair = Color(0x16FFFFFF)
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

/** The card's surface. Black, except for the colour card on a very dark colour, which goes mid-grey so it can be seen. */
internal object Surface {
    val Black = TagColors.Black.copy(alpha = 0.97f)
    val Grey = Color(0xFF8C8C8C)
    const val DARK_LUMINANCE = 0.075f

    fun forColour(c: Color): Color = if (c.luminance() < DARK_LUMINANCE) Grey else Black
    fun isGrey(c: Color): Boolean = c.luminance() < DARK_LUMINANCE
}
