package com.microtag.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.microtag.listener.PillText
import java.util.concurrent.Executors

/**
 * The AI option, off by default. Settings live in "microtag_ai", the key in the Keystore (see [SecretBox]).
 * Everything here is optional: with it off, or with no key, or offline, the app behaves exactly as without it.
 */
object Ai {
    private const val PREFS = "microtag_ai"
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var appContext: Context? = null

    private val condenser = AiCondenser(completer = { completerNow() }, callsPerHour = { config().callsPerHour })
    private val ruleHelper = AiRuleHelper { if (config().ruleHelp) completerNow() else null }

    fun init(context: Context) { appContext = context.applicationContext }

    fun config(context: Context? = appContext): AiConfig {
        val sp = (context ?: return AiConfig()).getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val provider = AiProvider.values().firstOrNull { it.name == sp.getString("provider", null) } ?: AiProvider.ANTHROPIC
        return AiConfig(
            provider = provider,
            baseUrl = sp.getString("base_url", null) ?: AiProtocol.defaultBase(provider),
            model = sp.getString("model", null) ?: AiProtocol.defaultModel(provider),
            enabled = sp.getBoolean("enabled", false),
            condense = sp.getBoolean("condense", true),
            ruleHelp = sp.getBoolean("rule_help", true),
            callsPerHour = sp.getInt("calls_per_hour", 30)
        )
    }

    /** Saves the settings. Choosing a provider resets the address and model to its defaults unless they are given. */
    fun save(context: Context, cfg: AiConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("provider", cfg.provider.name).putString("base_url", cfg.baseUrl).putString("model", cfg.model)
            .putBoolean("enabled", cfg.enabled).putBoolean("condense", cfg.condense).putBoolean("rule_help", cfg.ruleHelp)
            .putInt("calls_per_hour", cfg.callsPerHour.coerceIn(1, 500)).apply()
        appContext = context.applicationContext
    }

    fun setKey(context: Context, key: String) = SecretBox.put(context, key.trim())
    fun hasKey(context: Context): Boolean = SecretBox.has(context)

    /** On, with a key and a safe address. */
    fun ready(context: Context? = appContext): Boolean {
        val c = context ?: return false
        val cfg = config(c)
        return cfg.enabled && SecretBox.has(c) && AiProtocol.endpointProblem(cfg.baseUrl) == null
    }

    private fun completerNow(): Completer? {
        val c = appContext ?: return null
        val cfg = config(c)
        if (!ready(c)) return null
        val key = SecretBox.get(c) ?: return null
        return AiClient(cfg, key)
    }

    /** Whether a pill can be improved by the model right now. */
    fun canCondense(): Boolean = appContext?.let { config(it).condense && ready(it) && condenser.available() } == true

    /**
     * Asks for a shorter form off the calling thread. [onResult] runs on the main thread, and only with a text that
     * differs from [current]. Silence means nothing better came back.
     */
    fun condenseAsync(app: String, title: String, text: String, current: String, limit: Int = PillText.LIMIT, onResult: (String) -> Unit) {
        if (!canCondense()) return
        io.execute {
            val better = condenser.condense(app, title, text, limit)
            if (better != null && better != current) main.post { onResult(better) }
        }
    }

    /** Writes a rule from examples. Blocking: call it from a background thread. */
    fun suggestRule(request: AiRuleHelper.Request): AiRuleHelper.Answer = ruleHelper.suggest(request)

    /** A one-off test of the settings: a tiny request that proves the key, address and model work. Blocking. */
    fun test(context: Context): AiResult {
        val c = completerFor(context) ?: return AiResult(null, "Turn it on and add a key first")
        return c.complete("Reply with the single word: ok", "ping", 5)
    }

    private fun completerFor(context: Context): Completer? { appContext = context.applicationContext; return completerNow() }
}
