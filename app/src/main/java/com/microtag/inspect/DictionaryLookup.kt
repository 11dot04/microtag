package com.microtag.inspect

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

data class Sense(val pos: String, val definition: String, val example: String = "")

data class DictResult(
    val word: String,
    val phonetic: String = "",
    val senses: List<Sense> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
    /** True when no source could be reached at all. */
    val offline: Boolean = false
)

/**
 * Asks three free dictionaries at the same time and uses whichever answers first.
 * After the first good answer it waits a short moment for the others so it can fill in
 * a missing pronunciation or a second part of speech, then stops.
 */
class DictionaryLookup : DictionarySource {

    internal class Partial(
        val source: String,
        val reached: Boolean,
        val phonetic: String = "",
        val senses: List<Sense> = emptyList(),
        val suggestions: List<String> = emptyList()
    )

    override fun lookup(query: String): DictResult {
        val word = query.trim().lowercase(Locale.ROOT)
        val enc = Http.encode(word)
        val ecs = ExecutorCompletionService<Partial>(pool)
        val futures: List<Future<Partial>> = listOf(
            ecs.submit(Callable { fetchDictApi(enc) }),
            ecs.submit(Callable { fetchWiktionary(enc) }),
            ecs.submit(Callable { fetchDatamuse(enc, word) })
        )

        val results = mutableListOf<Partial>()
        var deadline = System.currentTimeMillis() + OVERALL_MS
        var finished = 0
        while (finished < futures.size) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) break
            val f = ecs.poll(remaining, TimeUnit.MILLISECONDS) ?: break
            finished++
            val p = try {
                f.get()
            } catch (_: Exception) {
                null
            } ?: continue
            val hadAnswer = results.any { it.senses.isNotEmpty() }
            results.add(p)
            if (!hadAnswer && p.senses.isNotEmpty()) {
                deadline = minOf(deadline, System.currentTimeMillis() + MERGE_MS)
            }
        }
        futures.forEach { it.cancel(true) }
        return merge(word, results)
    }

    companion object {
        private const val OVERALL_MS = 3500L
        private const val MERGE_MS = 350L
        private val pool = Executors.newCachedThreadPool { r ->
            Thread(r, "inspect-dict").apply { isDaemon = true }
        }

        private val priority = listOf("dictionaryapi.dev", "Wiktionary", "Datamuse")

        internal fun merge(word: String, results: List<Partial>): DictResult {
            val ordered = results.sortedBy { priority.indexOf(it.source).let { i -> if (i < 0) 99 else i } }
            val withSenses = ordered.filter { it.senses.isNotEmpty() }
            if (withSenses.isEmpty()) {
                val reachedAny = results.any { it.reached }
                val suggestions = ordered.flatMap { it.suggestions }.distinct().filter { it != word }.take(4)
                return DictResult(word, suggestions = suggestions, offline = !reachedAny)
            }
            val merged = mutableListOf<Sense>()
            val seen = HashSet<String>()
            fun add(s: Sense) {
                if (merged.size >= 4) return
                if (seen.add(s.definition.lowercase(Locale.ROOT).take(40))) merged.add(s)
            }
            // The best source supplies most of the card; the others only add a part of speech it lacks.
            withSenses.first().senses.take(3).forEach { add(it) }
            for (p in withSenses.drop(1)) {
                for (s in p.senses) {
                    if (merged.none { it.pos.equals(s.pos, ignoreCase = true) }) add(s)
                }
            }
            val phonetic = ordered.map { it.phonetic }.firstOrNull { it.isNotBlank() } ?: ""
            return DictResult(word, phonetic, merged, sources = withSenses.map { it.source })
        }

        private fun clean(html: String): String = html
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ").trim()

        private fun JSONObject.str(key: String): String {
            if (!has(key) || isNull(key)) return ""
            return optString(key, "")
        }

        internal fun parseDictApi(body: String): Partial {
            return try {
                val arr = JSONArray(body)
                var phonetic = ""
                val senses = mutableListOf<Sense>()
                for (i in 0 until arr.length()) {
                    val entry = arr.optJSONObject(i) ?: continue
                    if (phonetic.isBlank()) {
                        phonetic = entry.str("phonetic")
                        if (phonetic.isBlank()) {
                            val ph = entry.optJSONArray("phonetics")
                            if (ph != null) for (j in 0 until ph.length()) {
                                val t = ph.optJSONObject(j)?.str("text") ?: ""
                                if (t.isNotBlank()) { phonetic = t; break }
                            }
                        }
                    }
                    val meanings = entry.optJSONArray("meanings") ?: continue
                    for (m in 0 until meanings.length()) {
                        val meaning = meanings.optJSONObject(m) ?: continue
                        val pos = meaning.str("partOfSpeech")
                        val defs = meaning.optJSONArray("definitions") ?: continue
                        for (d in 0 until minOf(defs.length(), 2)) {
                            val def = defs.optJSONObject(d) ?: continue
                            val text = def.str("definition")
                            if (text.isNotBlank()) senses.add(Sense(pos, text, def.str("example")))
                        }
                    }
                }
                Partial("dictionaryapi.dev", true, phonetic, senses)
            } catch (_: Exception) {
                Partial("dictionaryapi.dev", true)
            }
        }

        internal fun parseWiktionary(body: String): Partial {
            return try {
                val o = JSONObject(body)
                val en = o.optJSONArray("en") ?: return Partial("Wiktionary", true)
                val senses = mutableListOf<Sense>()
                for (i in 0 until en.length()) {
                    val block = en.optJSONObject(i) ?: continue
                    val pos = block.str("partOfSpeech")
                    val defs = block.optJSONArray("definitions") ?: continue
                    for (d in 0 until minOf(defs.length(), 2)) {
                        val def = defs.optJSONObject(d) ?: continue
                        val text = clean(def.str("definition"))
                        if (text.isBlank()) continue
                        val ex = def.optJSONArray("examples")?.let { clean(it.optString(0, "")) } ?: ""
                        senses.add(Sense(pos, text, ex))
                    }
                }
                Partial("Wiktionary", true, senses = senses)
            } catch (_: Exception) {
                Partial("Wiktionary", true)
            }
        }

        private val datamusePos = mapOf("n" to "noun", "v" to "verb", "adj" to "adjective", "adv" to "adverb", "u" to "")

        internal fun parseDatamuse(body: String, word: String): Partial {
            return try {
                val arr = JSONArray(body)
                val senses = mutableListOf<Sense>()
                val suggestions = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val w = o.str("word")
                    if (w.equals(word, ignoreCase = true)) {
                        val defs = o.optJSONArray("defs") ?: continue
                        for (d in 0 until minOf(defs.length(), 3)) {
                            val raw = defs.optString(d, "")
                            val tab = raw.indexOf('\t')
                            if (tab < 0) continue
                            senses.add(Sense(datamusePos[raw.substring(0, tab)] ?: "", raw.substring(tab + 1).trim()))
                        }
                    } else if (w.isNotBlank()) {
                        suggestions.add(w)
                    }
                }
                Partial("Datamuse", true, senses = senses, suggestions = suggestions)
            } catch (_: Exception) {
                Partial("Datamuse", true)
            }
        }

        private fun fetchDictApi(enc: String): Partial {
            val r = Http.get("https://api.dictionaryapi.dev/api/v2/entries/en/$enc", 3000)
            return when {
                r.ok -> parseDictApi(r.body)
                r.networkError -> Partial("dictionaryapi.dev", false)
                else -> Partial("dictionaryapi.dev", true)
            }
        }

        private fun fetchWiktionary(enc: String): Partial {
            val r = Http.get("https://en.wiktionary.org/api/rest_v1/page/definition/$enc", 3000)
            return when {
                r.ok -> parseWiktionary(r.body)
                r.networkError -> Partial("Wiktionary", false)
                else -> Partial("Wiktionary", true)
            }
        }

        private fun fetchDatamuse(enc: String, word: String): Partial {
            val r = Http.get("https://api.datamuse.com/words?sp=$enc&md=d&max=6", 3000)
            return when {
                r.ok -> parseDatamuse(r.body, word)
                r.networkError -> Partial("Datamuse", false)
                else -> Partial("Datamuse", true)
            }
        }
    }
}

