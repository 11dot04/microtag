package com.microtag.ai

import org.json.JSONObject
import java.net.URI

enum class AiProvider(val label: String) {
    /** Anthropic's Messages API. */
    ANTHROPIC("Anthropic"),
    /** Anything that speaks the chat-completions protocol: OpenAI, Gemini's compatible endpoint, OpenRouter, a local Ollama. */
    OPENAI_COMPATIBLE("OpenAI-compatible")
}

data class AiConfig(
    val provider: AiProvider = AiProvider.ANTHROPIC,
    val baseUrl: String = AiProtocol.defaultBase(AiProvider.ANTHROPIC),
    val model: String = AiProtocol.defaultModel(AiProvider.ANTHROPIC),
    /** The master switch. Off until the person turns it on and gives a key. */
    val enabled: Boolean = false,
    /** Shorten a long pill with the model. */
    val condense: Boolean = true,
    /** Help write rules with the model. */
    val ruleHelp: Boolean = true,
    val callsPerHour: Int = 30
)

class AiRequest(val url: String, val headers: Map<String, String>, val body: String)

class AiResult(val text: String?, val error: String?) {
    val ok: Boolean get() = text != null
}

/** One round trip. The real one talks to a provider; tests use a fake. */
fun interface Completer {
    fun complete(system: String, user: String, maxTokens: Int): AiResult
}

/** Requests and replies for each provider. Pure Kotlin, so it is tested on the JVM. */
object AiProtocol {
    fun defaultBase(p: AiProvider) = when (p) {
        AiProvider.ANTHROPIC -> "https://api.anthropic.com"
        AiProvider.OPENAI_COMPATIBLE -> "https://api.openai.com/v1"
    }

    fun defaultModel(p: AiProvider) = when (p) {
        AiProvider.ANTHROPIC -> "claude-haiku-5-5"
        AiProvider.OPENAI_COMPATIBLE -> "gpt-4o-mini"
    }

    /**
     * A key travels with every request, so the address must be HTTPS. The one exception is a model running on the
     * person's own machine or network (localhost, a private address), which has no key to protect.
     */
    fun endpointProblem(baseUrl: String): String? {
        val u = runCatching { URI(baseUrl.trim()) }.getOrNull() ?: return "That address is not valid"
        val host = u.host ?: return "That address is not valid"
        return when (u.scheme?.lowercase()) {
            "https" -> null
            "http" -> if (isPrivateHost(host)) null else "Use https, or an address on your own network"
            else -> "That address is not valid"
        }
    }

    private fun isPrivateHost(h: String): Boolean =
        h == "localhost" || h.endsWith(".local") || h.startsWith("10.") || h.startsWith("192.168.") ||
            Regex("""^172\.(1[6-9]|2\d|3[01])\.""").containsMatchIn(h) || h == "127.0.0.1"

    fun jsonString(s: String): String = JSONObject.quote(s)

    fun build(cfg: AiConfig, key: String, system: String, user: String, maxTokens: Int): AiRequest {
        val base = cfg.baseUrl.trim().trimEnd('/')
        return when (cfg.provider) {
            AiProvider.ANTHROPIC -> AiRequest(
                url = "$base/v1/messages",
                headers = mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01", "content-type" to "application/json"),
                body = """{"model":${jsonString(cfg.model)},"max_tokens":$maxTokens,"system":${jsonString(system)},""" +
                    """"messages":[{"role":"user","content":${jsonString(user)}}]}"""
            )
            AiProvider.OPENAI_COMPATIBLE -> AiRequest(
                url = "$base/chat/completions",
                headers = mapOf("Authorization" to "Bearer $key", "content-type" to "application/json"),
                body = """{"model":${jsonString(cfg.model)},"max_tokens":$maxTokens,"temperature":0,""" +
                    """"messages":[{"role":"system","content":${jsonString(system)}},{"role":"user","content":${jsonString(user)}}]}"""
            )
        }
    }

    /** The reply's text, or null if the body is not in the shape the provider promises. */
    fun parse(cfg: AiConfig, body: String): String? = runCatching {
        val o = JSONObject(body)
        when (cfg.provider) {
            AiProvider.ANTHROPIC -> {
                val arr = o.optJSONArray("content") ?: return null
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("text")?.takeIf { t -> t.isNotEmpty() } }
                    .joinToString("").ifBlank { null }
            }
            AiProvider.OPENAI_COMPATIBLE -> {
                val first = o.optJSONArray("choices")?.optJSONObject(0) ?: return null
                first.optJSONObject("message")?.optString("content")?.ifBlank { null }
            }
        }
    }.getOrNull()

    /** A sentence for a failed call. The body is never echoed whole: it can contain the request. */
    fun errorMessage(code: Int, body: String): String = when {
        code == -1 -> "No connection to the model"
        code == -2 -> "That address is not allowed"
        code == 401 || code == 403 -> "The key was refused"
        code == 404 -> "The model or address was not found"
        code == 429 -> "The model is rate-limiting this key"
        code in 500..599 -> "The model service is having trouble"
        else -> runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()?.take(120)?.ifBlank { null }
            ?: "The model said no ($code)"
    }

    /** The JSON object in a reply, even if it is wrapped in a code fence or a sentence. */
    fun extractJson(reply: String): JSONObject? {
        val a = reply.indexOf('{'); val b = reply.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        return runCatching { JSONObject(reply.substring(a, b + 1)) }.getOrNull()
    }
}
