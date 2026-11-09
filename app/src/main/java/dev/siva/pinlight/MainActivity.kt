package dev.siva.pinlight

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import dev.siva.pinlight.ui.AodPreviewView
import dev.siva.pinlight.ui.SwatchView

class MainActivity : Activity() {

    private lateinit var settings: PinSettings
    private lateinit var preview: AodPreviewView
    private lateinit var status: TextView
    private lateinit var hookStatus: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val dp by lazy { resources.displayMetrics.density }

    private val pushRunnable = Runnable { PinSettings.push(this) }
    private val noReply = Runnable {
        hookStatus.text = "System UI hook: not loaded — check the Vector scope and reboot"
        hookStatus.setTextColor(WARN)
    }

    private val pongReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(noReply)
            hookStatus.text = "System UI hook: " + (intent.getStringExtra(Protocol.EXTRA_STATUS) ?: "unknown")
            hookStatus.setTextColor(MUTED)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        settings = PinSettings.load(this)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            addView(column)
            // Android 15+ draws edge-to-edge; keep content clear of the system bars.
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                column.setPadding(px(20), bars.top + px(16), px(20), bars.bottom + px(24))
                insets
            }
        }

        // Header
        column.addView(text("PinLight", 30f, TEXT, bold = true))
        status = text("", 13f, MUTED)
        hookStatus = text("", 13f, MUTED)
        column.addView(status)
        column.addView(hookStatus)

        // Live preview
        preview = AodPreviewView(this)
        preview.settings = settings
        column.addView(card(preview, top = 16))

        // Clock
        val clock = section(column, "Clock")
        clock.addView(slider("Size", 30, 100, settings.clockScale, "%") { settings.clockScale = it })
        clock.addView(label("Colour"))
        clock.addView(swatches(CLOCK_COLORS, settings.clockColor, multi = false) { settings.clockColor = it })
        clock.addView(slider("Move left / right", -150, 150, settings.clockOffsetX, " dp") { settings.clockOffsetX = it })
        clock.addView(slider("Move up / down", -350, 350, settings.clockOffsetY, " dp") { settings.clockOffsetY = it })

        // Notifications
        val pins = section(column, "Notification icons")
        pins.addView(slider("Show up to", 1, PinSettings.MAX_PINS_LIMIT, settings.maxPins, "") {
            settings.maxPins = it
            handler.postDelayed({ requestRefresh() }, 300)
        })
        pins.addView(slider("Per row", 1, 8, settings.perRow, "") { settings.perRow = it })
        pins.addView(slider("Icon size", 10, 32, settings.iconSize, " dp") { settings.iconSize = it })
        pins.addView(slider("Spacing", 4, 40, settings.iconSpacing, " dp") { settings.iconSpacing = it })
        pins.addView(slider("Gap below clock", 0, 120, settings.gapBelowClock, " dp") { settings.gapBelowClock = it })
        pins.addView(label("Colour"))
        pins.addView(swatches(ICON_COLORS, settings.iconColor, multi = true) { settings.iconColor = it })

        // Actions
        val actions = section(column, "Setup")
        actions.addView(button("Show test icons for 8 s") { sendPreview() })
        actions.addView(button("Notification access") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        actions.addView(button("Reset look to defaults") {
            PinSettings().save(this)
            recreate()
            handler.postDelayed({ requestRefresh() }, 300)
        })

        setContentView(scroll)
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(pongReceiver, IntentFilter(Protocol.ACTION_PONG), Context.RECEIVER_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        status.text = if (hasAccess()) "Notification access: on" else "Notification access: OFF — tap Notification access below"
        status.setTextColor(if (hasAccess()) MUTED else WARN)
        pingHook()
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(pongReceiver)
        handler.removeCallbacks(noReply)
    }

    // Save, redraw the preview, and push to System UI (debounced while dragging).
    private fun changed() {
        settings.save(this)
        preview.settings = settings
        handler.removeCallbacks(pushRunnable)
        handler.postDelayed(pushRunnable, 120)
    }

    // ---------- UI building blocks ----------

    private fun section(parent: LinearLayout, title: String): LinearLayout {
        val heading = text(title.uppercase(), 12f, ACCENT, bold = true)
        heading.letterSpacing = 0.08f
        heading.setPadding(px(4), px(24), 0, px(8))
        parent.addView(heading)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(8), px(16), px(12))
        }
        parent.addView(card(body, top = 0))
        return body
    }

    private fun card(content: View, top: Int): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(CARD)
                cornerRadius = 20 * dp
            }
            setPadding(px(4), px(12), px(4), px(12))
            addView(content)
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = px(top)
        wrap.layoutParams = lp
        return wrap
    }

    private fun slider(name: String, min: Int, max: Int, value: Int, suffix: String, onChange: (Int) -> Unit): View {
        val title = text(name, 15f, TEXT)
        val valueText = text("$value$suffix", 15f, MUTED)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, px(10), 0, 0)
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(valueText)
        }
        val bar = SeekBar(this).apply {
            this.min = min
            this.max = max
            progress = value.coerceIn(min, max)
            progressTintList = ColorStateList.valueOf(ACCENT)
            thumbTintList = ColorStateList.valueOf(ACCENT)
            progressBackgroundTintList = ColorStateList.valueOf(TRACK)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    valueText.text = "$progress$suffix"
                    onChange(progress)
                    changed()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            addView(bar)
        }
    }

    private fun swatches(colors: IntArray, selected: Int, multi: Boolean, onPick: (Int) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chips = ArrayList<SwatchView>()
        for (color in colors) {
            val chip = SwatchView(this, color, multi)
            chip.picked = color == selected
            chip.setOnClickListener {
                for (c in chips) c.picked = c === chip
                onPick(color)
                changed()
            }
            chips.add(chip)
            row.addView(chip)
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun label(value: String): TextView {
        val t = text(value, 15f, TEXT)
        t.setPadding(0, px(14), 0, px(6))
        return t
    }

    private fun button(value: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = value
            isAllCaps = false
            setTextColor(TEXT)
            background = GradientDrawable().apply {
                setColor(TRACK)
                cornerRadius = 14 * dp
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(48))
            lp.topMargin = px(8)
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun px(value: Int): Int = (value * dp).toInt()

    // ---------- Hook status / testing ----------

    private fun hasAccess(): Boolean {
        val me = ComponentName(this, PinListenerService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return enabled.split(":").contains(me)
    }

    private fun pingHook() {
        hookStatus.text = "System UI hook: checking…"
        hookStatus.setTextColor(MUTED)
        sendBroadcast(Intent(Protocol.ACTION_PING).setPackage(Protocol.SYSTEMUI))
        handler.removeCallbacks(noReply)
        handler.postDelayed(noReply, 1500)
    }

    // Shows sample icons on screen even while unlocked, using the current look.
    private fun sendPreview() {
        val count = settings.maxPins
        val colors = IntArray(count) { SAMPLE[it % SAMPLE.size] }
        val icons = ArrayList<Bitmap>()
        for (i in 0 until count) icons.add(sampleMask())

        val intent = Intent(Protocol.ACTION_UPDATE)
            .setPackage(Protocol.SYSTEMUI)
            .putExtra(Protocol.EXTRA_PACKAGES, Array(count) { "sample$it" })
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

    companion object {
        private val BG = 0xFF0E0E10.toInt()
        private val CARD = 0xFF1A1A1F.toInt()
        private val TRACK = 0xFF2C2C33.toInt()
        private val TEXT = 0xFFECECEF.toInt()
        private val MUTED = 0xFF9A9AA3.toInt()
        private val ACCENT = 0xFFA8C7FA.toInt()
        private val WARN = 0xFFF2B8B5.toInt()

        // 0 = keep System UI's own colour (clock) / each app's colour (icons).
        private val CLOCK_COLORS = intArrayOf(
            0, 0xFFFFFFFF.toInt(), 0xFFA8C7FA.toInt(), 0xFF81C995.toInt(), 0xFFFDD663.toInt(),
            0xFFF6AEA9.toInt(), 0xFFD7AEFB.toInt(), 0xFFFF8BCB.toInt(), 0xFF78D9EC.toInt(), 0xFF9AA0A6.toInt()
        )
        private val ICON_COLORS = intArrayOf(
            0, 0xFFFFFFFF.toInt(), 0xFFA8C7FA.toInt(), 0xFF81C995.toInt(), 0xFFFDD663.toInt(),
            0xFFF6AEA9.toInt(), 0xFFD7AEFB.toInt(), 0xFFFF8BCB.toInt(), 0xFF78D9EC.toInt()
        )
        private val SAMPLE = intArrayOf(
            0xFF25D366.toInt(), 0xFFEA4335.toInt(), 0xFF2AABEE.toInt(),
            0xFFE1306C.toInt(), 0xFFFBBC04.toInt(), 0xFF8AB4F8.toInt()
        )
    }
}
