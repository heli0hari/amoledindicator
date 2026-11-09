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
// Once System UI has started we add our own overlay window. The only hooks are
// System UI's "started going to sleep / waking up" events, used to restyle the
// AOD before its screen-off animation. If those classes ever disappear, the
// overlay falls back to public screen/display signals.
class PinLightModule : XposedModule() {

    private var processName = ""
    private var started = false

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        if (processName != Protocol.SYSTEMUI) {
            // e.g. PinLight's own app process: nothing to do here.
            safeDetach()
            return
        }
        log("attached to System UI (framework $frameworkName, API $apiVersion)")
    }

    // Fallback entry: some frameworks call one phase but not the other.
    override fun onPackageLoaded(param: PackageLoadedParam) {
        startIfSystemUi(param.packageName, param.defaultClassLoader)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        startIfSystemUi(param.packageName, param.classLoader)
    }

    private fun startIfSystemUi(packageName: String, classLoader: ClassLoader) {
        if (packageName != Protocol.SYSTEMUI) return
        if (processName != Protocol.SYSTEMUI) return
        if (started) return
        started = true

        log("module loaded into System UI")
        hookSleepEvents(classLoader)
        status = "Loaded, waiting for System UI to start"

        // We are inside System UI's startup on its main thread. Anything posted
        // now runs after the Application is created, so no method hook is needed.
        waitForApplication(0)
    }

    // Earliest moment the phone starts sleeping / waking. Tries every known
    // home of these events; whichever exists on this Android version is used.
    private fun hookSleepEvents(classLoader: ClassLoader) {
        val targets = arrayOf(
            "com.android.systemui.keyguard.WakefulnessLifecycle",
            "com.android.systemui.power.domain.interactor.PowerInteractor"
        )
        val sleepNames = arrayOf("dispatchStartedGoingToSleep", "onStartedGoingToSleep")
        val wakeNames = arrayOf("dispatchStartedWakingUp", "onStartedWakingUp")
        var hooked = 0

        for (className in targets) {
            val cls = try {
                classLoader.loadClass(className)
            } catch (t: Throwable) {
                continue
            }
            for (method in cls.declaredMethods) {
                val asleep = when (method.name) {
                    in sleepNames -> true
                    in wakeNames -> false
                    else -> continue
                }
                try {
                    hook(method).intercept { chain ->
                        val result = chain.proceed()
                        SleepSignals.post(asleep)
                        result
                    }
                    hooked++
                } catch (t: Throwable) {
                    log("hook ${method.name} failed: $t")
                }
            }
        }
        log("sleep hooks installed: $hooked")
    }

    private fun safeDetach() {
        try {
            detach()
        } catch (t: Throwable) {
            // Older frameworks lack detach(); callbacks are harmless anyway.
        }
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
        private var status = "Not loaded"

        fun log(message: String) {
            Log.i(Protocol.LOG_TAG, message)
        }
    }
}
