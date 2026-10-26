package com.cleanbeam.remote.adb

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Minimal ADB host-side client that talks to the local adbd over TCP.
 * Works only against an adbd with ro.adb.secure=0 (no AUTH handshake), which is how most
 * uncertified Android projectors ship. Certified devices that demand pairing are refused.
 */
class AdbClient(private val host: String = "127.0.0.1", private val port: Int = 5555) : Closeable {

    companion object {
        /** AOSP tcp port, then the port used by a lot of H713 / Allwinner projector boards. */
        val FALLBACK_PORTS = listOf(5555, 3333)

        /**
         * The port adbd is actually listening on, when the property is readable, then [FALLBACK_PORTS].
         * `service.adb.tcp.port` wins over the persisted value because `adb tcpip` changes it at runtime.
         */
        fun portsToTry(advertised: Int?): List<Int> {
            val out = LinkedHashSet<Int>()
            if (advertised != null && advertised in 1..65535) out.add(advertised)
            out.addAll(FALLBACK_PORTS)
            return out.toList()
        }

        /** Connects to the first local adbd port that answers without an AUTH challenge. */
        fun connectAny(): AdbClient {
            val ports = portsToTry(tcpPortProperty())
            var last: Exception? = null
            for (p in ports) {
                val c = AdbClient(port = p)
                try { c.connect(timeoutMs = 1500); return c } catch (e: Exception) { last = e; c.close() }
            }
            throw IOException("no local adbd on $ports (needs an adbd with authentication off)", last)
        }

        private fun tcpPortProperty(): Int? {
            return try {
                val cls = Class.forName("android.os.SystemProperties")
                val get = cls.getMethod("get", String::class.java, String::class.java)
                var found: Int? = null
                for (key in listOf("service.adb.tcp.port", "persist.adb.tcp.port")) {
                    val n = (get.invoke(null, key, "") as String).trim().toIntOrNull()
                    if (n != null && n > 0) { found = n; break }
                }
                found
            } catch (_: Throwable) {
                null
            }
        }
    }

    private var socket: Socket? = null
    private lateinit var input: BufferedInputStream
    private lateinit var output: BufferedOutputStream
    private var nextLocalId = 1

    fun connect(timeoutMs: Int = 3000) {
        val s = Socket()
        s.connect(InetSocketAddress(host, port), timeoutMs)
        s.soTimeout = 15_000
        socket = s
        input = BufferedInputStream(s.getInputStream())
        output = BufferedOutputStream(s.getOutputStream())
        send(AdbPacket(AdbPacket.A_CNXN, AdbPacket.VERSION, AdbPacket.MAX_PAYLOAD, "host::\u0000".toByteArray()))
        val reply = AdbPacket.decode(input)
        when (reply.cmd) {
            AdbPacket.A_CNXN -> return
            AdbPacket.A_AUTH -> throw IOException("adbd requires AUTH (ro.adb.secure=1); unsupported")
            else -> throw IOException("unexpected adb reply 0x${Integer.toHexString(reply.cmd)}")
        }
    }

    /** Run a shell command (legacy `shell:` service, pty-backed) and return its output once the stream closes. */
    fun shell(cmd: String, timeoutMs: Long = 10_000): String = run("shell:$cmd", timeoutMs)

    /**
     * Run a command through the `exec:` service: no pty, plain pipes. Use this to start background
     * processes that must outlive the stream; with `shell:` adbd's pty teardown kills them.
     */
    fun exec(cmd: String, timeoutMs: Long = 10_000): String = run("exec:$cmd", timeoutMs)

    private fun run(destination: String, timeoutMs: Long): String {
        val localId = open(destination)
        val out = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        socket?.soTimeout = timeoutMs.toInt()
        while (System.currentTimeMillis() < deadline) {
            val p = AdbPacket.decode(input)
            when (p.cmd) {
                AdbPacket.A_WRTE -> {
                    out.append(String(p.payload))
                    send(AdbPacket(AdbPacket.A_OKAY, localId, p.arg0, ByteArray(0)))
                }
                AdbPacket.A_CLSE -> {
                    send(AdbPacket(AdbPacket.A_CLSE, localId, p.arg0, ByteArray(0)))
                    return out.toString()
                }
                else -> { /* ignore OKAY etc. */ }
            }
        }
        return out.toString()
    }

    /** Sends OPEN and waits for the matching OKAY; returns our local stream id. */
    private fun open(destination: String): Int {
        val localId = nextLocalId++
        send(AdbPacket(AdbPacket.A_OPEN, localId, 0, (destination + "\u0000").toByteArray()))
        while (true) {
            val p = AdbPacket.decode(input)
            when (p.cmd) {
                AdbPacket.A_OKAY -> if (p.arg1 == localId) return localId
                AdbPacket.A_CLSE -> throw IOException("adbd refused stream: $destination")
                AdbPacket.A_WRTE -> send(AdbPacket(AdbPacket.A_OKAY, p.arg1, p.arg0, ByteArray(0)))
            }
        }
    }

    private fun send(p: AdbPacket) {
        output.write(p.encode())
        output.flush()
    }

    override fun close() {
        try { socket?.close() } catch (_: IOException) {}
        socket = null
    }
}
