package com.cleanbeam.remote.router

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenLabelTest {
    @Test
    fun `exact hints do not swallow a longer label`() {
        assertTrue(matchesScreenLabel("Focus", listOf("=focus", "对焦")))
        assertFalse(matchesScreenLabel("Input method", listOf("=input", "input source")))
        assertTrue(matchesScreenLabel("HDMI", listOf("hdmi")))
    }
}
