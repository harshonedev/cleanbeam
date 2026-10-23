package com.cleanyourprojector.launcher.graphics

import android.graphics.drawable.GradientDrawable
import java.util.Calendar

/** Time-of-day backdrop for the Ambient theme. Stays dark enough for white text. */
object Sky {
    private fun palette(hour: Int): IntArray = when (hour) {
        in 5..7   -> intArrayOf(0xFF1C1430.toInt(), 0xFF2A1E2E.toInt())   // dawn, violet
        in 8..11  -> intArrayOf(0xFF0C1526.toInt(), 0xFF122033.toInt())   // morning, blue
        in 12..16 -> intArrayOf(0xFF0B1020.toInt(), 0xFF141D30.toInt())   // day
        in 17..19 -> intArrayOf(0xFF201424.toInt(), 0xFF2A1A22.toInt())   // dusk, warm
        else      -> intArrayOf(0xFF06080F.toInt(), 0xFF0B1020.toInt())   // night, deep
    }

    fun hour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    /** The gradient for [hour]; bake it with [Layers.column] rather than drawing it as a shader. */
    fun drawable(hour: Int = hour()): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, palette(hour))
}
