package com.microtag.ui

import android.content.Context
import android.os.PowerManager
import androidx.core.content.edit
import java.util.Calendar

/** Preference keys for the tiles that don't already have a MicrotagPrefs.KEY_* constant. */
object TagKeys {
    const val UPI = "tag_upi_credit"
    const val PRINT = "tag_print_progress"
    const val STREAKS = "tag_streaks"
    const val CALENDAR = "tag_calendar_countdown"
    const val SOUND_MODE = "tag_sound_mode"
    const val BLUETOOTH = "tag_bluetooth"
    const val SONG_NEXT = "tag_song_up_next"
    const val AMBIENT_MUSIC = "tag_ambient_music_mod"
    const val UPLOADS = "tag_uploads"
    const val WEATHER = "tag_weather"
}

/** Values behind the chevron tiles. On/off lives in MicrotagPrefs; "how" lives here. */
class TagSettings(context: Context) {
    private val appContext = context.applicationContext
    private val sp = context.getSharedPreferences("tag_settings", Context.MODE_PRIVATE)

    /** Master pause switch for all interceptions. */
    var paused: Boolean
        get() = sp.getBoolean("master_paused", false)
        set(v) = sp.edit { putBoolean("master_paused", v) }

    /** Weather refresh interval in minutes. */
    var weatherIntervalMin: Int
        get() = sp.getInt("weather_interval_min", 60)
        set(v) = sp.edit { putInt("weather_interval_min", v) }

    /** "pace", "time" or "distance". */
    var stravaMetric: String
        get() = sp.getString("strava_metric", "pace") ?: "pace"
        set(v) = sp.edit { putString("strava_metric", v) }

    /** Which ringer modes announce themselves: "ring", "vibrate", "silent". */
    var ringerModes: Set<String>
        get() = sp.getStringSet("ringer_modes", setOf("vibrate", "silent"))?.toSet() ?: emptySet()
        set(v) = sp.edit { putStringSet("ringer_modes", v) }

    /** Package names whose streak notifications get promoted. */
    var streakApps: Set<String>
        get() = sp.getStringSet("streak_apps", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit { putStringSet("streak_apps", v) }

    /** How long before an event the countdown chip appears. */
    var calendarLeadMin: Int
        get() = sp.getInt("calendar_lead_min", 30)
        set(v) = sp.edit { putInt("calendar_lead_min", v) }

    /** Calendar IDs that are ignored. Empty means every calendar counts. */
    var calendarExcluded: Set<String>
        get() = sp.getStringSet("calendar_excluded", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit { putStringSet("calendar_excluded", v) }

    /** Swap the lime for the system accent colour. */
    var materialYou: Boolean
        get() = sp.getBoolean("material_you", false)
        set(v) = sp.edit { putBoolean("material_you", v) }

    var reducedMotion: Boolean
        get() = sp.getBoolean("reduced_motion", false)
        set(v) = sp.edit { putBoolean("reduced_motion", v) }

    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = sp.edit { putBoolean("haptics", v) }

    /** Micrographics: tiny marks, rules and annotations around the UI. */
    var micro: Boolean
        get() = sp.getBoolean("micro", true)
        set(v) = sp.edit { putBoolean("micro", v) }

    /** Pure black base on dark palettes. */
    var amoled: Boolean
        get() = sp.getBoolean("amoled", false)
        set(v) = sp.edit { putBoolean("amoled", v) }

    /** Weather location: follow the device (true) or use [weatherCity]. */
    var weatherAuto: Boolean
        get() = sp.getBoolean("weather_auto", true)
        set(v) = sp.edit { putBoolean("weather_auto", v) }

    var weatherCity: String
        get() = sp.getString("weather_city", "") ?: ""
        set(v) = sp.edit { putString("weather_city", v) }

    /** "native", "light", "dark" or "system" (see Appearance). */
    var appearance: String
        get() = sp.getString("appearance", "native") ?: "native"
        set(v) = sp.edit { putString("appearance", v) }

    /** Give neighbouring dashboard tiles different accents (needs a palette with more than one). */
    var multicolour: Boolean
        get() = sp.getBoolean("multicolour", false)
        set(v) = sp.edit { putBoolean("multicolour", v) }

    /** Id of the chosen TagPalette ("lime", "atelier", "rouge", "vermilion", "sage"). */
    var palette: String
        get() = sp.getString("palette", "lime") ?: "lime"
        set(v) = sp.edit { putString("palette", v) }

    // ---- Battery ------------------------------------------------------------------------------

    /** Keep TAG quiet overnight. OTP codes are exempt; see quietReason(). */
    var nightPause: Boolean
        get() = sp.getBoolean("night_pause", false)
        set(v) = sp.edit { putBoolean("night_pause", v) }

    /** Minutes after midnight. Defaults to 23:00. */
    var nightStartMin: Int
        get() = sp.getInt("night_start_min", 23 * 60)
        set(v) = sp.edit { putInt("night_start_min", v) }

    /** Minutes after midnight. Defaults to 06:00. */
    var nightEndMin: Int
        get() = sp.getInt("night_end_min", 6 * 60)
        set(v) = sp.edit { putInt("night_end_min", v) }

    /** Stay quiet while Android's Battery Saver is on. */
    var pauseOnBatterySaver: Boolean
        get() = sp.getBoolean("pause_battery_saver", false)
        set(v) = sp.edit { putBoolean("pause_battery_saver", v) }

    /** True inside the night window. The window may cross midnight (23:00 to 06:00). */
    fun inNightHours(minuteOfDay: Int = currentMinuteOfDay()): Boolean {
        if (!nightPause) return false
        val s = nightStartMin
        val e = nightEndMin
        return when {
            s == e -> false
            s < e -> minuteOfDay in s until e
            else -> minuteOfDay >= s || minuteOfDay < e
        }
    }

    /** Why TAG should stay silent right now ("night" or "battery saver"), or null when it shouldn't. */
    fun quietReason(): String? = when {
        inNightHours() -> "night"
        pauseOnBatterySaver && (appContext.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode ->
            "battery saver"
        else -> null
    }

    companion object {
        /** Changing any of these should make the listener re-evaluate straight away. */
        val QUIET_KEYS = setOf("night_pause", "night_start_min", "night_end_min", "pause_battery_saver")

        private fun currentMinuteOfDay(): Int {
            val c = Calendar.getInstance()
            return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        }
    }
}

/**
 * Applies every look-and-feel setting: palette, appearance, multicolour, micrographics and AMOLED.
 * MainActivity calls it, and so must any activity that can start without MainActivity ever running
 * (the Inspect card and image activities), because TagColors lives in the process, not in the activity.
 */
fun TagColors.applySettings(context: Context, settings: TagSettings) {
    // Material You used to be a switch laid over any palette. It is a palette of its own now.
    if (settings.materialYou) { settings.palette = "material"; settings.materialYou = false }
    usePalette(context, settings.palette)
    useMulticolour(settings.multicolour)
    useAppearance(settings.appearance)
    useMicro(settings.micro)
    useAmoled(settings.amoled)
}
