package io.egate.launcher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import java.util.Calendar

/** Time-of-day background gradient. Stays dark enough for white text. */
object Sky {
    private val DEFAULT = intArrayOf(0xFF0B1020.toInt(), 0xFF161B2E.toInt())

    private fun palette(hour: Int): IntArray = when (hour) {
        in 5..7   -> intArrayOf(0xFF1C1430.toInt(), 0xFF2A1E2E.toInt())   // dawn, violet
        in 8..11  -> intArrayOf(0xFF0C1526.toInt(), 0xFF122033.toInt())   // morning, blue
        in 12..16 -> intArrayOf(0xFF0B1020.toInt(), 0xFF141D30.toInt())   // day
        in 17..19 -> intArrayOf(0xFF201424.toInt(), 0xFF2A1A22.toInt())   // dusk, warm
        else      -> intArrayOf(0xFF06080F.toInt(), 0xFF0B1020.toInt())   // night, deep
    }

    fun drawable(enabled: Boolean): GradientDrawable {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val colors = if (enabled) palette(hour) else DEFAULT
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors)
    }

    fun greeting(): String {
        return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
    }
}
