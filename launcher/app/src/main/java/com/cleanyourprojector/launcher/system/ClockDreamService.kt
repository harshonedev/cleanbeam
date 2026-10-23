package com.cleanyourprojector.launcher.system

import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.View
import android.widget.TextView
import com.cleanyourprojector.launcher.Prefs
import com.cleanyourprojector.launcher.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class ClockDreamService : DreamService() {

    private val handler = Handler(Looper.getMainLooper())
    private val dateFmt = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())

    private lateinit var prefs: Prefs
    private var clock: TextView? = null
    private var ampm: TextView? = null
    private var date: TextView? = null
    private var root: View? = null

    private var phase = 0.0

    private val tick = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1_000)
        }
    }

    // Anti burn-in: slowly move the whole clock around a small orbit.
    private val drift = object : Runnable {
        override fun run() {
            phase += 0.02
            root?.let {
                it.translationX = (sin(phase) * dp(40f)).toFloat()
                it.translationY = (cos(phase * 0.7) * dp(28f)).toFloat()
            }
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs = Prefs(this)
        isInteractive = false
        isFullscreen = true
        setScreenBright(false)
        setContentView(R.layout.dream_clock)
        root = findViewById(R.id.dream_root)
        clock = findViewById(R.id.dream_clock)
        ampm = findViewById(R.id.dream_ampm)
        date = findViewById(R.id.dream_date)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        handler.post(tick)
        handler.post(drift)
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        handler.removeCallbacks(tick)
        handler.removeCallbacks(drift)
    }

    private fun render() {
        val now = Date()
        val timePattern = buildString {
            append(if (prefs.clock24h) "H:mm" else "h:mm")
            if (prefs.showSeconds) append(":ss")
        }
        clock?.text = SimpleDateFormat(timePattern, Locale.getDefault()).format(now)
        clock?.textSize = if (prefs.bigClock) 150f else 110f
        if (prefs.clock24h) {
            ampm?.visibility = View.GONE
        } else {
            ampm?.visibility = View.VISIBLE
            ampm?.text = SimpleDateFormat("a", Locale.getDefault()).format(now)
        }
        date?.text = dateFmt.format(now)
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
