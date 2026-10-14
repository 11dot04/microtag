package com.microtag.inspect

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Small image fetcher for the card (the Wikipedia thumbnail). HTTPS only, size capped, downsampled, cached in memory. */
object CardImages {
    private val cache = LruCache<String, Bitmap>(8)

    /** Blocking; call from a background thread. Returns null on any failure. */
    fun load(url: String, maxPx: Int = 720): Bitmap? {
        if (!url.startsWith("https://")) return null
        cache.get(url)?.let { return it }
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 4500
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Microtag/1.0 (Android; text inspector)")
            }
            if (conn.responseCode !in 200..299) return null
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            conn.inputStream.use { s ->
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > 2_500_000) return null
                    out.write(buf, 0, n)
                }
            }
            val bytes = out.toByteArray()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
            cache.put(url, bmp)
            return bmp
        } catch (_: Exception) {
            return null
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }
}
