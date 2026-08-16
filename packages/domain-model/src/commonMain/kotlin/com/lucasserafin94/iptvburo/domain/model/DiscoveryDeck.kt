package com.lucasserafin94.iptvburo.domain.model

/**
 * One title as the Descobrir deck needs it, independent of where it came from.
 *
 * Deliberately not a catalogue row: the deck ranks and the app draws, and keeping the two apart is
 * what lets the ranking be tested without a database, a network or a screen.
 */
data class DiscoveryCandidate(
    val id: String,
    val title: String,
    val genres: List<String> = emptyList(),
    val year: Int? = null,
    val rating: Double? = null,
    val isSeries: Boolean = false,
)

/**
 * What this profile has shown it likes, gathered from what they already did.
 *
 * Built from favourites and from what they actually watched rather than from a questionnaire: a
 * taste picker asks people to describe themselves, and what they say and what they watch are not
 * the same list.
 *
 * [watchedGenres] carries repeats on purpose — a genre watched six times counts six times, which is
 * what makes it outweigh one watched once.
 */
data class TasteProfile(
    val favouriteGenres: List<String> = emptyList(),
    val watchedGenres: List<String> = emptyList(),
    /** Ids already favourited, watched or dismissed. Never offered again. */
    val seenIds: Set<String> = emptySet(),
) {
    /**
     * How much each genre is worth, normalised so one strong taste cannot swamp the rest.
     *
     * A favourite counts double a view: choosing to keep something is a deliberate statement, while
     * watching includes everything abandoned after four minutes.
     */
    val genreWeights: Map<String, Double> by lazy {
        val counted = mutableMapOf<String, Double>()
        favouriteGenres.forEach { genre ->
            val key = genre.normaliseGenre() ?: return@forEach
            counted[key] = (counted[key] ?: 0.0) + FAVOURITE_WEIGHT
        }
        watchedGenres.forEach { genre ->
            val key = genre.normaliseGenre() ?: return@forEach
            counted[key] = (counted[key] ?: 0.0) + WATCHED_WEIGHT
        }
        // Divided by a fixed ceiling rather than by the highest score present.
        //
        // Normalising against the maximum looked tidier and erased the thing being measured: with
        // one favourite and nothing else, that favourite is the maximum and lands on 1.0 — and so
        // does a single view, in a profile that only ever watched. The two signals then rank
        // identically, which is exactly what favouriting twice as heavily was meant to avoid.
        //
        // A fixed divisor keeps the ratio between them and still bounds the result, so one heavily
        // watched genre cannot grow without limit and swamp everything else.
        counted
            .mapValues { (_, score) -> (score / WEIGHT_CEILING).coerceAtMost(1.0) }
            .filterValues { weight -> weight > 0.0 }
    }

    val hasTaste: Boolean get() = genreWeights.isNotEmpty()

    private companion object {
        const val FAVOURITE_WEIGHT = 2.0
        const val WATCHED_WEIGHT = 1.0

        /**
         * What counts as a fully-formed taste for one genre.
         *
         * Five favourites, or ten views, or any mix reaching the same total. Past this the genre is
         * already at full weight, so somebody with a hundred views of one thing does not drown out
         * everything else they watch.
         */
        const val WEIGHT_CEILING = 10.0
    }
}

/** What a viewer said about one card. */
enum class DiscoveryVerdict { KEPT, SKIPPED }

/**
 * What this session's swipes have said, which is a different thing from the watch history.
 *
 * [TasteProfile] is "what you tend to watch", built from finished viewing. This is "what you just
 * said about a poster", and the two disagree usefully: somebody can watch a lot of one genre and
 * still be plainly uninterested in the next film from it.
 *
 * It exists because a deck that ignored the last ten swipes would keep offering the same kind of
 * thing somebody had just turned down ten times — the fastest way to make the feature feel deaf.
 *
 * Immutable: a deck is scored against a snapshot rather than against a value changing under it
 * while the list is being sorted.
 */
