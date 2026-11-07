package com.microtag.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.microtag.inspect.Http
import org.json.JSONObject

/** The weather source: Open-Meteo (free, no key). Blocking calls: use them off the main thread. */
object WeatherEngine {
    private const val PREFS = "microtag_weather"
    private const val KEY_PLACE = "place"
    private const val KEY_LAST = "last_json"
    private const val KEY_LAST_AT = "last_at"
    private const val KEY_AUTO_LAT = "auto_lat"
    private const val KEY_AUTO_LON = "auto_lon"
    private const val MIN_GAP_MS = 10 * 60 * 1000L

    sealed class Result {
        class Ok(val weather: Weather, val place: Place?, val fahrenheit: Boolean) : Result()
        object NeedsLocation : Result()
        object NoPlace : Result()
        object Offline : Result()
    }

    // ---- settings the dashboard reads and writes

    fun place(context: Context): WeatherPlace =
        WeatherPlaceCodec.decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PLACE, null))

    private fun save(context: Context, p: WeatherPlace) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PLACE, WeatherPlaceCodec.encode(p)).remove(KEY_LAST_AT).apply()
    }

    fun setAuto(context: Context) = save(context, place(context).let { WeatherPlace(true, it.city, it.units) })
    fun setCity(context: Context, city: Place) = save(context, WeatherPlace(false, city, place(context).units))
    fun setUnits(context: Context, units: Units) = save(context, place(context).let { WeatherPlace(it.auto, it.city, units) })

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The city search behind the dashboard's city picker. */
    fun search(query: String): List<Place> {
        if (query.trim().length < 2) return emptyList()
        val r = Http.get(WeatherUrls.search(query), timeoutMs = 5000)
        return if (r.ok) WeatherParse.places(r.body) else emptyList()
    }

    /** The last answer, for showing something at once. */
    fun cached(context: Context): Weather? {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = sp.getString(KEY_LAST, null) ?: return null
        return WeatherParse.forecast(json, sp.getLong(KEY_LAST_AT, 0L))
    }

    /** Fresh weather for the chosen place, or the cached answer if it is younger than ten minutes and [force] is off. */
    fun refresh(context: Context, force: Boolean = false, now: Long = System.currentTimeMillis()): Result {
        val ctx = context.applicationContext
        val p = place(ctx)
        val units = p.units

        val lat: Double
        val lon: Double
        val city: Place?
        if (p.auto) {
            val loc = lastKnown(ctx)
            if (loc != null) {
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_AUTO_LAT, loc.latitude.toString()).putString(KEY_AUTO_LON, loc.longitude.toString()).apply()
                lat = loc.latitude; lon = loc.longitude
            } else {
                if (!hasLocationPermission(ctx)) return Result.NeedsLocation
                val kept = storedAuto(ctx) ?: return Result.Offline
                lat = kept.first; lon = kept.second
            }
            city = null
        } else {
            val c = p.city ?: return Result.NoPlace
            lat = c.lat; lon = c.lon; city = c
        }

        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val country = city?.country?.let { countryCode(it) } ?: java.util.Locale.getDefault().country
        val f = WeatherFormat.useFahrenheit(units, country)
        val cached = cached(ctx)
        if (!force && cached != null && now - cached.fetchedAt < MIN_GAP_MS) return Result.Ok(cached, city, f)

        val r = Http.get(WeatherUrls.forecast(lat, lon), timeoutMs = 6000)
        val w = if (r.ok) WeatherParse.forecast(r.body, now) else null
        if (w == null) return cached?.let { Result.Ok(it, city, f) } ?: Result.Offline
        sp.edit().putString(KEY_LAST, r.body).putLong(KEY_LAST_AT, now).apply()
        return Result.Ok(w, city, f)
    }

    private fun storedAuto(ctx: Context): Pair<Double, Double>? {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val la = sp.getString(KEY_AUTO_LAT, null)?.toDoubleOrNull(); val lo = sp.getString(KEY_AUTO_LON, null)?.toDoubleOrNull()
        return if (la != null && lo != null) la to lo else null
    }

    @Suppress("MissingPermission")
    private fun lastKnown(ctx: Context): Location? {
        if (!hasLocationPermission(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /** Geocoding gives a country name; only the few that use Fahrenheit matter. */
    private fun countryCode(name: String): String = when (name.lowercase()) {
        "united states", "united states of america", "usa" -> "US"
        "bahamas" -> "BS"; "belize" -> "BZ"; "cayman islands" -> "KY"; "liberia" -> "LR"
        else -> ""
    }
}
