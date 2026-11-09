package dev.siva.pinlight

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var hookStatus: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val noReply = Runnable {
        hookStatus.text = "System UI hook: NOT LOADED\nCheck LSPosed scope (System UI) and reboot."
    }

    private val pongReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(noReply)
            val text = intent.getStringExtra(Protocol.EXTRA_STATUS) ?: "unknown"
            hookStatus.text = "System UI hook: $text"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()

        val title = TextView(this).apply {
            text = "PinLight"
            textSize = 28f
            setPadding(0, 0, 0, pad / 2)
        }
        status = TextView(this).apply { textSize = 16f }
        hookStatus = TextView(this).apply {
            textSize = 16f
            setPadding(0, pad / 2, 0, pad)
        }

        val grant = Button(this).apply {
            text = "Notification access"
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        val preview = Button(this).apply {
            text = "Preview 4 pins (8s)"
            setOnClickListener { sendPreview() }
        }
        val refresh = Button(this).apply {
            text = "Resend real pins"
            setOnClickListener { requestRefresh() }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            // Android 15+ draws edge-to-edge; keep content clear of the status bar.
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(pad, bars.top + pad, pad, bars.bottom + pad)
                insets
            }
            addView(title)
            addView(status)
            addView(hookStatus)
            addView(grant)
            addView(preview)
            addView(refresh)
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        status.text = if (hasAccess()) {
            "Notification access: on"
        } else {
            "Notification access: OFF — tap below to grant."
        }
        pingHook()
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(pongReceiver, IntentFilter(Protocol.ACTION_PONG), Context.RECEIVER_EXPORTED)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(pongReceiver)
        handler.removeCallbacks(noReply)
    }

    private fun pingHook() {
        hookStatus.text = "System UI hook: checking…"
        sendBroadcast(Intent(Protocol.ACTION_PING).setPackage(Protocol.SYSTEMUI))
        handler.removeCallbacks(noReply)
        handler.postDelayed(noReply, 1500)
    }

    private fun hasAccess(): Boolean {
        val me = ComponentName(this, PinListenerService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return enabled.split(":").contains(me)
    }

    // Shows sample pins even while unlocked so you can check placement.
    private fun sendPreview() {
        val colors = intArrayOf(
            Color.parseColor("#25D366"),
            Color.parseColor("#EA4335"),
            Color.parseColor("#2AABEE"),
            Color.parseColor("#E1306C")
        )
        val icons = ArrayList<Bitmap>()
        for (i in colors.indices) icons.add(sampleMask())

        val intent = Intent(Protocol.ACTION_UPDATE)
            .setPackage(Protocol.SYSTEMUI)
            .putExtra(Protocol.EXTRA_PACKAGES, arrayOf("a", "b", "c", "d"))
            .putExtra(Protocol.EXTRA_COLORS, colors)
            .putParcelableArrayListExtra(Protocol.EXTRA_ICONS, icons)
            .putExtra(Protocol.EXTRA_PREVIEW, true)
        sendBroadcast(intent)
        handler.postDelayed({ requestRefresh() }, 8000)
    }

    private fun requestRefresh() {
        sendBroadcast(Intent(Protocol.ACTION_REQUEST).setPackage(Protocol.SELF))
    }

    private fun sampleMask(): Bitmap {
        val size = Protocol.ICON_PX
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        Canvas(bitmap).drawCircle(size / 2f, size / 2f, size * 0.42f, paint)
        return bitmap
    }
}
