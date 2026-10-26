package com.cleanbeam.launcher.graphics

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

class WallpaperHintTest {
    @Test fun `empty folder asks for pictures and names the folder`() {
        assertEquals("No pictures yet. Copy some to:\n/x/wallpapers", Wallpaper.hint(0, "/x/wallpapers"))
    }

    @Test fun `a folder with pictures counts them and names the folder`() {
        assertEquals("1 picture, a new one every half hour. Add more at:\n/x/wallpapers", Wallpaper.hint(1, "/x/wallpapers"))
        assertEquals("3 pictures, a new one every half hour. Add more at:\n/x/wallpapers", Wallpaper.hint(3, "/x/wallpapers"))
    }
}
