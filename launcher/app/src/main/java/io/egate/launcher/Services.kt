package io.egate.launcher

import java.net.URLEncoder

/**
 * A streaming service as the Home tab sees it: how TMDB names it (several variants per
 * service: "Netflix" / "Netflix basic with Ads"), which installed package is it, where to
 * install it from, and how to open a search for a title inside the app.
 *
 * YouTube is deliberately absent: its catalogue is user uploads, not releases.
 */
class Service(
    val name: String,
    val tmdbNames: List<String>,
    private val pkgFragments: List<String>,
    /** Play Store target when the app is missing. */
    val storePkg: String,
    /** `%s` is the URL-encoded title; null when the app has no search deep link. */
    private val searchTemplate: String?
) {
    private val tmdbLower = tmdbNames.map { it.lowercase() }

    fun matchesPkg(pkg: String) = pkgFragments.any { pkg.contains(it) }
    fun matchesTmdbName(n: String) = n.lowercase() in tmdbLower

    fun searchUrl(title: String): String? =
        searchTemplate?.replace("%s", URLEncoder.encode(title, "UTF-8").replace("+", "%20"))

    override fun toString() = name
}

object Services {

    val ALL: List<Service> = listOf(
        Service("Netflix", listOf("Netflix", "Netflix basic with Ads", "Netflix Kids"),
            listOf("netflix"), "com.netflix.ninja", "https://www.netflix.com/search?q=%s"),
        Service("Prime Video", listOf("Amazon Prime Video", "Amazon Prime Video with Ads"),
            listOf("amazonvideo", "primevideo", "amazon.avod"), "com.amazon.amazonvideo.livingroom",
            "https://app.primevideo.com/search?phrase=%s"),
        Service("JioHotstar", listOf("JioHotstar", "Hotstar", "Disney Plus Hotstar", "JioCinema"),
            listOf("hotstar", "jiocinema"), "in.startv.hotstar", "https://www.hotstar.com/in/search?q=%s"),
        Service("Disney+", listOf("Disney Plus"),
            listOf("disneyplus", "disney"), "com.disney.disneyplus", "https://www.disneyplus.com/search/%s"),
        Service("Zee5", listOf("Zee5"),
            listOf("graymatrix", "zee5"), "com.graymatrix.did", "https://www.zee5.com/search?q=%s"),
        Service("SonyLIV", listOf("Sony Liv", "SonyLIV"),
            listOf("sonyliv"), "com.sonyliv", "https://www.sonyliv.com/search?searchTerm=%s"),
        Service("Apple TV", listOf("Apple TV", "Apple TV Plus"),
            listOf("apple.atve", "appletv"), "com.apple.atve.androidtv.appletv", "https://tv.apple.com/search?term=%s"),
        Service("MX Player", listOf("MX Player", "Amazon MX Player"),
            listOf("mxtech"), "com.mxtech.videoplayer.ad", "https://www.mxplayer.in/search?q=%s"),
        Service("aha", listOf("aha"),
            listOf("ahaflix", "aha.tv"), "ahaflix.tv", null),
        Service("Crunchyroll", listOf("Crunchyroll"),
            listOf("crunchyroll"), "com.crunchyroll.crunchyroid", "https://www.crunchyroll.com/search?q=%s"),
        Service("MUBI", listOf("MUBI"),
            listOf("mubi"), "com.mubi", "https://mubi.com/search/films?query=%s"),
        Service("Hoichoi", listOf("Hoichoi"), listOf("hoichoi"), "com.viewlift.hoichoi", null),
        Service("Sun NXT", listOf("Sun Nxt"), listOf("sunnxt"), "com.suntv.sunnxt", null),
        Service("Lionsgate Play", listOf("Lionsgate Play"), listOf("lionsgate"), "com.lionsgateplay.videoapp", null),
        Service("Discovery+", listOf("Discovery+", "Discovery Plus"), listOf("discoveryplus", "discovery"),
            "com.discoveryplus.androidtv", null),
        Service("HBO Max", listOf("HBO Max", "Max"), listOf("hbomax", "wbd.stream", "hbo"),
            "com.wbd.stream", "https://play.max.com/search?q=%s"),
        Service("Hulu", listOf("Hulu"), listOf("hulu"), "com.hulu.plus", null),
        Service("Peacock", listOf("Peacock Premium", "Peacock"), listOf("peacock"), "com.peacocktv.peacockandroid", null),
        Service("Paramount+", listOf("Paramount Plus", "Paramount+"), listOf("paramount", "cbs.app"), "com.cbs.app", null),
        Service("Viki", listOf("Viki"), listOf("viki"), "com.viki.android", null)
    )

    private val NOISE = setOf("video", "plus", "play", "tv", "app", "the", "for", "android", "premium", "channel", "store", "with", "ads", "free")

    /** The service an installed app belongs to, by package fragment first, then by label. */
    fun forApp(pkg: String, label: String): Service? {
        val p = pkg.lowercase()
        ALL.firstOrNull { it.matchesPkg(p) }?.let { return it }
        val a = norm(label)
        if (a.length < 4) return null
        return ALL.firstOrNull { s ->
            (s.tmdbNames + s.name).any { n -> val b = norm(n); b.length >= 4 && (a == b || a.contains(b) || b.contains(a)) }
        }
    }

    /** Installed apps that belong to a service, most recently used first; one app per service. */
    fun installed(apps: List<AppInfo>): Map<Service, AppInfo> {
        val out = LinkedHashMap<Service, AppInfo>()
        for (app in apps.sortedByDescending { it.lastUsed }) {
            val s = forApp(app.packageName, app.label) ?: continue
            if (!out.containsKey(s)) out[s] = app
        }
        return out
    }

    /** TMDB provider names → distinct services, in the order TMDB listed them. Unknown names are dropped. */
    fun forTmdbNames(names: List<String>): List<Service> {
        val out = ArrayList<Service>()
        for (n in names) {
            val s = ALL.firstOrNull { it.matchesTmdbName(n) } ?: continue
            if (s !in out) out += s
        }
        return out
    }

    private fun norm(s: String): String =
        s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() && it !in NOISE }.joinToString("")
}
