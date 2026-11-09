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
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

// Modern libxposed (API 101/102) entry, listed in META-INF/xposed/java_init.list.
// Only hook: wait for System UI's Application.onCreate, then add our overlay.
// No System UI internal classes are touched, so Pixel updates rarely break it.
class PinLightModule : XposedModule() {

    private var processName = ""
    private var started = false

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        instance = this
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != Protocol.SYSTEMUI) return
        if (processName != Protocol.SYSTEMUI) return
        if (!param.isFirstPackage) return

        log("module loaded into System UI")
        status = "Loaded, waiting for System UI to start"

        val onCreate = Application::class.java.getDeclaredMethod("onCreate")
        hook(onCreate).intercept { chain ->
            val result = chain.proceed()
            val app = chain.thisObject as? Application
            if (app != null && !started) {
                started = true
                Handler(Looper.getMainLooper()).post { boot(app) }
            }
            result
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
