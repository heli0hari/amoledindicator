package dev.siva.pinlight.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.WindowManager
import dev.siva.pinlight.Protocol
import kotlin.random.Random

// Runs inside System UI. Owns one transparent full-screen window and draws the
// pins under the clock, only while the display is in AOD (doze).
class PinOverlay(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
    private val density = context.resources.displayMetrics.density

    private lateinit var windowManager: WindowManager
    private lateinit var view: PinView
    private lateinit var styler: AodStyler
    private var previewUntil = 0L
    private var dozing = false

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            onPinsReceived(intent)
        }
    }

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            shiftForBurnIn()
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) onDisplayState()
        }

        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
    }

    private val updateVisibilityRunnable = Runnable { updateVisibility() }

    // Returns a status line shown in the PinLight app.
    fun start(): String {
        // Android 16 wants system windows added from a window context.
        val windowContext = context.createDisplayContext(display)
            .createWindowContext(TYPE_SECURE_SYSTEM_OVERLAY, null)
        windowManager = windowContext.getSystemService(WindowManager::class.java)
        view = PinView(windowContext)
        view.visibility = View.GONE
        styler = AodStyler { centerX, bottom -> view.setAnchor(centerX, bottom) }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            TYPE_SECURE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        params.fitInsetsTypes = 0
        params.title = "PinLight"
        windowManager.addView(view, params)

        // Only senders holding our signature permission (our own app) get through.
        context.registerReceiver(
            updateReceiver,
            IntentFilter(Protocol.ACTION_UPDATE),
            Protocol.PERMISSION,
            handler,
            Context.RECEIVER_EXPORTED
        )
        context.registerReceiver(
            tickReceiver,
            IntentFilter(Intent.ACTION_TIME_TICK),
            null,
            handler,
            Context.RECEIVER_NOT_EXPORTED
        )
        displayManager.registerDisplayListener(displayListener, handler)
        onDisplayState()

        // System UI may have restarted; ask the app for the current pins.
        context.sendBroadcast(Intent(Protocol.ACTION_REQUEST).setPackage(Protocol.SELF))
        return "Connected"
    }

    private fun onDisplayState() {
        val state = display.state
        val nowDozing = state == Display.STATE_DOZE || state == Display.STATE_DOZE_SUSPEND
        if (nowDozing == dozing) return
        dozing = nowDozing
        PinLightModule.log("display dozing=$dozing")
        if (dozing) {
            styler.enter()
        } else {
            styler.exit()
            view.clearAnchor()
        }
        updateVisibility()
    }

    private fun onPinsReceived(intent: Intent) {
        val packages = intent.getStringArrayExtra(Protocol.EXTRA_PACKAGES) ?: emptyArray()
        val colors = intent.getIntArrayExtra(Protocol.EXTRA_COLORS) ?: IntArray(0)
        val icons = intent.getParcelableArrayListExtra(Protocol.EXTRA_ICONS, Bitmap::class.java)
            ?: ArrayList()

        val pins = ArrayList<PinView.Pin>()
        for (i in packages.indices) {
            if (i >= colors.size || i >= icons.size || i >= Protocol.MAX_PINS) break
            pins.add(PinView.Pin(icons[i], colors[i]))
        }
        view.setPins(pins)
        PinLightModule.log("received ${pins.size} pins")

        if (intent.getBooleanExtra(Protocol.EXTRA_PREVIEW, false)) {
            previewUntil = SystemClock.uptimeMillis() + PREVIEW_MS
            handler.removeCallbacks(updateVisibilityRunnable)
            handler.postDelayed(updateVisibilityRunnable, PREVIEW_MS + 100)
        }
        updateVisibility()
    }

    private fun updateVisibility() {
        val portrait = display.rotation == Surface.ROTATION_0
        val previewing = SystemClock.uptimeMillis() < previewUntil
        val show = view.hasPins() && portrait && (dozing || previewing)
        view.visibility = if (show) View.VISIBLE else View.GONE
    }

    // Only matters when the clock anchor is missing; otherwise the pins follow
    // the clock, which System UI already shifts for burn-in.
    private fun shiftForBurnIn() {
        val range = (BURN_IN_SHIFT_DP * density).toInt()
        view.shiftX = Random.nextInt(-range, range + 1).toFloat()
        view.shiftY = Random.nextInt(-range, range + 1).toFloat()
        view.invalidate()
    }

    companion object {
        // WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY (hidden API).
        // Draws above the keyguard; System UI holds the permission for it.
        private const val TYPE_SECURE_SYSTEM_OVERLAY = 2015
        private const val PREVIEW_MS = 8000L
        private const val BURN_IN_SHIFT_DP = 3f
    }
}
