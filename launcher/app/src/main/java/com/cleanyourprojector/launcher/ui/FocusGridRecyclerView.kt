package com.cleanyourprojector.launcher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewParent
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cleanyourprojector.launcher.R

/**
 * Vertical grid for the Apps and Settings tabs. Left, right and down never leave the grid
 * (the nearest view outside was a tab, so an edge press switched tabs); up past the first row
 * goes to [upTarget] when set, the selected tab.
 *
 * Down from the last column onto a shorter last row lands on that row's last tile instead of
 * staying put: otherwise the last tile could only be reached by going left first.
 */
class FocusGridRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    var upTarget: View? = null

    /**
     * Bottom padding lets the last row park at the top (clipToPadding is off): exactly the grid's
     * height less one row slot. More than the grid's height would put every row "out of bounds" for
     * the layout manager, which then re-anchors at row 0 whenever it lays out with no focused child
     * (a menu holding focus, for instance) and the grid jumps.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h <= 0) return
        val slot = (SLOT_DP * resources.displayMetrics.density).toInt()
        val bottom = (h - slot).coerceAtLeast(0)
        if (bottom != paddingBottom) setPadding(paddingLeft, paddingTop, paddingRight, bottom)
    }

    override fun focusSearch(focused: View?, direction: Int): View? {
        val next = super.focusSearch(focused, direction)
        if (next == null || contains(next)) {
            if (next == null && direction == View.FOCUS_DOWN) return lastRowFallback(focused) ?: focused
            // Left or right never changes row: at the end of a short last row the nearest view to the
            // right is one row up, and the grid would climb instead of stopping.
            if (next != null && (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) && row(next) != row(focused)) return focused
            return next
        }
        return when (direction) {
            View.FOCUS_UP -> upTarget ?: next
            View.FOCUS_DOWN -> lastRowFallback(focused) ?: focused
            View.FOCUS_LEFT, View.FOCUS_RIGHT -> focused   // edge of the grid: stay put
            else -> next
        }
    }

    /** Row of the tile holding [v], or -1 when it is not in the grid. */
    private fun row(v: View?): Int {
        val lm = layoutManager as? GridLayoutManager ?: return -1
        val item = v?.let { findContainingItemView(it) } ?: return -1
        val pos = getChildAdapterPosition(item)
        return if (pos < 0) -1 else pos / lm.spanCount
    }

    /** The last tile, when it sits on a later row than [focused] and nothing is straight below. */
    private fun lastRowFallback(focused: View?): View? {
        val f = focused ?: return null
        val lm = layoutManager as? GridLayoutManager ?: return null
        val item = findContainingItemView(f) ?: return null
        val pos = getChildAdapterPosition(item)
        val count = adapter?.itemCount ?: return null
        if (pos < 0 || count == 0) return null
        val last = count - 1
        if (last / lm.spanCount <= pos / lm.spanCount) return null
        val target = findViewHolderForAdapterPosition(last)?.itemView ?: return null
        return target.findViewById(R.id.card) ?: target
    }

    private fun contains(v: View): Boolean {
        var p: ViewParent? = v.parent
        while (p != null) { if (p === this) return true; p = p.parent }
        return false
    }

    companion object {
        /** Height of one row slot: the 119dp card plus 18dp padding above and below (item_app / item_tile). */
        private const val SLOT_DP = 155f
    }
}
