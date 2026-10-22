package io.egate.launcher

import android.app.ActivityManager
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
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
    private var lastHour = -1

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

        applySky(force = true)
        reloadApps()
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
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        runCatching { unregisterReceiver(timeReceiver) }
        runCatching { unregisterReceiver(packageReceiver) }
    }

    override fun onNewIntent(intent: Intent?) { super.onNewIntent(intent); grid.scrollToPosition(0) }
    override fun onBackPressed() { /* home: swallow */ }

    // ---------- clock / greeting / stats / sky ----------
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

        applySky(force = false)
    }

    private fun applySky(force: Boolean) {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (!force && hour == lastHour) return
        lastHour = hour
        root.background = Sky.drawable(prefs.ambientSky)
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
        actions += "App info" to {
            safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${app.packageName}")))
        }
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(actions.map { it.first }.toTypedArray()) { _, i -> actions[i].second() }
            .show()
    }

    // ---------- options ----------
    private fun showOptions() {
        val labels = arrayOf(
            "Accent color: ${Prefs.ACCENTS[prefs.accentIndex].name}",
            "Grid columns: ${prefs.columns}",
            "Per-app color glow: ${onOff(prefs.appGlow)}",
            "Ambient sky background: ${onOff(prefs.ambientSky)}",
            "Greeting: ${onOff(prefs.greeting)}",
            "System stats line: ${onOff(prefs.showStats)}",
            "App names: ${if (prefs.namesFocusedOnly) "Focused only" else "Always"}",
            "24-hour clock: ${onOff(prefs.clock24h)}",
            "Show seconds: ${onOff(prefs.showSeconds)}",
            "Large clock: ${onOff(prefs.bigClock)}"
        )
        AlertDialog.Builder(this)
            .setTitle("Options")
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> pickAccent()
                    1 -> pickColumns()
                    2 -> { prefs.appGlow = !prefs.appGlow; applyStyle(); showOptions() }
                    3 -> { prefs.ambientSky = !prefs.ambientSky; applySky(true); showOptions() }
                    4 -> { prefs.greeting = !prefs.greeting; updateClock(); showOptions() }
                    5 -> { prefs.showStats = !prefs.showStats; updateClock(); showOptions() }
                    6 -> { prefs.namesFocusedOnly = !prefs.namesFocusedOnly; applyStyle(); showOptions() }
                    7 -> { prefs.clock24h = !prefs.clock24h; updateClock(); showOptions() }
                    8 -> { prefs.showSeconds = !prefs.showSeconds; updateClock(); showOptions() }
                    9 -> { prefs.bigClock = !prefs.bigClock; updateClock(); showOptions() }
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
