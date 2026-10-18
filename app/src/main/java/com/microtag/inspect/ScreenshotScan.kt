package com.microtag.inspect

import kotlin.math.max
import kotlin.math.min

/** A rectangle in image pixels. */
class IBox(val l: Int, val t: Int, val r: Int, val b: Int) {
    fun contains(x: Float, y: Float, pad: Float = 0f): Boolean = x >= l - pad && x <= r + pad && y >= t - pad && y <= b + pad
    fun union(o: IBox) = IBox(min(l, o.l), min(t, o.t), max(r, o.r), max(b, o.b))
}

class OcrWord(val text: String, val box: IBox)

class OcrLine(val words: List<OcrWord>) {
    val text: String get() = words.joinToString(" ") { it.text }
    val box: IBox get() = words.map { it.box }.reduce { a, b -> a.union(b) }
}

/** Something in the screenshot the detectors recognised: its text, where it is and what kind of thing it is. */
class Finding(val text: String, val box: IBox, val kind: String, val score: Double)

/**
 * Finds the things worth outlining in text read off a screenshot. Pure Kotlin: the OCR itself (ML Kit) lives in the activity.
 *
 * Every run of up to [MAX_SPAN] neighbouring words on a line is offered to the detectors as if it were copied text
 * (the strict, clipboard reading, so ordinary words never light up). Only structured kinds count: a UPI id, an IFSC,
 * an amount, a date, a time with a zone, a link, a measurement, a portion of food, a colour code. Words that merely
 * have a definition or a Wikipedia page are not outlined; they are still tappable one by one.
 */
object ScreenshotScan {
    private val worth = setOf("upi", "ifsc", "money", "date", "timezone", "url", "units", "dimensions", "food", "colour", "math")
    private const val MAX_SPAN = 5
    private const val MIN_SCORE = 0.6
    private const val MAX_LINES = 250
    private const val MAX_CALLS = 4000
    private val domainLike = Regex("""[A-Za-z0-9-]+\.(?:com|in|org|net|io|co|me|app|ly|gov|edu)\b""")

    /** Cheap filter before asking the detectors: a span needs a digit, an @, a # or something link-shaped. */
    internal fun interesting(span: String): Boolean =
        span.any { it.isDigit() } || span.contains('@') || span.contains('#') || span.contains("http", true) ||
            span.contains("www.", true) || domainLike.containsMatchIn(span)

    fun analyze(lines: List<OcrLine>, imageHeight: Int, env: Env): List<Finding> {
        val out = ArrayList<Finding>()
        // The status bar clock and battery are never what anyone wants inspected.
        val statusCut = if (imageHeight > 0) (imageHeight * 0.045).toInt() else 0
        var calls = 0
        for (line in lines.take(MAX_LINES)) {
            val ws = line.words.filter { it.box.t >= statusCut }
            class Hit(val i: Int, val j: Int, val text: String, val score: Double, val kind: String)
            val hits = ArrayList<Hit>()
            for (i in ws.indices) {
                for (j in i until min(ws.size, i + MAX_SPAN)) {
                    val text = ws.subList(i, j + 1).joinToString(" ") { it.text }
                    if (text.length < 2 || !interesting(text)) continue
                    if (++calls > MAX_CALLS) break
                    val best = try {
                        Detectors.candidates(text, Source.CLIPBOARD, env).firstOrNull { it.name in worth && it.score >= MIN_SCORE }
                    } catch (_: Exception) {
                        null
                    } ?: continue
                    hits.add(Hit(i, j, text, best.score, best.name))
                }
            }
            // Longest run first (so "28 Feb 2027" beats "28 Feb"), then the surer reading; never overlapping.
            val used = BooleanArray(ws.size)
            for (h in hits.sortedWith(compareByDescending<Hit> { it.j - it.i }.thenByDescending { it.score })) {
                if ((h.i..h.j).any { used[it] }) continue
                for (k in h.i..h.j) used[k] = true
                out.add(Finding(h.text, ws.subList(h.i, h.j + 1).map { it.box }.reduce { a, b -> a.union(b) }, h.kind, h.score))
            }
        }
        return out
    }

    /** The finding under a tap, or null. Taps near an edge still count by [pad] pixels. */
    fun findingAt(findings: List<Finding>, x: Float, y: Float, pad: Float): Finding? =
        findings.filter { it.box.contains(x, y, pad) }.minByOrNull { (it.box.r - it.box.l).toLong() * (it.box.b - it.box.t) }

    /** The word under a tap, or null. */
    fun wordAt(lines: List<OcrLine>, x: Float, y: Float, pad: Float): OcrWord? =
        lines.flatMap { it.words }.filter { it.box.contains(x, y, pad) }.minByOrNull { (it.box.r - it.box.l).toLong() * (it.box.b - it.box.t) }

    /** The line under a tap, or null. */
    fun lineAt(lines: List<OcrLine>, x: Float, y: Float, pad: Float): OcrLine? =
        lines.filter { it.box.contains(x, y, pad) }.minByOrNull { (it.box.r - it.box.l).toLong() * (it.box.b - it.box.t) }
}
