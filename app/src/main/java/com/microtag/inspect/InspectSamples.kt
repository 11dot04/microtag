package com.microtag.inspect

import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/**
 * Example results for the Inspect screen. Every one is produced by the real detector (or the real builder
 * for the ones that need a lookup), so the pill preview and the card are always the same result.
 * Nothing here needs a network except [wiki], which tries one live lookup and falls back to a built-in summary.
 */
object InspectSamples {

    private val rates = object : RateSource {
        override fun table() = RateTable(
            mapOf("USD" to 1.0, "INR" to 88.0, "EUR" to 0.92, "GBP" to 0.79, "JPY" to 150.0, "AED" to 3.67),
            System.currentTimeMillis() - 2 * 3_600_000L
        )
    }

    private val dictionary = object : DictionarySource {
        override fun lookup(query: String) = DictResult(
            word = "raster", phonetic = "/ˈɹæstɚ/",
            senses = listOf(
                Sense("noun", "A scanning pattern of parallel lines that form the display of an image projected on a cathode-ray tube or monitor.", "The raster is redrawn sixty times a second."),
                Sense("noun", "A bitmap image, consisting of a grid of pixels, stored as a sequence of values."),
                Sense("noun", "A rectangular pattern of parallel scanning lines followed by the electron beam on a screen."),
                Sense("verb", "To scan in parallel lines."),
                Sense("verb", "To convert a vector image into a bitmap.", "The tool rasters the logo at three sizes.")
            ),
            sources = listOf("Wiktionary", "Datamuse")
        )
    }

    private fun env() = Env(Locale.getDefault(), Clock.systemDefaultZone(), rates, dictionary)

    /** The text the sample selects. Some depend on today's date or the device's currency. */
    fun input(key: String): String = when (key) {
        "date" -> DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).format(LocalDate.now().plusDays(136))
        "money" -> {
            val local = try { Currency.getInstance(Locale.getDefault()).currencyCode } catch (_: Exception) { "USD" }
            if (local == "USD") "€38" else "\$38"
        }
        else -> key
    }

    /** Runs the real detector on [text]. Null when nothing matches (which the screen shows as a blank card). */
    fun resolve(text: String): InspectResult? = try {
        Detectors.resolve(text, Source.SELECTION, env())
    } catch (_: Exception) {
        null
    }

    fun bank(): InspectResult = IfscDetector.build(
        "SBIN0001234",
        mapOf(
            "BANK" to "State Bank of India", "BRANCH" to "FORT", "CITY" to "MUMBAI", "STATE" to "MAHARASHTRA",
            "ADDRESS" to "STATE BANK BHAVAN, MADAME CAMA ROAD, NEAR MANTRALAYA", "MICR" to "400002001",
            "UPI" to "yes", "IMPS" to "yes", "NEFT" to "yes", "RTGS" to "yes"
        )
    )

    fun post(): InspectResult = PinCodeDetector.build(
        "400001",
        listOf(
            PinCodeDetector.Office("Fort", "Mumbai", "Maharashtra", "Maharashtra", "Sub Post Office"),
            PinCodeDetector.Office("Horniman Circle", "Mumbai", "Maharashtra", "Maharashtra", "Sub Post Office"),
            PinCodeDetector.Office("Mumbai GPO", "Mumbai", "Maharashtra", "Maharashtra", "Head Post Office")
        )
    )

    /** A live Wikipedia lookup (blocking: call off the main thread), or the same card without a photo if offline. */
    fun wiki(live: Boolean = true): InspectResult {
        val found = if (live) resolve("Leonhard Euler") else null
        if (found != null && found.domain == "WIKI") return found
        return EntityDetector.build(
            EntityDetector.Summary(
                "standard", "Leonhard Euler", "Swiss mathematician and physicist (1707–1783)",
                "Leonhard Euler was a Swiss mathematician, physicist, astronomer, geographer, logician, and engineer who founded the studies of graph theory and topology. He made pioneering and influential discoveries in many other branches of mathematics, such as analytic number theory, complex analysis, and infinitesimal calculus.",
                "https://en.wikipedia.org/wiki/Leonhard_Euler"
            )
        )
    }
}
