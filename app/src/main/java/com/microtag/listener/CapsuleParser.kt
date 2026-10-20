package com.microtag.listener

import android.app.Notification
import android.os.Build
import android.service.notification.StatusBarNotification
import android.os.Bundle
import com.microtag.core.MicrotagPrefs
import com.microtag.inspect.InspectPayload
import com.microtag.ui.TagKeys
import java.util.Calendar

data class ParsedCapsule(
    val pillText: String,
    val title: String,
    val content: String,
    val iconName: String? = null,
    val iconRes: Int = 0,
    val timeoutSeconds: Int = 0,
    val chronometerTargetMillis: Long? = null,
    val payload: InspectPayload? = null,
    val actions: List<Notification.Action> = emptyList(),
    /** Dashboard tile (MicrotagPrefs key) this capsule belongs to, if any. */
    val tile: String? = null
)

/** Settings the parser needs, passed in so the parser itself stays free of Android storage. */
data class ParseContext(
    val stravaMetric: String = "pace",
    /** True when CalendarCountdown reads the calendar provider, so calendar notifications are ignored. */
    val calendarProviderActive: Boolean = false,
    val calendarLeadMin: Int = 30
)

object CapsuleParser {

    private const val ICON_PLAY = "ic_play"
    private const val ICON_AMBIENT = "ic_ambient_music"
    private const val ICON_CALENDAR = "ic_calendar_countdown"
    private const val ICON_BOLT = "ic_capsule_bolt"
    private const val ICON_BLUETOOTH = "ic_earbuds"
    private const val ICON_DND = "ic_ringer_silent"
    private const val ICON_WEATHER = "ic_weather"
    private const val ICON_RUN = "ic_capsule_run"
    private const val ICON_CHAT = "ic_capsule_chat"
    private const val ICON_VPN = "ic_capsule_vpn"
    private const val ICON_OTP = "ic_otp"
    private const val ICON_DOWNLOAD = "ic_download"
    private const val ICON_UPLOAD = "ic_upload"

    private val stravaPacePattern = Regex("""\b\d{1,2}:\d{2}\s?/\s?(?:km|mi)\b""", RegexOption.IGNORE_CASE)

    private fun isMedia(extras: Bundle) = extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
    private fun isAmbient(pkg: String) = pkg == "com.kieronquinn.app.ambientmusicmod" || pkg.contains("intelligence.sense")
    private fun isCalendar(pkg: String) = pkg == "com.google.android.calendar" || pkg.contains("calendar")
    private fun isStrava(pkg: String) = pkg == "com.strava" || pkg.contains("workout") || pkg.contains("fitness")
    private fun isDiscord(pkg: String) = pkg == "com.discord" || pkg.contains("discord")
    private fun isWeather(pkg: String) = pkg.contains("weather") || pkg == "com.google.android.googlequicksearchbox"
    // Apps whose notifications can carry a one-time code: SMS apps, the common chat apps that send
    // verification messages, and mail apps. The code rule below still needs real OTP wording, so
    // ordinary chat or mail with a number in it is left alone.
    private val otpPackages = setOf(
        // SMS / RCS
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        "com.android.messaging", "com.oneplus.mms", "com.oplus.mms", "com.coloros.mms", "com.vivo.mms", "com.vivo.message",
        "com.huawei.message", "com.miui.mms", "com.asus.message", "com.google.android.apps.googlevoice",
        "com.motorola.messaging", "com.sonyericsson.conversations", "com.truecaller",
        "com.microsoft.android.smsorganizer", "com.simplemobiletools.smsmessenger", "com.textra",
        "com.jb.gosms", "com.handcent.nextsms", "org.smssecure.smssecure",
        // Chat apps that deliver verification codes
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.telegram.messenger.web", "org.thoughtcrime.securesms",
        "com.facebook.orca", "com.facebook.mlite", "com.skype.raider", "jp.naver.line.android", "com.viber.voip",
        // Mail
        "com.google.android.gm", "com.google.android.gm.lite", "com.microsoft.office.outlook",
        "com.android.email", "ch.protonmail.android", "com.yahoo.mobile.client.android.mail", "com.fsck.k9",
        "com.samsung.android.email.provider"
    )
    private val smsSegment = Regex("""(?:^|\.)(?:sms|mms|messaging)(?:\.|$)""")

