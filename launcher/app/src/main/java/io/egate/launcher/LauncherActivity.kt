package io.egate.launcher

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
import android.os.Handler
import android.os.Looper
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
    private lateinit var clock: TextView
    private lateinit var dateText: TextView
    private lateinit var favHeader: TextView
    private lateinit var favGrid: RecyclerView
    private lateinit var grid: RecyclerView
    private lateinit var gridAdapter: AppAdapter
    private lateinit var favAdapter: AppAdapter

    private var allApps: List<AppInfo> = emptyList()

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

        clock = findViewById(R.id.clock)
        dateText = findViewById(R.id.date)
        favHeader = findViewById(R.id.fav_header)
        favGrid = findViewById(R.id.fav_grid)
        grid = findViewById(R.id.grid)

        setupPill(findViewById(R.id.btn_options)) { showOptions() }
        setupPill(findViewById(R.id.btn_wifi)) { safeStart(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        setupPill(findViewById(R.id.btn_settings)) { safeStart(Intent(Settings.ACTION_SETTINGS)) }

        val accent = prefs.accentColor
        favAdapter = AppAdapter(emptyList(), accent, onClick = { launchApp(it) }, onLongClick = { appMenu(it) })
        gridAdapter = AppAdapter(emptyList(), accent, onClick = { launchApp(it) }, onLongClick = { appMenu(it) })

        favGrid.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        favGrid.adapter = favAdapter
        grid.layoutManager = GridLayoutManager(this, prefs.columns)
        grid.adapter = gridAdapter
        grid.setHasFixedSize(true)

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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        grid.scrollToPosition(0)
    }

    override fun onBackPressed() { /* home: swallow */ }

    // ---------- clock ----------
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
                    2 -> { prefs.clock24h = !prefs.clock24h; updateClock(); showOptions() }
                    3 -> { prefs.showSeconds = !prefs.showSeconds; updateClock(); showOptions() }
                    4 -> { prefs.bigClock = !prefs.bigClock; updateClock(); showOptions() }
                }
            }
            .show()
    }

    private fun pickAccent() {
        val names = Prefs.ACCENTS.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Accent color")
            .setSingleChoiceItems(names, prefs.accentIndex) { d, i ->
                prefs.accentIndex = i
                applyAccent()
                d.dismiss()
                showOptions()
            }
            .show()
    }

    private fun pickColumns() {
        val opts = (Prefs.MIN_COLS..Prefs.MAX_COLS).map { it.toString() }.toTypedArray()
        val current = prefs.columns - Prefs.MIN_COLS
        AlertDialog.Builder(this)
            .setTitle("Grid columns")
            .setSingleChoiceItems(opts, current) { d, i ->
                prefs.columns = Prefs.MIN_COLS + i
                (grid.layoutManager as GridLayoutManager).spanCount = prefs.columns
                gridAdapter.notifyDataSetChanged()
                d.dismiss()
                showOptions()
            }
            .show()
    }

    private fun applyAccent() {
        val a = prefs.accentColor
        gridAdapter.setAccent(a)
        favAdapter.setAccent(a)
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
