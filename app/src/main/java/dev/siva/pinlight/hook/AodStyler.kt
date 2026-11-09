package dev.siva.pinlight.hook

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import dev.siva.pinlight.PinSettings

// Restyles System UI's own AOD: resizes, moves and recolours the clock and
// hides everything else. Views are matched by resource id / class name taken
// from the Pixel 7 Android 16 AOD dump, so a Pixel update can only make
// something reappear, never crash.
class AodStyler(private val onClockBottom: (centerX: Float, bottom: Float) -> Unit) {

    var settings = PinSettings()
        set(value) {
            field = value
            root?.invalidate()
        }

    private var root: View? = null
    private var clock: View? = null
    private val hidden = HashMap<View, Float>() // view -> alpha before we hid it
    private var active = false
    private var dumped = false
    private var lastScan = 0L

    private val transform = Matrix()
    private val bounds = RectF()
    private val location = IntArray(2)
    private val layerPaint = Paint()
    private var appliedColor = 0

    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        if (active) enforce()
        true
    }

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (active) scan()
    }

    fun enter() {
        if (active) return
        val shade = findShadeRoot()
        if (shade == null) {
            PinLightModule.log("AOD: NotificationShade window not found")
            return
        }
        root = shade
        active = true
        scan()
        if (!dumped && clock != null) {
            dumped = true
            dump(shade, 0)
        }
        shade.viewTreeObserver.addOnPreDrawListener(preDraw)
        shade.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        enforce()
        shade.invalidate()
    }

    fun exit() {
        if (!active) return
        active = false
        root?.viewTreeObserver?.let {
            it.removeOnPreDrawListener(preDraw)
            it.removeOnGlobalLayoutListener(layoutListener)
        }
        for ((view, alpha) in hidden) view.alpha = alpha
        hidden.clear()
        clock?.let {
            it.animationMatrix = null
            if (appliedColor != 0) it.setLayerType(View.LAYER_TYPE_NONE, null)
        }
        appliedColor = 0
        clock = null
    }

    private fun scan() {
        val shade = root ?: return
        lastScan = SystemClock.uptimeMillis()
        val all = ArrayList<View>()
        collect(shade, all)

        val found = pickClock(all)
        if (found !== clock) {
            clock?.animationMatrix = null
            appliedColor = 0
            clock = found
        }

        for (view in all) {
            if (hidden.containsKey(view)) continue
            if (shouldHide(view)) hidden[view] = view.alpha
        }
    }

    private fun enforce() {
        for (view in hidden.keys) {
            if (view.alpha != 0f) view.alpha = 0f
        }

        val c = clock
        if (c == null || !c.isAttachedToWindow || c.width == 0) {
            // Keyguard may still be laying out right after "going to sleep".
            if (SystemClock.uptimeMillis() - lastScan > 100) scan()
            return
        }

        // Scale around the clock's centre, then move. Uses the animation matrix so
        // System UI's own translation (burn-in shift) keeps working on top.
        val density = c.resources.displayMetrics.density
        val scale = settings.clockScale / 100f
        transform.setScale(scale, scale, c.width / 2f, c.height / 2f)
        transform.postTranslate(settings.clockOffsetX * density, settings.clockOffsetY * density)
        if (c.animationMatrix != transform) c.animationMatrix = Matrix(transform)

        applyClockColor(c)

        // Where the transformed clock ends up, in screen coordinates.
        val parent = c.parent as? View ?: return
        parent.getLocationInWindow(location)
        bounds.set(0f, 0f, c.width.toFloat(), c.height.toFloat())
        transform.mapRect(bounds)
        bounds.offset(location[0] + c.left + c.translationX, location[1] + c.top + c.translationY)
        onClockBottom(bounds.centerX(), bounds.bottom)
    }

    // Recolour every pixel of the clock through a hardware layer, so it works
    // whatever way the clock face draws its digits.
    private fun applyClockColor(c: View) {
        val color = settings.clockColor
        if (color == 0) {
            if (appliedColor != 0) {
                c.setLayerType(View.LAYER_TYPE_NONE, null)
                appliedColor = 0
            }
            return
        }
        if (appliedColor != color || c.layerType != View.LAYER_TYPE_HARDWARE) {
            layerPaint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            c.setLayerType(View.LAYER_TYPE_HARDWARE, layerPaint)
            appliedColor = color
        }
    }

    private fun pickClock(all: List<View>): View? {
        // Exact match first (Android 16 Pixel clock), then any "clock" view.
        for (view in all) {
            if (view.javaClass.simpleName == "FlexClockViewGroup" && view.width > 0) return view
        }
        var best: View? = null
        var bestArea = 0L
        val screenArea = (root?.width ?: 0).toLong() * (root?.height ?: 0)
        for (view in all) {
            val name = (idName(view) + view.javaClass.simpleName).lowercase()
            if (!name.contains("clock")) continue
            if (!view.isShown || view.width == 0 || view.height == 0) continue
            val area = view.width.toLong() * view.height
            if (area > screenArea * 6 / 10) continue
            if (area > bestArea) {
                best = view
                bestArea = area
            }
        }
        return best
    }

    private fun shouldHide(view: View): Boolean {
        if (idName(view) in HIDE_IDS) return true
        return view.javaClass.simpleName in HIDE_CLASSES
    }

    private fun collect(view: View, out: MutableList<View>) {
        out.add(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) collect(view.getChildAt(i), out)
        }
    }

    private fun idName(view: View): String {
        if (view.id == View.NO_ID) return ""
        return try {
            view.resources.getResourceEntryName(view.id)
        } catch (e: Exception) {
            ""
        }
    }

    // One-time map of visible AOD views, for targeting new ids after updates.
    private fun dump(view: View, depth: Int) {
        if (depth > 25) return
        if (view.isShown && view.width > 0 && view.height > 0) {
            view.getLocationInWindow(location)
            PinLightModule.log(
                "DUMP ${"  ".repeat(depth)}${view.javaClass.simpleName} id=${idName(view)} " +
                    "at ${location[0]},${location[1]} ${view.width}x${view.height} a=${view.alpha}"
            )
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) dump(view.getChildAt(i), depth + 1)
        }
    }

    // System UI is a platform app, so reading WindowManagerGlobal is allowed here.
    private fun findShadeRoot(): View? {
        return try {
            val wmgClass = Class.forName("android.view.WindowManagerGlobal")
            val wmg = wmgClass.getMethod("getInstance").invoke(null)
            val field = wmgClass.getDeclaredField("mViews")
            field.isAccessible = true
            val views = ArrayList(field.get(wmg) as List<*>)
            views.filterIsInstance<View>().firstOrNull {
                val lp = it.layoutParams as? WindowManager.LayoutParams
                lp?.title?.toString() == "NotificationShade"
            }
        } catch (t: Throwable) {
            PinLightModule.log("AOD: root lookup failed: $t")
            null
        }
    }

    companion object {
        // From the Pixel 7 / Android 16 AOD dump, plus older names as fallbacks.
        private val HIDE_IDS = setOf(
            "keyguard_indication_area",      // "Charged", battery text
            "ambient_indication_container",  // Now Playing
            "date_smartspace_view_large",    // date + weather
            "date_smartspace_view",
            "bc_smartspace_view",
            "keyguard_slice_view",
            "device_entry_icon_view",        // fingerprint icon
            "lock_icon_view",
            "notification_icon_area",        // Pixel's AOD icon row
            "aod_notification_icon_container"
        )
        private val HIDE_CLASSES = setOf(
            "KeyguardIndicationArea",
            "AmbientIndicationContainer",
            "DateSmartspaceView",
            "BcSmartspaceView",
            "DeviceEntryIconView",
            "NotificationIconContainer"
        )
    }
}
