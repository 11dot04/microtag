package com.microtag.rules

import android.util.Log
import com.microtag.core.TileGate
import org.json.JSONObject
import java.util.regex.PatternSyntaxException

/** One entry of assets/custom_rules.json. */
data class ApertureRule(
    val packageName: String,
    val regex: Regex,
    val pillText: String,
    val iconName: String?,
    val timeoutSeconds: Int,
    /** MicrotagPrefs key of the dashboard tile that owns this rule, or null if no tile does. */
    val tile: String?
) {
    companion object {
        private const val TAG = "ApertureRule"

        fun fromJson(o: JSONObject): ApertureRule? {
            val pkg = o.optString("package_name").trim()
            val pattern = o.optString("regex")
            if (pkg.isEmpty() || pattern.isEmpty()) {
                Log.w(TAG, "Skipping rule without package_name/regex")
                return null
            }
            val regex = try {
                Regex(pattern)
            } catch (e: PatternSyntaxException) {
                Log.w(TAG, "Skipping rule for $pkg, bad regex: ${e.message}")
                return null
            }

            // An unknown tile name drops the rule instead of leaving it ungated by mistake.
            val tileName = o.optString("tile").trim().ifEmpty { null }
            val tile = TileGate.keyFor(tileName)
            if (tileName != null && tile == null) {
                Log.w(TAG, "Skipping rule for $pkg, unknown tile '$tileName'")
                return null
            }

            return ApertureRule(
                packageName = pkg,
                regex = regex,
                pillText = o.optString("pill_text"),
                iconName = o.optString("icon_name").trim().ifEmpty { null },
                timeoutSeconds = o.optInt("timeout_seconds", 0),
                tile = tile
            )
        }
    }
}

/** What RuleEngine.evaluate hands back. Note: shadows kotlin.text.MatchResult inside this package. */
data class MatchResult(
    val pillText: String,
    val iconName: String?,
    val timeoutSeconds: Int,
    val tile: String?
)
