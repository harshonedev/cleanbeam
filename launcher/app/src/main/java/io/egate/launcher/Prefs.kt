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
