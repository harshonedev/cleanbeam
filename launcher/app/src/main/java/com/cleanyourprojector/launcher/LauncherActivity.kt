package io.egate.launcher

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.app.AlertDialog
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
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.text.InputType
import android.text.format.DateUtils
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.egate.launcher.apps.AppInfo
import io.egate.launcher.apps.AppRepository
import io.egate.launcher.catalog.HomeRow
import io.egate.launcher.catalog.JustWatch
import io.egate.launcher.catalog.Playback
import io.egate.launcher.catalog.Service
import io.egate.launcher.catalog.Services
import io.egate.launcher.catalog.Title
import io.egate.launcher.catalog.Tmdb
import io.egate.launcher.graphics.Cards
import io.egate.launcher.graphics.ImageLoader
import io.egate.launcher.graphics.Layers
import io.egate.launcher.graphics.Sky
import io.egate.launcher.graphics.Wallpaper
import io.egate.launcher.settings.DeviceInfo
import io.egate.launcher.settings.SystemShortcuts
import io.egate.launcher.settings.Tile
import io.egate.launcher.system.ClockDreamService
import io.egate.launcher.system.KeepAliveService
import io.egate.launcher.system.RemoteLink
import io.egate.launcher.ui.AppAdapter
import io.egate.launcher.ui.FocusGridRecyclerView
import io.egate.launcher.ui.FocusRowRecyclerView
import io.egate.launcher.ui.HomeRowsAdapter
import io.egate.launcher.ui.ParkingGridLayoutManager
import io.egate.launcher.ui.ShelvesRecyclerView
import io.egate.launcher.ui.TileAdapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class LauncherActivity : AppCompatActivity() {

    private enum class Tab { HOME, APPS, FAVORITES, SETTINGS }

    private lateinit var prefs: Prefs
    private lateinit var root: View
    private lateinit var bgA: ImageView
    private lateinit var bgB: ImageView
    private lateinit var tint: View
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
    private lateinit var heroMeta: TextView
    private lateinit var heroBody: TextView
    private lateinit var row: FocusRowRecyclerView
    private lateinit var shelves: ShelvesRecyclerView
    private lateinit var empty: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyHint: TextView
    private lateinit var stats: TextView
    private lateinit var adapter: AppAdapter
    private lateinit var shelvesAdapter: HomeRowsAdapter

    private var allApps: List<AppInfo> = emptyList()
    private var homeRows: List<HomeRow> = emptyList()
    private var homeLoadedAt = 0L
    private var homeLoading = false
    private var homeGen = 0                    // bumps per Home load so a superseded fetch can't land
    private var tab = Tab.APPS
    private var heroItem: Any? = null          // AppInfo, Pair<Title, HomeRow> or Tile
    private var bgFront = false                // which bg layer is visible: false = A, true = B
    private var tintColor = 0
    private var tintAnim: ValueAnimator? = null
    private lateinit var tintDrawable: BitmapDrawable   // white gradient mask, tinted with the room colour
    private var wallpaperKey = ""
    private var firstLoad = true
    private var homeFirstShow = true           // cold start lands on the first card even if a tab holds focus
    private var heroGen = 0                    // bumps per hero change so a cancelled fade can't leave it hidden
    private var usageAccess = false            // AppOps answer, refreshed on resume instead of per hero
    private var clockPattern = ""
    private var clockFormat: SimpleDateFormat? = null
    private var lastClockText: String? = null
    private var lastWallpaperMinute = -1L

    private val io = Executors.newSingleThreadExecutor()
    private val rowsIo = Executors.newFixedThreadPool(3)   // Home shelves fetch side by side
    private val handler = Handler(Looper.getMainLooper())
    private val ease = PathInterpolator(0.2f, 0f, 0f, 1f)
    private val heroRunnable = Runnable {
        when (val it = heroItem) {
            is AppInfo -> renderAppHero(it, animate = true)
            is Pair<*, *> -> renderTitleHero(it.first as Title, it.second as HomeRow)
            is Tile -> renderTileHero(it)
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

    private val region: String get() = prefs.region.ifBlank { Tmdb.defaultRegion() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_launcher)
        // The theme's gradient is only the launch splash: root paints the whole screen itself, so
        // drop the window background instead of filling 1280x720 twice on every frame.
        window.setBackgroundDrawable(null)

        root = findViewById(R.id.root)
        bgA = findViewById(R.id.bg_a)
        bgB = findViewById(R.id.bg_b)
        tint = findViewById(R.id.tint)
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
        heroMeta = findViewById(R.id.hero_meta)
        heroBody = findViewById(R.id.hero_body)
        row = findViewById(R.id.row)
        shelves = findViewById(R.id.shelves)
        empty = findViewById(R.id.empty)
        emptyTitle = findViewById(R.id.empty_title)
        emptyHint = findViewById(R.id.empty_hint)
        stats = findViewById(R.id.stats)

        // Full-screen gradients cost this GPU ~5 ms each per frame as shaders; as bitmaps they are free.
        val dm = resources.displayMetrics
        tintDrawable = Layers.tintMask(resources, dm.widthPixels / 4, dm.heightPixels / 4)
        tint.background = tintDrawable
        tint.visibility = View.INVISIBLE   // nothing to show until the first card paints the room
        findViewById<View>(R.id.scrim_left_v).background = Layers.row(resources, getDrawable(R.drawable.scrim_left)!!, dm.widthPixels)
        findViewById<View>(R.id.scrim_bottom_v).background = Layers.column(resources, getDrawable(R.drawable.scrim_bottom)!!, dm.heightPixels)
        root.background = skyBackground()

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

        shelvesAdapter = HomeRowsAdapter(
            onClick = { t, r -> openTitle(t, r) },
            onLongClick = { t, r -> titleMenu(t, r) },
            onFocus = { t, r -> onCardFocused(t to r) },
            onAppClick = { launchApp(it) },
            onAppLongClick = { appMenu(it) },
            onAppFocus = { onCardFocused(it) })
        shelvesAdapter.upTarget = tabHome
        shelves.adapter = shelvesAdapter
        shelves.itemAnimator = null

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

        tab = if (Tmdb.configured(this)) Tab.HOME else Tab.APPS
        row.upTarget = currentTabView()
        grid.upTarget = currentTabView()
        heroTitle.text = getString(if (tab == Tab.HOME) R.string.home_loading else R.string.loading)
        heroSubtitle.text = ""; heroMeta.visibility = View.GONE
        underline.post { moveUnderline(currentTabView(), animate = false); paintTabs() }

        root.viewTreeObserver.addOnGlobalFocusChangeListener { old, new -> onFocusMoved(old, new) }
        // One synchronous read for the first frame; after that the network callback keeps it current.
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        wifi.visibility = if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) View.VISIBLE else View.GONE
        reloadApps()
        if (prefs.keepAwake) KeepAliveService.start(this)
    }

    override fun onResume() {
        super.onResume()
        usageAccess = AppRepository.hasUsageAccess(this)
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

    override fun onDestroy() { super.onDestroy(); io.shutdownNow(); rowsIo.shutdownNow() }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        when (tab) {
            Tab.HOME -> { shelves.scrollToPosition(0); focusFirstShelfCard() }
            Tab.APPS, Tab.SETTINGS -> { grid.scrollToPosition(0); grid.post { firstGridCard()?.requestFocus() } }
            Tab.FAVORITES -> { row.scrollToPosition(0); row.post { firstCard(row)?.requestFocus() } }
        }
    }

    override fun onBackPressed() { /* home: swallow */ }

    // ---------- remote shortcuts ----------
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && event.repeatCount == 0) {
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

    // ---------- clock / status / background ----------
    private fun updateClock() {
        val now = Date()
        val pattern = buildString {
            append(if (prefs.clock24h) "H:mm" else "h:mm")
            if (prefs.showSeconds) append(":ss")
            if (!prefs.clock24h) append(" a")
        }
        val fmt = clockFormat?.takeIf { pattern == clockPattern }
            ?: SimpleDateFormat(pattern, Locale.getDefault()).also { clockFormat = it; clockPattern = pattern }
        val text = fmt.format(now)
        // Same text means no relayout and no frame: with seconds hidden the screen stays still for a minute.
        if (text != lastClockText) { lastClockText = text; clock.text = text }

        if (prefs.showStats) {
            stats.visibility = View.VISIBLE
            val line = statsLine()
            if (line != stats.text.toString()) stats.text = line
        } else stats.visibility = View.GONE

        val minute = now.time / 60_000
        if (minute != lastWallpaperMinute) { lastWallpaperMinute = minute; applyWallpaper() }
    }

    private val connectivity by lazy { getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    /** Wi-Fi icon follows the default network; the system calls back on changes, nothing polls. */
    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            wifi.visibility = if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) View.VISIBLE else View.GONE
        }
        override fun onLost(network: Network) { wifi.visibility = View.GONE }
    }

    /** Wallpaper mode replaces per-item art with a user image; otherwise the focused item paints the room. */
    private fun applyWallpaper() {
        val imgs = if (prefs.wallpaper) Wallpaper.images(this) else emptyList()
        if (imgs.isEmpty()) {
            if (wallpaperKey.isNotEmpty()) { wallpaperKey = ""; handler.post(heroRunnable) }
            return
        }
        val idx = ((System.currentTimeMillis() / (30 * 60 * 1000)) % imgs.size).toInt()
        val key = "${imgs[idx].path}:${prefs.wallpaperDim}"
        if (key == wallpaperKey) return
        wallpaperKey = key
        io.execute {
            val bmp = Wallpaper.load(imgs[idx].path, 1280, 720) ?: return@execute
            val d = BitmapDrawable(resources, bmp).apply { alpha = 255 - prefs.wallpaperDim * 255 / 100 }
            handler.post { crossfadeArt(d) }
        }
    }

    private fun ambientFor(app: AppInfo): Drawable? {
        val bmp: Bitmap = app.ambient ?: return null
        return BitmapDrawable(resources, bmp).apply { isFilterBitmap = true; alpha = 0x8C }
    }

    private fun crossfadeArt(d: Drawable?) {
        val show = if (bgFront) bgA else bgB
        val hide = if (bgFront) bgB else bgA
        bgFront = !bgFront
        show.setImageDrawable(d)
        show.scaleX = 1.08f; show.scaleY = 1.08f
        show.animate().cancel(); hide.animate().cancel()
        show.animate().alpha(if (d == null) 0f else 1f).scaleX(1f).scaleY(1f).setDuration(650).setInterpolator(ease).start()
        hide.animate().alpha(0f).setDuration(650).setInterpolator(ease)
            .withEndAction { hide.setImageDrawable(null) }.start()
    }

    private fun skyBackground(): Drawable =
        Layers.column(resources, Sky.drawable(prefs.ambientSky), resources.displayMetrics.heightPixels)

    private fun animateTint(color: Int) {
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
                tintDrawable.setTint(Color.rgb(Color.red(c), Color.green(c), Color.blue(c)))
            }
            start()
        }
    }

    private fun statsLine(): String {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
        val ramFree = mi.availMem / (1024.0 * 1024 * 1024)
        val fs = StatFs(Environment.getDataDirectory().path)
        val storFree = fs.availableBytes / (1024.0 * 1024 * 1024)
        return String.format(Locale.US, "RAM %.1fG free    Storage %.0fG free", ramFree, storFree)
    }

    // ---------- apps ----------
    private fun reloadApps() {
        io.execute {
            val t0 = System.currentTimeMillis()
            val apps = runCatching { AppRepository.loadApps(this) }
                .onFailure { Log.e("EGate", "loadApps failed", it) }
                .getOrDefault(allApps)
            Log.i("EGate", "loaded ${apps.size} apps in ${System.currentTimeMillis() - t0}ms")
            handler.post {
                allApps = apps
                prefs.seedFavorites(apps.map { it.packageName })
                populate(animate = firstLoad)
                firstLoad = false
            }
        }
    }

    /**
     * Back from another app: only the usage figures can have moved, so refresh those and keep the
     * decoded icons, banners and colors. A changed set of installed apps still reloads everything.
     */
    private fun refreshApps() {
        val current = allApps
        io.execute {
            val installed = runCatching { AppRepository.installedPackages(this) }.getOrNull()
            if (installed == null || installed != current.mapTo(HashSet()) { it.packageName }) { reloadApps(); return@execute }
            val apps = runCatching { AppRepository.withUsage(this, current) }.getOrDefault(current)
            handler.post { allApps = apps; populate(animate = false) }
        }
    }

    private fun visibleApps(): List<AppInfo> = if (tab == Tab.FAVORITES) favoriteApps() else allApps

    private fun populate(animate: Boolean) {
        when (tab) {
            Tab.HOME -> populateHome()
            Tab.APPS -> populateGrid(animate)
            Tab.FAVORITES -> populateRow(animate)
            Tab.SETTINGS -> populateSettings()
        }
    }

    // ---------- settings tab: quick-action tiles ----------
    private fun tiles(): List<Tile> {
        val out = ArrayList<Tile>()
        out += Tile("Phone remote", "Remote", remote.summary(), R.drawable.ic_phone) {
            remote.pairingIntent()?.let { safeStart(it) } ?: toast("EGate Remote isn't installed")
        }
        for ((s, intent) in SystemShortcuts.available(this)) {
            // Live state where the system lets us read it; the shortcut's own blurb otherwise.
            val detail = when (s.label) {
                "Wi-Fi" -> DeviceInfo.wifi(this)
                "Bluetooth" -> DeviceInfo.bluetooth(this)
                "System settings" -> DeviceInfo.system()
                "Projector settings" -> DeviceInfo.firmware()
                else -> s.detail
            }
            out += Tile(s.label, s.group, detail, s.icon) { safeStart(intent) }
        }
        val ver = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
        out += Tile("Launcher", "This launcher", "EGate Home $ver  ·  accent ${Prefs.ACCENTS[prefs.accentIndex].name}  ·  region $region", R.drawable.ic_home) { showLauncherOptions() }
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
            heroMeta.text = t.detail
            heroMeta.visibility = View.VISIBLE
            heroBody.visibility = View.GONE
        }
        if (wallpaperKey.isEmpty()) crossfadeArt(null)
        animateTint(prefs.accentColor)
    }

    private fun favoriteApps(): List<AppInfo> {
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

    private fun showEmpty(title: Int, hint: Int) {
        emptyTitle.text = getString(title); emptyHint.text = getString(hint)
        empty.visibility = View.VISIBLE
        heroItem = null
        hero.animate().alpha(0f).setDuration(200).withLayer().start()
        crossfadeArt(null); animateTint(prefs.accentColor)
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

    // ---------- home (TMDB) ----------
    /** Favorites shelf first, then the TMDB shelves (loaded, or skeletons while they load). */
    private fun displayRows(): List<HomeRow> {
        val favs = favoriteApps()
        val favRow = if (favs.isEmpty()) emptyList() else listOf(HomeRow(getString(R.string.tab_favorites), emptyList(), null, null, favs))
        return favRow + homeRows.filter { it.visible }
    }

    private fun populateHome() {
        row.visibility = View.GONE
        grid.visibility = View.GONE
        val configured = Tmdb.configured(this)
        val stale = System.currentTimeMillis() - homeLoadedAt > Tmdb.ROWS_TTL
        // Kick the fetch first: on a cold start it seeds skeleton shelves so the tab paints at once.
        if (configured && (stale || homeRows.isEmpty()) && !homeLoading && allApps.isNotEmpty()) loadHome()
        if (!configured && homeRows.isEmpty() && favoriteApps().isEmpty()) {
            shelves.visibility = View.GONE
            shelvesAdapter.submit(emptyList())
            showEmpty(R.string.home_no_token, R.string.home_no_token_hint)
            return
        }
        renderHome()
    }

    /** Draw whatever Home has right now: favorites, finished shelves, skeletons; or the empty state. */
    private fun renderHome() {
        val rows = displayRows()
        if (rows.isEmpty()) {
            shelves.visibility = View.GONE
            shelvesAdapter.submit(emptyList())
            showEmpty(R.string.home_empty, R.string.home_empty_hint)
        } else showShelves(rows)
    }

    /**
     * Shelves load one by one, side by side. On a cold start each starts as a skeleton row and is
     * swapped in as it arrives; a refresh behind rows already on screen swaps them all at the end.
     */
    private fun loadHome() {
        homeLoading = true
        val gen = ++homeGen
        val apps = allApps
        val reg = region
        val pending = Tmdb.plan(this, apps, reg)
        val cold = homeRows.isEmpty()
        if (cold) homeRows = pending.map { it.row }
        val done = arrayOfNulls<HomeRow>(pending.size)
        val remaining = java.util.concurrent.atomic.AtomicInteger(pending.size)
        io.execute {
            val providers = runCatching { Tmdb.providers(this, reg) }
                .onFailure { Log.e("EGate", "tmdb providers failed", it) }.getOrDefault(emptyList())
            pending.forEachIndexed { i, p ->
                rowsIo.execute {
                    val r = runCatching { p.load(providers) }
                        .onFailure { Log.e("EGate", "tmdb row '${p.row.header}' failed", it) }
                        .getOrDefault(p.row.copy(loading = false))
                    done[i] = r
                    val last = remaining.decrementAndGet() == 0
                    handler.post {
                        if (gen != homeGen) return@post
                        if (cold) homeRows = homeRows.map { if (it.header == r.header) r else it }
                        if (last) {
                            homeLoading = false
                            val rows = done.filterNotNull().filter { it.titles.isNotEmpty() }
                            Log.i("EGate", "home rows: ${rows.map { "${it.header} (${it.titles.size})" }}")
                            if (rows.isNotEmpty()) { homeRows = rows; homeLoadedAt = System.currentTimeMillis() }
                            else if (cold) homeRows = emptyList()
                        }
                        if ((cold || last) && tab == Tab.HOME) renderHome()
                    }
                }
            }
        }
    }

    private fun showShelves(rows: List<HomeRow>) {
        shelves.visibility = View.VISIBLE
        empty.visibility = View.GONE
        val before = currentFocus
        val focusWasInShelves = before != null && shelves.findContainingItemView(before) != null
        shelvesAdapter.submit(rows)
        val focused = currentFocus
        val focusInShelves = focused != null && shelves.findContainingItemView(focused) != null
        // A rebind recreated the focused card: put focus back on the first card.
        if (focusWasInShelves && !focusInShelves) focusFirstShelfCard()
        if (!focusInShelves && !focusWasInShelves) {
            // Skeletons only: the hero and the landing focus wait for the first real shelf.
            val first = rows.firstOrNull { !it.loading } ?: return
            onCardFocused(if (first.isAppRow) first.apps[0] else first.titles[0] to first)
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
        shelves.post {
            val card = (0 until shelves.childCount).asSequence()
                .map { firstCard(shelves.getChildAt(it)?.findViewById(R.id.row_titles)) }
                .firstOrNull { it?.isFocusable == true }
            if (card != null) card.requestFocus()
            else if (attempt < 8 && tab == Tab.HOME) handler.postDelayed({ focusFirstShelfCard(attempt + 1) }, 60)
            else Log.w("EGate", "first shelf card not found after $attempt attempts")
        }
    }

    private fun onCardFocused(item: Any) {
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
        val subtitle = listOfNotNull(
            app.category?.let { "$it app" },
            shortVersion(app.version)?.let { "version $it" }
        ).joinToString(", ").replaceFirstChar { it.uppercase() }
        val meta = when {
            app.lastUsed > 0 -> {
                val rel = relative(app.lastUsed, DateUtils.MINUTE_IN_MILLIS)
                val hrs = app.foregroundMs / 3_600_000
                if (hrs >= 1) "Last opened $rel, ${hrs}h this month" else "Last opened $rel"
            }
            usageAccess -> "Not opened this month"
            app.installTime > 0 -> "Installed " + relative(app.installTime, DateUtils.DAY_IN_MILLIS)
            else -> ""
        }
        swapHero(animate) {
            heroTitle.text = app.label
            heroSubtitle.text = subtitle
            heroMeta.text = meta
            heroMeta.visibility = if (meta.isEmpty()) View.GONE else View.VISIBLE
            heroBody.visibility = View.GONE
        }
        if (wallpaperKey.isEmpty()) crossfadeArt(ambientFor(app))
        animateTint(if (prefs.appGlow) app.color else prefs.accentColor)
    }

    private fun renderTitleHero(t: Title, r: HomeRow) {
        val subtitle = listOfNotNull(
            if (t.kind == "tv") "Series" else "Film",
            t.year,
            if (t.rating > 0) String.format(Locale.US, "%.1f on TMDB", t.rating) else null
        ).joinToString(", ")
        swapHero(animate = true) {
            heroTitle.text = t.name
            heroSubtitle.text = subtitle
            heroMeta.visibility = View.GONE
            heroBody.text = t.overview
            heroBody.visibility = if (t.overview.isBlank()) View.GONE else View.VISIBLE
        }
        val url = Tmdb.imageUrl(t.backdrop, "w780") ?: return
        if (wallpaperKey.isNotEmpty() && !prefs.appGlow) return
        ImageLoader.fetch(this, url, 800, { bmp -> bmp to AppRepository.colorOf(bmp) }) { res ->
            if (res == null || (heroItem as? Pair<*, *>)?.first != t) return@fetch
            if (wallpaperKey.isEmpty()) crossfadeArt(BitmapDrawable(resources, res.first).apply { alpha = 0x99 })
            animateTint(if (prefs.appGlow) res.second else prefs.accentColor)
        }
    }

    /** "2 hours ago" / "yesterday" read mid-sentence; a date like "May 26" keeps its capital. */
    private fun relative(time: Long, minRes: Long): String {
        val s = DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), minRes).toString()
        return if (s.endsWith("ago") || s.equals("yesterday", true) || s.equals("today", true))
            s.replaceFirstChar { it.lowercase() } else s
    }

    /** Build suffixes like "-857911644-release" are noise on a TV. Keep the dotted part. */
    private fun shortVersion(v: String?): String? {
        if (v.isNullOrBlank()) return null
        val core = v.trim().takeWhile { it.isDigit() || it == '.' }
        return (if (core.length >= 3) core else v.trim()).take(24)
    }

    // ---------- launching ----------
    private fun launchApp(app: AppInfo) = launchPackage(app.packageName, app.label)

    private fun launchPackage(pkg: String, label: String) {
        val intent = AppRepository.launchIntentFor(this, pkg)
        if (intent == null) { toast("Can't open $label"); return }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        safeStart(intent)
    }

    /** One way to watch a title: an installed app to open, or a service to install. */
    private class WatchOption(val service: Service, val app: AppInfo?)

    /**
     * OK on a title: find the services streaming it here, then play it in the one installed app,
     * offer to install the one missing app, or ask when there is more than one choice.
     */
    private fun openTitle(t: Title, r: HomeRow) = watchOptions(t, r) { options ->
        when (options.size) {
            0 -> toast("${t.name} isn't streaming in $region right now")
            1 -> watch(t, options[0])
            else -> chooseWatchOption(t, options)
        }
    }

    /** Long press on a title: always show the full list of services carrying it. */
    private fun titleMenu(t: Title, r: HomeRow) = watchOptions(t, r) { options ->
        if (options.isEmpty()) toast("${t.name} isn't streaming in $region right now")
        else chooseWatchOption(t, options)
    }

    private fun watchOptions(t: Title, r: HomeRow, cb: (List<WatchOption>) -> Unit) {
        val apps = allApps; val reg = region
        io.execute {
            val services = Services.forTmdbNames(Tmdb.providersFor(this, t, reg)).toMutableList()
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
        val labels = options.map { o -> if (o.app != null) "Open in ${o.app.label}" else "Install ${o.service.name}" }
        AlertDialog.Builder(this).setTitle(t.name)
            .setItems(labels.toTypedArray()) { _, i -> watch(t, options[i]) }
            .show()
    }

    private fun watch(t: Title, o: WatchOption) {
        val app = o.app
        if (app == null) {
            val intent = Playback.storeIntent(this, o.service.storePkg)
            if (intent == null) toast("Can't open ${o.service.name}") else safeStart(intent)
            return
        }
        val reg = region
        io.execute {
            // Per-title link (network, cached); null just means the search link is used instead.
            val link = runCatching { JustWatch.urlFor(this, t, reg, o.service) }
                .onFailure { Log.w("EGate", "justwatch ${t.name} failed", it) }.getOrNull()
            val intent = Playback.playIntent(this, app.packageName, o.service, t.name, link)
            Log.i("EGate", "watch '${t.name}' in ${app.packageName}: link=$link -> ${intent?.toUri(0)}")
            handler.post { if (intent == null) toast("Can't open ${o.service.name}") else safeStart(intent) }
        }
    }

    // ---------- long-press menu (apps) ----------
    private fun appMenu(app: AppInfo) {
        val fav = prefs.isFavorite(app.packageName)
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (fav) {
            actions += "Unpin from favorites" to { prefs.toggleFavorite(app.packageName); refreshIfFavorites() }
            actions += "Move left" to { prefs.moveFavorite(app.packageName, -1); refreshIfFavorites() }
            actions += "Move right" to { prefs.moveFavorite(app.packageName, +1); refreshIfFavorites() }
        } else {
            actions += "Pin to favorites" to { prefs.toggleFavorite(app.packageName); refreshIfFavorites() }
        }
        actions += "Assign remote button" to { captureShortcut(app) }
        actions += "App info" to { appInfo(app) }
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(actions.map { it.first }.toTypedArray()) { _, i -> actions[i].second() }
            .show()
    }

    private fun refreshIfFavorites() {
        when (tab) {
            Tab.FAVORITES -> populateRow(animate = false)
            Tab.HOME -> shelvesAdapter.submit(displayRows())
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
        val dlg = AlertDialog.Builder(this)
            .setTitle("Assign button to ${app.label}")
            .setMessage("Press the remote button to use as a shortcut.\n(Direction, OK, Back and Home can't be used.)")
            .setNegativeButton("Cancel", null)
            .create()
        dlg.setOnKeyListener { d, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode !in reservedKeys) {
                prefs.setShortcut(keyCode, app.packageName)
                d.dismiss()
                toast("${keyName(keyCode)} -> ${app.label}")
                true
            } else false
        }
        dlg.show()
    }

    private fun keyName(code: Int): String {
        val n = KeyEvent.keyCodeToString(code)
        return n.removePrefix("KEYCODE_").replace('_', ' ')
    }

    // ---------- options ----------
    private fun showLauncherOptions() {
        val tokenState = if (Tmdb.configured(this)) "Set" else "Not set"
        val labels = arrayOf(
            "TMDB access token: $tokenState",
            "Region: $region",
            "Refresh Home",
            "Accent color: ${Prefs.ACCENTS[prefs.accentIndex].name}",
            "Tint room with art color: ${onOff(prefs.appGlow)}",
            "Wallpaper: ${onOff(prefs.wallpaper)}",
            "Ambient sky background: ${onOff(prefs.ambientSky)}",
            "System stats line: ${onOff(prefs.showStats)}",
            "24-hour clock: ${onOff(prefs.clock24h)}",
            "Show seconds: ${onOff(prefs.showSeconds)}",
            "Remote shortcuts…",
            "Keep Wi-Fi awake in standby: ${onOff(prefs.keepAwake)}"
        )
        AlertDialog.Builder(this)
            .setTitle("Launcher settings")
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> enterToken()
                    1 -> pickRegion()
                    2 -> { Tmdb.clearCache(this); JustWatch.clearCache(this); homeRows = emptyList(); homeLoadedAt = 0; if (tab == Tab.HOME) populateHome() else selectTab(Tab.HOME) }
                    3 -> pickAccent()
                    4 -> { prefs.appGlow = !prefs.appGlow; retint(); showLauncherOptions() }
                    5 -> wallpaperMenu()
                    6 -> { prefs.ambientSky = !prefs.ambientSky; root.background = skyBackground(); showLauncherOptions() }
                    7 -> { prefs.showStats = !prefs.showStats; updateClock(); showLauncherOptions() }
                    8 -> { prefs.clock24h = !prefs.clock24h; updateClock(); showLauncherOptions() }
                    9 -> { prefs.showSeconds = !prefs.showSeconds; updateClock(); showLauncherOptions() }
                    10 -> shortcutsManager()
                    11 -> {
                        prefs.keepAwake = !prefs.keepAwake
                        if (prefs.keepAwake) KeepAliveService.start(this) else KeepAliveService.stop(this)
                        showLauncherOptions()
                    }
                }
            }
            .show()
    }

    private fun enterToken() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            hint = "v4 read access token or v3 API key"
            setText(prefs.tmdbToken)
            setSingleLine()
        }
        AlertDialog.Builder(this).setTitle("TMDB access token")
            .setMessage("Free at themoviedb.org → Settings → API. Or push it to files/tmdb.key with adb.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                prefs.tmdbToken = input.text.toString()
                Tmdb.invalidateToken()
                Tmdb.clearCache(this); JustWatch.clearCache(this); homeRows = emptyList(); homeLoadedAt = 0
                if (tab == Tab.HOME) populateHome() else selectTab(Tab.HOME)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickRegion() {
        val codes = arrayOf("IN", "US", "GB", "CA", "AU", "DE", "FR", "ES", "IT", "BR", "MX", "JP", "KR", "SG", "AE")
        val cur = codes.indexOf(region).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("Region for streaming availability")
            .setSingleChoiceItems(codes, cur) { d, i ->
                prefs.region = codes[i]
                Tmdb.clearCache(this); JustWatch.clearCache(this); homeRows = emptyList(); homeLoadedAt = 0
                d.dismiss()
                if (tab == Tab.HOME) populateHome() else showLauncherOptions()
            }.show()
    }

    private fun retint() { tintColor = 0; handler.post(heroRunnable) }

    private fun pickAccent() {
        val names = Prefs.ACCENTS.map { it.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Accent color")
            .setSingleChoiceItems(names, prefs.accentIndex) { d, i ->
                prefs.accentIndex = i; retint(); d.dismiss(); showLauncherOptions()
            }.show()
    }

    private fun wallpaperMenu() {
        val imgs = Wallpaper.images(this)
        val dir = Wallpaper.dir(this).absolutePath
        val labels = arrayOf(
            "Wallpaper: ${onOff(prefs.wallpaper)}",
            "Dim: ${prefs.wallpaperDim}%",
            "Images found: ${imgs.size}"
        )
        AlertDialog.Builder(this)
            .setTitle("Wallpaper")
            .setItems(labels) { _, i ->
                when (i) {
                    0 -> {
                        prefs.wallpaper = !prefs.wallpaper
                        if (prefs.wallpaper && imgs.isEmpty()) toast("Add images to: $dir")
                        wallpaperKey = ""; applyWallpaper(); wallpaperMenu()
                    }
                    1 -> pickDim()
                    2 -> toast("adb push pic.jpg $dir/")
                }
            }
            .show()
    }

    private fun pickDim() {
        val opts = intArrayOf(0, 20, 40, 60, 80)
        val labels = opts.map { "$it%" }.toTypedArray()
        val cur = opts.indexOf(prefs.wallpaperDim).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("Wallpaper dim")
            .setSingleChoiceItems(labels, cur) { d, i ->
                prefs.wallpaperDim = opts[i]; wallpaperKey = ""; applyWallpaper(); d.dismiss(); wallpaperMenu()
            }.show()
    }

    private fun shortcutsManager() {
        val map = prefs.shortcuts()
        val byPkg = allApps.associateBy { it.packageName }
        if (map.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Remote shortcuts")
                .setMessage("None yet. Hold OK on an app and choose \"Assign remote button\" to add one.")
                .setPositiveButton("OK", null).show()
            return
        }
        val entries = map.entries.toList()
        val labels = entries.map { "${keyName(it.key)}  ->  ${byPkg[it.value]?.label ?: it.value}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Remote shortcuts (select to remove)")
            .setItems(labels) { _, i ->
                val e = entries[i]
                prefs.removeShortcut(e.key)
                toast("Removed ${keyName(e.key)}")
            }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun onOff(b: Boolean) = if (b) "On" else "Off"

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    private fun safeStart(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure { toast("Not available") }
    }
}
