package com.lucasserafin94.iptvburo.domain.model

import java.util.Locale

/**
 * The rule behind a smart or system playlist.
 *
 * These are the seven initial rules named in GDD 8 section 17, and only those. "Daily mix" and
 * algorithmic recommendation are explicitly out of scope there until a real, explainable model
 * exists, so there is no rule for them and no placeholder pretending there might be — a shelf
 * labelled "for you" that shuffles randomly is a claim the product cannot back up.
 *
 * Every rule is derived from facts the app actually holds: the user's favourites, their own listening
 * history, and the playlist's own metadata. None of them infer taste.
 */
sealed interface SmartPlaylistRule {
    /** Tracks the profile has marked as favourite. */
    data object Favourites : SmartPlaylistRule

    /** Played at least once, most recent first. */
    data object RecentlyPlayed : SmartPlaylistRule

    /** Ranked by play count, highest first. */
    data object MostPlayed : SmartPlaylistRule

    /**
     * Never counted a play.
     *
     * Note this is not "has no history row". A track that was started and abandoned below the play
     * threshold has a row with a zero count, and it has still never been played.
     */
    data object NeverPlayed : SmartPlaylistRule

    /**
     * Newest additions to the library.
     *
     * An M3U carries no added-on date, so "recently added" reads the playlist's own ordering from
     * the end. That is the only honest reading available: playlist authors append, so the tail is
     * the newest material.
     */
    data object RecentlyAdded : SmartPlaylistRule

    /** Every track carrying [genre], compared case-insensitively. */
    data class ByGenre(val genre: String) : SmartPlaylistRule

    /**
     * Every track whose year falls in the decade starting at [startYear].
     *
     * [startYear] is the first year of the decade — 1990 covers 1990 through 1999 inclusive.
     */
    data class ByDecade(val startYear: Int) : SmartPlaylistRule
}

/**
 * Evaluates smart playlists against a library and the profile's own history.
 *
 * Pure: it takes the library, the history and the favourites, and returns tracks. No store, no
 * clock, no I/O — which is what makes each rule testable at its boundaries.
 */
object SmartPlaylists {
    /**
     * How many tracks a smart shelf yields by default.
     *
     * Bounded because these feed rails in the UI, and an unbounded "never played" on a 20,000-entry
     * playlist would render twenty thousand cards.
     */
    const val DEFAULT_LIMIT: Int = 100

