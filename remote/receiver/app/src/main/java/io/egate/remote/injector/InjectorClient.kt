package io.egate.remote.injector

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import io.egate.remote.adb.AdbClient
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream

/**
 * App-side handle to the injector helper. Connects to its LocalSocket; if that is refused,
 * spawns the helper via the local adbd and retries until it answers.
 */
class InjectorClient(
    private val spawner: Spawner,
    private val connector: Connector,
    private val retryDelayMs: Long = 200,
    private val spawnTimeoutMs: Long = 5_000,
) {
    interface Spawner { fun spawn() }
    interface Connector { fun connect(): LineChannel? }
    interface LineChannel : Closeable { fun send(line: String): String }

    private var channel: LineChannel? = null

    @Synchronized
    fun send(json: String): String {
        val ch = channel ?: ensureConnected()
        return try {
            ch.send(json)
        } catch (e: IOException) {
            dropChannel()
            ensureConnected().send(json)
        }
    }

    @Synchronized
    fun close() = dropChannel()

    private fun ensureConnected(): LineChannel {
        connector.connect()?.let { channel = it; return it }
        spawner.spawn()
        val deadline = System.currentTimeMillis() + spawnTimeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (retryDelayMs > 0) Thread.sleep(retryDelayMs)
            connector.connect()?.let { channel = it; return it }
        }
        throw IOException("injector helper did not come up within ${spawnTimeoutMs}ms")
    }

    private fun dropChannel() {
        try { channel?.close() } catch (_: IOException) {}
        channel = null
    }

    companion object {
        private const val TAG = "EGateRemote"
        private const val UID_ROOT = 0
        private const val UID_SHELL = 2000

        /** The helper is spawned by adbd, so it runs as root or shell; anything else is a squatter. */
        fun isTrustedHelperUid(uid: Int): Boolean = uid == UID_ROOT || uid == UID_SHELL

        /** Production wiring: spawn via adbd on localhost, connect via abstract LocalSocket. */
        fun create(apkPath: String, appUid: Int): InjectorClient {
            val spawner = object : Spawner {
                override fun spawn() {
                    try {
                        AdbClient().use {
                            it.connect()
                            it.shellDetached("CLASSPATH=$apkPath app_process /system/bin io.egate.remote.injector.Main --uid $appUid")
                            Thread.sleep(300) // let adbd actually fork before the socket closes
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "spawn via adb failed", e)
                    }
                }
            }
            val connector = object : Connector {
                override fun connect(): LineChannel? = try {
                    val s = LocalSocket()
                    s.connect(LocalSocketAddress(Main.SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT))
                    val peer = s.peerCredentials.uid
                    if (!isTrustedHelperUid(peer)) {
                        Log.w(TAG, "injector socket owned by untrusted uid $peer; refusing")
                        s.close()
                        null
                    } else LocalLineChannel(s)
                } catch (e: IOException) {
                    null
                }
            }
            return InjectorClient(spawner, connector)
        }
    }

    private class LocalLineChannel(private val socket: LocalSocket) : LineChannel {
        private val reader = BufferedReader(InputStreamReader(socket.inputStream))
        private val out: OutputStream = socket.outputStream
        override fun send(line: String): String {
            out.write((line + "\n").toByteArray()); out.flush()
            return reader.readLine() ?: throw IOException("injector closed")
        }
        override fun close() = socket.close()
    }
}
