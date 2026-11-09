package dev.siva.pinlight.hook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.view.View
import dev.siva.pinlight.PinSettings
import kotlin.math.abs

// Draws the pins under the AOD clock: centred rows, newest first.
class PinView(context: Context) : View(context) {

    class Pin(val mask: Bitmap, val color: Int)

    var settings = PinSettings()
        set(value) {
            field = value
            invalidate()
        }

    // Fallback burn-in nudge, used only when the clock can't be found
    // (the clock anchor already moves with System UI's own burn-in shift).
    var shiftX = 0f
    var shiftY = 0f

    private var pins: List<Pin> = emptyList()
    private var anchorX = -1f
    private var clockBottom = -1f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
    private val density = resources.displayMetrics.density

    fun setPins(list: List<Pin>) {
        pins = list
        invalidate()
    }

    fun hasPins(): Boolean = pins.isNotEmpty()

    // The transformed clock's bottom-centre, in screen coordinates.
    fun setAnchor(centerX: Float, bottom: Float) {
        if (abs(centerX - anchorX) < 0.5f && abs(bottom - clockBottom) < 0.5f) return
        anchorX = centerX
        clockBottom = bottom
        invalidate()
    }

    fun clearAnchor() {
        anchorX = -1f
        clockBottom = -1f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (pins.isEmpty()) return

        val size = settings.iconSize * density
        val gap = settings.iconSpacing * density
        val perRow = settings.perRow.coerceAtLeast(1)
        val hasAnchor = anchorX >= 0f
        val cx = if (hasAnchor) anchorX else width / 2f + shiftX
        var top = if (hasAnchor) {
            clockBottom + settings.gapBelowClock * density
        } else {
            height * FALLBACK_Y + shiftY
        }

        var index = 0
        while (index < pins.size) {
            val inRow = minOf(perRow, pins.size - index)
            val rowWidth = inRow * size + (inRow - 1) * gap
            var left = cx - rowWidth / 2f
            for (i in 0 until inRow) {
                val pin = pins[index + i]
                val color = if (settings.iconColor != 0) settings.iconColor else pin.color
                dst.set(left, top, left + size, top + size)
                paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(pin.mask, null, dst, paint)
                left += size + gap
            }
            index += inRow
            top += size + gap
        }
    }

    companion object {
        private const val FALLBACK_Y = 0.55f
    }
}
