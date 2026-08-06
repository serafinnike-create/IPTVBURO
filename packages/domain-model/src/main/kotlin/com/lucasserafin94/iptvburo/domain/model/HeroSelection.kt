package com.lucasserafin94.iptvburo.domain.model

/**
 * Choosing the titles that carry the home screen's banner.
 *
 * The old rule was one title picked by `dayOfYear % poolSize` — it changed daily, which is all it
 * did. It ignored rating, ignored release year, and had no way to avoid putting the same forgettable
 * catalogue filler in the largest slot on the screen two days running.
 *
 * What a banner is for is making a good title findable. So the selection scores what it is given and
 * takes the best few, then rotates through them, which also means a title the user has just seen is
 * not the one waiting for them when they come back.
 *
 * Pure: the date is an input, not a clock read. The same day and the same catalogue always produce
 * the same banner, so the home screen does not reshuffle itself on every recomposition.
 */
data class HeroCandidate(
    val id: String,
    val title: String,
    val year: Int? = null,
    val rating: Double? = null,
    val hasArtwork: Boolean = true,
)

object HeroSelection {
    /**
     * The banner rotation for [dayOfEpoch], best first.
     *
     * Returns at most [count] titles and never repeats one. An empty pool yields an empty list —
     * a home screen with nothing to show is an ordinary state, not an error.
     */
    fun rotationFor(
        candidates: List<HeroCandidate>,
        dayOfEpoch: Long,
        count: Int = DEFAULT_ROTATION,
    ): List<HeroCandidate> {
        // Artwork is not a preference here, it is a requirement: the banner is mostly image, and a
        // title without one renders as a large empty rectangle.
        val usable = candidates.filter { candidate -> candidate.hasArtwork }
        if (usable.isEmpty() || count <= 0) return emptyList()

        // Scored first, then a window of the best is rotated through. Taking strictly the top few
        // would show the same handful for ever; rotating a wider pool keeps the quality bar while
        // still changing what is on screen.
        val ranked =
            usable
                .sortedWith(
                    compareByDescending<HeroCandidate> { candidate -> score(candidate) }
                        // Ties broken by id, not by input order, so a re-fetch that returns the same
                        // titles in a different order does not reshuffle the banner.
                        .thenBy { candidate -> candidate.id },
                ).take(POOL_SIZE)

        val start = Math.floorMod(dayOfEpoch, ranked.size.toLong()).toInt()
        return (0 until minOf(count, ranked.size)).map { offset ->
            ranked[(start + offset) % ranked.size]
        }
    }

    /**
     * How much this title deserves the banner.
     *
     * Rating dominates, because it is the only direct signal of whether the title is any good. Recency
     * is a smaller bonus: a well-regarded older film is a better banner than a mediocre new one, but
     * between two equally rated titles the newer one is the better invitation.
     *
     * An unrated title scores as mid-range rather than zero. Providers leave the field empty
     * constantly, and treating that as "bad" would bar most of a catalogue from the banner.
     */
    private fun score(candidate: HeroCandidate): Double {
        val rating = candidate.rating?.takeIf { it > 0 } ?: NEUTRAL_RATING
        val recency =
            candidate.year
                ?.let { year -> ((year - RECENCY_BASE_YEAR).coerceIn(0, RECENCY_SPAN)).toDouble() / RECENCY_SPAN }
                ?: 0.0
        return rating + recency * RECENCY_WEIGHT
    }

    private const val DEFAULT_ROTATION = 5

    /**
     * How many titles the rotation is drawn from.
     *
     * Wide enough that the banner is not the same five titles all month, narrow enough that what
     * lands there is genuinely among the best the catalogue has.
     */
    private const val POOL_SIZE = 40

    /** What an unrated title is worth. Roughly "unremarkable", not "bad". */
    private const val NEUTRAL_RATING = 6.0

    private const val RECENCY_BASE_YEAR = 2000
    private const val RECENCY_SPAN = 30

    /** At most one rating point of advantage for being new. Quality still wins. */
    private const val RECENCY_WEIGHT = 1.0
}
