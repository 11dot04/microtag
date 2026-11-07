package com.microtag.rules

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
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
 * An installed app's icon as a one-colour glyph for the status bar. The themed (monochrome) layer is used when the app
 * ships one (Android 13+); otherwise the shape of the foreground layer. A glyph that fills its whole square is rejected
 * (it would be a white block) and the caller falls back to a built-in icon. Cached as a PNG per app.
 */
object AppIcons {
    private const val SIZE = 96

    private fun cacheFile(context: Context, pkg: String) = File(File(context.filesDir, "rule_icons").apply { mkdirs() }, "$pkg.png")

    fun icon(context: Context, pkg: String): Icon? {
        val f = cacheFile(context, pkg)
        val bmp = if (f.exists()) BitmapFactory.decodeFile(f.path) else render(context, pkg)?.also { b ->
            runCatching { FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
        return bmp?.let { Icon.createWithBitmap(it) }
    }

    /** Call when an app is updated or removed. */
    fun forget(context: Context, pkg: String) { runCatching { cacheFile(context, pkg).delete() } }

    fun render(context: Context, pkg: String): Bitmap? = try {
        val d = context.packageManager.getApplicationIcon(pkg)
        val layer: Drawable? = if (d is AdaptiveIconDrawable) {
            (if (Build.VERSION.SDK_INT >= 33) d.monochrome else null) ?: d.foreground
        } else d
        layer?.let { glyph(it) }
    } catch (_: Exception) {
        null
    }

    private fun glyph(layer: Drawable): Bitmap? {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // Adaptive layers are drawn on a 108 grid with a 72 safe zone: scale so the safe zone fills the square.
        val pad = (SIZE * 0.25f).toInt()
        val d = layer.mutate()
        d.setBounds(-pad, -pad, SIZE + pad, SIZE + pad)
        d.draw(c)
        // Keep the shape (alpha) and make it white: the system tints a small icon anyway.
        val white = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val p = android.graphics.Paint().apply { colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN) }
        Canvas(white).drawBitmap(bmp, 0f, 0f, p)
        // Reject a block: nearly every pixel opaque means there is no glyph, only a background.
        var opaque = 0
        val px = IntArray(SIZE * SIZE)
        white.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        for (v in px) if ((v ushr 24) > 200) opaque++
        return if (opaque > px.size * 0.92f || opaque < px.size * 0.01f) null else white
    }
}
