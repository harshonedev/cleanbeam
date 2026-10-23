package com.cleanyourprojector.launcher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewParent
import androidx.recyclerview.widget.RecyclerView
import com.cleanyourprojector.launcher.graphics.Cards

/**
 * Horizontal shelf that keeps the focused card parked at the start padding (leanback style),
 * keeps left/right inside the shelf, and sends D-pad up to [upTarget] when set.
 */
class FocusRowRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    var upTarget: View? = null

    init { layoutManager = ParkingLayoutManager(context, HORIZONTAL) }

    /**
     * The end padding only has to let the last card park at the start padding, so it is trimmed
     * to exactly that once the width is known. The layout manager fills the padding with real
     * cards (clipToPadding is off), so a generous XML value meant binding and loading art for
     * cards that sit far past the right edge of the screen.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0) return
        val slot = Cards.dp(this, SLOT_DP).toInt()
        val end = (w - paddingStart - slot).coerceAtLeast(0)
        if (end != paddingEnd) setPaddingRelative(paddingStart, paddingTop, end, paddingBottom)
    }

    override fun focusSearch(focused: View?, direction: Int): View? {
        if (direction == View.FOCUS_UP) upTarget?.let { return it }
        val next = super.focusSearch(focused, direction)
        if ((direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) && next != null && !contains(next)) {
            return focused   // end of the shelf: stay put instead of jumping rows or tabs
        }
        return next
    }

    private fun contains(v: View): Boolean {
        var p: ViewParent? = v.parent
        while (p != null) { if (p === this) return true; p = p.parent }
        return false
    }

    companion object {
        /** Width of one card slot: the 212dp card plus 9dp padding on each side (item_title / item_app). */
        private const val SLOT_DP = 230f
    }
}
