package io.egate.launcher.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRowTest {

    private val title = Title(1, "movie", "Dune", "", "2021", 8.0, "/d.jpg", null)

    @Test
    fun `a loading shelf is drawn as skeletons even with no titles`() {
        assertTrue(HomeRow("On Netflix", emptyList(), "com.netflix", "Netflix", loading = true).visible)
    }

    @Test
    fun `a shelf that finished loading empty is dropped`() {
        assertFalse(HomeRow("On Netflix", emptyList(), "com.netflix", "Netflix", loading = false).visible)
    }

    @Test
    fun `a loaded shelf with titles is drawn`() {
        assertTrue(HomeRow("On Netflix", listOf(title), "com.netflix", "Netflix").visible)
    }
}
