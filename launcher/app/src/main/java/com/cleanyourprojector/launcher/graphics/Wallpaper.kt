package com.cleanyourprojector.launcher.graphics

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Background pictures. Bundled ones live in drawable-nodpi; the user's own live in the app's
 * external files dir (no runtime permission needed):
 *   /sdcard/Android/data/com.cleanyourprojector.launcher/files/wallpapers/
 * Push images there with:  adb push pic.jpg /sdcard/Android/data/com.cleanyourprojector.launcher/files/wallpapers/
 */
object Wallpaper {

    fun dir(context: Context): File =
        File(context.getExternalFilesDir(null), "wallpapers").apply { mkdirs() }

    fun images(context: Context): List<File> {
        val d = dir(context)
        return (d.listFiles { f ->
            f.isFile && f.name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp")
        }?.sortedBy { it.name } ?: emptyList())
    }

    /** The picture for this moment: [paths] in turn, one per [periodMs]; null when there are none. */
    fun pick(paths: List<String>, nowMs: Long, periodMs: Long): String? {
        if (paths.isEmpty()) return null
        return paths[((nowMs / periodMs) % paths.size).toInt()]
    }

    const val HALF_HOUR_MS = 30 * 60 * 1000L

    /** What the theme list says under "My wallpaper": where to put pictures, and how many there are. */
    fun hint(count: Int, dir: String): String = when (count) {
        0 -> "No pictures yet. Copy some to:\n$dir"
        1 -> "1 picture, a new one every half hour. Add more at:\n$dir"
        else -> "$count pictures, a new one every half hour. Add more at:\n$dir"
    }

    /** Decode one image downsampled to roughly the screen size, 16-bit: half the memory of ARGB. */
    fun load(path: String, reqW: Int, reqH: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            BitmapFactory.decodeFile(path, options(sample(bounds, reqW, reqH)))
        } catch (e: Exception) { null }
    }

    /** A bundled picture, same decode as a pushed one. */
    fun load(res: Resources, id: Int, reqW: Int, reqH: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(res, id, bounds)
            BitmapFactory.decodeResource(res, id, options(sample(bounds, reqW, reqH)))
        } catch (e: Exception) { null }
    }

    private fun sample(bounds: BitmapFactory.Options, reqW: Int, reqH: Int): Int {
        var sample = 1
        while (bounds.outWidth / sample > reqW * 1.2 && bounds.outHeight / sample > reqH * 1.2) sample *= 2
        return sample
    }

    private fun options(sample: Int) = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565
        inScaled = false
    }
}
