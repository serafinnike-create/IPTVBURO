package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Finding a title by its content key, on a catalogue the size of a real one.
 *
 * This is the lookup behind the history screen, and it was a linear scan that rebuilt a full
 * `XtreamCatalogItem` at every index — decoding category ids and composing an artwork URL — just to
 * read one field off it. On a 41,698-item catalogue with a 200-entry history that is over eight
 * million objects constructed, and the history gallery re-derives its list on every keystroke in
 * its search box.
 *
 * The catalogue here is synthetic and generated in the test. Nothing is read from a real playlist.
 */
class ContentKeyLookupTest {
    private fun catalogue(size: Int): CompactXtreamCatalog =
        CompactXtreamCatalog(XtreamContentType.MOVIE).apply {
            repeat(size) { index ->
                add(
                    XtreamCatalogItem(
                        providerId = index.toString(),
                        name = "Synthetic title $index",
                        contentType = XtreamContentType.MOVIE,
                        categoryIds = listOf("category-$index"),
                        containerExtension = "mp4",
                        artworkUrl = null,
                        year = 2000 + (index % 25),
                        rating = null,
                        addedAtEpochSeconds = null,
                    ),
                )
            }
        }

    @Test
    fun `a key that exists is found`() {
        val catalog = catalogue(500)
        val wanted = catalog.identityAt(321).key

        val found = catalog.indexOfContentKey(wanted)

        assertEquals(321, found)
        assertEquals("Synthetic title 321", catalog.itemAt(found).name)
    }

    @Test
    fun `a key that does not exist returns no index`() {
        assertEquals(-1, catalogue(500).indexOfContentKey("movie:nothing-here_1999"))
    }

    /** The index must survive rows arriving after it was first consulted. */
    @Test
    fun `titles added after the first lookup are still findable`() {
        val catalog = catalogue(100)
        assertNotNull(catalog.indexOfContentKey(catalog.identityAt(50).key).takeIf { it >= 0 })

        catalog.add(
            XtreamCatalogItem(
                providerId = "9999",
                name = "Late arrival",
                contentType = XtreamContentType.MOVIE,
                categoryIds = listOf("category-late"),
                containerExtension = "mp4",
                artworkUrl = null,
                year = 2026,
                rating = null,
                addedAtEpochSeconds = null,
            ),
        )

        val found = catalog.indexOfContentKey(catalog.identityAt(100).key)
        assertEquals(100, found, "a row added after the index was built must still be found")
        assertEquals("Late arrival", catalog.itemAt(found).name)
    }

    /**
     * The point of the change, stated as a bound rather than a hope.
     *
     * Two hundred lookups is what the history screen does. Against a catalogue this size the old
     * linear scan is roughly eight million item constructions; the bound below is generous enough
     * not to be flaky on a loaded machine and still far under what a scan can achieve.
     */
    @Test
    fun `two hundred lookups on a large catalogue stay fast`() {
        val catalog = catalogue(40_000)
        val keys = (0 until 200).map { index -> catalog.identityAt(index * 197).key }

        // The first lookup pays for the index; the timed run is what the screen actually repeats.
        catalog.indexOfContentKey(keys.first())

        val elapsed =
            measureTimeMillis {
                keys.forEach { key ->
                    assertTrue(catalog.indexOfContentKey(key) >= 0, "key went missing: $key")
                }
            }

        assertTrue(
            elapsed < 250,
            "200 content-key lookups took ${elapsed}ms on a 40,000-item catalogue; " +
                "this runs on every keystroke in the history search box",
        )
    }

    /** A repository with no catalogue loaded must answer, not fail. */
    @Test
    fun `an empty catalogue answers without a match`() {
        assertNull(
            CompactXtreamCatalog(XtreamContentType.MOVIE)
                .indexOfContentKey("movie:anything_2020")
                .takeIf { it >= 0 },
        )
    }
}
