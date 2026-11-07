package com.microtag.ai

import com.microtag.listener.PillText
import com.microtag.rules.RuleTemplate
import com.microtag.rules.SafeRegex

/**
 * Writes a rule from examples. The model's answer is never trusted: it is compiled, run against the real samples,
 * and checked for a usable pill before it is offered. If it fails, the failure is sent back once for a second try.
 */
class AiRuleHelper(private val completer: () -> Completer?) {

    class Sample(val title: String, val text: String) { val target: String get() = "$title $text".trim() }

    class Request(
        val appLabel: String,
        val pkg: String,
        /** Notifications the rule should match. The real text: it is only ever sent redacted. */
        val positive: List<Sample>,
        /** Notifications it must not match. */
        val negative: List<Sample> = emptyList(),
        /** In the person's words: "the amount", "minutes left", "the sender's name". */
        val wanted: String,
        val limit: Int = PillText.LIMIT
    )

    sealed class Answer {
        class Ok(val regex: String, val pill: String, val explain: String) : Answer()
        class Failed(val message: String) : Answer()
    }

    companion object {
        const val SYSTEM = "You write a Java/Kotlin regular expression that turns a phone notification into a tiny status-bar chip. " +
            "Reply with ONLY a JSON object: {\"regex\": \"...\", \"pill\": \"...\", \"explain\": \"...\"}. " +
            "The regex is matched against the title and the text joined by one space. It is case-insensitive. " +
            "Use capture groups for the parts to show. The pill is a template that uses \$1, \$2 for the groups, for example \"\$1d\" or \"Page \$1/\$2\". " +
            "Keep the regex simple and specific. Never use lookbehind of unbounded length or nested quantifiers. " +
            "The pill must be short: the limit is given in the request."

        fun prompt(r: Request, feedback: String?): String = buildString {
            append("App: ${r.appLabel} (${r.pkg})\nShow in the chip: ${r.wanted}\nPill limit: ${r.limit} characters.\n\n")
            append("Notifications that MUST match:\n")
            r.positive.forEachIndexed { i, s -> append("${i + 1}. title: ${com.microtag.rules.Redactor.forModel(s.title)} | text: ${com.microtag.rules.Redactor.forModel(s.text)}\n") }
            if (r.negative.isNotEmpty()) {
                append("\nNotifications that must NOT match:\n")
                r.negative.forEachIndexed { i, s -> append("${i + 1}. title: ${com.microtag.rules.Redactor.forModel(s.title)} | text: ${com.microtag.rules.Redactor.forModel(s.text)}\n") }
            }
            if (feedback != null) append("\nYour previous answer failed: $feedback\nFix it and answer again.\n")
        }

        /** Why an answer is not good enough, or null if it passes against the real samples. */
        fun check(r: Request, regex: String, pill: String): String? {
            SafeRegex.problem(regex)?.let { return it }
            val groups = SafeRegex.groupCount(regex)
            if (pill.isBlank()) return "the pill is empty"
            if (RuleTemplate.maxRef(pill) > groups) return "the pill uses a group number the regex does not have"
            val rx = SafeRegex.compile(regex).getOrNull() ?: return "the regex does not compile"
            for (s in r.positive) {
                val f = SafeRegex.find(rx, s.target) as? SafeRegex.Outcome.Found ?: return "it does not match: ${s.target.take(80)}"
                val out = RuleTemplate.expand(pill, f.groups)
                if (out.isBlank()) return "it gives an empty pill for: ${s.target.take(80)}"
                if (out.length > r.limit) return "the pill '$out' is longer than ${r.limit} characters"
            }
            for (s in r.negative) {
                if (SafeRegex.find(rx, s.target) is SafeRegex.Outcome.Found) return "it wrongly matches: ${s.target.take(80)}"
            }
            return null
        }
    }

    fun suggest(r: Request): Answer {
        val c = completer() ?: return Answer.Failed("The AI helper is off")
        if (r.positive.isEmpty()) return Answer.Failed("Pick at least one notification to match")
        var feedback: String? = null
        var last = "The model gave no usable answer"
        repeat(2) {
            val reply = c.complete(SYSTEM, prompt(r, feedback), 400)
            val text = reply.text ?: return Answer.Failed(reply.error ?: last)
            val json = AiProtocol.extractJson(text)
            val regex = json?.optString("regex").orEmpty()
            val pill = json?.optString("pill").orEmpty()
            if (json == null || regex.isBlank()) { feedback = "it was not a JSON object with a regex"; last = feedback!!; return@repeat }
            val problem = check(r, regex, pill)
            if (problem == null) return Answer.Ok(regex, pill, json.optString("explain").take(200))
            feedback = problem; last = "Tried twice; the last problem was: $problem"
        }
        return Answer.Failed(last)
    }
}
