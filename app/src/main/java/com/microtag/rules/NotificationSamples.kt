package com.microtag.rules

import com.microtag.listener.OtpExtractor

/**
 * What an app's notifications recently said, for the rule builder to pick a sample from. In memory only: never written
 * to disk, forgotten after [TTL_MS], and a notification that carries a code is never kept.
 */
object NotificationSamples {
    data class Sample(val pkg: String, val title: String, val text: String, val timeMs: Long)

    const val TTL_MS = 30 * 60 * 1000L
    private const val PER_APP = 15
    private const val MAX_APPS = 60

    private val lock = Any()
    private val byApp = LinkedHashMap<String, ArrayDeque<Sample>>()

    fun record(pkg: String, title: String, text: String, now: Long = System.currentTimeMillis()) {
        if (title.isBlank() && text.isBlank()) return
        if (OtpExtractor.isCodeMessage("$title $text")) return
        synchronized(lock) {
            prune(now)
            val q = byApp.getOrPut(pkg) { ArrayDeque() }
            if (q.firstOrNull()?.let { it.title == title && it.text == text } == true) return
            q.addFirst(Sample(pkg, title, text, now))
            while (q.size > PER_APP) q.removeLast()
            if (byApp.size > MAX_APPS) byApp.remove(byApp.keys.first())
        }
    }

    fun recent(pkg: String, now: Long = System.currentTimeMillis()): List<Sample> = synchronized(lock) {
        prune(now); byApp[pkg]?.toList().orEmpty()
    }

    /** Apps that have said something lately, most recent first, with how many samples each has. */
    fun apps(now: Long = System.currentTimeMillis()): List<Pair<String, Int>> = synchronized(lock) {
        prune(now)
        byApp.entries.sortedByDescending { it.value.firstOrNull()?.timeMs ?: 0L }.map { it.key to it.value.size }
    }

    fun clear() = synchronized(lock) { byApp.clear() }

    private fun prune(now: Long) {
        val it = byApp.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            while (e.value.isNotEmpty() && now - e.value.last().timeMs > TTL_MS) e.value.removeLast()
            if (e.value.isEmpty()) it.remove()
        }
    }
}
