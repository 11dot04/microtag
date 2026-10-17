package com.microtag.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

// Everything comes from the active palette, so the poster follows the theme.
private val Poster: Color get() = TagColors.PausedPoster
private val Dim: Color get() = lerp(TagColors.PausedPoster, TagColors.White, 0.09f)
private val Faint: Color get() = TagColors.Dim
private val Mono: FontFamily get() = TagFonts.data

private fun mono(size: Int, color: Color, weight: FontWeight = FontWeight.Medium) =
    TextStyle(fontFamily = Mono, fontWeight = weight, fontSize = size.sp, lineHeight = (size + 3).sp, color = color)

private fun Color.hexParts(): Pair<String, String> {
    val c = toArgb()
    val r = (c shr 16) and 0xFF
    val g = (c shr 8) and 0xFF
    val b = c and 0xFF
    return "%02X %02X %02X".format(r, g, b) to "$r $g $b"
}

/**
 * The last thing at the bottom of the dashboard: a poster of metadata, registration marks and the
 * wordmark. Purely decorative, apart from the GitHub handle.
 */
@Composable
fun EasterEgg() {
    val context = LocalContext.current
    val view = LocalView.current
    val info = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull() }
    val versionName = info?.versionName ?: "dev"
    val build = info?.longVersionCode?.toString()?.padStart(6, '0') ?: "000000"
    val updated = info?.lastUpdateTime?.let { SimpleDateFormat("yy.MM.dd", Locale.US).format(Date(it)) } ?: "--.--.--"

    val blink by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 1f,
        targetValue = if (TagMotion.enabled) 0.25f else 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "blink"
    )

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth
        val h = w * 1.3f

        Box(
            Modifier
                .fillMaxWidth()
                .height(h)
                .background(Poster)
        ) {
            // Ease out of the dashboard's pure black.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(Brush.verticalGradient(listOf(TagColors.Black, Poster)))
            )

            // Registration marks, circles and checker strips.
            Canvas(Modifier.fillMaxSize()) {
                val mark = 3.dp.toPx()
                val markColor = lerp(Poster, TagColors.White, 0.2f)
                for (cx in listOf(0.33f, 0.74f)) for (cy in listOf(0.26f, 0.52f, 0.76f, 0.95f)) {
                    val x = size.width * cx
                    val y = size.height * cy
                    drawLine(markColor, Offset(x - mark, y - mark), Offset(x + mark, y + mark), 1.dp.toPx())
                    drawLine(markColor, Offset(x - mark, y + mark), Offset(x + mark, y - mark), 1.dp.toPx())
                }
                val ring = Stroke(1.dp.toPx())
                for ((fx, fy) in listOf(0.88f to 0.20f, 0.74f to 0.58f, 0.02f to 0.66f, 0.49f to 0.66f, 0.74f to 0.66f)) {
                    drawCircle(TagColors.White, 5.dp.toPx(), Offset(size.width * fx, size.height * fy), style = ring)
                }
                // dim checker strips down the left edge
                val cell = 4.dp.toPx()
                fun strip(top: Float, height: Float) {
                    var yy = top
                    var row = 0
                    while (yy < top + height) {
                        var xx = 20.dp.toPx()
                        var col = 0
                        while (xx < 20.dp.toPx() + 28.dp.toPx()) {
                            if ((row + col) % 2 == 0) drawRect(Dim, Offset(xx, yy), Size(cell, cell))
                            xx += cell; col++
                        }
                        yy += cell; row++
                    }
                }
                strip(size.height * 0.06f, 12.dp.toPx())
                strip(size.height * 0.16f, 12.dp.toPx())
                strip(size.height * 0.68f, size.height * 0.16f)
                strip(size.height * 0.86f, size.height * 0.07f)
            }

            // Accent checker swatch and the small wordmark.
            Canvas(Modifier.padding(start = 20.dp, top = h * 0.185f).size(28.dp, 24.dp)) {
                val cell = 4.dp.toPx()
                var row = 0
                var yy = 0f
                while (yy < size.height) {
                    var col = 0
                    var xx = 0f
                    while (xx < size.width) {
                        if ((row + col) % 2 == 0) drawRect(TagColors.Lime, Offset(xx, yy), Size(cell, cell))
                        xx += cell; col++
                    }
                    yy += cell; row++
                }
            }
            Image(
                painter = painterResource(R.drawable.ic_tag_wordmark),
                contentDescription = null,
                colorFilter = ColorFilter.tint(TagColors.White),
                modifier = Modifier.padding(start = 20.dp, top = h * 0.185f + 36.dp).width(72.dp)
            )

            // The accent bar: build number, namespace, status.
            Row(
                Modifier
                    .padding(top = h * 0.33f)
                    .fillMaxWidth(0.78f)
                    .height(28.dp)
                    .drawBehind {
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(
                                    TagColors.Lime,
                                    lerp(TagColors.Lime, Color.White, 0.28f),
                                    TagColors.Lime,
                                    lerp(TagColors.Lime, Color.Black, 0.22f),
                                    TagColors.Lime
                                )
                            )
                        )
                        // grain
                        val rnd = Random(11)
                        repeat(420) {
                            drawRect(
                                Color.Black.copy(alpha = 0.07f),
                                Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height),
                                Size(1.2.dp.toPx(), 1.2.dp.toPx())
                            )
                        }
                    }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(build, style = mono(10, TagColors.OnLime))
                Box(Modifier.weight(1f).padding(horizontal = 8.dp).height(1.dp).background(TagColors.OnLime.copy(alpha = 0.7f)))
                Text("COM.MICROTAG", style = mono(10, TagColors.OnLime))
                Box(Modifier.weight(1f).padding(horizontal = 8.dp).height(1.dp).background(TagColors.OnLime.copy(alpha = 0.7f)))
                Text("V$versionName", style = mono(10, TagColors.OnLime))
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(10.dp)
                        .graphicsLayer { alpha = blink }
                        .border(1.dp, TagColors.OnLime, CircleShape)
                )
            }

            // Palette readout, three columns like a spec sheet.
            Row(
                Modifier.padding(start = 20.dp, top = h * 0.5f),
                horizontalArrangement = Arrangement.spacedBy(w * 0.12f)
            ) {
                // The dark and light ends of the palette, whichever way round this palette is.
                val dark = if (TagColors.IsLight) TagColors.White else TagColors.Black
                val light = if (TagColors.IsLight) TagColors.Black else TagColors.White
                listOf("ACCENT" to TagColors.Lime, "INK" to dark, "PAPER" to light).forEach { (name, color) ->
                    val (hex, rgb) = color.hexParts()
                    Column {
                        Box(Modifier.size(10.dp, 2.dp).background(TagColors.White))
                        Spacer(Modifier.height(5.dp))
                        Text(name, style = mono(8, Faint))
                        Text(hex, style = mono(8, Faint))
                        Text(rgb, style = mono(8, Faint))
                    }
                }
            }

            // Supporting colours of the palette, if it has any.
            if (TagColors.Extras.isNotEmpty()) {
                Row(
                    Modifier.padding(start = 20.dp, top = h * 0.5f + 62.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TagColors.Extras.take(5).forEach { Box(Modifier.size(14.dp).background(it)) }
                }
            }

            // The big, dim wordmark, bleeding off the right edge.
            Image(
                painter = painterResource(R.drawable.ic_tag_wordmark),
                contentDescription = null,
                colorFilter = ColorFilter.tint(lerp(Poster, TagColors.White, 0.08f)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 96.dp)
                    .offset(x = w * 0.16f)
                    .width(w * 0.95f)
            )

            // Metadata and the handle.
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 28.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text("VERSION  $versionName", style = mono(10, Faint))
                Text("BUILD    $build", style = mono(10, Faint))
                Text("UPDATED  $updated", style = mono(10, Faint))
                Text("TARGET   API ${android.os.Build.VERSION.SDK_INT}", style = mono(10, Faint))
                Text("PALETTE  ${TagColors.Name.uppercase()}", style = mono(10, Faint))
                Spacer(Modifier.height(10.dp))
                Text("GITHUB", style = mono(8, Faint))
                Text(
                    "11DOT04",
                    style = mono(15, TagColors.White, FontWeight.Bold).copy(textDecoration = TextDecoration.Underline),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            view.tick(Tick.Press)
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/11DOT04"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                )
            }
        }
    }
}
