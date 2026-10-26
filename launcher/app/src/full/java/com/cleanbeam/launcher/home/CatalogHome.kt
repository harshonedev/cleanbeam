package com.cleanbeam.launcher.home

import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import com.cleanbeam.launcher.Edition
import com.cleanbeam.launcher.R
import com.cleanbeam.launcher.apps.AppInfo
import com.cleanbeam.launcher.apps.AppRepository
import com.cleanbeam.launcher.catalog.HomeRow
import com.cleanbeam.launcher.catalog.JustWatch
import com.cleanbeam.launcher.catalog.Playback
import com.cleanbeam.launcher.catalog.Service
import com.cleanbeam.launcher.catalog.Services
import com.cleanbeam.launcher.catalog.Title
import com.cleanbeam.launcher.catalog.Tmdb
import com.cleanbeam.launcher.graphics.ImageLoader
import com.cleanbeam.launcher.ui.FocusRowRecyclerView
import com.cleanbeam.launcher.ui.HomeRowsAdapter
import com.cleanbeam.launcher.ui.SidePanel.Content
import com.cleanbeam.launcher.ui.SidePanel.Row
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Full's Home tab: a Favorites shelf, then one shelf per installed streaming service and a few
 * TMDB lists, with a title opening in the app that streams it.
 */
class CatalogHome(host: HomeHost) : HomeTab(host) {

    private lateinit var shelvesAdapter: HomeRowsAdapter
    private var homeRows: List<HomeRow> = emptyList()
    private var homeLoadedAt = 0L
    private var homeLoading = false
    private var homeGen = 0                    // bumps per Home load so a superseded fetch can't land
    private var homeFirstShow = true           // cold start lands on the first card even if a tab holds focus

    private val io = Executors.newSingleThreadExecutor()
    private val rowsIo = Executors.newFixedThreadPool(3)   // shelves fetch side by side
    private val handler = Handler(Looper.getMainLooper())

    private val region: String get() = host.prefs.region.ifBlank { Tmdb.defaultRegion() }

    override val startsOnHome: Boolean get() = Tmdb.configured(host.activity)
    override val loadingText: Int = R.string.home_loading

    override fun onCreate() {
        shelvesAdapter = HomeRowsAdapter(
            onClick = { t, r -> openTitle(t, r) },
            onLongClick = { t, r -> titleMenu(t, r) },
            onFocus = { t, r -> host.onCardFocused(t to r) },
            onAppClick = { host.launchApp(it) },
            onAppLongClick = { host.appMenu(it) },
            onAppFocus = { host.onCardFocused(it) })
        shelvesAdapter.upTarget = host.homeTabView
        host.shelves.adapter = shelvesAdapter
        host.shelves.itemAnimator = null
    }

    override fun onDestroy() { io.shutdownNow(); rowsIo.shutdownNow() }

    /** Favorites shelf first, then the TMDB shelves (loaded, or skeletons while they load). */
    private fun displayRows(): List<HomeRow> {
        val favs = host.favoriteApps()
        val favRow = if (favs.isEmpty()) emptyList() else listOf(HomeRow(host.activity.getString(R.string.tab_favorites), emptyList(), null, null, favs))
        return favRow + homeRows.filter { it.visible }
    }

    override fun show() {
        val configured = Tmdb.configured(host.activity)
        val stale = System.currentTimeMillis() - homeLoadedAt > Tmdb.ROWS_TTL
        // Kick the fetch first: on a cold start it seeds skeleton shelves so the tab paints at once.
        if (configured && (stale || homeRows.isEmpty()) && !homeLoading && host.apps().isNotEmpty()) loadHome()
        if (!configured && homeRows.isEmpty() && host.favoriteApps().isEmpty()) {
            host.shelves.visibility = View.GONE
            shelvesAdapter.submit(emptyList())
            host.showEmpty(R.string.home_no_token, R.string.home_no_token_hint)
            return
        }
        renderHome()
    }

    override fun scrollToTop() {
        host.shelves.scrollToPosition(0)
        focusFirstShelfCard()
    }

    override fun onFavoritesChanged() { shelvesAdapter.submit(displayRows()) }

    /** Draw whatever Home has right now: favorites, finished shelves, skeletons; or the empty state. */
    private fun renderHome() {
        val rows = displayRows()
        if (rows.isEmpty()) {
            host.shelves.visibility = View.GONE
            shelvesAdapter.submit(emptyList())
            host.showEmpty(R.string.home_empty, R.string.home_empty_hint)
        } else showShelves(rows)
    }

