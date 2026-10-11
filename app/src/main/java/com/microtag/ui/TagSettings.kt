package com.microtag.ui

import android.content.Context
import androidx.core.content.edit

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
    private val sp = context.getSharedPreferences("tag_settings", Context.MODE_PRIVATE)

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
}
