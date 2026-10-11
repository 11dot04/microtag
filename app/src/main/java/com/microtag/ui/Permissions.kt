package com.microtag.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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

    internal var launchNotif: () -> Unit = {}
    internal var launchCalendar: () -> Unit = {}

    fun refresh() {
        hasListener = checkListenerPermission(context)
        hasNotif = checkPostNotifPermission(context)
        hasShizuku = checkShizukuPermission()
        hasCalendar = hasCalendarPermission(context)
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

    fun openListenerSettings() {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun requestNotifications() = launchNotif()
    fun requestCalendar() = launchCalendar()

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
    perms.launchNotif = { notif.launch(Manifest.permission.POST_NOTIFICATIONS) }
    perms.launchCalendar = { calendar.launch(Manifest.permission.READ_CALENDAR) }

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
