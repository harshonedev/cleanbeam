package io.egate.remote.injector

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Drives a kernel evdev mouse (the projector's vendor "jixin_mouse" device) by writing
 * input_event structs to its /dev/input node. Unlike InputManager injection this goes
 * through InputReader, so Android draws the real pointer sprite. Needs root.
 */
class EvdevMouse private constructor(private val out: FileOutputStream, private val eventSize: Int) : AutoCloseable {

    private fun emit(type: Int, code: Int, value: Int) {
        val b = ByteBuffer.allocate(eventSize).order(ByteOrder.LITTLE_ENDIAN)
        if (eventSize == 24) { b.putLong(0); b.putLong(0) } else { b.putInt(0); b.putInt(0) }
        b.putShort(type.toShort()); b.putShort(code.toShort()); b.putInt(value)
        out.write(b.array())
    }

    private fun syn() { emit(EV_SYN, 0, 0); out.flush() }

    fun move(dx: Int, dy: Int) {
        if (dx != 0) emit(EV_REL, REL_X, dx)
        if (dy != 0) emit(EV_REL, REL_Y, dy)
        syn()
    }

    fun click(right: Boolean) {
        val btn = if (right) BTN_RIGHT else BTN_LEFT
        emit(EV_KEY, btn, 1); syn()
        emit(EV_KEY, btn, 0); syn()
    }

    /** Positive = scroll content down (wheel towards the user). */
    fun scroll(dy: Int) {
        emit(EV_REL, REL_WHEEL, -dy); syn()
    }

    override fun close() = out.close()

    companion object {
        private const val EV_SYN = 0
        private const val EV_KEY = 1
        private const val EV_REL = 2
        private const val REL_X = 0
        private const val REL_Y = 1
        private const val REL_WHEEL = 8
        private const val BTN_LEFT = 0x110
        private const val BTN_RIGHT = 0x111

        /** Finds an evdev node whose name matches [name]; null if none or not writable. */
        fun open(name: String = "jixin_mouse"): EvdevMouse? {
            val dir = File("/sys/class/input").listFiles() ?: return null
            for (d in dir) {
                if (!d.name.startsWith("event")) continue
                val n = runCatching { File(d, "device/name").readText().trim() }.getOrNull() ?: continue
                if (n != name) continue
                val node = File("/dev/input/${d.name}")
                return runCatching {
                    val is64 = System.getProperty("os.arch")?.contains("64") == true
                    EvdevMouse(FileOutputStream(node), if (is64) 24 else 16)
                }.getOrNull()
            }
            return null
        }
    }
}
