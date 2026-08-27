package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Telling a cover from a stamp a provider hands out ten thousand times.
 *
 * The numbers here are from a real list, not invented: 52,201 covers, 30,301 distinct, and one
 * address repeated 10,353 times while the next most repeated appeared six. That gap is what makes
 * counting a safe test — there is nothing in between to misjudge.
 */
class PlaceholderArtworkTest {
    @Test
    fun `a cover shared by thousands is not a cover`() {
        val urls = List(10_353) { "http://provider.invalid/ACU" }.asSequence()
        assertEquals(setOf("http://provider.invalid/ACU"), PlaceholderArtwork.detect(urls))
    }

    @Test
    fun `a genuine cover is left alone`() {
        // A cover belongs to one film. Treating it as a placeholder would erase artwork the list
        // actually carries, which is worse than the problem being solved.
        val urls = (1..500).map { "http://provider.invalid/poster-$it.jpg" }.asSequence()
        assertTrue(PlaceholderArtwork.detect(urls).isEmpty())
    }

    @Test
    fun `a title carried at several qualities keeps its cover`() {
        // Providers list the same film in 4K, HD and SD, in two dubbings, under three category
        // prefixes. That is ordinary duplication and must stay well under the threshold.
        val urls = List(12) { "http://provider.invalid/same-film.jpg" }.asSequence()
        assertTrue(
            PlaceholderArtwork.detect(urls).isEmpty(),
            "twelve copies of one film is duplication, not a placeholder",
        )
    }

    @Test
    fun `both kinds in one catalogue are separated`() {
        // What a real list looks like: a placeholder among thousands of real covers.
        val real = (1..300).map { "http://provider.invalid/poster-$it.jpg" }
        val placeholder = List(4_000) { "http://provider.invalid/XXX-ADULT" }
        val found = PlaceholderArtwork.detect((real + placeholder).asSequence())
        assertEquals(setOf("http://provider.invalid/XXX-ADULT"), found)
    }

    @Test
    fun `titles with no cover at all are ignored`() {
        // Null and blank are absence, not a shared address; counting them would produce a
        // "placeholder" of empty string and match every uncovered row.
        val urls = sequenceOf<String?>(null, "", "   ", null, "")
        assertTrue(PlaceholderArtwork.detect(urls).isEmpty())
    }

    @Test
    fun `an empty catalogue finds nothing`() {
        assertTrue(PlaceholderArtwork.detect(emptySequence()).isEmpty())
    }

    @Test
    fun `the threshold sits far from both sides`() {
        // The gap measured on a real list is three orders of magnitude, so this number is not
        // delicate — but it must stay above ordinary duplication and below a real placeholder.
        assertTrue(PlaceholderArtwork.SHARED_COVER_THRESHOLD > 15, "above duplication")
        assertTrue(PlaceholderArtwork.SHARED_COVER_THRESHOLD < 500, "below a placeholder")
    }

    @Test
    fun `spacing around an address does not hide it`() {
        // A provider that pads its field would otherwise produce two counts for one address, and
        // neither would reach the threshold.
        val urls = List(30) { if (it % 2 == 0) " http://p.invalid/A " else "http://p.invalid/A" }
        assertFalse(PlaceholderArtwork.detect(urls.asSequence()).isEmpty())
    }
}
