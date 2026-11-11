package com.microtag.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.microtag.ai.Ai
import com.microtag.ai.AiConfig
import com.microtag.ai.AiProtocol
import com.microtag.ai.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The AI helper's settings. Off until switched on and given a key. The key never leaves the phone except to the address
 * shown here, and is kept encrypted with the Android Keystore.
 */
@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    var cfg by remember { mutableStateOf(Ai.config(context)) }
    var hasKey by remember { mutableStateOf(Ai.hasKey(context)) }
    var keyText by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }

    fun update(c: AiConfig) { cfg = c; Ai.save(context, c) }

    SubScreen(onBack, spacing = 10.dp) {
        FormGap(12)
        FormTitle("AI")
        FormCaption("Optional. It helps write rules and shortens long chips. Nothing is sent until you turn it on, and only a scrubbed copy of a notification is ever sent: long numbers, emails and names are masked first.")
        FormGap(4)

        FormSwitch("Use AI", if (cfg.enabled && !hasKey) "Add a key below to finish" else null, cfg.enabled) { update(cfg.copy(enabled = it)) }

        FormLabel("Provider")
        FormWrap {
            AiProvider.values().forEach { p ->
                FormChoice(p.label, cfg.provider == p, {
                    if (cfg.provider != p) update(cfg.copy(provider = p, baseUrl = AiProtocol.defaultBase(p), model = AiProtocol.defaultModel(p)))
                })
            }
        }
        if (cfg.provider == AiProvider.OPENAI_COMPATIBLE) {
            FormCaption("OpenAI, Gemini's compatible address, OpenRouter, or a model running on your own network all work here.")
            FormLabel("Presets")
            FormWrap {
                val gemini = "https://generativelanguage.googleapis.com/v1beta/openai"
                FormChoice("Gemini", cfg.baseUrl == gemini, { update(cfg.copy(baseUrl = gemini, model = "gemini-2.5-flash")) })
                FormChoice("OpenAI", cfg.baseUrl == AiProtocol.defaultBase(AiProvider.OPENAI_COMPATIBLE), {
                    update(cfg.copy(baseUrl = AiProtocol.defaultBase(AiProvider.OPENAI_COMPATIBLE), model = AiProtocol.defaultModel(AiProvider.OPENAI_COMPATIBLE)))
                })
            }
            if (cfg.baseUrl.contains("generativelanguage.googleapis.com")) FormCaption("Key from Google AI Studio. The free tier is rate-limited. Use gemini-2.5-flash: the gemini-flash-latest alias is rejected.")
            FormLabel("Address")
            FormField(cfg.baseUrl, { update(cfg.copy(baseUrl = it.trim())) }, "https://…", mono = true)
        }
        FormLabel("Model")
        FormField(cfg.model, { update(cfg.copy(model = it.trim())) }, "model name", mono = true)

        FormLabel("Key")
        if (hasKey) {
            FormPlate { FormCaption("A key is saved on this phone.", color = TagColors.White) }
            Row {
                FormButton("Replace", { hasKey = false }, filled = false)
                FormGapW()
                FormButton("Remove", { Ai.setKey(context, ""); hasKey = false; update(cfg.copy(enabled = false)) }, filled = false, danger = true)
            }
        } else {
            FormField(keyText, { keyText = it }, "Paste your key", mono = true, secret = true)
            FormButton("Save key", {
                if (keyText.isNotBlank()) { Ai.setKey(context, keyText); keyText = ""; hasKey = Ai.hasKey(context) }
            }, enabled = keyText.isNotBlank())
        }

        FormLabel("What it may do")
        FormSwitch("Shorten long chips", "Only for rules set to “Shorten with AI”.", cfg.condense) { update(cfg.copy(condense = it)) }
        FormSwitch("Help write rules", "Adds an “Ask AI” button to the rule builder.", cfg.ruleHelp) { update(cfg.copy(ruleHelp = it)) }

        FormLabel("Limit")
        FormWrap {
            listOf(10, 30, 100).forEach { n -> FormChoice("$n an hour", cfg.callsPerHour == n, { update(cfg.copy(callsPerHour = n)) }) }
        }

        FormGap(8)
        FormButton(if (testing) "Testing…" else "Test it", {
            testing = true; result = null
            scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { Ai.test(context) }.getOrNull() }
                testing = false
                failed = r == null || !r.ok
                result = if (r == null) "It could not be reached" else if (r.ok) "Working" else (r.error ?: "It did not answer")
            }
        }, enabled = !testing)
        result?.let { if (failed) FormNotice(it) else FormPlate { FormCaption(it, color = TagColors.White) } }
        FormGap(24)
    }
}
