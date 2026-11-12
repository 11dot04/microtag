package com.microtag.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.microtag.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * A poster of the current stats selection: the big number, the hourly curve, the headline facts.
 * Drawn straight onto a bitmap, saved to Pictures/TAG, then offered to the share sheet.
 */
object StatsCard {

    /** The poster looks. Index: ruled numbers, staggered. Giant: one huge numeral on paper. Signal: mono read-out and a dot matrix. */
    enum class Style(val label: String) { Index("Index"), Giant("Giant"), Signal("Signal") }

    suspend fun share(
        context: Context,
        days: String,
        total: Int,
        values: IntArray,
        peakHour: Int?,
        sources: List<Pair<String, Int>>,
        style: Style = Style.Index
    ) {
        val uri = withContext(Dispatchers.IO) {
            runCatching { save(context, render(context, days, total, values, peakHour, sources, style)) }.getOrNull()
        } ?: return
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share stats").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun save(context: Context, bitmap: Bitmap): Uri? {
        val resolver = context.contentResolver
        val details = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "TAG-stats-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TAG")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, details) ?: return null
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        details.clear()
        details.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, details, null, null)
        return uri
    }

    private const val W = 1080
    private const val H = 1350

    /** Fonts, colours and a paint factory shared by every poster. Everything comes from the active palette. */
    private class Kit(context: Context) {
        val accent = TagColors.Lime.toArgb()
        val base = TagColors.Black.toArgb()   // the palette's base (cream on a light palette)
        val ink = TagColors.White.toArgb()
        val grey = TagColors.Outline.toArgb()
        val faint = TagColors.White.copy(alpha = 0.22f).toArgb()
        val heavy: Typeface? = TagFonts.typeface(context, TagColors.DisplayBase) ?: ResourcesCompat.getFont(context, R.font.montserrat_black)
        val bold: Typeface? = ResourcesCompat.getFont(context, R.font.montserrat_bold)
        val medium: Typeface? = ResourcesCompat.getFont(context, R.font.montserrat_medium)
        val mono: Typeface = TagFonts.typeface(context, TagColors.MonoBase, listOf("medium", "regular", "")) ?: Typeface.MONOSPACE
        private val displayAxes = TagColors.Flex?.display

        fun paint(color: Int, size: Float, face: Typeface?, spacingEm: Float = 0f, align: Paint.Align = Paint.Align.LEFT) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; textSize = size; typeface = face; letterSpacing = spacingEm; textAlign = align
                if (face === heavy && displayAxes != null) setFontVariationSettings(TagFonts.variationString(900, displayAxes))
            }

        fun line(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; strokeWidth = width; style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT
        }

        fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    }

    /** The numbers the posters are made from. */
    private class Facts(
        val days: String, val total: Int, val values: IntArray, val peakHour: Int?, val sources: List<Pair<String, Int>>
    ) {
        val peakCount = peakHour?.let { values[it] } ?: 0
        val activeHours = values.count { it > 0 }
        val topName = sources.firstOrNull()?.first ?: "--"
        val topCount = sources.firstOrNull()?.second ?: 0
        val topShare = if (total > 0) (topCount * 100f / total).toInt() else 0
        val peakShare = if (total > 0) (peakCount * 100f / total).toInt() else 0
        val peakText = peakHour?.let { "%02d:00".format(it) } ?: "--"
        val date: String = SimpleDateFormat("dd.MM.yy", Locale.US).format(Date())
        val year: String = SimpleDateFormat("yyyy", Locale.US).format(Date())
    }

    private fun render(
        context: Context,
        days: String,
        total: Int,
        values: IntArray,
        peakHour: Int?,
        sources: List<Pair<String, Int>>,
        style: Style
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val k = Kit(context)
        val f = Facts(days, total, values, peakHour, sources)
        when (style) {
            Style.Index -> index(c, k, f)
            Style.Giant -> giant(c, k, f)
            Style.Signal -> signal(c, k, f)
        }
        return bitmap
    }

    /** Shrinks the size until the text fits the width. */
    private fun fit(p: Paint, text: String, maxWidth: Float, start: Float): Paint {
        p.textSize = start
        while (p.measureText(text) > maxWidth && p.textSize > 40f) p.textSize -= 4f
        return p
    }

    // ---------------------------------------------------------------------------------------------
    // INDEX: ruled numbers that step across the sheet, tiny caps labels, one accent dot. After the
    // "year of freelancing in numbers" poster: a label and a rule, then the number, alternating sides.
    // ---------------------------------------------------------------------------------------------
    private fun index(c: Canvas, k: Kit, f: Facts) {
        c.drawColor(k.base)
        val right = 984f
        c.drawText("TAG, INTERCEPTIONS", 96f, 120f, k.paint(k.ink, 24f, k.mono, 0.1f))
        c.drawText("IN NUMBERS  " + f.days, 96f, 154f, k.paint(k.grey, 24f, k.mono, 0.1f))
        c.drawRect(96f, 184f, 96f + 340f, 196f, k.fill(k.ink))
        c.drawCircle(right - 28f, 128f, 28f, k.fill(k.accent))
        c.drawText("T", right - 28f, 139f, k.paint(k.base, 32f, k.bold, 0f, Paint.Align.CENTER))

        data class Block(val label: String, val number: String)
        val blocks = listOf(
            Block("TOTAL OF NOTIFICATIONS\nTURNED INTO CHIPS", f.total.toString()),
            Block("TOTAL AT THE PEAK HOUR\n" + f.peakText, f.peakCount.toString()),
            Block("SHARE FROM THE TOP SOURCE\n" + f.topName.uppercase().take(26), "${f.topShare}%"),
            Block("HOURS OF THE DAY\nWITH ANY ACTIVITY", "${f.activeHours}/24")
        )
        blocks.forEachIndexed { i, b ->
            val top = 262f + i * 240f
            val x0 = if (i % 2 == 0) 96f else 400f
            b.label.split("\n").forEachIndexed { n, t -> c.drawText(t, x0, top + n * 32f, k.paint(k.ink, 24f, k.mono, 0.08f)) }
            if (i % 2 == 0) c.drawLine(x0, top + 70f, right, top + 70f, k.line(k.ink, 2f))
            else c.drawRect(x0, top + 62f, right, top + 74f, k.fill(k.ink))
            val num = fit(k.paint(k.ink, 170f, k.heavy, -0.03f, if (i % 2 == 0) Paint.Align.RIGHT else Paint.Align.LEFT), b.number, right - x0 - 6f, 170f)
            c.drawText(b.number, if (i % 2 == 0) right else x0, top + 70f + 160f, num)
        }

        // The day as a micro bar chart, 24 slim bars.
        val maxV = max(1, f.values.max())
        val bx = 96f
        val bw = (right - bx) / 24f
        for (h in 0 until 24) {
            val bh = 6f + f.values[h] / maxV.toFloat() * 44f
            c.drawRect(bx + h * bw + 2f, 1276f - bh, bx + (h + 1) * bw - 2f, 1276f, k.fill(if (h == f.peakHour) k.accent else k.ink))
        }
        c.drawText("TAG  11DOT04", 96f, 1318f, k.paint(k.grey, 20f, k.mono, 0.1f))
        c.drawText(f.date, right, 1318f, k.paint(k.grey, 20f, k.mono, 0.1f, Paint.Align.RIGHT))
    }

    // ---------------------------------------------------------------------------------------------
    // GIANT: paper and one soft accent glow, an enormous tight numeral with a hairline caption beside
    // it, small outlined pills, a dotted card. After the research-report slide with the 38% and 53%.
    // ---------------------------------------------------------------------------------------------
    private fun giant(c: Canvas, k: Kit, f: Facts) {
        val paper = 0xFFF4F1E8.toInt()
        val ink = 0xFF141414.toInt()
        val mid = 0xFF6B6B66.toInt()
        c.drawColor(paper)
        // The glow: accent fading into the paper.
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(260f, 210f, 560f, intArrayOf((k.accent and 0x00FFFFFF) or (0xE0 shl 24), (k.accent and 0x00FFFFFF)), floatArrayOf(0.15f, 1f), android.graphics.Shader.TileMode.CLAMP)
        }
        c.drawCircle(260f, 210f, 560f, glow)

        // Outlined pill, top left; report mark, top right.
        c.drawRoundRect(72f, 92f, 72f + 330f, 92f + 54f, 27f, 27f, k.line(ink, 3f))
        c.drawText("INTERCEPTION DATA", 72f + 165f, 92f + 35f, k.paint(ink, 22f, k.mono, 0.08f, Paint.Align.CENTER))
        c.drawText("TAG REPORT '" + f.year.takeLast(2), 1008f, 126f, k.paint(ink, 22f, k.mono, 0.08f, Paint.Align.RIGHT))
        c.drawLine(72f, 196f, 1008f, 196f, k.line(ink, 2f))

        // 1: the total.
        val total = f.total.toString()
        c.drawText(total, 52f, 640f, fit(k.paint(ink, 560f, k.heavy, -0.07f), total, 700f, 560f))
        c.drawLine(750f, 386f, 1008f, 386f, k.line(ink, 2f))
        listOf("notifications turned", "into chips across", f.days.lowercase().take(22)).forEachIndexed { i, t ->
            c.drawText(t, 750f, 426f + i * 38f, k.paint(ink, 28f, k.medium))
        }
        c.drawLine(72f, 700f, 1008f, 700f, k.line(ink, 2f))

        // 2: the busiest hour.
        c.drawText(f.peakText, 52f, 960f, fit(k.paint(ink, 300f, k.heavy, -0.06f), f.peakText, 690f, 300f))
        c.drawLine(750f, 790f, 1008f, 790f, k.line(ink, 2f))
        listOf("the busiest hour,", "${f.peakCount} chips, ${f.peakShare}%", "of the total").forEachIndexed { i, t ->
            c.drawText(t, 750f, 830f + i * 38f, k.paint(ink, 28f, k.medium))
        }
        c.drawLine(72f, 1020f, 1008f, 1020f, k.line(ink, 2f))

        // 3: the top source, in a dotted card with a black pill on its edge.
        val dotted = k.line(ink, 2.5f).apply { pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 9f), 0f); strokeCap = Paint.Cap.ROUND }
        c.drawRoundRect(72f, 1080f, 1008f, 1250f, 22f, 22f, dotted)
        c.drawRoundRect(96f, 1054f, 96f + 236f, 1054f + 52f, 26f, 26f, k.fill(ink))
        c.drawText("TOP SOURCE", 96f + 118f, 1054f + 34f, k.paint(paper, 22f, k.mono, 0.08f, Paint.Align.CENTER))
        val name = TextUtils.ellipsize(f.topName, android.text.TextPaint(k.paint(ink, 62f, k.heavy, -0.03f)), 860f, TextUtils.TruncateAt.END).toString()
        c.drawText(name, 104f, 1176f, k.paint(ink, 62f, k.heavy, -0.03f))
        c.drawText("${f.topCount} chips, ${f.topShare}% of the total", 104f, 1224f, k.paint(mid, 26f, k.mono, 0.04f))

        c.drawText("INSIGHT 001, TAG", 72f, 1310f, k.paint(mid, 20f, k.mono, 0.08f))
        c.drawText(f.date, 1008f, 1310f, k.paint(mid, 20f, k.mono, 0.08f, Paint.Align.RIGHT))
    }

    // ---------------------------------------------------------------------------------------------
    // SIGNAL: a mono read-out. Dot matrix of the 24 hours, a dial pointing at the peak, a ruled scale,
    // registration marks in the corners, a sideways status strip. After the dotted decipher labels,
    // the device-link marks and the orange tape.
    // ---------------------------------------------------------------------------------------------
    private fun registration(c: Canvas, k: Kit, x: Float, y: Float) {
        val p = k.line(k.grey, 2f)
        c.drawCircle(x, y, 14f, p)
        c.drawLine(x - 24f, y, x + 24f, y, p)
        c.drawLine(x, y - 24f, x, y + 24f, p)
    }

    private fun signal(c: Canvas, k: Kit, f: Facts) {
        c.drawColor(k.base)
        registration(c, k, 56f, 56f); registration(c, k, W - 56f, 56f)
        registration(c, k, 56f, H - 56f); registration(c, k, W - 56f, H - 56f)

        // The sideways strip, reading upward along the left edge.
        val strip = TextUtils.ellipsize(
            "TERMINAL / HOURLY - ${f.days} - SYS.STATUS: OPERATIONAL - - - - -",
            android.text.TextPaint(k.paint(k.grey, 22f, k.mono, 0.1f)), 1100f, TextUtils.TruncateAt.END
        ).toString()
        c.save(); c.translate(84f, 1240f); c.rotate(-90f)
        c.drawText(strip, 0f, 0f, k.paint(k.grey, 22f, k.mono, 0.1f)); c.restore()
        c.drawLine(110f, 120f, 110f, 1240f, k.line(k.faint, 2f))

        val left = 150f
        val right = 984f
        c.drawText("[ TAG // OUTPUT ]", left, 130f, k.paint(k.accent, 30f, k.mono, 0.06f).apply { isFakeBoldText = true })
        c.drawText("INTERCEPTION LOG", left, 168f, k.paint(k.grey, 22f, k.mono, 0.12f))

        val total = f.total.toString()
        c.drawText(total, left - 8f, 500f, fit(k.paint(k.ink, 330f, k.heavy, -0.04f), total, right - left, 330f))
        c.drawText("INTERCEPTIONS", left, 556f, k.paint(k.accent, 28f, k.mono, 0.2f))

        // The validation box: outlined, with a dial whose needle points at the peak hour.
        val box = k.line(k.ink, 2f)
        c.drawRoundRect(left, 624f, left + 400f, 624f + 108f, 12f, 12f, box)
        listOf("VALIDATED", "PEAK: ${f.peakText}", "COUNT: ${f.peakCount}").forEachIndexed { i, t ->
            c.drawText(t, left + 20f, 662f + i * 28f, k.paint(k.ink, 22f, k.mono, 0.06f))
        }
        val dx = left + 340f; val dy = 678f
        c.drawCircle(dx, dy, 32f, box)
        f.peakHour?.let { h ->
            val a = Math.toRadians((h / 24.0) * 360.0 - 90.0)
            c.drawLine(dx, dy, dx + (26 * Math.cos(a)).toFloat(), dy + (26 * Math.sin(a)).toFloat(), k.line(k.accent, 4f))
        }
        listOf("TOP SRC  " + f.topName.uppercase().take(14), "ACTIVE   ${f.activeHours}/24 H", "SHARE    ${f.topShare}%").forEachIndexed { i, t ->
            c.drawText(t, left + 450f, 650f + i * 38f, k.paint(k.ink, 24f, k.mono, 0.04f))
        }

        // The dot matrix: 24 columns, 6 rows, lit from the bottom. The peak column is in ink.
        c.drawText("[ HOURLY ]", left, 830f, k.paint(k.grey, 22f, k.mono, 0.12f))
        val maxV = max(1, f.values.max())
        val colW = (right - left) / 24f
        val lit = k.fill(k.accent); val peakLit = k.fill(k.ink); val off = k.fill(k.faint)
        for (h in 0 until 24) {
            val n = if (f.values[h] == 0) 0 else max(1, Math.ceil(f.values[h] * 6.0 / maxV).toInt())
            for (r in 0 until 6) {
                val x = left + colW * (h + 0.5f)
                val y = 1112f - r * 44f
                val on = r < n
                c.drawCircle(x, y, if (on) 11f else 6f, if (on) (if (h == f.peakHour) peakLit else lit) else off)
            }
        }
        // The scale.
        val base = 1160f
        c.drawLine(left, base, right, base, k.line(k.ink, 2f))
        for (h in 0..24) {
            val x = left + colW * h
            c.drawLine(x, base, x, base + if (h % 6 == 0) 22f else 10f, k.line(k.ink, 2f))
            if (h % 6 == 0) c.drawText("%02d".format(h), x, base + 52f, k.paint(k.grey, 20f, k.mono, 0.06f, Paint.Align.CENTER))
        }
        c.drawText("(C) ${f.year} TAG  11DOT04", left, 1296f, k.paint(k.grey, 20f, k.mono, 0.1f))
        c.drawText(f.date, right, 1296f, k.paint(k.grey, 20f, k.mono, 0.1f, Paint.Align.RIGHT))
    }
}
