package com.cleanyourprojector.launcher.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class LiteClockTest {
    private fun at(h: Int, m: Int, s: Int) =
        Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 23, h, m, s) }.time

    @Test fun `twelve hour clock with date`() {
        assertEquals(LiteClock.Text("9:26", "PM", "Friday, 23 October"), LiteClock.format(at(21, 26, 5), false, false, Locale.US))
    }

    @Test fun `twenty four hour clock with seconds`() {
        assertEquals(LiteClock.Text("21:26:05", "", "Friday, 23 October"), LiteClock.format(at(21, 26, 5), true, true, Locale.US))
    }

    @Test fun `twelve hour clock at midnight and noon`() {
        assertEquals("12:00" to "AM", LiteClock.format(at(0, 0, 0), false, false, Locale.US).let { it.time to it.ampm })
        assertEquals("12:00" to "PM", LiteClock.format(at(12, 0, 0), false, false, Locale.US).let { it.time to it.ampm })
    }

    @Test fun `follows a changed default time zone`() {
        val was = TimeZone.getDefault()
        try {
            val noon = Date(1_750_000_000_000L)   // a fixed instant
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val utc = LiteClock.format(noon, true, false, Locale.US).time
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+05:30"))
            val india = LiteClock.format(noon, true, false, Locale.US).time
            assertEquals("15:06", utc)
            assertEquals("20:36", india)
        } finally { TimeZone.setDefault(was) }
    }
}
