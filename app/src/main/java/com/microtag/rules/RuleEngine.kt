package com.microtag.rules

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.InputStreamReader

object RuleEngine {
    private const val TAG = "ApertureRuleEngine"

    // Swapped as a whole so the listener thread never sees a half-built list.
    @Volatile
    private var rules: List<ApertureRule> = emptyList()

    fun init(context: Context) {
        val loaded = mutableListOf<ApertureRule>()
        try {
            context.assets.open("custom_rules.json").use { stream ->
                val jsonArr = JSONArray(InputStreamReader(stream).readText())
                for (i in 0 until jsonArr.length()) {
                    ApertureRule.fromJson(jsonArr.getJSONObject(i))?.let(loaded::add)
                }
            }
            Log.d(TAG, "Loaded ${loaded.size} rules from custom_rules.json")
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading custom_rules.json: ${e.message}")
        }
        rules = loaded
    }

    fun getActiveRules(): List<ApertureRule> = rules

    /**
     * [allow] runs before a rule's regex does, so a rule that is switched off
     * (tile off, app not picked for streaks) is never matched against the text at all.
     */
    fun evaluate(
        packageName: String,
        title: String,
        content: String,
        allow: (ApertureRule) -> Boolean = { true }
    ): MatchResult? {
        val targetText = "$title $content".trim()

        for (rule in rules) {
            if (!rule.packageName.equals(packageName, ignoreCase = true)) continue
            if (!allow(rule)) continue

            val match = rule.regex.find(targetText) ?: continue

            var outputPill = rule.pillText
            match.groupValues.forEachIndexed { idx, value ->
                outputPill = outputPill.replace("$$idx", value)
            }
            // Template had no $n tokens: fall back to the first capture group.
            if (outputPill == rule.pillText && match.groupValues.size > 1) {
                outputPill = match.groupValues[1]
            }

            return MatchResult(
                pillText = outputPill.trim(),
                iconName = rule.iconName,
                timeoutSeconds = rule.timeoutSeconds,
                tile = rule.tile
            )
        }
        return null
    }
}
