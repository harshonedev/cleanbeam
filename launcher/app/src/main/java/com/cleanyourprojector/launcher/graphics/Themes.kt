package com.cleanyourprojector.launcher.graphics

import androidx.annotation.DrawableRes
import com.cleanyourprojector.launcher.R

/**
 * One look of the launcher: a single still background (a flat colour, or one picture over it)
 * and the card style. Nothing in a theme changes per frame or per focused app.
 *
 * @param background colour behind everything; also what shows while a picture decodes
 * @param wallpaper bundled picture in drawable-nodpi, 0 for a flat theme
 * @param glass frosted cards and panel (over a picture or a flat colour) instead of solid ones
 * @param userWallpaper pictures the user pushed to files/wallpapers, one per half hour
 * @param dynamic the Ambient look: the focused item's art washed over the room in its colour,
 *   over a time-of-day sky, with scrims behind the hero text; the one theme that moves
 */
class Theme(
    val name: String,
    val background: Int,
    @DrawableRes val wallpaper: Int,
    val glass: Boolean,
    val userWallpaper: Boolean = false,
    val dynamic: Boolean = false
) {
    val hasPicture: Boolean get() = wallpaper != 0 || userWallpaper
}

object Themes {
    val ALL = listOf(
        Theme("Midnight", 0xFF070A14.toInt(), 0, glass = false),
        Theme("Ambient", 0xFF0B1020.toInt(), 0, glass = false, dynamic = true),
        Theme("Aurora", 0xFF02040C.toInt(), R.drawable.wp_aurora, glass = true),
        Theme("Nebula", 0xFF070B16.toInt(), R.drawable.wp_nebula, glass = true),
        Theme("Ruska", 0xFF07080D.toInt(), R.drawable.wp_ruska, glass = true),
        Theme("Graphite", 0xFF15171C.toInt(), 0, glass = true),
        Theme("Pure black", 0xFF000000.toInt(), 0, glass = false),
        Theme("My wallpaper", 0xFF070A14.toInt(), 0, glass = true, userWallpaper = true)
    )
    const val DEFAULT = 0
    fun indexOf(name: String?): Int = ALL.indexOfFirst { it.name == name }
}

/** The card style every adapter reads at bind time, so recycled cards follow a theme change. */
object Look {
    @Volatile var glass = false
    val cardBg: Int get() = if (glass) R.drawable.card_surface_glass else R.drawable.card_surface
    val panelBg: Int get() = if (glass) R.drawable.panel_bg_glass else R.drawable.panel_bg
}
