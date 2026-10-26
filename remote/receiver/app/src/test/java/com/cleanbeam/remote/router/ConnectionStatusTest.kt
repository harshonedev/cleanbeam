package com.cleanbeam.remote.router

import com.cleanbeam.remote.protocol.AppItem
import org.junit.Assert.assertEquals
import org.junit.Test

private class LabelledSession(override val label: String) : Session {
    val sent = mutableListOf<String>()
    override fun send(json: String) { sent += json }
    override fun close() {}
}

class ConnectionStatusTest {
    private val status = ConnectionStatus()
    private val creds = object : Credentials {
        override val token = "TOKEN"
        override fun matches(candidate: String) = candidate == token
        override fun tryPair(code: String) = false
    }
    private val apps = object : AppsProvider {
        override fun list() = emptyList<AppItem>()
        override fun launch(pkg: String) = true
        override fun foregroundApp(): String? = null
        override fun volume() = 0 to 1
        override fun screenOn() = true
    }
    private val router = CommandRouter(creds, { "{\"t\":\"ok\"}" }, apps, "n", "1", status)

    @Test
    fun `authenticated sessions appear in the status and leave on close`() {
        val s = LabelledSession("Wi-Fi 192.168.10.44")
        val c = router.newConnection(s)
        assertEquals(emptyList<String>(), status.clients())
        c.onMessage("""{"t":"auth","token":"TOKEN"}""")
        assertEquals(listOf("Wi-Fi 192.168.10.44"), status.clients())
        c.onClosed()
        assertEquals(emptyList<String>(), status.clients())
    }

    @Test
    fun `unauthenticated sessions never appear`() {
        val s = LabelledSession("Bluetooth AA:BB")
        val c = router.newConnection(s)
        c.onMessage("""{"t":"auth","token":"wrong"}""")
        assertEquals(emptyList<String>(), status.clients())
        c.onClosed()
    }

    @Test
    fun `an unauthenticated connection closing does not clear an authenticated one with the same label`() {
        val a = router.newConnection(LabelledSession("Wi-Fi 192.168.10.44"))
        a.onMessage("""{"t":"auth","token":"TOKEN"}""")
        val b = router.newConnection(LabelledSession("Wi-Fi 192.168.10.44"))
        b.onMessage("""{"t":"auth","token":"wrong"}""")
        b.onClosed()
        assertEquals(listOf("Wi-Fi 192.168.10.44"), status.clients())
        a.onClosed()
        assertEquals(emptyList<String>(), status.clients())
    }

    @Test
    fun `two authenticated connections with one label stay listed until both close`() {
        val a = router.newConnection(LabelledSession("Bluetooth X"))
        val b = router.newConnection(LabelledSession("Bluetooth X"))
        a.onMessage("""{"t":"auth","token":"TOKEN"}""")
        b.onMessage("""{"t":"auth","token":"TOKEN"}""")
        a.onClosed()
        assertEquals(listOf("Bluetooth X"), status.clients())
        b.onClosed()
        assertEquals(emptyList<String>(), status.clients())
    }

    @Test
    fun `summary text reads naturally`() {
        assertEquals("No phone connected", status.summary())
        status.bluetooth = "advertising"
        val s = LabelledSession("Wi-Fi 192.168.10.44")
        router.newConnection(s).onMessage("""{"t":"auth","token":"TOKEN"}""")
        assertEquals("Connected: Wi-Fi 192.168.10.44", status.summary())
    }
}
