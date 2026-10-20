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

    suspend fun share(
        context: Context,
        days: String,
        total: Int,
        values: IntArray,
        peakHour: Int?,
        sources: List<Pair<String, Int>>
    ) {
        val uri = withContext(Dispatchers.IO) {
            runCatching { save(context, render(context, days, total, values, peakHour, sources)) }.getOrNull()
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

    private fun render(
        context: Context,
        days: String,
        total: Int,
        values: IntArray,
        peakHour: Int?,
        sources: List<Pair<String, Int>>
    ): Bitmap {
        val w = 1080
        val h = 1350
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)

        // Everything comes from the active palette: its base, ink and accent, and its display face when it has one.
        val accent = TagColors.Lime.toArgb()
        val black = TagColors.Black.toArgb()      // the base colour (cream on a light palette)
        val white = TagColors.White.toArgb()      // the ink
        val grey = TagColors.Outline.toArgb()
        val faint = TagColors.White.copy(alpha = 0.27f).toArgb()

        val heavy = TagFonts.typeface(context, TagColors.DisplayBase) ?: ResourcesCompat.getFont(context, R.font.montserrat_black)
        val bold = ResourcesCompat.getFont(context, R.font.montserrat_bold)
        val medium = ResourcesCompat.getFont(context, R.font.montserrat_medium)
        val mono = TagFonts.typeface(context, TagColors.MonoBase, listOf("medium", "regular", "")) ?: Typeface.MONOSPACE

        fun paint(color: Int, size: Float, face: Typeface?, spacingEm: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = face
            letterSpacing = spacingEm
        }

        c.drawColor(black)

        // registration crosses in the four corners, like the rest of the app
        val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = grey; strokeWidth = 3f }
        fun cross(x: Float, y: Float, r: Float = 14f) {
            c.drawLine(x - r, y, x + r, y, crossPaint)
            c.drawLine(x, y - r, x, y + r, crossPaint)
        }
        if (TagColors.MicroOn) {
            cross(36f, 36f); cross(w - 36f, 36f); cross(36f, h - 36f); cross(w - 36f, h - 36f)
            val annotation = paint(grey, 26f, mono, 0.08f).apply { textAlign = Paint.Align.RIGHT }
            c.drawText("TAG / STATS", w - 72f, 120f, annotation)
        }

        // wordmark, in the accent
        ContextCompat.getDrawable(context, R.drawable.ic_tag_wordmark)?.mutate()?.let {
            it.setTint(accent)
            it.setBounds(72, 72, 72 + 216, 72 + 81)
            it.draw(c)
        }
        c.drawText(days, 72f, 230f, paint(grey, 34f, medium, 0.08f))

        // the number
        c.drawText(total.toString(), 56f, 600f, paint(white, 400f, heavy, -0.04f))
        c.drawText("INTERCEPTIONS", 72f, 690f, paint(white, 72f, heavy, -0.02f))

        // hourly curve
        val left = 72f
        val right = w - 72f
        val top = 790f
        val bottom = 1060f
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = faint; strokeWidth = 2f }
        val label = paint(grey, 26f, medium).apply { textAlign = Paint.Align.CENTER }
        for (t in 0..24 step 4) {
            val x = left + t / 24f * (right - left)
            c.drawLine(x, top - 20f, x, bottom, grid)
            if (t < 24) c.drawText("%02d".format(t), x, bottom + 40f, label)
        }

        val maxV = max(1, values.max())
        fun px(hour: Int) = left + (hour + 0.5f) / 24f * (right - left)
        fun py(v: Int) = bottom - v / maxV.toFloat() * (bottom - top) * 0.9f
        val pts = List(24) { px(it) to py(values[it]) }
        val path = Path().apply {
            moveTo(pts[0].first, pts[0].second)
            for (i in 0 until 23) {
                val p0 = pts[max(i - 1, 0)]
                val p1 = pts[i]
                val p2 = pts[i + 1]
                val p3 = pts[min(i + 2, 23)]
                cubicTo(
                    p1.first + (p2.first - p0.first) / 6f, (p1.second + (p2.second - p0.second) / 6f).coerceIn(top, bottom),
                    p2.first - (p3.first - p1.first) / 6f, (p2.second - (p3.second - p1.second) / 6f).coerceIn(top, bottom),
                    p2.first, p2.second
                )
            }
        }
        c.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 8f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = white
            }
        )
        if (peakHour != null) {
            val (x, y) = pts[peakHour]
            c.drawCircle(x, y, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = black })
            c.drawCircle(x, y, 15f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
            val tag = paint(white, 44f, bold).apply { textAlign = Paint.Align.CENTER }
            c.drawText("${values[peakHour]} at %02d:00".format(peakHour), x.coerceIn(200f, w - 200f), top - 40f, tag)
        }

        // headline facts
        val cap = paint(grey, 26f, bold, 0.12f)
        val big = paint(white, 64f, heavy, -0.02f)
        c.drawText("PEAK HOUR", 72f, 1150f, cap)
        c.drawText(peakHour?.let { "%02d:00".format(it) } ?: "--", 72f, 1220f, big)
        val topSource = sources.firstOrNull()
        c.drawText("TOP SOURCE", 460f, 1150f, cap)
        val name = TextUtils.ellipsize(topSource?.first ?: "--", asTextPaint(big), 520f, TextUtils.TruncateAt.END).toString()
        c.drawText(name, 460f, 1220f, big)

        // footer
        val foot = paint(grey, 30f, medium, 0.06f)
        c.drawText("TAG  ·  11DOT04", 72f, 1296f, foot)
        foot.textAlign = Paint.Align.RIGHT
        c.drawText(SimpleDateFormat("dd.MM.yy", Locale.US).format(Date()), w - 72f, 1296f, foot)
        return bitmap
    }

    private fun asTextPaint(p: Paint) = android.text.TextPaint(p)
}
