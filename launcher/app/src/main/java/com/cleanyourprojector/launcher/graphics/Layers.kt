package io.egate.launcher.graphics

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable

/**
 * Gradient layers as tiny bitmaps.
 *
 * The box's Mali-450 evaluates a gradient shader per pixel, and the launcher stacks four of them
 * over the whole screen; each one measured ~5-6 ms of GPU time per frame, while a full-screen
 * bitmap measured none. A gradient that varies along one axis is drawn into a strip one pixel
 * wide and stretched across the view: bilinear sampling of a linear ramp reproduces the ramp
 * exactly, so the pixels match the shader. A diagonal gradient is drawn at a quarter of the
 * screen size for the same reason.
 */
object Layers {

    /** Vertical gradient: one column, as tall as the screen, stretched sideways. */
    fun column(res: Resources, d: Drawable, h: Int): BitmapDrawable =
        BitmapDrawable(res, render(d, 1, h))

    /** Horizontal gradient: one row, as wide as the screen, stretched down. */
    fun row(res: Resources, d: Drawable, w: Int): BitmapDrawable =
        BitmapDrawable(res, render(d, w, 1))

    /** Diagonal gradient at [w]x[h]; keep the aspect ratio of the view it will fill. */
    fun sheet(res: Resources, d: Drawable, w: Int, h: Int): BitmapDrawable =
        BitmapDrawable(res, render(d, w, h))

    /**
     * The room tint as a white alpha mask (0x7A at the top-left, 0x24 in the middle, clear at the
     * bottom-right); tinting it SRC_IN with a colour gives the same pixels as the coloured gradient.
     */
    fun tintMask(res: Resources, w: Int, h: Int): BitmapDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(0x7AFFFFFF, 0x24FFFFFF, 0x00FFFFFF))
        return sheet(res, g, w, h)
    }

    private var cardScrimBitmap: Bitmap? = null

    /** The dark strip under a card's name, drawn once and shared by every card. */
    fun cardScrim(res: Resources, heightPx: Int): BitmapDrawable {
        val bmp = cardScrimBitmap?.takeIf { it.height == heightPx }
            ?: render(GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xD9070A14.toInt(), 0x00070A14)), 1, heightPx).also { cardScrimBitmap = it }
        return BitmapDrawable(res, bmp)
    }

    fun render(d: Drawable, w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, b.width, b.height)
        d.draw(Canvas(b))
        return b
    }
}
