package com.cleanyourprojector.launcher.home

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import com.cleanyourprojector.launcher.R
import com.cleanyourprojector.launcher.ui.FocusRowRecyclerView
import com.cleanyourprojector.launcher.ui.LiteHomeAdapter

/** Lite's Home tab: the favorites and a few settings as shelves. */
class LiteHome(host: HomeHost) : HomeTab(host) {

    override val startsOnHome: Boolean = true
    override val loadingText: Int = R.string.loading

    private lateinit var adapter: LiteHomeAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var firstShow = true               // cold start lands on the first card even if a tab holds focus

    override fun onCreate() {
        adapter = LiteHomeAdapter(
            onAppClick = { host.launchApp(it) },
            onAppLongClick = { host.appMenu(it) },
            onTileClick = { it.action() })
        adapter.upTarget = host.homeTabView
        host.shelves.adapter = adapter
        host.shelves.itemAnimator = null
    }

    private fun rows(): List<LiteRow> = LiteRows.build(
        host.favoriteApps(), LiteRows.quick(host.settingsTiles()),
        host.activity.getString(R.string.tab_favorites), host.activity.getString(R.string.home_quick))

    override fun show() {
        val shelves = host.shelves
        host.hideEmpty()
        shelves.visibility = View.VISIBLE
        val before = host.activity.currentFocus
        val wasInside = before != null && shelves.findContainingItemView(before) != null
        adapter.submit(rows())
        val after = host.activity.currentFocus
        val inside = after != null && shelves.findContainingItemView(after) != null
        // A rebind recreated the focused card: back to the first one. Cold start or nothing
        // focused: land on the first card. Coming from a tab: leave focus on the tab.
        if (wasInside && !inside) focusFirstCard()
        else if (!inside && !wasInside && (after == null || firstShow)) focusFirstCard()
        firstShow = false
    }

    override fun scrollToTop() {
        host.shelves.scrollToPosition(0)
        focusFirstCard()
    }

    override fun onFavoritesChanged() { if (host.isHomeShowing) adapter.submit(rows()) }

    private fun firstCard(rv: View?): View? =
        (rv as? FocusRowRecyclerView)?.getChildAt(0)?.findViewById(R.id.card)

    private fun focusFirstCard(attempt: Int = 0) {
        val shelves = host.shelves
        shelves.post {
            val card = firstCard(shelves.getChildAt(0)?.findViewById(R.id.row_titles))
            if (card != null) card.requestFocus()
            else if (attempt < 8 && host.isHomeShowing) handler.postDelayed({ focusFirstCard(attempt + 1) }, 60)
            else Log.w(TAG, "first Home card not found after $attempt attempts")
        }
    }

    private companion object { const val TAG = "Launcher" }
}
