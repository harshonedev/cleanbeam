package io.egate.remote.adb

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Minimal ADB host-side client that talks to the local adbd over TCP.
 * Works only against an adbd with ro.adb.secure=0 (no AUTH handshake).
 */
class AdbClient(private val host: String = "127.0.0.1", private val port: Int = 3333) : Closeable {

    companion object {
        /** persist.adb.tcp.port is 3333, but `adb root` restarts adbd on the default 5555. */
        val CANDIDATE_PORTS = listOf(3333, 5555)

        /** Connects to the first local adbd port that answers. */
        fun connectAny(): AdbClient {
            var last: Exception? = null
            for (p in CANDIDATE_PORTS) {
                val c = AdbClient(port = p)
                try { c.connect(); return c } catch (e: Exception) { last = e; c.close() }
            }
            throw IOException("no local adbd on ${CANDIDATE_PORTS}", last)
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
