package io.egate.remote.router

import io.egate.remote.protocol.AppItem
import io.egate.remote.protocol.Codec
import io.egate.remote.protocol.Command
import io.egate.remote.protocol.Event

/** One connected phone, regardless of transport. */
interface Session {
    /** Human-readable link description shown on the projector, e.g. "Wi-Fi 192.168.10.44". */
    val label: String
    fun send(json: String)
    fun close()
}

interface Credentials {
    val token: String
    fun matches(candidate: String): Boolean
    /**
     * Atomically checks [code] against the active pairing PIN. On success the PIN is consumed;
     * after a few failures it is invalidated. Returns true when the caller may be authenticated.
     */
    fun tryPair(code: String): Boolean
}

interface AppsProvider {
    fun list(): List<AppItem>
    fun launch(pkg: String): Boolean
    fun foregroundApp(): String?
    fun volume(): Pair<Int, Int>
    fun screenOn(): Boolean
}

class CommandRouter(
    private val creds: Credentials,
    private val injector: (String) -> String,
    private val apps: AppsProvider,
    private val deviceName: String,
    private val version: String,
    private val status: ConnectionStatus = ConnectionStatus(),
) {
    fun newConnection(session: Session) = Connection(session)

    inner class Connection(private val session: Session) {
        @Volatile var authenticated = false
            private set(value) {
                field = value
                if (value) status.onAuthenticated(session.label)
            }
        private var badAttempts = 0

        /** Transports call this when the underlying link goes away. */
        fun onClosed() { if (authenticated) { authenticated = false; status.onClosed(session.label) } }

        fun onMessage(json: String) {
            val cmd = Codec.decodeCommand(json)
            if (!authenticated) { handleUnauthenticated(cmd); return }
            val reply: Event? = when (cmd) {
                is Command.Key, is Command.Text, is Command.Mouse, is Command.Click, is Command.Scroll, is Command.Power -> {
                    val r = try { injector(json) } catch (e: Exception) { "{\"t\":\"err\",\"msg\":\"injector unavailable\"}" }
                    if (r.contains("\"err\"")) Event.Err(if (r.contains("unavailable")) "injector unavailable" else "inject failed") else null
                }
                is Command.Launch -> if (apps.launch(cmd.pkg)) null else Event.Err("cannot launch ${cmd.pkg}")
                Command.Apps -> Event.Apps(apps.list())
                Command.Status -> status()
                Command.Ping -> Event.Pong
                is Command.Auth, is Command.Pair -> Event.AuthOk(deviceName, version)
                is Command.Unknown -> Event.Err("unknown type: ${cmd.t}")
            }
            reply?.let { session.send(Codec.encodeEvent(it)) }
        }

        fun status(): Event.Status {
            val (vol, max) = apps.volume()
            return Event.Status(apps.foregroundApp(), apps.screenOn(), vol, max)
        }

        private fun handleUnauthenticated(cmd: Command) {
            when (cmd) {
                is Command.Auth -> if (creds.matches(cmd.token)) {
                    authenticated = true
                    session.send(Codec.encodeEvent(Event.AuthOk(deviceName, version)))
                    return
                } else fail(Event.AuthFail)
                is Command.Pair -> if (creds.tryPair(cmd.code)) {
                    authenticated = true
                    session.send(Codec.encodeEvent(Event.PairOk(creds.token, deviceName)))
                    return
                } else fail(Event.PairFail)
                else -> fail(Event.AuthFail)
            }
        }

        private fun fail(e: Event) {
            badAttempts++
            session.send(Codec.encodeEvent(e))
            if (badAttempts >= MAX_BAD_ATTEMPTS) session.close()
        }
    }

    companion object {
        const val MAX_BAD_ATTEMPTS = 3
    }
}
