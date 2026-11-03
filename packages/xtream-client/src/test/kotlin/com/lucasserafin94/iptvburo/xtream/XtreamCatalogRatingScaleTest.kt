package com.lucasserafin94.iptvburo.xtream

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The catalogue's rating is always out of five.
 *
 * Providers send `rating_5based`, `rating` (out of ten), or both, in either order. Kept as it came,
 * one list mixed the two scales and the five-star filter stopped meaning anything.
 */
class XtreamCatalogRatingScaleTest {
    private fun ratingOf(json: String): Double? {
        var rating: Double? = null
        XtreamCatalogStreamParser(
            contentType = XtreamContentType.MOVIE,
            maximumItems = 10,
            sanitizeArtwork = { null },
        ).parse(json.byteInputStream(StandardCharsets.UTF_8)) { item -> rating = item.rating }
        return rating
    }

    @Test
    fun `the five-based score is kept as it is`() {
        assertEquals(3.7, ratingOf("""[{"stream_id":"1","name":"A","rating_5based":"3.7"}]""")!!, 0.001)
    }

    @Test
    fun `a score out of ten alone is halved`() {
        assertEquals(3.7, ratingOf("""[{"stream_id":"1","name":"A","rating":"7.4"}]""")!!, 0.001)
    }

    @Test
    fun `the five-based score wins whichever comes first`() {
        assertEquals(3.7, ratingOf("""[{"stream_id":"1","name":"A","rating":"9","rating_5based":"3.7"}]""")!!, 0.001)
        assertEquals(3.7, ratingOf("""[{"stream_id":"1","name":"A","rating_5based":"3.7","rating":"9"}]""")!!, 0.001)
    }
}
