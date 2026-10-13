package com.microtag.listener

import android.app.Notification
import android.app.PendingIntent
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.microtag.core.MicrotagLog
import com.microtag.core.MicrotagLog.Decision
import com.microtag.core.MicrotagPrefs
import com.microtag.core.MicrotagReminder
import com.microtag.core.TileGate
import com.microtag.calendar.CalendarCountdown
import com.microtag.rules.RuleEngine
import com.microtag.shizuku.ShizukuBridge
import com.microtag.ui.TagKeys
import com.microtag.ui.TagSettings
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class MicrotagNotificationService : NotificationListenerService() {

    private lateinit var prefs: MicrotagPrefs
    private lateinit var settings: TagSettings
    private val handler = Handler(Looper.getMainLooper())

    // sbn.key -> owning dashboard tile / source package of the capsule we posted for it
    private val tiles = ConcurrentHashMap<String, String>()
    private val pkgs = ConcurrentHashMap<String, String>()
    // sbn.key -> pending "come back after the refresh interval" task
    private val deferred = ConcurrentHashMap<String, Runnable>()
    // sbn.key -> when the pill text last changed (drives the refresh interval, unlike lastShownAt)
    private val pillChangedAt = ConcurrentHashMap<String, Long>()

    // Held in a field on purpose: SharedPreferences keeps listeners weakly.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        handler.post { onPrefChanged(key) }
    }

    private var ringerReceiverRegistered = false
    private var lastRingerMode = -1
    private val ringerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            onRingerChanged()
        }
    }

    // sbn.key -> signature of what we last showed (skip identical re-posts)
    private val lastShown = ConcurrentHashMap<String, String>()
    // sbn.key -> last title/content we showed, and when we last posted (for throttling)
    private val lastContent = ConcurrentHashMap<String, String>()
    private val lastShownAt = ConcurrentHashMap<String, Long>()
    // sbn.key -> capsule notification id we posted for it
    private val capsuleIds = ConcurrentHashMap<String, Int>()
    // keys whose capsule expires by itself (timeoutSeconds > 0)
    private val timedKeys = ConcurrentHashMap.newKeySet<String>()

    private val pollTask = object : Runnable {
        override fun run() {
            pollActiveNotifications()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = MicrotagPrefs(applicationContext)
        settings = TagSettings(applicationContext)
        MicrotagLog.init(applicationContext)
        RuleEngine.init(applicationContext)
        CountryCodes.init(applicationContext)
        getSharedPreferences("microtag_prefs", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)
        getSharedPreferences("tag_settings", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Listener connected")
        handler.removeCallbacks(pollTask)
        handler.post(pollTask)
        startSideSources()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener disconnected, requesting rebind")
        handler.removeCallbacks(pollTask)
        try {
            requestRebind(ComponentName(this, MicrotagNotificationService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Rebind failed", e)
        }
    }

    override fun onDestroy() {
        getSharedPreferences("microtag_prefs", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
        getSharedPreferences("tag_settings", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
        handler.removeCallbacksAndMessages(null)
        setRingerWatching(false)
        CalendarCountdown.stop()
        super.onDestroy()
    }

    // ---- Sources that don't come from a notification --------------------------------------------

    private fun startSideSources() {
        ShizukuBridge.init(applicationContext)
        CalendarCountdown.start(applicationContext)
        setRingerWatching(prefs.isAppEnabled(TagKeys.SOUND_MODE))
    }

    private fun setRingerWatching(on: Boolean) {
        if (on == ringerReceiverRegistered) return
        if (on) {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            lastRingerMode = am.ringerMode // sticky broadcast would replay this; only changes count
            ContextCompat.registerReceiver(
                this, ringerReceiver, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            try { unregisterReceiver(ringerReceiver) } catch (_: Exception) {}
            MicrotagReminder.cancelCapsule(applicationContext, RINGER_CAPSULE_ID)
        }
        ringerReceiverRegistered = on
    }

    private fun onRingerChanged() {
        if (!prefs.isAppEnabled(TagKeys.SOUND_MODE)) return
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val mode = am.ringerMode
        if (mode == lastRingerMode) return
        lastRingerMode = mode

        val (name, pill, icon) = when (mode) {
            AudioManager.RINGER_MODE_NORMAL -> Triple("ring", "Ring", "ic_ringer_ring")
            AudioManager.RINGER_MODE_VIBRATE -> Triple("vibrate", "Vibrate", "ic_ringer_vibrate")
            else -> Triple("silent", "Silent", "ic_ringer_silent")
        }
        if (name !in settings.ringerModes) {
            MicrotagReminder.cancelCapsule(applicationContext, RINGER_CAPSULE_ID)
            return
        }
        prefs.incrementInterceptCount()
        MicrotagReminder.showCapsule(
            context = applicationContext,
            pillText = pill,
            title = "Sound mode",
            content = pill,
            notificationId = RINGER_CAPSULE_ID,
            timeoutSeconds = 4,
            iconName = icon,
            contentIntent = settingsIntent(Settings.ACTION_SOUND_SETTINGS, 7103)
        )
    }

    // ---- Dashboard changes take effect immediately ----------------------------------------------

    private fun onPrefChanged(key: String?) {
        when {
            key == null -> {
                startSideSources()
                pollActiveNotifications()
            }
            key.startsWith("app_toggle_") -> {
                val tile = key.removePrefix("app_toggle_")
                val enabled = prefs.isAppEnabled(tile)
                // Turning a tile off ends its live capsule, not just future ones.
                if (!enabled) clearCapsulesOfTile(tile)
                when (tile) {
                    MicrotagPrefs.KEY_CLIPBOARD -> ShizukuBridge.refresh()
                    TagKeys.CALENDAR -> CalendarCountdown.refresh()
                    TagKeys.SOUND_MODE -> setRingerWatching(enabled)
                }
                if (enabled) pollActiveNotifications()
            }
            key == "streak_apps" -> {
                val picked = settings.streakApps
                val stale = tiles.filter { it.value == TagKeys.STREAKS }.keys
                    .filter { k -> picked.none { it.equals(pkgs[k], ignoreCase = true) } }
                stale.forEach(::clearCapsule)
                pollActiveNotifications()
            }
            key == "weather_interval_min" -> {
                cancelAllDeferred()
                pollActiveNotifications()
            }
            key == "strava_metric" -> {
                // Force a re-evaluation so the chip switches metric right away.
                tiles.filter { it.value == MicrotagPrefs.KEY_STRAVA }.keys.forEach {
                    lastShown.remove(it); lastContent.remove(it)
                }
                pollActiveNotifications()
            }
            key == "ringer_modes" -> {
                lastRingerMode = -1
                if (ringerReceiverRegistered) {
                    val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    lastRingerMode = am.ringerMode
                }
            }
            key == "calendar_lead_min" || key == "calendar_excluded" -> CalendarCountdown.refresh()
        }
    }

    private fun clearCapsulesOfTile(tile: String) {
        tiles.filter { it.value == tile }.keys.forEach(::clearCapsule)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        try {
            process(sbn)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process ${sbn.key}", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null || sbn.packageName == packageName) return

        val id = capsuleIds[sbn.key]
        if (id != null) MicrotagReminder.cancelTicker(id)
        cancelDeferred(sbn.key)

        // Timed capsules (OTP, songs...) expire on their own; everything else follows the source.
        if (!timedKeys.contains(sbn.key)) {
            if (capsuleIds.containsKey(sbn.key)) {
                logDecision(sbn, "REMOVED", Decision.CLEARED, "source notification removed")
            }
            clearCapsule(sbn.key)
        } else {
            lastShown.remove(sbn.key)
            capsuleIds.remove(sbn.key)
            timedKeys.remove(sbn.key)
            tiles.remove(sbn.key)
            pkgs.remove(sbn.key)
            pillChangedAt.remove(sbn.key)
        }
    }

    // Some apps update in ways that never trigger a fresh post event, so re-check ongoing ones.
    private fun pollActiveNotifications() {
        val active = try {
            activeNotifications
        } catch (e: Exception) {
            Log.w(TAG, "Poll failed reading active notifications", e)
            null
        } ?: return

        for (sbn in active) {
            if (sbn.packageName == packageName || !sbn.isOngoing) continue
            try {
                process(sbn)
            } catch (e: Exception) {
                Log.e(TAG, "Poll failed for ${sbn.key}", e)
            }
        }
    }

    private fun process(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return // never react to our own capsules
        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val id = capsuleIdFor(sbn.key)

        // 1. Dynamic rules (custom_rules.json). The gate runs before each rule's regex, so a rule
        //    that is off (tile off, app not picked for streaks) is never matched against the text.
        val match = RuleEngine.evaluate(pkg, title, text) { rule ->
            TileGate.ruleAllowed(applicationContext, prefs, settings, rule)
        }
        if (match != null) {
            val signature = "${match.pillText}|$title|$text|${match.iconName}"
            if (lastShown[sbn.key] == signature) {
                logDecision(sbn, "RULE", Decision.UNCHANGED, "rule match, same as last shown",
                    match.pillText, match.iconName)
                return
            }
            if (throttled(sbn, match.tile)) {
                logDecision(sbn, "RULE", Decision.UNCHANGED, "held back by refresh interval",
                    match.pillText, match.iconName)
                return
            }
            Log.d(TAG, "RULE   $pkg pill='${match.pillText}' icon=${match.iconName}")
            logDecision(sbn, "RULE", Decision.PROMOTED, "custom rule matched",
                match.pillText, match.iconName)
            lastShown[sbn.key] = signature
            lastShownAt[sbn.key] = System.currentTimeMillis()
            pillChangedAt[sbn.key] = System.currentTimeMillis()
            capsuleIds[sbn.key] = id
            remember(sbn.key, pkg, match.tile)
            trackTimeout(sbn.key, match.timeoutSeconds)
            prefs.incrementInterceptCount()
            MicrotagReminder.showCapsule(
                context = applicationContext,
                pillText = match.pillText,
                title = title.ifBlank { MicrotagReminder.appLabel(applicationContext, pkg) },
                content = text,
                notificationId = id,
                timeoutSeconds = match.timeoutSeconds,
                iconName = match.iconName,
                sourcePackage = pkg,
                sourceIntent = notif.contentIntent
            )
            return
        }

        // 2. Capsule parser. The owning tile is known from package/extras alone, so an off tile
        //    never reaches the parser.
        val routedTile = CapsuleParser.tileFor(pkg, extras)
        if (!TileGate.isOn(prefs, routedTile)) {
            if (capsuleIds.containsKey(sbn.key)) clearCapsule(sbn.key)
            logDecision(sbn, "PARSER", Decision.SKIPPED, "tile is off in dashboard")
            return
        }

        val parsed = CapsuleParser.parse(
            sbn,
            ParseContext(
                stravaMetric = settings.stravaMetric,
                calendarProviderActive = CalendarCountdown.isActive(applicationContext),
                calendarLeadMin = settings.calendarLeadMin
            )
        )
        if (parsed != null) {
            // System UI notifications only get their tile after parsing (bluetooth / DND).
            if (!TileGate.isOn(prefs, parsed.tile)) {
                logDecision(sbn, "PARSER", Decision.SKIPPED, "tile is off in dashboard",
                    parsed.pillText, parsed.iconName)
                return
            }

            // A pill/icon change posts immediately. Title/content-only changes (e.g. Strava's
            // per-second timer) are throttled so we don't re-post the capsule every second.
            val headline = "${parsed.pillText}|${parsed.iconName}"
            val content = "${parsed.title}|${parsed.content}"
            val now = System.currentTimeMillis()
            val headlineSame = lastShown[sbn.key] == headline
            val contentSame = lastContent[sbn.key] == content
            val recentlyPosted = now - (lastShownAt[sbn.key] ?: 0L) < CONTENT_REFRESH_MS
            if (headlineSame && (contentSame || recentlyPosted)) {
                logDecision(sbn, "PARSER", Decision.UNCHANGED,
                    if (contentSame) "same as last shown" else "same pill, content refresh throttled",
                    parsed.pillText, parsed.iconName)
                return
            }
            if (!headlineSame && throttled(sbn, parsed.tile)) {
                logDecision(sbn, "PARSER", Decision.UNCHANGED, "held back by refresh interval",
                    parsed.pillText, parsed.iconName)
                return
            }
            Log.d(TAG, "PARSER $pkg pill='${parsed.pillText}' icon=${parsed.iconName}")
            logDecision(sbn, "PARSER", Decision.PROMOTED,
                if (headlineSame) "content refresh" else "parser produced a capsule",
                parsed.pillText, parsed.iconName)
            lastShown[sbn.key] = headline
            lastContent[sbn.key] = content
            lastShownAt[sbn.key] = now
            // A content-only refresh must not restart the weather interval.
            if (!headlineSame) pillChangedAt[sbn.key] = now
            capsuleIds[sbn.key] = id
            remember(sbn.key, pkg, parsed.tile)
            trackTimeout(sbn.key, parsed.timeoutSeconds)

            prefs.incrementInterceptCount()
            // System capsules (Bluetooth, DND, battery) open the matching settings page; every other
            // capsule names its app and opens what the original notification would have opened.
            val isSystem = pkg == "com.android.bluetooth" || pkg == "com.android.systemui"
            MicrotagReminder.showCapsule(
                context = applicationContext,
                pillText = parsed.pillText,
                title = parsed.title,
                content = parsed.content,
                notificationId = id,
                timeoutSeconds = parsed.timeoutSeconds,
                iconName = parsed.iconName,
                customIcon = null,
                detailPayload = parsed.payload,
                chronometerTargetMillis = parsed.chronometerTargetMillis,
                actions = parsed.actions,
                sourcePackage = if (isSystem) null else pkg,
                sourceIntent = if (isSystem) systemTap(pkg, "${parsed.title} ${parsed.content}") else notif.contentIntent
            )
            return
        }

        // 3. Simple fallbacks (Teams / Maps)
        val handled = when {
            pkg == "com.microsoft.teams" && prefs.isAppEnabled(MicrotagPrefs.KEY_TEAMS) ->
                handleTeams(sbn, id, title, text)
            pkg == "com.google.android.apps.maps" && prefs.isAppEnabled(MicrotagPrefs.KEY_MAPS) ->
                handleMaps(sbn, id, title, text)
            else -> false
        }
        if (handled) return

        // The source stopped matching anything (VPN disconnected, call ended, workout stopped...)
        if (lastShown.containsKey(sbn.key) && !timedKeys.contains(sbn.key)) {
            Log.d(TAG, "CLEAR  $pkg (no longer matches)")
            logDecision(sbn, "PARSER", Decision.CLEARED, "source no longer matches any rule or parser")
            clearCapsule(sbn.key)
        } else {
            logDecision(sbn, "NONE", Decision.IGNORED, "no rule, parser or fallback matched")
        }
    }

    private fun remember(key: String, pkg: String, tile: String?) {
        pkgs[key] = pkg
        if (tile != null) tiles[key] = tile else tiles.remove(key)
    }

    // ---- Refresh interval (weather) -------------------------------------------------------------

    private fun intervalMsFor(tile: String?): Long =
        if (tile == TagKeys.WEATHER) settings.weatherIntervalMin * 60_000L else 0L

    /**
     * True when this tile only updates every N minutes and the last update was more recent than that.
     * The first capsule for a notification shows immediately; later changes wait for the interval.
     * A deferred re-check is scheduled so the update still lands when the interval is up.
     */
    private fun throttled(sbn: StatusBarNotification, tile: String?): Boolean {
        val interval = intervalMsFor(tile)
        if (interval <= 0L) return false
        if (!capsuleIds.containsKey(sbn.key)) return false
        val last = pillChangedAt[sbn.key] ?: return false
        val remaining = interval - (System.currentTimeMillis() - last)
        if (remaining <= 0L) return false
        scheduleDeferred(sbn.key, remaining)
        return true
    }

    private fun scheduleDeferred(key: String, delayMs: Long) {
        if (deferred.containsKey(key)) return
        val task = Runnable {
            deferred.remove(key)
            val sbn = try { activeNotifications?.firstOrNull { it.key == key } } catch (e: Exception) { null }
            if (sbn != null) {
                try { process(sbn) } catch (e: Exception) { Log.e(TAG, "Deferred process failed", e) }
            }
        }
        deferred[key] = task
        handler.postDelayed(task, delayMs + 100L)
    }

    private fun cancelDeferred(key: String) {
        deferred.remove(key)?.let { handler.removeCallbacks(it) }
    }

    private fun cancelAllDeferred() {
        deferred.values.forEach { handler.removeCallbacks(it) }
        deferred.clear()
    }

    // ---- Where a tap should land ----------------------------------------------------------------

    private fun settingsIntent(action: String, requestCode: Int): PendingIntent? = try {
        PendingIntent.getActivity(
            applicationContext, requestCode,
            Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    } catch (_: Exception) {
        null
    }

    private fun systemTap(pkg: String, text: String): PendingIntent? {
        val t = text.lowercase()
        return when {
            pkg == "com.android.bluetooth" || t.contains("bluetooth") ->
                settingsIntent(Settings.ACTION_BLUETOOTH_SETTINGS, 7101)
            t.contains("batter") || t.contains("charg") ->
                settingsIntent(Intent.ACTION_POWER_USAGE_SUMMARY, 7102)
            else -> settingsIntent(Settings.ACTION_SOUND_SETTINGS, 7103)
        }
    }

    private fun handleTeams(sbn: StatusBarNotification, id: Int, title: String, text: String): Boolean {
        if (!(title.contains("meeting", ignoreCase = true) || text.contains("call", ignoreCase = true))) {
            return false
        }
        val signature = "Teams Live|$title|$text"
        if (lastShown[sbn.key] == signature) {
            logDecision(sbn, "FALLBACK", Decision.UNCHANGED, "Teams, same as last shown", "Teams Live")
            return true
        }
        logDecision(sbn, "FALLBACK", Decision.PROMOTED, "Teams meeting/call fallback", "Teams Live")
        lastShown[sbn.key] = signature
        capsuleIds[sbn.key] = id
        trackTimeout(sbn.key, 15)
        remember(sbn.key, sbn.packageName, MicrotagPrefs.KEY_TEAMS)
        prefs.incrementInterceptCount()
        MicrotagReminder.showCapsule(
            context = applicationContext,
            pillText = "Teams Live",
            title = title.ifBlank { "Microsoft Teams" },
            content = text,
            notificationId = id,
            timeoutSeconds = 15,
            actions = sbn.notification.actions?.toList() ?: emptyList(),
            sourcePackage = sbn.packageName,
            sourceIntent = sbn.notification.contentIntent
        )
        return true
    }

    private fun handleMaps(sbn: StatusBarNotification, id: Int, title: String, text: String): Boolean {
        if (!sbn.isOngoing) return false
        val signature = "Nav|$title|$text"
        if (lastShown[sbn.key] == signature) {
            logDecision(sbn, "FALLBACK", Decision.UNCHANGED, "Maps, same as last shown", "Nav")
            return true
        }
        logDecision(sbn, "FALLBACK", Decision.PROMOTED, "Maps ongoing navigation fallback", "Nav")
        lastShown[sbn.key] = signature
        capsuleIds[sbn.key] = id
        trackTimeout(sbn.key, 10)
        remember(sbn.key, sbn.packageName, MicrotagPrefs.KEY_MAPS)
        prefs.incrementInterceptCount()
        MicrotagReminder.showCapsule(
            context = applicationContext,
            pillText = "Nav",
            title = title,
            content = text,
            notificationId = id,
            timeoutSeconds = 10,
            sourcePackage = sbn.packageName,
            sourceIntent = sbn.notification.contentIntent
        )
        return true
    }

    private fun clearCapsule(key: String) {
        val id = capsuleIds.remove(key)
        lastShown.remove(key)
        lastContent.remove(key)
        lastShownAt.remove(key)
        timedKeys.remove(key)
        tiles.remove(key)
        pkgs.remove(key)
        pillChangedAt.remove(key)
        cancelDeferred(key)
        if (id != null) {
            MicrotagReminder.cancelTicker(id)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(id)
        }
    }

    private fun trackTimeout(key: String, timeoutSeconds: Int) {
        if (timeoutSeconds > 0) timedKeys.add(key) else timedKeys.remove(key)
    }

    // Unique per source notification (package + id + tag), so apps can't overwrite each other.
    private fun capsuleIdFor(key: String): Int {
        val h = key.hashCode()
        return if (h == Int.MIN_VALUE) 0 else abs(h)
    }

    // ---- Logging -----------------------------------------------------------------------------

    private fun logDecision(
        sbn: StatusBarNotification,
        path: String,
        decision: Decision,
        reason: String,
        pill: String? = null,
        icon: String? = null
    ) {
        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val sub = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        // UNCHANGED entries drop title/text so per-second timers don't defeat the log's repeat-collapsing.
        val quiet = decision == Decision.UNCHANGED
        MicrotagLog.add(
            key = sbn.key,
            pkg = sbn.packageName.orEmpty(),
            path = path,
            decision = decision,
            reason = reason,
            pill = pill,
            icon = icon,
            title = if (quiet) "" else title,
            text = if (quiet) "" else if (sub.isNotBlank()) "$text | sub: $sub" else text,
            meta = metaOf(sbn)
        )
    }

    private fun metaOf(sbn: StatusBarNotification): String {
        val n = sbn.notification ?: return ""
        val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) n.channelId.orEmpty() else ""
        val actions = n.actions?.joinToString("|") { it.title?.toString().orEmpty() }.orEmpty()
        val template = n.extras?.getString(Notification.EXTRA_TEMPLATE)
            ?.substringAfterLast('$').orEmpty()
        // 0x40000 = FLAG_PROMOTED_ONGOING: the source app is already showing its own Live Update
        val sourcePromoted = Build.VERSION.SDK_INT >= 36 && (n.flags and 0x40000) != 0
        return "ongoing=${sbn.isOngoing} clearable=${sbn.isClearable} cat=${n.category} " +
                "ch=$channel tmpl=$template promotedSrc=$sourcePromoted actions=[$actions]"
    }

    companion object {
        private const val TAG = "MicrotagListener"
        private const val POLL_INTERVAL_MS = 3_000L
        private const val CONTENT_REFRESH_MS = 10_000L
        private const val RINGER_CAPSULE_ID = 7_300_002
    }
}
