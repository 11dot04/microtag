package com.microtag.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import rikka.shizuku.Shizuku

/** Live permission state shared by onboarding and Settings. Refreshes whenever the app resumes. */
@Stable
class Permissions(private val context: Context) {
    var hasListener by mutableStateOf(checkListenerPermission(context)); private set
    var hasNotif by mutableStateOf(checkPostNotifPermission(context)); private set
    var hasShizuku by mutableStateOf(checkShizukuPermission()); private set
    var hasCalendar by mutableStateOf(hasCalendarPermission(context)); private set
    /** Nearby devices (Bluetooth connect): lets the Bluetooth chip read a device's name. */
    var hasNearby by mutableStateOf(checkNearbyPermission(context)); private set
    /** Read sensitive notifications (Android 15+): lets TAG read OTP codes. Granted through Shizuku or adb. */
    var hasSensitive by mutableStateOf(checkSensitivePermission(context)); private set

    internal var launchNotif: () -> Unit = {}
    internal var launchCalendar: () -> Unit = {}
    internal var launchNearby: () -> Unit = {}

    fun refresh() {
        hasListener = checkListenerPermission(context)
        hasNotif = checkPostNotifPermission(context)
        hasShizuku = checkShizukuPermission()
        hasCalendar = hasCalendarPermission(context)
        hasNearby = checkNearbyPermission(context)
        hasSensitive = checkSensitivePermission(context)
    }

    internal fun onNotifResult(ok: Boolean) {
        hasNotif = ok
        if (!ok) {
            // The system won't ask again once it has been refused, so go to the settings page.
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    internal fun onCalendarResult(ok: Boolean) { hasCalendar = ok }

    internal fun onNearbyResult(ok: Boolean) {
        hasNearby = ok
        if (!ok) {
            // Refused once means the system won't ask again; send them to this app's settings page.
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openListenerSettings() {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun requestNotifications() = launchNotif()
    fun requestCalendar() = launchCalendar()
    fun requestNearby() = launchNearby()

    /** The one-line command that grants "read sensitive notifications". Shizuku can run it; so can adb. */
    fun sensitiveCommand(): String =
        "adb shell appops set ${context.packageName} RECEIVE_SENSITIVE_NOTIFICATIONS allow"

    /** Copies [sensitiveCommand] so it can be pasted into a Shizuku-enabled terminal or adb shell. */
    fun copySensitiveCommand() {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("TAG grant command", sensitiveCommand()))
        Toast.makeText(context, "Command copied", Toast.LENGTH_SHORT).show()
    }

    fun requestShizuku() {
        try {
            if (Shizuku.pingBinder()) Shizuku.requestPermission(1001)
            else Toast.makeText(context, "Start Shizuku first", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(context, "Shizuku isn't installed", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun rememberPermissions(): Permissions {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val perms = remember { Permissions(context) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { perms.onNotifResult(it) }
    val calendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { perms.onCalendarResult(it) }
    val nearby = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { perms.onNearbyResult(it) }
    perms.launchNotif = { notif.launch(Manifest.permission.POST_NOTIFICATIONS) }
    perms.launchCalendar = { calendar.launch(Manifest.permission.READ_CALENDAR) }
    perms.launchNearby = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) nearby.launch(Manifest.permission.BLUETOOTH_CONNECT) }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) perms.refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return perms
}

fun checkListenerPermission(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(context.packageName)
}

fun checkPostNotifPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else true

fun checkShizukuPermission(): Boolean = try {
    Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
} catch (_: Exception) {
    false
}

/** Android 12+: "Nearby devices" is the BLUETOOTH_CONNECT runtime permission. Older versions need nothing. */
fun checkNearbyPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else true

/** Android 15 redacts OTP notifications from listeners unless this app-op is allowed. */
fun sensitiveNotificationsApply(): Boolean = Build.VERSION.SDK_INT >= 35

private const val SENSITIVE_APPOP = "android:receive_sensitive_notifications"

/** True when the app-op is allowed (or when this Android version has no such redaction). Never throws. */
fun checkSensitivePermission(context: Context): Boolean {
    if (!sensitiveNotificationsApply()) return true
    return runCatching {
        val ops = context.getSystemService(AppOpsManager::class.java)
        ops.unsafeCheckOpNoThrow(SENSITIVE_APPOP, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)
}
