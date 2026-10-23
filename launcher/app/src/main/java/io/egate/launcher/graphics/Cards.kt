package io.egate.launcher.graphics

import android.util.TypedValue
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator

/** Focus look shared by app, title and settings cards: overshoot scale and a white ring. */
object Cards {
    private val overshoot = OvershootInterpolator(1.6f)
    private val settle = PathInterpolator(0.2f, 0f, 0f, 1f)

    fun dp(v: View, value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, v.resources.displayMetrics)

    /** The focus ring itself comes from the layout (@drawable/focus_ring), shared through the resource cache. */
    fun prepare(card: View) {
        card.clipToOutline = true
    }

    /** Focus look: overshoot scale and the white ring. Cards stay flat (no elevation shadow). */
    fun bindFocus(card: View, onFocus: () -> Unit) {
        card.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.12f else 1.0f
            v.animate().scaleX(scale).scaleY(scale)
                .setDuration(if (hasFocus) 320 else 200)
                .setInterpolator(if (hasFocus) overshoot else settle)
                .start()
            if (hasFocus) onFocus()
        }
    }

    fun reset(card: View) {
        card.animate().cancel()
        card.scaleX = 1f; card.scaleY = 1f
    }
}
