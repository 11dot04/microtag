package com.microtag.core

import android.content.Context
import java.io.File

/**
 * A small permanent record of every interception, kept apart from the log. The log holds only the last 500 entries of
 * every kind (ignored, skipped and repeated ones included), so promoted ones reach back about two days. This keeps one
 * line per interception (when, which app, which path) and nothing about the content, so Stats can reach back months.
 *
 * "Interception" here is what Stats has always meant by one: a notification key that was promoted and had been quiet
 * for 30 minutes. Clearing the log does not clear this.
 */
object SessionLedger {
    class Row(val timeMs: Long, val pkg: String, val path: String)

    private const val FILE_NAME = "interceptions.tsv"
    private const val GAP_MS = 30 * 60 * 1000L
    private const val MAX_ROWS = 50_000

    private val lock = Any()
    private val rows = ArrayList<Row>()                  // oldest first
    private val lastSeen = HashMap<String, Long>()       // notification key -> last time it was promoted
    private var file: File? = null
    @Volatile private var ready = false

    /**
     * Loads the ledger. The first time ever, it is filled from whatever promoted entries the log still holds, so the
     * days already in the log are not lost.
     */
    fun init(context: Context, seed: List<MicrotagLog.Entry>) {
        if (ready) return
        synchronized(lock) {
            if (ready) return
            val f = File(context.applicationContext.filesDir, FILE_NAME).also { file = it }
            if (f.exists()) {
                runCatching {
                    f.forEachLine { line ->
                        val p = line.split('\\t')
                        if (p.size >= 3) p[0].toLongOrNull()?.let { rows.add(Row(it, p[1], p[2])) }
                    }
                }
            } else {
                // Seed oldest first so the 30 minute rule works the same way as live.
                seed.filter { it.decision == MicrotagLog.Decision.PROMOTED }.sortedBy { it.timeMs }
                    .forEach { addIfNew(it.key, it.pkg, it.path, it.timeMs, persist = false) }
                rewrite()
            }
            rows.lastOrNull()?.let { /* lastSeen is rebuilt from live calls; a restart can split one long session in two at most */ }
            ready = true
        }
    }

    /** Call for every promoted notification, however often it repeats. */
    fun record(key: String, pkg: String, path: String, timeMs: Long) {
        if (!ready) return
        synchronized(lock) { addIfNew(key, pkg, path, timeMs, persist = true) }
    }

    fun snapshot(): List<Row> = synchronized(lock) { rows.toList() }

    private fun addIfNew(key: String, pkg: String, path: String, timeMs: Long, persist: Boolean) {
        val previous = lastSeen[key]
        lastSeen[key] = timeMs
        if (previous != null && timeMs - previous <= GAP_MS) return
        rows.add(Row(timeMs, pkg, path))
        if (persist) {
            runCatching { file?.appendText("$timeMs\\t$pkg\\t$path\\n") }
            if (rows.size > MAX_ROWS + 2_000) { while (rows.size > MAX_ROWS) rows.removeAt(0); rewrite() }
        }
    }

    private fun rewrite() {
        runCatching { file?.writeText(rows.joinToString("") { "${it.timeMs}\\t${it.pkg}\\t${it.path}\\n" }) }
    }
}
