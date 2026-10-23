package com.cleanyourprojector.launcher.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.palette.graphics.Palette
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object AppRepository {

    private val HIDDEN = setOf("com.cleanyourprojector.launcher", "io.egate.launcher", "com.jixin.launcher5")
    private const val FALLBACK = 0xFF5B8CFF.toInt()
    /** Banners wider than this are shrunk: a card is ~265px wide, 1.12x when focused. */
    private const val MAX_BANNER_PX = 600

    /** Icon decode, banner decode and Palette per app, spread over the cores instead of one thread. */
    private val workers = (Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors().coerceIn(2, 4)) as ThreadPoolExecutor)
        .apply { setKeepAliveTime(20, TimeUnit.SECONDS); allowCoreThreadTimeOut(true) }   // idle threads go away between loads

    fun loadApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val jobs = resolveAll(pm).map { ri -> Callable { build(context, pm, ri) } }
        return workers.invokeAll(jobs).map { it.get() }.sortedBy { it.label.lowercase() }
    }

    /** Package names the launcher would show right now; cheap, no icons or banners decoded. */
    fun installedPackages(context: Context): Set<String> =
        resolveAll(context.packageManager).mapTo(HashSet()) { it.activityInfo.packageName }

    /** Leanback launcher activities first, then plain launcher ones; one entry per package. */
    private fun resolveAll(pm: PackageManager): List<ResolveInfo> {
        val found = LinkedHashMap<String, ResolveInfo>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg in HIDDEN || found.containsKey(pkg)) continue
                found[pkg] = ri
            }
        }
        return found.values.toList()
    }

    private fun build(context: Context, pm: PackageManager, ri: ResolveInfo): AppInfo {
        val pkg = ri.activityInfo.packageName
        val ai = ri.activityInfo.applicationInfo
        val label = ri.loadLabel(pm)?.toString() ?: pkg
        val icon = ri.activityInfo.loadIcon(pm)
        val banner = runCatching { ri.activityInfo.loadBanner(pm) ?: ai.loadBanner(pm) }.getOrNull()
            ?.let { shrinkBanner(context, it) }
            ?.also { (it as? BitmapDrawable)?.bitmap?.prepareToDraw() }   // upload to the GPU before the card is drawn
        val cat = ApplicationInfo.getCategoryTitle(context, ai.category)?.toString()
        return AppInfo(
            label = label,
            packageName = pkg,
            icon = icon,
            banner = banner,
            ambient = ambientArt(banner ?: icon),
            color = dominantColor(banner ?: icon),
            category = cat
        )
    }

    /**
     * Apps ship banners at anything up to 1920x1080; on a card they draw at a quarter of that.
     * Keep at least 2x the card width so the downscale stays invisible, and drop the rest of
     * the pixels before they reach the heap and the GPU.
     */
    private fun shrinkBanner(context: Context, d: Drawable): Drawable {
        val src = (d as? BitmapDrawable)?.bitmap ?: return d
        if (src.width <= MAX_BANNER_PX) return d
        val h = (src.height.toLong() * MAX_BANNER_PX / src.width).toInt().coerceAtLeast(1)
        return try {
            val small = Bitmap.createScaledBitmap(src, MAX_BANNER_PX, h, true)
            if (!src.hasAlpha() && small.config != Bitmap.Config.RGB_565) {
                val packed = small.copy(Bitmap.Config.RGB_565, false)
                if (packed != null) { small.recycle(); return BitmapDrawable(context.resources, packed) }
            }
            BitmapDrawable(context.resources, small)
        } catch (e: Exception) { d }
    }

    private fun dominantColor(d: Drawable): Int {
        val bmp = toBitmap(d) ?: return FALLBACK
        return try { colorOf(bmp) } finally { if (bmp !== (d as? BitmapDrawable)?.bitmap) bmp.recycle() }
    }

    /** Lively tint for a bitmap: vibrant swatch, lifted if too dark, accent if too gray. */
    fun colorOf(bmp: Bitmap): Int {
        return try {
            val p = Palette.from(bmp).clearFilters().generate()
            val c = p.getVibrantColor(0)
                .takeIf { it != 0 } ?: p.getLightVibrantColor(0)
                .takeIf { it != 0 } ?: p.getDominantColor(FALLBACK)
            boost(c)
        } catch (e: Exception) { FALLBACK }
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
