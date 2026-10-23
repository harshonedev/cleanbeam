package com.cleanyourprojector.launcher

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cleanyourprojector.launcher.apps.AppInfo
import com.cleanyourprojector.launcher.apps.AppRepository
import com.cleanyourprojector.launcher.graphics.Backdrop
import com.cleanyourprojector.launcher.home.HomeHost
import com.cleanyourprojector.launcher.home.HomeTab
import com.cleanyourprojector.launcher.graphics.Cards
import com.cleanyourprojector.launcher.graphics.Layers
import com.cleanyourprojector.launcher.graphics.Sky
import com.cleanyourprojector.launcher.graphics.Look
import com.cleanyourprojector.launcher.graphics.Themes
import com.cleanyourprojector.launcher.graphics.Wallpaper
import com.cleanyourprojector.launcher.settings.DeviceInfo
import com.cleanyourprojector.launcher.settings.SystemShortcuts
import com.cleanyourprojector.launcher.settings.Tile
import com.cleanyourprojector.launcher.system.ClockDreamService
import com.cleanyourprojector.launcher.system.KeepAliveService
import com.cleanyourprojector.launcher.system.RemoteLink
import com.cleanyourprojector.launcher.ui.AppAdapter
import com.cleanyourprojector.launcher.ui.FocusGridRecyclerView
import com.cleanyourprojector.launcher.ui.FocusRowRecyclerView
import com.cleanyourprojector.launcher.ui.ParkingGridLayoutManager
import com.cleanyourprojector.launcher.ui.ShelvesRecyclerView
import com.cleanyourprojector.launcher.ui.SidePanel
import com.cleanyourprojector.launcher.ui.SidePanel.Content
import com.cleanyourprojector.launcher.ui.SidePanel.Row
import com.cleanyourprojector.launcher.ui.TileAdapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

class LauncherActivity : AppCompatActivity(), HomeHost {

    private enum class Tab { HOME, APPS, FAVORITES, SETTINGS }

    override lateinit var prefs: Prefs
    private lateinit var root: View
    private lateinit var bg: ImageView
    private lateinit var dim: View
    private lateinit var artA: ImageView               // Ambient theme: two layers crossfade when the focused item changes
    private lateinit var artB: ImageView
    private lateinit var tint: View
    private lateinit var scrimLeft: View
    private lateinit var scrimBottom: View
    override lateinit var panel: SidePanel
    private lateinit var tabHome: TextView
    private lateinit var tabApps: TextView
    private lateinit var tabFavorites: TextView
    private lateinit var tabSettings: TextView
    private lateinit var underline: View
    private lateinit var clock: TextView
    private lateinit var wifi: ImageView
    private lateinit var phone: ImageView
    private lateinit var grid: FocusGridRecyclerView
    private lateinit var gridAdapter: AppAdapter
    private lateinit var tileAdapter: TileAdapter
    private lateinit var remote: RemoteLink
    private lateinit var hero: View
    private lateinit var heroTitle: TextView
    private lateinit var heroSubtitle: TextView
    private lateinit var heroBody: TextView
    private lateinit var row: FocusRowRecyclerView
    override lateinit var shelves: ShelvesRecyclerView
    private lateinit var empty: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyHint: TextView
    private lateinit var adapter: AppAdapter
    private lateinit var home: HomeTab             // the edition's Home tab

    private var allApps: List<AppInfo> = emptyList()
    private var tab = Tab.APPS
    override var heroItem: Any? = null         // AppInfo, Tile, or the Home tab's own item
        private set
    private val backdrop = Backdrop()          // what the background shows: a resource id or a file path
    private var userSlot = -1L                 // half-hour slot the user picture was chosen for
    private var artFront = false               // which art layer is visible: false = A, true = B
    private var tintColor = 0
    private var tintAnim: ValueAnimator? = null
    private var tintDrawable: BitmapDrawable? = null    // white gradient mask, tinted with the room colour; Full only
    private var skyHour = -1
    private var userPick: String? = null
    private var firstLoad = true
    private var heroGen = 0                    // bumps per hero change so a cancelled fade can't leave it hidden
    private var clockPattern = ""
    private var clockFormat: SimpleDateFormat? = null
    private var lastClockText: String? = null
    private var lastWallpaperMinute = -1L

    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val ease = PathInterpolator(0.2f, 0f, 0f, 1f)
    private val heroRunnable = Runnable {
        when (val it = heroItem) {
            null -> {}
            is AppInfo -> renderAppHero(it, animate = true)
            is Tile -> renderTileHero(it)
            else -> home.renderHero(it)
        }
    }

