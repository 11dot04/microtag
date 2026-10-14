package com.microtag.inspect

/** Collects every detector's guesses for a piece of text, best first. */
object Detectors {

    fun normalize(raw: String): String = raw.replace(' ', ' ').replace('​', ' ').trim()

    fun candidates(text: String, source: Source, env: Env): List<Candidate> {
        val t = normalize(text)
        if (t.isEmpty() || t.length > 5000) return emptyList()
        val all = ArrayList<Candidate>()
        // Order matters only for ties: earlier wins (units before money, for example).
        all += ColourDetector.candidates(t, source)
        all += DimensionDetector.candidates(t, source)
        all += UnitDetector.candidates(t, source)
        all += CurrencyDetector.candidates(t, source, env)
        all += MathDetector.candidates(t, source, env)
        all += TimeZoneDetector.candidates(t, env)
        all += DateDetector.candidates(t, env)
        all += UrlInspector.candidates(t, source)
        all += UpiDetector.candidates(t, source)
        all += IfscDetector.candidates(t, source)
        all += PinCodeDetector.candidates(t, source)
        all += NumberDetector.candidates(t, source, env)
        all += DefineDetector.candidates(t, source, env)
        all += EntityDetector.candidates(t, source)
        all += ReadingDetector.candidates(t, source)
        return all.sortedByDescending { it.score }
    }

    /** The first candidate that produces a result, or null. Slow candidates may hit the network. */
    fun resolve(text: String, source: Source, env: Env): InspectResult? {
        for (c in candidates(text, source, env)) {
            val r = try {
                c.resolve()
            } catch (_: Exception) {
                null
            }
            if (r != null) return r
        }
        return null
    }
}
