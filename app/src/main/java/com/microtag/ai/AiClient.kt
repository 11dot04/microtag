package com.microtag.ai

import java.net.HttpURLConnection
import java.net.URL

/** The real round trip to a provider. Blocking: call it off the main and listener threads. */
class AiClient(private val cfg: AiConfig, private val key: String, private val timeoutMs: Int = 8000) : Completer {

    override fun complete(system: String, user: String, maxTokens: Int): AiResult {
        AiProtocol.endpointProblem(cfg.baseUrl)?.let { return AiResult(null, it) }
        val req = AiProtocol.build(cfg, key, system, user, maxTokens)
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(req.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                doOutput = true
                instanceFollowRedirects = false
                req.headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            conn.outputStream.use { it.write(req.body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { it.readBytes().take(200_000).toByteArray().toString(Charsets.UTF_8) }.orEmpty()
            if (code !in 200..299) AiResult(null, AiProtocol.errorMessage(code, body))
            else AiProtocol.parse(cfg, body)?.let { AiResult(it, null) } ?: AiResult(null, "The reply was not readable")
        } catch (_: Exception) {
            AiResult(null, AiProtocol.errorMessage(-1, ""))
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }
}
