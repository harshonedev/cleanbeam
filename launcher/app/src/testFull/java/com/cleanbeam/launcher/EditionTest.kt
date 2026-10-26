package com.cleanbeam.launcher

import com.cleanbeam.launcher.graphics.Themes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditionTest {
    @Test fun `full has the ambient theme`() {
        assertTrue(Edition.AMBIENT)
        assertEquals("full", Edition.NAME)
        assertEquals(1, Themes.ALL.count { it.dynamic })
        assertEquals("Ambient", Themes.ALL[1].name)
    }
}
