package com.microtag.inspect

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** code >= 0 is an HTTP status; -1 is a network failure (offline, timeout, DNS); -2 is a refused (non-https) URL. */
internal class HttpResult(val code: Int, val body: String) {
    val ok: Boolean get() = code in 200..299
    val networkError: Boolean get() = code == -1
}

/** Small, strict HTTP GET. HTTPS only, short timeouts, capped body size. */
internal object Http {

    fun get(url: String, timeoutMs: Int = 3000, maxBytes: Int = 1_000_000): HttpResult {
        if (!url.startsWith("https://")) return HttpResult(-2, "")
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Microtag/1.0 (Android; text inspector)")
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { readLimited(it, maxBytes) } ?: ""
            return HttpResult(code, body)
        } catch (_: Exception) {
            return HttpResult(-1, "")
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun readLimited(s: InputStream, max: Int): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = s.read(buf)
            if (n < 0) break
            total += n
            if (total > max) break
            out.write(buf, 0, n)
        }
        return out.toString("UTF-8")
    }

    fun encode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
