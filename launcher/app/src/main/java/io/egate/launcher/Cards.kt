package io.egate.launcher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator

/** Focus look shared by app cards and title cards: overshoot scale, white ring, colored shadow. */
object Cards {
    private val overshoot = OvershootInterpolator(1.6f)
    private val settle = PathInterpolator(0.2f, 0f, 0f, 1f)

    fun dp(v: View, value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, v.resources.displayMetrics)

    fun prepare(card: View) {
        card.clipToOutline = true
        card.foreground = focusRing(card)
    }

    fun bindFocus(card: View, shadow: Int, onFocus: () -> Unit) {
        card.outlineSpotShadowColor = shadow
        card.outlineAmbientShadowColor = shadow
        card.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.12f else 1.0f
            v.animate().scaleX(scale).scaleY(scale)
                .setDuration(if (hasFocus) 320 else 200)
                .setInterpolator(if (hasFocus) overshoot else settle)
                .start()
            v.elevation = if (hasFocus) dp(v, 14f) else dp(v, 2f)
            if (hasFocus) onFocus()
        }
    }

    fun reset(card: View) {
        card.animate().cancel()
        card.scaleX = 1f; card.scaleY = 1f
    }

    private fun focusRing(v: View): StateListDrawable {
        val radius = dp(v, 14f)
        val focused = GradientDrawable().apply {
            cornerRadius = radius; setColor(Color.TRANSPARENT); setStroke(dp(v, 3f).toInt(), Color.WHITE)
        }
        val normal = GradientDrawable().apply {
            cornerRadius = radius; setColor(Color.TRANSPARENT); setStroke(dp(v, 1f).toInt(), 0x22FFFFFF)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }
}
