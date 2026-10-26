package com.cleanbeam.launcher.catalog

import android.content.Context
import android.util.Log
import com.cleanbeam.launcher.Prefs
import com.cleanbeam.launcher.apps.AppInfo
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal TMDB v3 client. Every GET is cached on disk; a stale copy is served when
 * the network is down so the Home tab still fills on a cold start.
 *
 * Auth: a v4 read access token (starts with "eyJ") goes in the Authorization header,
 * a v3 key goes in the query string. The token comes from prefs or from
 *   /sdcard/Android/data/com.cleanbeam.launcher/files/tmdb.key
 */
object Tmdb {

    private const val BASE = "https://api.themoviedb.org/3"
    private const val IMG = "https://image.tmdb.org/t/p/"
    private const val HOUR = 60 * 60 * 1000L
    const val ROWS_TTL = 6 * HOUR
    private const val PROVIDERS_TTL = 7 * 24 * HOUR

    class Provider(val id: Int, val name: String, val priority: Int)

    // ---------- config ----------
    @Volatile private var cachedToken: String? = null

    fun token(context: Context): String {
        cachedToken?.let { return it }
        val p = Prefs(context).tmdbToken.trim()
        val t = if (p.isNotEmpty()) p
                else runCatching { File(context.getExternalFilesDir(null), "tmdb.key").readText().trim() }.getOrDefault("")
        if (t.isNotEmpty()) cachedToken = t   // an empty result is re-checked: the key file may be pushed later
        return t
    }

    /** Call after the token changes in prefs. */
    fun invalidateToken() { cachedToken = null }

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

    /** TMDB provider ids (all variants, e.g. Netflix + Netflix basic with Ads) for a service. */
    fun idsFor(service: Service, providers: List<Provider>): List<Int> =
        providers.filter { service.matchesTmdbName(it.name) }.map { it.id }

    /** One Home shelf waiting for its titles: the skeleton [row] to show now, and [load] to fill it. */
    class Pending(val row: HomeRow, private val fetch: (List<Provider>) -> List<Title>) {
        /** Call off the main thread. Returns the finished row, with no titles when nothing streams here. */
        fun load(providers: List<Provider>): HomeRow = row.copy(titles = fetch(providers), loading = false)
    }

    /**
     * The shelves Home will show, in order, before any network call: one per installed streaming
     * app, then the cross-service rows. Each knows how to fetch itself once the providers are known.
     */
    fun plan(context: Context, apps: List<AppInfo>, region: String): List<Pending> {
        val out = ArrayList<Pending>()
        for ((service, app) in Services.installed(apps)) {
            out += Pending(HomeRow("On ${app.label}", emptyList(), app.packageName, service.name, loading = true)) { providers ->
                val ids = idsFor(service, providers)
                if (ids.isEmpty()) emptyList()
                else interleave(discover(context, "movie", ids, region), discover(context, "tv", ids, region))
            }
        }
        // Rows across every service we know how to open, so each card is playable or installable.
        out += Pending(HomeRow("Popular on streaming", emptyList(), null, null, loading = true)) { providers ->
            val known = Services.ALL.flatMap { idsFor(it, providers) }.distinct()
            if (known.isEmpty()) emptyList()
            else interleave(discover(context, "movie", known, region), discover(context, "tv", known, region))
        }
        out += Pending(HomeRow("Top rated on streaming", emptyList(), null, null, loading = true)) { providers ->
            val known = Services.ALL.flatMap { idsFor(it, providers) }.distinct()
            if (known.isEmpty()) emptyList() else topRated(context, known, region)
        }
        return out
    }

    /** Every shelf, fetched in one go. Used for a refresh behind rows already on screen. */
    fun rows(context: Context, apps: List<AppInfo>, region: String): List<HomeRow> {
        val providers = providers(context, region)
        val out = plan(context, apps, region).map { it.load(providers) }.filter { it.titles.isNotEmpty() }
        Log.i("Projector", "home rows: ${out.map { "${it.header} (${it.titles.size})" }}")
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
    private const val MONETIZATION = "with_watch_monetization_types=flatrate|free|ads"

    private fun discover(context: Context, kind: String, providerIds: List<Int>, region: String): List<Title> {
        val floor = if (kind == "movie") 300 else 100
        val q = "/discover/$kind?with_watch_providers=${providerIds.joinToString("|")}&watch_region=$region" +
            "&sort_by=popularity.desc&vote_count.gte=$floor&$MONETIZATION"
        return parseList(get(context, q, ROWS_TTL), kind)
    }

    private fun topRated(context: Context, providerIds: List<Int>, region: String): List<Title> {
        val q = "/discover/movie?with_watch_providers=${providerIds.joinToString("|")}&watch_region=$region" +
            "&sort_by=vote_average.desc&vote_count.gte=2000&$MONETIZATION"
        return parseList(get(context, q, ROWS_TTL), "movie")
    }

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
                if (c.responseCode != 200) { Log.w("Projector", "tmdb $path -> ${c.responseCode}"); null }
                else c.inputStream.bufferedReader().readText()
            } finally { c.disconnect() }
        } catch (e: Exception) { Log.w("Projector", "tmdb $path failed: ${e.message}"); null }
    }

    fun clearCache(context: Context) { File(context.cacheDir, "tmdb").deleteRecursively() }
}
