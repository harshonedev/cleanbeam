package io.egate.launcher

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewParent
import androidx.recyclerview.widget.RecyclerView

/**
 * Horizontal shelf that keeps the focused card parked at the start padding (leanback style),
 * keeps left/right inside the shelf, and sends D-pad up to [upTarget] when set.
 */
class FocusRowRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    var upTarget: View? = null

    init { layoutManager = ParkingLayoutManager(context, HORIZONTAL) }

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

}
