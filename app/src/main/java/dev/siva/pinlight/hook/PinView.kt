package dev.siva.pinlight.hook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.view.View

// Draws up to 4 pins: newest nearest the camera, alternating left/right.
//   [L2] [L1] (camera) [R1] [R2]   order: L1, R1, L2, R2
class PinView(context: Context) : View(context) {

    class Pin(val mask: Bitmap, val color: Int)

    var cutout = Rect()
    var shiftX = 0f
    var shiftY = 0f

    private var pins: List<Pin> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
    private val density = resources.displayMetrics.density

    fun setPins(list: List<Pin>) {
        pins = list
        invalidate()
    }

    fun hasPins(): Boolean = pins.isNotEmpty()

    override fun onDraw(canvas: Canvas) {
        if (pins.isEmpty() || cutout.isEmpty) return

        val size = (cutout.height() * SIZE_OF_CUTOUT)
            .coerceIn(MIN_ICON_DP * density, MAX_ICON_DP * density)
        val gapFromCamera = size * 0.6f
        val gapBetween = size * 0.55f
        val centerY = cutout.exactCenterY() + shiftY

        for (i in pins.indices) {
            val pin = pins[i]
            val onLeft = i % 2 == 0
            val slot = i / 2
            val offset = gapFromCamera + slot * (size + gapBetween)
            val left = if (onLeft) cutout.left - offset - size else cutout.right + offset

            dst.set(left + shiftX, centerY - size / 2f, left + shiftX + size, centerY + size / 2f)
            paint.colorFilter = PorterDuffColorFilter(pin.color, PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(pin.mask, null, dst, paint)
        }
    }

    companion object {
        private const val SIZE_OF_CUTOUT = 0.5f
        private const val MIN_ICON_DP = 12f
        private const val MAX_ICON_DP = 16f
    }
}
