package io.egate.remote.router

import io.egate.remote.protocol.AppItem
import io.egate.remote.protocol.Codec
import io.egate.remote.protocol.Command
import io.egate.remote.protocol.Event

/** One connected phone, regardless of transport. */
interface Session {
    fun send(json: String)
    fun close()
}

interface Credentials {
    val token: String
    val pairingPin: String?
    fun matches(candidate: String): Boolean
    /** Called on every wrong PIN; implementations invalidate the PIN after a few failures. */
    fun onPairFailed()
    /** Called after a successful pair; the PIN is single-use. */
    fun onPaired()
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
) {
    fun newConnection(session: Session) = Connection(session)

    inner class Connection(private val session: Session) {
        @Volatile var authenticated = false
            private set
        private var badAttempts = 0

        fun onMessage(json: String) {
            val cmd = Codec.decodeCommand(json)
            if (!authenticated) { handleUnauthenticated(cmd); return }
            val reply: Event? = when (cmd) {
                is Command.Key, is Command.Text, is Command.Mouse, is Command.Click, is Command.Scroll, is Command.Power -> {
                    val r = injector(json)
                    if (r.contains("\"err\"")) Event.Err("inject failed") else null
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
                is Command.Pair -> {
                    val pin = creds.pairingPin
                    if (pin != null && cmd.code == pin) {
                        authenticated = true
                        creds.onPaired()
                        session.send(Codec.encodeEvent(Event.PairOk(creds.token, deviceName)))
                        return
                    } else {
                        creds.onPairFailed()
                        fail(Event.PairFail)
                    }
                }
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
