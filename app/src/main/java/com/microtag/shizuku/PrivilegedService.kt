package com.microtag.shizuku

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Runs inside Shizuku's user-service process, which is a plain shell-uid process (not an app
 * process), so it can talk to hidden system services and run shell commands.
 * Keep this class free of anything that needs an app Context.
 */
class PrivilegedService : IPrivilegedService.Stub() {

    @Volatile private var client: IClipListener? = null
    @Volatile private var clipboard: Any? = null
    @Volatile private var registeredListener: Any? = null
    private var lastClip: String? = null

    private val clientDeath = IBinder.DeathRecipient { stopClipboard() }

    /** Receives IOnPrimaryClipChangedListener.onPrimaryClipChanged() (transaction 1) from system_server. */
    private val clipBinder = object : Binder() {
        override fun getInterfaceDescriptor(): String = LISTENER_DESCRIPTOR

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == FIRST_CALL_TRANSACTION) {
                data.enforceInterface(LISTENER_DESCRIPTOR)
                onPrimaryClipChanged()
                return true
            }
            return super.onTransact(code, data, reply, flags)
        }
    }

    override fun destroy() {
        stopClipboard()
        exitProcess(0)
    }

    @Synchronized
    override fun startClipboard(listener: IClipListener?) {
        if (listener == null) return
        stopClipboard()
        try {
            // Without this, the clipboard service refuses reads from the shell package on Android 10+.
            sh("cmd", "appops", "set", SHELL_PKG, "READ_CLIPBOARD", "allow")

            val service = getClipboardService()
            val proxy = asClipListener()
            val add = service.javaClass.methods.first { it.name == "addPrimaryClipChangedListener" }
            add.invoke(service, *argsFor(add, proxy))

            client = listener
            listener.asBinder().linkToDeath(clientDeath, 0)
            clipboard = service
            registeredListener = proxy
            Log.i(TAG, "Clipboard listener registered")
        } catch (e: Throwable) {
            Log.e(TAG, "startClipboard failed: ${e.message}", e)
        }
    }

    @Synchronized
    override fun stopClipboard() {
        val service = clipboard
        val proxy = registeredListener
        if (service != null && proxy != null) {
            try {
                val remove = service.javaClass.methods.first { it.name == "removePrimaryClipChangedListener" }
                remove.invoke(service, *argsFor(remove, proxy))
            } catch (e: Throwable) {
                Log.w(TAG, "removePrimaryClipChangedListener failed: ${e.message}")
            }
        }
        try { client?.asBinder()?.unlinkToDeath(clientDeath, 0) } catch (_: Throwable) {}
        client = null
        clipboard = null
        registeredListener = null
        lastClip = null
    }

    override fun hardenBackground(pkg: String?, listenerComponent: String?): String {
        if (pkg.isNullOrBlank()) return "no package"
        val log = StringBuilder()
        fun run(vararg cmd: String) {
            val out = sh(*cmd)
            log.append(cmd.joinToString(" ")).append(" -> ").append(out.ifBlank { "ok" }).append('\n')
        }
        run("cmd", "deviceidle", "whitelist", "+$pkg")
        run("cmd", "appops", "set", pkg, "RUN_IN_BACKGROUND", "allow")
        run("cmd", "appops", "set", pkg, "RUN_ANY_IN_BACKGROUND", "allow")
        run("am", "set-standby-bucket", pkg, "active")
        if (!listenerComponent.isNullOrBlank()) {
            // Turns notification access on without a trip to Settings, and re-enables it if it was dropped.
            run("cmd", "notification", "allow_listener", listenerComponent)
        }
        return log.toString()
    }

    override fun launchText(component: String?, text: String?): Boolean {
        if (component.isNullOrBlank() || text.isNullOrEmpty()) return false
        val out = sh(
            "am", "start", "--user", "0",
            "-n", component,
            "-a", "android.intent.action.PROCESS_TEXT",
            "-t", "text/plain",
            "--es", "android.intent.extra.PROCESS_TEXT", text,
            "-f", (0x10000000 or 0x00010000 or 0x00800000).toString() // NEW_TASK | NO_ANIMATION | EXCLUDE_FROM_RECENTS
        )
        return !out.contains("Error", ignoreCase = true)
    }

    // ---- clipboard internals -----------------------------------------------------------------

    private fun onPrimaryClipChanged() {
        val target = client ?: return
        try {
            val text = readClipText() ?: return
            // Some ROMs fire the callback twice for one copy.
            if (text == lastClip) return
            lastClip = text
            target.onClip(text)
        } catch (e: RemoteException) {
            Log.w(TAG, "App is gone, dropping clipboard listener")
            stopClipboard()
        } catch (e: Throwable) {
            Log.e(TAG, "Clip dispatch failed: ${e.message}", e)
        }
    }

    private fun readClipText(): String? {
        val service = clipboard ?: return null
        val get = service.javaClass.methods.first { it.name == "getPrimaryClip" }
        val clip = get.invoke(service, *argsFor(get, null)) as? android.content.ClipData ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).text?.toString()
    }

    private fun getClipboardService(): Any {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager.getMethod("getService", String::class.java)
            .invoke(null, "clipboard") as IBinder
        return Class.forName("android.content.IClipboard\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)!!
    }

    /** Wraps clipBinder as an IOnPrimaryClipChangedListener proxy (no local interface is attached). */
    private fun asClipListener(): Any =
        Class.forName("android.content.IOnPrimaryClipChangedListener\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, clipBinder)!!

    /**
     * IClipboard's signatures differ per release:
     *   (listener, pkg, userId) / (listener, pkg, attributionTag, userId) / (..., userId, deviceId)
     * so arguments are built from the parameter types instead of hardcoding an API level.
     */
    private fun argsFor(m: Method, listener: Any?): Array<Any?> {
        var strings = 0
        return m.parameterTypes.map { type ->
            when {
                type == String::class.java -> if (strings++ == 0) SHELL_PKG else null
                type == Int::class.javaPrimitiveType -> 0
                type.name == LISTENER_DESCRIPTOR -> listener
                else -> null
            }
        }.toTypedArray()
    }

    private fun sh(vararg cmd: String): String = try {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        if (!p.waitFor(8, TimeUnit.SECONDS)) p.destroyForcibly()
        out
    } catch (e: Throwable) {
        "error: ${e.message}"
    }

    private companion object {
        const val TAG = "MicrotagPriv"
        const val SHELL_PKG = "com.android.shell"
        const val LISTENER_DESCRIPTOR = "android.content.IOnPrimaryClipChangedListener"
    }
}
