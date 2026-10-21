package com.microtag.core

import java.io.File
import java.io.FileOutputStream

/**
 * The books behind [Interceptions]: pure Kotlin (no Android), so the rules are tested on the JVM.
 *
 * DEFINITION. An interception is one thing TAG took over, counted once:
 *  - A SESSION is one interception however often its notification updates or our chip is refreshed: a VPN session,
 *    a Discord call, a workout, a download or backup, a Bluetooth connection. It starts when the source first
 *    appears and ends when it is removed (or has been silent for [GAP_MS]). The same source coming back after that
 *    is a new interception.
 *  - An EVENT is its own interception each time: an OTP, a new song, a message, a ringer change. A source that keeps
 *    one notification but changes its headline (a player moving to the next song) counts each new headline.
 * The callers decide which kind a source is ([isOpen] then [record]); this class keeps the books and keeps them safe.
 *
 * Safety: every record is appended to disk at once (a leading newline, so a write that was cut short never swallows
 * the next record), a damaged line is skipped instead of discarding the file, and the open sessions survive a restart.
 */
class InterceptionBook(private val env: Env) {

    /** Where the books live. Android supplies files and SharedPreferences; tests supply memory and a temp folder. */
    interface Env {
        val dir: File
        fun getInt(key: String, def: Int): Int
        fun putInt(key: String, value: Int)
        fun getString(key: String): String
        fun putString(key: String, value: String)
        /** The home screen's old counter, kept equal to the total. */
        fun getLegacyCount(): Int
        fun putLegacyCount(value: Int)
    }

    data class Record(val timeMs: Long, val pkg: String, val path: String)

    private class Open(var lastSeen: Long, var closedAt: Long)

    companion object {
        const val FILE_NAME = "interceptions.tsv"
        private const val KEY_BASE = "base"
        private const val KEY_OPEN = "open"
        private const val KEY_MIGRATED = "migrated"
        /** A session with no sign of life for this long is over. */
        const val GAP_MS = 2 * 60 * 60 * 1000L
        /** A removed source that comes straight back (an app reposting its notification) is still the same session. */
        const val GRACE_MS = 60_000L
        private const val PERSIST_EVERY_MS = 60_000L
        var maxRecords = 25_000
        var keepRecords = 20_000
    }

    private val lock = Any()
    private val records = ArrayList<Record>()
    private val open = HashMap<String, Open>()
    private val debounce = HashMap<String, Long>()
    private var base = env.getInt(KEY_BASE, 0)
    private var lastPersist = 0L
    private val file = File(env.dir, FILE_NAME)

    init {
        readFile()
        env.getString(KEY_OPEN).lineSequence().forEach { line ->
            val f = line.split('\t')
            if (f.size == 3) {
                val seen = f[1].toLongOrNull(); val closed = f[2].toLongOrNull()
                if (seen != null && closed != null) open[f[0]] = Open(seen, closed)
            }
        }
    }

    fun migrated(): Boolean = env.getInt(KEY_MIGRATED, 0) == 1

    /**
     * One-time: the old log's sessions come in as history (oldest first) and the old counter carries over, so ALL TIME
     * does not drop. The base is whatever the old counter held beyond the history.
     */
    fun migrate(history: List<Record>) {
        if (migrated()) return
        synchronized(lock) {
            val legacy = env.getLegacyCount()
            val have = records.map { it.timeMs }.toHashSet()
            history.filter { it.timeMs !in have }.forEach { appendLine(it); records.add(it) }
            records.sortBy { it.timeMs }
            base = (legacy - records.size).coerceAtLeast(0)
            env.putInt(KEY_BASE, base)
            env.putInt(KEY_MIGRATED, 1)
            env.putLegacyCount(base + records.size)
        }
    }

    /** Every interception kept, oldest first. Older ones past the cap live on in the count. */
    fun records(): List<Record> = synchronized(lock) { records.toList() }

    fun total(): Int = synchronized(lock) { base + records.size }

    /** Was [key] already counted, and is it still going? */
    fun isOpen(key: String, now: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        val o = open[key] ?: return false
        if (now - o.lastSeen > GAP_MS) return false
        o.closedAt == 0L || now - o.closedAt <= GRACE_MS
    }

    /** The source is still there: keeps its session alive. */
    fun touch(key: String, now: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val o = open[key] ?: return
            if (now - o.lastSeen > GAP_MS) return
            // Back within the grace period: the same session. Back after it: left closed, so the next record is a new one.
            if (o.closedAt == 0L || now - o.closedAt <= GRACE_MS) {
                o.lastSeen = now
                o.closedAt = 0L
            }
            if (now - lastPersist > PERSIST_EVERY_MS) persistOpen(now)
        }
    }

    /** The source went away. */
    fun end(key: String, now: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val o = open[key] ?: return
            o.closedAt = now
            persistOpen(now)
        }
    }

    /** Counts one interception and opens [key]. The caller has already decided it is a new one. Returns the new total. */
    fun record(key: String, pkg: String, path: String, now: Long = System.currentTimeMillis()): Int = synchronized(lock) {
        val r = Record(now, pkg, path)
        appendLine(r)
        records.add(r)
        open[key] = Open(now, 0L)
        if (open.size > 400) open.entries.removeAll { now - it.value.lastSeen > GAP_MS }
        persistOpen(now)
        if (records.size > maxRecords) compact()
        env.putLegacyCount(base + records.size)
        base + records.size
    }

    /** True at most once per [ms] for [tag]: for sources that fire in bursts (the ringer). */
    fun debounced(tag: String, ms: Long, now: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        val last = debounce[tag] ?: 0L
        if (now - last < ms) false else { debounce[tag] = now; true }
    }

    // ------------------------------------------------------------ storage

    private fun line(r: Record) = "${r.timeMs}\t${r.pkg.replace('\t', ' ')}\t${r.path.replace('\t', ' ')}"

    private fun readFile() {
        if (!file.exists()) return
        runCatching {
            file.forEachLine { raw ->
                val f = raw.trim('\n', '\r').split('\t')
                // A damaged or cut-short line is skipped; the rest still counts.
                val t = f.getOrNull(0)?.toLongOrNull()
                if (f.size == 3 && t != null) records.add(Record(t, f[1], f[2]))
            }
        }
        records.sortBy { it.timeMs }
    }

    private fun appendLine(r: Record) {
        runCatching { FileOutputStream(file, true).use { it.write(("\n" + line(r)).toByteArray()) } }
    }

    /** Past the cap: keep the newest [keepRecords], fold the rest into the base so the total never drops. */
    private fun compact() {
        val drop = records.size - keepRecords
        if (drop <= 0) return
        val kept = ArrayList(records.subList(drop, records.size))
        val tmp = File(env.dir, "$FILE_NAME.tmp")
        val ok = runCatching {
            tmp.bufferedWriter().use { w -> kept.forEach { w.write("\n" + line(it)) } }
            tmp.renameTo(file)
        }.getOrDefault(false)
        if (ok) {
            base += drop
            records.clear(); records.addAll(kept)
            env.putInt(KEY_BASE, base)
        }
    }

    private fun persistOpen(now: Long) {
        lastPersist = now
        env.putString(KEY_OPEN, open.entries.joinToString("\n") { "${it.key}\t${it.value.lastSeen}\t${it.value.closedAt}" })
    }
}
