package io.egate.remote.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChunkerTest {

    @Test
    fun `single chunk when fits`() {
        val chunks = Chunker.split("{\"t\":\"ping\"}".toByteArray(), 23)
        assertEquals(1, chunks.size)
        assertEquals(0x80.toByte(), chunks[0][0])
        assertEquals("{\"t\":\"ping\"}", String(chunks[0], 1, chunks[0].size - 1))
    }

    @Test
    fun `splits 1000 bytes at mtu 23 into 53 chunks`() {
        val msg = ByteArray(1000) { (it % 251).toByte() }
        val chunks = Chunker.split(msg, 23)
        assertEquals(53, chunks.size)
        assertEquals(0x00.toByte(), chunks[0][0])
        assertEquals(0x01.toByte(), chunks[1][0])
        assertEquals((0x80 or 52).toByte(), chunks[52][0])
        assertEquals(20, chunks[0].size)
        assertEquals(1 + (1000 - 52 * 19), chunks[52].size)
    }

    @Test
    fun `seq wraps at 128`() {
        val msg = ByteArray(19 * 130)
        val chunks = Chunker.split(msg, 23)
        assertEquals(130, chunks.size)
        assertEquals(127.toByte(), chunks[127][0])
        assertEquals(0.toByte(), chunks[128][0])
        assertEquals((0x80 or 1).toByte(), chunks[129][0])
    }

    @Test
    fun `assembler rebuilds`() {
        val msg = ByteArray(1000) { (it % 251).toByte() }
        val a = Chunker.Assembler()
        val chunks = Chunker.split(msg, 23)
        for (i in 0 until chunks.size - 1) assertNull(a.feed(chunks[i]))
        assertArrayEquals(msg, a.feed(chunks.last()))
    }

    @Test
    fun `assembler discards on seq gap and restarts`() {
        val msg = ByteArray(100) { it.toByte() }
        val chunks = Chunker.split(msg, 23)
        val a = Chunker.Assembler()
        a.feed(chunks[0])
        assertNull(a.feed(chunks[2]))   // gap: partial discarded, chunk dropped (seq != 0)
        assertNull(a.feed(chunks[5]))   // still dropped
        // a fresh message now goes through
        for (i in 0 until chunks.size - 1) assertNull(a.feed(chunks[i]))
        assertArrayEquals(msg, a.feed(chunks.last()))
    }

    @Test
    fun `twenty back-to-back single messages stay ordered`() {
        val a = Chunker.Assembler()
        val out = mutableListOf<String>()
        for (i in 0 until 20) {
            val m = "{\"t\":\"key\",\"code\":$i}".toByteArray()
            for (c in Chunker.split(m, 512)) a.feed(c)?.let { out += String(it) }
        }
        assertEquals((0 until 20).map { "{\"t\":\"key\",\"code\":$it}" }, out)
    }
}
