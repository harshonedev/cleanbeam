package io.egate.remote.injector

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.Looper
import android.os.Process
import io.egate.remote.protocol.Codec
import io.egate.remote.protocol.Command
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream

/**
 * Entry point run under app_process (as shell/root) by the receiver app:
 *   CLASSPATH=<apk> app_process /system/bin io.egate.remote.injector.Main
 * Reads newline-delimited JSON commands from a LocalServerSocket and injects them.
 */
object Main {
    const val SOCKET_NAME = "egate-injector"

    @JvmStatic
    fun main(args: Array<String>) {
        if (Looper.myLooper() == null) Looper.prepare()
        val sink = AndroidSink()
        val injector = InputInjector(sink, sink)
        if (args.isNotEmpty() && args[0] == "selftest") {
            injector.key(20, io.egate.remote.protocol.KeyAction.TAP)
            System.err.println("selftest: injected DPAD_DOWN")
        }
        val server = LocalServerSocket(SOCKET_NAME)
        System.err.println("egate-injector listening pid=${Process.myPid()}")
        while (true) {
            val client = try { server.accept() } catch (e: Exception) { break }
            serve(client, injector)
        }
    }

    private fun serve(client: LocalSocket, injector: InputInjector) {
        try {
            val reader = BufferedReader(InputStreamReader(client.inputStream))
            val out = client.outputStream
            while (true) {
                val line = reader.readLine() ?: break
                out.writeLine(handle(line, injector))
            }
        } catch (_: Exception) {
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    internal fun handle(line: String, injector: InputInjector): String {
        if (line.contains("\"hello\"")) return "{\"t\":\"hello_ok\",\"pid\":${Process.myPid()}}"
        val ok = when (val c = Codec.decodeCommand(line)) {
            is Command.Key -> injector.key(c.code, c.action)
            is Command.Text -> injector.text(c.s)
            is Command.Mouse -> injector.mouseMove(c.dx, c.dy)
            is Command.Click -> injector.click(c.button)
            is Command.Scroll -> injector.scroll(c.dy)
            is Command.Power -> injector.power(c.action)
            else -> return "{\"t\":\"err\",\"msg\":\"injector cannot handle\"}"
        }
        return if (ok) "{\"t\":\"ok\"}" else "{\"t\":\"err\",\"msg\":\"inject failed\"}"
    }

    private fun OutputStream.writeLine(s: String) {
        write((s + "\n").toByteArray()); flush()
    }
}
