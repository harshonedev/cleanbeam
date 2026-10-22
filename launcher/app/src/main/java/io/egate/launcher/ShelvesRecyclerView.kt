package io.egate.launcher

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView

/** Vertical stack of shelves; the shelf holding focus parks at the top padding. */
class ShelvesRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    override fun requestChildRectangleOnScreen(child: View, rect: Rect, immediate: Boolean): Boolean {
        val dy = child.top - paddingTop
        if (dy == 0) return false
        if (immediate) scrollBy(0, dy) else smoothScrollBy(0, dy, DecelerateInterpolator(2f), 300)
        return true
    }
}
