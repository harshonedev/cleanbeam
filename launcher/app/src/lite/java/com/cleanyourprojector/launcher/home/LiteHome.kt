package com.cleanyourprojector.launcher.home

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewStub
import android.widget.TextView
import com.cleanyourprojector.launcher.R
import com.cleanyourprojector.launcher.ui.FocusRowRecyclerView
import com.cleanyourprojector.launcher.ui.LiteHomeAdapter
import java.util.Date

/** Lite's Home tab: a large clock where the hero was, then the favorites and a few settings as shelves. */
class LiteHome(host: HomeHost) : HomeTab(host) {

    override val startsOnHome: Boolean = true
    override val loadingText: Int = R.string.loading

    private lateinit var adapter: LiteHomeAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var firstShow = true               // cold start lands on the first card even if a tab holds focus
    private lateinit var clock: View
    private lateinit var time: TextView
    private lateinit var ampm: TextView
    private lateinit var date: TextView
    private var shown: LiteClock.Text? = null   // what the clock shows; same text means no relayout

    override fun onCreate() {
        adapter = LiteHomeAdapter(
            onAppClick = { host.launchApp(it) },
            onAppLongClick = { host.appMenu(it) },
            onTileClick = { it.action() })
        adapter.upTarget = host.homeTabView
        host.shelves.adapter = adapter
        host.shelves.itemAnimator = null
        clock = host.activity.findViewById<ViewStub>(R.id.home_clock_stub).inflate()
        time = clock.findViewById(R.id.home_clock_time)
        ampm = clock.findViewById(R.id.home_clock_ampm)
        date = clock.findViewById(R.id.home_clock_date)
        showClock(true)   // Lite starts on Home
    }

    /** The clock stands in for the hero and the small status clock while Home shows. */
    private fun showClock(on: Boolean) {
        clock.visibility = if (on) View.VISIBLE else View.GONE
        host.setHeroVisible(!on)
        host.setStatusClockVisible(!on)
        if (on) onTick(Date())
    }

    override fun onTick(now: Date) {
        if (clock.visibility != View.VISIBLE) return
        val t = LiteClock.format(now, host.prefs.clock24h, host.prefs.showSeconds)
        val was = shown
        if (t == was) return
        shown = t
        if (t.time != was?.time) time.text = t.time
        if (t.ampm != was?.ampm) { ampm.text = t.ampm; ampm.visibility = if (t.ampm.isEmpty()) View.GONE else View.VISIBLE }
        if (t.date != was?.date) date.text = t.date
    }

    override fun hide() { showClock(false) }

    private fun rows(): List<LiteRow> = LiteRows.build(
        host.favoriteApps(), LiteRows.quick(host.settingsTiles()),
        host.activity.getString(R.string.tab_favorites), host.activity.getString(R.string.home_quick))

    override fun show() {
        val shelves = host.shelves
        showClock(true)
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
