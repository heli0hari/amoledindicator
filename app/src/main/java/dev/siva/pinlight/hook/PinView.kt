package dev.siva.pinlight.hook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.view.View
import kotlin.math.abs

// Draws up to 4 pins in one centred row under the AOD clock, newest on the left.
class PinView(context: Context) : View(context) {

    class Pin(val mask: Bitmap, val color: Int)

    // Fallback burn-in nudge, used only when the clock can't be found
    // (the clock anchor already moves with System UI's own burn-in shift).
    var shiftX = 0f
    var shiftY = 0f

    private var pins: List<Pin> = emptyList()
    private var anchorX = -1f
    private var anchorY = -1f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
    private val density = resources.displayMetrics.density

    fun setPins(list: List<Pin>) {
        pins = list
        invalidate()
    }

    fun hasPins(): Boolean = pins.isNotEmpty()

    // Called with the clock's bottom-centre in screen coordinates.
    fun setAnchor(centerX: Float, clockBottom: Float) {
        val y = clockBottom + GAP_BELOW_CLOCK_DP * density
        if (abs(centerX - anchorX) < 0.5f && abs(y - anchorY) < 0.5f) return
        anchorX = centerX
        anchorY = y
        invalidate()
    }

    fun clearAnchor() {
        anchorX = -1f
        anchorY = -1f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (pins.isEmpty()) return

        val size = ICON_DP * density
        val gap = GAP_BETWEEN_DP * density
        val hasAnchor = anchorX >= 0f
        val cx = if (hasAnchor) anchorX else width / 2f + shiftX
        val top = if (hasAnchor) anchorY else height * FALLBACK_Y + shiftY

        val total = pins.size * size + (pins.size - 1) * gap
        var left = cx - total / 2f

        for (pin in pins) {
            dst.set(left, top, left + size, top + size)
            paint.colorFilter = PorterDuffColorFilter(pin.color, PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(pin.mask, null, dst, paint)
            left += size + gap
        }
    }

    companion object {
        private const val ICON_DP = 16f
        private const val GAP_BETWEEN_DP = 12f
        private const val GAP_BELOW_CLOCK_DP = 20f
        private const val FALLBACK_Y = 0.55f
    }
}
