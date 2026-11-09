package dev.siva.pinlight

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.service.notification.StatusBarNotification
import androidx.palette.graphics.Palette

// Turns a notification into a white alpha mask plus a visible tint colour.
object IconPacker {

    private val colorCache = HashMap<String, Int>()

    fun mask(context: Context, sbn: StatusBarNotification): Bitmap? {
        val icon = sbn.notification.smallIcon ?: return null
        val drawable = try {
            icon.loadDrawable(context)
        } catch (e: Exception) {
            null
        } ?: return null

        val bitmap = Bitmap.createBitmap(Protocol.ICON_PX, Protocol.ICON_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, Protocol.ICON_PX, Protocol.ICON_PX)
        drawable.setTint(Color.WHITE)
        drawable.setTintMode(PorterDuff.Mode.SRC_IN)
        drawable.draw(canvas)
        return bitmap
    }

    fun color(context: Context, sbn: StatusBarNotification): Int {
        val raw = sbn.notification.color
        val base = if (raw != Notification.COLOR_DEFAULT && Color.alpha(raw) > 0) {
            raw
        } else {
            colorCache.getOrPut(sbn.packageName) { appColor(context, sbn.packageName) }
        }
        return keepVisible(base)
    }

    private fun appColor(context: Context, pkg: String): Int {
        return try {
            val icon = context.packageManager.getApplicationIcon(pkg)
            val palette = Palette.from(render(icon, 96)).generate()
            val swatch = palette.vibrantSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.dominantSwatch
            swatch?.rgb ?: Color.WHITE
        } catch (e: Exception) {
            Color.WHITE
        }
    }

    // Dark brand colours vanish on a black AMOLED panel, so lift brightness.
    private fun keepVisible(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        if (hsv[2] < 0.8f) hsv[2] = 0.9f
        return Color.HSVToColor(255, hsv)
    }

    private fun render(drawable: Drawable, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }
}
