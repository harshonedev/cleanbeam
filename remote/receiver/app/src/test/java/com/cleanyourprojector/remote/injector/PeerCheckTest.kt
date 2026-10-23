package com.cleanyourprojector.remote.injector

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

class HelloTest {
    @Test
    fun `text containing hello is not treated as the handshake`() {
        // The handshake is only {"t":"hello"}; any other line is a normal command.
        assertEquals(Main.socketName(10075), "egate-injector-10075")
        val sink = object : InputInjector.Sink {
            override fun now() = 0L
            override fun displaySize() = 1280 to 720
            override fun key(code: Int, down: Boolean, downTime: Long, eventTime: Long, metaState: Int) = true
            override fun motion(action: Int, x: Float, y: Float, buttonState: Int, vScroll: Float) = true
            override fun exec(cmd: String) {}
        }
        val inj = InputInjector(sink, object : InputInjector.CharMapper { override fun map(c: Char) = listOf(InputInjector.KeyStroke(29, 0)) })
        assertEquals("{\"t\":\"ok\"}", Main.handle("{\"t\":\"text\",\"s\":\"hello\"}", inj))
        assertTrue(Main.handle("{\"t\":\"hello\"}", inj).startsWith("{\"t\":\"hello_ok\""))
    }
}
