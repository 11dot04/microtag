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
            // dictionaryapi.dev has the best definitions and the real IPA, so once it is in there is nothing left to wait for.
            if (p.source == "dictionaryapi.dev" && p.senses.isNotEmpty()) break
        }
        futures.forEach { it.cancel(true) }
        return merge(word, results)
    }

    companion object {
        private const val OVERALL_MS = 3500L
        private const val MERGE_MS = 1500L
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
            val phonetic = wrapIpa(ordered.map { it.phonetic }.firstOrNull { it.isNotBlank() } ?: "")
            return DictResult(word, phonetic, merged, sources = withSenses.map { it.source })
        }

        /** "dɛkeɪd" -> "/dɛkeɪd/"; leaves anything already in slashes or brackets alone. */
        internal fun wrapIpa(p: String): String {
            val t = p.trim()
            if (t.isEmpty()) return ""
            return if (t.startsWith("/") || t.startsWith("[")) t else "/$t/"
        }

        /** Datamuse/Wiktionary text has stray spaces before punctuation ("ten years , particularly"). */
        internal fun tidy(s: String): String = s
            .replace(Regex("\\s+([,.;:!?)])"), "$1")
            .replace(Regex("([(])\\s+"), "$1")
            .replace(Regex("\\s+"), " ")
            .trim()

        /** A definition that only introduces a list ("particularly:") is not worth showing. */
        private fun usable(def: String): Boolean = def.length >= 6 && !def.endsWith(":")

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
                            val text = tidy(def.str("definition"))
                            if (usable(text)) senses.add(Sense(pos, text, tidy(def.str("example"))))
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
                        val text = tidy(clean(def.str("definition")))
                        if (!usable(text)) continue
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
                var phonetic = ""
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val w = o.str("word")
                    if (w.equals(word, ignoreCase = true)) {
                        // "pron:D EH0 K EY1 D" is ARPAbet. It is only a rough guide (stress is unreliable), so it is
                        // converted without stress marks and used only when no real IPA turns up.
                        val tags = o.optJSONArray("tags")
                        if (tags != null) for (t in 0 until tags.length()) {
                            val tag = tags.optString(t, "")
                            if (tag.startsWith("pron:")) { phonetic = Arpabet.toIpa(tag.removePrefix("pron:")); break }
                        }
                        val defs = o.optJSONArray("defs") ?: continue
                        for (d in 0 until minOf(defs.length(), 3)) {
                            val raw = defs.optString(d, "")
                            val tab = raw.indexOf('\t')
                            if (tab < 0) continue
                            val text = tidy(raw.substring(tab + 1))
                            if (usable(text)) senses.add(Sense(datamusePos[raw.substring(0, tab)] ?: "", text))
                        }
                    } else if (w.isNotBlank()) {
                        suggestions.add(w)
                    }
                }
                Partial("Datamuse", true, phonetic = phonetic, senses = senses, suggestions = suggestions)
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
            val r = Http.get("https://api.datamuse.com/words?sp=$enc&md=dr&max=6", 3000)
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

    private fun firstSeen(senses: List<Sense>, pos: String): Int = senses.indexOfFirst { it.pos.equals(pos, ignoreCase = true) }

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
        var lastPos = "\u0000"
        // Senses are grouped by part of speech (named once), and each example follows its own sense.
        val ordered = r.senses.withIndex().sortedWith(compareBy({ firstSeen(r.senses, it.value.pos) }, { it.index })).map { it.value }
        ordered.take(12).forEach {
            val label = if (it.pos.equals(lastPos, ignoreCase = true)) "" else it.pos.ifBlank { "Meaning" }
            lastPos = it.pos
            rows.add(label to short(it.definition, 220))
            if (it.example.isNotBlank()) rows.add("Example" to "\u201c${short(it.example, 160)}\u201d")
        }
        return InspectResult(
            domain = "DEFINE",
            pill = pillFor((runCatching { PillStyle.valueOf(PillConfig.DEFINE) }.getOrDefault(PillStyle.GLOSS)), r, first.definition),
            title = r.word,
            subtitle = r.phonetic,
            cardTitle = r.word,
            cardText = first.definition,
            rows = rows,
            copyText = "${r.word}: ${first.definition}",
            caption = r.sources.joinToString(" · "),
            iconName = "ic_inspect_define",
            openUrl = "https://en.wiktionary.org/wiki/${Http.encode(r.word)}",
            timeoutSeconds = 25
        )
    }
}

