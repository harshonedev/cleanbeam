package com.cleanyourprojector.launcher.catalog

import com.cleanyourprojector.launcher.apps.AppInfo

/** One film or series from TMDB. */
data class Title(
    val id: Int,
    val kind: String,          // "movie" | "tv"
    val name: String,
    val overview: String,
    val year: String?,
    val rating: Double,        // 0 when unknown
    val backdrop: String?,     // TMDB path, e.g. /abc.jpg
    val poster: String?
)

/**
 * A horizontal shelf on the Home tab: either TMDB titles (providerPkg is the installed app that
 * carries them) or, when [apps] is set, a row of app cards such as Favorites.
 * While [loading] the shelf shows skeleton cards under its header; a row that finished loading
 * with no titles is dropped from the screen.
 */
data class HomeRow(
    val header: String,
    val titles: List<Title>,
    val providerPkg: String?,
    val providerName: String?,
    val apps: List<AppInfo> = emptyList(),
    val loading: Boolean = false
) {
    val isAppRow get() = apps.isNotEmpty()
    /** Something to draw: cards, or skeletons while they load. */
    val visible get() = loading || isAppRow || titles.isNotEmpty()
}
