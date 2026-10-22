package io.egate.remote.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import io.egate.remote.router.AppsHandler
import io.egate.remote.router.CommandRouter
import io.egate.remote.router.Session
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress

/** Wi-Fi transport: WebSocket on [port], app icons over HTTP on [iconPort], mDNS registration. */
class WsServer(
    private val ctx: Context,
    private val router: CommandRouter,
    private val apps: AppsHandler,
    private val port: Int = 7272,
    private val iconPort: Int = 7273,
) {
    private var ws: Server? = null
    private var http: IconServer? = null
    private var nsd: NsdManager? = null
    private var registration: NsdManager.RegistrationListener? = null

    fun start() {
        ws = Server(InetSocketAddress(port)).also { it.isReuseAddr = true; it.start() }
        http = IconServer(iconPort).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
        registerMdns()
        Log.i(TAG, "ws on :$port, icons on :$iconPort")
    }

    fun stop() {
        try { registration?.let { nsd?.unregisterService(it) } } catch (_: Exception) {}
        try { ws?.stop(500) } catch (_: Exception) {}
        try { http?.stop() } catch (_: Exception) {}
        ws = null; http = null
    }

    /** Push a status event to every authenticated client. */
    fun broadcast(json: String) {
        ws?.connections?.forEach { c ->
            val conn = c.getAttachment<CommandRouter.Connection>()
            if (conn != null && conn.authenticated && c.isOpen) c.send(json)
        }
    }

    private fun registerMdns() {
        val info = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) { Log.i(TAG, "mdns registered as ${i.serviceName}") }
            override fun onRegistrationFailed(i: NsdServiceInfo, code: Int) { Log.w(TAG, "mdns registration failed $code") }
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, code: Int) {}
        }
        nsd = (ctx.getSystemService(Context.NSD_SERVICE) as NsdManager).also {
            it.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        }
        registration = listener
    }

    private inner class Server(addr: InetSocketAddress) : WebSocketServer(addr) {
        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
            val session = object : Session {
                override fun send(json: String) { if (conn.isOpen) conn.send(json) }
                override fun close() { conn.close(1008, "auth") }
            }
            conn.setAttachment(router.newConnection(session))
            Log.i(TAG, "ws open from ${conn.remoteSocketAddress}")
        }
        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
            Log.i(TAG, "ws closed ${conn.remoteSocketAddress} ($code)")
        }
        override fun onMessage(conn: WebSocket, message: String) {
            conn.getAttachment<CommandRouter.Connection>()?.onMessage(message)
        }
        override fun onError(conn: WebSocket?, ex: Exception) { Log.w(TAG, "ws error", ex) }
        override fun onStart() { connectionLostTimeout = 30 }
    }

    private inner class IconServer(p: Int) : NanoHTTPD(p) {
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            if (!uri.startsWith("/icon/")) return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")
            val pkg = uri.removePrefix("/icon/")
            val png = apps.iconPng(pkg) ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "no icon")
            return newFixedLengthResponse(Response.Status.OK, "image/png", ByteArrayInputStream(png), png.size.toLong())
        }
    }

    companion object {
        private const val TAG = "EGateRemote"
        const val SERVICE_NAME = "EGate Atom3X"
        const val SERVICE_TYPE = "_egate-remote._tcp"
    }
}