    /**
     * Evaluates [rule] over [tracks].
     *
     * [history] is keyed by track id. Radio stations are excluded from every rule except
     * [SmartPlaylistRule.Favourites]: a station has no play count, no year and no album, so it
     * would sit permanently at the top of "never played" without that ever meaning anything.
     */
    fun evaluate(
        rule: SmartPlaylistRule,
        tracks: List<MusicTrack>,
        history: Map<String, ListeningHistoryEntry> = emptyMap(),
        favouriteIds: Set<String> = emptySet(),
        limit: Int = DEFAULT_LIMIT,
    ): List<MusicTrack> {
        val songs = tracks.filterNot(MusicTrack::isRadio)
        val evaluated =
            when (rule) {
                // Favourites keeps stations: a favourited station is exactly the thing a user wants
                // to find again, and it is the one list where they belong.
                SmartPlaylistRule.Favourites -> tracks.filter { it.id in favouriteIds }

                SmartPlaylistRule.RecentlyPlayed ->
                    songs
                        .mapNotNull { track ->
                            val entry = history[track.id] ?: return@mapNotNull null
                            // Only genuinely played tracks, not merely touched ones. A row with a
                            // zero count means playback was abandoned below the threshold, and
                            // listing it as "recently played" would contradict "never played",
                            // which excludes the very same track.
                            if (!entry.hasBeenPlayed) return@mapNotNull null
                            track to entry
                        }.sortedByDescending { it.second.lastPlayedAtEpochMillis }
                        .map { it.first }

                SmartPlaylistRule.MostPlayed ->
                    songs
                        .mapNotNull { track ->
                            val entry = history[track.id] ?: return@mapNotNull null
                            if (!entry.hasBeenPlayed) return@mapNotNull null
                            track to entry
                        }.sortedWith(
                            compareByDescending<Pair<MusicTrack, ListeningHistoryEntry>> { it.second.playCount }
                                // Ties broken by title so the ordering is stable between runs
                                // rather than depending on map iteration order.
                                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first.title },
                        ).map { it.first }

                SmartPlaylistRule.NeverPlayed ->
                    songs.filter { track ->
                        val entry = history[track.id]
                        // Absent row or a row that never crossed the threshold both count as never
                        // played. Testing only for the row's absence is the naive version and would
                        // wrongly drop every track the user skipped past.
                        entry == null || !entry.hasBeenPlayed
                    }

                // Reversed, not sorted: there is no date to sort by, only the author's ordering.
                SmartPlaylistRule.RecentlyAdded -> songs.asReversed()

                is SmartPlaylistRule.ByGenre ->
                    songs.filter { track ->
                        track.genre?.trim()?.equals(rule.genre.trim(), ignoreCase = true) == true
                    }

                is SmartPlaylistRule.ByDecade ->
                    songs.filter { track ->
                        val year = releaseYearOf(track) ?: return@filter false
                        // Half-open by construction: 1990..1999 for startYear 1990, so 1999 and 1990
                        // group together while 2000 begins the next decade.
                        year >= rule.startYear && year < rule.startYear + 10
                    }
            }
        return evaluated.take(limit)
    }

    /**
     * The decades present in [tracks], newest first, each with its track count.
     *
     * Only decades that actually have tracks are returned, so the UI never offers an empty "1950s"
     * shelf just because the range is conceivable.
     */
    fun decadesIn(tracks: List<MusicTrack>): List<MusicDecade> =
        tracks
            .filterNot(MusicTrack::isRadio)
            .mapNotNull(::releaseYearOf)
            .groupingBy { year -> (year / 10) * 10 }
            .eachCount()
            .map { (startYear, count) -> MusicDecade(startYear = startYear, trackCount = count) }
            .sortedByDescending(MusicDecade::startYear)

    /**
     * The release year of [track], or null when it cannot be established.
     *
     * An M3U has no year attribute, so the year is read from the text the playlist does carry —
     * a parenthesised or bracketed year in the album or title, the convention used by nearly every
     * tagged library ("Nevermind (1991)").
     *
     * Null is returned rather than a guess. A track with no discoverable year is absent from every
     * decade shelf, which is correct: filing it under an invented decade would be a fabricated fact
     * presented to the user as a real one.
     */
    fun releaseYearOf(track: MusicTrack): Int? =
        yearIn(track.album) ?: yearIn(track.title)

    /**
     * Extracts a bracketed four-digit year from [text].
     *
     * Bracketing is required. A bare four-digit run matches far too much — "Blink 182", "Level 42",
     * a bitrate, a station frequency — and an unbracketed match would scatter unrelated tracks
     * across decade shelves.
     */
    private fun yearIn(text: String?): Int? {
        val value = text?.takeIf(String::isNotBlank) ?: return null
        return BRACKETED_YEAR
            .findAll(value)
            .mapNotNull { match -> match.groupValues[1].toIntOrNull() }
            // The most recent plausible year wins: a reissue reads "Album (1971) (2011 remaster)",
            // and either is defensible, but taking the last keeps one consistent answer.
            .lastOrNull { year -> year in PLAUSIBLE_YEARS }
    }

    /**
     * Recorded music does not predate the 1900s, and a year beyond the near future is a typo or a
     * misread bitrate rather than a release date.
     */
    private val PLAUSIBLE_YEARS = 1900..2100

    private val BRACKETED_YEAR = Regex("""[(\[](\d{4})[)\]]""")

    /**
     * The genres present in [tracks], ordered for display.
     *
     * Case-insensitive: a playlist that writes "Rock" on one line and "rock" on the next means one
     * genre, and showing two shelves for it looks like a fault.
     */
    fun genresIn(tracks: List<MusicTrack>): List<String> =
        tracks
            .filterNot(MusicTrack::isRadio)
            .mapNotNull { it.genre?.trim()?.takeIf(String::isNotBlank) }
            .groupBy { it.lowercase(Locale.ROOT) }
            .map { (_, spellings) -> spellings.first() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
}

/** A decade shelf, derived from the tracks rather than from any provider metadata. */
data class MusicDecade(
    val startYear: Int,
    val trackCount: Int,
) {
    /** "1990s" — built here so every caller labels a decade the same way. */
    val label: String
        get() = "${startYear}s"
}
