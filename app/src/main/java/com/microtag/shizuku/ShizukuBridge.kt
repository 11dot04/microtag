package com.microtag.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.microtag.core.MicrotagPrefs
import com.microtag.inspect.InspectEngine
import com.microtag.inspect.Source
import com.microtag.listener.MicrotagNotificationService
import rikka.shizuku.Shizuku

/**
 * App-side half of the Shizuku integration. It owns the connection to [PrivilegedService]
 * (clipboard hook, background hardening) and replaces the old ShizukuClipboardWatcher.
 *
 * Call [init] once (the listener service and MainActivity both do; it is idempotent) and
 * [refresh] whenever the clipboard tile or the Shizuku state may have changed.
 */
object ShizukuBridge {
    private const val TAG = "MicrotagShizuku"

    // Bump when PrivilegedService / the AIDL change so Shizuku restarts the daemon with new code.
    private const val SERVICE_VERSION = 1

    private val main = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var initialised = false

    private var service: IPrivilegedService? = null
    private var binding = false
    private var hardened = false
    private var clipboardRunning = false
    private var lastClip = ""
    private var lastClipAt = 0L
    private var grantsDone = false
    private const val DEDUPE_MS = 2000L

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            binding = false
            if (!binder.pingBinder()) return
            service = IPrivilegedService.Stub.asInterface(binder)
            clipboardRunning = false
            Log.i(TAG, "Privileged service connected")
            refresh()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.w(TAG, "Privileged service disconnected")
            service = null
            binding = false
            clipboardRunning = false
            hardened = false
        }
    }

    private val clipCallback = object : IClipListener.Stub() {
        override fun onClip(text: String?) {
            if (text.isNullOrBlank()) return
            main.post { evaluateClip(text) }
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { main.post { refresh() } }

    private val binderDead = Shizuku.OnBinderDeadListener {
        service = null
        binding = false
        clipboardRunning = false
        hardened = false
    }

    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) main.post { refresh() }
    }

    @Synchronized
    fun init(context: Context) {
        if (initialised) {
            refresh()
            return
        }
        appContext = context.applicationContext
        initialised = true
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
    }

    fun isRunning(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    fun hasPermission(): Boolean = try {
        isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    /** Asks Shizuku for permission; the result arrives through [permissionResult]. */
    fun requestPermission(requestCode: Int = 1001) {
        try {
            if (isRunning() && !Shizuku.shouldShowRequestPermissionRationale()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "requestPermission failed: ${e.message}")
        }
    }

    /** Brings everything in line with the current Shizuku state and the clipboard tile. */
    @Synchronized
    fun refresh() {
        val ctx = appContext ?: return
        if (!hasPermission()) {
            clipboardRunning = false
            return
        }

        grantPermissions(ctx)

        val svc = service
        if (svc == null || !svc.asBinder().pingBinder()) {
            service = null
            bind(ctx)
            return
        }

        if (!hardened) {
            hardened = true
            Thread {
                try {
                    val log = svc.hardenBackground(
                        ctx.packageName,
                        ComponentName(ctx, MicrotagNotificationService::class.java).flattenToString()
                    )
                    Log.i(TAG, "Background hardening:\n$log")
                } catch (e: Throwable) {
                    hardened = false
                    Log.w(TAG, "hardenBackground failed: ${e.message}")
                }
            }.start()
        }

        val wantClipboard = MicrotagPrefs(ctx).isAppEnabled(MicrotagPrefs.KEY_CLIPBOARD)
        try {
            if (wantClipboard && !clipboardRunning) {
                svc.startClipboard(clipCallback)
                clipboardRunning = true
            } else if (!wantClipboard && clipboardRunning) {
                svc.stopClipboard()
                clipboardRunning = false
            }
        } catch (e: Throwable) {
            Log.w(TAG, "clipboard toggle failed: ${e.message}")
            clipboardRunning = false
        }
    }

    /**
     * Permissions Android only lets the shell grant, done once per process while Shizuku is available:
     *  - Reading sensitive notifications. Since Android 15 a listener sees "Sensitive notification content hidden"
     *    instead of a one-time code unless this app op is allowed. Without it OTP chips have nothing to read.
     *  - Bluetooth: connecting devices' names and battery, for the earbuds chip.
     * The result is in logcat under MicrotagShizuku.
     */
    private fun grantPermissions(ctx: Context) {
        if (grantsDone) return
        grantsDone = true
        val pkg = ctx.packageName
        Thread {
            val log = StringBuilder()
            var ran = false
            for (op in listOf("RECEIVE_SENSITIVE_NOTIFICATIONS", "android:receive_sensitive_notifications")) {
                val out = shell("cmd appops set --user 0 $pkg $op allow")
                if (out != null) ran = true
                log.append("appops set $op: ${out?.trim().orEmpty().ifBlank { "ok" }}\n")
            }
            val state = shell("cmd appops get $pkg RECEIVE_SENSITIVE_NOTIFICATIONS")
            log.append("appops get: ${state?.trim().orEmpty()}\n")
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                val out = shell("pm grant $pkg android.permission.BLUETOOTH_CONNECT")
                if (out != null) ran = true
                log.append("pm grant BLUETOOTH_CONNECT: ${out?.trim().orEmpty().ifBlank { "ok" }}\n")
            }
            Log.i(TAG, "Permission grants:\n$log")
            // Nothing ran at all (no shell): try again next time rather than giving up for this process.
            if (!ran) grantsDone = false
        }.start()
    }

    /** Runs a shell command through Shizuku. Output (stdout and stderr) or null when it could not be run. */
    private fun shell(command: String): String? = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        val process = m.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
        val out = process.inputStream.bufferedReader().readText() + process.errorStream.bufferedReader().readText()
        process.waitFor()
        out
    } catch (e: Throwable) {
        Log.w(TAG, "shell failed: ${e.message}")
        null
    }

    private fun bind(ctx: Context) {
        if (binding) return
        binding = true
        try {
            Shizuku.bindUserService(userServiceArgs(ctx), connection)
        } catch (e: Throwable) {
            binding = false
            Log.e(TAG, "bindUserService failed: ${e.message}", e)
        }
    }

    private fun userServiceArgs(ctx: Context) = Shizuku.UserServiceArgs(
        ComponentName(ctx.packageName, PrivilegedService::class.java.name)
    )
        .processNameSuffix("privileged")
        .tag("microtag-privileged")
        .daemon(true)
        .version(SERVICE_VERSION)

    /**
     * Clipboard text arrived from the shell process. The detectors decide what is worth showing, so
     * this only filters noise: too short or too long, the app's own copy button, and repeats.
     */
    private fun evaluateClip(text: String) {
        val ctx = appContext ?: return
        if (!MicrotagPrefs(ctx).isAppEnabled(MicrotagPrefs.KEY_CLIPBOARD)) return

        val trimmed = text.trim()
        if (trimmed.length !in 2..500) return
        if (InspectEngine.isSelfCopy(trimmed)) return

        val now = System.currentTimeMillis()
        if (trimmed == lastClip && now - lastClipAt < DEDUPE_MS) return
        lastClip = trimmed
        lastClipAt = now

        // Runs on the engine's own thread; no activity is started, so no app is pulled to the front.
        InspectEngine.inspect(ctx, trimmed, Source.CLIPBOARD)
    }
}
