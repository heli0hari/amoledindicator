package dev.siva.pinlight.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import dev.siva.pinlight.PinSettings
import java.util.Calendar

// Approximate Pixel 7 AOD, drawn in phone pixels (1080 x 2400) and scaled to fit.
// Clock box and density come from the real AOD dump.
class AodPreviewView(context: Context) : View(context) {

    var settings = PinSettings()
        set(value) {
            field = value
            invalidate()
        }

    private val dp = resources.displayMetrics.density
    private val phone = RectF()
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
        color = 0xFF2A2A30.toInt()
    }
    private val screenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cameraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C1C1F.toInt() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = (300 * dp).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    override fun onDraw(canvas: Canvas) {
        // Fit a 1080 x 2400 phone into the view height.
        val k = height / PHONE_H
        val phoneW = PHONE_W * k
        val left = (width - phoneW) / 2f
        phone.set(left, 0f, left + phoneW, height.toFloat())
        val corner = 18 * dp
        canvas.drawRoundRect(phone, corner, corner, screenPaint)
        canvas.drawRoundRect(phone, corner, corner, framePaint)
        canvas.drawCircle(left + 552 * k, 68 * k, 30 * k, cameraPaint)

        canvas.save()
        canvas.translate(left, 0f)
        canvas.scale(k, k)
        drawAod(canvas)
        canvas.restore()
    }

    // Everything below is in phone pixels.
    private fun drawAod(canvas: Canvas) {
        val s = settings
        val scale = s.clockScale / 100f
        val cx = CLOCK_CX + s.clockOffsetX * PHONE_DP
        val cy = CLOCK_CY + s.clockOffsetY * PHONE_DP

        val now = Calendar.getInstance()
        val hour12 = now.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        val hours = "%02d".format(hour12)
        val minutes = "%02d".format(now.get(Calendar.MINUTE))

        clockPaint.color = if (s.clockColor != 0) s.clockColor else DEFAULT_CLOCK
        // Two stacked lines filling the real clock box (478 x 759 px).
        clockPaint.textSize = 470f * scale
        canvas.drawText(hours, cx, cy - 25f * scale, clockPaint)
        canvas.drawText(minutes, cx, cy + 370f * scale, clockPaint)

        val clockBottom = cy + CLOCK_H / 2f * scale
        val size = s.iconSize * PHONE_DP
        val gap = s.iconSpacing * PHONE_DP
        val perRow = s.perRow.coerceAtLeast(1)
        var top = clockBottom + s.gapBelowClock * PHONE_DP
        var index = 0
        while (index < s.maxPins) {
            val inRow = minOf(perRow, s.maxPins - index)
            val rowWidth = inRow * size + (inRow - 1) * gap
            var x = cx - rowWidth / 2f
            for (i in 0 until inRow) {
                dotPaint.color = if (s.iconColor != 0) s.iconColor else SAMPLE[(index + i) % SAMPLE.size]
                canvas.drawCircle(x + size / 2f, top + size / 2f, size * 0.42f, dotPaint)
                x += size + gap
            }
            index += inRow
            top += size + gap
        }
    }

    companion object {
        private const val PHONE_W = 1080f
        private const val PHONE_H = 2400f
        private const val PHONE_DP = 2.625f  // Pixel 7 density
        private const val CLOCK_CX = 540f
        private const val CLOCK_CY = 1074f
        private const val CLOCK_H = 759f
        private val DEFAULT_CLOCK = 0xFFA8C7FA.toInt()
        private val SAMPLE = intArrayOf(
            0xFF25D366.toInt(), 0xFFEA4335.toInt(), 0xFF2AABEE.toInt(),
            0xFFE1306C.toInt(), 0xFFFBBC04.toInt(), 0xFF8AB4F8.toInt()
        )
    }
}
