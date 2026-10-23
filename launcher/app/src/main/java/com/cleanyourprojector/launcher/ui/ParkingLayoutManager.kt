package io.egate.launcher.ui

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Leanback-style scrolling: the item that gains focus is parked at the start padding
 * (left for horizontal, top for vertical) instead of merely being brought into view.
 * RecyclerView hands focus scrolling to the LayoutManager, so this is where it must live.
 */
class ParkingLayoutManager(context: Context, orientation: Int) :
    LinearLayoutManager(context, orientation, false) {

    private fun park(parent: RecyclerView, child: View, immediate: Boolean): Boolean {
        val d = if (orientation == HORIZONTAL) child.left - parent.paddingLeft else child.top - parent.paddingTop
        if (d == 0) return false
        val dx = if (orientation == HORIZONTAL) d else 0
        val dy = if (orientation == HORIZONTAL) 0 else d
        if (immediate) parent.scrollBy(dx, dy)
        else parent.smoothScrollBy(dx, dy, DecelerateInterpolator(2f), if (orientation == HORIZONTAL) 260 else 300)
        return true
    }

    /** Keep the shelves above and below laid out so D-pad focus search can land on them. */
    override fun calculateExtraLayoutSpace(state: RecyclerView.State, extraLayoutSpace: IntArray) {
        if (orientation == VERTICAL) { extraLayoutSpace[0] = EXTRA_SHELF_PX; extraLayoutSpace[1] = EXTRA_SHELF_PX }
        else super.calculateExtraLayoutSpace(state, extraLayoutSpace)
    }

    override fun onRequestChildFocus(parent: RecyclerView, state: RecyclerView.State, child: View, focused: View?): Boolean {
        park(parent, child, immediate = false)
        return true   // handled; stop RecyclerView from doing its own bring-into-view scroll
    }

    override fun requestChildRectangleOnScreen(
        parent: RecyclerView, child: View, rect: Rect, immediate: Boolean, focusedChildVisible: Boolean
    ): Boolean = park(parent, child, immediate)

    companion object {
        /** One shelf (~210px) past each edge: enough for D-pad focus search to find the next shelf,
         *  without keeping every shelf on the Home tab laid out, bound and loading art at once. */
        private const val EXTRA_SHELF_PX = 320
    }
}
