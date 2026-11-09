package dev.siva.pinlight.hook

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.WindowManager
import dev.siva.pinlight.Protocol
import kotlin.random.Random

// Runs inside System UI. Owns one transparent window across the top of the
// screen and shows pins only while the phone is locked (lock screen + AOD).
class PinOverlay(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val display = context.getSystemService(DisplayManager::class.java)
        .getDisplay(Display.DEFAULT_DISPLAY)

    private lateinit var windowManager: WindowManager
    private lateinit var view: PinView
    private var previewUntil = 0L
    private val density = context.resources.displayMetrics.density

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            onPinsReceived(intent)
        }
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_TIME_TICK) shiftForBurnIn()
            updateVisibility()
        }
    }

    private val updateVisibilityRunnable = Runnable { updateVisibility() }

    // Returns a status line shown in the PinLight app.
    fun start(): String {
        val cutout = display.cutout?.boundingRectTop
        if (cutout == null || cutout.isEmpty) return "Failed: no camera cutout found"

        // Android 16 wants system windows added from a window context.
        val windowContext = context.createDisplayContext(display)
            .createWindowContext(TYPE_SECURE_SYSTEM_OVERLAY, null)
        windowManager = windowContext.getSystemService(WindowManager::class.java)
        view = PinView(windowContext)
        view.cutout = Rect(cutout)
        view.visibility = View.GONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            cutout.bottom + cutout.height() / 2,
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

        val stateFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }
        context.registerReceiver(stateReceiver, stateFilter, null, handler, Context.RECEIVER_NOT_EXPORTED)

        // System UI may have restarted; ask the app for the current pins.
        context.sendBroadcast(Intent(Protocol.ACTION_REQUEST).setPackage(Protocol.SELF))
        return "Connected — cutout $cutout"
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
        SystemUiHook.log("received ${pins.size} pins")

        if (intent.getBooleanExtra(Protocol.EXTRA_PREVIEW, false)) {
            previewUntil = SystemClock.uptimeMillis() + PREVIEW_MS
            handler.removeCallbacks(updateVisibilityRunnable)
            handler.postDelayed(updateVisibilityRunnable, PREVIEW_MS + 100)
        }
        updateVisibility()
    }

    private fun updateVisibility() {
        val portrait = display.rotation == Surface.ROTATION_0
        val locked = keyguard.isKeyguardLocked || !power.isInteractive
        val previewing = SystemClock.uptimeMillis() < previewUntil
        val show = view.hasPins() && portrait && (locked || previewing)
        view.visibility = if (show) View.VISIBLE else View.GONE
    }

    // Static pixels burn in. Nudge everything a few px each minute (TIME_TICK
    // still fires while dozing on AOD).
    private fun shiftForBurnIn() {
        val range = (BURN_IN_SHIFT_DP * density).toInt()
        view.shiftX = Random.nextInt(-range, range + 1).toFloat()
        view.shiftY = Random.nextInt(-range / 2, range / 2 + 1).toFloat()
        view.invalidate()
    }

    companion object {
        // WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY (hidden API).
        // Draws above the keyguard; System UI holds the permission for it.
        private const val TYPE_SECURE_SYSTEM_OVERLAY = 2015
        private const val PREVIEW_MS = 8000L
        private const val BURN_IN_SHIFT_DP = 2f
    }
}
