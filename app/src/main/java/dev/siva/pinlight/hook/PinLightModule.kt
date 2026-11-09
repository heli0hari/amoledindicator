package dev.siva.pinlight.hook

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.siva.pinlight.Protocol
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

// Modern libxposed (API 101/102) entry, listed in META-INF/xposed/java_init.list.
// No method hooks: once System UI has started, add our own overlay window.
// No System UI internal classes are touched, so Pixel updates rarely break it.
class PinLightModule : XposedModule() {

    private var processName = ""
    private var started = false

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        instance = this
        log("attached to process '$processName' (framework $frameworkName, API $apiVersion)")
    }

    // Fallback entry: some frameworks call one phase but not the other.
    override fun onPackageLoaded(param: PackageLoadedParam) {
        log("onPackageLoaded: ${param.packageName}")
        startIfSystemUi(param.packageName)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        log("onPackageReady: ${param.packageName}")
        startIfSystemUi(param.packageName)
    }

    private fun startIfSystemUi(packageName: String) {
        if (packageName != Protocol.SYSTEMUI) return
        if (!processName.startsWith(Protocol.SYSTEMUI)) {
            log("skipping: process is '$processName', not System UI")
            return
        }
        if (processName != Protocol.SYSTEMUI) return
        if (started) return
        started = true

        log("module loaded into System UI")
        status = "Loaded, waiting for System UI to start"

        // We are inside System UI's startup on its main thread. Anything posted
        // now runs after the Application is created, so no method hook is needed.
        waitForApplication(0)
    }

    private fun waitForApplication(attempt: Int) {
        Handler(Looper.getMainLooper()).postDelayed({
            val app = currentApplication()
            if (app != null) {
                boot(app)
            } else if (attempt < 50) {
                waitForApplication(attempt + 1)
            } else {
                status = "Failed: System UI Application never appeared"
                log(status)
            }
        }, if (attempt == 0) 0L else 200L)
    }

    private fun currentApplication(): Application? {
        return try {
            val activityThread = Class.forName("android.app.ActivityThread")
            activityThread.getMethod("currentApplication").invoke(null) as? Application
        } catch (t: Throwable) {
            log("currentApplication failed: $t")
            null
        }
    }

    private fun boot(app: Application) {
        // Register the status ping first, so the app can see failures too.
        try {
            app.registerReceiver(
                pingReceiver,
                IntentFilter(Protocol.ACTION_PING),
                Protocol.PERMISSION,
                Handler(Looper.getMainLooper()),
                Context.RECEIVER_EXPORTED
            )
        } catch (t: Throwable) {
            log("ping receiver failed: $t")
        }

        try {
            status = PinOverlay(app).start()
        } catch (t: Throwable) {
            status = "Failed: $t"
            Log.e(Protocol.LOG_TAG, "overlay failed", t)
        }
        log(status)
    }

    private val pingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val reply = Intent(Protocol.ACTION_PONG)
                .setPackage(Protocol.SELF)
                .putExtra(Protocol.EXTRA_STATUS, status)
            context.sendBroadcast(reply)
        }
    }

    companion object {
        private var instance: PinLightModule? = null
        private var status = "Not loaded"

        fun log(message: String) {
            Log.i(Protocol.LOG_TAG, message)
            try {
                instance?.log(Log.INFO, Protocol.LOG_TAG, message)
            } catch (t: Throwable) {
                // Framework log is optional.
            }
        }
    }
}
