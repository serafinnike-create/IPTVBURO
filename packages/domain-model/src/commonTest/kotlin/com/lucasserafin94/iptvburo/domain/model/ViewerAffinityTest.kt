package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The banner leaning towards what the viewer actually watches.
 *
 * The rule is deliberately shallow: it counts the categories of titles that have been opened, and
 * nothing else. No profile of the person, no inference beyond "you have watched several things
 * filed under this". It is built on the machine from history the user can clear.
 *
 * What it must not do is put a weak title in the largest slot on the screen because it happens to
 * match a habit. A banner that does that teaches the viewer to ignore the banner.
 */
class ViewerAffinityTest {
    private fun candidate(
        id: String,
        rating: Double? = null,
        year: Int? = 2024,
        categories: List<String> = emptyList(),
    ) = HeroCandidate(
        id = id,
        title = id,
        year = year,
        rating = rating,
        hasArtwork = true,
        categoryIds = categories,
    )

    @Test
    fun `nothing is known until a few titles have been watched`() {
        val barely = ViewerAffinity.from(listOf(listOf("action"), listOf("action")))

        assertFalse(barely.isKnown, "two watches is noise, not a preference")
        assertEquals(0.0, barely.affinityFor(candidate("x", categories = listOf("action"))))
    }

    @Test
    fun `a repeated category becomes the strongest preference`() {
        val watched =
            ViewerAffinity.from(
                listOf(listOf("action"), listOf("action"), listOf("action"), listOf("drama")),
            )

        assertTrue(watched.isKnown)
        assertEquals(1.0, watched.affinityFor(candidate("a", categories = listOf("action"))))
        assertTrue(watched.affinityFor(candidate("d", categories = listOf("drama"))) < 1.0)
        assertEquals(0.0, watched.affinityFor(candidate("k", categories = listOf("korean"))))
    }

    /**
     * A title filed under six categories is not six times more relevant.
     *
     * Providers file generously — a single film can carry "action", "2024", "dubbed" and three
     * more. Summing the matches would make the most heavily tagged title win every time, which is
     * a measure of the provider's filing rather than of the viewer's taste.
     */
    @Test
    fun `the strongest matching category decides - not the sum`() {
        val watched =
            ViewerAffinity.from(
                listOf(listOf("action"), listOf("action"), listOf("action"), listOf("drama")),
            )

        val focused = watched.affinityFor(candidate("one", categories = listOf("action")))
        val scattered =
            watched.affinityFor(candidate("many", categories = listOf("action", "drama", "comedy")))

        assertEquals(focused, scattered, "extra categories must not inflate the match")
    }

    /**
     * Taste is allowed to change.
     *
     * A season watched a year ago should not outweigh what the viewer has been opening this week,
     * so only the recent past counts.
     */
    @Test
    fun `only recent history counts`() {
        val recentActionThenOldDrama =
            ViewerAffinity.from(
                List(40) { listOf("action") } + List(100) { listOf("drama") },
            )

        assertTrue(
            recentActionThenOldDrama.affinityFor(candidate("a", categories = listOf("action"))) >
                recentActionThenOldDrama.affinityFor(candidate("d", categories = listOf("drama"))),
            "history from long ago must not outweigh the recent past",
        )
    }

    /**
     * The banner still prefers a better title, whatever the viewer watches.
     *
     * This is the property that keeps the feature honest: a 6.0 in a favoured category must lose to
     * an 8.0 outside it.
     */
    @Test
    fun `a strong title outside the preference still beats a weak one inside it`() {
        val watched = ViewerAffinity.from(List(10) { listOf("action") })

        val rotation =
            HeroSelection.rotationFor(
                candidates =
                    listOf(
                        candidate("weak-but-liked", rating = 6.0, categories = listOf("action")),
                        candidate("strong-elsewhere", rating = 8.0, categories = listOf("drama")),
                    ),
                dayOfEpoch = 0,
                count = 1,
                affinity = watched,
            )

        assertEquals(
            "strong-elsewhere",
            rotation.single().id,
            "a habit must not put a weaker title in the largest slot on the screen",
        )
    }

    /**
     * Between near-equals, the viewer's taste is what decides.
     *
     * This is the whole point of the feature: two titles the app has no other reason to separate,
     * and the one they are more likely to want goes first.
     */
    @Test
    fun `between equals the preferred category wins`() {
        val watched = ViewerAffinity.from(List(10) { listOf("action") })

        val rotation =
            HeroSelection.rotationFor(
                candidates =
                    listOf(
                        candidate("drama-pick", rating = 7.5, categories = listOf("drama")),
                        candidate("action-pick", rating = 7.5, categories = listOf("action")),
                    ),
                dayOfEpoch = 0,
                count = 1,
                affinity = watched,
            )

        assertEquals("action-pick", rotation.single().id)
    }

    /**
     * A new installation behaves exactly as it did before this existed.
     *
     * The first evening is when a customer decides whether the app is any good, and it is also the
     * moment there is no history at all.
     */
    @Test
    fun `with no history the ranking is unchanged`() {
        val candidates =
            listOf(
                candidate("best", rating = 9.0, categories = listOf("drama")),
                candidate("middle", rating = 7.0, categories = listOf("action")),
                candidate("worst", rating = 5.0, categories = listOf("action")),
            )

        val withoutAffinity = HeroSelection.rotationFor(candidates, dayOfEpoch = 0, count = 3)
        val withEmptyAffinity =
            HeroSelection.rotationFor(candidates, dayOfEpoch = 0, count = 3, affinity = ViewerAffinity())

        assertEquals(withoutAffinity.map(HeroCandidate::id), withEmptyAffinity.map(HeroCandidate::id))
        assertEquals("best", withoutAffinity.first().id)
    }

    /** A catalogue with no categories at all must not break the ranking. */
    @Test
    fun `titles without categories are ranked on their own merits`() {
        val watched = ViewerAffinity.from(List(10) { listOf("action") })

        val rotation =
            HeroSelection.rotationFor(
                candidates =
                    listOf(
                        candidate("uncategorised-good", rating = 9.0),
                        candidate("categorised-poor", rating = 5.0, categories = listOf("action")),
                    ),
                dayOfEpoch = 0,
                count = 1,
                affinity = watched,
            )

        assertEquals("uncategorised-good", rotation.single().id)
    }
}
