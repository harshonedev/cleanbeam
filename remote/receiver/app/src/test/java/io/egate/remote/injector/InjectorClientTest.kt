package io.egate.remote.injector

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

private class FakeChannel(private val replyWith: String = "{\"t\":\"ok\"}") : InjectorClient.LineChannel {
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
