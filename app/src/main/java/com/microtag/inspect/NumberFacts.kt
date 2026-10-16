package com.microtag.inspect

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.log10
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * The number card's "bonus line". Rare on purpose: most numbers get nothing.
 *
 * Three sources, strongest first:
 *  1. Curated facts that do not change (86,400 seconds in a day, 299,792,458 m/s, the human genome).
 *     Round selections (3 significant digits or fewer) also match within a few percent and get an "≈".
 *  2. Computed facts about the number itself (power of two, Fibonacci, factorial, perfect number, palindrome).
 *  3. Scale intuition for powers of ten: a million seconds is about 11.6 days.
 *
 * Nothing here changes over time, and nothing touches the network.
 */
internal object NumberFacts {

    private class Fact(val value: Double, val text: String, val tol: Double = 0.03)

    private val curated = listOf(
        // Time
        Fact(1_440.0, "Minutes in a day"),
        Fact(3_600.0, "Seconds in an hour"),
        Fact(8_760.0, "Hours in a 365-day year"),
        Fact(86_400.0, "Seconds in a day"),
        Fact(525_600.0, "Minutes in a 365-day year"),
        Fact(604_800.0, "Seconds in a week"),
        Fact(31_536_000.0, "Seconds in a 365-day year"),
        Fact(9_192_631_770.0, "Cycles of caesium-133 radiation that define one second", 0.0),
        // Light, space, Earth
        Fact(299_792_458.0, "Speed of light in metres per second", 0.0),
        Fact(299_792.0, "Speed of light in kilometres per second", 0.002),
        Fact(384_400.0, "Average distance from Earth to the Moon, in km", 0.01),
        Fact(149_597_870.0, "One astronomical unit (Earth to Sun), in km", 0.01),
        Fact(9_460_730_472_580.0, "One light-year, in km", 0.01),
        Fact(40_075.0, "Earth's circumference at the equator, in km", 0.01),
        Fact(12_742.0, "Earth's mean diameter, in km", 0.01),
        Fact(1_392_700.0, "Diameter of the Sun, in km", 0.01),
        Fact(8_849.0, "Height of Mount Everest, in metres", 0.005),
        Fact(10_935.0, "Depth of the Challenger Deep, in metres, give or take", 0.01),
        Fact(4_540_000_000.0, "Age of the Earth, in years", 0.03),
        Fact(13_800_000_000.0, "Age of the universe, in years", 0.03),
        Fact(100_000_000_000.0, "Estimated stars in the Milky Way: 100 to 400 billion", 0.0),
        // Bodies and people
        Fact(3_100_000_000.0, "Base pairs in the human genome", 0.05),
        Fact(86_000_000_000.0, "Neurons in a human brain, roughly", 0.03),
        Fact(37_000_000_000_000.0, "Cells in a human body, roughly 37 trillion", 0.03),
        Fact(7_000_000_000.0, "World population passed 7 billion in 2011", 0.02),
        Fact(8_000_000_000.0, "World population passed 8 billion in November 2022", 0.02),
        // Computing
        Fact(1_024.0, "Bytes in a kibibyte"),
        Fact(1_048_576.0, "Bytes in a mebibyte"),
        Fact(1_073_741_824.0, "Bytes in a gibibyte"),
        Fact(16_777_216.0, "Colours in 24-bit RGB", 0.0),
        Fact(2_147_483_647.0, "Largest signed 32-bit integer, and a Mersenne prime", 0.0),
        Fact(4_294_967_296.0, "Possible IPv4 addresses, 2³²", 0.0),
        Fact(65_535.0, "Largest unsigned 16-bit integer, and the number of TCP ports", 0.0),
        Fact(1_420_405_751.0, "Frequency of the hydrogen line, in Hz", 0.0),
        // Everyday measures
        Fact(5_280.0, "Feet in a mile", 0.0),
        Fact(1_609.0, "Metres in a mile", 0.002),
        Fact(1_852.0, "Metres in a nautical mile", 0.0),
        Fact(42_195.0, "Length of a marathon, in metres", 0.0),
        // Maths
        Fact(1_729.0, "The Hardy–Ramanujan number: the smallest number that is a sum of two cubes in two ways", 0.0)
    )

