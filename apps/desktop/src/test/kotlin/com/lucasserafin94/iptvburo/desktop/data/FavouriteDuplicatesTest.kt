package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.ContentKind
import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One row per title on the favourites screen.
 *
 * A favourite is keyed on what the content *is* — kind, title, year — rather than on a provider id,
 * so that it survives replacing the playlist. Providers routinely carry the same film several times
 * over for different qualities, and every one of those copies matches that single key: marking a
 * film once made four cards appear, which reads as the app saving it repeatedly.
 *
 * The catalogue proper must keep every copy. That is where the choice of quality is made, and
 * collapsing them there would take the choice away.
 */
class FavouriteDuplicatesTest {
    private fun catalogue(vararg names: String): CompactXtreamCatalog =
        CompactXtreamCatalog(XtreamContentType.MOVIE).apply {
            names.forEachIndexed { index, name ->
                add(
                    XtreamCatalogItem(
                        providerId = "id-$index",
                        name = name,
                        contentType = XtreamContentType.MOVIE,
                        categoryIds = listOf("category-movies"),
                        containerExtension = "mp4",
                        artworkUrl = null,
                        year = 2026,
                        rating = 3.0 + index * 0.1,
                        addedAtEpochSeconds = null,
                    ),
                )
            }
        }

    /**
     * The reported case, from the user's own screen: four Supergirl cards, one favourite.
     *
     * The ratings differ slightly between the provider's copies, which is what showed these were
     * genuinely separate catalogue rows rather than the favourite being written four times.
     */
    @Test
    fun `four provider copies of one film collapse to a single favourite`() {
        val catalogue = catalogue("Supergirl", "Supergirl", "Supergirl", "Supergirl")
        val favourite = catalogue.identityAt(0)

        val listed = (0 until catalogue.size).filter { index -> catalogue.identityAt(index) == favourite }
        val distinct = listed.map(catalogue::identityAt).toSet()

        assertEquals(4, listed.size, "the provider really does carry four rows")
        assertEquals(1, distinct.size, "and all four are one title, which is why one heart marks all")
    }

    /**
     * Different titles are never collapsed together.
     *
     * The failure that would matter most: a deduplication keyed too loosely would hide films the
     * customer had actually saved, and they would never know what was missing.
     */
    @Test
    fun `different titles keep their own rows`() {
        val catalogue = catalogue("Supergirl", "Jackass", "Operação Sombra")

        val identities = (0 until catalogue.size).map(catalogue::identityAt).toSet()

        assertEquals(3, identities.size, "three different films are three favourites")
    }

    /**
     * The same title in different years is two different films.
     *
     * Remakes share a name. Collapsing them would hide one behind the other, and the year is what
     * the identity already carries to tell them apart.
     */
    @Test
    fun `a remake is not a duplicate of the original`() {
        val original = ContentIdentity.of(kind = ContentKind.MOVIE, title = "Dune", year = 1984)
        val remake = ContentIdentity.of(kind = ContentKind.MOVIE, title = "Dune", year = 2021)

        assertTrue(original != remake, "two films sharing a name are still two films")
    }
}
