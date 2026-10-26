package com.cleanbeam.launcher.home

import com.cleanbeam.launcher.apps.AppInfo
import com.cleanbeam.launcher.settings.Tile

/** One shelf of Lite's Home: app cards or settings tiles, never both. */
data class LiteRow(
    val header: String,
    val apps: List<AppInfo> = emptyList(),
    val tiles: List<Tile> = emptyList()
)

object LiteRows {

    /** Settings tiles that earn a place on Home, by title, in this order. */
    val QUICK = listOf("Wi-Fi", "Bluetooth", "Focus", "Keystone", "Brightness", "Input source", "Launcher")

    /** The quick tiles this box has, in [QUICK] order. */
    fun quick(tiles: List<Tile>): List<Tile> = QUICK.mapNotNull { q -> tiles.firstOrNull { it.title == q } }

    /** Favorites first (only when there are any), then the quick settings shelf, always. */
    fun build(favorites: List<AppInfo>, quick: List<Tile>, favoritesHeader: String, quickHeader: String): List<LiteRow> =
        listOfNotNull(
            favorites.takeIf { it.isNotEmpty() }?.let { LiteRow(favoritesHeader, apps = it) },
            LiteRow(quickHeader, tiles = quick)
        )
}
