package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ContentIdentityMappingTest {
    private fun item(
        providerId: String,
        name: String,
        year: Int? = null,
        type: XtreamContentType = XtreamContentType.MOVIE,
    ) = XtreamCatalogItem(
        providerId = providerId,
        name = name,
        contentType = type,
        categoryIds = emptyList(),
        containerExtension = "mp4",
        artworkUrl = null,
        year = year,
        rating = null,
        addedAtEpochSeconds = null,
    )

    @Test
    fun `a favourite survives replacing the playlist`() {
        // The same film, carried by two different providers under different ids and decorations.
        val inOldList = item(providerId = "8123", name = "[4K] Cidade de Deus (2002) DUAL", year = 2002)
        val inNewList = item(providerId = "455", name = "Cidade de Deus 1080p", year = 2002)

        assertEquals(inOldList.contentIdentity(), inNewList.contentIdentity())
    }

    @Test
    fun `reusing a provider id in another list does not transfer the favourite`() {
        // This is the bug the old scheme had: both lists number a film "512", so the stored
        // favourite matched and marked an unrelated title.
        val oldList = item(providerId = "512", name = "Cidade de Deus", year = 2002)
        val newList = item(providerId = "512", name = "Tropa de Elite", year = 2007)

        assertNotEquals(oldList.contentIdentity(), newList.contentIdentity())
    }

    @Test
    fun `legacy favourite keys are rewritten when the item is still resolvable`() {
        val resolved = item(providerId = "77", name = "Matrix", year = 1999)

        val migrated =
            migrateFavoriteKeys(setOf("MOVIE:77")) { key ->
                resolved.takeIf { key == "MOVIE:77" }
            }

        assertEquals(setOf(resolved.contentIdentity().key), migrated)
    }

    @Test
    fun `an unresolvable legacy key is dropped rather than kept`() {
        // Keeping it is what made unrelated titles show up as favourites after a list change.
        val migrated = migrateFavoriteKeys(setOf("MOVIE:999")) { null }

        assertTrue(migrated.isEmpty())
    }

    @Test
    fun `already migrated keys pass through untouched`() {
        val identity = item(providerId = "1", name = "Heat", year = 1995).contentIdentity().key

        val migrated = migrateFavoriteKeys(setOf(identity)) { error("should not be resolved") }

        assertEquals(setOf(identity), migrated)
    }

    @Test
    fun `legacy detection does not misfire on new keys`() {
        assertTrue(isLegacyFavoriteKey("MOVIE:12"))
        assertTrue(isLegacyFavoriteKey("SERIES:12"))
        assertTrue(!isLegacyFavoriteKey("movie:matrix:1999"))
    }

    @Test
    fun `year embedded in the title is enough to match across lists`() {
        // One provider fills the year field, the other only puts it in the title.
        val withField = item(providerId = "1", name = "Blade Runner", year = 1982)
        val withTitleOnly = item(providerId = "2", name = "Blade Runner (1982)", year = null)

        assertEquals(withField.contentIdentity(), withTitleOnly.contentIdentity())
    }

    @Test
    fun `the catalogue index and the item agree on identity`() {
        // These two must match exactly. add() resolves a missing year out of the title, so
        // reading the stored column produced an identity with a year while the item — holding the
        // provider's raw null — produced one without. Favouriting wrote the second and the filter
        // searched for the first, so favourites appeared empty.
        val catalog = CompactXtreamCatalog(XtreamContentType.MOVIE)
        val item = item(providerId = "9", name = "Some Film (1998)", year = null)
        catalog.add(item)

        assertEquals(item.contentIdentity(), catalog.identityAt(0))
    }

    @Test
    fun `index and item agree when the provider supplies the year`() {
        val catalog = CompactXtreamCatalog(XtreamContentType.MOVIE)
        val item = item(providerId = "10", name = "Другой Film", year = 2011)
        catalog.add(item)

        assertEquals(item.contentIdentity(), catalog.identityAt(0))
    }
}