    private val tick = object : Runnable {
        override fun run() { updateClock(); handler.postDelayed(this, 1_000) }
    }
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = updateClock()
    }
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = reloadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_launcher)
        // The theme's colour is only the launch splash: root paints the whole screen itself, so
        // drop the window background instead of filling 1280x720 twice on every frame.
        window.setBackgroundDrawable(null)

        root = findViewById(R.id.root)
        bg = findViewById(R.id.bg)
        dim = findViewById(R.id.dim)
        // A flat fill faded with View.alpha would otherwise be composited through a full-screen layer each frame.
        dim.forceHasOverlappingRendering(false)
        artA = findViewById(R.id.art_a)
        artB = findViewById(R.id.art_b)
        tint = findViewById(R.id.tint)
        scrimLeft = findViewById(R.id.scrim_left_v)
        scrimBottom = findViewById(R.id.scrim_bottom_v)
        // Ambient theme layers. Full-screen gradients cost this GPU ~5 ms each per frame as shaders; as bitmaps they are free.
        if (Edition.AMBIENT) {
            val dm = resources.displayMetrics
            tintDrawable = Layers.tintMask(resources, dm.widthPixels / 4, dm.heightPixels / 4)
            tint.background = tintDrawable
            scrimLeft.background = Layers.row(resources, getDrawable(R.drawable.scrim_left)!!, dm.widthPixels)
            scrimBottom.background = Layers.column(resources, getDrawable(R.drawable.scrim_bottom)!!, dm.heightPixels)
        }
        panel = findViewById(R.id.panel)
        tabHome = findViewById(R.id.tab_home)
        tabApps = findViewById(R.id.tab_apps)
        tabFavorites = findViewById(R.id.tab_favorites)
        tabSettings = findViewById(R.id.tab_settings)
        underline = findViewById(R.id.tab_underline)
        clock = findViewById(R.id.clock)
        wifi = findViewById(R.id.wifi)
        phone = findViewById(R.id.phone)
        grid = findViewById(R.id.apps_grid)
        hero = findViewById(R.id.hero)
        heroTitle = findViewById(R.id.hero_title)
        heroSubtitle = findViewById(R.id.hero_subtitle)
        heroBody = findViewById(R.id.hero_body)
        row = findViewById(R.id.row)
        shelves = findViewById(R.id.shelves)
        empty = findViewById(R.id.empty)
        emptyTitle = findViewById(R.id.empty_title)
        emptyHint = findViewById(R.id.empty_hint)

        setupTab(tabHome) { selectTab(Tab.HOME) }
        setupTab(tabApps) { selectTab(Tab.APPS) }
        setupTab(tabFavorites) { selectTab(Tab.FAVORITES) }
        setupTab(tabSettings) { selectTab(Tab.SETTINGS) }

        adapter = AppAdapter(emptyList(),
            onClick = { launchApp(it) },
            onLongClick = { appMenu(it) },
            onFocus = { onCardFocused(it) })
        row.adapter = adapter
        row.itemAnimator = null

        gridAdapter = AppAdapter(emptyList(),
            onClick = { launchApp(it) },
            onLongClick = { appMenu(it) },
            onFocus = { onCardFocused(it) })
        tileAdapter = TileAdapter(onClick = { it.action() }, onFocus = { onCardFocused(it) })
        grid.adapter = gridAdapter
        grid.itemAnimator = null
        grid.layoutManager = ParkingGridLayoutManager(this, 4)
        // Like the shelves: clip to our own bounds so rows scrolled above the top edge don't paint over the hero.
        grid.outlineProvider = ViewOutlineProvider.BOUNDS
        grid.clipToOutline = true
        // The grid is hidden at start, so size the columns whenever its width actually changes.
        grid.addOnLayoutChangeListener { v, l, _, r, _, ol, _, or_, _ ->
            if (r - l == or_ - ol) return@addOnLayoutChangeListener
            val cell = Cards.dp(v, 230f)
            val cols = ((r - l - v.paddingLeft - v.paddingRight) / cell).toInt().coerceIn(3, 6)
            val lm = grid.layoutManager as GridLayoutManager
            if (lm.spanCount != cols) grid.post { lm.spanCount = cols }
        }

        remote = RemoteLink(this) { updateRemoteIndicator(); if (tab == Tab.SETTINGS) refreshTiles() }

        home = Edition.home(this)
        home.onCreate()
        tab = if (home.startsOnHome) Tab.HOME else Tab.APPS
        row.upTarget = currentTabView()
        grid.upTarget = currentTabView()
        heroTitle.text = getString(if (tab == Tab.HOME) home.loadingText else R.string.loading)
        heroSubtitle.text = ""; heroBody.visibility = View.GONE
        underline.post { moveUnderline(currentTabView(), animate = false); paintTabs() }

        root.viewTreeObserver.addOnGlobalFocusChangeListener { old, new -> onFocusMoved(old, new) }
        // One synchronous read for the first frame; after that the network callback keeps it current.
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        wifi.visibility = if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) View.VISIBLE else View.GONE
        applyTheme()
        reloadApps()
        if (prefs.keepAwake) KeepAliveService.start(this)
    }

    override fun onResume() {
        super.onResume()
        lastWallpaperMinute = -1
        updateClock(); handler.post(tick)
        runCatching { connectivity.registerDefaultNetworkCallback(netCallback, handler) }
        registerReceiver(timeReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        if (!firstLoad) refreshApps()
        ensureScreensaver()
        remote.start()
    }

    private fun updateRemoteIndicator() {
        phone.visibility = if (remote.connected) View.VISIBLE else View.GONE
    }

    /** Vendor apps (jixin settings) re-point the screensaver on boot; reassert ours. */
    private fun ensureScreensaver() {
        val want = ComponentName(this, ClockDreamService::class.java).flattenToString()
        try {
            val cur = Settings.Secure.getString(contentResolver, "screensaver_components")
            if (cur != want) {
                Settings.Secure.putString(contentResolver, "screensaver_components", want)
                Settings.Secure.putInt(contentResolver, "screensaver_enabled", 1)
            }
        } catch (e: SecurityException) {
            // WRITE_SECURE_SETTINGS not granted; granted once via adb.
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        runCatching { connectivity.unregisterNetworkCallback(netCallback) }
        runCatching { unregisterReceiver(timeReceiver) }
        runCatching { unregisterReceiver(packageReceiver) }
        remote.stop()
    }

    override fun onDestroy() { super.onDestroy(); io.shutdownNow(); home.onDestroy() }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        panel.close()
        when (tab) {
            Tab.HOME -> home.scrollToTop()
            Tab.APPS, Tab.SETTINGS -> { grid.scrollToPosition(0); grid.post { firstGridCard()?.requestFocus() } }
            Tab.FAVORITES -> { row.scrollToPosition(0); row.post { firstCard(row)?.requestFocus() } }
        }
    }

    override fun onBackPressed() { /* home: swallow */ }

    // ---------- remote shortcuts ----------
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && event.repeatCount == 0 && !panel.isOpen) {
            val pkg = prefs.shortcuts()[keyCode]
            if (pkg != null) {
                AppRepository.launchIntentFor(this, pkg)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); safeStart(it); return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------- tabs ----------
    private val tabViews get() = listOf(tabHome, tabApps, tabFavorites, tabSettings)
    private val tabActions = HashMap<View, () -> Unit>()

    private fun setupTab(v: TextView, onSelect: () -> Unit) {
        v.setOnClickListener { onSelect() }
        tabActions[v] = onSelect
    }

    /**
     * Tabs select when focus *moves* onto them. Focus that arrives from nowhere is the system
     * picking the first focusable view after the focused card vanished (a list refresh, coming
     * back from another app); that must not switch tabs.
     */
    private fun onFocusMoved(old: View?, new: View?) {
        if (new != null && new in tabViews) {
            moveUnderline(new, animate = true)
            if (old != null) tabActions[new]?.invoke() else paintTabs()
        } else if (old != null && old in tabViews) {
            paintTabs()
            moveUnderline(currentTabView(), animate = true)
        }
    }

    private fun currentTabView(): TextView = when (tab) {
        Tab.HOME -> tabHome; Tab.APPS -> tabApps; Tab.FAVORITES -> tabFavorites; Tab.SETTINGS -> tabSettings
    }

    /** Focused and selected tabs are white, the rest grey. */
    private fun paintTabs() {
        for (t in listOf(tabHome, tabApps, tabFavorites, tabSettings)) {
            val active = t === currentTabView() || t.hasFocus()
            t.setTextColor(getColor(if (active) R.color.text_primary else R.color.text_secondary))
        }
    }

    private var underlineAnim: ValueAnimator? = null

    /**
     * Tab indicator: a line under the focused tab, a dot under the selected tab once focus has
     * moved on. Position, width and height animate together so one morphs into the other.
     */
    private fun moveUnderline(target: View, animate: Boolean) {
        val labelW = target.width - target.paddingLeft - target.paddingRight
        if (labelW <= 0) return
        val line = target.hasFocus()
        val dot = Cards.dp(underline, 6f).toInt()
        val h = if (line) Cards.dp(underline, 3f).toInt() else dot
        val w = if (line) labelW else dot
        val x = target.left + (target.parent as View).left + target.paddingLeft + (labelW - w) / 2
        val tx = (x - underline.left).toFloat()
        underlineAnim?.cancel()
        if (!animate) {
            underline.translationX = tx
            underline.layoutParams = underline.layoutParams.apply { width = w; height = h }
            return
        }
        val fromX = underline.translationX; val fromW = underline.width; val fromH = underline.height
        underlineAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280; interpolator = ease
            addUpdateListener {
                val f = it.animatedValue as Float
                underline.translationX = fromX + (tx - fromX) * f
                underline.layoutParams = underline.layoutParams.apply {
                    width = (fromW + (w - fromW) * f).toInt(); height = (fromH + (h - fromH) * f).toInt()
                }
            }
            start()
        }
    }

    private fun selectTab(t: Tab) {
        if (tab == t) { paintTabs(); return }
        tab = t
        row.upTarget = currentTabView()
        grid.upTarget = currentTabView()
        paintTabs()
        populate(animate = true)
    }

    // ---------- clock / status ----------
    private fun updateClock() {
        val now = Date()
        val pattern = buildString {
            append(if (prefs.clock24h) "H:mm" else "h:mm")
            if (prefs.showSeconds) append(":ss")
            if (!prefs.clock24h) append(" a")
        }
        val fmt = clockFormat?.takeIf { pattern == clockPattern }
            ?: SimpleDateFormat(pattern, Locale.getDefault()).also { clockFormat = it; clockPattern = pattern }
        fmt.timeZone = TimeZone.getDefault()   // a cached formatter keeps its zone; the user can change it in Settings
        val text = fmt.format(now)
        // Same text means no relayout and no frame: with seconds hidden the screen stays still for a minute.
        if (text != lastClockText) { lastClockText = text; clock.text = text }
        home.onTick(now)

        val minute = now.time / 60_000
        if (minute != lastWallpaperMinute) {
            lastWallpaperMinute = minute
            applyWallpaper()
            if (ambient && Sky.hour() != skyHour) root.background = skyBackground()
        }
    }

    private val connectivity by lazy { getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    /** Wi-Fi icon follows the default network; the system calls back on changes, nothing polls. */
    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            wifi.visibility = if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) View.VISIBLE else View.GONE
        }
        override fun onLost(network: Network) { wifi.visibility = View.GONE }
    }

    // ---------- theme / background ----------
    /** The Ambient theme is selected and this edition has it; the Lite edition folds this to false. */
    private val ambient: Boolean get() = Edition.AMBIENT && prefs.theme.dynamic

    /** One still background and one card style; nothing here depends on the focused item. */
    private fun applyTheme() {
        val t = prefs.theme
        Look.glass = t.glass
        if (ambient) root.background = skyBackground() else root.setBackgroundColor(t.background)
        scrimLeft.visibility = if (ambient) View.VISIBLE else View.GONE
        scrimBottom.visibility = if (ambient) View.VISIBLE else View.GONE
        if (!ambient) {
            crossfadeArt(null)
            tintAnim?.cancel(); tintColor = 0
            tint.visibility = View.INVISIBLE
        }
        panel.restyle(prefs.accentColor)
        restyleCards(root)
        userSlot = -1
        lastWallpaperMinute = -1
        applyWallpaper()
        // Ambient: paint the room for whatever is focused right now.
        if (ambient) handler.post(heroRunnable)
    }

    private fun skyBackground(): Drawable {
        skyHour = Sky.hour()
        return Layers.column(resources, Sky.drawable(skyHour), resources.displayMetrics.heightPixels)
    }

    // ---------- Ambient theme: the focused item paints the room ----------
    private fun ambientFor(app: AppInfo): Drawable? {
        val bmp: Bitmap = app.ambient ?: return null
        return BitmapDrawable(resources, bmp).apply { isFilterBitmap = true; alpha = 0x8C }
    }

    private fun crossfadeArt(d: Drawable?) {
        val show = if (artFront) artA else artB
        val hide = if (artFront) artB else artA
        if (d == null && show.drawable == null && hide.drawable == null) return
        artFront = !artFront
        show.setImageDrawable(d)
        show.scaleX = 1.08f; show.scaleY = 1.08f
        show.animate().cancel(); hide.animate().cancel()
        show.animate().alpha(if (d == null) 0f else 1f).scaleX(1f).scaleY(1f).setDuration(650).setInterpolator(ease).start()
        hide.animate().alpha(0f).setDuration(650).setInterpolator(ease)
            .withEndAction { hide.setImageDrawable(null) }.start()
    }

    private fun animateTint(color: Int) {
        val mask = tintDrawable ?: return
        if (color == tintColor) return
        val from = if (tintColor == 0) Color.TRANSPARENT else tintColor
        tintColor = color
        tint.visibility = View.VISIBLE
        tintAnim?.cancel()
        tintAnim = ValueAnimator.ofObject(ArgbEvaluator(), from, color).apply {
            duration = 650; interpolator = ease
            addUpdateListener { a ->
                val c = a.animatedValue as Int
                // The mask carries the alpha ramp; only the colour changes.
                mask.setTint(Color.rgb(Color.red(c), Color.green(c), Color.blue(c)))
            }
            start()
        }
    }

    /** Live cards take the new card style now; recycled ones take it when they are bound again. */
    private fun restyleCards(v: View) {
        if (v.id == R.id.card) v.setBackgroundResource(Look.cardBg)
        if (v is ViewGroup) for (i in 0 until v.childCount) restyleCards(v.getChildAt(i))
    }

    /** The theme's picture: a bundled one, or the user's own (a new one every half hour). */
    private fun applyWallpaper() {
        val t = prefs.theme
        val source: Any? = when {
            t.wallpaper != 0 -> t.wallpaper
            t.userWallpaper -> userPicture()
            else -> null
        }
        val dm = resources.displayMetrics
        when (val c = backdrop.change(source?.toString())) {
            Backdrop.Change.None -> if (source != null) dim.alpha = prefs.wallpaperDim / 100f
            Backdrop.Change.Clear -> showBackground(null)
            is Backdrop.Change.Load -> io.execute {
                val bmp = if (source is Int) Wallpaper.load(resources, source, dm.widthPixels, dm.heightPixels)
                          else Wallpaper.load(source as String, dm.widthPixels, dm.heightPixels)
                handler.post { if (backdrop.isCurrent(c.key)) showBackground(bmp) }
            }
        }
    }

    /** The user's picture for this half hour; the folder is listed once per slot, not every minute. */
    private fun userPicture(): String? {
        val slot = System.currentTimeMillis() / (30 * 60 * 1000)
        if (slot != userSlot) {
            userSlot = slot
            userPick = Wallpaper.images(this).takeIf { it.isNotEmpty() }?.let { it[(slot % it.size).toInt()].path }
        }
        return userPick
    }

    private fun showBackground(bmp: Bitmap?) {
        bg.animate().cancel(); dim.animate().cancel()
        if (bmp == null) {
            bg.animate().alpha(0f).setDuration(300).withEndAction { bg.setImageDrawable(null) }.start()
            dim.animate().alpha(0f).setDuration(300).start()
            return
        }
        bg.setImageBitmap(bmp)
        bg.animate().alpha(1f).setDuration(500).setInterpolator(ease).start()
        dim.animate().alpha(prefs.wallpaperDim / 100f).setDuration(500).start()
    }

    // ---------- apps ----------
    private fun reloadApps() {
        io.execute {
            val t0 = System.currentTimeMillis()
            val apps = runCatching { AppRepository.loadApps(this) }
                .onFailure { Log.e(TAG, "loadApps failed", it) }
                .getOrDefault(allApps)
            Log.i(TAG, "loaded ${apps.size} apps in ${System.currentTimeMillis() - t0}ms")
            handler.post {
                allApps = apps
                prefs.seedFavorites(apps.map { it.packageName })
                populate(animate = firstLoad)
                firstLoad = false
            }
        }
    }

    /** Back from another app: a changed set of installed apps reloads everything; otherwise nothing moved. */
    private fun refreshApps() {
        val current = allApps
        io.execute {
            val installed = runCatching { AppRepository.installedPackages(this) }.getOrNull()
            if (installed == null || installed != current.mapTo(HashSet()) { it.packageName }) reloadApps()
        }
    }

    private fun visibleApps(): List<AppInfo> = if (tab == Tab.FAVORITES) favoriteApps() else allApps

    private fun populate(animate: Boolean) {
        if (tab == Tab.HOME) {
            row.visibility = View.GONE
            grid.visibility = View.GONE
            home.show()
            return
        }
        home.hide()
        when (tab) {
            Tab.APPS -> populateGrid(animate)
            Tab.FAVORITES -> populateRow(animate)
            Tab.SETTINGS -> populateSettings()
            Tab.HOME -> {}
        }
    }

    // ---------- settings tab: quick-action tiles ----------
    private fun tiles(): List<Tile> {
        val out = ArrayList<Tile>()
        out += Tile("Phone remote", "Remote", remote.summary(), R.drawable.ic_phone) {
            remote.pairingIntent()?.let { safeStart(it) } ?: toast("The phone remote app isn't installed")
        }
        for ((s, intent) in SystemShortcuts.available(this)) {
            // Live state where the system lets us read it; the shortcut's own blurb otherwise.
            val detail = when (s.label) {
                "Wi-Fi" -> DeviceInfo.wifi(this)
                "Bluetooth" -> DeviceInfo.bluetooth(this)
                else -> s.detail
            }
            out += Tile(s.label, s.group, detail, s.icon) { safeStart(intent) }
        }
        out += Tile("Launcher", "This launcher", "Theme ${prefs.theme.name}  ·  Accent ${Prefs.ACCENTS[prefs.accentIndex].name}", R.drawable.ic_home) { showLauncherOptions() }
        return out
    }

    private fun populateSettings() {
        remote.query()
        row.visibility = View.GONE
        shelves.visibility = View.GONE
        empty.visibility = View.GONE
        grid.visibility = View.VISIBLE
        if (grid.adapter !== tileAdapter) grid.adapter = tileAdapter
        val list = tiles()
        tileAdapter.submit(list)
        grid.scrollToPosition(0)
        val focused = currentFocus
        val focusInGrid = focused != null && grid.findContainingItemView(focused) != null
        if (focusInGrid || focused == null) grid.post { if (!focusInside(grid)) firstGridCard()?.requestFocus() }
        if (!focusInGrid) onCardFocused(list[0])
    }

    /** Re-render tile text in place (e.g. the phone remote line) without disturbing focus. */
    private fun refreshTiles() {
        val list = tiles()
        tileAdapter.submit(list)
        (heroItem as? Tile)?.let { cur -> list.firstOrNull { it.title == cur.title }?.let { heroItem = it; renderTileHero(it) } }
    }

    private fun renderTileHero(t: Tile) {
        swapHero(animate = true) {
            heroTitle.text = t.title
            heroSubtitle.text = t.subtitle
            heroBody.text = t.detail
            heroBody.visibility = if (t.detail.isBlank()) View.GONE else View.VISIBLE
        }
        if (ambient) { crossfadeArt(null); animateTint(prefs.accentColor) }
    }

    override fun favoriteApps(): List<AppInfo> {
        val byPkg = allApps.associateBy { it.packageName }
        return prefs.favorites().mapNotNull { byPkg[it] }
    }

    // ---------- apps grid (Apps tab) ----------
    private fun populateGrid(animate: Boolean) {
        row.visibility = View.GONE
        shelves.visibility = View.GONE
        empty.visibility = View.GONE
        grid.visibility = View.VISIBLE
        if (grid.adapter !== gridAdapter) grid.adapter = gridAdapter
        gridAdapter.entrance = animate
        gridAdapter.submit(allApps)
        grid.scrollToPosition(0)
        if (animate) grid.post { gridAdapter.entrance = false }
        if (allApps.isEmpty()) return
        val focused = currentFocus
        val focusInGrid = focused != null && grid.findContainingItemView(focused) != null
        if (focusInGrid || focused == null) grid.post { if (!focusInside(grid)) firstGridCard()?.requestFocus() }
        keepOrResetHero(allApps, focusInGrid)
    }

    private fun firstGridCard(): View? = grid.getChildAt(0)?.findViewById(R.id.card)

    private fun focusInside(rv: RecyclerView): Boolean {
        val f = currentFocus ?: return false
        return rv.findContainingItemView(f) != null
    }

    private fun appInfo(app: AppInfo) {
        safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${app.packageName}")))
    }

    override fun showEmpty(title: Int, hint: Int) {
        emptyTitle.text = getString(title); emptyHint.text = getString(hint)
        empty.visibility = View.VISIBLE
        heroItem = null
        hero.animate().alpha(0f).setDuration(200).withLayer().start()
        if (ambient) { crossfadeArt(null); animateTint(prefs.accentColor) }
    }

    private fun populateRow(animate: Boolean) {
        val list = visibleApps()
        shelves.visibility = View.GONE
        grid.visibility = View.GONE
        adapter.entrance = animate
        adapter.submit(list)
        row.scrollToPosition(0)
        row.visibility = if (list.isEmpty()) View.INVISIBLE else View.VISIBLE
        if (animate) row.post { adapter.entrance = false }

        if (list.isEmpty()) {
            if (tab == Tab.FAVORITES) showEmpty(R.string.empty_favorites, R.string.empty_favorites_hint)
            return
        }
        empty.visibility = View.GONE
        val focused = currentFocus
        val focusInRow = focused != null && row.findContainingItemView(focused) != null
        // Browsing the row (or nothing focused): make sure a card holds focus. On a tab: leave focus there.
        if (focusInRow || focused == null) row.post { if (!focusInside(row)) firstCard(row)?.requestFocus() }
        // Entering the tab shows its first item; a refresh mid-browse keeps the one in view.
        keepOrResetHero(list, focusInRow)
    }

    /**
     * After a list refresh the AppInfo objects are new, so match the hero by package: keep it
     * (with fresh data) while the user is browsing, otherwise show the list's first item.
     */
    private fun keepOrResetHero(list: List<AppInfo>, browsing: Boolean) {
        val cur = heroItem as? AppInfo
        val match = if (browsing && cur != null) list.firstOrNull { it.packageName == cur.packageName } else null
        when {
            match == null -> onCardFocused(list[0])
            match !== cur -> { heroItem = match; renderAppHero(match, animate = false) }
        }
    }

    private fun firstCard(rv: View?): View? =
        (rv as? FocusRowRecyclerView)?.getChildAt(0)?.findViewById(R.id.card)

    override fun onCardFocused(item: Any) {
        if (heroItem == item) return
        heroItem = item
        handler.removeCallbacks(heroRunnable)
        handler.postDelayed(heroRunnable, 90)
    }

    // ---------- hero ----------
    private fun swapHero(animate: Boolean, apply: () -> Unit) {
        val gen = ++heroGen
        if (!animate) { apply(); hero.alpha = 1f; hero.translationY = 0f; return }
        hero.animate().cancel()
        hero.animate().alpha(0f).translationY(6f).setDuration(110).withLayer().withEndAction {
            if (gen != heroGen) return@withEndAction
            apply()
            hero.translationY = 14f
            hero.animate().alpha(1f).translationY(0f).setDuration(320).setInterpolator(ease).withLayer().start()
        }.start()
    }

    private fun renderAppHero(app: AppInfo, animate: Boolean) {
        val subtitle = app.category?.let { "$it app" }?.replaceFirstChar { it.uppercase() } ?: ""
        swapHero(animate) {
            heroTitle.text = app.label
            heroSubtitle.text = subtitle
            heroBody.visibility = View.GONE
        }
        if (ambient) { crossfadeArt(ambientFor(app)); animateTint(app.color) }
    }

    // ---------- launching ----------
    override fun launchApp(app: AppInfo) = launchPackage(app.packageName, app.label)

    private fun launchPackage(pkg: String, label: String) {
        val intent = AppRepository.launchIntentFor(this, pkg)
        if (intent == null) { toast("Can't open $label"); return }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        safeStart(intent)
    }

    // ---------- long-press menu (apps) ----------
    override fun appMenu(app: AppInfo) {
        panel.open {
            val fav = prefs.isFavorite(app.packageName)
            val rows = ArrayList<Row>()
            if (fav) {
                rows += Row("Unpin from favorites") { prefs.toggleFavorite(app.packageName); refreshIfFavorites(); panel.refresh() }
                rows += Row("Move left") { prefs.moveFavorite(app.packageName, -1); refreshIfFavorites() }
                rows += Row("Move right") { prefs.moveFavorite(app.packageName, +1); refreshIfFavorites() }
            } else {
                rows += Row("Pin to favorites") { prefs.toggleFavorite(app.packageName); refreshIfFavorites(); panel.refresh() }
            }
            rows += Row("Assign remote button") { captureShortcut(app) }
            rows += Row("App info") { panel.close(); appInfo(app) }
            Content(app.label, rows, message = app.category?.let { "$it app" })
        }
    }

    private fun refreshIfFavorites() {
        when (tab) {
            Tab.FAVORITES -> populateRow(animate = false)
            Tab.HOME -> home.onFavoritesChanged()
            Tab.APPS -> {}
            Tab.SETTINGS -> {}
        }
    }

    // ---------- remote shortcut capture ----------
    private val reservedKeys = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME
    )

    private fun captureShortcut(app: AppInfo) {
        panel.open {
            Content(
                "Assign a button to ${app.label}",
                listOf(Row("Cancel") { panel.back() }),
                message = "Press the remote button to use as a shortcut.\nDirection, OK, Back and Home can't be used.",
                onKey = { event ->
                    if (event.action == KeyEvent.ACTION_DOWN && event.keyCode !in reservedKeys) {
                        prefs.setShortcut(event.keyCode, app.packageName)
                        toast("${keyName(event.keyCode)} opens ${app.label}")
                        panel.back()
                        true
                    } else false
                }
            )
        }
    }

    private fun keyName(code: Int): String {
        val n = KeyEvent.keyCodeToString(code)
        return n.removePrefix("KEYCODE_").replace('_', ' ')
    }

    // ---------- launcher settings ----------
    private fun showLauncherOptions() {
        panel.open {
            val rows = ArrayList<Row>()
            rows += Row("Theme", prefs.theme.name) { pickTheme() }
            rows += Row("Accent color", Prefs.ACCENTS[prefs.accentIndex].name) { pickAccent() }
            if (prefs.theme.hasPicture) rows += Row("Picture dim", "${prefs.wallpaperDim}%") { pickDim() }
            rows += Row("24-hour clock", onOff(prefs.clock24h)) { prefs.clock24h = !prefs.clock24h; updateClock(); panel.refresh() }
            rows += Row("Show seconds", onOff(prefs.showSeconds)) { prefs.showSeconds = !prefs.showSeconds; updateClock(); panel.refresh() }
            rows += Row("Remote shortcuts") { shortcutsManager() }
            rows += home.settingsRows()
            rows += Row("Keep Wi-Fi awake in standby", onOff(prefs.keepAwake)) {
                prefs.keepAwake = !prefs.keepAwake
                if (prefs.keepAwake) KeepAliveService.start(this) else KeepAliveService.stop(this)
                panel.refresh()
            }
            Content("Launcher settings", rows)
        }
    }

    /** Picking a theme applies it at once, behind the panel, so the list doubles as a preview. */
    private fun pickTheme() {
        panel.open {
            val noPictures = prefs.theme.userWallpaper && Wallpaper.images(this).isEmpty()
            Content("Theme", Themes.ALL.mapIndexed { i, t ->
                Row(t.name, checked = i == prefs.themeIndex) {
                    if (prefs.themeIndex != i) { prefs.themeIndex = i; applyTheme(); refreshTilesIfSettings(); panel.refresh() }
                }
            }, message = if (noPictures) "No pictures yet. Copy some to:\n${Wallpaper.dir(this).absolutePath}" else null)
        }
    }

    private fun pickAccent() {
        panel.open {
            Content("Accent color", Prefs.ACCENTS.mapIndexed { i, a ->
                Row(a.name, checked = i == prefs.accentIndex) {
                    prefs.accentIndex = i; panel.restyle(prefs.accentColor); refreshTilesIfSettings(); panel.refresh()
                }
            })
        }
    }

    private fun pickDim() {
        val opts = intArrayOf(0, 20, 40, 60, 80)
        panel.open {
            Content("Picture dim", opts.map { v ->
                Row("$v%", checked = v == prefs.wallpaperDim) {
                    prefs.wallpaperDim = v; dim.animate().alpha(v / 100f).setDuration(200).start(); panel.refresh()
                }
            })
        }
    }

    private fun shortcutsManager() {
        panel.open {
            val map = prefs.shortcuts()
            val byPkg = allApps.associateBy { it.packageName }
            if (map.isEmpty()) {
                Content("Remote shortcuts", listOf(Row("OK") { panel.back() }),
                    message = "None yet. Hold OK on an app and choose \"Assign remote button\" to add one.")
            } else {
                Content("Remote shortcuts", map.entries.map { e ->
                    Row(keyName(e.key), byPkg[e.value]?.label ?: e.value) {
                        prefs.removeShortcut(e.key); toast("Removed ${keyName(e.key)}"); panel.refresh()
                    }
                }, message = "Select a button to remove its shortcut.")
            }
        }
    }

    private fun refreshTilesIfSettings() { if (tab == Tab.SETTINGS) refreshTiles() }

    private fun onOff(b: Boolean) = if (b) "On" else "Off"

    // ---------- HomeHost: what the edition's Home tab may use ----------
    override val activity: Activity get() = this
    override val homeTabView: View get() = tabHome
    override val isHomeShowing: Boolean get() = tab == Tab.HOME
    override fun apps(): List<AppInfo> = allApps
    override fun settingsTiles(): List<Tile> = tiles()
    override fun setHero(title: String, subtitle: String, body: String?) {
        swapHero(animate = true) {
            heroTitle.text = title
            heroSubtitle.text = subtitle
            heroBody.text = body ?: ""
            heroBody.visibility = if (body.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }
    override fun paintRoom(art: Bitmap?, color: Int) {
        if (!ambient) return
        crossfadeArt(art?.let { BitmapDrawable(resources, it).apply { alpha = 0x99 } })
        animateTint(color)
    }
    override fun setHeroVisible(visible: Boolean) { hero.visibility = if (visible) View.VISIBLE else View.GONE }
    override fun setStatusClockVisible(visible: Boolean) { clock.visibility = if (visible) View.VISIBLE else View.GONE }
    override fun hideEmpty() { empty.visibility = View.GONE }
    override fun showHome() { if (tab == Tab.HOME) populate(animate = false) else selectTab(Tab.HOME) }

    override fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    override fun safeStart(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure { toast("Not available") }
    }

    private companion object { const val TAG = "Launcher" }
}
