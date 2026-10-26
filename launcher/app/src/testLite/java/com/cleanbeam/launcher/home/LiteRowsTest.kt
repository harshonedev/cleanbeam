package com.cleanbeam.launcher.home

import com.cleanbeam.launcher.settings.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRowsTest {
    private fun tile(title: String) = Tile(title, "", "", 0) {}

    @Test fun `quick tiles keep the fixed order and skip titles the box lacks`() {
        val got = LiteRows.quick(listOf(tile("Launcher"), tile("System settings"), tile("Wi-Fi"), tile("Focus")))
        assertEquals(listOf("Wi-Fi", "Focus", "Launcher"), got.map { it.title })
    }

    @Test fun `no favorites leaves only the quick settings shelf`() {
        val rows = LiteRows.build(emptyList(), listOf(tile("Wi-Fi")), "Favorites", "Quick settings")
        assertEquals(listOf("Quick settings"), rows.map { it.header })
        assertEquals(listOf("Wi-Fi"), rows[0].tiles.map { it.title })
        assertTrue(rows[0].apps.isEmpty())
    }

    @Test fun `quick settings shelf exists even with no quick tiles`() {
        assertEquals(1, LiteRows.build(emptyList(), emptyList(), "Favorites", "Quick settings").size)
    }
}
