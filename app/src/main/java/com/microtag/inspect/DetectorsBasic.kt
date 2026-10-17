package com.microtag.inspect

import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.Period
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ==========================================================
// COLOUR: #RGB, #RRGGBB, #RRGGBBAA, rgb(), hsl(), and bare hex when it is clearly a colour
// ==========================================================

internal object ColourDetector {
    private val hexRe = Regex("^#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
    private val bareRe = Regex("^[0-9a-fA-F]{6}$")
    private val rgbRe = Regex(
        """(?i)^rgba?\(\s*(\d{1,3})\s*[, ]\s*(\d{1,3})\s*[, ]\s*(\d{1,3})\s*(?:[,/]\s*([\d.]+%?)\s*)?\)$"""
    )
    private val hslRe = Regex(
        """(?i)^hsla?\(\s*(\d{1,3}(?:\.\d+)?)(?:deg)?\s*[, ]\s*(\d{1,3}(?:\.\d+)?)%\s*[, ]\s*(\d{1,3}(?:\.\d+)?)%\s*(?:[,/]\s*([\d.]+%?)\s*)?\)$"""
    )

    fun candidates(text: String, source: Source): List<Candidate> {
        val t = text.trim()
        val rgba = parse(t, source) ?: return emptyList()
        val score = if (t.startsWith("#") || t.contains("(")) 0.95 else 0.6
        return listOf(Candidate("colour", score) { build(rgba) })
    }

    /** Returns r, g, b, a (all 0..255) or null. */
    internal fun parse(t: String, source: Source): IntArray? {
        hexRe.find(t)?.let { m ->
            var h = m.groupValues[1]
            if (h.length <= 4) h = h.map { "$it$it" }.joinToString("")
            val r = h.substring(0, 2).toInt(16)
            val g = h.substring(2, 4).toInt(16)
            val b = h.substring(4, 6).toInt(16)
            val a = if (h.length == 8) h.substring(6, 8).toInt(16) else 255
            return intArrayOf(r, g, b, a)
        }
        // A bare six-digit hex is also a number, a PIN, or a word ("decade"). Only treat it as a
        // colour when it mixes digits and letters, and never from the clipboard.
        if (source == Source.SELECTION && bareRe.matches(t) &&
            t.any { it.isDigit() } && t.any { it.isLetter() }
        ) {
            return intArrayOf(
                t.substring(0, 2).toInt(16), t.substring(2, 4).toInt(16), t.substring(4, 6).toInt(16), 255
            )
        }
        rgbRe.find(t)?.let { m ->
            val r = m.groupValues[1].toInt()
            val g = m.groupValues[2].toInt()
            val b = m.groupValues[3].toInt()
            if (r > 255 || g > 255 || b > 255) return null
            return intArrayOf(r, g, b, alphaOf(m.groupValues[4]))
        }
        hslRe.find(t)?.let { m ->
            val h = m.groupValues[1].toDouble()
            val s = m.groupValues[2].toDouble()
            val l = m.groupValues[3].toDouble()
            if (h > 360 || s > 100 || l > 100) return null
            val rgb = hslToRgb(h, s / 100.0, l / 100.0)
            return intArrayOf(rgb[0], rgb[1], rgb[2], alphaOf(m.groupValues[4]))
        }
        return null
    }

    private fun alphaOf(s: String): Int {
        if (s.isBlank()) return 255
        val v = if (s.endsWith("%")) s.dropLast(1).toDoubleOrNull()?.div(100.0) else s.toDoubleOrNull()
        return ((v ?: 1.0).coerceIn(0.0, 1.0) * 255).roundToInt()
    }

    internal fun hslToRgb(h: Double, s: Double, l: Double): IntArray {
        val c = (1 - abs(2 * l - 1)) * s
        val hp = (((h % 360) + 360) % 360) / 60.0
        val x = c * (1 - abs(hp % 2 - 1))
        val (r1, g1, b1) = when {
            hp < 1 -> Triple(c, x, 0.0)
            hp < 2 -> Triple(x, c, 0.0)
            hp < 3 -> Triple(0.0, c, x)
            hp < 4 -> Triple(0.0, x, c)
            hp < 5 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        val m = l - c / 2
        return intArrayOf(
            ((r1 + m) * 255).roundToInt().coerceIn(0, 255),
            ((g1 + m) * 255).roundToInt().coerceIn(0, 255),
            ((b1 + m) * 255).roundToInt().coerceIn(0, 255)
        )
    }

    internal fun rgbToHsl(r: Int, g: Int, b: Int): Triple<Int, Int, Int> {
        val rf = r / 255.0
        val gf = g / 255.0
        val bf = b / 255.0
        val mx = max(rf, max(gf, bf))
        val mn = min(rf, min(gf, bf))
        val l = (mx + mn) / 2
        val d = mx - mn
        if (d == 0.0) return Triple(0, 0, (l * 100).roundToInt())
        val s = d / (1 - abs(2 * l - 1))
        val h = when (mx) {
            rf -> ((gf - bf) / d) % 6
            gf -> (bf - rf) / d + 2
            else -> (rf - gf) / d + 4
        } * 60
        return Triple(((h + 360) % 360).roundToInt() % 360, (s * 100).roundToInt(), (l * 100).roundToInt())
    }

    private fun luminance(r: Int, g: Int, b: Int): Double {
        fun ch(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)
    }

    private fun mix(a: Int, b: Int, t: Double): Int = (a + (b - a) * t).roundToInt().coerceIn(0, 255)

    internal fun build(c: IntArray, nameOverride: String? = null): InspectResult {
        val (r, g, b, a) = listOf(c[0], c[1], c[2], c[3])
        val hex = String.format(Locale.ROOT, "#%02X%02X%02X", r, g, b)
        val full = if (a < 255) hex + String.format(Locale.ROOT, "%02X", a) else hex
        val (h, s, l) = rgbToHsl(r, g, b)
        val lum = luminance(r, g, b)
        val onWhite = 1.05 / (lum + 0.05)
        val onBlack = (lum + 0.05) / 0.05
        val useBlack = onBlack >= onWhite
        val ratio = if (useBlack) onBlack else onWhite
        val who = if (useBlack) "Black" else "White"
        val readable = when {
            ratio >= 7.0 -> "$who text suitable"
            ratio >= 4.5 -> "$who text readable"
            ratio >= 3.0 -> "$who text, large only"
            else -> "Low contrast, $who text best"
        } + " · ${Fmt.trimmed(ratio, 1)}:1"
        val rows = mutableListOf(
            "HEX" to full,
            "RGB" to "rgb($r, $g, $b)",
            "HSL" to "hsl($h, $s%, $l%)"
        )
        if (a < 255) rows.add("Alpha" to "${(a / 2.55).roundToInt()}%")
        rows.add("Readable with" to readable)

        // The complement sits opposite on the colour wheel; the accent is the same hue, lifted so it reads on a dark card.
        val comp = hslToRgb(h + 180.0, s / 100.0, l / 100.0)
        val compHex = String.format(Locale.ROOT, "#%02X%02X%02X", comp[0], comp[1], comp[2])
        rows.add("Complement" to compHex)
        val name = nameOverride ?: ColourNames.nearest(r, g, b)
        name?.let { rows.add(0, "Name" to it) }
        val acc = hslToRgb(h.toDouble(), (if (s < 8) 0 else maxOf(s, 55)) / 100.0, (l.coerceIn(62, 74)) / 100.0)
        return InspectResult(
            domain = "COLOUR",
            pill = hex,
            title = name ?: hex,
            big = hex,
            subtitle = "rgb($r, $g, $b)",
            cardTitle = "Colour",
            cardText = "rgb($r, $g, $b) · hsl($h, $s%, $l%)",
            rows = rows,
            copyText = full,
            swatch = (0xFF shl 24) or (r shl 16) or (g shl 8) or b,
            accent = (0xFF shl 24) or (acc[0] shl 16) or (acc[1] shl 8) or acc[2],
            palette = listOf(
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b,
                (0xFF shl 24) or (comp[0] shl 16) or (comp[1] shl 8) or comp[2],
                // a lighter and a darker step of the same colour, for the card's pinwheel
                (0xFF shl 24) or (mix(r, 255, 0.38) shl 16) or (mix(g, 255, 0.38) shl 8) or mix(b, 255, 0.38),
                (0xFF shl 24) or (mix(r, 0, 0.42) shl 16) or (mix(g, 0, 0.42) shl 8) or mix(b, 0, 0.42)
            ),
            timeoutSeconds = 15
        )
    }
}

// ==========================================================
// TIME ZONE: "6 PM EST", "18:00 UTC", "9:30 IST", "7pm UTC+5:30"
// Abbreviations map to FIXED offsets. EST stays UTC-5 in summer; the old
// version mapped it to New York and was an hour off during daylight saving.
// ==========================================================

internal object TimeZoneDetector {
    private val re = Regex(
        """(?i)^\s*(\d{1,2})(?:[:.](\d{2}))?\s*(a\.?m\.?|p\.?m\.?)?\s*(?:(utc|gmt)\s*([+\-−])\s*(\d{1,2})(?::?(\d{2}))?|([a-z]{2,5}))\s*$"""
    )

    private fun h(hours: Int, minutes: Int = 0) = hours * 60 + (if (hours < 0) -minutes else minutes)

    private val offsets: Map<String, Int> = mapOf(
        "UTC" to 0, "GMT" to 0, "Z" to 0,
        "EST" to h(-5), "EDT" to h(-4), "CST" to h(-6), "CDT" to h(-5),
        "MST" to h(-7), "MDT" to h(-6), "PST" to h(-8), "PDT" to h(-7),
        "AKST" to h(-9), "AKDT" to h(-8), "HST" to h(-10),
        "BST" to h(1), "WET" to 0, "WEST" to h(1), "CET" to h(1), "CEST" to h(2),
        "EET" to h(2), "EEST" to h(3), "MSK" to h(3),
        "IST" to h(5, 30), "PKT" to h(5), "BDT" to h(6), "NPT" to h(5, 45),
        "ICT" to h(7), "WIB" to h(7), "SGT" to h(8), "HKT" to h(8), "PHT" to h(8),
        "AWST" to h(8), "JST" to h(9), "KST" to h(9),
        "ACST" to h(9, 30), "AEST" to h(10), "AEDT" to h(11),
        "NZST" to h(12), "NZDT" to h(13)
    )

    private val notes: Map<String, String> = mapOf(
        "IST" to "IST read as India Standard Time (UTC+5:30)",
        "CST" to "CST read as US Central Standard Time (UTC-6)",
        "BST" to "BST read as British Summer Time (UTC+1)"
    )

    fun candidates(text: String, env: Env): List<Candidate> {
        val m = re.find(text) ?: return emptyList()
        val parsed = parse(m) ?: return emptyList()
        return listOf(Candidate("timezone", 0.95) { build(text.trim(), parsed, env) })
    }

    private class Parsed(val hour: Int, val minute: Int, val offsetMinutes: Int, val label: String, val note: String?, val twelve: Boolean = false, val abbr: String? = null)

    private fun parse(m: MatchResult): Parsed? {
        var hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: 0
        val ampm = m.groupValues[3].lowercase(Locale.ROOT).replace(".", "")
        if (minute > 59) return null
        if (ampm.isNotEmpty()) {
            if (hour !in 1..12) return null
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
        } else if (hour > 23) return null

        val utcWord = m.groupValues[4]
        if (utcWord.isNotEmpty()) {
            val sign = if (m.groupValues[5] == "+") 1 else -1
            val oh = m.groupValues[6].toIntOrNull() ?: return null
            val om = m.groupValues[7].toIntOrNull() ?: 0
            if (oh > 14 || om > 59) return null
            val total = sign * (oh * 60 + om)
            return Parsed(hour, minute, total, utcWord.uppercase(Locale.ROOT) + offsetText(total).removePrefix("UTC"), null, ampm.isNotEmpty())
        }
        val abbr = m.groupValues[8].uppercase(Locale.ROOT)
        val off = offsets[abbr] ?: return null
        return Parsed(hour, minute, off, abbr, notes[abbr], ampm.isNotEmpty(), abbr)
    }

    private fun offsetText(minutes: Int): String {
        val sign = if (minutes < 0) "-" else "+"
        val a = abs(minutes)
        val hh = a / 60
        val mm = a % 60
        return if (mm == 0) "UTC$sign$hh" else "UTC$sign$hh:${mm.toString().padStart(2, '0')}"
    }

    /** Popular zones get the abbreviation people actually say ("IST", not "GMT+05:30"). Standard, then daylight. */
    private val zoneNames: Map<String, Pair<String, String>> = mapOf(
        "Asia/Kolkata" to ("IST" to "IST"), "Asia/Calcutta" to ("IST" to "IST"),
        "Asia/Dubai" to ("GST" to "GST"), "Asia/Karachi" to ("PKT" to "PKT"), "Asia/Dhaka" to ("BDT" to "BDT"),
        "Asia/Kathmandu" to ("NPT" to "NPT"), "Asia/Bangkok" to ("ICT" to "ICT"), "Asia/Jakarta" to ("WIB" to "WIB"),
        "Asia/Singapore" to ("SGT" to "SGT"), "Asia/Hong_Kong" to ("HKT" to "HKT"), "Asia/Manila" to ("PHT" to "PHT"),
        "Asia/Shanghai" to ("CST" to "CST"), "Asia/Tokyo" to ("JST" to "JST"), "Asia/Seoul" to ("KST" to "KST"),
        "Europe/London" to ("GMT" to "BST"), "Europe/Dublin" to ("GMT" to "IST"), "Europe/Lisbon" to ("WET" to "WEST"),
        "Europe/Paris" to ("CET" to "CEST"), "Europe/Berlin" to ("CET" to "CEST"), "Europe/Madrid" to ("CET" to "CEST"),
        "Europe/Rome" to ("CET" to "CEST"), "Europe/Amsterdam" to ("CET" to "CEST"), "Europe/Zurich" to ("CET" to "CEST"),
        "Europe/Athens" to ("EET" to "EEST"), "Europe/Helsinki" to ("EET" to "EEST"), "Europe/Istanbul" to ("TRT" to "TRT"),
        "Europe/Moscow" to ("MSK" to "MSK"), "Africa/Cairo" to ("EET" to "EEST"), "Africa/Johannesburg" to ("SAST" to "SAST"),
        "Africa/Lagos" to ("WAT" to "WAT"), "Africa/Nairobi" to ("EAT" to "EAT"),
        "America/New_York" to ("EST" to "EDT"), "America/Toronto" to ("EST" to "EDT"), "America/Chicago" to ("CST" to "CDT"),
        "America/Denver" to ("MST" to "MDT"), "America/Phoenix" to ("MST" to "MST"), "America/Los_Angeles" to ("PST" to "PDT"),
        "America/Vancouver" to ("PST" to "PDT"), "America/Anchorage" to ("AKST" to "AKDT"), "Pacific/Honolulu" to ("HST" to "HST"),
        "America/Sao_Paulo" to ("BRT" to "BRT"), "America/Mexico_City" to ("CST" to "CST"),
        "Australia/Sydney" to ("AEST" to "AEDT"), "Australia/Melbourne" to ("AEST" to "AEDT"), "Australia/Brisbane" to ("AEST" to "AEST"),
        "Australia/Perth" to ("AWST" to "AWST"), "Australia/Adelaide" to ("ACST" to "ACDT"), "Pacific/Auckland" to ("NZST" to "NZDT"),
        "UTC" to ("UTC" to "UTC"), "Etc/UTC" to ("UTC" to "UTC"), "GMT" to ("GMT" to "GMT")
    )

    /** The zone's short name: the table above, then Java's own if it is letters, otherwise "UTC+5:30". */
    internal fun zoneLabel(zone: java.time.ZoneId, at: Instant): String {
        val dst = zone.rules.isDaylightSavings(at)
        zoneNames[zone.id]?.let { return if (dst) it.second else it.first }
        val z = at.atZone(zone)
        val java = DateTimeFormatter.ofPattern("zzz", Locale.ENGLISH).format(z)
        if (java.length in 2..5 && java.all { it.isLetter() }) return java
        val off = z.offset.totalSeconds / 60
        return offsetText(off).replace(":00", "")
    }

    /** US standard abbreviations and the zone they belong to, for the "that zone is on daylight time now" note. */
    private val usStandard = mapOf(
        "EST" to ("Eastern" to "America/New_York"), "CST" to ("Central" to "America/Chicago"),
        "MST" to ("Mountain" to "America/Denver"), "PST" to ("Pacific" to "America/Los_Angeles")
    )

    private fun build(original: String, p: Parsed, env: Env): InspectResult {
        val srcOffset = ZoneOffset.ofTotalSeconds(p.offsetMinutes * 60)
        val now = Instant.now(env.clock)
        val srcDate = now.atOffset(srcOffset).toLocalDate()
        val src = OffsetDateTime.of(srcDate, LocalTime.of(p.hour, p.minute), srcOffset)

        val zone = env.clock.zone
        val local = src.atZoneSameInstant(zone)
        val today = now.atZone(zone).toLocalDate()
        val dayShift = ChronoUnit.DAYS.between(today, local.toLocalDate()).toInt()

        // 24-hour unless the user typed am/pm.
        val fmt = DateTimeFormatter.ofPattern(if (p.twelve) "h:mm a" else "HH:mm", env.locale)
        val localTime = fmt.format(local)
        val srcTime = fmt.format(src)
        val zoneName = zoneLabel(zone, src.toInstant())
        val srcDay = DateTimeFormatter.ofPattern("EEE d MMM", env.locale).format(srcDate)
        val localDay = DateTimeFormatter.ofPattern("EEE d MMM", env.locale).format(local)
        val dayWord = when (dayShift) {
            0 -> "today"
            1 -> "tomorrow"
            -1 -> "yesterday"
            else -> if (dayShift > 0) "in $dayShift days" else "${-dayShift} days ago"
        }
        val shiftTag = when {
            dayShift > 0 -> " +${dayShift}d"
            dayShift < 0 -> " ${dayShift}d"
            else -> ""
        }
        val dayNote = when {
            dayShift == 0 -> "SAME DAY"
            dayShift > 0 -> "+$dayShift DAY" + if (dayShift > 1) "S" else ""
            else -> "−${-dayShift} DAY" + if (dayShift < -1) "S" else ""
        }
        val localOffsetMin = local.offset.totalSeconds / 60
        val gap = localOffsetMin - p.offsetMinutes

        val rows = mutableListOf(
            "From" to "$original (${offsetText(p.offsetMinutes)})",
            "Local" to localDay,
            "Day" to dayWord.replaceFirstChar { it.uppercase(Locale.ROOT) },
            "That is" to untilText(Duration.between(now, src.toInstant()))
        )
        // "3 PM EST" while New York is on daylight time: say what EST means and what EDT would give.
        var note = p.note
        val us = p.abbr?.let { usStandard[it] }
        if (us != null) {
            val rules = java.time.ZoneId.of(us.second).rules
            if (rules.isDaylightSavings(src.toInstant())) {
                val dstOffset = ZoneOffset.ofTotalSeconds((p.offsetMinutes + 60) * 60)
                val alt = OffsetDateTime.of(srcDate, LocalTime.of(p.hour, p.minute), dstOffset).atZoneSameInstant(zone)
                val altText = DateTimeFormatter.ofPattern("h:mm a", env.locale).format(alt).lowercase(Locale.ROOT)
                val sign = if (p.offsetMinutes < 0) "-" else "+"
                val dstName = us.first.first() + "DT"
                val dstShown = offsetText(p.offsetMinutes + 60)
                note = "${p.abbr} is ${offsetText(p.offsetMinutes).replace("UTC$sign", "UTC$sign")}. ${us.first} time is on $dstName now ($dstShown): that would be $altText."
            }
        }
        note?.let { rows.add("Note" to it) }

        val pill = when (PillConfig.TIME) {
            "TIME_ZONE" -> "$localTime $zoneName"
            "DAY" -> dayWord
            else -> localTime + shiftTag
        }
        return InspectResult(
            domain = "TIME",
            pill = pill,
            title = "$localTime $zoneName",
            subtitle = "From $original",
            cardTitle = "Time conversion",
            cardText = "$original = $localTime $zoneName ($dayWord)",
            rows = rows,
            copyText = "$localTime $zoneName",
            nums = listOf(
                (p.hour * 60 + p.minute).toDouble(), (local.hour * 60 + local.minute).toDouble(), dayShift.toDouble(),
                Duration.between(now, src.toInstant()).toMinutes().toDouble(), p.offsetMinutes.toDouble(), localOffsetMin.toDouble()
            ),
            parts = listOf(srcTime, p.label, srcDay, localTime, zoneName, localDay, Timespan.gapText(gap), dayNote),
            iconName = "ic_calendar_countdown",
            timeoutSeconds = 15
        )
    }

    internal fun untilText(d: Duration): String {
        val secs = d.seconds
        val a = abs(secs)
        if (a < 60) return "right now"
        val h = a / 3600
        val m = (a % 3600) / 60
        val body = when {
            h >= 48 -> "${h / 24} days"
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
        return if (secs > 0) "in $body" else "$body ago"
    }
}

// ==========================================================
// MATH: + - * / ^ % ( ), x and ÷, "15% of 240", "200 + 10%", sqrt() and friends
// ==========================================================

internal object MathDetector {
    private val dateLike = Regex("""^\d{1,4}[-/.]\d{1,2}([-/.]\d{1,4})?$""")
    private val phoneLike = Regex("""^\+?\d[\d\s-]{7,}$""")
    private val bareRange = Regex("""^\d+\s*-\s*\d+$""")
    private val funcCall = Regex("""\b(sqrt|abs|round|floor|ceil|ln|log)\s*\(""")

    fun candidates(text: String, source: Source, env: Env): List<Candidate> {
        val original = text.trim()
        if (original.length > 80 || original.none { it.isDigit() }) return emptyList()
        if (dateLike.matches(original) || phoneLike.matches(original)) return emptyList()
        if (source == Source.CLIPBOARD && bareRange.matches(original)) return emptyList()

        val expr = normalise(original) ?: return emptyList()
        val hasOp = Regex("""[0-9)a-z.]\s*[+\-*/^]""").containsMatchIn(expr) || funcCall.containsMatchIn(expr)
        if (!hasOp) return emptyList()

        val score = if (bareRange.matches(original)) 0.45 else 0.9
        return listOf(Candidate("math", score) { build(original, expr, env.locale) })
    }

    /** Lower-cases, fixes symbols, and rejects anything that is not arithmetic. */
    internal fun normalise(original: String): String? {
        var t = original.lowercase(Locale.ROOT)
            .replace('×', '*').replace('÷', '/')
            .replace('−', '-').replace('–', '-').replace('—', '-')
        t = t.replace(Regex("""(?<=[\d)])\s*x\s*(?=[\d(.])"""), "*")
        t = t.replace(Regex("""(?<=\d),(?=\d)"""), "")
        t = Regex("""^(\d+(?:\.\d+)?)\s*%\s*of\s*(.+)$""").replace(t) { "(${it.groupValues[1]}/100)*(${it.groupValues[2]})" }
        if (!Regex("""^[0-9a-z+\-*/^%().\s]+$""").matches(t)) return null
        val stripped = t
            .replace(Regex("""\d+(?:\.\d+)?e[+-]?\d+"""), "0")
            .replace(Regex("""\b(sqrt|abs|round|floor|ceil|ln|log|pi|e)\b"""), "0")
        if (stripped.any { it.isLetter() }) return null
        return t
    }

    private fun build(original: String, expr: String, locale: Locale): InspectResult? {
        val value = Parser(expr).parse() ?: return null
        if (value.isNaN() || value.isInfinite()) {
            return InspectResult(
                domain = "CALC",
                pill = "÷0",
                title = "Undefined",
                cardTitle = "Calculation",
                cardText = "$original is not a number (division by zero?)",
                rows = listOf("Expression" to original),
                iconName = "ic_equals",
                timeoutSeconds = 8,
                status = Status.WARN
            )
        }
        val plain = Fmt.plain(value)
        val hero = if (abs(value) < 1e15) Fmt.grouped(value, locale, 10) else plain
        val tape = tapeFor(original, locale)
        return InspectResult(
            domain = "CALC",
            parts = tape,
            pill = if (PillConfig.CALC == "EQUATION") original.replace(" ", "") + "=" + plain else plain,
            title = hero,
            subtitle = original,
            cardTitle = "Calculation",
            cardText = "$original = $plain",
            rows = listOf("Expression" to original, "Result" to plain),
            copyText = plain,
            iconName = "ic_equals",
            timeoutSeconds = 15
        )
    }

    private val pctAdd = Regex("""^\s*([\d.,]+)\s*([+\-−–])\s*([\d.,]+)\s*%\s*$""")
    private val pctOf = Regex("""(?i)^\s*([\d.,]+)\s*%\s*of\s*([\d.,]+)\s*$""")

    /** The working for percentage sums: "567 + 15%" shows 567 × 15% = 85.05, then 567 + 85.05. */
    internal fun tapeFor(original: String, locale: Locale): List<String> {
        fun g(v: Double) = Fmt.grouped(v, locale, 6)
        pctAdd.find(original)?.let { m ->
            val base = Fmt.parseNumber(m.groupValues[1]) ?: return emptyList()
            val pct = Fmt.parseNumber(m.groupValues[3]) ?: return emptyList()
            val op = if (m.groupValues[2] == "+") "+" else "−"
            val part = base * pct / 100.0
            return listOf("${g(base)} × ${Fmt.plain(pct)}% = ${g(part)}", "${g(base)} $op ${g(part)}")
        }
        pctOf.find(original)?.let { m ->
            val pct = Fmt.parseNumber(m.groupValues[1]) ?: return emptyList()
            val base = Fmt.parseNumber(m.groupValues[2]) ?: return emptyList()
            return listOf("${g(base)} × ${Fmt.plain(pct)}% = ${g(base * pct / 100.0)}")
        }
        return emptyList()
    }

    private class ParseEx : Exception()

    private class Num(val v: Double, val pct: Boolean = false)

    private class Parser(private val s: String) {
        private var i = 0

        fun parse(): Double? = try {
            val r = expr()
            skipWs()
            if (i == s.length) r.v else null
        } catch (_: ParseEx) {
            null
        }

        private fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun peek(): Char = if (i < s.length) s[i] else '\u0000'

        private fun expr(): Num {
            var left = term()
            while (true) {
                skipWs()
                val c = peek()
                if (c != '+' && c != '-') break
                i++
                val right = term()
                // "200 + 10%" means 200 plus 10 percent OF 200.
                val rv = if (right.pct) left.v * right.v else right.v
                left = Num(if (c == '+') left.v + rv else left.v - rv)
            }
            return left
        }

        private fun term(): Num {
            var left = unary()
            while (true) {
                skipWs()
                val c = peek()
                when (c) {
                    '*' -> { i++; left = Num(left.v * unary().v) }
                    '/' -> { i++; left = Num(left.v / unary().v) }
                    '%' -> { i++; left = Num(left.v % unary().v) }   // only reached when an operand follows
                    else -> return left
                }
            }
        }

        private fun unary(): Num {
            skipWs()
            return when (peek()) {
                '-' -> { i++; Num(-unary().v) }
                '+' -> { i++; unary() }
                else -> power()
            }
        }

        private fun power(): Num {
            val base = postfix()
            skipWs()
            if (peek() == '^') {
                i++
                val exp = unary()
                return Num(base.v.pow(exp.v))
            }
            return base
        }

        private fun postfix(): Num {
            val p = primary()
            skipWs()
            if (peek() == '%') {
                // "10 % 3" is a remainder; "10%" or "10% )" is a percentage.
                var j = i + 1
                while (j < s.length && s[j].isWhitespace()) j++
                val operandFollows = j < s.length && (s[j].isDigit() || s[j] == '(' || s[j] == '.')
                if (!operandFollows) {
                    i++
                    return Num(p.v / 100.0, pct = true)
                }
            }
            return p
        }

        private fun primary(): Num {
            skipWs()
            val c = peek()
            if (c == '(') {
                i++
                val r = expr()
                skipWs()
                if (peek() != ')') throw ParseEx()
                i++
                return Num(r.v)
            }
            if (c.isDigit() || c == '.') return Num(number())
            if (c.isLetter()) {
                val start = i
                while (i < s.length && s[i].isLetter()) i++
                val name = s.substring(start, i)
                skipWs()
                if (peek() == '(') {
                    i++
                    val arg = expr()
                    skipWs()
                    if (peek() != ')') throw ParseEx()
                    i++
                    return Num(
                        when (name) {
                            "sqrt" -> sqrt(arg.v)
                            "abs" -> abs(arg.v)
                            "round" -> kotlin.math.round(arg.v)
                            "floor" -> kotlin.math.floor(arg.v)
                            "ceil" -> kotlin.math.ceil(arg.v)
                            "ln" -> kotlin.math.ln(arg.v)
                            "log" -> kotlin.math.log10(arg.v)
                            else -> throw ParseEx()
                        }
                    )
                }
                return Num(
                    when (name) {
                        "pi" -> Math.PI
                        "e" -> Math.E
                        else -> throw ParseEx()
                    }
                )
            }
            throw ParseEx()
        }

        private fun number(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (i < s.length && s[i] == 'e' && i + 1 < s.length &&
                (s[i + 1].isDigit() || ((s[i + 1] == '+' || s[i + 1] == '-') && i + 2 < s.length && s[i + 2].isDigit()))
            ) {
                i += 2
                while (i < s.length && s[i].isDigit()) i++
            }
            return s.substring(start, i).toDoubleOrNull() ?: throw ParseEx()
        }
    }
}

// ==========================================================
// DATE: "9 November", "Nov 9, 2026", "2026-11-09", "09/11/2026"
// Counts calendar days (the old version compared against midnight and said
// "Today" for tomorrow). With no year it picks the nearest occurrence.
// ==========================================================

internal object DateDetector {
    private val monthNames = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december"
    )
    private val monthLookup: Map<String, Int> = buildMap {
        monthNames.forEachIndexed { idx, name ->
            put(name, idx + 1)
            put(name.take(3), idx + 1)
        }
        put("sept", 9)
    }

    private const val WEEKDAY = """(?:(?:mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)[a-z]*\.?,?\s+)?"""
    private val dayMonth = Regex("""(?i)^$WEEKDAY(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?([a-z]{3,9})\.?,?(?:\s+(\d{4}))?$""")
    private val monthDay = Regex("""(?i)^$WEEKDAY([a-z]{3,9})\.?\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{4}))?$""")
    private val iso = Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})$""")
    private val numeric = Regex("""^(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{2}|\d{4})$""")

    private class Parsed(val date: LocalDate, val note: String?)

    fun candidates(text: String, env: Env): List<Candidate> {
        val t = text.trim().trimEnd('.', ',')
        val today = LocalDate.now(env.clock)
        val parsed = parse(t, today, env.locale) ?: return emptyList()
        val score = if (iso.matches(t) || numeric.matches(t)) 0.85 else 0.9
        return listOf(Candidate("date", score) { build(parsed, today, env.locale) })
    }

    private fun parse(t: String, today: LocalDate, locale: Locale): Parsed? {
        dayMonth.find(t)?.let { m ->
            val month = monthLookup[m.groupValues[2].lowercase(Locale.ROOT)] ?: return null
            return withYear(m.groupValues[1].toInt(), month, m.groupValues[3], today)
        }
        monthDay.find(t)?.let { m ->
            val month = monthLookup[m.groupValues[1].lowercase(Locale.ROOT)] ?: return null
            return withYear(m.groupValues[2].toInt(), month, m.groupValues[3], today)
        }
        iso.find(t)?.let { m ->
            return safe(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { Parsed(it, null) }
        }
        numeric.find(t)?.let { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            var y = m.groupValues[3].toInt()
            if (m.groupValues[3].length == 2) y += 2000
            val usOrder = locale.country.equals("US", ignoreCase = true)
            val (day, month, note) = when {
                a > 12 -> Triple(a, b, null)
                b > 12 -> Triple(b, a, null)
                a == b -> Triple(a, b, null)
                usOrder -> Triple(b, a, "Read as month/day/year")
                else -> Triple(a, b, "Read as day/month/year")
            }
            return safe(y, month, day)?.let { Parsed(it, note) }
        }
        return null
    }

    private fun safe(y: Int, m: Int, d: Int): LocalDate? = try {
        LocalDate.of(y, m, d)
    } catch (_: DateTimeException) {
        null
    }

    private fun withYear(day: Int, month: Int, yearText: String, today: LocalDate): Parsed? {
        if (yearText.isNotEmpty()) return safe(yearText.toInt(), month, day)?.let { Parsed(it, null) }
        // No year: take the occurrence closest to today (last year, this year or next year).
        val options = (today.year - 1..today.year + 1).mapNotNull { safe(it, month, day) }
        val best = options.minByOrNull { abs(ChronoUnit.DAYS.between(today, it)) } ?: return null
        return Parsed(best, null)
    }

    private fun plural(n: Long, unit: String) = if (n == 1L) "1 $unit" else "$n ${unit}s"

    private fun build(p: Parsed, today: LocalDate, locale: Locale): InspectResult {
        val d = p.date
        val delta = ChronoUnit.DAYS.between(today, d)
        val relative = when {
            delta == 0L -> "today"
            delta == 1L -> "tomorrow"
            delta == -1L -> "yesterday"
            delta > 0 -> "in ${plural(delta, "day")}"
            else -> "${plural(-delta, "day")} ago"
        }
        val a = abs(delta)
        val pill = when {
            delta == 0L -> "Today"
            delta == 1L -> "Tomorrow"
            delta == -1L -> "Yesterday"
            a < 100 -> if (delta > 0) "in ${a}d" else "${a}d ago"
            a < 365 -> if (delta > 0) "in ${a / 30}mo" else "${a / 30}mo ago"
            else -> if (delta > 0) "in ${a / 365}y" else "${a / 365}y ago"
        }
        val formatted = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale).format(d)

        val breakdown = if (a < 2) null else {
            val period = if (delta > 0) Period.between(today, d) else Period.between(d, today)
            val parts = mutableListOf<String>()
            if (period.years > 0) parts.add(plural(period.years.toLong(), "year"))
            if (period.months > 0) parts.add(plural(period.months.toLong(), "month"))
            val weeks = period.days / 7
            val days = period.days % 7
            if (weeks > 0) parts.add(plural(weeks.toLong(), "week"))
            if (days > 0) parts.add(plural(days.toLong(), "day"))
            parts.joinToString(", ")
        }

        val first = if (d.isBefore(today)) d else today
        val second = if (d.isBefore(today)) today else d
        fun wd(x: LocalDate) = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH).format(x).uppercase(Locale.ROOT)
        val parts = listOf(
            first.monthValue.toString(), first.dayOfMonth.toString(), wd(first),
            second.monthValue.toString(), second.dayOfMonth.toString(), wd(second),
            breakdown.orEmpty(), if (second.year != first.year || second.year != today.year) second.year.toString() else ""
        )
        val rows = mutableListOf("Date" to formatted, "Relative" to relative)
        breakdown?.let { rows.add("Breakdown" to it) }
        rows.add("Week" to "Week ${d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)} of ${d.get(IsoFields.WEEK_BASED_YEAR)}")
        rows.add("Day of year" to d.dayOfYear.toString())
        p.note?.let { rows.add("Note" to it) }

        return InspectResult(
            domain = "DATE",
            pill = if (PillConfig.DATE == "DATE") DateTimeFormatter.ofPattern("d MMM", locale).format(d) else pill,
            title = relative.replaceFirstChar { it.uppercase(Locale.ROOT) },
            subtitle = formatted,
            parts = parts,
            caption = "WEEK ${d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)} · DAY ${d.dayOfYear}",
            cardTitle = "Date",
            cardText = "$formatted · $relative",
            rows = rows,
            copyText = formatted,
            nums = listOf(today.toEpochDay().toDouble(), d.toEpochDay().toDouble()),
            iconName = "ic_calendar_countdown",
            timeoutSeconds = 15
        )
    }
}

// ==========================================================
// READING TIME: any longer selection
// ==========================================================

internal object ReadingDetector {
    private const val MIN_WORDS = 8

    fun candidates(text: String, source: Source): List<Candidate> {
        if (source != Source.SELECTION) return emptyList()
        val words = Regex("""\S+""").findAll(text).count()
        if (words < MIN_WORDS) return emptyList()
        return listOf(Candidate("reading", 0.5) { build(text, words) })
    }

    private fun clock(seconds: Int): String = when {
        seconds < 60 -> "${seconds}s"
        else -> "${seconds / 60}m ${(seconds % 60).toString().padStart(2, '0')}s"
    }

    private fun build(text: String, words: Int): InspectResult {
        val readSecs = (words / 230.0 * 60).roundToInt().coerceAtLeast(1)
        val speakSecs = (words / 150.0 * 60).roundToInt().coerceAtLeast(1)
        val sentences = Regex("""[^.!?]+[.!?]+""").findAll(text).count().coerceAtLeast(1)
        val pill = if (readSecs < 60) "${readSecs}s read" else "~${(readSecs / 60.0).roundToInt().coerceAtLeast(1)}m read"
        return InspectResult(
            domain = "TEXT",
            pill = if (PillConfig.TEXT == "WORDS") "$words words" else pill,
            title = clock(readSecs) + " read",
            subtitle = "$words words",
            cardTitle = "Reading estimate",
            cardText = "$words words · ${text.length} characters · ${clock(readSecs)} to read",
            rows = listOf(
                "Characters" to text.length.toString(),
                "Sentences" to sentences.toString(),
                "Reading" to "${clock(readSecs)} at 230 wpm",
                "Speaking" to "${clock(speakSecs)} at 150 wpm"
            ),
            copyText = "$words words, ${clock(readSecs)} read",
            iconName = "ic_inspect_define",
            timeoutSeconds = 12
        )
    }
}
