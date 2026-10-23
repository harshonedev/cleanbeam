package io.egate.launcher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.ViewOutlineProvider
import androidx.recyclerview.widget.RecyclerView

/** Vertical stack of shelves; the shelf holding focus parks at the top padding. */
class ShelvesRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {
    init {
        layoutManager = ParkingLayoutManager(context, VERTICAL)
        // The parent keeps clipChildren off for the app row's scaled cards, which in hardware
        // rendering also un-clips this view; clip to our own outline so shelves scrolled above
        // the top edge don't paint over the hero.
        outlineProvider = ViewOutlineProvider.BOUNDS
        clipToOutline = true
    }
}
