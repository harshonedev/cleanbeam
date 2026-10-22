package io.egate.remote.adb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdbPacketTest {

    private fun u32(bytes: ByteArray, offset: Int): Long =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL

    @Test
    fun `encodes CNXN header fields in little endian`() {
        val payload = "host::\u0000".toByteArray()
        val bytes = AdbPacket(AdbPacket.A_CNXN, 0x01000001, 256 * 1024, payload).encode()
        assertEquals(24 + payload.size, bytes.size)
        assertEquals(AdbPacket.A_CNXN.toLong() and 0xFFFFFFFFL, u32(bytes, 0))
        assertEquals(0x01000001L, u32(bytes, 4))
        assertEquals(256L * 1024, u32(bytes, 8))
        assertEquals(payload.size.toLong(), u32(bytes, 12))
        assertEquals(payload.sumOf { it.toInt() and 0xFF }.toLong(), u32(bytes, 16))
        assertArrayEquals(payload, bytes.copyOfRange(24, bytes.size))
    }

    @Test
    fun `magic is complement of cmd`() {
        val bytes = AdbPacket(AdbPacket.A_OKAY, 1, 2, ByteArray(0)).encode()
        assertEquals((AdbPacket.A_OKAY.inv()).toLong() and 0xFFFFFFFFL, u32(bytes, 20))
    }

    @Test
    fun `decode round-trips encode`() {
        val original = AdbPacket(AdbPacket.A_WRTE, 7, 9, "uid=0(root)\n".toByteArray())
        val decoded = AdbPacket.decode(ByteArrayInputStream(original.encode()))
        assertEquals(original.cmd, decoded.cmd)
        assertEquals(original.arg0, decoded.arg0)
        assertEquals(original.arg1, decoded.arg1)
        assertArrayEquals(original.payload, decoded.payload)
    }

    @Test
    fun `command constants match adb protocol`() {
        assertEquals(0x4e584e43, AdbPacket.A_CNXN)
        assertEquals(0x4e45504f, AdbPacket.A_OPEN)
        assertEquals(0x59414b4f, AdbPacket.A_OKAY)
        assertEquals(0x45534c43, AdbPacket.A_CLSE)
        assertEquals(0x45545257, AdbPacket.A_WRTE)
    }
}
