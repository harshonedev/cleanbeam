package io.egate.launcher

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.TimeZone

/**
 * Minimal TMDB v3 client. Every GET is cached on disk; a stale copy is served when
 * the network is down so the Home tab still fills on a cold start.
 *
 * Auth: a v4 read access token (starts with "eyJ") goes in the Authorization header,
 * a v3 key goes in the query string. The token comes from prefs or from
 *   /sdcard/Android/data/io.egate.launcher/files/tmdb.key
 */
object Tmdb {

    private const val BASE = "https://api.themoviedb.org/3"
    private const val IMG = "https://image.tmdb.org/t/p/"
    private const val HOUR = 60 * 60 * 1000L
    const val ROWS_TTL = 6 * HOUR
    private const val PROVIDERS_TTL = 7 * 24 * HOUR

    /** package-name fragment -> TMDB provider names, tried in order. */
    private val PKG_HINTS = listOf(
        "netflix" to listOf("Netflix"),
        "amazonvideo" to listOf("Amazon Prime Video"),
        "primevideo" to listOf("Amazon Prime Video"),
        "hotstar" to listOf("JioHotstar", "Hotstar", "Disney Plus Hotstar", "Disney Plus"),
        "disney" to listOf("Disney Plus", "JioHotstar"),
        "youtube" to listOf("YouTube"),
        "ahaflix" to listOf("aha"),
        "mxtech" to listOf("MX Player", "Amazon MX Player"),
        "jiocinema" to listOf("JioCinema", "JioHotstar"),
        "graymatrix" to listOf("Zee5"),
        "zee5" to listOf("Zee5"),
        "sonyliv" to listOf("Sony Liv"),
        "apple" to listOf("Apple TV", "Apple TV Plus"),
        "crunchyroll" to listOf("Crunchyroll"),
        "mubi" to listOf("MUBI"),
        "hoichoi" to listOf("Hoichoi"),
        "sunnxt" to listOf("Sun Nxt"),
        "erosnow" to listOf("Eros Now"),
        "lionsgate" to listOf("Lionsgate Play"),
        "discovery" to listOf("Discovery+"),
        "hbomax" to listOf("HBO Max", "Max"),
        "hulu" to listOf("Hulu"),
        "peacock" to listOf("Peacock Premium", "Peacock"),
        "paramount" to listOf("Paramount Plus"),
        "viki" to listOf("Viki")
    )
    private val NOISE = setOf("video", "plus", "play", "tv", "app", "the", "for", "android", "premium", "channel", "store", "with", "ads", "free")

    class Provider(val id: Int, val name: String, val priority: Int)

    // ---------- config ----------
    fun token(context: Context): String {
        val p = Prefs(context).tmdbToken.trim()
        if (p.isNotEmpty()) return p
        return runCatching { File(context.getExternalFilesDir(null), "tmdb.key").readText().trim() }.getOrDefault("")
    }

    fun configured(context: Context) = token(context).isNotEmpty()

    fun defaultRegion(): String {
        val tz = TimeZone.getDefault().id
        if (tz == "Asia/Kolkata" || tz == "Asia/Calcutta") return "IN"
        return Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
    }

    fun imageUrl(path: String?, size: String): String? = path?.let { "$IMG$size$it" }

