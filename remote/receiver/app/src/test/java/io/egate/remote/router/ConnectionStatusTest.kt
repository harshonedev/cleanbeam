package io.egate.remote.router

import io.egate.remote.protocol.AppItem
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
    fun `summary text reads naturally`() {
        assertEquals("No phone connected", status.summary())
        status.bluetooth = "advertising"
        val s = LabelledSession("Wi-Fi 192.168.10.44")
        router.newConnection(s).onMessage("""{"t":"auth","token":"TOKEN"}""")
        assertEquals("Connected: Wi-Fi 192.168.10.44", status.summary())
    }
}
