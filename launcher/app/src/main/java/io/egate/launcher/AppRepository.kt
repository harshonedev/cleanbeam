package io.egate.launcher

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import androidx.palette.graphics.Palette

object AppRepository {

    private val HIDDEN = setOf("io.egate.launcher", "com.jixin.launcher5")
    private const val FALLBACK = 0xFF5B8CFF.toInt()
    private const val MONTH_MS = 30L * 24 * 60 * 60 * 1000

    private class Usage(val lastUsed: Long, val foregroundMs: Long)

    fun loadApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val usage = usage(context)
        val found = LinkedHashMap<String, AppInfo>()
        collect(context, pm, Intent.CATEGORY_LEANBACK_LAUNCHER, usage, found)
        collect(context, pm, Intent.CATEGORY_LAUNCHER, usage, found)
        return found.values.sortedBy { it.label.lowercase() }
    }

    private fun collect(
        context: Context, pm: PackageManager, category: String,
        usage: Map<String, Usage>, out: LinkedHashMap<String, AppInfo>
    ) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        for (ri in pm.queryIntentActivities(intent, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg in HIDDEN || out.containsKey(pkg)) continue
            val ai = ri.activityInfo.applicationInfo
            val label = ri.loadLabel(pm)?.toString() ?: pkg
            val icon = ri.activityInfo.loadIcon(pm)
            val banner = runCatching { ri.activityInfo.loadBanner(pm) ?: ai.loadBanner(pm) }.getOrNull()
            val pi = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull()
            val cat = ApplicationInfo.getCategoryTitle(context, ai.category)?.toString()
            val u = usage[pkg]
            out[pkg] = AppInfo(
                label = label,
                packageName = pkg,
                icon = icon,
                banner = banner,
                ambient = ambientArt(banner ?: icon),
                color = dominantColor(banner ?: icon),
                category = cat,
                version = pi?.versionName,
                installTime = pi?.firstInstallTime ?: 0L,
                lastUsed = u?.lastUsed ?: 0L,
                foregroundMs = u?.foregroundMs ?: 0L
            )
        }
    }

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun usage(context: Context): Map<String, Usage> {
        if (!hasUsageAccess(context)) return emptyMap()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - MONTH_MS, now)
        }.getOrNull() ?: return emptyMap()
        val last = HashMap<String, Long>()
        val total = HashMap<String, Long>()
        for (s in stats) {
            last[s.packageName] = maxOf(last[s.packageName] ?: 0L, s.lastTimeUsed)
            total[s.packageName] = (total[s.packageName] ?: 0L) + s.totalTimeInForeground
        }
        return last.mapValues { (pkg, l) -> Usage(l, total[pkg] ?: 0L) }
    }

    private fun dominantColor(d: Drawable): Int {
        val bmp = toBitmap(d) ?: return FALLBACK
        return try {
            val p = Palette.from(bmp).clearFilters().generate()
            val c = p.getVibrantColor(0)
                .takeIf { it != 0 } ?: p.getLightVibrantColor(0)
                .takeIf { it != 0 } ?: p.getDominantColor(FALLBACK)
            boost(c)
        } catch (e: Exception) { FALLBACK }
        finally { if (bmp !== (d as? BitmapDrawable)?.bitmap) bmp.recycle() }
    }

    // Keep tint colors lively: lift very dark/gray swatches toward a usable tone.
    private fun boost(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        if (hsv[2] < 0.45f) hsv[2] = 0.65f      // value
        if (hsv[1] < 0.25f) return FALLBACK      // too gray -> accent fallback
        return Color.HSVToColor(hsv)
    }

    fun toBitmap(d: Drawable, max: Int = 96): Bitmap? {
        if (d is BitmapDrawable && d.bitmap != null) return d.bitmap
        val w = d.intrinsicWidth.takeIf { it > 0 } ?: 48
        val h = d.intrinsicHeight.takeIf { it > 0 } ?: 48
        return try {
            val b = Bitmap.createBitmap(w.coerceAtMost(max), h.coerceAtMost(max), Bitmap.Config.ARGB_8888)
            val c = Canvas(b); d.setBounds(0, 0, c.width, c.height); d.draw(c); b
        } catch (e: Exception) { null }
    }

    /**
     * Cheap "blur": render the art at a tiny size, upscale it twice with bilinear filtering
     * and let the ImageView stretch the result. Runs off the main thread before the drawable
     * is attached to any view, so no view callbacks fire. Costs nothing per frame.
     */
    private fun ambientArt(d: Drawable): Bitmap? {
        val src = toBitmap(d, 256) ?: return null
        return try {
            val tiny = Bitmap.createScaledBitmap(src, 14, 8, true)
            Bitmap.createScaledBitmap(tiny, 112, 64, true).also { if (it !== tiny) tiny.recycle() }
        } catch (e: Exception) { null }
        finally { if (src !== (d as? BitmapDrawable)?.bitmap) src.recycle() }
    }

    fun launchIntentFor(context: Context, pkg: String): Intent? {
        val pm = context.packageManager
        return pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
    }
}