/** What the collapsed pill shows for a definition. GLOSS = a one-to-three word head of the meaning. */
internal enum class PillStyle { GLOSS, IPA, WORD }

internal const val DEFINE_PILL_STYLE_DEFAULT = "GLOSS"

private val danglingWords = setOf("of", "a", "an", "the", "to", "in", "for", "and", "or", "with", "by", "on", "as", "at", "that", "is")

/**
 * The head of a definition, kept very short for the pill: qualifiers like "(uncountable, surveying)" and a
 * leading "A/The/To" are dropped, then words are added while they fit in [max] characters, never ending on
 * "of", "a", "the"... "a period of ten years" gives "period"; "to resolve or settle" gives "resolve".
 * It is cut from the real definition, so it is short but never a wrong guess.
 */
internal fun gloss(definition: String, fallback: String, max: Int = 12): String {
    var g = definition.trim()
    g = g.replace(Regex("""^(?:\s*[(\[][^)\]]*[)\]])+\s*"""), "")
    g = g.replace(Regex("""(?i)^(?:a|an|the|to)\s+"""), "")
    val cut = Regex("""[;,:.]|\s+(?:that|which|who|especially|particularly|such as|used)\b""").find(g)
    if (cut != null && cut.range.first >= 3) g = g.substring(0, cut.range.first)
    val words = g.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return fallback
    val kept = ArrayList<String>()
    for (w in words) {
        val next = (kept + w).joinToString(" ")
        if (kept.isNotEmpty() && next.length > max) break
        kept.add(w)
    }
    while (kept.size > 1 && kept.last().lowercase() in danglingWords) kept.removeAt(kept.size - 1)
    val out = kept.joinToString(" ").trim('-', '\u2013', '\u2014', ' ')
    return if (out.length < 3) fallback else out
}

/** Chooses the pill text for a result. IPA falls back to the gloss, the gloss falls back to the word. */
internal fun pillFor(style: PillStyle, r: DictResult, firstDefinition: String): String = when (style) {
    PillStyle.WORD -> r.word
    PillStyle.IPA -> r.phonetic.ifBlank { gloss(firstDefinition, r.word) }
    PillStyle.GLOSS -> gloss(firstDefinition, r.word)
}

/** ARPAbet (CMU style, as Datamuse returns it) to a rough IPA string. No stress marks: they are unreliable. */
internal object Arpabet {
    private val map = mapOf(
        "AA" to "ɑ", "AE" to "æ", "AH" to "ʌ", "AO" to "ɔ", "AW" to "aʊ", "AY" to "aɪ", "B" to "b", "CH" to "tʃ",
        "D" to "d", "DH" to "ð", "EH" to "ɛ", "ER" to "ɝ", "EY" to "eɪ", "F" to "f", "G" to "ɡ", "HH" to "h",
        "IH" to "ɪ", "IY" to "i", "JH" to "dʒ", "K" to "k", "L" to "l", "M" to "m", "N" to "n", "NG" to "ŋ",
        "OW" to "oʊ", "OY" to "ɔɪ", "P" to "p", "R" to "ɹ", "S" to "s", "SH" to "ʃ", "T" to "t", "TH" to "θ",
        "UH" to "ʊ", "UW" to "u", "V" to "v", "W" to "w", "Y" to "j", "Z" to "z", "ZH" to "ʒ"
    )

    fun toIpa(arpa: String): String {
        val sb = StringBuilder()
        for (tok in arpa.trim().split(Regex("\\s+"))) {
            if (tok.isEmpty()) continue
            val stress = tok.lastOrNull()?.takeIf { it.isDigit() }
            val base = tok.trimEnd { it.isDigit() }
            val ipa = when {
                base == "AH" && stress == '0' -> "ə"
                base == "ER" && stress == '0' -> "ɚ"
                else -> map[base] ?: return ""
            }
            sb.append(ipa)
        }
        return sb.toString()
    }
}
