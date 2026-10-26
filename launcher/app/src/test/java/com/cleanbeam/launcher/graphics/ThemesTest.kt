package com.cleanbeam.launcher.graphics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemesTest {

    @Test fun `names are distinct`() {
        assertEquals(Themes.ALL.size, Themes.ALL.map { it.name }.toSet().size)
    }

    @Test fun `default is the flat midnight theme`() {
        val t = Themes.ALL[Themes.DEFAULT]
        assertEquals("Midnight", t.name)
        assertFalse(t.hasPicture)
        assertFalse(t.glass)
    }

    @Test fun `every picture theme uses glass cards`() {
        for (t in Themes.ALL.filter { it.hasPicture }) assertTrue(t.name, t.glass)
    }

    @Test fun `themes are found by name`() {
        assertEquals(0, Themes.indexOf("Midnight"))
        assertEquals(-1, Themes.indexOf("Nope"))
        assertEquals(-1, Themes.indexOf(null))
    }

    @Test fun `exactly one theme reads the user's own pictures`() {
        assertEquals(1, Themes.ALL.count { it.userWallpaper })
    }
}
