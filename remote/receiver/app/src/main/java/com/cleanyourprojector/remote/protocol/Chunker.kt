package com.cleanyourprojector.remote.protocol

import java.io.ByteArrayOutputStream

/**
 * BLE framing: byte0 = (seq & 0x7F) | (last ? 0x80 : 0), then payload.
 * See remote/PROTOCOL.md.
 */
object Chunker {
    private const val LAST = 0x80
    private const val SEQ_MASK = 0x7F
    private const val ATT_OVERHEAD = 3
    private const val HEADER = 1
    const val DEFAULT_MAX_MESSAGE = 64 * 1024

    /** ATT caps an attribute value at 512 bytes whatever the MTU; the Amlogic stack aborts past it. */
    const val MAX_ATTR_VALUE = 512

    fun payloadSize(mtu: Int): Int = (minOf(mtu - ATT_OVERHEAD, MAX_ATTR_VALUE) - HEADER).coerceAtLeast(1)

    fun split(msg: ByteArray, mtu: Int): List<ByteArray> {
        val size = payloadSize(mtu)
        val chunks = ArrayList<ByteArray>()
        var off = 0
        var seq = 0
        do {
            val n = minOf(size, msg.size - off)
            val last = off + n >= msg.size
            val c = ByteArray(1 + n)
            c[0] = ((seq and SEQ_MASK) or (if (last) LAST else 0)).toByte()
            System.arraycopy(msg, off, c, 1, n)
            chunks += c
            off += n
            seq = (seq + 1) and SEQ_MASK
        } while (off < msg.size)
        return chunks
    }

    /** Messages larger than [maxMessage] bytes are dropped (a BLE peer could otherwise exhaust memory). */
    class Assembler(private val maxMessage: Int = DEFAULT_MAX_MESSAGE) {
        private val buf = ByteArrayOutputStream()
        private var expected = 0
        private var open = false

        /** Returns the complete message when [chunk] finishes one, else null. */
        fun feed(chunk: ByteArray): ByteArray? {
            if (chunk.isEmpty()) return null
            val h = chunk[0].toInt() and 0xFF
            val seq = h and SEQ_MASK
            val last = (h and LAST) != 0
            if (!open || seq != expected) {
                // Gap or stray chunk: drop partial; only a seq 0 chunk may start a new message.
                buf.reset(); open = false; expected = 0
                if (seq != 0) return null
                open = true
            }
            if (buf.size() + chunk.size - 1 > maxMessage) {
                buf.reset(); open = false; expected = 0
                return null
            }
            buf.write(chunk, 1, chunk.size - 1)
            expected = (seq + 1) and SEQ_MASK
            if (last) {
                val out = buf.toByteArray()
                buf.reset(); open = false; expected = 0
                return out
            }
            return null
        }
    }
}
