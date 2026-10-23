package com.cleanyourprojector.launcher.ui

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Vertical grid with the same leanback parking as [ParkingLayoutManager]: the focused row scrolls to the top padding. */
class ParkingGridLayoutManager(context: Context, spans: Int) : GridLayoutManager(context, spans) {

    private var clientScroll = false

    private fun park(parent: RecyclerView, child: View, immediate: Boolean): Boolean {
        val d = child.top - parent.paddingTop
        if (d == 0) return false
        if (immediate) parent.scrollBy(0, d) else parent.smoothScrollBy(0, d, DecelerateInterpolator(2f), 300)
        return true
    }

    /** Keep the rows above and below laid out so D-pad focus search can land on them. */
    override fun calculateExtraLayoutSpace(state: RecyclerView.State, extraLayoutSpace: IntArray) {
        extraLayoutSpace[0] = 700; extraLayoutSpace[1] = 700
    }

    override fun scrollToPosition(position: Int) {
        clientScroll = true
        super.scrollToPosition(position)
    }

    override fun scrollToPositionWithOffset(position: Int, offset: Int) {
        clientScroll = true
        super.scrollToPositionWithOffset(position, offset)
    }

    /**
     * A relayout with no focused child (a menu holds focus) picks its anchor from the first
     * child it deems visible, and with rows laid out above the top for D-pad search that is a
     * row above the parked one: the grid jumped. Pin the anchor to the row at the top instead,
     * unless the activity asked for a scroll. The requestLayout this raises mid-layout is dropped
     * by RecyclerView, so it cannot cascade.
     */
    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (!clientScroll && !state.isPreLayout && focusedChild == null && childCount > 0) {
            var anchor: View? = null
            for (i in 0 until childCount) {
                val c = getChildAt(i) ?: continue
                if (getDecoratedBottom(c) > paddingTop) { anchor = c; break }
            }
            if (anchor != null) super.scrollToPositionWithOffset(getPosition(anchor), getDecoratedTop(anchor) - paddingTop)
        }
        clientScroll = false
        super.onLayoutChildren(recycler, state)
    }

    override fun onRequestChildFocus(parent: RecyclerView, state: RecyclerView.State, child: View, focused: View?): Boolean {
        park(parent, child, immediate = false)
        return true
    }

    override fun requestChildRectangleOnScreen(
        parent: RecyclerView, child: View, rect: Rect, immediate: Boolean, focusedChildVisible: Boolean
    ): Boolean = park(parent, child, immediate)
}
