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
    /**
     * The categories the provider files this title under, for matching against what the user
     * actually watches. Empty is ordinary — plenty of playlists carry no categories at all.
     */
    val categoryIds: List<String> = emptyList(),
    /** Whether this is a series rather than a film. The banner deliberately carries both. */
    val isSeries: Boolean = false,
    /**
     * When the provider added this to the catalogue, if it says.
     *
     * Distinct from [year]: a 2026 film added six months ago is a current release, but it is not one
     * of *today's* arrivals. The banner leads with what turned up today, and this is the only field
     * that can tell them apart.
     */
    val addedAtEpochSeconds: Long? = null,
)

/**
 * What the viewer has shown an interest in, derived from what they have opened.
 *
 * Deliberately shallow. This counts categories, nothing else: no profile of the person, no attempt
 * to infer taste beyond "you have watched four things filed under this". It lives on the machine,
 * is rebuilt from history that the user can clear, and never leaves the app.
 *
 * Empty means "nothing known yet", which is the state every new installation starts in and the one
 * the banner has to look right in.
 */
data class ViewerAffinity(
    /** How many watched titles fell under each category id. */
    val watchesByCategory: Map<String, Int> = emptyMap(),
) {
    /** Whether there is enough history for the preference to mean anything. */
    val isKnown: Boolean get() = watchesByCategory.values.sum() >= MINIMUM_WATCHES

    /**
     * How well [candidate] matches what the viewer watches, from 0.0 to 1.0.
     *
     * The strongest category the title belongs to decides, rather than the sum: a film filed under
     * six categories is not six times more relevant than one filed under the right single category.
     */
    fun affinityFor(candidate: HeroCandidate): Double {
        if (!isKnown || candidate.categoryIds.isEmpty()) return 0.0
        val strongest = watchesByCategory.values.maxOrNull()?.takeIf { it > 0 } ?: return 0.0
        val best = candidate.categoryIds.mapNotNull { id -> watchesByCategory[id] }.maxOrNull() ?: 0
        return best.toDouble() / strongest
    }

    companion object {
        /**
         * Below this the history is noise.
         *
         * Three titles is enough to see a pattern and few enough that the banner starts reflecting
         * the viewer within a first evening. Under it the banner behaves exactly as it always has.
         */
        const val MINIMUM_WATCHES = 3

        /** Builds an affinity from the categories of what has been watched, most recent first. */
        fun from(watchedCategoryIds: List<List<String>>): ViewerAffinity {
            val counts = mutableMapOf<String, Int>()
            // Only the recent past. Taste changes, and a season watched a year ago should not
            // outweigh what the viewer has been opening this week.
            watchedCategoryIds.take(RECENT_WATCH_WINDOW).forEach { categories ->
                categories.forEach { id -> counts[id] = (counts[id] ?: 0) + 1 }
            }
            return ViewerAffinity(counts)
        }

        /** How far back the preference looks. Recent enough to follow a change of taste. */
        private const val RECENT_WATCH_WINDOW = 40
    }
}

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
        /**
         * What the viewer tends to watch, which nudges the ranking towards them.
         *
         * Defaulted to empty so every existing caller and test keeps its previous behaviour: with
         * no history the banner scores exactly as it did before this existed.
         */
        affinity: ViewerAffinity = ViewerAffinity(),
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
                    compareByDescending<HeroCandidate> { candidate -> score(candidate, affinity) }
                        // Ties broken by id, not by input order, so a re-fetch that returns the same
                        // titles in a different order does not reshuffle the banner.
                        .thenBy { candidate -> candidate.id },
                ).take(POOL_SIZE)

        // `mod` rather than `%`: the day counter can be negative for a clock set before the epoch,
        // and `%` would then return a negative index. Kotlin's `mod` matches Math.floorMod.
        val start = dayOfEpoch.mod(ranked.size.toLong()).toInt()
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
    private fun score(candidate: HeroCandidate, affinity: ViewerAffinity): Double {
        val rating = candidate.rating?.takeIf { it > 0 } ?: NEUTRAL_RATING
        val recency =
            candidate.year
                ?.let { year -> ((year - RECENCY_BASE_YEAR).coerceIn(0, RECENCY_SPAN)).toDouble() / RECENCY_SPAN }
                ?: 0.0
        // What the viewer watches, worth about a rating point and a half at most.
        //
        // Enough to bring a well-liked title from a category they favour ahead of an equally rated
        // one they never open, and not enough to promote something poor. A banner that showed a
        // weak title because it matched a habit would teach the viewer to ignore the banner.
        return rating + recency * RECENCY_WEIGHT + affinity.affinityFor(candidate) * AFFINITY_WEIGHT
    }

    /**
     * How many titles the banner cycles through in a day.
     *
     * Twenty rather than five. Five is a handful somebody recognises by the second day, and the
     * banner stops being a reason to look at the home screen. The pool it draws from is forty, so
     * twenty is still the better half of it rather than the whole catalogue.
     */
    private const val DEFAULT_ROTATION = 20

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

    /**
     * At most one and a half rating points for matching what the viewer watches.
     *
     * Chosen to be decisive between near-equals and powerless against a real quality gap: a 6.0 in
     * a favoured category still loses to an 8.0 outside it. The banner is the largest thing on the
     * screen, and filling it with something weak because it fits a pattern is how a recommendation
     * feature loses the viewer's trust.
     */
    private const val AFFINITY_WEIGHT = 1.5

    /**
     * How old a title may be and still count as a new release.
     *
     * Two years rather than one: a catalogue is not a cinema listing, and a film from last year is
     * still what somebody means by "new" when they are looking at what to watch tonight.
     */
    const val NEW_RELEASE_YEARS = 2

    /** Before this, a title is one of the old ones the mix deliberately keeps room for. */
    const val OLD_RELEASE_YEARS = 15

    /**
     * How many of each kind the rotation aims to carry. See [mixed].
     *
     * One each of anime, an old film and a series; three of the day's own arrivals; and everything
     * after that a current release. The banner is meant to be about what is new — the other three
     * are there so it is not *only* that, not so it becomes a survey of the catalogue.
     */
    const val ANIME_SLOTS = 1
    const val OLD_FILM_SLOTS = 1
    const val SERIES_SLOTS = 1
    const val TODAY_SLOTS = 3

    /** The category words that mark a title as anime, matched case-insensitively. */
    private val ANIME_WORDS = listOf("anime", "animação japonesa", "animacao japonesa")

    /** A day, in seconds: how recently a title must have arrived to count as today's. */
    private const val ARRIVED_TODAY_SECONDS = 86_400L

    /**
     * Whether this turned up in the catalogue within the last day.
     *
     * False when the provider does not date its entries, which many do not — a title of unknown age
     * is not claimed as new, it simply competes for the ordinary slots. A date in the future is
     * treated the same way: a provider's clock is not something to trust into the banner's lead.
     */
    private fun arrivedToday(
        candidate: HeroCandidate,
        nowEpochSeconds: Long,
    ): Boolean {
        if (nowEpochSeconds <= 0L) return false
        val addedAt = candidate.addedAtEpochSeconds ?: return false
        val age = nowEpochSeconds - addedAt
        return age in 0 until ARRIVED_TODAY_SECONDS
    }

    /** Whether the provider files this title under anime. */
    fun isAnime(candidate: HeroCandidate): Boolean =
        candidate.categoryIds.any { category ->
            val lower = category.lowercase()
            ANIME_WORDS.any { word -> lower.contains(word) }
        }

    /** How old a title is, as the mix counts it. Untitled years count as middle-aged, not ancient. */
    private fun ageBand(candidate: HeroCandidate, thisYear: Int): String {
        val year = candidate.year ?: return "middle"
        return when {
            year >= thisYear - NEW_RELEASE_YEARS -> "new"
            year < thisYear - OLD_RELEASE_YEARS -> "old"
            else -> "middle"
        }
    }

    /**
     * The rotation, rearranged so it is not all one thing.
     *
     * Ranked purely by score, the banner fills with whatever the catalogue has most of — and a
     * viewer scrolling past twenty titles from the same year and the same shelf learns nothing
     * about what else is there. The mix keeps the quality order inside each kind and only decides
     * how many of each kind appear:
     *
     *  - three of the day's own arrivals lead, because that is what the banner is for;
     *  - one anime, a shelf people either follow closely or never see at all;
     *  - one series, so a banner of nothing but films does not say the app has no series;
     *  - one old film, so the catalogue's depth shows;
     *  - and everything after that a current release.
     *
     * Everything not claimed by a slot keeps its place behind them, so nothing is lost — a small
     * catalogue with no old titles simply carries more new ones rather than showing fewer.
     */
    fun mixed(
        rotation: List<HeroCandidate>,
        thisYear: Int,
        /**
         * Now, for deciding what counts as arriving today.
         *
         * Passed rather than read, so the same catalogue and the same day always produce the same
         * banner instead of drifting as the clock moves during a session.
         */
        nowEpochSeconds: Long = 0L,
        /**
         * Varies which titles fill each slot, without changing what the slots are.
         *
         * The composition is the day's: what arrived today leads, then one anime, one series, one
         * older film, then current releases. That part is deliberate and stays fixed. What was not
         * deliberate is that the *same* titles filled those slots on every launch — open the app
         * three times in an afternoon and the banner played the same trailers in the same order,
         * which is what was reported.
         *
         * Zero keeps the old exact ordering, which is what the tests pin: they are checking the
         * composition rules, and a shuffled list would make them assert nothing.
         */
        shuffleSeed: Long = 0L,
    ): List<HeroCandidate> {
        if (rotation.size <= ANIME_SLOTS + OLD_FILM_SLOTS + SERIES_SLOTS + TODAY_SLOTS) return rotation

        val taken = LinkedHashSet<String>()
        val picked = mutableListOf<HeroCandidate>()

        // Ranked order, or the same titles lightly reordered when a seed is given. Each category
        // still draws from the whole rotation, so a shuffle changes who appears rather than which
        // kinds do.
        val pool =
            if (shuffleSeed == 0L) rotation else rotation.shuffled(kotlin.random.Random(shuffleSeed))

        fun take(
            limit: Int,
            predicate: (HeroCandidate) -> Boolean,
        ) {
            pool
                .asSequence()
                .filter { it.id !in taken && predicate(it) }
                .take(limit)
                .forEach { picked += it; taken += it.id }
        }

        // What arrived today leads, because that is what the banner is for.
        take(TODAY_SLOTS) { arrivedToday(it, nowEpochSeconds) }
        // Then one of each of the three the banner would otherwise never show.
        take(ANIME_SLOTS, ::isAnime)
        take(SERIES_SLOTS) { it.isSeries }
        take(OLD_FILM_SLOTS) { !it.isSeries && ageBand(it, thisYear) == "old" }
        // And everything after that is a current release, which is the rest of the rotation.
        take(rotation.size) { ageBand(it, thisYear) == "new" }

        // Then everything else, still in the order the pool gave.
        pool.forEach { candidate ->
            if (candidate.id !in taken) {
                picked += candidate
                taken += candidate.id
            }
        }
        return picked
    }
}
