package com.cleanbeam.launcher.catalog

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Per-title deep links from JustWatch's public GraphQL endpoint (the same data TMDB's
 * watch-provider lists come from). For a title we search by name, match the candidate
 * whose TMDB id and kind agree with ours, and take the offer for the service the user
 * picked: its Android TV `intent:` URI first, its web URL second.
 *
 * Unofficial API: every failure degrades to null and the caller falls back to the
 * service's search link. Responses are cached on disk like TMDB's.
 */
object JustWatch {

    private const val ENDPOINT = "https://apis.justwatch.com/graphql"
    private const val TTL = 7 * 24 * 60 * 60 * 1000L

    class Offer(val provider: String, val deeplink: String?, val web: String?)
    class Candidate(val tmdbId: Int?, val kind: String, val offers: List<Offer>)

    private const val QUERY = """
        query(${'$'}q: String!, ${'$'}c: Country!) {
          popularTitles(country: ${'$'}c, first: 5, filter: { searchQuery: ${'$'}q }) {
            edges { node {
              objectType
              content(country: ${'$'}c, language: "en") { externalIds { tmdbId } }
              ... on MovieOrShow {
                offers(country: ${'$'}c, platform: ANDROID_TV) {
                  standardWebURL
                  deeplinkURL(platform: ANDROID_TV)
                  package { clearName }
                }
              }
            } }
          }
        }
    """

    // ---------- pure ----------
    fun parse(json: String): List<Candidate> {
        val edges = runCatching {
            JSONObject(json).getJSONObject("data").getJSONObject("popularTitles").getJSONArray("edges")
        }.getOrNull() ?: return emptyList()
        val out = ArrayList<Candidate>()
        for (i in 0 until edges.length()) {
            val node = edges.optJSONObject(i)?.optJSONObject("node") ?: continue
            val kind = when (node.optString("objectType")) { "MOVIE" -> "movie"; "SHOW" -> "tv"; else -> continue }
            val tmdbId = node.optJSONObject("content")?.optJSONObject("externalIds")
                ?.optString("tmdbId")?.toIntOrNull()
            val offers = ArrayList<Offer>()
            val arr = node.optJSONArray("offers")
            if (arr != null) for (j in 0 until arr.length()) {
                val o = arr.optJSONObject(j) ?: continue
                offers += Offer(
                    provider = o.optJSONObject("package")?.optString("clearName") ?: "",
                    deeplink = o.optString("deeplinkURL").ifEmpty { null },
                    web = o.optString("standardWebURL").ifEmpty { null }
                )
            }
            out += Candidate(tmdbId, kind, offers)
        }
        return out
    }

    /** Best URL that opens [t] inside [service]: an `intent:` URI, else a web URL, else null. */
    fun pickUrl(candidates: List<Candidate>, t: Title, service: Service): String? {
        val c = candidates.firstOrNull { it.tmdbId == t.id && it.kind == t.kind } ?: return null
        val offers = c.offers.filter { service.matchesTmdbName(it.provider) }
        return offers.firstNotNullOfOrNull { it.deeplink } ?: offers.firstNotNullOfOrNull { it.web }
    }

    // ---------- network (call off the main thread) ----------
    fun urlFor(context: Context, t: Title, region: String, service: Service): String? =
        pickUrl(lookup(context, t, region), t, service)

    private fun lookup(context: Context, t: Title, region: String): List<Candidate> {
        val body = JSONObject().put("query", QUERY)
            .put("variables", JSONObject().put("q", t.name).put("c", region)).toString()
        val f = cacheFile(context, body)
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < TTL
        if (fresh) runCatching { return parse(f.readText()) }
        val resp = post(body)
        if (resp != null) {
            runCatching { f.writeText(resp) }
            return parse(resp)
        }
        return if (f.exists()) runCatching { parse(f.readText()) }.getOrDefault(emptyList()) else emptyList()
    }

    private fun cacheFile(context: Context, key: String): File {
        val dir = File(context.cacheDir, "justwatch").apply { mkdirs() }
        val h = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$h.json")
    }

    private fun post(body: String): String? = try {
        val c = URL(ENDPOINT).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 10000
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "CleanBeamLauncher/1.0")
        try {
            c.outputStream.use { it.write(body.toByteArray()) }
            if (c.responseCode != 200) { Log.w("Projector", "justwatch -> ${c.responseCode}"); null }
            else c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    } catch (e: Exception) { Log.w("Projector", "justwatch failed: ${e.message}"); null }

    fun clearCache(context: Context) { File(context.cacheDir, "justwatch").deleteRecursively() }
}
