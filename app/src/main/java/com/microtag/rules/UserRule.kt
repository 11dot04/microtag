package com.microtag.rules

import com.microtag.listener.PillText
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One rule a person made for an installed app: when its notification matches, show this pill. */
data class UserRule(
    val id: String,
    val pkg: String,
    val appLabel: String,
    val name: String,
    val regex: String,
    /** "$1", "$1d", "Page $1/$2". */
    val pill: String = "$1",
    val field: Field = Field.BOTH,
    val timeoutSeconds: Int = 15,
    val ignoreCase: Boolean = true,
    val iconMode: IconMode = IconMode.APP,
    /** A drawable name from the app, used when [iconMode] is BUILTIN or when the app's own icon cannot be read. */
    val iconName: String = "ic_capsule_bolt",
    val pillMode: PillMode = PillMode.FIT,
    val enabled: Boolean = true,
    val createdAt: Long = 0L,
    val contributed: Boolean = false
) {
    enum class Field { TITLE, TEXT, BOTH }
    /** APP: the app's own monochrome icon. BUILTIN: [iconName]. NONE: the default bolt. */
    enum class IconMode { APP, BUILTIN, NONE }
    /** EXACT: the template as written. FIT: cut to the pill's room. AI: fitted at once, then improved by the model if it is on. */
    enum class PillMode { EXACT, FIT, AI }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("pkg", pkg); put("label", appLabel); put("name", name); put("regex", regex); put("pill", pill)
        put("field", field.name); put("timeout", timeoutSeconds); put("ic", ignoreCase); put("iconMode", iconMode.name)
        put("icon", iconName); put("pillMode", pillMode.name); put("on", enabled); put("t", createdAt); put("shared", contributed)
    }

    companion object {
        fun fromJson(o: JSONObject): UserRule? {
            val id = o.optString("id"); val pkg = o.optString("pkg"); val rx = o.optString("regex")
            if (id.isBlank() || pkg.isBlank() || rx.isBlank()) return null
            fun <T : Enum<T>> en(v: String, all: Array<T>, d: T) = all.firstOrNull { it.name == v } ?: d
            return UserRule(
                id = id, pkg = pkg, appLabel = o.optString("label", pkg), name = o.optString("name", ""), regex = rx,
                pill = o.optString("pill", "$1"), field = en(o.optString("field"), Field.values(), Field.BOTH),
                timeoutSeconds = o.optInt("timeout", 15), ignoreCase = o.optBoolean("ic", true),
                iconMode = en(o.optString("iconMode"), IconMode.values(), IconMode.APP),
                iconName = o.optString("icon", "ic_capsule_bolt"), pillMode = en(o.optString("pillMode"), PillMode.values(), PillMode.FIT),
                enabled = o.optBoolean("on", true), createdAt = o.optLong("t", 0L), contributed = o.optBoolean("shared", false)
            )
        }
    }
}

/** What a matching rule says to show. [pill] is already fitted; [aiCandidate] means the model may improve it. */
data class UserMatch(val rule: UserRule, val pill: String, val fullPill: String, val groups: List<String>, val aiCandidate: Boolean)

/** The rule file: user_rules.json in the app's files, replaced atomically on every change. Pure, so it is tested on the JVM. */
class UserRuleBook(private val dir: File) {
    companion object {
        const val FILE_NAME = "user_rules.json"
        const val MAX_RULES = 200
        const val EXACT_CAP = 24
    }

    private val file = File(dir, FILE_NAME)
    private val lock = Any()
    @Volatile private var rules: List<UserRule> = load()
    /** Compiled once per rule version. */
    private val compiled = HashMap<String, Pair<String, Regex?>>()

    private fun load(): List<UserRule> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(UserRule::fromJson) }
        }.getOrElse {
            // Unreadable: keep the bytes for inspection, never overwrite them with an empty list.
            runCatching { file.renameTo(File(dir, "$FILE_NAME.damaged")) }
            emptyList()
        }
    }

    private fun save(list: List<UserRule>) {
        val tmp = File(dir, "$FILE_NAME.tmp")
        val json = JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }.toString()
        runCatching {
            tmp.writeText(json)
            if (!tmp.renameTo(file)) { file.writeText(json); tmp.delete() }
        }
    }

    fun all(): List<UserRule> = rules
    fun forPackage(pkg: String): List<UserRule> = rules.filter { it.pkg.equals(pkg, ignoreCase = true) }
    fun get(id: String): UserRule? = rules.firstOrNull { it.id == id }

    /** Saves a new or changed rule. Returns a sentence when it cannot be saved. */
    fun upsert(rule: UserRule): String? {
        validate(rule)?.let { return it }
        synchronized(lock) {
            val list = rules.toMutableList()
            val i = list.indexOfFirst { it.id == rule.id }
            if (i >= 0) list[i] = rule else {
                if (list.size >= MAX_RULES) return "That is the most rules there is room for"
                list.add(rule)
            }
            rules = list; compiled.remove(rule.id); save(list)
        }
        return null
    }

    fun delete(id: String) = synchronized(lock) { rules = rules.filter { it.id != id }; compiled.remove(id); save(rules) }
    fun setEnabled(id: String, on: Boolean) = synchronized(lock) {
        rules = rules.map { if (it.id == id) it.copy(enabled = on) else it }; save(rules)
    }
    fun markContributed(id: String) = synchronized(lock) {
        rules = rules.map { if (it.id == id) it.copy(contributed = true) else it }; save(rules)
    }

    fun validate(rule: UserRule): String? {
        if (rule.pkg.isBlank()) return "Pick an app"
        SafeRegex.problem(rule.regex, rule.ignoreCase)?.let { return it }
        val groups = SafeRegex.groupCount(rule.regex)
        if (RuleTemplate.maxRef(rule.pill) > groups) return "The pill uses $${RuleTemplate.maxRef(rule.pill)} but the pattern has only $groups group${if (groups == 1) "" else "s"}"
        if (rule.pill.isBlank()) return "The pill is empty"
        return null
    }

    private fun regexOf(rule: UserRule): Regex? {
        val sig = "${rule.regex}|${rule.ignoreCase}"
        synchronized(compiled) {
            compiled[rule.id]?.let { if (it.first == sig) return it.second }
            val rx = SafeRegex.compile(rule.regex, rule.ignoreCase).getOrNull()
            compiled[rule.id] = sig to rx
            return rx
        }
    }

    /** The first enabled rule for [pkg] that matches, with its pill built and fitted. */
    fun evaluate(pkg: String, title: String, text: String, limit: Int = PillText.LIMIT): UserMatch? {
        for (rule in rules) {
            if (!rule.enabled || !rule.pkg.equals(pkg, ignoreCase = true)) continue
            val rx = regexOf(rule) ?: continue
            val target = when (rule.field) {
                UserRule.Field.TITLE -> title
                UserRule.Field.TEXT -> text
                UserRule.Field.BOTH -> "$title $text".trim()
            }
            val found = SafeRegex.find(rx, target) as? SafeRegex.Outcome.Found ?: continue
            val full = RuleTemplate.expand(rule.pill, found.groups)
            if (full.isBlank()) continue
            val pill = when (rule.pillMode) {
                UserRule.PillMode.EXACT -> full.take(EXACT_CAP)
                else -> if (full.length <= limit) full else PillText.cut(full, limit)
            }
            return UserMatch(rule, pill, full, found.groups, aiCandidate = rule.pillMode == UserRule.PillMode.AI && full.length > limit)
        }
        return null
    }
}
