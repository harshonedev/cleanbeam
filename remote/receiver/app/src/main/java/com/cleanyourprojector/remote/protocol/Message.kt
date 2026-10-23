package io.egate.remote.protocol

import org.json.JSONException
import org.json.JSONObject

enum class KeyAction { TAP, DOWN, UP;
    companion object {
        fun from(s: String?): KeyAction = when (s) { "down" -> DOWN; "up" -> UP; else -> TAP }
    }
}

data class AppItem(val pkg: String, val label: String)

/** A projector/system screen the phone can open directly ("Input source", "Wi-Fi", ...). */
data class ShortcutItem(val id: String, val label: String, val group: String)

/** Phone → projector. */
sealed class Command {
    data class Auth(val token: String) : Command()
    data class Pair(val code: String) : Command()
    data class Key(val code: Int, val action: KeyAction) : Command()
    data class Text(val s: String) : Command()
    data class Mouse(val dx: Int, val dy: Int) : Command()
    data class Click(val button: String) : Command()
    data class Scroll(val dy: Int) : Command()
    data class Launch(val pkg: String) : Command()
    object Apps : Command()
    data class Power(val action: String) : Command()
    object Shortcuts : Command()
    data class Open(val id: String) : Command()
    object Status : Command()
    object Ping : Command()
    data class Unknown(val t: String) : Command()
}

/** Projector → phone. */
sealed class Event {
    data class AuthOk(val name: String, val version: String, val ip: String? = null) : Event()
    object AuthFail : Event()
    data class PairOk(val token: String, val name: String) : Event()
    object PairFail : Event()
    data class Apps(val items: List<AppItem>) : Event()
    data class Status(val app: String?, val screenOn: Boolean, val volume: Int, val maxVolume: Int, val ip: String? = null) : Event()
    data class Shortcuts(val items: List<ShortcutItem>) : Event()
    object Pong : Event()
    data class Err(val msg: String) : Event()
}

object Codec {

    fun decodeCommand(json: String): Command {
        val o = try { JSONObject(json) } catch (e: JSONException) { return Command.Unknown("") }
        val t = o.optString("t", "")
        return when (t) {
            "auth" -> Command.Auth(o.optString("token", ""))
            "pair" -> Command.Pair(o.optString("code", ""))
            "key" -> Command.Key(o.optInt("code", 0), KeyAction.from(o.optString("action", "tap")))
            "text" -> Command.Text(o.optString("s", ""))
            "mouse" -> Command.Mouse(o.optInt("dx", 0), o.optInt("dy", 0))
            "click" -> Command.Click(o.optString("button", "left"))
            "scroll" -> Command.Scroll(o.optInt("dy", 0))
            "launch" -> Command.Launch(o.optString("pkg", ""))
            "apps" -> Command.Apps
            "power" -> Command.Power(o.optString("action", "toggle"))
            "shortcuts" -> Command.Shortcuts
            "open" -> Command.Open(o.optString("id", ""))
            "status" -> Command.Status
            "ping" -> Command.Ping
            else -> Command.Unknown(t)
        }
    }

    fun encodeEvent(e: Event): String {
        val sb = StringBuilder("{\"t\":")
        when (e) {
            is Event.AuthOk -> {
                sb.append(q("auth_ok")).append(",\"name\":").append(q(e.name)).append(",\"version\":").append(q(e.version))
                e.ip?.let { sb.append(",\"ip\":").append(q(it)) }
            }
            Event.AuthFail -> sb.append(q("auth_fail"))
            is Event.PairOk -> sb.append(q("pair_ok")).append(",\"token\":").append(q(e.token)).append(",\"name\":").append(q(e.name))
            Event.PairFail -> sb.append(q("pair_fail"))
            is Event.Apps -> {
                sb.append(q("apps")).append(",\"items\":[")
                e.items.forEachIndexed { idx, i ->
                    if (idx > 0) sb.append(',')
                    sb.append("{\"pkg\":").append(q(i.pkg)).append(",\"label\":").append(q(i.label)).append('}')
                }
                sb.append(']')
            }
            is Event.Status -> sb.append(q("status"))
                .append(",\"app\":").append(if (e.app == null) "null" else q(e.app))
                .append(",\"screenOn\":").append(e.screenOn)
                .append(",\"volume\":").append(e.volume)
                .append(",\"maxVolume\":").append(e.maxVolume)
                .also { b -> e.ip?.let { b.append(",\"ip\":").append(q(it)) } }
            is Event.Shortcuts -> {
                sb.append(q("shortcuts")).append(",\"items\":[")
                e.items.forEachIndexed { idx, i ->
                    if (idx > 0) sb.append(',')
                    sb.append("{\"id\":").append(q(i.id)).append(",\"label\":").append(q(i.label)).append(",\"group\":").append(q(i.group)).append('}')
                }
                sb.append(']')
            }
            Event.Pong -> sb.append(q("pong"))
            is Event.Err -> sb.append(q("err")).append(",\"msg\":").append(q(e.msg))
        }
        return sb.append('}').toString()
    }

    private fun q(s: String): String = JSONObject.quote(s)
}