    private fun isMessaging(pkg: String) =
        pkg in otpPackages || pkg.contains("messaging") || smsSegment.containsMatchIn(pkg)

    /** Everything a notification can say, including the body, the line list and MessagingStyle messages. */
    private fun fullText(extras: Bundle): String {
        val parts = ArrayList<String>()
        for (key in listOf(
            Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_BIG_TEXT, Notification.EXTRA_SUMMARY_TEXT, Notification.EXTRA_INFO_TEXT
        )) extras.getCharSequence(key)?.toString()?.let { if (it.isNotBlank()) parts.add(it) }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { if (it.isNotBlank()) parts.add(it.toString()) }
        // MessagingStyle (Google Messages, WhatsApp, Telegram): the code can be in an earlier message of the thread.
        try {
            @Suppress("DEPRECATION")
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.forEach { m ->
                (m as? Bundle)?.getCharSequence("text")?.toString()?.let { if (it.isNotBlank()) parts.add(it) }
            }
        } catch (_: Exception) {
        }
        return parts.distinct().joinToString(" ")
    }

    /** An app that is not a messenger, but whose notification clearly carries a code (a bank app, a game launcher...). */
    private fun carriesCode(extras: Bundle): Boolean = OtpExtractor.isCodeMessage(fullText(extras))

    // ---- uploads and downloads share the system progress bar; the wording and the app tell them apart ----
    private val uploadWords = Regex("""(?i)\b(?:upload(?:ing|ed)?|back(?:ing)?[ -]?up|sending|saving to (?:cloud|drive)|sync(?:ing)? (?:to|with) (?:cloud|drive))\b""")
    private val downloadWords = Regex("""(?i)\b(?:download(?:ing|ed)?|restor(?:e|ing)|receiving|installing|updating|fetching)\b""")
    private val uploadPackages = setOf(
        "com.google.android.apps.photos", "com.google.android.apps.docs", "com.dropbox.android",
        "com.microsoft.skydrive", "com.amazon.clouddrive.photos", "com.mega.android", "com.synology.dsphoto"
    )

    private val percentIn = Regex("""(\d{1,3})\s?%""")

    /**
     * A transfer in progress. Checked before the messenger test: WhatsApp is a code app, but its backup and media
     * transfers carry a progress bar, or say "Uploading: 45 MB of 45 MB (97%)", and must not be read as chat.
     */
    private fun isTransfer(extras: Bundle): Boolean {
        if (extras.containsKey(Notification.EXTRA_PROGRESS) && extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0) return true
        val text = fullText(extras)
        return percentIn.containsMatchIn(text) && (uploadWords.containsMatchIn(text) || downloadWords.containsMatchIn(text))
    }

    private fun isUpload(pkg: String, extras: Bundle): Boolean {
        val text = fullText(extras)
        val up = uploadWords.containsMatchIn(text)
        val down = downloadWords.containsMatchIn(text)
        if (down && !up) return false
        return up || pkg in uploadPackages
    }

    /**
     * Which dashboard tile owns this notification, decided from package/extras alone so the
     * caller can skip parsing entirely when that tile is off. Null = decided after parsing
     * (system UI) or not tied to a tile (generic progress is handled as Downloads).
     */
    fun tileFor(pkg: String, extras: Bundle): String? = when {
        isMedia(extras) -> TagKeys.SONG_NEXT
        isAmbient(pkg) -> TagKeys.AMBIENT_MUSIC
        isCalendar(pkg) -> TagKeys.CALENDAR
        pkg == "com.android.bluetooth" || pkg == "com.android.systemui" -> null
        pkg == "ch.protonvpn.android" -> MicrotagPrefs.KEY_PROTON
        isStrava(pkg) -> MicrotagPrefs.KEY_STRAVA
        isDiscord(pkg) -> MicrotagPrefs.KEY_DISCORD
        isWeather(pkg) -> TagKeys.WEATHER
        isTransfer(extras) -> if (isUpload(pkg, extras)) TagKeys.UPLOADS else MicrotagPrefs.KEY_DOWNLOADS
        isMessaging(pkg) -> MicrotagPrefs.KEY_OTP
        extras.containsKey(Notification.EXTRA_PROGRESS) -> if (isUpload(pkg, extras)) TagKeys.UPLOADS else MicrotagPrefs.KEY_DOWNLOADS
        carriesCode(extras) -> MicrotagPrefs.KEY_OTP
        else -> null
    }

