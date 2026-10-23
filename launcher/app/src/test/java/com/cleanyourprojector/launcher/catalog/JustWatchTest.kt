package io.egate.launcher.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JustWatchTest {

    private val hotstar = Services.ALL.first { it.name == "JioHotstar" }
    private val prime = Services.ALL.first { it.name == "Prime Video" }
    private val netflix = Services.ALL.first { it.name == "Netflix" }

    private fun title(id: Int, kind: String) = Title(id, kind, "x", "", null, 0.0, null, null)

    // Trimmed real response for searchQuery "Avatar", country IN.
    private val sample = """
    {"data":{"popularTitles":{"edges":[
      {"node":{"objectType":"MOVIE","content":{"title":"Avatar","originalReleaseYear":2009,"externalIds":{"tmdbId":"19995","imdbId":"tt0499549"}},
        "offers":[
          {"monetizationType":"FLATRATE","standardWebURL":"https://www.hotstar.com/in/movies/avatar/1770000948",
           "deeplinkURL":"intent://content/1770000948#Intent;launchFlags=0x00800000;scheme=hotstar;end",
           "package":{"clearName":"JioHotstar"}},
          {"monetizationType":"BUY","standardWebURL":"https://app.primevideo.com/detail?gti=amzn1.dv.gti.abc",
           "deeplinkURL":null,
           "package":{"clearName":"Amazon Prime Video"}}
        ]}},
      {"node":{"objectType":"SHOW","content":{"title":"Avatar: The Last Airbender","originalReleaseYear":2005,"externalIds":{"tmdbId":"246","imdbId":"tt0417299"}},
        "offers":[
          {"monetizationType":"FLATRATE","standardWebURL":"https://www.hotstar.com/in/1971022510",
           "deeplinkURL":"intent://content/1971022510#Intent;launchFlags=0x00800000;scheme=hotstar;end",
           "package":{"clearName":"JioHotstar"}}
        ]}},
      {"node":{"objectType":"MOVIE","content":{"title":"No ids","originalReleaseYear":2001,"externalIds":{"tmdbId":null,"imdbId":null}},
        "offers":[]}}
    ]}}}
    """.trimIndent()

    @Test
    fun `parse reads tmdb id, kind and offers per candidate`() {
        val c = JustWatch.parse(sample)
        assertEquals(3, c.size)
        assertEquals(19995, c[0].tmdbId)
        assertEquals("movie", c[0].kind)
        assertEquals(2, c[0].offers.size)
        assertEquals("tv", c[1].kind)
        assertEquals(246, c[1].tmdbId)
        assertNull(c[2].tmdbId)
    }

    @Test
    fun `pickUrl prefers the android tv intent uri for the matching title and service`() {
        val c = JustWatch.parse(sample)
        assertEquals(
            "intent://content/1770000948#Intent;launchFlags=0x00800000;scheme=hotstar;end",
            JustWatch.pickUrl(c, title(19995, "movie"), hotstar)
        )
    }

    @Test
    fun `pickUrl falls back to the web url when there is no intent uri`() {
        val c = JustWatch.parse(sample)
        assertEquals("https://app.primevideo.com/detail?gti=amzn1.dv.gti.abc", JustWatch.pickUrl(c, title(19995, "movie"), prime))
    }

    @Test
    fun `pickUrl matches on kind so a show and a movie with the same name do not mix`() {
        val c = JustWatch.parse(sample)
        assertEquals(
            "intent://content/1971022510#Intent;launchFlags=0x00800000;scheme=hotstar;end",
            JustWatch.pickUrl(c, title(246, "tv"), hotstar)
        )
        assertNull(JustWatch.pickUrl(c, title(246, "movie"), hotstar))
    }

    @Test
    fun `pickUrl is null when the service has no offer or the title is unknown`() {
        val c = JustWatch.parse(sample)
        assertNull(JustWatch.pickUrl(c, title(19995, "movie"), netflix))
        assertNull(JustWatch.pickUrl(c, title(1, "movie"), hotstar))
    }

    @Test
    fun `parse tolerates garbage`() {
        assertEquals(0, JustWatch.parse("not json").size)
        assertEquals(0, JustWatch.parse("""{"errors":[{"message":"x"}],"data":null}""").size)
    }
}
