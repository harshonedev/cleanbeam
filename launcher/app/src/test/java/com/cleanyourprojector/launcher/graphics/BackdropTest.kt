package com.cleanyourprojector.launcher.graphics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropTest {

    @Test fun `first picture loads`() {
        val b = Backdrop()
        assertEquals(Backdrop.Change.Load("res:1"), b.change("res:1"))
    }

    @Test fun `same picture again is a no-op`() {
        val b = Backdrop()
        b.change("res:1")
        assertEquals(Backdrop.Change.None, b.change("res:1"))
    }

    @Test fun `another picture loads`() {
        val b = Backdrop()
        b.change("res:1")
        assertEquals(Backdrop.Change.Load("res:2"), b.change("res:2"))
    }

    @Test fun `flat theme after a picture clears it`() {
        val b = Backdrop()
        b.change("res:1")
        assertEquals(Backdrop.Change.Clear, b.change(null))
    }

    @Test fun `flat theme with nothing shown is a no-op`() {
        val b = Backdrop()
        assertEquals(Backdrop.Change.None, b.change(null))
        b.change("res:1"); b.change(null)
        assertEquals(Backdrop.Change.None, b.change(null))
    }

    @Test fun `a load that was superseded is ignored`() {
        val b = Backdrop()
        b.change("res:1")
        b.change("res:2")
        assertTrue(b.isCurrent("res:2"))
        assertTrue(!b.isCurrent("res:1"))
    }
}
