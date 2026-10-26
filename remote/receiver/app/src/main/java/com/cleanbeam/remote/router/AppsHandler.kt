package com.cleanbeam.remote.router

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.util.Log
import com.cleanbeam.remote.Net
import com.cleanbeam.remote.protocol.ShortcutItem
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.PowerManager
import com.cleanbeam.remote.protocol.AppItem
import java.io.ByteArrayOutputStream

/**
 * @param top asks the injector helper for the focused package (`{"t":"top"}`); null when it cannot say.
 */
/**
 * @param start runs `am start <args>` in the helper; returns true on success, false when the helper
 *   cannot (then the receiver tries startActivity itself, which only works while its own window is visible).
 */
class AppsHandler(
    private val ctx: Context,
    private val top: () -> String? = { null },
    private val start: (args: String) -> Boolean = { false },
) : AppsProvider {

    private val pm: PackageManager get() = ctx.packageManager

    override fun list(): List<AppItem> {
        val found = LinkedHashMap<String, AppItem>()
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, found)
        collect(Intent.CATEGORY_LAUNCHER, found)
        return found.values.sortedBy { it.label.lowercase() }
    }

    private fun collect(category: String, out: LinkedHashMap<String, AppItem>) {
        val skip = hidden()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        for (ri in pm.queryIntentActivities(intent, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg in skip || out.containsKey(pkg)) continue
            out[pkg] = AppItem(pkg, ri.loadLabel(pm)?.toString() ?: pkg)
        }
    }

    /** Our apps, stock TV launchers, and whatever this box registered as a home screen. */
    private fun hidden(): Set<String> {
        val homes = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }
        return STOCK_SHELLS + homes
    }

    override fun launch(pkg: String): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return startViaHelper(intent) || try { ctx.startActivity(intent); true } catch (e: Exception) { false }
    }

    /** `am start -n pkg/cls` or `-a action`, run by the helper (shell) so it works while any app is in front. */
    private fun startViaHelper(intent: Intent): Boolean {
        val comp = intent.component
        val args = when {
            comp != null -> "-n ${comp.packageName}/${comp.className}"
            intent.action != null -> "-a ${intent.action}"
            else -> return false
        }
        val flags = intent.flags and (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return start("$args -f 0x${Integer.toHexString(flags)}")
    }

    private var lastForeground: String? = null
    private var loggedEmpty = false

    /**
     * Most recent ACTIVITY_RESUMED in the last few minutes. The window is re-read every time rather
     * than scanned incrementally: UsageStatsService returns nothing at all when the query starts
     * after its newest event, which an incremental scan hits on every idle poll. Falls back to the
     * package with the newest lastTimeUsed when the event log is empty for this caller.
     */
    override fun foregroundApp(): String? {
        top()?.let { lastForeground = it; return it }
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - EVENT_WINDOW_MS, now + 1000)
        val ev = UsageEvents.Event()
        var newest: String? = null
        var newestTs = 0L
        var count = 0
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            count++
            if (ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED && ev.timeStamp >= newestTs) {
                newest = ev.packageName; newestTs = ev.timeStamp
            }
        }
        if (newest == null) {
            if (!loggedEmpty) { loggedEmpty = true; Log.w(TAG, "usage events: $count in window, no ACTIVITY_RESUMED; using usage stats") }
            newest = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - STATS_WINDOW_MS, now)
                ?.filter { it.lastTimeUsed > 0 && it.packageName != ctx.packageName }
                ?.maxByOrNull { it.lastTimeUsed }?.packageName
        }
        if (newest != null) lastForeground = newest
        return lastForeground
    }

    override fun shortcuts(): List<ShortcutItem> = Shortcuts.available(ctx).map { it.first.item() }

    override fun open(id: String): Boolean {
        val intent = Shortcuts.byId(id)?.resolve(ctx) ?: return false
        return startViaHelper(intent) || try { ctx.startActivity(intent); true } catch (e: Exception) { Log.w(TAG, "open $id failed", e); false }
    }

    override fun ip(): String? = Net.ipv4(ctx)

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
        private const val TAG = "CleanBeamRemote"
        private const val EVENT_WINDOW_MS = 5 * 60 * 1000L
        private const val STATS_WINDOW_MS = 6 * 60 * 60 * 1000L
        private val STOCK_SHELLS = setOf(
            "com.cleanbeam.remote",
            "com.cleanbeam.launcher",
            "com.jixin.launcher5",
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher",
            "com.android.tv.launcher",
        )
    }
}
