package io.egate.launcher

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
 */
data class HomeRow(
    val header: String,
    val titles: List<Title>,
    val providerPkg: String?,
    val providerName: String?,
    val apps: List<AppInfo> = emptyList()
) {
    val isAppRow get() = apps.isNotEmpty()
}
