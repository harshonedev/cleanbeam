package io.egate.remote.router

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.PowerManager
import io.egate.remote.protocol.AppItem
import java.io.ByteArrayOutputStream

class AppsHandler(private val ctx: Context) : AppsProvider {

    private val pm: PackageManager get() = ctx.packageManager

    override fun list(): List<AppItem> {
        val found = LinkedHashMap<String, AppItem>()
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, found)
        collect(Intent.CATEGORY_LAUNCHER, found)
        return found.values.sortedBy { it.label.lowercase() }
    }

    private fun collect(category: String, out: LinkedHashMap<String, AppItem>) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        for (ri in pm.queryIntentActivities(intent, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg in HIDDEN || out.containsKey(pkg)) continue
            out[pkg] = AppItem(pkg, ri.loadLabel(pm)?.toString() ?: pkg)
        }
    }

    override fun launch(pkg: String): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try { ctx.startActivity(intent); true } catch (e: Exception) { false }
    }

    private var lastForeground: String? = null
    private var lastForegroundScan = 0L

    /** Scans only the usage events since the previous scan (first scan: last 10 minutes). */
    override fun foregroundApp(): String? {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val from = if (lastForegroundScan == 0L) now - 10 * 60 * 1000 else lastForegroundScan - 1000
        val events = usm.queryEvents(from, now)
        val ev = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED) lastForeground = ev.packageName
        }
        lastForegroundScan = now
        return lastForeground
    }

    override fun volume(): Pair<Int, Int> {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) to am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }

    override fun screenOn(): Boolean =
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive

    fun iconPng(pkg: String): ByteArray? {
        val drawable: Drawable = try { pm.getApplicationIcon(pkg) } catch (e: PackageManager.NameNotFoundException) { return null }
        val bmp = if (drawable is BitmapDrawable && drawable.bitmap != null) drawable.bitmap else {
            val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 128
            val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 128
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                val c = Canvas(it); drawable.setBounds(0, 0, w, h); drawable.draw(c)
            }
        }
        val scaled = if (bmp.width > 128) Bitmap.createScaledBitmap(bmp, 128, 128, true) else bmp
        return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    companion object {
        private val HIDDEN = setOf("io.egate.remote", "io.egate.launcher", "com.jixin.launcher5")
    }
}
