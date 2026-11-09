package dev.siva.pinlight.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

// A round colour chip. color = 0 draws the "automatic" chip:
// a split ring for the clock, a four-colour pie for icons.
class SwatchView(context: Context, val color: Int, private val multi: Boolean) : View(context) {

    var picked = false
        set(value) {
            field = value
            invalidate()
        }

    private val dp = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
    }
    private val arc = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (40 * dp).toInt()
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = 13 * dp

        if (color != 0) {
            fill.color = color
            canvas.drawCircle(cx, cy, r, fill)
        } else if (multi) {
            arc.set(cx - r, cy - r, cx + r, cy + r)
            val colors = intArrayOf(0xFF25D366.toInt(), 0xFF2AABEE.toInt(), 0xFFE1306C.toInt(), 0xFFFBBC04.toInt())
            for (i in colors.indices) {
                fill.color = colors[i]
                canvas.drawArc(arc, i * 90f - 90f, 90f, true, fill)
            }
        } else {
            ring.color = 0xFFA8C7FA.toInt()
            canvas.drawCircle(cx, cy, r - 3 * dp, ring)
            ring.color = Color.WHITE
        }

        if (picked) {
            ring.color = Color.WHITE
            canvas.drawCircle(cx, cy, r + 4 * dp, ring)
        }
    }
}
