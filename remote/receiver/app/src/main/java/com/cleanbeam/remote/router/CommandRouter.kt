package com.cleanbeam.remote.router

import com.cleanbeam.remote.protocol.AppItem
import com.cleanbeam.remote.protocol.Codec
import com.cleanbeam.remote.protocol.Command
import com.cleanbeam.remote.protocol.Event
import com.cleanbeam.remote.protocol.KeyAction
import com.cleanbeam.remote.protocol.ShortcutItem

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
    /** Projector/system screens this box can open, in display order. */
    fun shortcuts(): List<ShortcutItem> = emptyList()
    fun open(id: String): Boolean = false
    /** Current Wi-Fi IPv4 of the projector, so a phone on Bluetooth can find the faster link. */
    fun ip(): String? = null
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
        /** Keys this phone is holding down (volume ramp, D-pad scroll); released if the link drops. */
        private val held = LinkedHashSet<Int>()

        /** Transports call this when the underlying link goes away. */
        fun onClosed() {
            if (!authenticated) return
            authenticated = false
            status.onClosed(session.label)
            releaseHeldKeys()
        }

        private fun releaseHeldKeys() {
            val codes = synchronized(held) { held.toList().also { held.clear() } }
            for (code in codes) {
                try { injector("{\"t\":\"key\",\"code\":$code,\"action\":\"up\"}") } catch (_: Exception) {}
            }
        }

        fun onMessage(json: String) {
            val cmd = Codec.decodeCommand(json)
            if (!authenticated) { handleUnauthenticated(cmd); return }
            val reply: Event? = when (cmd) {
                is Command.Text -> if (cmd.s.length > MAX_TEXT_CHARS) Event.Err("text too long (max $MAX_TEXT_CHARS chars)") else inject(json)
                is Command.Key -> {
                    synchronized(held) { if (cmd.action == KeyAction.DOWN) held += cmd.code else held -= cmd.code }
                    inject(json)
                }
                is Command.Mouse, is Command.Click, is Command.Scroll, is Command.Power -> inject(json)
                is Command.Launch -> if (apps.launch(cmd.pkg)) null else Event.Err("cannot launch ${cmd.pkg}")
                Command.Apps -> Event.Apps(apps.list())
                Command.Shortcuts -> Event.Shortcuts(apps.shortcuts())
                is Command.Open -> if (apps.open(cmd.id)) null else Event.Err("cannot open ${cmd.id}")
                Command.Status -> status()
                Command.Ping -> Event.Pong
                is Command.Auth, is Command.Pair -> Event.AuthOk(deviceName, version, apps.ip())
                is Command.Unknown -> Event.Err("unknown type: ${cmd.t}")
            }
            reply?.let { session.send(Codec.encodeEvent(it)) }
        }

        private fun inject(json: String): Event? {
            val r = try { injector(json) } catch (e: Exception) { "{\"t\":\"err\",\"msg\":\"injector unavailable\"}" }
            return if (r.contains("\"err\"")) Event.Err(if (r.contains("unavailable")) "injector unavailable" else "inject failed") else null
        }

        fun status(): Event.Status {
            val (vol, max) = apps.volume()
            return Event.Status(apps.foregroundApp(), apps.screenOn(), vol, max, apps.ip())
        }

        private fun handleUnauthenticated(cmd: Command) {
            when (cmd) {
                is Command.Auth -> if (creds.matches(cmd.token)) {
                    authenticated = true
                    session.send(Codec.encodeEvent(Event.AuthOk(deviceName, version, apps.ip())))
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
        /** One phone message carries at most this much text; a paste is chunked by the phone. */
        const val MAX_TEXT_CHARS = 1000
    }
}
