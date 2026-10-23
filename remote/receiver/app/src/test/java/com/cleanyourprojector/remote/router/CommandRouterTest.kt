package com.cleanyourprojector.remote.router

import com.cleanyourprojector.remote.protocol.AppItem
import com.cleanyourprojector.remote.protocol.ShortcutItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeSession : Session {
    override val label = "test"
    val sent = mutableListOf<String>()
    var closed = false
    override fun send(json: String) { sent += json }
    override fun close() { closed = true }
}

private class FakeApps : AppsProvider {
    val launched = mutableListOf<String>()
    override fun list() = listOf(AppItem("com.google.android.youtube.tv", "YouTube"))
    override fun launch(pkg: String): Boolean { launched += pkg; return true }
    override fun foregroundApp(): String? = "com.cleanyourprojector.launcher"
    override fun volume(): Pair<Int, Int> = 7 to 15
    override fun screenOn(): Boolean = true
    val opened = mutableListOf<String>()
    var ipValue: String? = null
    override fun shortcuts() = listOf(ShortcutItem("input", "Input source", "Projector"), ShortcutItem("wifi", "Wi-Fi", "Network"))
    override fun open(id: String): Boolean { if (id != "input" && id != "wifi") return false; opened += id; return true }
    override fun ip(): String? = ipValue
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
        assertEquals("""{"t":"status","app":"com.cleanyourprojector.launcher","screenOn":true,"volume":7,"maxVolume":15}""", s.sent[1])
        assertEquals("""{"t":"pong"}""", s.sent[2])
    }

    @Test
    fun `shortcuts lists what the box can open and open fires it`() {
        val (c, s) = authed()
        c.onMessage("""{"t":"shortcuts"}""")
        assertEquals("""{"t":"shortcuts","items":[{"id":"input","label":"Input source","group":"Projector"},{"id":"wifi","label":"Wi-Fi","group":"Network"}]}""", s.sent[1])
        c.onMessage("""{"t":"open","id":"input"}""")
        assertEquals(listOf("input"), apps.opened)
        assertEquals(2, s.sent.size) // success is silent
        c.onMessage("""{"t":"open","id":"nope"}""")
        assertEquals("""{"t":"err","msg":"cannot open nope"}""", s.sent[2])
    }

    @Test
    fun `auth_ok and status carry the projector ip when known`() {
        apps.ipValue = "192.168.10.56"
        val (c, s) = authed()
        assertEquals("""{"t":"auth_ok","name":"EGate Atom3X","version":"1.0","ip":"192.168.10.56"}""", s.sent[0])
        c.onMessage("""{"t":"status"}""")
        assertEquals("""{"t":"status","app":"com.cleanyourprojector.launcher","screenOn":true,"volume":7,"maxVolume":15,"ip":"192.168.10.56"}""", s.sent[1])
    }

    @Test
    fun `over-long text is refused without reaching the injector`() {
        val (c, s) = authed()
        val long = "a".repeat(CommandRouter.MAX_TEXT_CHARS + 1)
        c.onMessage("""{"t":"text","s":"$long"}""")
        assertTrue(injected.isEmpty())
        assertTrue(s.sent[1].startsWith("""{"t":"err","msg":"text too long"""))
        c.onMessage("""{"t":"text","s":"${"a".repeat(CommandRouter.MAX_TEXT_CHARS)}"}""")
        assertEquals(1, injected.size)
    }

    @Test
    fun `keys still held when the link drops are released`() {
        val (c, _) = authed()
        c.onMessage("""{"t":"key","code":24,"action":"down"}""")
        c.onMessage("""{"t":"key","code":20,"action":"down"}""")
        c.onMessage("""{"t":"key","code":20,"action":"up"}""")
        c.onMessage("""{"t":"key","code":23,"action":"tap"}""")
        injected.clear()
        c.onClosed()
        assertEquals(listOf("""{"t":"key","code":24,"action":"up"}"""), injected)
        c.onClosed() // idempotent
        assertEquals(1, injected.size)
    }

    @Test
    fun `unknown type yields err`() {
        val (c, s) = authed()
        c.onMessage("""{"t":"foo"}""")
        assertTrue(s.sent[1].startsWith("""{"t":"err""""))
    }
}

class RouterInjectorFailureTest {
    @Test
    fun `injector exception becomes an err event instead of escaping`() {
        val creds = FakeCredentials()
        val router = CommandRouter(creds, { throw java.io.IOException("down") }, FakeApps(), "n", "1")
        val s = FakeSession()
        val c = router.newConnection(s)
        c.onMessage("""{"t":"auth","token":"TOKEN"}""")
        c.onMessage("""{"t":"key","code":20,"action":"tap"}""")
        assertEquals("""{"t":"err","msg":"injector unavailable"}""", s.sent[1])
    }
}