// ==========================================================
// DEFINE: a single selected word
// ==========================================================

internal object DefineDetector {
    private val wordRe = Regex("""^[A-Za-z][A-Za-z'’-]{1,29}$""")

    fun candidates(text: String, source: Source, env: Env): List<Candidate> {
        if (source != Source.SELECTION) return emptyList()
        val t = text.trim()
        if (!wordRe.matches(t)) return emptyList()
        val dict = env.dictionary ?: return emptyList()
        val word = t.replace('’', '\'')
        return listOf(Candidate("define", 0.4, slow = true) { build(dict.lookup(word)) })
    }

    private fun short(s: String, max: Int): String =
        if (s.length <= max) s else s.take(max - 1).trimEnd() + "…"

    fun build(r: DictResult): InspectResult? {
        if (r.senses.isEmpty()) {
            if (r.offline) {
                return InspectResult(
                    domain = "DEFINE", pill = "Offline", title = r.word, cardTitle = "Define",
                    cardText = "No connection, so I couldn't look this up.",
                    iconName = "ic_inspect_define", status = Status.ERROR
                )
            }
            if (r.suggestions.isNotEmpty()) {
                val list = r.suggestions.take(3).joinToString(", ")
                return InspectResult(
                    domain = "DEFINE", pill = "Did you mean ${r.suggestions.first()}?", title = r.word,
                    cardTitle = "No exact match", cardText = "Did you mean $list?",
                    rows = listOf("Did you mean" to list), copyText = r.suggestions.first(),
                    iconName = "ic_inspect_define", status = Status.WARN
                )
            }
            return null
        }
        val first = r.senses.first()
        val rows = mutableListOf<Pair<String, String>>()
        r.senses.take(3).forEach { rows.add(it.pos.ifBlank { "Meaning" } to it.definition) }
        r.senses.firstOrNull { it.example.isNotBlank() }?.let { rows.add("Example" to "“${it.example}”") }
        if (r.sources.isNotEmpty()) rows.add("Source" to r.sources.joinToString(", "))
        return InspectResult(
            domain = "DEFINE",
            pill = short(first.definition, 26),
            title = r.word,
            subtitle = listOf(r.phonetic, first.pos).filter { it.isNotBlank() }.joinToString(" · "),
            cardTitle = r.word,
            cardText = first.definition,
            rows = rows,
            copyText = "${r.word}: ${first.definition}",
            iconName = "ic_inspect_define",
            openUrl = "https://en.wiktionary.org/wiki/${Http.encode(r.word)}",
            timeoutSeconds = 25
        )
    }
}
