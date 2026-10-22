package io.egate.remote.router

import io.egate.remote.protocol.AppItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeSession : Session {
    val sent = mutableListOf<String>()
    var closed = false
    override fun send(json: String) { sent += json }
    override fun close() { closed = true }
}

private class FakeApps : AppsProvider {
    val launched = mutableListOf<String>()
    override fun list() = listOf(AppItem("com.google.android.youtube.tv", "YouTube"))
    override fun launch(pkg: String): Boolean { launched += pkg; return true }
    override fun foregroundApp(): String? = "io.egate.launcher"
    override fun volume(): Pair<Int, Int> = 7 to 15
    override fun screenOn(): Boolean = true
}

private class FakeCredentials(override val token: String = "TOKEN") : Credentials {
    var pairingPin: String? = null
    var pairFailures = 0
    override fun matches(candidate: String): Boolean = candidate == token
    override fun tryPair(code: String): Boolean {
        val pin = pairingPin ?: return false
        if (code == pin) { pairingPin = null; return true }
        if (++pairFailures >= 5) pairingPin = null
        return false
    }
}

class CommandRouterTest {

    private val injected = mutableListOf<String>()
    private val creds = FakeCredentials()
    private val apps = FakeApps()
    private val router = CommandRouter(creds, { json -> injected += json; "{\"t\":\"ok\"}" }, apps, "EGate Atom3X", "1.0")

    private fun authed(): Pair<CommandRouter.Connection, FakeSession> {
        val s = FakeSession()
        val c = router.newConnection(s)
        c.onMessage("""{"t":"auth","token":"TOKEN"}""")
        return c to s
    }

    @Test
    fun `drops commands before auth`() {
        val s = FakeSession()
        val c = router.newConnection(s)
        c.onMessage("""{"t":"key","code":20,"action":"tap"}""")
        assertTrue(injected.isEmpty())
        assertEquals(listOf("""{"t":"auth_fail"}"""), s.sent)
    }

    @Test
    fun `closes after 3 bad auth`() {
        val s = FakeSession()
        val c = router.newConnection(s)
        repeat(2) { c.onMessage("""{"t":"auth","token":"wrong"}""") }
        assertTrue(!s.closed)
        c.onMessage("""{"t":"auth","token":"wrong"}""")
        assertTrue(s.closed)
    }

    @Test
    fun `auth_ok then forwards key json to injector`() {
        val (c, s) = authed()
        assertEquals("""{"t":"auth_ok","name":"EGate Atom3X","version":"1.0"}""", s.sent[0])
        c.onMessage("""{"t":"key","code":20,"action":"tap"}""")
        assertEquals(listOf("""{"t":"key","code":20,"action":"tap"}"""), injected)
    }

    @Test
    fun `pair with current pin returns token`() {
        creds.pairingPin = "482913"
        val s = FakeSession()
        val c = router.newConnection(s)
        c.onMessage("""{"t":"pair","code":"482913"}""")
        assertEquals("""{"t":"pair_ok","token":"TOKEN","name":"EGate Atom3X"}""", s.sent[0])
        c.onMessage("""{"t":"ping"}""")
        assertEquals("""{"t":"pong"}""", s.sent[1])
    }

    @Test
    fun `pin is cleared after a successful pair`() {
        creds.pairingPin = "482913"
        val s = FakeSession()
        router.newConnection(s).onMessage("""{"t":"pair","code":"482913"}""")
        assertEquals(null, creds.pairingPin)
    }

    @Test
    fun `pin is invalidated after five failed attempts across connections`() {
        creds.pairingPin = "482913"
        repeat(5) {
            val s = FakeSession()
            router.newConnection(s).onMessage("""{"t":"pair","code":"000000"}""")
        }
        assertEquals(null, creds.pairingPin)
        val s = FakeSession()
        router.newConnection(s).onMessage("""{"t":"pair","code":"482913"}""")
        assertEquals(listOf("""{"t":"pair_fail"}"""), s.sent)
    }

    @Test
    fun `pair fails when no pin active`() {
        val s = FakeSession()
        val c = router.newConnection(s)
        c.onMessage("""{"t":"pair","code":"482913"}""")
        assertEquals(listOf("""{"t":"pair_fail"}"""), s.sent)
    }

    @Test
    fun `apps returns list event and launch launches`() {
        val (c, s) = authed()
        c.onMessage("""{"t":"apps"}""")
        assertEquals("""{"t":"apps","items":[{"pkg":"com.google.android.youtube.tv","label":"YouTube"}]}""", s.sent[1])
        c.onMessage("""{"t":"launch","pkg":"org.videolan.vlc"}""")
        assertEquals(listOf("org.videolan.vlc"), apps.launched)
    }

    @Test
    fun `status and ping`() {
        val (c, s) = authed()
        c.onMessage("""{"t":"status"}""")
        c.onMessage("""{"t":"ping"}""")
        assertEquals("""{"t":"status","app":"io.egate.launcher","screenOn":true,"volume":7,"maxVolume":15}""", s.sent[1])
        assertEquals("""{"t":"pong"}""", s.sent[2])
    }

    @Test
    fun `unknown type yields err`() {
        val (c, s) = authed()
        c.onMessage("""{"t":"foo"}""")
        assertTrue(s.sent[1].startsWith("""{"t":"err""""))
    }
}