    /**
     * Shelves load one by one, side by side. On a cold start each starts as a skeleton row and is
     * swapped in as it arrives; a refresh behind rows already on screen swaps them all at the end.
     */
    private fun loadHome() {
        homeLoading = true
        val gen = ++homeGen
        val apps = host.apps()
        val reg = region
        val pending = Tmdb.plan(host.activity, apps, reg)
        val cold = homeRows.isEmpty()
        if (cold) homeRows = pending.map { it.row }
        val done = arrayOfNulls<HomeRow>(pending.size)
        val remaining = AtomicInteger(pending.size)
        io.execute {
            val providers = runCatching { Tmdb.providers(host.activity, reg) }
                .onFailure { Log.e(TAG, "tmdb providers failed", it) }.getOrDefault(emptyList())
            pending.forEachIndexed { i, p ->
                rowsIo.execute {
                    val r = runCatching { p.load(providers) }
                        .onFailure { Log.e(TAG, "tmdb row '${p.row.header}' failed", it) }
                        .getOrDefault(p.row.copy(loading = false))
                    done[i] = r
                    val last = remaining.decrementAndGet() == 0
                    handler.post {
                        if (gen != homeGen) return@post
                        if (cold) homeRows = homeRows.map { if (it.header == r.header) r else it }
                        if (last) {
                            homeLoading = false
                            val rows = done.filterNotNull().filter { it.titles.isNotEmpty() }
                            Log.i(TAG, "home rows: ${rows.map { "${it.header} (${it.titles.size})" }}")
                            if (rows.isNotEmpty()) { homeRows = rows; homeLoadedAt = System.currentTimeMillis() }
                            else if (cold) homeRows = emptyList()
                        }
                        if ((cold || last) && host.isHomeShowing) renderHome()
                    }
                }
            }
        }
    }

    private fun showShelves(rows: List<HomeRow>) {
        val shelves = host.shelves
        shelves.visibility = View.VISIBLE
        host.hideEmpty()
        val before = host.activity.currentFocus
        val focusWasInShelves = before != null && shelves.findContainingItemView(before) != null
        shelvesAdapter.submit(rows)
        val focused = host.activity.currentFocus
        val focusInShelves = focused != null && shelves.findContainingItemView(focused) != null
        // A rebind recreated the focused card: put focus back on the first card.
        if (focusWasInShelves && !focusInShelves) focusFirstShelfCard()
        if (!focusInShelves && !focusWasInShelves) {
            // Skeletons only: the hero and the landing focus wait for the first real shelf.
            val first = rows.firstOrNull { !it.loading } ?: return
            host.onCardFocused(if (first.isAppRow) first.apps[0] else first.titles[0] to first)
            // Cold start (or nothing focused): land on the first card. From a tab: leave focus on the tab.
            if (focused == null || homeFirstShow) focusFirstShelfCard()
        }
        homeFirstShow = false
        shelvesAdapter.warmUp(shelves)
    }

    private fun firstCard(rv: View?): View? =
        (rv as? FocusRowRecyclerView)?.getChildAt(0)?.findViewById(R.id.card)

    /** First focusable card on screen; skeleton shelves are skipped. */
    private fun focusFirstShelfCard(attempt: Int = 0) {
        val shelves = host.shelves
        shelves.post {
            val card = (0 until shelves.childCount).asSequence()
                .map { firstCard(shelves.getChildAt(it)?.findViewById(R.id.row_titles)) }
                .firstOrNull { it?.isFocusable == true }
            // A park started for the shelf losing focus (the system re-focuses it around the HOME
            // intent) would keep scrolling after the jump to the top; it must not outlive this.
            shelves.stopScroll()
            if (card != null) card.requestFocus()
            else if (attempt < 8 && host.isHomeShowing) handler.postDelayed({ focusFirstShelfCard(attempt + 1) }, 60)
            else Log.w(TAG, "first shelf card not found after $attempt attempts")
        }
    }

    // ---------- hero ----------
    override fun renderHero(item: Any): Boolean {
        val t = (item as? Pair<*, *>)?.first as? Title ?: return false
        val subtitle = listOfNotNull(
            if (t.kind == "tv") "Series" else "Film",
            t.year,
            if (t.rating > 0) String.format(Locale.US, "rated %.1f", t.rating) else null
        ).joinToString(", ")
        host.setHero(t.name, subtitle, t.overview)
        if (!Edition.AMBIENT || !host.prefs.theme.dynamic) return true
        val url = Tmdb.imageUrl(t.backdrop, "w780") ?: return true
        ImageLoader.fetch(host.activity, url, 800, { bmp -> bmp to AppRepository.colorOf(bmp) }) { res ->
            if (res == null || (host.heroItem as? Pair<*, *>)?.first != t || !host.prefs.theme.dynamic) return@fetch
            host.paintRoom(res.first, res.second)
        }
        return true
    }

