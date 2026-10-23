package com.cleanyourprojector.launcher.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale

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
}
