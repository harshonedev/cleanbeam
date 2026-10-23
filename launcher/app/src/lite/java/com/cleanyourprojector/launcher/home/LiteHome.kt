package com.cleanyourprojector.launcher.home

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import com.cleanyourprojector.launcher.R
import com.cleanyourprojector.launcher.ui.FocusRowRecyclerView
import com.cleanyourprojector.launcher.ui.LiteHomeAdapter

/** Lite's Home tab: the favorites and a few settings as shelves, with the focused card in the hero as on every tab. */
class LiteHome(host: HomeHost) : HomeTab(host) {

    override val startsOnHome: Boolean = true
    override val loadingText: Int = R.string.loading
    override val favoritesTab: Boolean = false

    private lateinit var adapter: LiteHomeAdapter
    private val handler by lazy { Handler(Looper.getMainLooper()) }   // lazy: construction stays free of Android
    private var firstShow = true               // cold start lands on the first card even if a tab holds focus

    override fun onCreate() {
        adapter = LiteHomeAdapter(
            onAppClick = { host.launchApp(it) },
            onAppLongClick = { host.appMenu(it) },
            onAppFocus = { host.onCardFocused(it) },
            onTileClick = { it.action() },
            onTileFocus = { host.onCardFocused(it) })
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
        val rows = rows()
        val before = host.activity.currentFocus
        val wasInside = before != null && shelves.findContainingItemView(before) != null
        adapter.submit(rows)
        val after = host.activity.currentFocus
        val inside = after != null && shelves.findContainingItemView(after) != null
        // A rebind recreated the focused card: back to the first one.
        if (wasInside && !inside) focusFirstCard()
        if (!inside && !wasInside) {
            // The hero shows the first item; cold start or nothing focused lands on its card,
            // coming from a tab leaves focus on the tab.
            val first = rows.firstOrNull() ?: return
            host.onCardFocused(if (first.apps.isNotEmpty()) first.apps[0] else first.tiles.firstOrNull() ?: return)
            if (after == null || firstShow) focusFirstCard()
        }
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
            // A park started for the shelf losing focus (the system re-focuses it around the HOME
            // intent) would keep scrolling after the jump to the top; it must not outlive this.
            shelves.stopScroll()
            if (card != null) card.requestFocus()
            else if (attempt < 8 && host.isHomeShowing) handler.postDelayed({ focusFirstCard(attempt + 1) }, 60)
            else Log.w(TAG, "first Home card not found after $attempt attempts")
        }
    }

    private companion object { const val TAG = "Launcher" }
}
