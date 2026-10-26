package com.cleanbeam.launcher

import com.cleanbeam.launcher.graphics.Themes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EditionTest {
    @Test fun `lite has no ambient theme`() {
        assertFalse(Edition.AMBIENT)
        assertEquals("lite", Edition.NAME)
        assertEquals(listOf("Midnight", "Aurora", "Nebula", "Ruska", "Graphite", "Pure black", "My wallpaper"),
            Themes.ALL.map { it.name })
    }

    @Test fun `an Ambient choice saved by Full falls back to the default`() {
        assertEquals(-1, Themes.indexOf("Ambient"))
        assertEquals("Midnight", Themes.ALL[Themes.DEFAULT].name)
    }
}
