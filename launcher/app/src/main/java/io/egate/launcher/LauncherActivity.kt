package io.egate.launcher

import android.app.ActivityManager
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class LauncherActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var root: View
    private lateinit var greeting: TextView
    private lateinit var clock: TextView
    private lateinit var dateText: TextView
    private lateinit var stats: TextView
    private lateinit var favHeader: TextView
    private lateinit var favGrid: RecyclerView
    private lateinit var grid: RecyclerView
    private lateinit var gridAdapter: AppAdapter
    private lateinit var favAdapter: AppAdapter

    private var allApps: List<AppInfo> = emptyList()
    private var bgKey: String = ""

    private val handler = Handler(Looper.getMainLooper())
    private val dateFmt = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())

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

        root = findViewById(R.id.root)
        greeting = findViewById(R.id.greeting)
        clock = findViewById(R.id.clock)
        dateText = findViewById(R.id.date)
        stats = findViewById(R.id.stats)
        favHeader = findViewById(R.id.fav_header)
        favGrid = findViewById(R.id.fav_grid)
        grid = findViewById(R.id.grid)

        setupPill(findViewById(R.id.btn_options)) { showOptions() }
        setupPill(findViewById(R.id.btn_wifi)) { safeStart(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        setupPill(findViewById(R.id.btn_settings)) { safeStart(Intent(Settings.ACTION_SETTINGS)) }

        favAdapter = AppAdapter(emptyList(), prefs.accentColor, prefs.appGlow, prefs.namesFocusedOnly,
            onClick = { launchApp(it) }, onLongClick = { appMenu(it) })
        gridAdapter = AppAdapter(emptyList(), prefs.accentColor, prefs.appGlow, prefs.namesFocusedOnly,
            onClick = { launchApp(it) }, onLongClick = { appMenu(it) })

        favGrid.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        favGrid.adapter = favAdapter
        grid.layoutManager = GridLayoutManager(this, prefs.columns)
        grid.adapter = gridAdapter
        grid.setHasFixedSize(true)

        applyBackground(force = true)
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
        reloadApps()
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

    override fun onNewIntent(intent: Intent?) { super.onNewIntent(intent); grid.scrollToPosition(0) }
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

    // ---------- clock / greeting / stats / background ----------
    private fun updateClock() {
        val now = Date()
        val pattern = buildString {
            append(if (prefs.clock24h) "H:mm" else "h:mm")
            if (prefs.showSeconds) append(":ss")
            if (!prefs.clock24h) append(" a")
        }
        clock.text = SimpleDateFormat(pattern, Locale.getDefault()).format(now)
        clock.textSize = if (prefs.bigClock) 56f else 40f
        dateText.text = dateFmt.format(now)

        greeting.visibility = if (prefs.greeting) View.VISIBLE else View.GONE
        if (prefs.greeting) greeting.text = Sky.greeting()

        if (prefs.showStats) { stats.visibility = View.VISIBLE; stats.text = statsLine() }
        else stats.visibility = View.GONE

        applyBackground(force = false)
    }

    private fun applyBackground(force: Boolean) {
        val imgs = if (prefs.wallpaper) Wallpaper.images(this) else emptyList()
        val key: String
        val bg: Drawable
        if (imgs.isNotEmpty()) {
            val idx = ((System.currentTimeMillis() / (30 * 60 * 1000)) % imgs.size).toInt()
            key = "wp:${imgs[idx].path}:${prefs.wallpaperDim}"
            if (!force && key == bgKey) return
            val bmp = Wallpaper.load(imgs[idx].path, 1280, 720)
            bg = if (bmp != null) {
                val pic = BitmapDrawable(resources, bmp).apply { gravity = Gravity.FILL }
                val dim = ColorDrawable(Color.argb((prefs.wallpaperDim * 255 / 100), 0, 0, 0))
                LayerDrawable(arrayOf(pic, dim))
            } else Sky.drawable(prefs.ambientSky)
        } else {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            key = "sky:$hour:${prefs.ambientSky}"
            if (!force && key == bgKey) return
            bg = Sky.drawable(prefs.ambientSky)
        }
        bgKey = key
        root.background = bg
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
        allApps = AppRepository.loadApps(this)
        gridAdapter.submit(allApps)
        reloadFavorites()
    }

    private fun reloadFavorites() {
        val byPkg = allApps.associateBy { it.packageName }
        val favs = prefs.favorites().mapNotNull { byPkg[it] }
        val show = favs.isNotEmpty()
        favHeader.visibility = if (show) View.VISIBLE else View.GONE
        favGrid.visibility = if (show) View.VISIBLE else View.GONE
        favAdapter.submit(favs)
    }

    private fun launchApp(app: AppInfo) {
        val intent = AppRepository.launchIntentFor(this, app.packageName)
        if (intent == null) { toast("Can't open ${app.label}"); return }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        safeStart(intent)
    }

    // ---------- long-press menu ----------
    private fun appMenu(app: AppInfo) {
        val fav = prefs.isFavorite(app.packageName)
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (fav) {
            actions += "Unpin from Favorites" to { prefs.toggleFavorite(app.packageName); reloadFavorites() }
            actions += "Move left" to { prefs.moveFavorite(app.packageName, -1); reloadFavorites() }
            actions += "Move right" to { prefs.moveFavorite(app.packageName, +1); reloadFavorites() }
        } else {
            actions += "Pin to Favorites" to { prefs.toggleFavorite(app.packageName); reloadFavorites() }
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
        val labels = arrayOf(
            "Accent color: ${Prefs.ACCENTS[prefs.accentIndex].name}",
            "Grid columns: ${prefs.columns}",
            "Per-app color glow: ${onOff(prefs.appGlow)}",
            "Wallpaper: ${onOff(prefs.wallpaper)}",
            "Ambient sky background: ${onOff(prefs.ambientSky)}",
            "Greeting: ${onOff(prefs.greeting)}",
            "System stats line: ${onOff(prefs.showStats)}",
            "App names: ${if (prefs.namesFocusedOnly) "Focused only" else "Always"}",
            "24-hour clock: ${onOff(prefs.clock24h)}",
            "Show seconds: ${onOff(prefs.showSeconds)}",
            "Large clock: ${onOff(prefs.bigClock)}",
            "Remote shortcuts…",
            "Keep Wi-Fi awake in standby: ${onOff(prefs.keepAwake)}"
        )
        AlertDialog.Builder(this)
            .setTitle("Options")
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> pickAccent()
                    1 -> pickColumns()
                    2 -> { prefs.appGlow = !prefs.appGlow; applyStyle(); showOptions() }
                    3 -> wallpaperMenu()
                    4 -> { prefs.ambientSky = !prefs.ambientSky; applyBackground(true); showOptions() }
                    5 -> { prefs.greeting = !prefs.greeting; updateClock(); showOptions() }
                    6 -> { prefs.showStats = !prefs.showStats; updateClock(); showOptions() }
                    7 -> { prefs.namesFocusedOnly = !prefs.namesFocusedOnly; applyStyle(); showOptions() }
                    8 -> { prefs.clock24h = !prefs.clock24h; updateClock(); showOptions() }
                    9 -> { prefs.showSeconds = !prefs.showSeconds; updateClock(); showOptions() }
                    10 -> { prefs.bigClock = !prefs.bigClock; updateClock(); showOptions() }
                    11 -> shortcutsManager()
                    12 -> {
                        prefs.keepAwake = !prefs.keepAwake
                        if (prefs.keepAwake) KeepAliveService.start(this) else KeepAliveService.stop(this)
                        showOptions()
                    }
                }
            }
            .show()
    }

    private fun pickAccent() {
        val names = Prefs.ACCENTS.map { it.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Accent color")
            .setSingleChoiceItems(names, prefs.accentIndex) { d, i ->
                prefs.accentIndex = i; applyStyle(); d.dismiss(); showOptions()
            }.show()
    }

    private fun pickColumns() {
        val opts = (Prefs.MIN_COLS..Prefs.MAX_COLS).map { it.toString() }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Grid columns")
            .setSingleChoiceItems(opts, prefs.columns - Prefs.MIN_COLS) { d, i ->
                prefs.columns = Prefs.MIN_COLS + i
                (grid.layoutManager as GridLayoutManager).spanCount = prefs.columns
                gridAdapter.notifyDataSetChanged(); d.dismiss(); showOptions()
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
                        if (prefs.wallpaper && imgs.isEmpty())
                            toast("Add images to: $dir")
                        applyBackground(true); wallpaperMenu()
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
                prefs.wallpaperDim = opts[i]; applyBackground(true); d.dismiss(); wallpaperMenu()
            }.show()
    }

    private fun shortcutsManager() {
        val map = prefs.shortcuts()
        val byPkg = allApps.associateBy { it.packageName }
        if (map.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Remote shortcuts")
                .setMessage("None yet. Long-press an app and choose \"Assign remote button\" to add one.")
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

    private fun applyStyle() {
        gridAdapter.setStyle(prefs.accentColor, prefs.appGlow, prefs.namesFocusedOnly)
        favAdapter.setStyle(prefs.accentColor, prefs.appGlow, prefs.namesFocusedOnly)
        setupPillBg(findViewById(R.id.btn_options))
        setupPillBg(findViewById(R.id.btn_wifi))
        setupPillBg(findViewById(R.id.btn_settings))
    }

    private fun onOff(b: Boolean) = if (b) "On" else "Off"

    // ---------- pills ----------
    private fun setupPill(v: TextView, onClick: () -> Unit) {
        setupPillBg(v)
        v.setOnClickListener { onClick() }
        v.setOnFocusChangeListener { view, hasFocus ->
            val s = if (hasFocus) 1.08f else 1.0f
            view.animate().scaleX(s).scaleY(s).setDuration(120).start()
        }
    }

    private fun setupPillBg(v: View) {
        val accent = prefs.accentColor
        val r = dp(24f)
        val normal = GradientDrawable().apply { cornerRadius = r; setColor(Color.parseColor("#1E2438")) }
        val focused = GradientDrawable().apply {
            cornerRadius = r
            setColor(Color.argb(0x40, Color.red(accent), Color.green(accent), Color.blue(accent)))
            setStroke(dp(2.5f).toInt(), accent)
        }
        v.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    private fun safeStart(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure { toast("Not available") }
    }
}
