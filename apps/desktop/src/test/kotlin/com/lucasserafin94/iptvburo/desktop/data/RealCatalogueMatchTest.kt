package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.domain.model.LibraryCandidate
import com.lucasserafin94.iptvburo.domain.model.LibraryOfferPolicy
import com.lucasserafin94.iptvburo.domain.model.MatchKind
import com.lucasserafin94.iptvburo.domain.model.ExternalCandidate
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * How real playlist titles line up against real TMDb titles.
 *
 * The matching policy was written against clean names. A provider's catalogue is not clean: entries
 * carry quality tags, language markers, release-group suffixes and no year at all. The user reports
 * films they own not being recognised, and these cases are transcribed from the shapes an Xtream
 * list actually contains.
 */
class RealCatalogueMatchTest {
    private fun local(
        title: String,
        year: Int? = null,
    ) = LibraryCandidate(
        localContentId = "MOVIE:1",
        title = title,
        year = year,
        kind = MatchKind.MOVIE,
    )

    private fun external(
        title: String,
        year: Int?,
    ) = ExternalCandidate(
        externalContentId = "tmdb:1",
        title = title,
        year = year,
        kind = MatchKind.MOVIE,
    )

    /**
     * The commonest shape in a real list, and the one that matters most.
     *
     * Providers prefix or suffix the quality. TMDb has the bare title. If this cannot match, most of
     * a catalogue is invisible to the feature.
     */
    @Test
    fun `a quality tag in the playlist name still matches`() {
        val found =
            LibraryOfferPolicy.findInLibrary(
                external = external("Duna", 2021),
                library = listOf(local("Duna 4K", 2021), local("Duna [1080p]", 2021), local("4K Duna", 2021)),
            )

        assertNotNull(found, "a title with a quality tag was not recognised")
    }

    /**
     * Xtream lists frequently carry no year at all, and the policy requires title *and* year to
     * claim a match. That combination silently hides everything unyeared.
     */
    @Test
    fun `a playlist entry with no year still matches a title with one`() {
        val found =
            LibraryOfferPolicy.findInLibrary(
                external = external("Duna", 2021),
                library = listOf(local("Duna", year = null)),
            )

        assertNotNull(found, "an unyeared playlist entry was not recognised")
    }

    @Test
    fun `the year in the name rather than the field still matches`() {
        val found =
            LibraryOfferPolicy.findInLibrary(
                external = external("Duna", 2021),
                library = listOf(local("Duna (2021)", year = null)),
            )

        assertNotNull(found, "a year inside the title was not read")
    }

    @Test
    fun `a dubbed or subtitled marker does not prevent a match`() {
        val found =
            LibraryOfferPolicy.findInLibrary(
                external = external("Duna", 2021),
                library = listOf(local("Duna DUBLADO", 2021), local("Duna - Legendado", 2021)),
            )

        assertNotNull(found, "a language marker blocked the match")
    }

    /** The safety property must survive all of the above: a remake is still not the same film. */
    @Test
    fun `loosening the match must not start accepting remakes`() {
        assertNull(
            LibraryOfferPolicy.findInLibrary(
                external = external("Duna", 2021),
                library = listOf(local("Duna 4K", 1984)),
            ),
            "a 1984 entry matched the 2021 film",
        )
    }

    @Test
    fun `a different film is still refused`() {
        assertNull(
            LibraryOfferPolicy.findInLibrary(
                external = external("Duna", 2021),
                library = listOf(local("Oppenheimer 4K", 2023)),
            ),
        )
    }
}
