package io.egate.launcher

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView

/**
 * Horizontal row that keeps the focused card parked at the start padding
 * (leanback style) instead of letting it drift to whichever edge it hits.
 */
class FocusRowRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    override fun requestChildRectangleOnScreen(child: View, rect: Rect, immediate: Boolean): Boolean {
        val dx = child.left - paddingLeft
        if (dx == 0) return false
        if (immediate) scrollBy(dx, 0) else smoothScrollBy(dx, 0, DecelerateInterpolator(2f), 260)
        return true
    }
}
