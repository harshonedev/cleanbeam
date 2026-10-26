package com.cleanbeam.launcher.graphics

/**
 * What the screen's background shows right now, so a theme change loads a picture once and
 * clears it once. The key is private: a caller that could reset it would skip the clear when a
 * flat theme follows a picture (which is exactly what happened before this class existed).
 */
class Backdrop {

    sealed class Change {
        object None : Change()
        object Clear : Change()
        data class Load(val key: String) : Change()
    }

    private var key = ""

    /** [source] is the picture to show (a resource id or a file path, as text), or null for a flat theme. */
    fun change(source: String?): Change = when {
        source == null -> if (key.isEmpty()) Change.None else { key = ""; Change.Clear }
        source == key -> Change.None
        else -> { key = source; Change.Load(source) }
    }

    /** A decode that finished for [key] may be shown only while it is still the one wanted. */
    fun isCurrent(key: String): Boolean = this.key == key
}
