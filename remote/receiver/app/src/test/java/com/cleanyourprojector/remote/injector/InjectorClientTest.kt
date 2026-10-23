package io.egate.remote.injector

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

private open class FakeChannel(private val replyWith: String = "{\"t\":\"ok\"}") : InjectorClient.LineChannel {
    var closed = false
    var failNext = false
    val sent = mutableListOf<String>()
    override fun send(line: String): String {
        if (closed || failNext) { failNext = false; closed = true; throw IOException("socket closed") }
        sent += line
        return replyWith
    }
    override fun close() { closed = true }
}

private class FakeEnv {
    var spawnCount = 0
    var connectAttemptsUntilUp = 0   // how many connect() calls return null before the "helper" is up
    val channels = mutableListOf<FakeChannel>()
    val spawner = object : InjectorClient.Spawner { override fun spawn() { spawnCount++; connectAttemptsUntilUp = 0 } }
    val connector = object : InjectorClient.Connector {
        override fun connect(): InjectorClient.LineChannel? {
            if (spawnCount == 0 && connectAttemptsUntilUp >= 0) { return null }
            return FakeChannel().also { channels += it }
        }
    }
    fun client() = InjectorClient(spawner, connector, retryDelayMs = 0, spawnTimeoutMs = 50)
}

class InjectorClientTest {

    @Test
    fun `spawns when connect refused then connects`() {
        val env = FakeEnv()
        val reply = env.client().send("{\"t\":\"key\"}")
        assertEquals("{\"t\":\"ok\"}", reply)
        assertEquals(1, env.spawnCount)
        assertEquals(listOf("{\"t\":\"key\"}"), env.channels.single().sent)
    }

    @Test
    fun `does not spawn when already connected`() {
        val env = FakeEnv()
        val c = env.client()
        c.send("a"); c.send("b")
        assertEquals(1, env.spawnCount)
        assertEquals(1, env.channels.size)
        assertEquals(listOf("a", "b"), env.channels[0].sent)
    }

    @Test
    fun `respawns helper when socket closed`() {
        val env = FakeEnv()
        val c = env.client()
        c.send("a")
        env.channels[0].failNext = true
        env.spawnCount = 0 // simulate the helper dying: connector refuses until spawned again
        val reply = c.send("b")
        assertEquals("{\"t\":\"ok\"}", reply)
        assertEquals(1, env.spawnCount)
        assertEquals(2, env.channels.size)
        assertEquals(listOf("b"), env.channels[1].sent)
    }

    @Test
    fun `throws when helper never comes up`() {
        val env = FakeEnv()
        val stubbornSpawner = object : InjectorClient.Spawner { override fun spawn() {} }
        val c = InjectorClient(stubbornSpawner, env.connector, retryDelayMs = 0, spawnTimeoutMs = 20)
        try {
            c.send("a")
            throw AssertionError("expected IOException")
        } catch (e: IOException) {
            // expected
        }
    }
}

class InjectorTimeoutTest {
    @Test
    fun `a read timeout drops the channel and does not replay the command`() {
        var connects = 0
        val channels = mutableListOf<FakeChannel>()
        var timeoutsLeft = 1
        val connector = object : InjectorClient.Connector {
            override fun connect(): InjectorClient.LineChannel? { connects++; return TimeoutOnce { timeoutsLeft-- > 0 }.also { channels += it } }
        }
        val c = InjectorClient(object : InjectorClient.Spawner { override fun spawn() {} }, connector, retryDelayMs = 0, spawnTimeoutMs = 0)
        try { c.send("slow"); throw AssertionError("expected IOException") } catch (e: IOException) {
            assertEquals(true, e.message!!.contains("timed out"))
        }
        assertEquals(1, connects)                       // no immediate reconnect/replay
        assertEquals(listOf("slow"), channels[0].sent)  // sent once only
        assertEquals(true, channels[0].closed)
        assertEquals("{\"t\":\"ok\"}", c.send("next")) // lazily reconnects for the next command
        assertEquals(2, connects)
    }

    private class TimeoutOnce(private val shouldTimeout: () -> Boolean) : FakeChannel() {
        override fun send(line: String): String {
            if (shouldTimeout()) { sent += line; throw java.net.SocketTimeoutException("read timed out") }
            return super.send(line)
        }
    }
}

class InjectorCooldownTest {
    @Test
    fun `after a failed spawn, sends fail fast until the cooldown passes`() {
        var now = 0L
        var connectCalls = 0
        val connector = object : InjectorClient.Connector { override fun connect(): InjectorClient.LineChannel? { connectCalls++; return null } }
        val c = InjectorClient(object : InjectorClient.Spawner { override fun spawn() {} }, connector,
            retryDelayMs = 0, spawnTimeoutMs = 0, cooldownMs = 10_000, clock = { now })
        try { c.send("a"); throw AssertionError() } catch (e: IOException) {}
        val callsAfterFirst = connectCalls
        now = 5_000
        try { c.send("b"); throw AssertionError() } catch (e: IOException) { assertEquals(true, e.message!!.contains("cooling down")) }
        assertEquals(callsAfterFirst, connectCalls) // no connect attempt during cooldown
        now = 11_000
        try { c.send("c") } catch (e: IOException) {}
        assertEquals(true, connectCalls > callsAfterFirst)
    }
}
