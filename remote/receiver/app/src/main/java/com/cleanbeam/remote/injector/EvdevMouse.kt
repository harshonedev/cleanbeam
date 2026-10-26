package com.cleanbeam.remote.injector

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Drives a kernel evdev relative mouse by writing input_event structs to its /dev/input node.
 * Unlike InputManager injection this goes through InputReader, so Android draws the real pointer
 * sprite. Prefers a device named "jixin_mouse" (common on H713 projector boards), then any node
 * whose name says mouse, then any other relative pointer. Needs root or shell.
 */
class EvdevMouse private constructor(
    private val out: FileOutputStream,
    private val eventSize: Int,
    val deviceName: String,
) : AutoCloseable {

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

        /** Opens the best writable relative pointer; null when this kernel has none. */
        fun open(): EvdevMouse? {
            val dir = File("/sys/class/input").listFiles() ?: return null
            val ranked = dir.mapNotNull { d ->
                if (!d.name.startsWith("event")) return@mapNotNull null
                val name = runCatching { File(d, "device/name").readText().trim() }.getOrNull() ?: return@mapNotNull null
                val rel = runCatching { File(d, "device/capabilities/rel").readText() }.getOrNull().orEmpty()
                val rank = pointerRank(name, rel) ?: return@mapNotNull null
                Triple(rank, name, File("/dev/input/${d.name}"))
            }.sortedBy { it.first }
            for ((_, name, node) in ranked) {
                openNode(node, name)?.let { return it }
            }
            return null
        }

        private fun openNode(node: File, name: String): EvdevMouse? = runCatching {
            val is64 = System.getProperty("os.arch")?.let { it.contains("64") || it.contains("aarch64") } == true
            EvdevMouse(FileOutputStream(node), if (is64) 24 else 16, name)
        }.getOrNull()
    }
}

/**
 * Lower is better. Null means this node is not a pointer we should drive.
 * Capability words are printed most-significant first, so REL_X and REL_Y live in the last word.
 */
internal fun pointerRank(name: String, relCaps: String): Int? {
    val relative = isRelativePointer(relCaps)
    val mouse = name.contains("mouse", ignoreCase = true)
    return when {
        name.equals("jixin_mouse", ignoreCase = true) -> 0
        mouse && (relative || relCaps.isBlank()) -> 1
        relative && !looksLikeKeysOrTouch(name) -> 2
        else -> null
    }
}

internal fun isRelativePointer(relCaps: String): Boolean {
    val last = relCaps.trim().split(Regex("\\s+")).lastOrNull()?.takeIf { it.isNotEmpty() } ?: return false
    val bits = last.toLongOrNull(16) ?: return false
    return bits and 0x3L == 0x3L
}

internal fun looksLikeKeysOrTouch(name: String): Boolean {
    val n = name.lowercase()
    return n.contains("touch") || n.contains("key") || n.contains("gpio") || n.contains("ir_")
}
