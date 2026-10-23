package io.egate.launcher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewParent
import androidx.recyclerview.widget.RecyclerView

/**
 * Vertical grid for the Apps and Settings tabs. Left, right and down never leave the grid
 * (the nearest view outside was a tab, so an edge press switched tabs); up past the first row
 * goes to [upTarget] when set, the selected tab.
 */
class FocusGridRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    var upTarget: View? = null

    override fun focusSearch(focused: View?, direction: Int): View? {
        val next = super.focusSearch(focused, direction)
        if (next == null || contains(next)) return next
        return when (direction) {
            View.FOCUS_UP -> upTarget ?: next
            View.FOCUS_LEFT, View.FOCUS_RIGHT, View.FOCUS_DOWN -> focused   // edge of the grid: stay put
            else -> next
        }
    }

    private fun contains(v: View): Boolean {
        var p: ViewParent? = v.parent
        while (p != null) { if (p === this) return true; p = p.parent }
        return false
    }
}
