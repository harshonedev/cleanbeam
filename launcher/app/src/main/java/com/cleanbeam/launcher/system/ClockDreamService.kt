package com.cleanbeam.launcher.system

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.cleanbeam.launcher.Prefs
import com.cleanbeam.launcher.R
import com.cleanbeam.launcher.graphics.Backdrop
import com.cleanbeam.launcher.graphics.Wallpaper
import java.util.concurrent.Executors
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
    private var bg: ImageView? = null
    private var dim: View? = null

    private val io = Executors.newSingleThreadExecutor()
    private val backdrop = Backdrop()
    private var pictureSlot = -1L              // period the folder was last listed for

    private var phase = 0.0

    private val tick = object : Runnable {
        override fun run() {
            render()
            showPicture()
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
        bg = findViewById(R.id.dream_bg)
        dim = findViewById(R.id.dream_dim)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        io.shutdownNow()
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

    /** The user's pictures (files/wallpapers) in turn behind the clock; the folder is listed once per period. */
    private fun showPicture() {
        val now = System.currentTimeMillis()
        val slot = now / PICTURE_MS
        if (slot == pictureSlot) return
        pictureSlot = slot
        val path = Wallpaper.pick(Wallpaper.images(this).map { it.path }, now, PICTURE_MS)
        val dm = resources.displayMetrics
        when (val c = backdrop.change(path)) {
            Backdrop.Change.None -> {}
            Backdrop.Change.Clear -> fadeTo(null)
            is Backdrop.Change.Load -> io.execute {
                val bmp = Wallpaper.load(c.key, dm.widthPixels, dm.heightPixels)
                handler.post { if (backdrop.isCurrent(c.key)) fadeTo(bmp) }
            }
        }
    }

    private fun fadeTo(bmp: Bitmap?) {
        val bg = bg ?: return
        val dim = dim ?: return
        bg.animate().cancel()
        if (bmp == null) {
            bg.animate().alpha(0f).setDuration(600).withEndAction { bg.setImageDrawable(null) }.start()
            dim.animate().alpha(0f).setDuration(600).start()
            return
        }
        val swap = Runnable {
            bg.setImageBitmap(bmp)
            bg.animate().alpha(1f).setDuration(900).start()
            dim.animate().alpha(prefs.wallpaperDim / 100f).setDuration(900).start()
        }
        if (bg.drawable == null) swap.run() else bg.animate().alpha(0f).setDuration(600).withEndAction(swap).start()
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    companion object {
        /** How long each of the user's pictures stays up. */
        const val PICTURE_MS = 10 * 60 * 1000L
    }
}
