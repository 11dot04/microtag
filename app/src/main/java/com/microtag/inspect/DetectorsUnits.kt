package com.microtag.inspect

import java.util.Locale

// ==========================================================
// UNITS: "10 km", "98.6°F", "5'10\"", "2 gal", "500 GB"
// Table driven: every unit knows how to reach its dimension's base unit, so adding a unit is
// one line. Each unit lists what it converts to, first target first.
// ==========================================================

internal object UnitDetector {

    private class U(
        val sym: String,
        val dim: String,
        val toBase: (Double) -> Double,
        val fromBase: (Double) -> Double,
        val names: List<String>,
        val targets: List<String>
    )

    private fun u(sym: String, dim: String, factor: Double, names: String, targets: String) = U(
        sym, dim, { it * factor }, { it / factor },
        names.split("|"), targets.split(",").filter { it.isNotBlank() }
    )

    private val units: List<U> = listOf(
        // length (m)
        u("mm", "len", 0.001, "mm|millimeter|millimeters|millimetre|millimetres", "in"),
        u("cm", "len", 0.01, "cm|centimeter|centimeters|centimetre|centimetres", "in,ft"),
        u("m", "len", 1.0, "m|meter|meters|metre|metres", "ft,yd"),
        u("km", "len", 1000.0, "km|kms|kilometer|kilometers|kilometre|kilometres", "mi"),
        u("in", "len", 0.0254, "in|inch|inches", "cm,mm"),
        u("ft", "len", 0.3048, "ft|foot|feet", "m,cm"),
        u("yd", "len", 0.9144, "yd|yds|yard|yards", "m"),
        u("mi", "len", 1609.344, "mi|mile|miles", "km"),
        u("nmi", "len", 1852.0, "nmi|nautical mile|nautical miles", "km,mi"),
        // mass (kg)
        u("mg", "mass", 1e-6, "mg|milligram|milligrams", "oz"),
        u("g", "mass", 0.001, "g|gm|gms|gram|grams", "oz"),
        u("kg", "mass", 1.0, "kg|kgs|kilo|kilos|kilogram|kilograms", "lb,st"),
        u("t", "mass", 1000.0, "tonne|tonnes|metric ton|metric tons", "lb"),
        u("oz", "mass", 0.028349523125, "oz|ounce|ounces", "g"),
        u("lb", "mass", 0.45359237, "lb|lbs|pound|pounds", "kg,g"),
        u("st", "mass", 6.35029318, "st|stone|stones", "kg,lb"),
        // volume (l), US customary
        u("ml", "vol", 0.001, "ml|milliliter|milliliters|millilitre|millilitres", "fl oz,tsp"),
        u("l", "vol", 1.0, "l|ltr|liter|liters|litre|litres", "gal,fl oz,cup"),
        u("fl oz", "vol", 0.0295735295625, "fl oz|floz|fluid ounce|fluid ounces", "ml"),
        u("cup", "vol", 0.2365882365, "cup|cups", "ml"),
        u("tbsp", "vol", 0.01478676478125, "tbsp|tablespoon|tablespoons", "ml"),
        u("tsp", "vol", 0.00492892159375, "tsp|teaspoon|teaspoons", "ml"),
        u("pt", "vol", 0.473176473, "pt|pint|pints", "ml,l"),
        u("qt", "vol", 0.946352946, "qt|quart|quarts", "l"),
        u("gal", "vol", 3.785411784, "gal|gallon|gallons", "l"),
        // speed (m/s)
        u("mph", "speed", 0.44704, "mph|mi/h|miles per hour", "km/h,m/s"),
        u("km/h", "speed", 1.0 / 3.6, "km/h|kmh|kph|kmph|kilometers per hour|kilometres per hour", "mph,m/s"),
        u("m/s", "speed", 1.0, "m/s|mps|meters per second|metres per second", "km/h,mph"),
        u("kn", "speed", 1852.0 / 3600.0, "kn|kt|kts|knot|knots", "km/h,mph"),
        // area (m2)
        u("m²", "area", 1.0, "m²|m2|sq m|sqm|square meter|square meters|square metre|square metres", "ft²"),
        u("km²", "area", 1e6, "km²|km2|sq km|sqkm|square kilometer|square kilometers|square kilometre|square kilometres", "ha,acre"),
        u("ha", "area", 10000.0, "ha|hectare|hectares", "acre,m²"),
        u("acre", "area", 4046.8564224, "acre|acres", "ha,m²"),
        u("ft²", "area", 0.09290304, "ft²|ft2|sq ft|sqft|square foot|square feet", "m²"),
        // pressure (Pa)
        u("kPa", "press", 1000.0, "kpa|kilopascal|kilopascals", "psi,bar"),
        u("bar", "press", 100000.0, "bar|bars", "psi,kPa"),
        u("psi", "press", 6894.757293168, "psi", "bar,kPa"),
        u("atm", "press", 101325.0, "atm|atmosphere|atmospheres", "bar,psi"),
        u("mmHg", "press", 133.322387415, "mmhg|mm hg", "kPa"),
        // temperature (degrees C)
        U("°C", "temp", { it }, { it },
            listOf("°c", "° c", "c", "celsius", "degc", "deg c", "degrees celsius", "degree celsius", "centigrade"),
            listOf("°F", "K")),
        U("°F", "temp", { (it - 32.0) * 5.0 / 9.0 }, { it * 9.0 / 5.0 + 32.0 },
            listOf("°f", "° f", "f", "fahrenheit", "degf", "deg f", "degrees fahrenheit", "degree fahrenheit"),
            listOf("°C", "K")),
        U("K", "temp", { it - 273.15 }, { it + 273.15 },
            listOf("kelvin", "kelvins"),
            listOf("°C", "°F")),
        // digital storage (bytes)
        u("KB", "data", 1e3, "kb|kilobyte|kilobytes", "KiB"),
        u("MB", "data", 1e6, "mb|megabyte|megabytes", "MiB,GB"),
        u("GB", "data", 1e9, "gb|gigabyte|gigabytes", "MB,GiB"),
        u("TB", "data", 1e12, "tb|terabyte|terabytes", "GB,TiB"),
        u("KiB", "data", 1024.0, "kib", "KB"),
        u("MiB", "data", 1048576.0, "mib", "MB,KiB"),
        u("GiB", "data", 1073741824.0, "gib", "GB,MiB"),
        u("TiB", "data", 1099511627776.0, "tib", "TB,GiB"),
        // duration (s)
        u("s", "time", 1.0, "sec|secs|second|seconds", "min"),
        u("min", "time", 60.0, "min|mins|minute|minutes", "h,s"),
        u("h", "time", 3600.0, "hr|hrs|hour|hours", "min,d"),
        u("d", "time", 86400.0, "day|days", "h,wk"),
        u("wk", "time", 604800.0, "wk|wks|week|weeks", "d,h")
    )

