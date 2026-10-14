package com.microtag.inspect

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Clock
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/** Where the text came from. Clipboard is stricter: it must never fire on ordinary copies. */
enum class Source { SELECTION, CLIPBOARD }

enum class Status { OK, WARN, ERROR }

/** Everything a detector needs from the outside world, so detectors stay pure and testable. */
class Env(
    val locale: Locale = Locale.getDefault(),
    val clock: Clock = Clock.systemDefaultZone(),
    val rates: RateSource? = null,
    val dictionary: DictionarySource? = null
)

interface RateSource {
    /** May block on the network. Null when there is nothing usable at all. */
    fun table(): RateTable?
}

/** Rates are units of each currency per 1 USD. */
class RateTable(
    val perUsd: Map<String, Double>,
    val fetchedAtMillis: Long,
    /** True when this is an old copy because a refresh failed. */
    val offlineCopy: Boolean = false
)

interface DictionarySource {
    fun lookup(query: String): DictResult
}

/**
 * One possible reading of the selected text. The engine tries candidates from the highest
 * score down and uses the first one that produces a result.
 */
class Candidate(
    val name: String,
    val score: Double,
    /** True when resolving may hit the network, so the engine shows a loading pill first. */
    val slow: Boolean = false,
    val resolve: () -> InspectResult?
)

data class InspectResult(
    /** Short tag for the kind of result: CALC, CONV, MONEY, TIME, DATE, COLOUR, URL, DEFINE, ENTITY, TEXT, INFO. */
    val domain: String,
    /** Text for the status-bar pill. */
    val pill: String,
    /** Hero value, also the expanded card's title. */
    val title: String,
    val cardTitle: String,
    val cardText: String,
    val subtitle: String = "",
    val rows: List<Pair<String, String>> = emptyList(),
    val body: String = "",
    val copyText: String = "",
    val iconName: String? = null,
    /** ARGB colour drawn as the pill icon (colour swatch). */
    val swatch: Int? = null,
    /** ARGB accent for the card (label, glow, Copy button). Null = the domain's default. */
    val accent: Int? = null,
    /** Colours to show as a strip in the card (a colour and its complement). */
    val palette: List<Int> = emptyList(),
    val openUrl: String = "",
    val timeoutSeconds: Int = 15,
    val status: Status = Status.OK,
    /** Site name the card's CHECK button may look up (DNS filtering, domain age). Blank = no button. */
    val checkHost: String = "",
    /** A shortened link the card's RESOLVE button may expand. Blank = no button. */
    val resolveUrl: String = "",
    /** The large, tap-to-copy value on the card (the hex for a colour). Blank = the title. */
    val big: String = "",
    /** Picture shown on the card (Wikipedia thumbnail). */
    val imageUrl: String = "",
    /** Numbers a card graphic draws (dimensions: w, h, d). Meaning depends on the domain. */
    val nums: List<Double> = emptyList(),
    val unit: String = "",
    /** Calendar event to open when the card's arrow is tapped (0 = none). */
    val eventId: Long = 0L
) {
    fun toPayload(): InspectPayload = InspectPayload(
        domain = domain,
        title = title,
        subtitle = subtitle,
        fullContent = body,
        copyText = copyText.ifBlank { title },
        rows = rows,
        url = openUrl,
        status = status.name,
        accent = accent ?: 0,
        palette = palette,
        checkHost = checkHost,
        resolveUrl = resolveUrl,
        big = big,
        imageUrl = imageUrl,
        nums = nums,
        unit = unit,
        eventId = eventId
    )
}

internal object Fmt {

    /** Up to 12 significant digits, no grouping, no trailing zeros. 0.1+0.2 prints as 0.3. */
    fun plain(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return v.toString()
        if (v == 0.0) return "0"
        val a = abs(v)
        if (a >= 1e15 || a < 1e-9) return String.format(Locale.ROOT, "%.6e", v)
        return BigDecimal(v).round(MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString()
    }

    /** Rounds to [frac] decimals and drops trailing zeros. */
    fun trimmed(v: Double, frac: Int): String {
        if (v.isNaN() || v.isInfinite()) return v.toString()
        if (v == 0.0) return "0"
        return BigDecimal.valueOf(v).setScale(frac, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    /** Precision that suits the size of the number: 1234.5, 12.35, 0.0123. */
    fun adaptive(v: Double): String {
        val a = abs(v)
        val frac = when {
            a == 0.0 -> 0
            a >= 100 -> 1
            a >= 1 -> 2
            a >= 0.01 -> 4
            else -> 6
        }
        return trimmed(v, frac)
    }

    fun grouped(v: Double, locale: Locale, maxFrac: Int = 6): String {
        val nf = NumberFormat.getNumberInstance(locale)
        nf.maximumFractionDigits = maxFrac
        nf.minimumFractionDigits = 0
        return nf.format(v)
    }

    /** "1,299.50", "1.299,50", "12,5", "1,00,000" -> a Double. */
    fun parseNumber(raw: String): Double? {
        var s = raw.trim().replace(" ", "").replace(" ", "")
        if (s.isEmpty()) return null
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        s = when {
            lastDot >= 0 && lastComma >= 0 ->
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            lastComma >= 0 -> {
                val after = s.length - lastComma - 1
                val commas = s.count { it == ',' }
                if (commas == 1 && after in 1..2) s.replace(',', '.') else s.replace(",", "")
            }
            else -> s
        }
        if (s.count { it == '.' } > 1) s = s.replace(".", "")
        return s.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    fun currencySymbol(code: String, locale: Locale): String = try {
        Currency.getInstance(code).getSymbol(locale)
    } catch (_: Exception) {
        code
    }

    fun money(v: Double, code: String, locale: Locale): String = try {
        val cur = Currency.getInstance(code)
        val nf = NumberFormat.getCurrencyInstance(locale)
        nf.currency = cur
        val frac = cur.defaultFractionDigits.coerceAtLeast(0)
        nf.minimumFractionDigits = frac
        nf.maximumFractionDigits = if (abs(v) < 1 && frac > 0) frac + 2 else frac
        nf.format(v)
    } catch (_: Exception) {
        "${adaptive(v)} $code"
    }
}