data class SessionTaste(
    /** Net leaning per genre: positive for kept, negative for skipped. */
    val leaningByGenre: Map<String, Int> = emptyMap(),
) {
    /**
     * How much this session leans towards a title, from -1 to 1.
     *
     * The strongest genre decides rather than the sum: a film filed under six genres is not six
     * times more interesting than one filed under the single right one.
     */
    fun leaningFor(genres: List<String>): Double {
        if (genres.isEmpty() || leaningByGenre.isEmpty()) return 0.0
        val strongest =
            leaningByGenre.values.maxOfOrNull { value -> if (value < 0) -value else value }
                ?.takeIf { it > 0 } ?: return 0.0
        val best =
            genres
                .mapNotNull { genre -> genre.normaliseGenre() }
                .mapNotNull { genre -> leaningByGenre[genre] }
                .maxOrNull() ?: return 0.0
        return best.toDouble() / strongest
    }

    /** The taste after one decision. A new value rather than a mutation of this one. */
    fun after(genres: List<String>, verdict: DiscoveryVerdict): SessionTaste {
        val keys = genres.mapNotNull { genre -> genre.normaliseGenre() }
        if (keys.isEmpty()) return this
        val delta = if (verdict == DiscoveryVerdict.KEPT) KEPT_WEIGHT else SKIPPED_WEIGHT
        val next = leaningByGenre.toMutableMap()
        keys.forEach { key ->
            // Clamped, so a long run of one answer cannot make a genre outweigh everything the
            // viewer has ever watched. This is a nudge, not a verdict.
            next[key] = ((next[key] ?: 0) + delta).coerceIn(-CEILING, CEILING)
        }
        return SessionTaste(next)
    }

    private companion object {
        /** Keeping something is a clear statement. */
        const val KEPT_WEIGHT = 2

        /**
         * Passing over is a weaker one.
         *
         * People skip for reasons that have nothing to do with genre — already seen it, poor
         * poster, not in the mood — so a skip must not condemn a whole genre the way keeping one
         * endorses it.
         */
        const val SKIPPED_WEIGHT = -1

        /** How far a single session may push one genre. */
        const val CEILING = 6
    }
}

/**
 * Builds the Descobrir deck: a small hand of titles this profile is likely to want.
 *
 * ## Why a hand and not a feed
 *
 * [DECK_SIZE] at a time. The deck is meant to be finished in a sitting — an endless stream turns a
 * game into a chore, and somebody who swipes fifteen cards has told us something we can use for the
 * next fifteen. Running out is a real state with its own screen, not a failure.
 *
 * ## What the ranking does
 *
 * Three things, in descending importance:
 *
 * 1. **Genre match.** The heart of it: a title sharing genres with what the viewer keeps and
 *    watches scores highest.
 * 2. **Rating**, as a mild tiebreaker. Never dominant — a well-reviewed documentary should not
 *    outrank a thriller for somebody who only watches thrillers.
 * 3. **A deliberate stranger.** [SURPRISE_SLOTS] of the deck ignore taste entirely. A deck that
 *    only ever confirms what somebody already likes stops being discovery and becomes a mirror,
 *    and the whole appeal of swiping is the occasional thing you did not expect.
 *
 * Nothing already favourited, watched or dismissed is offered — see [TasteProfile.seenIds].
 *
 * With no taste yet, the deck is the best-rated titles the catalogue has: a first-run profile has
 * told us nothing, and guessing would be worse than offering what most people like.
 */
object DiscoveryDeck {
    const val DECK_SIZE = 15

    /**
     * Cards in a deck that ignore taste.
     *
     * Two of fifteen. Enough that most decks carry one, few enough that the deck still reads as
     * chosen for the viewer rather than shuffled.
     */
    const val SURPRISE_SLOTS = 2

