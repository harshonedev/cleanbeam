package com.cleanyourprojector.launcher.home

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The big clock's three lines. Formatters are cached per pattern: this runs once a second. */
object LiteClock {

    data class Text(val time: String, val ampm: String, val date: String)

    private val formats = HashMap<String, SimpleDateFormat>()

    /** [Text.ampm] is empty in 24-hour mode. */
    fun format(now: Date, clock24h: Boolean, showSeconds: Boolean, locale: Locale = Locale.getDefault()): Text {
        val time = buildString {
            append(if (clock24h) "H:mm" else "h:mm")
            if (showSeconds) append(":ss")
        }
        return Text(
            time = fmt(time, locale).format(now),
            ampm = if (clock24h) "" else fmt("a", locale).format(now),
            date = fmt("EEEE, d MMMM", locale).format(now)
        )
    }

    private fun fmt(pattern: String, locale: Locale): SimpleDateFormat =
        formats.getOrPut("$pattern|$locale") { SimpleDateFormat(pattern, locale) }
}
