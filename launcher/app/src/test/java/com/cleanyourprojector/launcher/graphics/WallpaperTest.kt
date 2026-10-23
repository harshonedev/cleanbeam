package com.cleanyourprojector.launcher.graphics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WallpaperTest {

    private val tenMin = 10 * 60 * 1000L

    @Test fun `no pictures gives nothing`() {
        assertNull(Wallpaper.pick(emptyList(), 0L, tenMin))
    }

    @Test fun `one picture shows at any time`() {
        assertEquals("a", Wallpaper.pick(listOf("a"), 0L, tenMin))
        assertEquals("a", Wallpaper.pick(listOf("a"), 7 * tenMin, tenMin))
    }

    @Test fun `the picture moves on once per period and wraps`() {
        val pics = listOf("a", "b", "c")
        assertEquals("a", Wallpaper.pick(pics, 0L, tenMin))
        assertEquals("a", Wallpaper.pick(pics, tenMin - 1, tenMin))
        assertEquals("b", Wallpaper.pick(pics, tenMin, tenMin))
        assertEquals("c", Wallpaper.pick(pics, 2 * tenMin, tenMin))
        assertEquals("a", Wallpaper.pick(pics, 3 * tenMin, tenMin))
    }
}
