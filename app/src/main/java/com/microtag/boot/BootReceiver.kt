package com.microtag.boot

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.util.Log
import com.microtag.listener.MicrotagNotificationService
import com.microtag.shizuku.ShizukuBridge

/**
 * Starts the app process after boot or an update. That process start is what makes
 * ShizukuProvider hand over the Shizuku binder (if Shizuku is already running), and the
 * rebind request nudges the listener service if the system hasn't bound it yet.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i("MicrotagBoot", "Received ${intent.action}")
                try {
                    NotificationListenerService.requestRebind(
                        ComponentName(context, MicrotagNotificationService::class.java)
                    )
                } catch (e: Exception) {
                    Log.w("MicrotagBoot", "Rebind request failed: ${e.message}")
                }
                ShizukuBridge.init(context.applicationContext)
            }
        }
    }
}