    private const val DISCORD_VOICE_CHANNEL_ID = "mediaConnections"

    private val discordVoiceTextPattern = Regex(
        """\bvoice\s+(?:connected|connection|channel)\b|\bin\s+call\b|\bon\s+call\b|\bconnecting\b|\bwaiting\s+for\s+voice\b""",
        RegexOption.IGNORE_CASE
    )
    private val discordConnectingPattern = Regex(
        """\b(?:connecting|reconnecting|authenticating|waiting|rtc|ice)\b""",
        RegexOption.IGNORE_CASE
    )
    private val discordMutedPattern = Regex("""\bmuted\b""", RegexOption.IGNORE_CASE)
    private val discordDeafenedPattern = Regex("""\bdeafened\b""", RegexOption.IGNORE_CASE)

    // Strava's "Stop" button pauses the recording and its notification then says "Stopped".
    private val stravaPausedPattern = Regex("""paused|stopped|暫停|暂停""", RegexOption.IGNORE_CASE)
    private val stravaDistancePattern = Regex("""\b\d+(?:[.,]\d+)?\s?(?:km|mi)\b""", RegexOption.IGNORE_CASE)
    private val stravaTimePattern = Regex("""\b(?:\d{1,2}:)?\d{1,2}:\d{2}\b""")

    private val protonCountryPattern = Regex("""(?i)connected\s*(?:to|:|-|–)?\s*(.+)""")

    fun parse(sbn: StatusBarNotification, ctx: ParseContext = ParseContext()): ParsedCapsule? {
        val pkg = sbn.packageName ?: return null
        val notif = sbn.notification ?: return null
        val extras = notif.extras ?: return null

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val combined = "$title $text $subText".trim()

        val parsed = when {
            isMedia(extras) -> parseMedia(notif, title, text, subText)
            isAmbient(pkg) -> parseAmbientMusic(notif, title, text)
            // The calendar provider drives countdowns when it's readable; the notification is a fallback.
            isCalendar(pkg) -> if (ctx.calendarProviderActive) null else parseCalendar(notif, title, text, ctx)
            pkg == "com.android.bluetooth" || pkg == "com.android.systemui" -> parseSystemUi(pkg, notif, title, text, combined)
            pkg == "ch.protonvpn.android" -> parseProtonVpn(notif, title, text)
            isStrava(pkg) -> parseStrava(notif, combined, title, text, ctx)
            isDiscord(pkg) -> parseDiscord(notif, combined, title, text)
            isWeather(pkg) -> parseWeather(combined, title, text)
            isTransfer(extras) -> parseProgress(notif, title, text, isUpload(pkg, extras), fullText(extras))
            isMessaging(pkg) -> parseOtp(fullText(extras).ifBlank { combined }, title, text)
            extras.containsKey(Notification.EXTRA_PROGRESS) -> parseProgress(notif, title, text, isUpload(pkg, extras), fullText(extras))
            carriesCode(extras) -> parseOtp(fullText(extras), title, text)
            else -> null
        }
        // Fill in the tile from routing when the branch didn't set one itself (system UI does).
        return if (parsed != null && parsed.tile == null) parsed.copy(tile = tileFor(pkg, extras)) else parsed
    }

