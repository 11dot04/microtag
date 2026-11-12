package com.microtag.rules

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import java.io.File
import java.io.FileOutputStream

/** The rule book on Android: init once, then evaluate notifications, list installed apps' icons and save rules. */
object UserRules {
    /** The tile name rules of the person's own report, so the log and the clear-on-off logic can tell them apart. */
    const val TILE = "custom"

    @Volatile private var book: UserRuleBook? = null

    fun init(context: Context) {
        if (book != null) return
        synchronized(this) { if (book == null) book = UserRuleBook(context.applicationContext.filesDir) }
    }

    fun book(context: Context): UserRuleBook { init(context); return book!! }

    /** Used by the listener, for every notification. Cheap when there are no rules for the app. */
    fun evaluate(context: Context, pkg: String, title: String, text: String): UserMatch? = book(context).evaluate(pkg, title, text)

    /** The status-bar icon for a match: the app's own monochrome icon when asked and readable, otherwise null (use the drawable name). */
    fun iconFor(context: Context, rule: UserRule): Icon? = when (rule.iconMode) {
        UserRule.IconMode.APP -> AppIcons.icon(context, rule.pkg)
        else -> null
    }
}

/**
 * An installed app's icon as a one-colour glyph for the status bar. Never blank for an installed app:
 *  1. the themed (monochrome) layer when the app ships one (Android 13+);
 *  2. otherwise a glyph derived from the whole rendered icon by contrast against its dominant (background) colour,
 *     so full-colour art and pre-masked full-bleed icons keep their shape instead of becoming a white blob;
 *  3. otherwise a monogram: the first letter of the app's name.
 * Cached as a PNG per app.
 */
object AppIcons {
    private const val SIZE = 96

    private fun cacheFile(context: Context, pkg: String) = File(File(context.filesDir, "rule_icons_v2").apply { mkdirs() }, "$pkg.png")

    fun icon(context: Context, pkg: String): Icon? {
        val f = cacheFile(context, pkg)
        val bmp = if (f.exists()) BitmapFactory.decodeFile(f.path) else render(context, pkg)?.also { b ->
            runCatching { FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
        return bmp?.let { Icon.createWithBitmap(it) }
    }

    /** Call when an app is updated or removed. */
    fun forget(context: Context, pkg: String) { runCatching { cacheFile(context, pkg).delete() } }

    /** The white glyph the chip will draw. Null only if the app is not installed. */
    fun render(context: Context, pkg: String): Bitmap? = try {
        val d = context.packageManager.getApplicationIcon(pkg)
        val mono = if (Build.VERSION.SDK_INT >= 33 && d is AdaptiveIconDrawable) d.monochrome else null
        (mono?.let { monochromeGlyph(it) }
            ?: derivedGlyph(d)
            ?: monogram(label(context, pkg)))
    } catch (_: Exception) {
        null
    }

    /** The app's real, coloured icon, for recognition in the picker. */
    fun colour(context: Context, pkg: String): Bitmap? = try {
        drawToBitmap(context.packageManager.getApplicationIcon(pkg))
    } catch (_: Exception) {
        null
    }

    private fun label(context: Context, pkg: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.'))

    private fun drawToBitmap(layer: Drawable): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val d = layer.mutate()
        d.setBounds(0, 0, SIZE, SIZE)
        d.draw(Canvas(bmp))
        return bmp
    }

    private fun whiten(src: Bitmap): Bitmap {
        val white = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val p = Paint().apply { colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN) }
        Canvas(white).drawBitmap(src, 0f, 0f, p)
        return white
    }

    private fun coverage(b: Bitmap): Float {
        val px = IntArray(SIZE * SIZE)
        b.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        var n = 0
        for (v in px) if ((v ushr 24) > 128) n++
        return n / px.size.toFloat()
    }

    /** A monochrome layer is already a shape: whiten it, and keep it unless it is truly empty. */
    private fun monochromeGlyph(layer: Drawable): Bitmap? {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val pad = (SIZE * 0.25f).toInt()
        val d = layer.mutate()
        d.setBounds(-pad, -pad, SIZE + pad, SIZE + pad)
        d.draw(Canvas(bmp))
        val white = whiten(bmp)
        return if (coverage(white) < 0.01f) null else white
    }

    /**
     * No themed layer: render the whole icon, find its dominant opaque colour (the background), and make each pixel's
     * alpha the colour distance from it. What stands out from the background becomes the glyph.
     */
    private fun derivedGlyph(icon: Drawable): Bitmap? {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        icon.mutate().apply { setBounds(0, 0, SIZE, SIZE) }.draw(Canvas(bmp))
        val px = IntArray(SIZE * SIZE)
        bmp.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        // Dominant colour, quantised to 4 bits per channel.
        val hist = HashMap<Int, Int>()
        for (v in px) {
            if ((v ushr 24) < 200) continue
            val k = ((Color.red(v) shr 4) shl 8) or ((Color.green(v) shr 4) shl 4) or (Color.blue(v) shr 4)
            hist[k] = (hist[k] ?: 0) + 1
        }
        val top = hist.maxByOrNull { it.value } ?: return null
        val br = ((top.key shr 8) and 15) * 17; val bg = ((top.key shr 4) and 15) * 17; val bb = (top.key and 15) * 17
        val out = IntArray(px.size)
        var opaque = 0
        for (i in px.indices) {
            val v = px[i]
            val a0 = (v ushr 24) / 255f
            val dr = Color.red(v) - br; val dg = Color.green(v) - bg; val db = Color.blue(v) - bb
            val dist = Math.sqrt((dr * dr + dg * dg + db * db).toDouble()).toFloat()
            val a = (((dist - 40f) / 90f).coerceIn(0f, 1f)) * a0
            if (a > 0.5f) opaque++
            out[i] = (Math.round(a * 255f) shl 24) or 0x00FFFFFF
        }
        val share = opaque / px.size.toFloat()
        // Nothing stands out (a flat icon), or nearly everything does (a busy photo): not a usable glyph.
        if (share < 0.02f || share > 0.80f) return null
        val result = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, SIZE, 0, 0, SIZE, SIZE)
        return result
    }

    /** The first letter of the app's name, bold, white, centred. */
    private fun monogram(label: String): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val letter = label.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = SIZE * 0.66f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val y = SIZE / 2f - (p.descent() + p.ascent()) / 2f
        Canvas(bmp).drawText(letter, SIZE / 2f, y, p)
        return bmp
    }
}
