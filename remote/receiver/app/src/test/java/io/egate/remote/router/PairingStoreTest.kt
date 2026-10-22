package io.egate.remote.router

import io.egate.remote.PairingStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemoryBacking : PairingStore.Backing {
    var value: String? = null
    override fun read(): String? = value
    override fun write(token: String) { value = token }
}

class PairingStoreTest {

    @Test
    fun `token is created once and persisted`() {
        val b = MemoryBacking()
        val t1 = PairingStore(b).token
        val t2 = PairingStore(b).token
        assertEquals(t1, t2)
        assertTrue(t1.length >= 43) // base64 of 32 bytes
    }

    @Test
    fun `regenerate changes the token`() {
        val s = PairingStore(MemoryBacking())
        val before = s.token
        val after = s.regenerate()
        assertNotEquals(before, after)
        assertEquals(after, s.token)
    }

    @Test
    fun `matches is exact`() {
        val s = PairingStore(MemoryBacking())
        assertTrue(s.matches(s.token))
        assertFalse(s.matches(s.token.dropLast(1)))
        assertFalse(s.matches(""))
    }

    @Test
    fun `newPin is six digits and becomes pairingPin`() {
        val s = PairingStore(MemoryBacking())
        val pin = s.newPin()
        assertTrue(pin.matches(Regex("[0-9]{6}")))
        assertEquals(pin, s.pairingPin)
    }
}