    private fun cleanSongTitle(rawTitle: String, artist: String): String {
        var cleaned = rawTitle.replace(Regex("""\s*[\(\[].*?[\)\]]"""), "").trim()
        
        if (cleaned.contains("-") && artist.isNotBlank()) {
            val parts = cleaned.split("-").map { it.trim() }
            val artistLower = artist.lowercase()
            
            val songPart = parts.firstOrNull { part ->
                !part.lowercase().contains(artistLower) && !artistLower.contains(part.lowercase())
            }
            if (songPart != null) {
                cleaned = songPart
            }
        }
        return cleaned.ifBlank { rawTitle }.take(14)
    }

    private fun parseMedia(notif: Notification, title: String, text: String, subText: String): ParsedCapsule? {
        if (title.isBlank()) return null
        val artist = text.ifBlank { subText } 
        val songName = cleanSongTitle(title, artist)

        return ParsedCapsule(
            pillText = songName,
            title = title,
            content = text,
            iconName = ICON_PLAY,
            timeoutSeconds = 6,
            actions = notif.actions?.toList() ?: emptyList()
        )
    }

    private fun parseAmbientMusic(notif: Notification, title: String, text: String): ParsedCapsule? {
        // Ignore the app's permanent foreground-service notification; it isn't a song.
        if (title.contains("Ambient Music Mod", ignoreCase = true) ||
            text.contains("running in the background", ignoreCase = true)
        ) return null

        val rawSong = title.replace("Now Playing", "", ignoreCase = true).trim().ifBlank { 
            text.split("-").firstOrNull()?.trim() ?: "Music" 
        }
        val songName = cleanSongTitle(rawSong, "")

        return ParsedCapsule(
            pillText = songName,
            title = "Now Playing",
            content = rawSong,
            iconName = ICON_AMBIENT,
            timeoutSeconds = 8,
            actions = notif.actions?.toList() ?: emptyList()
        )
    }

    /**
     * Notification-based fallback, used only when the calendar provider can't be read.
     * An event that already started never rolls over to tomorrow (that showed "in 23h"),
     * and events further away than the lead time are ignored.
     */
    private fun parseCalendar(notif: Notification, title: String, text: String, ctx: ParseContext): ParsedCapsule? {
        val timeMatch = Regex("""\b(\d{1,2}):(\d{2})\s?(AM|PM|am|pm)?\b""").find(text) ?: return null

        val hours = timeMatch.groupValues[1].toInt()
        val minutes = timeMatch.groupValues[2].toInt()
        val ampm = timeMatch.groupValues.getOrNull(3)?.uppercase()
        var hourOfDay = hours
        if (ampm == "PM" && hours < 12) hourOfDay += 12
        if (ampm == "AM" && hours == 12) hourOfDay = 0

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minutes)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = System.currentTimeMillis()
        val target = calendar.timeInMillis
        val leadMs = ctx.calendarLeadMin * 60_000L
        if (target - now > leadMs) return null

