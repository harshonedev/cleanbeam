package io.egate.remote.injector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerCheckTest {
    @Test
    fun `helper accepts only the receiver app uid`() {
        assertTrue(Main.isAllowedPeer(10123, 10123))
        assertFalse(Main.isAllowedPeer(10124, 10123))
        assertFalse(Main.isAllowedPeer(-1, 10123))
    }

    @Test
    fun `helper requires --uid argument`() {
        assertEquals(10123, Main.parseUid(arrayOf("--uid", "10123")))
        assertEquals(10123, Main.parseUid(arrayOf("selftest", "--uid", "10123")))
        assertNull(Main.parseUid(arrayOf()))
        assertNull(Main.parseUid(arrayOf("--uid")))
        assertNull(Main.parseUid(arrayOf("--uid", "x")))
    }

    @Test
    fun `client trusts only root or shell helper`() {
        assertTrue(InjectorClient.isTrustedHelperUid(0))
        assertTrue(InjectorClient.isTrustedHelperUid(2000))
        assertFalse(InjectorClient.isTrustedHelperUid(10123))
    }
}
