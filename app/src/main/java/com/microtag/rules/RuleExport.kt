package com.microtag.rules

import org.json.JSONObject
import java.net.URLEncoder

/**
 * Offering a rule back to the project. Nothing leaves the phone unless the person taps share, and the notification
 * sample is optional and always redacted. The output is an entry in the same shape as custom_rules.json, so a
 * maintainer can paste it straight in.
 */
object RuleExport {
    /** Where contributions go. Set to the project's repository ("owner/name"). */
    var repo = "microtag/microtag"

    /** One entry of custom_rules.json. The tile is left for the maintainer to choose. */
    fun builtinEntry(rule: UserRule): String {
        val o = JSONObject()
        o.put("package_name", rule.pkg)
        o.put("regex", (if (rule.ignoreCase && !rule.regex.startsWith("(?i)")) "(?i)" else "") + rule.regex)
        o.put("pill_text", rule.pill)
        o.put("icon_name", if (rule.iconMode == UserRule.IconMode.BUILTIN) rule.iconName else "ic_capsule_bolt")
        o.put("timeout_seconds", rule.timeoutSeconds)
        o.put("tile", "custom")
        return o.toString()
    }

    /** The text of a contribution: what the rule is for, the entry, and (if asked) one redacted sample. */
    fun bundle(rule: UserRule, sample: String? = null): String = buildString {
        append("App: ${rule.appLabel} (${rule.pkg})\n")
        if (rule.name.isNotBlank()) append("Rule: ${rule.name}\n")
        append("\ncustom_rules.json entry:\n```json\n${builtinEntry(rule)}\n```\n")
        if (!sample.isNullOrBlank()) append("\nRedacted sample notification:\n```\n${Redactor.redact(sample)}\n```\n")
    }

    /** A pre-filled new-issue address. Kept short enough for a browser. */
    fun issueUrl(rule: UserRule, sample: String? = null): String {
        val title = "Rule: ${rule.appLabel}" + if (rule.name.isNotBlank()) " – ${rule.name}" else ""
        var body = bundle(rule, sample)
        if (body.length > 5000) body = bundle(rule, null)
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        return "https://github.com/$repo/issues/new?title=${enc(title)}&body=${enc(body)}&labels=rule"
    }
}
