package dev.siva.pinlight.hook

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager

// Restyles System UI's own AOD while the display dozes: shrinks the clock and
// hides everything else. Views are matched by resource id / class name, not by
// System UI class hierarchy, so a Pixel update can only make something
// reappear, never crash.
class AodStyler(private val onClockBottom: (centerX: Float, bottom: Float) -> Unit) {

    private var root: View? = null
    private var clock: View? = null
    private val hidden = HashMap<View, Float>() // view -> alpha before we hid it
    private var active = false
    private var dumped = false
    private val rect = Rect()

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
        if (!dumped) {
            dumped = true
            dump(shade, 0)
        }
        shade.viewTreeObserver.addOnPreDrawListener(preDraw)
        shade.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        enforce()
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
            it.scaleX = 1f
            it.scaleY = 1f
        }
        clock = null
    }

    private fun scan() {
        val shade = root ?: return
        val all = ArrayList<View>()
        collect(shade, all)

        clock = pickClock(all, shade)
        val clockView = clock

        for (view in all) {
            if (hidden.containsKey(view)) continue
            if (!matches(view, HIDE_KEYS)) continue
            if (view === clockView) continue
            if (clockView != null && isAncestor(view, clockView)) continue
            hidden[view] = view.alpha
        }
    }

    private fun enforce() {
        for (view in hidden.keys) {
            if (view.alpha != 0f) view.alpha = 0f
        }
        val c = clock ?: return
        if (!c.isAttachedToWindow) {
            scan()
            return
        }
        if (c.scaleX != CLOCK_SCALE) {
            c.pivotX = c.width / 2f
            c.pivotY = c.height / 2f
            c.scaleX = CLOCK_SCALE
            c.scaleY = CLOCK_SCALE
        }
        if (c.getGlobalVisibleRect(rect)) {
            onClockBottom(rect.exactCenterX(), rect.bottom.toFloat())
        }
    }

    // The large clock: the biggest visible "clock" view that isn't a
    // full-screen container.
    private fun pickClock(all: List<View>, shade: View): View? {
        val screenArea = shade.width.toLong() * shade.height
        var best: View? = null
        var bestArea = 0L
        for (view in all) {
            if (!matches(view, CLOCK_KEYS)) continue
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

    private fun matches(view: View, keys: Array<String>): Boolean {
        val id = idName(view).lowercase()
        val cls = view.javaClass.simpleName.lowercase()
        for (key in keys) {
            if (id.contains(key) || cls.contains(key)) return true
        }
        return false
    }

    private fun isAncestor(parent: View, child: View): Boolean {
        var p = child.parent
        while (p != null) {
            if (p === parent) return true
            p = p.parent
        }
        return false
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

    // One-time map of visible AOD views, so new ids can be targeted precisely.
    private fun dump(view: View, depth: Int) {
        if (depth > 25) return
        if (view.isShown && view.width > 0 && view.height > 0) {
            view.getGlobalVisibleRect(rect)
            PinLightModule.log(
                "DUMP ${"  ".repeat(depth)}${view.javaClass.simpleName} " +
                    "id=${idName(view)} $rect a=${view.alpha}"
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
        const val CLOCK_SCALE = 0.6f

        private val CLOCK_KEYS = arrayOf("clock")

        // Date/weather, charging text, fingerprint icon, Pixel's AOD icon row.
        private val HIDE_KEYS = arrayOf(
            "smartspace",
            "keyguard_slice",
            "status_area",
            "indication",
            "device_entry_icon",
            "lock_icon",
            "udfps",
            "notification_icon",
            "notificationicon",
            "shelf",
            "now_playing",
            "ambient"
        )
    }
}