    private val perfect = setOf(8_128L, 33_550_336L, 8_589_869_056L, 137_438_691_328L, 2_305_843_008_139_952_128L)
    private val mersenneExp = mapOf(8_191L to 13, 131_071L to 17, 524_287L to 19)
    private val sup = "⁰¹²³⁴⁵⁶⁷⁸⁹"

    private fun power(base: Int, exp: Int): String = base.toString() + exp.toString().map { sup[it - '0'] }.joinToString("")

    fun find(value: Double, locale: Locale): String? {
        if (value < 1000.0 || value >= 1e15) return null
        val sig = sigDigits(value)
        // 1. Exact curated.
        curated.firstOrNull { value == it.value }?.let { return it.text }
        // 2. Computed facts about the integer itself.
        if (value == Math.floor(value) && value < 9e15) computed(value.roundToLong(), locale)?.let { return it }
        // 3. Approximate curated, round numbers only.
        if (sig <= 3) {
            curated.filter { it.tol > 0.0 }
                .minByOrNull { abs(value - it.value) / it.value }
                ?.let { f ->
                    val d = abs(value - f.value) / f.value
                    if (d <= f.tol) return if (d < 1e-9) f.text else "≈ " + f.text
                }
        }
        // 4. Scale intuition for powers of ten.
        scale(value, locale)?.let { return it }
        return null
    }

    internal fun sigDigits(v: Double): Int {
        var n = Math.abs(v).roundToLong()
        if (n == 0L) return 0
        while (n % 10 == 0L) n /= 10
        return n.toString().length
    }

    private fun isPowerOfTen(v: Double): Int? {
        val l = log10(v)
        val r = l.roundToLong()
        return if (abs(l - r) < 1e-12 && r in 3..15) r.toInt() else null
    }

    private fun computed(n: Long, locale: Locale): String? {
        if (isPowerOfTen(n.toDouble()) != null) return null
        if (n in perfect) return "A perfect number: it equals the sum of its proper divisors"
        mersenneExp[n]?.let { return "A Mersenne prime: ${power(2, it)} − 1" }
        // Power of two.
        if (n > 0 && (n and (n - 1)) == 0L) {
            val e = 63 - java.lang.Long.numberOfLeadingZeros(n)
            if (e >= 10) return "A power of two: ${power(2, e)}"
        }
        // Fibonacci.
        var a = 1L; var b = 1L; var i = 2
        while (b < n && b < 8_000_000_000_000_000L) { val t = a + b; a = b; b = t; i++ }
        if (b == n) return "A Fibonacci number (F$i)"
        // Factorial.
        var f = 1L
        for (k in 2..18) {
            f *= k
            if (f == n && k >= 7) return "$k! (${k} factorial)"
            if (f > n) break
        }
        // Squares and cubes (not for powers of ten, which get the scale line instead).
        val s = sqrt(n.toDouble()).roundToLong()
        val c = cbrt(n.toDouble()).roundToLong()
        val sq = s * s == n && s >= 32
        val cu = c * c * c == n && c >= 11
        val g = { x: Long -> com.microtag.inspect.Fmt.grouped(x.toDouble(), locale, 0) }
        // Round numbers like 8,000,000,000 (2,000³) are not interesting; leave them to curated facts.
        if (sigDigits(n.toDouble()) <= 2 && (sq || cu)) return null
        if (sq && cu) return "Both a perfect square (${g(s)}²) and a perfect cube (${g(c)}³)"
        if (sq) return "A perfect square: ${g(s)}²"
        if (cu) return "A perfect cube: ${g(c)}³"
        // Palindrome.
        val d = n.toString()
        if (d.length >= 5 && d == d.reversed() && d.toSet().size > 1) return "A palindrome: reads the same backwards"
        return null
    }

    private fun scale(v: Double, locale: Locale): String? {
        val k = isPowerOfTen(v) ?: return null
        if (k !in 5..10) return null
        val day = 86_400.0
        val year = 365.25 * day
        val text = when {
            v < 2 * day -> "${Fmt.trimmed(v / 3600, 1)} hours"
            v < 90 * day -> "${Fmt.trimmed(v / day, 1)} days"
            v < 2 * year -> "${Fmt.trimmed(v / (year / 12), 1)} months"
            v < 100 * year -> "${Fmt.trimmed(v / year, 1)} years"
            else -> "${Fmt.trimmed(v / year, 0)} years"
        }
        return "${Fmt.grouped(v, locale, 0)} seconds is about $text"
    }
}
