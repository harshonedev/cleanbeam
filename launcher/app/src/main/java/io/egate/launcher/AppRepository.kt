package io.egate.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.palette.graphics.Palette

object AppRepository {

    private val HIDDEN = setOf("io.egate.launcher", "com.jixin.launcher5")
    private const val FALLBACK = 0xFF5B8CFF.toInt()

    fun loadApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, AppInfo>()
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, found)
        collect(pm, Intent.CATEGORY_LAUNCHER, found)
        return found.values.sortedBy { it.label.lowercase() }
    }

    private fun collect(pm: PackageManager, category: String, out: LinkedHashMap<String, AppInfo>) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        for (ri in pm.queryIntentActivities(intent, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg in HIDDEN || out.containsKey(pkg)) continue
            val label = ri.loadLabel(pm)?.toString() ?: pkg
            val icon = ri.activityInfo.loadIcon(pm)
            out[pkg] = AppInfo(label, pkg, icon, dominantColor(icon))
        }
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

    // Keep glow colors lively: lift very dark/gray swatches toward a usable tone.
    private fun boost(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        if (hsv[2] < 0.45f) hsv[2] = 0.65f      // value
        if (hsv[1] < 0.25f) return FALLBACK      // too gray -> accent fallback
        return Color.HSVToColor(hsv)
    }

    private fun toBitmap(d: Drawable): Bitmap? {
        if (d is BitmapDrawable && d.bitmap != null) return d.bitmap
        val w = d.intrinsicWidth.takeIf { it > 0 } ?: 48
        val h = d.intrinsicHeight.takeIf { it > 0 } ?: 48
        return try {
            val b = Bitmap.createBitmap(w.coerceAtMost(96), h.coerceAtMost(96), Bitmap.Config.ARGB_8888)
            val c = Canvas(b); d.setBounds(0, 0, c.width, c.height); d.draw(c); b
        } catch (e: Exception) { null }
    }

    fun launchIntentFor(context: Context, pkg: String): Intent? {
        val pm = context.packageManager
        return pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
    }
}