        val started = target <= now
        return ParsedCapsule(
            pillText = if (started) "Now" else "in {DUR}",
            title = title,
            content = text,
            iconName = ICON_CALENDAR,
            timeoutSeconds = 0,
            chronometerTargetMillis = if (started) null else target,
            actions = notif.actions?.toList() ?: emptyList(),
            tile = TagKeys.CALENDAR
        )
    }

    private fun parseSystemUi(pkg: String, notif: Notification, title: String, text: String, combined: String): ParsedCapsule? {
        return when {
            combined.contains("battery", ignoreCase = true) || combined.contains("charging", ignoreCase = true) -> {
                val pct = Regex("""\d+%""").find(combined)?.value ?: "Power"
                ParsedCapsule(pct, title, text, ICON_BOLT, timeoutSeconds = 5)
            }
            // "Connected to" alone is too loose (Wi-Fi, casting): only the Bluetooth app or the word itself.
            pkg == "com.android.bluetooth" || combined.contains("bluetooth", ignoreCase = true) -> {
                val device = title.replace("Connected to ", "", ignoreCase = true).take(10)
                ParsedCapsule(device, "Bluetooth", text, ICON_BLUETOOTH, timeoutSeconds = 5, actions = notif.actions?.toList() ?: emptyList(), tile = TagKeys.BLUETOOTH)
            }
            combined.contains("do not disturb", ignoreCase = true) || combined.contains("muted", ignoreCase = true) -> {
                ParsedCapsule("DND", title, text, ICON_DND, timeoutSeconds = 4, tile = TagKeys.SOUND_MODE)
            }
            else -> null
        }
    }

    private fun parseWeather(combined: String, title: String, text: String): ParsedCapsule? {
        val tempMatch = Regex("""-?\d{1,3}°[CFcf]?""").find(combined) ?: return null
        val cleanTemp = tempMatch.value.replace(Regex("""[CFcf]"""), "")

        return ParsedCapsule(
            pillText = cleanTemp,
            title = title.ifBlank { "Weather" },
            content = text,
            iconName = ICON_WEATHER,
            timeoutSeconds = 0
        )
    }

    // ---------------------------------------------------------------------
    // Strava: the pill shows the metric chosen in the dashboard ("Chip shows").
    //   pace / time / distance, falling back to the next one that exists, then the activity.
    // ---------------------------------------------------------------------
    private fun stravaMetricValue(combined: String, metric: String): String? {
        val pace = stravaPacePattern.find(combined)?.value?.replace(" ", "")
        // Pace looks like a time ("5:30/km"), so strip it before looking for elapsed time.
        val time = stravaTimePattern.find(combined.replace(stravaPacePattern, " "))?.value
        val distance = stravaDistancePattern.find(combined)?.value?.replace(" ", "")
        return when (metric) {
            "time" -> time ?: distance ?: pace
            "distance" -> distance ?: time ?: pace
            else -> pace ?: distance ?: time
        }
    }

    private fun parseStrava(notif: Notification, combined: String, title: String, text: String, ctx: ParseContext): ParsedCapsule? {
        val actions = notif.actions?.toList() ?: emptyList()
        val value = stravaMetricValue(combined, ctx.stravaMetric)

        if (stravaPausedPattern.containsMatchIn(combined)) {
            return ParsedCapsule(
                pillText = value ?: "Paused",
                title = title.ifBlank { "Workout Paused" },
                content = text,
                iconName = ICON_RUN,
                timeoutSeconds = 0,
                actions = actions
            )
        }

        return ParsedCapsule(
            pillText = value ?: stravaActivityLabel(combined),
            title = title.ifBlank { "Strava" },
            content = text,
            iconName = ICON_RUN,
            timeoutSeconds = 0,
            actions = actions
        )
    }

    private fun stravaActivityLabel(combined: String): String {
        val lower = combined.lowercase()
        return when {
            Regex("""\b(run|running)\b""").containsMatchIn(lower) -> "Run"
            Regex("""\b(ride|riding|cycling|bike|biking)\b""").containsMatchIn(lower) -> "Ride"
            Regex("""\bwalk(ing)?\b""").containsMatchIn(lower) -> "Walk"
            Regex("""\bhik(e|ing)\b""").containsMatchIn(lower) -> "Hike"
            Regex("""\bswim(ming)?\b""").containsMatchIn(lower) -> "Swim"
            else -> "Active"
        }
    }

    // ---------------------------------------------------------------------
    // Discord
    //   voice session -> "On VC" / "On call" / "Muted" / "Deafened" / "Connecting"
    //   anything else -> mention / DM as before
    // ---------------------------------------------------------------------
    private fun parseDiscord(notif: Notification, combined: String, title: String, text: String): ParsedCapsule? {
        val actions = notif.actions?.toList() ?: emptyList()
        val actionTitles = actions.mapNotNull { it.title?.toString()?.lowercase() }

        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notif.channelId?.trim().orEmpty()
        } else {
            ""
        }
        val isVoice = channelId.equals(DISCORD_VOICE_CHANNEL_ID, ignoreCase = true) ||
                discordVoiceTextPattern.containsMatchIn(combined)

        if (isVoice) {
            // Discord titles look like "Voice Connected – Tap to return to call".
            // Only the part before the dash is the state; the rest is boilerplate
            // (it contains "call", which is why everything used to read "On call").
            val status = title.substringBefore(" – ").substringBefore(" - ").trim()
            val isConnecting = discordConnectingPattern.containsMatchIn(status)

            // The action buttons show the *next* state, so "Unmute" means currently muted.
            val isDeafened = actionTitles.any { it.contains("undeafen") } ||
                    discordDeafenedPattern.containsMatchIn(status)
            val isMuted = actionTitles.any { it.contains("unmute") } ||
                    discordMutedPattern.containsMatchIn(status)

            val pill = when {
                isConnecting -> "Connecting"
                isDeafened -> "Deafened"
                isMuted -> "Muted"
                else -> "On VC"
            }

            return ParsedCapsule(
                pillText = pill,
                title = title.ifBlank { "Discord" },
                content = text,
                iconName = ICON_CHAT,
                timeoutSeconds = 0,
                actions = actions
            )
        }

        val hasMention = combined.contains("@") || combined.contains("mentioned", ignoreCase = true)
        val pill = if (hasMention) {
            val sender = title.split(" ").firstOrNull()?.take(8) ?: "Msg"
            "@$sender"
        } else {
            "DM"
        }

        return ParsedCapsule(
            pillText = pill,
            title = title.ifBlank { "Discord" },
            content = text,
            iconName = ICON_CHAT,
            timeoutSeconds = 8,
            actions = actions
        )
    }

    // ---------------------------------------------------------------------
    // ProtonVPN: "Connected to Japan" -> "Japan" with a VPN icon
    // ---------------------------------------------------------------------
    private fun parseProtonVpn(notif: Notification, title: String, text: String): ParsedCapsule? {
        val source = when {
            title.contains("Connected", ignoreCase = true) -> title
            text.contains("Connected", ignoreCase = true) -> text
            else -> return null
        }

        val rawPlace = protonCountryPattern.find(source)?.groupValues?.get(1).orEmpty()
        // Drop server suffixes like "Japan - JP#12", "Japan • JP#12", "Japan (P2P)"
        val place = rawPlace
            .split(" - ", " – ", " • ", " · ", "#", "(")
            .firstOrNull()
            ?.trim()
            ?.trimEnd('.', ',', ':')
            .orEmpty()
            .let { CountryCodes.shorten(it) }
            .ifBlank { "VPN" }

        return ParsedCapsule(
            pillText = place,
            title = title,
            content = text,
            iconName = ICON_VPN,
            timeoutSeconds = 0, 
            actions = notif.actions?.toList() ?: emptyList()
        )
    }

    /** Kept for the tests: the code in [haystack], or null. */
    internal fun extractOtp(haystack: String): String? = OtpExtractor.extract(haystack)

    private fun parseOtp(combined: String, title: String, text: String): ParsedCapsule? {
        val code = OtpExtractor.extract(combined) ?: return null

        return ParsedCapsule(
            pillText = code,
            title = "Verification Code",
            content = text.ifBlank { "Tap to copy: $code" },
            iconName = ICON_OTP,
            timeoutSeconds = 30,
            payload = InspectPayload(
                title = "Authentication Token",
                primaryText = code,
                secondaryText = title,
                actionType = "COPY",
                copyContent = code
            )
        )
    }

    /** Downloads and uploads (backups, cloud sync) both show a system progress bar; they get their own icon and tile. */
    private fun parseProgress(notif: Notification, title: String, text: String, upload: Boolean, full: String = text): ParsedCapsule? {
        val max = notif.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val progress = notif.extras.getInt(Notification.EXTRA_PROGRESS, 0)
        // No usable progress bar: take the percentage the text states.
        val pct = if (max > 0) {
            if (progress >= max) return null
            ((progress.toDouble() / max) * 100).toInt()
        } else {
            val said = percentIn.find(full)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            if (said >= 100) return null
            said
        }
        return ParsedCapsule(
            pillText = "$pct%",
            title = title.ifBlank { if (upload) "Uploading" else "Downloading" },
            content = text.ifBlank { "$progress / $max completed" },
            iconName = if (upload) ICON_UPLOAD else ICON_DOWNLOAD,
            timeoutSeconds = 4,
            tile = if (upload) TagKeys.UPLOADS else MicrotagPrefs.KEY_DOWNLOADS
        )
    }
}
