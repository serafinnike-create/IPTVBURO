package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the banner's suggestion promises.
 *
 * The rule that matters most is the one about staying quiet: with too little history, or with
 * nothing that shares a genre, this returns null. A recommendation nobody can explain is worse than
 * no recommendation, and the caller must not paper over it.
 */
class ViewingTasteTest {
    private fun item(
        id: String,
        genre: String?,
    ) = BrowsableItem(id = id, title = id, genre = genre, year = 2026, rating = null)

    @Test
    fun `nothing is suggested from too little history`() {
        val suggestion =
            ViewingTaste.suggest(
                candidates = listOf(item("a", "Ação"), item("b", "Comédia")),
                watchedGenres = listOf("Ação"),
                watchedIds = emptySet(),
            )

        assertNull(suggestion, "One viewing is not a habit, and guessing from it is not a service.")
    }

    @Test
    fun `nothing is suggested when no candidate shares a genre`() {
        val suggestion =
            ViewingTaste.suggest(
                candidates = listOf(item("a", "Documentário"), item("b", "Musical")),
                watchedGenres = listOf("Terror", "Terror", "Suspense"),
                watchedIds = emptySet(),
            )

        assertNull(suggestion)
    }

    @Test
    fun `the strongest shared genre wins`() {
        val suggestion =
            ViewingTaste.suggest(
                // Both match something watched; only one matches the genre watched most.
                candidates = listOf(item("occasional", "Suspense"), item("favourite", "Terror")),
                watchedGenres = listOf("Terror", "Terror", "Terror", "Suspense"),
                watchedIds = emptySet(),
            )

        assertEquals("favourite", suggestion?.id)
    }

    @Test
    fun `something already watched is never suggested`() {
        val suggestion =
            ViewingTaste.suggest(
                candidates = listOf(item("seen", "Terror"), item("unseen", "Terror")),
                watchedGenres = listOf("Terror", "Terror"),
                watchedIds = setOf("seen"),
            )

        assertEquals("unseen", suggestion?.id, "Recommending what somebody just finished is noise.")
    }

    @Test
    fun `genres are counted across separators and ordered by how often they appear`() {
        // "Ação" three times, "Aventura" twice, "Comédia" once — an unambiguous order, so the
        // assertion tests the counting rather than the alphabetical tie-break.
        val preferred =
            ViewingTaste.preferredGenres(
                listOf("Ação, Aventura", "Ação / Comédia", "Ação; Aventura", null, ""),
            )

        assertEquals(listOf("ação", "aventura", "comédia"), preferred)
        assertTrue(
            preferred.none(String::isBlank),
            "A null or empty entry must not become a genre of its own.",
        )
    }
}
