package io.egate.launcher

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
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
import android.view.animation.PathInterpolator
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class LauncherActivity : AppCompatActivity() {

    private enum class Tab { HOME, APPS, FAVORITES }

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
    private var tab = Tab.APPS
    private var heroItem: Any? = null          // AppInfo or Pair<Title, HomeRow>
    private var bgFront = false                // which bg layer is visible: false = A, true = B
    private var tintColor = 0
    private var tintAnim: ValueAnimator? = null
    private var tintDrawable = GradientDrawable()
    private var wallpaperKey = ""
    private var firstLoad = true
    private var homeFirstShow = true           // cold start lands on the first card even if a tab holds focus
    private var heroGen = 0                    // bumps per hero change so a cancelled fade can't leave it hidden

    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val ease = PathInterpolator(0.2f, 0f, 0f, 1f)
    private val heroRunnable = Runnable {
        when (val it = heroItem) {
            is AppInfo -> renderAppHero(it, animate = true)
            is Pair<*, *> -> renderTitleHero(it.first as Title, it.second as HomeRow)
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

        tint.background = tintDrawable
        root.background = Sky.drawable(prefs.ambientSky)

        setupTab(tabHome) { selectTab(Tab.HOME) }
        setupTab(tabApps) { selectTab(Tab.APPS) }
        setupTab(tabFavorites) { selectTab(Tab.FAVORITES) }
        setupTab(tabSettings, selectOnFocus = false) { showOptions() }

        adapter = AppAdapter(emptyList(),
            onClick = { launchApp(it) },
            onLongClick = { appMenu(it) },
            onFocus = { onCardFocused(it) })
        row.adapter = adapter
        row.itemAnimator = null

        shelvesAdapter = HomeRowsAdapter(
            onClick = { t, r -> openTitle(t, r) },
            onLongClick = { t, r -> titleMenu(t, r) },
            onFocus = { t, r -> onCardFocused(t to r) })
        shelvesAdapter.upTarget = tabHome
        shelves.adapter = shelvesAdapter
        shelves.itemAnimator = null

        tab = if (Tmdb.configured(this)) Tab.HOME else Tab.APPS
        row.upTarget = currentTabView()
        heroTitle.text = getString(if (tab == Tab.HOME) R.string.home_loading else R.string.loading)
        heroSubtitle.text = ""; heroMeta.visibility = View.GONE
        underline.post { moveUnderline(currentTabView(), animate = false); paintTabs() }

        reloadApps()
        if (prefs.keepAwake) KeepAliveService.start(this)
    }

    override fun onResume() {
        super.onResume()
        updateClock(); handler.post(tick)
        registerReceiver(timeReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        if (!firstLoad) reloadApps()
        ensureScreensaver()
    }

    /** Vendor apps (jixin settings) re-point the screensaver on boot; reassert ours. */
    private fun ensureScreensaver() {
        val want = "io.egate.launcher/.ClockDreamService"
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
        runCatching { unregisterReceiver(timeReceiver) }
        runCatching { unregisterReceiver(packageReceiver) }
    }

    override fun onDestroy() { super.onDestroy(); io.shutdownNow() }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (tab == Tab.HOME) { shelves.scrollToPosition(0); focusFirstShelfCard() }
        else { row.scrollToPosition(0); row.post { firstCard(row)?.requestFocus() } }
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
    private fun setupTab(v: TextView, selectOnFocus: Boolean = true, onSelect: () -> Unit) {
        v.setOnClickListener { onSelect() }
        v.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                moveUnderline(view, animate = true)
                if (selectOnFocus) onSelect() else paintTabs()
            } else {
                paintTabs()
                moveUnderline(currentTabView(), animate = true)
            }
        }
    }

    private fun currentTabView(): TextView = when (tab) {
        Tab.HOME -> tabHome; Tab.APPS -> tabApps; Tab.FAVORITES -> tabFavorites
    }

    private fun paintTabs() {
        for (t in listOf(tabHome, tabApps, tabFavorites, tabSettings)) {
            val active = t === currentTabView() || t.hasFocus()
            t.setTextColor(getColor(if (active) R.color.text_primary else R.color.text_secondary))
        }
    }

    private fun moveUnderline(target: View, animate: Boolean) {
        val x = target.left + (target.parent as View).left + target.paddingLeft
        val w = target.width - target.paddingLeft - target.paddingRight
        if (w <= 0) return
        if (!animate) {
            underline.translationX = (x - underline.left).toFloat()
            underline.layoutParams = underline.layoutParams.apply { width = w }
            return
        }
        underline.animate().translationX((x - underline.left).toFloat())
            .setDuration(260).setInterpolator(ease).start()
        val from = underline.width
        ValueAnimator.ofInt(from, w).apply {
            duration = 260; interpolator = ease
            addUpdateListener { underline.layoutParams = underline.layoutParams.apply { width = it.animatedValue as Int } }
        }.start()
    }

    private fun selectTab(t: Tab) {
        if (tab == t) { paintTabs(); return }
        tab = t
        row.upTarget = currentTabView()
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
        clock.text = SimpleDateFormat(pattern, Locale.getDefault()).format(now)
        wifi.visibility = if (wifiConnected()) View.VISIBLE else View.GONE

        if (prefs.showStats) { stats.visibility = View.VISIBLE; stats.text = statsLine() }
        else stats.visibility = View.GONE

        applyWallpaper()
    }

    private fun wifiConnected(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
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

    private fun animateTint(color: Int) {
        if (color == tintColor) return
        val from = if (tintColor == 0) Color.TRANSPARENT else tintColor
        tintColor = color
        tintAnim?.cancel()
        tintAnim = ValueAnimator.ofObject(ArgbEvaluator(), from, color).apply {
            duration = 650; interpolator = ease
            addUpdateListener { a ->
                val c = a.animatedValue as Int
                tintDrawable.orientation = GradientDrawable.Orientation.TL_BR
                tintDrawable.colors = intArrayOf(
                    Color.argb(0x7A, Color.red(c), Color.green(c), Color.blue(c)),
                    Color.argb(0x24, Color.red(c), Color.green(c), Color.blue(c)),
                    Color.TRANSPARENT
                )
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
                populate(animate = firstLoad)
                firstLoad = false
            }
        }
    }

    private fun visibleApps(): List<AppInfo> = when (tab) {
        Tab.FAVORITES -> {
            val byPkg = allApps.associateBy { it.packageName }
            prefs.favorites().mapNotNull { byPkg[it] }
        }
        else -> allApps
    }

    private fun populate(animate: Boolean) {
        if (tab == Tab.HOME) populateHome() else populateRow(animate)
    }

    private fun showEmpty(title: Int, hint: Int) {
        emptyTitle.text = getString(title); emptyHint.text = getString(hint)
        empty.visibility = View.VISIBLE
        heroItem = null
        hero.animate().alpha(0f).setDuration(200).start()
        crossfadeArt(null); animateTint(prefs.accentColor)
    }

    private fun populateRow(animate: Boolean) {
        val list = visibleApps()
        shelves.visibility = View.GONE
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
        val cur = heroItem as? AppInfo
        if (cur == null || !list.contains(cur) || focusInRow) {
            if (focused == null || focusInRow || cur == null) row.post { firstCard(row)?.requestFocus() }
            if (cur == null || !list.contains(cur)) onCardFocused(list[0])
        }
    }

    // ---------- home (TMDB) ----------
    private fun populateHome() {
        row.visibility = View.GONE
        if (!Tmdb.configured(this)) {
            shelves.visibility = View.GONE
            shelvesAdapter.submit(emptyList())
            showEmpty(R.string.home_no_token, R.string.home_no_token_hint)
            return
        }
        empty.visibility = View.GONE
        shelves.visibility = View.VISIBLE
        val stale = System.currentTimeMillis() - homeLoadedAt > Tmdb.ROWS_TTL
        if (homeRows.isNotEmpty()) showShelves(homeRows)
        else { heroTitle.text = getString(R.string.home_loading); heroSubtitle.text = ""; heroMeta.visibility = View.GONE; heroBody.visibility = View.GONE; hero.alpha = 1f }
        if ((stale || homeRows.isEmpty()) && !homeLoading && allApps.isNotEmpty()) loadHome()
    }

    private fun loadHome() {
        homeLoading = true
        val apps = allApps
        val reg = region
        io.execute {
            val rows = runCatching { Tmdb.rows(this, apps, reg) }
                .onFailure { Log.e("EGate", "tmdb rows failed", it) }.getOrDefault(emptyList())
            handler.post {
                homeLoading = false
                if (rows.isNotEmpty()) { homeRows = rows; homeLoadedAt = System.currentTimeMillis() }
                if (tab != Tab.HOME) return@post
                if (homeRows.isEmpty()) { shelves.visibility = View.GONE; showEmpty(R.string.home_empty, R.string.home_empty_hint) }
                else if (rows.isNotEmpty()) showShelves(rows)
            }
        }
    }

    private fun showShelves(rows: List<HomeRow>) {
        shelves.visibility = View.VISIBLE
        empty.visibility = View.GONE
        shelvesAdapter.submit(rows)
        shelves.scrollToPosition(0)
        val focused = currentFocus
        val focusInTabs = focused != null && focused.parent === tabHome.parent
        val cur = heroItem as? Pair<*, *>
        if (cur == null || !rows.contains(cur.second)) {
            onCardFocused(rows[0].titles[0] to rows[0])
            // Cold start: land on the first card. Later refreshes leave a user who is on the tabs alone.
            if (!focusInTabs || homeFirstShow) focusFirstShelfCard()
        }
        homeFirstShow = false
    }

    private fun firstCard(rv: View?): View? =
        (rv as? FocusRowRecyclerView)?.getChildAt(0)?.findViewById(R.id.card)

    private fun focusFirstShelfCard() {
        shelves.post {
            val inner = shelves.getChildAt(0)?.findViewById<FocusRowRecyclerView>(R.id.row_titles)
            val card = firstCard(inner)
            if (card != null) card.requestFocus()
            else inner?.post { firstCard(inner)?.requestFocus() }
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
        hero.animate().alpha(0f).translationY(6f).setDuration(110).withEndAction {
            if (gen != heroGen) return@withEndAction
            apply()
            hero.translationY = 14f
            hero.animate().alpha(1f).translationY(0f).setDuration(320).setInterpolator(ease).start()
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
            AppRepository.hasUsageAccess(this) -> "Not opened this month"
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

    /** OK on a title: open the shelf's app, or look up which installed app carries it. */
    private fun openTitle(t: Title, r: HomeRow) {
        if (r.providerPkg != null) { launchPackage(r.providerPkg, r.providerName ?: r.header); return }
        val apps = allApps; val reg = region
        io.execute {
            val names = Tmdb.providersFor(this, t, reg)
            val installed = Tmdb.matchProviders(apps, Tmdb.providers(this, reg))
            val hit = installed.firstOrNull { (_, p) -> p.name in names }
            handler.post {
                when {
                    hit != null -> launchPackage(hit.first.packageName, hit.first.label)
                    names.isEmpty() -> toast("${t.name} isn't streaming in $reg right now")
                    else -> toast("${t.name} is on ${names.take(3).joinToString(", ")}, not installed here")
                }
            }
        }
    }

    private fun titleMenu(t: Title, r: HomeRow) {
        val apps = allApps; val reg = region
        io.execute {
            val names = Tmdb.providersFor(this, t, reg)
            val installed = Tmdb.matchProviders(apps, Tmdb.providers(this, reg)).associateBy { it.second.name }
            handler.post {
                if (names.isEmpty()) { toast("${t.name} isn't streaming in $reg right now"); return@post }
                val labels = names.map { n -> if (installed.containsKey(n)) "Open in $n" else "$n (not installed)" }
                AlertDialog.Builder(this).setTitle(t.name)
                    .setItems(labels.toTypedArray()) { _, i ->
                        installed[names[i]]?.let { launchPackage(it.first.packageName, it.first.label) }
                    }.show()
            }
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
        actions += "App info" to {
            safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${app.packageName}")))
        }
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(actions.map { it.first }.toTypedArray()) { _, i -> actions[i].second() }
            .show()
    }

    private fun refreshIfFavorites() { if (tab == Tab.FAVORITES) populateRow(animate = false) }

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
    private fun showOptions() {
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
            "Keep Wi-Fi awake in standby: ${onOff(prefs.keepAwake)}",
            "Wi-Fi settings",
            "System settings"
        )
        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> enterToken()
                    1 -> pickRegion()
                    2 -> { Tmdb.clearCache(this); homeRows = emptyList(); homeLoadedAt = 0; if (tab == Tab.HOME) populateHome() else selectTab(Tab.HOME) }
                    3 -> pickAccent()
                    4 -> { prefs.appGlow = !prefs.appGlow; retint(); showOptions() }
                    5 -> wallpaperMenu()
                    6 -> { prefs.ambientSky = !prefs.ambientSky; root.background = Sky.drawable(prefs.ambientSky); showOptions() }
                    7 -> { prefs.showStats = !prefs.showStats; updateClock(); showOptions() }
                    8 -> { prefs.clock24h = !prefs.clock24h; updateClock(); showOptions() }
                    9 -> { prefs.showSeconds = !prefs.showSeconds; updateClock(); showOptions() }
                    10 -> shortcutsManager()
                    11 -> {
                        prefs.keepAwake = !prefs.keepAwake
                        if (prefs.keepAwake) KeepAliveService.start(this) else KeepAliveService.stop(this)
                        showOptions()
                    }
                    12 -> safeStart(Intent(Settings.ACTION_WIFI_SETTINGS))
                    13 -> safeStart(Intent(Settings.ACTION_SETTINGS))
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
                Tmdb.clearCache(this); homeRows = emptyList(); homeLoadedAt = 0
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
                Tmdb.clearCache(this); homeRows = emptyList(); homeLoadedAt = 0
                d.dismiss()
                if (tab == Tab.HOME) populateHome() else showOptions()
            }.show()
    }

    private fun retint() { tintColor = 0; handler.post(heroRunnable) }

    private fun pickAccent() {
        val names = Prefs.ACCENTS.map { it.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Accent color")
            .setSingleChoiceItems(names, prefs.accentIndex) { d, i ->
                prefs.accentIndex = i; retint(); d.dismiss(); showOptions()
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