    // ---------- public queries (call off the main thread) ----------
    fun providers(context: Context, region: String): List<Provider> {
        val json = get(context, "/watch/providers/movie?watch_region=$region", PROVIDERS_TTL) ?: return emptyList()
        val out = ArrayList<Provider>()
        val arr = json.optJSONArray("results") ?: return out
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val pri = o.optJSONObject("display_priorities")?.optInt(region, 999) ?: 999
            out += Provider(o.getInt("provider_id"), o.getString("provider_name"), pri)
        }
        return out.sortedBy { it.priority }
    }

    /** Installed apps that map to a TMDB provider, most recently used first; one app per provider. */
    fun matchProviders(apps: List<AppInfo>, providers: List<Provider>): List<Pair<AppInfo, Provider>> {
        val byName = providers.associateBy { it.name.lowercase() }
        val matched = LinkedHashMap<Int, Pair<AppInfo, Provider>>()
        for (app in apps.sortedByDescending { it.lastUsed }) {
            val p = matchOne(app, providers, byName) ?: continue
            if (!matched.containsKey(p.id)) matched[p.id] = app to p
        }
        return matched.values.toList()
    }

    private fun matchOne(app: AppInfo, providers: List<Provider>, byName: Map<String, Provider>): Provider? {
        val pkg = app.packageName.lowercase()
        for ((frag, names) in PKG_HINTS) {
            if (!pkg.contains(frag)) continue
            for (n in names) byName[n.lowercase()]?.let { return it }
        }
        // Fallback: the label and the provider name collapse to the same word ("MX Player" / "mxplayer"),
        // or one contains the other ("CLAP Netflix" / "Netflix"). Short names must match exactly.
        val a = norm(app.label)
        if (a.isEmpty()) return null
        return providers.firstOrNull { p ->
            val b = norm(p.name)
            b.isNotEmpty() && (a == b || (b.length >= 4 && a.length >= 4 && (a.contains(b) || b.contains(a))))
        }
    }

    private fun norm(s: String): String =
        s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() && it !in NOISE }.joinToString("")

    fun rows(context: Context, apps: List<AppInfo>, region: String): List<HomeRow> {
        val out = ArrayList<HomeRow>()
        val providers = providers(context, region)
        for ((app, p) in matchProviders(apps, providers)) {
            val titles = interleave(
                discover(context, "movie", p.id, region),
                discover(context, "tv", p.id, region)
            )
            if (titles.isNotEmpty()) out += HomeRow("On ${app.label}", titles, app.packageName, p.name)
        }
        trending(context)?.takeIf { it.isNotEmpty() }?.let { out += HomeRow("Trending this week", it, null, null) }
        topRated(context, region)?.takeIf { it.isNotEmpty() }?.let { out += HomeRow("Top rated films", it, null, null) }
        return out
    }

    /** Streaming services (flatrate) that carry this title in the region. */
    fun providersFor(context: Context, t: Title, region: String): List<String> {
        val json = get(context, "/${t.kind}/${t.id}/watch/providers", ROWS_TTL) ?: return emptyList()
        val r = json.optJSONObject("results")?.optJSONObject(region) ?: return emptyList()
        val out = ArrayList<String>()
        for (key in listOf("flatrate", "free", "ads")) {
            val arr = r.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) out += arr.getJSONObject(i).optString("provider_name")
        }
        return out.distinct()
    }

    // ---------- endpoints ----------
    private fun discover(context: Context, kind: String, provider: Int, region: String): List<Title> {
        val floor = if (kind == "movie") 300 else 100
        val q = "/discover/$kind?with_watch_providers=$provider&watch_region=$region" +
            "&sort_by=popularity.desc&vote_count.gte=$floor&with_watch_monetization_types=flatrate|free|ads"
        return parseList(get(context, q, ROWS_TTL), kind)
    }

    private fun trending(context: Context): List<Title>? =
        get(context, "/trending/all/week", ROWS_TTL)?.let { parseList(it, null).filter { t -> t.kind != "person" } }

    private fun topRated(context: Context, region: String): List<Title>? =
        get(context, "/movie/top_rated?region=$region", ROWS_TTL)?.let { parseList(it, "movie") }

    private fun interleave(a: List<Title>, b: List<Title>): List<Title> {
        val out = ArrayList<Title>()
        val ia = a.iterator(); val ib = b.iterator()
        while ((ia.hasNext() || ib.hasNext()) && out.size < 20) {
            if (ia.hasNext()) out += ia.next()
            if (ib.hasNext()) out += ib.next()
        }
        return out
    }

    private fun parseList(json: JSONObject?, kind: String?): List<Title> {
        val arr = json?.optJSONArray("results") ?: return emptyList()
        val out = ArrayList<Title>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val k = kind ?: o.optString("media_type", "movie")
            val name = o.optString("title").ifEmpty { o.optString("name") }
            if (name.isEmpty() || o.optString("backdrop_path").isEmpty()) continue
            val date = o.optString("release_date").ifEmpty { o.optString("first_air_date") }
            out += Title(
                id = o.getInt("id"), kind = k, name = name,
                overview = o.optString("overview"),
                year = date.takeIf { it.length >= 4 }?.substring(0, 4),
                rating = o.optDouble("vote_average", 0.0),
                backdrop = o.optString("backdrop_path").ifEmpty { null },
                poster = o.optString("poster_path").ifEmpty { null }
            )
        }
        return out
    }

    // ---------- transport + cache ----------
    private fun cacheFile(context: Context, path: String): File {
        val dir = File(context.cacheDir, "tmdb").apply { mkdirs() }
        val h = MessageDigest.getInstance("SHA-1").digest(path.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$h.json")
    }

    fun get(context: Context, path: String, ttl: Long): JSONObject? {
        val f = cacheFile(context, path)
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < ttl
        if (fresh) runCatching { return JSONObject(f.readText()) }
        val body = fetch(context, path)
        if (body != null) {
            runCatching { f.writeText(body) }
            return runCatching { JSONObject(body) }.getOrNull()
        }
        return if (f.exists()) runCatching { JSONObject(f.readText()) }.getOrNull() else null
    }

    private fun fetch(context: Context, path: String): String? {
        val tok = token(context)
        if (tok.isEmpty()) return null
        val bearer = tok.startsWith("eyJ")
        val sep = if (path.contains('?')) "&" else "?"
        val url = BASE + path + (if (bearer) "" else "${sep}api_key=$tok")
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 10000
            c.setRequestProperty("Accept", "application/json")
            if (bearer) c.setRequestProperty("Authorization", "Bearer $tok")
            try {
                if (c.responseCode != 200) { Log.w("EGate", "tmdb $path -> ${c.responseCode}"); null }
                else c.inputStream.bufferedReader().readText()
            } finally { c.disconnect() }
        } catch (e: Exception) { Log.w("EGate", "tmdb $path failed: ${e.message}"); null }
    }

    fun clearCache(context: Context) { File(context.cacheDir, "tmdb").deleteRecursively() }
}
