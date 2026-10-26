package com.cleanbeam.remote.injector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvdevMouseTest {
    @Test
    fun `relative pointer bits live in the last capability word`() {
        assertTrue(isRelativePointer("103"))
        assertTrue(isRelativePointer("0 3"))
        assertFalse(isRelativePointer("100"))
        assertFalse(isRelativePointer(""))
        assertFalse(isRelativePointer("not-hex"))
    }

    @Test
    fun `ranking prefers a named mouse over any other relative device`() {
        assertEquals(0, pointerRank("jixin_mouse", "103"))
        assertEquals(1, pointerRank("Bluetooth Mouse", "103"))
        assertEquals(1, pointerRank("Vendor Mouse", ""))
        assertEquals(2, pointerRank("adc-rel", "3"))
        assertNull(pointerRank("gpio-keys", "0"))
        assertNull(pointerRank("ir_keypad", "103"))
        assertNull(pointerRank("goodix-ts", "0"))
    }
}
