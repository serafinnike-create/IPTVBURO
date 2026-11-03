package com.lucasserafin94.iptvburo.data.sync

import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.ContentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidFavoriteSyncTest {
    private fun channel(
        name: String,
        type: CatalogContentType,
        year: Int? = null,
    ) = Channel(id = "row-1", sourceId = "s", name = name, streamUri = "http://x.invalid/1", contentType = type, year = year)

    /**
     * The same film must have the same identity here as on the computer, or a favourite made on
     * one is never recognised on the other. The computer derives it as `ContentIdentity.of(kind,
     * name, year ?: yearFromTitle(name))`; this is that rule, from a catalogue row.
     */
    @Test
    fun `a film's identity matches the computer's for the same title`() {
        val decorated = channel("[4K] A Filha de Zeus (2024) DUAL", CatalogContentType.MOVIE)

        val expected = ContentIdentity.of(ContentKind.MOVIE, decorated.name, ContentIdentity.yearFromTitle(decorated.name))
        assertEquals(expected, identityOf(decorated))
    }

    @Test
    fun `a stated year wins over one guessed from the title`() {
        val row = channel("Dark", CatalogContentType.SERIES, year = 2017)

        assertEquals(ContentIdentity.of(ContentKind.SERIES, "Dark", 2017), identityOf(row))
    }

    @Test
    fun `live channels are not shared as favourite titles`() {
        assertNull(identityOf(channel("Globo HD", CatalogContentType.LIVE)))
        assertNull(syncFavoriteOf("Ana", channel("Globo HD", CatalogContentType.LIVE)))
    }

    @Test
    fun `a favourite row becomes a sync entry with its real title`() {
        val entry = syncFavoriteOf("Ana", channel("Dark", CatalogContentType.SERIES, year = 2017))!!

        assertEquals("series", entry.kind)
        assertEquals("Dark", entry.title)
        assertEquals(2017, entry.year)
        assertEquals("Ana", entry.profile)
    }

    @Test
    fun `the catalogue is searched by the title's most telling word`() {
        assertEquals("poderoso", searchWordFor("o grande e poderoso genio"))
        assertEquals("Dark", searchWordFor("Dark"))
        assertNull(searchWordFor("Up"))
        assertNull(searchWordFor("24"))
    }
}
