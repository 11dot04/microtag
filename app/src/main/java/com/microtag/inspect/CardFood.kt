package com.microtag.inspect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.ui.TagColors

// ==========================================================
// FOOD. Energy as the hero, and where it comes from.
//   header   label, pin, share arrow
//   line     what was asked for and its weight ("2 × roti · 80 g")
//   bar      one bar split by where the energy comes from: protein, carbs, fat (4, 4 and 9 kcal per gram)
//   legend   the three macros in grams, in the bar's colours; tap one to copy it
//   rows     fibre, per 100 g, sugars, salt, per serving...
//   hero     the kcal, large; the caption says it is approximate
// ==========================================================

private val Protein = Color(0xFFD2A76C)
private val Carbs = Color(0xFF8CAA8F)
private val Fat = Color(0xFFC98B77)

@Composable
internal fun FoodCard(c: CardCtx, modifier: Modifier) {
    val p = c.p
    val protein = p.nums.getOrNull(1) ?: 0.0
    val carbs = p.nums.getOrNull(2) ?: 0.0
    val fat = p.nums.getOrNull(3) ?: 0.0
    val energy = floatArrayOf((protein * 4).toFloat(), (carbs * 4).toFloat(), (fat * 9).toFloat())
    val total = energy.sum()
    val macroRows = mapOf("Protein" to Protein, "Carbs" to Carbs, "Fat" to Fat)
    val macros = p.rows.filter { it.first in macroRows }
    val others = p.rows.filter { it.first !in macroRows && it.second.isNotBlank() }
    val heroColour = if (c.flash == p.bigText) c.accent else TagColors.White

    CardFrame(p, modifier, c.drag, Surface.Black, 400.dp) {
        CardHeader(c, arrowHere = true)
        Spacer(Modifier.height(12.dp))
        Text(
            p.subText.ifBlank { p.title },
            style = sans(17f, FontWeight.Medium, TagColors.White.copy(alpha = 0.78f)).copy(lineHeight = 24.sp),
            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.plainClick { c.onCopy(p.title) }
        )
        Spacer(Modifier.height(14.dp))

        if (total > 0f) {
            Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                val gap = 3.dp.toPx()
                val usable = size.width - gap * 2
                var x = 0f
                val colours = listOf(Protein, Carbs, Fat)
                for (i in 0 until 3) {
                    val w = usable * energy[i] / total
                    if (w > 0.5f) {
                        drawRoundRect(colours[i], Offset(x, 0f), Size(w, size.height), CornerRadius(3.dp.toPx()))
                        x += w + gap
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            macros.forEach { (k, v) ->
                val tone = macroRows.getValue(k)
                Column(Modifier.plainClick { c.onCopy("$v ${k.lowercase()}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(tone))
                        Spacer(Modifier.width(6.dp))
                        Text(k.uppercase(), style = mono(9f, Paper.Mute, 0.9f, FontWeight.Bold), maxLines = 1)
                    }
                    Text(v, style = sans(17f, FontWeight.Medium, TagColors.White), maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        others.take(6).forEachIndexed { i, (k, v) ->
            DetailRow(k, v, c.flash == v, c.accent, separator = i < others.take(6).lastIndex, onCopy = c.onCopy)
        }
        Spacer(Modifier.height(10.dp))
        HeroValue(
            p.bigText, TextStyle(fontFamily = DisplayFont, fontSize = 84.sp, color = heroColour),
            minSize = 24f, splitSuffix = true, onClick = { c.onCopy(p.copyValue.ifBlank { p.bigText }) }
        )
        if (p.caption.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Caption(p.caption)
        }
    }
}
