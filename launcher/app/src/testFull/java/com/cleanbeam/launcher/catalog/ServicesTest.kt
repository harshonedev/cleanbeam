package com.cleanbeam.launcher.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServicesTest {

    @Test
    fun `installed app matches by package fragment`() {
        assertEquals("Prime Video", Services.forApp("com.amazon.amazonvideo.livingroom", "Prime Video")?.name)
        assertEquals("Netflix", Services.forApp("com.clap.netflix", "CLAP Netflix")?.name)
        assertEquals("JioHotstar", Services.forApp("in.startv.hotstar", "Hotstar")?.name)
        assertEquals("aha", Services.forApp("ahaflix.tv", "aha")?.name)
    }

    @Test
    fun `installed app matches by label when the package is unfamiliar`() {
        assertEquals("Zee5", Services.forApp("com.example.tvapp", "ZEE5")?.name)
    }

    @Test
    fun `youtube and unknown apps map to nothing`() {
        assertNull(Services.forApp("com.google.android.youtube.tv", "YouTube"))
        assertNull(Services.forApp("com.jixin.filebrowser", "File Browser"))
    }

    @Test
    fun `tmdb provider variants collapse to one service and keep order`() {
        val names = listOf("Amazon Prime Video", "Amazon Prime Video with Ads", "Netflix", "Netflix basic with Ads", "Hungama Play")
        assertEquals(listOf("Prime Video", "Netflix"), Services.forTmdbNames(names).map { it.name })
    }

    @Test
    fun `tmdb names are matched case-insensitively`() {
        assertEquals("JioHotstar", Services.forTmdbNames(listOf("jiohotstar")).single().name)
        assertEquals("JioHotstar", Services.forTmdbNames(listOf("Disney Plus Hotstar")).single().name)
    }

    @Test
    fun `search url encodes the title`() {
        val s = Services.forTmdbNames(listOf("Netflix")).single()
        assertEquals("https://www.netflix.com/search?q=The%20Bear%20%26%20Co", s.searchUrl("The Bear & Co"))
        assertNull(Services.forTmdbNames(listOf("aha")).single().searchUrl("x"))
    }
}
