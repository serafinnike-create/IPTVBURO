package com.lucasserafin94.iptvburo.desktop.app

import com.lucasserafin94.iptvburo.domain.model.normalisedForMatching
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Finding a title in a history that has grown large.
 *
 * Writing this is what caught the bug it now guards: a plain `contains(ignoreCase = true)` compares
 * code points, so in a Portuguese catalogue "chefao" found nothing while "Chefão" sat on screen.
 * The gallery matches on the same normalised form the library matcher uses, which strips accents
 * and provider decoration alike.
 *
 * The filter itself is one expression inside a composable, so it is restated here rather than
 * driven through Compose — what is under test is the matching rule, and a UI harness would test
 * the framework instead.
 */
class HistorySearchTest {
    private val titles =
        listOf(
            "O Poderoso Chefão",
            "Duna: Parte Dois",
            "Duna 4K",
            "A Origem",
            "Interestelar",
        )

    /** Exactly what HistoryGallery does. */
    private fun search(query: String): List<String> {
        val needle = query.trim().normalisedForMatching()
        return if (needle.isBlank()) {
            titles
        } else {
            titles.filter { title -> title.normalisedForMatching().contains(needle) }
        }
    }

    @Test
    fun `an empty query keeps everything`() {
        assertEquals(titles, search(""))
        assertEquals(titles, search("   "), "whitespace is not a search")
    }

    /** Nobody types the capital, and a search that demands one is a search that finds nothing. */
    @Test
    fun `case is ignored`() {
        assertEquals(listOf("Duna: Parte Dois", "Duna 4K"), search("DUNA"))
        assertEquals(listOf("Duna: Parte Dois", "Duna 4K"), search("duna"))
    }

    /** The bug this test found: an unaccented query must find the accented title. */
    @Test
    fun `an accented title is found without typing the accent`() {
        assertEquals(listOf("O Poderoso Chefão"), search("chefao"))
        assertEquals(listOf("O Poderoso Chefão"), search("chefão"), "and with the accent too")
    }

    /** Partway through a title is how anyone actually searches. */
    @Test
    fun `a fragment matches`() {
        assertEquals(listOf("Interestelar"), search("estel"))
    }

    /**
     * Punctuation between words is the provider's, not the user's.
     *
     * The normaliser drops it, so a title written "Duna: Parte Dois" is found by typing it the way
     * anyone would say it.
     */
    @Test
    fun `punctuation and spacing do not have to be reproduced`() {
        assertEquals(listOf("Duna: Parte Dois"), search("duna parte"))
        assertEquals(listOf("Duna: Parte Dois"), search("Duna: Parte"))
    }

    /**
     * Quality tags are decoration and are stripped from both sides.
     *
     * A user who types "duna 4k" is looking for Duna, and one who types "duna" should still find
     * the copy the provider labelled "Duna 4K".
     */
    @Test
    fun `provider decoration does not block a match`() {
        assertTrue("Duna 4K" in search("duna"), "was ${search("duna")}")
        assertTrue("Duna 4K" in search("duna 4k"), "was ${search("duna 4k")}")
    }

    @Test
    fun `a query matching nothing returns nothing rather than everything`() {
        assertTrue(search("zzzz").isEmpty(), "a failed search must not silently show the full list")
    }
}
