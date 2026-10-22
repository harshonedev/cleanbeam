package io.egate.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Tiny URL -> Bitmap loader: memory LRU, disk cache in cacheDir/img, three download threads. */
object ImageLoader {

    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val mem = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    /** Set the image on [target] once it arrives; a recycled view that moved on is left alone. */
    fun load(context: Context, url: String?, target: ImageView, maxPx: Int = 800) {
        target.tag = url
        if (url == null) { target.setImageDrawable(null); return }
        mem.get(url)?.let { target.setImageBitmap(it); return }
        target.setImageDrawable(null)
        fetch(context, url, maxPx, { it }) { bmp ->
            if (bmp != null && target.tag == url) {
                target.alpha = 0f
                target.setImageBitmap(bmp)
                target.animate().alpha(1f).setDuration(220).start()
            }
        }
    }

    /**
     * Fetch, run [work] on the pool thread with the bitmap, then deliver its result on main.
     * Lets callers do Palette or scaling off the UI thread.
     */
    fun <T> fetch(context: Context, url: String, maxPx: Int, work: (Bitmap) -> T, done: (T?) -> Unit) {
        val cached = mem.get(url)
        pool.execute {
            val bmp = cached ?: loadBitmap(context, url, maxPx)?.also { mem.put(url, it) }
            val result = bmp?.let { runCatching { work(it) }.getOrNull() }
            main.post { done(result) }
        }
    }

    private fun loadBitmap(context: Context, url: String, maxPx: Int): Bitmap? {
        val f = diskFile(context, url)
        if (!f.exists()) download(url, f)
        if (!f.exists()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxPx * 1.5) sample *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply {
                inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565
            })
        } catch (e: Exception) { f.delete(); null }
    }

    private fun download(url: String, into: File) {
        val tmp = File(into.path + ".part")
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 15000
            try {
                if (c.responseCode == 200) {
                    c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                    tmp.renameTo(into)
                }
            } finally { c.disconnect() }
        } catch (e: Exception) { tmp.delete() }
    }

    private fun diskFile(context: Context, url: String): File {
        val dir = File(context.cacheDir, "img").apply { mkdirs() }
        val h = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, h)
    }
}
