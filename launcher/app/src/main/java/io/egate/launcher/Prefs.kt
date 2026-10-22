package io.egate.launcher

import android.content.Context
import android.graphics.Color

/** Shared settings used by both the launcher and the screensaver. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("egate_prefs", Context.MODE_PRIVATE)

    // ----- accent -----
    data class Accent(val name: String, val color: Int)

    companion object {
        val ACCENTS = listOf(
            Accent("Blue",   Color.parseColor("#5B8CFF")),
            Accent("Teal",   Color.parseColor("#2BC7C7")),
            Accent("Green",  Color.parseColor("#3DD68C")),
            Accent("Orange", Color.parseColor("#FF9F43")),
            Accent("Pink",   Color.parseColor("#FF5C8A")),
            Accent("Purple", Color.parseColor("#9B6CFF"))
        )
        /** Package-name fragments, in display order. */
        val DEFAULT_FAVORITES = listOf("youtube", "netflix", "hotstar", "amazonvideo", "primevideo")
        const val MIN_COLS = 4
        const val MAX_COLS = 7
    }

    var accentIndex: Int
        get() = sp.getInt("accent", 0).coerceIn(0, ACCENTS.size - 1)
        set(v) = sp.edit().putInt("accent", v).apply()

    val accentColor: Int get() = ACCENTS[accentIndex].color

    var columns: Int
        get() = sp.getInt("columns", 5).coerceIn(MIN_COLS, MAX_COLS)
        set(v) = sp.edit().putInt("columns", v.coerceIn(MIN_COLS, MAX_COLS)).apply()

    var clock24h: Boolean
        get() = sp.getBoolean("clock24h", false)
        set(v) = sp.edit().putBoolean("clock24h", v).apply()

    var showSeconds: Boolean
        get() = sp.getBoolean("seconds", false)
        set(v) = sp.edit().putBoolean("seconds", v).apply()

    var bigClock: Boolean
        get() = sp.getBoolean("bigclock", true)
        set(v) = sp.edit().putBoolean("bigclock", v).apply()

    var appGlow: Boolean
        get() = sp.getBoolean("appglow", true)
        set(v) = sp.edit().putBoolean("appglow", v).apply()

    var ambientSky: Boolean
        get() = sp.getBoolean("sky", true)
        set(v) = sp.edit().putBoolean("sky", v).apply()

    var greeting: Boolean
        get() = sp.getBoolean("greeting", true)
        set(v) = sp.edit().putBoolean("greeting", v).apply()

    var showStats: Boolean
        get() = sp.getBoolean("stats", false)
        set(v) = sp.edit().putBoolean("stats", v).apply()

    var namesFocusedOnly: Boolean
        get() = sp.getBoolean("names_focus", false)
        set(v) = sp.edit().putBoolean("names_focus", v).apply()

    var wallpaper: Boolean
        get() = sp.getBoolean("wallpaper", false)
        set(v) = sp.edit().putBoolean("wallpaper", v).apply()

    var wallpaperDim: Int
        get() = sp.getInt("wallpaper_dim", 40).coerceIn(0, 80)
        set(v) = sp.edit().putInt("wallpaper_dim", v.coerceIn(0, 80)).apply()

    // ----- remote shortcuts (keycode -> package) -----
    fun shortcuts(): LinkedHashMap<Int, String> {
        val raw = sp.getString("shortcuts", "") ?: ""
        val map = LinkedHashMap<Int, String>()
        if (raw.isEmpty()) return map
        for (part in raw.split(";")) {
            if (part.isBlank()) continue
            val kv = part.split(":", limit = 2)
            val code = kv[0].toIntOrNull() ?: continue
            if (kv.size == 2 && kv[1].isNotBlank()) map[code] = kv[1]
        }
        return map
    }

    private fun saveShortcuts(map: Map<Int, String>) {
        sp.edit().putString("shortcuts", map.entries.joinToString(";") { "${it.key}:${it.value}" }).apply()
    }

    fun setShortcut(keyCode: Int, pkg: String) {
        val m = shortcuts(); m[keyCode] = pkg; saveShortcuts(m)
    }

    fun removeShortcut(keyCode: Int) {
        val m = shortcuts(); m.remove(keyCode); saveShortcuts(m)
    }

    var tmdbToken: String
        get() = sp.getString("tmdb_token", "") ?: ""
        set(v) = sp.edit().putString("tmdb_token", v.trim()).apply()

    var region: String
        get() = sp.getString("region", "") ?: ""
        set(v) = sp.edit().putString("region", v.trim().uppercase()).apply()

    var keepAwake: Boolean
        get() = sp.getBoolean("keep_awake", false)
        set(v) = sp.edit().putBoolean("keep_awake", v).apply()

    // ----- favorites (ordered list of package names) -----
    fun favorites(): MutableList<String> {
        val raw = sp.getString("favorites", "") ?: ""
        return if (raw.isEmpty()) mutableListOf()
        else raw.split(",").filter { it.isNotBlank() }.toMutableList()
    }

    private fun saveFavorites(list: List<String>) {
        sp.edit().putString("favorites", list.joinToString(",")).apply()
    }

    fun isFavorite(pkg: String) = favorites().contains(pkg)

    /**
     * Once per install: pin the big streaming apps that are installed, in this order, ahead of
     * anything already pinned. Never runs again, so a user who unpins them stays unpinned.
     */
    fun seedFavorites(installedPkgs: List<String>) {
        if (sp.getBoolean("favorites_seeded", false) || installedPkgs.isEmpty()) return
        val seed = ArrayList<String>()
        for (frag in DEFAULT_FAVORITES) installedPkgs.firstOrNull { it.lowercase().contains(frag) }?.let { if (it !in seed) seed += it }
        for (pkg in favorites()) if (pkg !in seed) seed += pkg
        saveFavorites(seed)
        sp.edit().putBoolean("favorites_seeded", true).apply()
    }

    fun toggleFavorite(pkg: String) {
        val f = favorites()
        if (f.contains(pkg)) f.remove(pkg) else f.add(pkg)
        saveFavorites(f)
    }

    /** Move a favorite one slot left (-1) or right (+1). */
    fun moveFavorite(pkg: String, dir: Int) {
        val f = favorites()
        val i = f.indexOf(pkg)
        if (i < 0) return
        val j = (i + dir).coerceIn(0, f.size - 1)
        if (i == j) return
        f.removeAt(i)
        f.add(j, pkg)
        saveFavorites(f)
    }
}
