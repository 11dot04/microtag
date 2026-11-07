package com.microtag.weather

import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

/** A place the weather is for. */
data class Place(val name: String, val region: String, val country: String, val lat: Double, val lon: Double) {
    val label: String get() = listOf(name, region).filter { it.isNotBlank() }.distinct().joinToString(", ")
}

data class Weather(
    val tempC: Double,
    val feelsC: Double,
    val code: Int,
    val isDay: Boolean,
    val humidity: Int,
    val windKmh: Double,
    val precipMm: Double,
    val highC: Double?,
    val lowC: Double?,
    /** Highest chance of rain today, percent. */
    val rainChanceToday: Int?,
    /** Chance of rain for the next few hours, starting now. */
    val rainNextHours: List<Int>,
    val fetchedAt: Long
)

enum class Units { AUTO, CELSIUS, FAHRENHEIT }

/** WMO weather codes as Open-Meteo reports them. */
object WeatherCodes {
    fun label(code: Int, isDay: Boolean = true): String = when (code) {
        0 -> if (isDay) "Clear" else "Clear night"
        1 -> if (isDay) "Mostly clear" else "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61 -> "Light rain"
        63 -> "Rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71 -> "Light snow"
        73 -> "Snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80 -> "Light showers"
        81 -> "Showers"
        82 -> "Violent showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunder, hail"
        else -> "Weather"
    }

    /** A short key for the icon: the UI maps it to a drawable. */
    fun icon(code: Int, isDay: Boolean): String = when (code) {
        0, 1 -> if (isDay) "sun" else "moon"
        2 -> if (isDay) "partly" else "partly_night"
        3 -> "cloud"
        45, 48 -> "fog"
        in 51..57 -> "drizzle"
        in 61..67, in 80..82 -> "rain"
        in 71..77, 85, 86 -> "snow"
        in 95..99 -> "storm"
        else -> "cloud"
    }

    fun isWet(code: Int) = code in 51..67 || code in 80..82 || code in 95..99
}

object WeatherParse {
    /** Open-Meteo's "current" + "daily" + "hourly" answer, or null if it is not that shape. */
    fun forecast(json: String, now: Long): Weather? = runCatching {
        val o = JSONObject(json)
        val cur = o.optJSONObject("current") ?: return null
        if (!cur.has("temperature_2m")) return null
        val daily = o.optJSONObject("daily")
        fun first(key: String): Double? = daily?.optJSONArray(key)?.let { if (it.length() > 0) it.optDouble(0, Double.NaN).takeIf { v -> !v.isNaN() } else null }
        val hourly = o.optJSONObject("hourly")?.optJSONArray("precipitation_probability")
        val probs = (0 until (hourly?.length() ?: 0)).map { hourly!!.optInt(it, 0) }
        Weather(
            tempC = cur.optDouble("temperature_2m", 0.0),
            feelsC = cur.optDouble("apparent_temperature", cur.optDouble("temperature_2m", 0.0)),
            code = cur.optInt("weather_code", -1),
            isDay = cur.optInt("is_day", 1) == 1,
            humidity = cur.optInt("relative_humidity_2m", 0),
            windKmh = cur.optDouble("wind_speed_10m", 0.0),
            precipMm = cur.optDouble("precipitation", 0.0),
            highC = first("temperature_2m_max"),
            lowC = first("temperature_2m_min"),
            rainChanceToday = first("precipitation_probability_max")?.roundToInt(),
            rainNextHours = probs,
            fetchedAt = now
        )
    }.getOrNull()

    fun places(json: String): List<Place> = runCatching {
        val arr = JSONObject(json).optJSONArray("results") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val r = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = r.optString("name"); if (name.isBlank() || !r.has("latitude") || !r.has("longitude")) return@mapNotNull null
            Place(name, r.optString("admin1"), r.optString("country"), r.optDouble("latitude", 0.0), r.optDouble("longitude", 0.0))
        }
    }.getOrElse { emptyList() }
}

