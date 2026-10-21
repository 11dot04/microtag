package com.microtag.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Persistent ring buffer of every notification the listener evaluated and what it decided.
 * Identical consecutive decisions for the same source are collapsed into one entry with a repeat count,
 * so the 3 s poll doesn't flood the log.
 *
 * This is the debug view for the Logs screen. The interception count and Stats live in [Interceptions]. Saves are
 * atomic (write a temp file, rename it over the old one), a file that cannot be read is set aside instead of being
 * overwritten with an empty log, and [flush] writes at once so an app update does not lose the last seconds.
 */
object MicrotagLog {

    enum class Decision(val label: String) {
        PROMOTED("✅ PROMOTED"),
        UNCHANGED("🔁 UNCHANGED"),
        SKIPPED("⏭ SKIPPED"),
        IGNORED("❌ IGNORED"),
        CLEARED("🧹 CLEARED"),
        INFO("ℹ️ INFO")
    }

    data class Entry(
        val key: String,
        var timeMs: Long,
        val pkg: String,
        val path: String,
        val decision: Decision,
        val reason: String,
        val pill: String?,
        val icon: String?,
        val title: String,
        val text: String,
        val meta: String,
        var repeats: Int = 1
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("key", key); put("t", timeMs); put("pkg", pkg); put("path", path)
            put("decision", decision.name); put("reason", reason)
            put("pill", pill ?: JSONObject.NULL); put("icon", icon ?: JSONObject.NULL)
            put("title", title); put("text", text); put("meta", meta); put("repeats", repeats)
        }

        fun format(): String {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timeMs))
            val rep = if (repeats > 1) "  ×$repeats" else ""
            return buildString {
                append("$time  ${decision.label}$rep\n")
                append("$pkg  [$path]\n")
                if (pill != null) append("pill: $pill   icon: ${icon ?: "-"}\n")
                append("why: $reason\n")
                append("title: $title\n")
                append("text: $text\n")
                append("meta: $meta")
            }
        }

        fun sameDecisionAs(o: Entry) =
            decision == o.decision && reason == o.reason && pill == o.pill &&
                    title == o.title && text == o.text && path == o.path

        companion object {
            fun fromJson(o: JSONObject) = Entry(
                key = o.optString("key"),
                timeMs = o.optLong("t"),
                pkg = o.optString("pkg"),
                path = o.optString("path"),
                decision = runCatching { Decision.valueOf(o.optString("decision")) }
                    .getOrDefault(Decision.IGNORED),
                reason = o.optString("reason"),
                pill = if (o.isNull("pill")) null else o.optString("pill"),
                icon = if (o.isNull("icon")) null else o.optString("icon"),
                title = o.optString("title"),
                text = o.optString("text"),
                meta = o.optString("meta"),
                repeats = o.optInt("repeats", 1)
            )
        }
    }

    private const val MAX_ENTRIES = 500
    private const val PREFS = "microtag_log_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val FILE_NAME = "microtag_log.json"
    private const val SAVE_DEBOUNCE_MS = 2_000L
    private val digitRun = Regex("""\d{4,}""")

    private val lock = Any()
    private val entries = ArrayDeque<Entry>() // newest first
    private val handler = Handler(Looper.getMainLooper())
    private var file: File? = null
    private var appContext: Context? = null
    @Volatile private var initialized = false
    @Volatile var enabled: Boolean = true
        private set

    /** Called whenever the log changes (may be on any thread). */
    @Volatile var onChanged: (() -> Unit)? = null

    private val saveTask = Runnable { saveNow() }

    fun init(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            val ctx = context.applicationContext
            appContext = ctx
            file = File(ctx.filesDir, FILE_NAME)
            enabled = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, true)
            val readable = runCatching {
                val raw = file?.takeIf { it.exists() }?.readText().orEmpty()
                if (raw.isNotBlank()) {
                    val arr = JSONArray(raw)
                    for (i in 0 until arr.length()) {
                        entries.addLast(Entry.fromJson(arr.getJSONObject(i)))
                    }
                }
            }.isSuccess
            // Damaged: keep the bytes for inspection and start fresh, rather than silently overwriting them.
            if (!readable) {
                entries.clear()
                file?.let { runCatching { it.renameTo(File(it.parentFile, "$FILE_NAME.damaged")) } }
            }
            initialized = true
        }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
    }

    fun add(
        key: String,
        pkg: String,
        path: String,
        decision: Decision,
        reason: String,
        pill: String? = null,
        icon: String? = null,
        title: String = "",
        text: String = "",
        meta: String = ""
    ) {
        if (!enabled || !initialized) return
        val entry = Entry(
            key = key,
            timeMs = System.currentTimeMillis(),
            pkg = pkg,
            path = path,
            decision = decision,
            reason = reason,
            pill = pill?.let(::sanitize),
            icon = icon,
            title = sanitize(title),
            text = sanitize(text),
            meta = meta
        )
        synchronized(lock) {
            val previous = entries.firstOrNull { it.key == key }
            if (previous != null && previous.sameDecisionAs(entry)) {
                previous.timeMs = entry.timeMs
                previous.repeats += 1
                entries.remove(previous)
                entries.addFirst(previous)
            } else {
                entries.addFirst(entry)
                while (entries.size > MAX_ENTRIES) entries.removeLast()
            }
        }
        scheduleSave()
        onChanged?.invoke()
    }

    fun snapshot(): List<Entry> = synchronized(lock) { entries.toList() }

    fun clear() {
        synchronized(lock) { entries.clear() }
        scheduleSave()
        onChanged?.invoke()
    }

    fun asText(list: List<Entry>): String =
        list.joinToString("\n\n") { it.format() }

    // Keep notification content short and mask long digit runs (OTP codes, order numbers).
    private fun sanitize(value: String): String =
        value.replace(digitRun, "••••").replace('\n', ' ').take(140)

    private fun scheduleSave() {
        handler.removeCallbacks(saveTask)
        handler.postDelayed(saveTask, SAVE_DEBOUNCE_MS)
    }

    /** Writes now, on the calling thread. Called when the service is going away or being rebound. */
    fun flush() {
        handler.removeCallbacks(saveTask)
        saveNow()
    }

    private fun saveNow() {
        val target = file ?: return
        val json = synchronized(lock) {
            JSONArray().also { arr -> entries.forEach { arr.put(it.toJson()) } }.toString()
        }
        // Temp file, then rename: a kill in the middle leaves the old file whole.
        val tmp = File(target.parentFile, "$FILE_NAME.tmp")
        runCatching {
            tmp.writeText(json)
            if (!tmp.renameTo(target)) { target.writeText(json); tmp.delete() }
        }
    }
}
