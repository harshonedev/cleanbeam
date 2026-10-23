package com.cleanyourprojector.remote.adb

import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One ADB wire packet: 24-byte little-endian header followed by the payload. */
data class AdbPacket(val cmd: Int, val arg0: Int, val arg1: Int, val payload: ByteArray) {

    fun encode(): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_SIZE + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(cmd)
        buf.putInt(arg0)
        buf.putInt(arg1)
        buf.putInt(payload.size)
        buf.putInt(checksum(payload))
        buf.putInt(cmd.inv())
        buf.put(payload)
        return buf.array()
    }

    companion object {
        const val HEADER_SIZE = 24
        const val A_CNXN = 0x4e584e43
        const val A_OPEN = 0x4e45504f
        const val A_OKAY = 0x59414b4f
        const val A_CLSE = 0x45534c43
        const val A_WRTE = 0x45545257
        const val A_AUTH = 0x48545541

        const val VERSION = 0x01000001
        const val MAX_PAYLOAD = 256 * 1024

        fun checksum(payload: ByteArray): Int {
            var sum = 0
            for (b in payload) sum += b.toInt() and 0xFF
            return sum
        }

        fun decode(input: InputStream): AdbPacket {
            val header = readFully(input, HEADER_SIZE)
            val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val cmd = buf.int
            val arg0 = buf.int
            val arg1 = buf.int
            val len = buf.int
            buf.int // checksum, not verified
            buf.int // magic
            val payload = if (len > 0) readFully(input, len) else ByteArray(0)
            return AdbPacket(cmd, arg0, arg1, payload)
        }

        private fun readFully(input: InputStream, n: Int): ByteArray {
            val out = ByteArray(n)
            var off = 0
            while (off < n) {
                val r = input.read(out, off, n - off)
                if (r < 0) throw EOFException("adb stream closed")
                off += r
            }
            return out
        }
    }
}
