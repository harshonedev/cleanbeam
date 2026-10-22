package io.egate.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Wallpapers live in the app's external files dir (no runtime permission needed):
 *   /sdcard/Android/data/io.egate.launcher/files/wallpapers/
 * Push images there with:  adb push pic.jpg /sdcard/Android/data/io.egate.launcher/files/wallpapers/
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

    /** Decode one image downsampled to roughly the screen size. */
    fun load(path: String, reqW: Int, reqH: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > reqW * 1.2 && bounds.outHeight / sample > reqH * 1.2) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) { null }
    }
}
