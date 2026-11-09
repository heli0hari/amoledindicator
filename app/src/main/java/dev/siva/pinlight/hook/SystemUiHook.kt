package dev.siva.pinlight.hook

import android.app.Application
import android.os.Handler
import android.os.Looper
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

        XposedHelpers.findAndHookMethod(Application::class.java, "onCreate", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (started) return
                started = true
                val app = param.thisObject as Application
                Handler(Looper.getMainLooper()).post {
                    try {
                        PinOverlay(app).start()
                        XposedBridge.log("PinLight: overlay started")
                    } catch (t: Throwable) {
                        XposedBridge.log("PinLight: failed to start")
                        XposedBridge.log(t)
                    }
                }
            }
        })
    }

    companion object {
        private var started = false
    }
}
