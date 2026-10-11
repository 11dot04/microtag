package com.microtag.rules

import android.util.Log
import com.microtag.core.TileGate
import org.json.JSONObject

/** One entry of assets/custom_rules.json. */
data class ApertureRule(
    val packageName: String,
    val regex: Regex,
    val pillText: String,
    /** Null falls back to the default capsule icon (ic_capsule_bolt) in MicrotagReminder. */
    val iconName: String?,
    val timeoutSeconds: Int,
    /** MicrotagPrefs key of the dashboard tile that owns this rule, or null if none does. */
    val tile: String?
) {
    companion object {
        private const val TAG = "ApertureRule"

        fun fromJson(json: JSONObject): ApertureRule? {
            val pkg = json.optString("package_name").takeIf { it.isNotBlank() } ?: return null
            val pattern = json.optString("regex").takeIf { it.isNotBlank() } ?: return null

            // An unknown tile name drops the rule instead of leaving it ungated by mistake.
            val tileName = json.optString("tile").trim().ifEmpty { null }
            val tile = TileGate.keyFor(tileName)
            if (tileName != null && tile == null) {
                Log.w(TAG, "Skipping rule for $pkg, unknown tile '$tileName'")
                return null
            }

            return try {
                ApertureRule(
                    packageName = pkg,
                    regex = Regex(pattern),
                    pillText = json.optString("pill_text", ""),
                    iconName = json.optString("icon_name", "").trim().ifEmpty { null },
                    timeoutSeconds = json.optInt("timeout_seconds", 15),
                    tile = tile
                )
            } catch (e: Exception) {
                Log.w(TAG, "Skipping rule for $pkg, bad regex: ${e.message}")
                null
            }
        }
    }
}

data class MatchResult(
    val pillText: String,
    val iconName: String?,
    val timeoutSeconds: Int,
    val tile: String?
)
