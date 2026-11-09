package dev.siva.pinlight

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()

        status = TextView(this).apply { textSize = 16f }

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
            setPadding(pad, pad * 3, pad, pad)
            addView(status)
            addView(grant)
            addView(preview)
            addView(refresh)
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        status.text = if (hasAccess()) {
            "Notification access: on\nModule: enable in LSPosed (scope: System UI), then restart System UI."
        } else {
            "Notification access: OFF — tap below to grant."
        }
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
        Handler(Looper.getMainLooper()).postDelayed({ requestRefresh() }, 8000)
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
