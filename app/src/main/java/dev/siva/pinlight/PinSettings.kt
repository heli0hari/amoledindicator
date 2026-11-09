package dev.siva.pinlight

import android.content.Context
import android.os.Bundle

// User look settings. Stored in the app, sent to System UI as a Bundle.
class PinSettings {
    var clockScale = 60     // percent of the normal clock size
    var clockColor = 0      // 0 = keep System UI's colour
    var clockOffsetX = 0    // dp
    var clockOffsetY = 0    // dp
    var maxPins = 4
    var perRow = 4
    var iconSize = 16       // dp
    var iconSpacing = 12    // dp
    var gapBelowClock = 20  // dp
    var iconColor = 0       // 0 = each app's own colour

    fun toBundle(): Bundle {
        val b = Bundle()
        b.putInt(K_CLOCK_SCALE, clockScale)
        b.putInt(K_CLOCK_COLOR, clockColor)
        b.putInt(K_CLOCK_X, clockOffsetX)
        b.putInt(K_CLOCK_Y, clockOffsetY)
        b.putInt(K_MAX_PINS, maxPins)
        b.putInt(K_PER_ROW, perRow)
        b.putInt(K_ICON_SIZE, iconSize)
        b.putInt(K_ICON_SPACING, iconSpacing)
        b.putInt(K_GAP, gapBelowClock)
        b.putInt(K_ICON_COLOR, iconColor)
        return b
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        val b = toBundle()
        for (key in b.keySet()) prefs.putInt(key, b.getInt(key))
        prefs.apply()
    }

    companion object {
        const val PREFS = "pinlight"

        // Hard limits, also used by the settings sliders.
        const val MAX_PINS_LIMIT = 12

        private const val K_CLOCK_SCALE = "clock_scale"
        private const val K_CLOCK_COLOR = "clock_color"
        private const val K_CLOCK_X = "clock_x"
        private const val K_CLOCK_Y = "clock_y"
        private const val K_MAX_PINS = "max_pins"
        private const val K_PER_ROW = "per_row"
        private const val K_ICON_SIZE = "icon_size"
        private const val K_ICON_SPACING = "icon_spacing"
        private const val K_GAP = "gap_below_clock"
        private const val K_ICON_COLOR = "icon_color"

        fun fromBundle(b: Bundle?): PinSettings {
            val s = PinSettings()
            if (b == null) return s
            s.clockScale = b.getInt(K_CLOCK_SCALE, s.clockScale)
            s.clockColor = b.getInt(K_CLOCK_COLOR, s.clockColor)
            s.clockOffsetX = b.getInt(K_CLOCK_X, s.clockOffsetX)
            s.clockOffsetY = b.getInt(K_CLOCK_Y, s.clockOffsetY)
            s.maxPins = b.getInt(K_MAX_PINS, s.maxPins)
            s.perRow = b.getInt(K_PER_ROW, s.perRow)
            s.iconSize = b.getInt(K_ICON_SIZE, s.iconSize)
            s.iconSpacing = b.getInt(K_ICON_SPACING, s.iconSpacing)
            s.gapBelowClock = b.getInt(K_GAP, s.gapBelowClock)
            s.iconColor = b.getInt(K_ICON_COLOR, s.iconColor)
            return s
        }

        fun load(context: Context): PinSettings {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val b = Bundle()
            for ((key, value) in prefs.all) {
                if (value is Int) b.putInt(key, value)
            }
            return fromBundle(b)
        }

        // Push the saved settings to System UI.
        fun push(context: Context) {
            val intent = android.content.Intent(Protocol.ACTION_SETTINGS)
                .setPackage(Protocol.SYSTEMUI)
                .putExtra(Protocol.EXTRA_SETTINGS, load(context).toBundle())
            context.sendBroadcast(intent)
        }
    }
}