    private val bySymbol: Map<String, U> = units.associateBy { it.sym }
    private val byName: Map<String, U> = buildMap {
        units.forEach { unit -> unit.names.forEach { put(it, unit) } }
    }

    private val simple = Regex("""^\s*(-?\d[\d.,]*)\s*(\S.*?)\s*$""")
    private val feetInches = Regex("""(?i)^\s*(\d+)\s*(?:'|′|ft|feet|foot)\s*(\d+(?:\.\d+)?)\s*(?:"|″|in|inch|inches)?\s*$""")

    fun candidates(text: String, source: Source): List<Candidate> {
        feetInches.find(text)?.let { m ->
            val feet = m.groupValues[1].toDouble()
            val inches = m.groupValues[2].toDouble()
            if (inches < 12 || m.groupValues[0].contains("in", ignoreCase = true)) {
                return listOf(Candidate("units", 0.9) { buildHeight(feet, inches) })
            }
        }
        val m = simple.find(text) ?: return emptyList()
        val numberText = m.groupValues[1]
        val unitText = m.groupValues[2]
        val value = Fmt.parseNumber(numberText) ?: return emptyList()
        val norm = unitText.lowercase(Locale.ROOT).trimEnd('.').replace(Regex("""\s+"""), " ")
        val unit = byName[norm] ?: return emptyList()
        if (unit.dim != "temp" && value < 0) return emptyList()

        var score = 0.9
        // A bare "c" or "f" is only a temperature when typed tight ("30c") and selected, never copied.
        if (norm == "c" || norm == "f") {
            val tight = text.trim().length == numberText.length + unitText.length
            if (source != Source.SELECTION || !tight) return emptyList()
            score = 0.75
        }
        return listOf(Candidate("units", score) { build(value, unit) })
    }

    private fun build(value: Double, unit: U): InspectResult? {
        val base = unit.toBase(value)
        val outs = unit.targets.mapNotNull { sym -> bySymbol[sym]?.let { sym to it.fromBase(base) } }
        if (outs.isEmpty()) return null
        val (primarySym, primaryValue) = outs.first()
        val pill = "${Fmt.adaptive(primaryValue)} $primarySym"
        val input = "${Fmt.plain(value)} ${unit.sym}"

        val rows = mutableListOf("Input" to input)
        outs.forEach { (sym, v) -> rows.add("In $sym" to "${Fmt.adaptive(v)} $sym") }
        if (unit.dim == "time") composite(base)?.let { rows.add("As" to it) }

        return InspectResult(
            domain = "CONV",
            pill = pill,
            title = pill,
            subtitle = input,
            cardTitle = "Unit conversion",
            cardText = "$input = $pill",
            rows = rows,
            copyText = pill,
            iconName = "ic_inspect_convert",
            timeoutSeconds = 15
        )
    }

    private fun buildHeight(feet: Double, inches: Double): InspectResult {
        val totalIn = feet * 12 + inches
        val cm = totalIn * 2.54
        val pill = "${Fmt.adaptive(cm)} cm"
        val input = "${Fmt.plain(feet)} ft ${Fmt.plain(inches)} in"
        return InspectResult(
            domain = "CONV",
            pill = pill,
            title = pill,
            subtitle = input,
            cardTitle = "Height",
            cardText = "$input = $pill",
            rows = listOf(
                "Input" to input,
                "In cm" to pill,
                "In m" to "${Fmt.adaptive(cm / 100.0)} m",
                "In inches" to "${Fmt.adaptive(totalIn)} in"
            ),
            copyText = pill,
            iconName = "ic_inspect_convert",
            timeoutSeconds = 15
        )
    }

    /** 5400 s -> "1h 30m". */
    private fun composite(seconds: Double): String? {
        var s = seconds.toLong()
        if (s < 60) return null
        val days = s / 86400; s %= 86400
        val hours = s / 3600; s %= 3600
        val mins = s / 60; s %= 60
        val parts = mutableListOf<String>()
        if (days > 0) parts.add("${days}d")
        if (hours > 0) parts.add("${hours}h")
        if (mins > 0) parts.add("${mins}m")
        if (s > 0 && days == 0L) parts.add("${s}s")
        return parts.joinToString(" ")
    }
}