    // ---------- watching ----------
    /** One way to watch a title: an installed app to open, or a service to install. */
    private class WatchOption(val service: Service, val app: AppInfo?)

    /**
     * OK on a title: find the services streaming it here, then play it in the one installed app,
     * offer to install the one missing app, or ask when there is more than one choice.
     */
    private fun openTitle(t: Title, r: HomeRow) = watchOptions(t, r) { options ->
        when (options.size) {
            0 -> host.toast("${t.name} isn't streaming in $region right now")
            1 -> watch(t, options[0])
            else -> chooseWatchOption(t, options)
        }
    }

    /** Long press on a title: always show the full list of services carrying it. */
    private fun titleMenu(t: Title, r: HomeRow) = watchOptions(t, r) { options ->
        if (options.isEmpty()) host.toast("${t.name} isn't streaming in $region right now")
        else chooseWatchOption(t, options)
    }

    private fun watchOptions(t: Title, r: HomeRow, cb: (List<WatchOption>) -> Unit) {
        val apps = host.apps(); val reg = region
        io.execute {
            val services = Services.forTmdbNames(Tmdb.providersFor(host.activity, t, reg)).toMutableList()
            val installed = Services.installed(apps)
            // The shelf's own service goes first: that is where the user saw the title.
            if (r.providerPkg != null) {
                val shelf = services.firstOrNull { installed[it]?.packageName == r.providerPkg }
                if (shelf != null) { services.remove(shelf); services.add(0, shelf) }
            }
            val options = services.sortedByDescending { installed.containsKey(it) }.map { WatchOption(it, installed[it]) }
            handler.post { cb(options) }
        }
    }

    private fun chooseWatchOption(t: Title, options: List<WatchOption>) {
        host.panel.open {
            Content(t.name, options.map { o ->
                val label = if (o.app != null) "Open in ${o.app.label}" else "Install ${o.service.name}"
                Row(label) { host.panel.close(); watch(t, o) }
            })
        }
    }

    private fun watch(t: Title, o: WatchOption) {
        val app = o.app
        if (app == null) {
            val intent = Playback.storeIntent(host.activity, o.service.storePkg)
            if (intent == null) host.toast("Can't open ${o.service.name}") else host.safeStart(intent)
            return
        }
        val reg = region
        io.execute {
            // Per-title link (network, cached); null just means the search link is used instead.
            val link = runCatching { JustWatch.urlFor(host.activity, t, reg, o.service) }
                .onFailure { Log.w(TAG, "justwatch ${t.name} failed", it) }.getOrNull()
            val intent = Playback.playIntent(host.activity, app.packageName, o.service, t.name, link)
            Log.i(TAG, "watch '${t.name}' in ${app.packageName}: link=$link -> ${intent?.toUri(0)}")
            handler.post { if (intent == null) host.toast("Can't open ${o.service.name}") else host.safeStart(intent) }
        }
    }

    // ---------- launcher settings rows ----------
    override fun settingsRows(): List<Row> = listOf(
        Row("Refresh Home") {
            clearCatalog()
            host.panel.close()
            host.showHome()
        },
        Row("Region", region) { pickRegion() },
        Row("TMDB access token", if (Tmdb.configured(host.activity)) "Set" else "Not set") { enterToken() }
    )

    private fun clearCatalog() {
        Tmdb.clearCache(host.activity); JustWatch.clearCache(host.activity)
        homeRows = emptyList(); homeLoadedAt = 0
    }

    private fun pickRegion() {
        val codes = arrayOf("IN", "US", "GB", "CA", "AU", "DE", "FR", "ES", "IT", "BR", "MX", "JP", "KR", "SG", "AE")
        val panel = host.panel
        panel.open {
            Content("Region for streaming availability", codes.map { c ->
                Row(c, checked = c == region) {
                    if (c != region) {
                        host.prefs.region = c
                        clearCatalog()
                        if (host.isHomeShowing) show()
                    }
                    panel.back()
                }
            })
        }
    }

    private fun enterToken() {
        val input = EditText(host.activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            hint = "Paste the token here"
            setText(host.prefs.tmdbToken)
            setSingleLine()
        }
        val panel = host.panel
        panel.open {
            Content("TMDB access token", listOf(
                Row("Save") {
                    host.prefs.tmdbToken = input.text.toString()
                    Tmdb.invalidateToken()
                    clearCatalog()
                    panel.close()
                    host.showHome()
                },
                Row("Cancel") { panel.back() }
            ), message = "Free at themoviedb.org under Settings, API. It lets Home show what to watch.", input = input)
        }
    }

    private companion object { const val TAG = "Launcher" }
}