    fun build(
        candidates: List<DiscoveryCandidate>,
        taste: TasteProfile,
        /** What this session's swipes have said. Empty on the first deck of a sitting. */
        session: SessionTaste = SessionTaste(),
        /** Injected so a deck can be reproduced in a test rather than asserted loosely. */
        shuffleSeed: Int = 0,
    ): List<DiscoveryCandidate> {
        val eligible =
            candidates
                .asSequence()
                .filter { candidate -> candidate.id.isNotBlank() }
                .filter { candidate -> candidate.id !in taste.seenIds }
                .distinctBy { candidate -> candidate.id }
                .toList()
        if (eligible.isEmpty()) return emptyList()
        if (!taste.hasTaste && session.leaningByGenre.isEmpty()) {
            // Nothing known about this profile yet: the best the catalogue has, which is a better
            // opening hand than a random one and makes no claim it cannot support.
            return eligible
                .sortedWith(compareByDescending<DiscoveryCandidate> { it.rating ?: 0.0 }.thenBy { it.title })
                .take(DECK_SIZE)
        }

        val ranked =
            eligible
                .map { candidate -> candidate to score(candidate, taste, session) }
                .sortedWith(
                    compareByDescending<Pair<DiscoveryCandidate, Double>> { (_, score) -> score }
                        // Title as the final tiebreak, so a deck built twice from the same
                        // catalogue comes out the same rather than reshuffling under the user.
                        .thenBy { (candidate, _) -> candidate.title },
                ).map { (candidate, _) -> candidate }

        val matched = ranked.take(DECK_SIZE - SURPRISE_SLOTS)

        // The surprise has to be a genre they do not already lean on.
        //
        // Taking the next few off the same ranked list looked like a departure and was not: a
        // catalogue holding twenty action films and five documentaries ranks all twenty first, so
        // positions fourteen and fifteen are still action. The deck then contained nothing the
        // viewer had not effectively asked for, which is the one thing this slot exists to prevent.
        //
        // Chosen by rating among the genres they *do not* watch, so it is a departure with
        // something going for it rather than a title picked at random.
        val leanedOn = taste.genreWeights.keys
        val strangers =
            ranked
                .filterNot { candidate -> candidate in matched }
                .filter { candidate ->
                    candidate.genres.mapNotNull { it.normaliseGenre() }.none { it in leanedOn }
                }
        val surprises =
            strangers
                .rotate(shuffleSeed)
                .take(SURPRISE_SLOTS)
                // A catalogue with nothing outside their taste yields none, and the deck simply
                // fills up with matches rather than coming back short.
                .ifEmpty { ranked.drop(DECK_SIZE - SURPRISE_SLOTS).take(SURPRISE_SLOTS) }
        return (matched + surprises).distinctBy { it.id }.take(DECK_SIZE)
    }

    /**
     * How well one title fits this taste, from 0 upwards.
     *
     * Internal so the weighting can be asserted directly — the deck is the visible behaviour, but a
     * test that can only see the final order cannot say *why* something ranked where it did.
     */
    internal fun score(
        candidate: DiscoveryCandidate,
        taste: TasteProfile,
        session: SessionTaste = SessionTaste(),
    ): Double {
        val genreScore =
            candidate.genres
                .mapNotNull { genre -> genre.normaliseGenre() }
                .sumOf { genre -> taste.genreWeights[genre] ?: 0.0 }
        // Rating contributes at most a fraction of a single genre match, which is what keeps it a
        // tiebreaker: two titles that fit equally well are separated by it, and a title that fits
        // badly is not rescued by it.
        val ratingScore = ((candidate.rating ?: 0.0) / 10.0).coerceIn(0.0, 1.0) * RATING_WEIGHT
        // What this sitting has said, weighted above the watch history on purpose: the last ten
        // swipes are a fresher statement than a month of viewing, and a deck that ignored them
        // would keep offering what somebody had just turned down.
        val sessionScore = session.leaningFor(candidate.genres) * SESSION_WEIGHT
        return genreScore + ratingScore + sessionScore
    }

    private const val RATING_WEIGHT = 0.35

    /**
     * How much this sitting's swipes count against the longer history.
     *
     * Above a single genre match, so a run of answers visibly moves the next deck — that responsive
     * feeling is most of the appeal — but not so far that one skip erases what somebody has watched
     * for months.
     */
    private const val SESSION_WEIGHT = 1.2

    /** Rotation rather than a shuffle: no random source, and the same seed gives the same deck. */
    private fun <T> List<T>.rotate(by: Int): List<T> {
        if (isEmpty()) return this
        val offset = ((by % size) + size) % size
        return subList(offset, size) + subList(0, offset)
    }
}

/**
 * A genre reduced to something comparable, or null when it is not a genre at all.
 *
 * Providers write these inconsistently — `"Ação"`, `"acao"`, `"Action / Adventure"` — and a match
 * on the raw string would treat those as three different tastes. Splitting is left to the caller so
 * this stays one decision.
 */
internal fun String.normaliseGenre(): String? {
    val trimmed = trim().lowercase()
    if (trimmed.isEmpty() || trimmed.length > MAX_GENRE_LENGTH) return null
    return ContentIdentity.slugify(trimmed).takeIf { it.isNotEmpty() }
}

private const val MAX_GENRE_LENGTH = 60
