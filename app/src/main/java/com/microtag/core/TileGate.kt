package com.microtag.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.microtag.rules.ApertureRule
import com.microtag.ui.TagKeys
import com.microtag.ui.TagSettings

/**
 * The one place that decides whether a dashboard tile lets something through.
 * Tile identity is a MicrotagPrefs key, the same string the dashboard toggles.
 */
object TileGate {

    /** Short names used in custom_rules.json -> prefs keys. */
    fun keyFor(name: String?): String? = when (name?.trim()?.lowercase()) {
        "streaks" -> TagKeys.STREAKS
        "upi" -> TagKeys.UPI
        "print" -> TagKeys.PRINT
        "calendar" -> TagKeys.CALENDAR
        "weather" -> TagKeys.WEATHER
        "downloads" -> MicrotagPrefs.KEY_DOWNLOADS
        "uploads" -> TagKeys.UPLOADS
        "otp" -> MicrotagPrefs.KEY_OTP
        "strava" -> MicrotagPrefs.KEY_STRAVA
        "discord" -> MicrotagPrefs.KEY_DISCORD
        "proton" -> MicrotagPrefs.KEY_PROTON
        "clipboard" -> MicrotagPrefs.KEY_CLIPBOARD
        "bluetooth" -> TagKeys.BLUETOOTH
        "sound" -> TagKeys.SOUND_MODE
        "song" -> TagKeys.SONG_NEXT
        "ambient" -> TagKeys.AMBIENT_MUSIC
        else -> null
    }

    /** A null tile means no tile owns the source, so nothing can switch it off. */
    fun isOn(prefs: MicrotagPrefs, tile: String?): Boolean = tile == null || prefs.isAppEnabled(tile)

    /**
     * Gate for a custom rule. Runs before the rule's regex.
     *  - the tile must be on
     *  - Streaks: the rule's app must be one the user picked
     *  - Calendar: notification rules are only a fallback for when the calendar
     *    itself can't be read (CalendarCountdown handles it otherwise)
     */
    fun ruleAllowed(
        context: Context,
        prefs: MicrotagPrefs,
        settings: TagSettings,
        rule: ApertureRule
    ): Boolean {
        val tile = rule.tile ?: return true
        if (!prefs.isAppEnabled(tile)) return false
        return when (tile) {
            TagKeys.STREAKS -> settings.streakApps.any { it.equals(rule.packageName, ignoreCase = true) }
            TagKeys.CALENDAR -> !hasCalendarAccess(context)
            else -> true
        }
    }

    private fun hasCalendarAccess(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
}