object WeatherUrls {
    fun forecast(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${"%.3f".format(Locale.US, lat)}&longitude=${"%.3f".format(Locale.US, lon)}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,is_day,precipitation,weather_code,wind_speed_10m" +
            "&hourly=precipitation_probability&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&forecast_days=1&forecast_hours=6&timezone=auto"

    fun search(query: String): String =
        "https://geocoding-api.open-meteo.com/v1/search?name=${java.net.URLEncoder.encode(query.trim(), "UTF-8")}&count=6&language=en&format=json"
}

/** Words and numbers for the pill and the card. */
object WeatherFormat {
    private val fahrenheitCountries = setOf("US", "BS", "BZ", "KY", "LR", "PW", "FM", "MH")

    fun useFahrenheit(units: Units, countryCode: String): Boolean = when (units) {
        Units.CELSIUS -> false
        Units.FAHRENHEIT -> true
        Units.AUTO -> countryCode.uppercase() in fahrenheitCountries
    }

    fun temp(c: Double, f: Boolean): Int = (if (f) c * 9.0 / 5.0 + 32.0 else c).roundToInt()

    /** "27°": the whole pill. */
    fun pill(w: Weather, f: Boolean): String = "${temp(w.tempC, f)}°"

    fun title(w: Weather, place: String?): String {
        val c = WeatherCodes.label(w.code, w.isDay)
        return if (place.isNullOrBlank()) c else "$c · $place"
    }

    /** "Feels 30° · H 31° L 24° · Rain 40%". Parts with nothing to say are left out. */
    fun content(w: Weather, f: Boolean): String {
        val parts = ArrayList<String>()
        if (temp(w.feelsC, f) != temp(w.tempC, f)) parts.add("Feels ${temp(w.feelsC, f)}°")
        if (w.highC != null && w.lowC != null) parts.add("H ${temp(w.highC, f)}° L ${temp(w.lowC, f)}°")
        rainSoon(w)?.let { parts.add(it) } ?: w.rainChanceToday?.takeIf { it >= 20 }?.let { parts.add("Rain $it%") }
        if (parts.isEmpty()) parts.add("${w.humidity}% humidity")
        return parts.joinToString(" · ")
    }

    /** "Rain likely in 2h" when the chance climbs past 60% within the next hours and it is not raining now. */
    fun rainSoon(w: Weather): String? {
        if (WeatherCodes.isWet(w.code) || w.precipMm > 0.1) return null
        val i = w.rainNextHours.indexOfFirst { it >= 60 }
        return when {
            i < 0 -> null
            i <= 0 -> "Rain likely now"
            else -> "Rain likely in ${i}h"
        }
    }

    /** True once the data is older than [maxAgeMs]. */
    fun stale(w: Weather?, now: Long, maxAgeMs: Long): Boolean = w == null || now - w.fetchedAt > maxAgeMs
}

/** Where weather is for, kept as plain values so the rules around it are tested on the JVM. */
class WeatherPlace(
    val auto: Boolean = true,
    val city: Place? = null,
    val units: Units = Units.AUTO
)

object WeatherPlaceCodec {
    fun encode(p: WeatherPlace): String = listOf(
        if (p.auto) "1" else "0", p.units.name,
        p.city?.name.orEmpty(), p.city?.region.orEmpty(), p.city?.country.orEmpty(),
        p.city?.lat?.toString().orEmpty(), p.city?.lon?.toString().orEmpty()
    ).joinToString("\t")

    fun decode(s: String?): WeatherPlace {
        if (s.isNullOrBlank()) return WeatherPlace()
        val f = s.split('\t')
        if (f.size < 7) return WeatherPlace()
        val lat = f[5].toDoubleOrNull(); val lon = f[6].toDoubleOrNull()
        val city = if (f[2].isNotBlank() && lat != null && lon != null) Place(f[2], f[3], f[4], lat, lon) else null
        return WeatherPlace(f[0] == "1", city, Units.values().firstOrNull { it.name == f[1] } ?: Units.AUTO)
    }
}
