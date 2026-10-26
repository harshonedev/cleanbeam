package com.cleanbeam.launcher.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenLabelTest {
    @Test
    fun `exact hints do not swallow a longer label`() {
        assertTrue(matchesScreenLabel("Focus", listOf("=focus", "对焦")))
        assertTrue(matchesScreenLabel("对焦", listOf("=focus", "对焦")))
        assertFalse(matchesScreenLabel("Input method", listOf("=input", "input source")))
        assertTrue(matchesScreenLabel("Input source", listOf("=input", "input source")))
        assertTrue(matchesScreenLabel("Keystone correction", listOf("keystone")))
        assertFalse(matchesScreenLabel("", listOf("focus")))
    }
}
