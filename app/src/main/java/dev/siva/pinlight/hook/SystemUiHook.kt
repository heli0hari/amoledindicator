package dev.siva.pinlight.hook

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import dev.siva.pinlight.Protocol

// Only hook: wait for System UI to start, then add our own overlay window.
// No System UI internal classes are touched, so Pixel updates rarely break it.
class SystemUiHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != Protocol.SYSTEMUI) return
        if (lpparam.processName != Protocol.SYSTEMUI) return
        log("module loaded into System UI")
        status = "Loaded, waiting for System UI to start"

        XposedHelpers.findAndHookMethod(Application::class.java, "onCreate", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (started) return
                started = true
                val app = param.thisObject as Application
                Handler(Looper.getMainLooper()).post { boot(app) }
            }
        })
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
        private var started = false
        private var status = "Not loaded"

        fun log(message: String) {
            Log.i(Protocol.LOG_TAG, message)
            try {
                XposedBridge.log("PinLight: $message")
            } catch (t: Throwable) {
                // Some frameworks don't expose the bridge log.
            }
        }
    }
}
