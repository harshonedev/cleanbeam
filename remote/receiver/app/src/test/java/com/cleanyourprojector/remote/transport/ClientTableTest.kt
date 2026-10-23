package com.cleanyourprojector.remote.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientTableTest {
    private val dropped = mutableListOf<String>()
    private val table = ClientTable<String> { dropped += it }

    @Test
    fun `a new connection for an address that is still listed drops the old one`() {
        table.add("AA", "first")
        table.add("AA", "second")
        assertEquals(listOf("first"), dropped)
        assertSame("second", table["AA"])
    }

    @Test
    fun `remove drops the client it removes and nothing else`() {
        table.add("AA", "a"); table.add("BB", "b")
        table.remove("AA")
        assertEquals(listOf("a"), dropped)
        assertEquals(listOf("b"), table.values.toList())
    }

    @Test
    fun `removeIf only removes the given client`() {
        table.add("AA", "a")
        assertTrue(!table.removeIf("AA", "stale"))
        assertTrue(table.removeIf("AA", "a"))
        assertEquals(listOf("a"), dropped)
    }

    @Test
    fun `clear drops every client`() {
        table.add("AA", "a"); table.add("BB", "b")
        table.clear()
        assertEquals(setOf("a", "b"), dropped.toSet())
        assertTrue(table.values.isEmpty())
    }
}
