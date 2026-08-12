package com.lucasserafin94.iptvburo.domain.model

/**
 * What somebody tends to watch, and what to put in front of them because of it.
 *
 * Deliberately small. This reads the genres of what has actually been watched and prefers unwatched
 * titles that share them — no profile of the person, no history leaving the device, nothing stored
 * beyond what the playback progress already keeps.
 *
 * It is a **suggestion, not a verdict**: the caller decides where the result goes, and a viewer with
 * no history simply gets nothing back rather than a guess dressed up as a recommendation.
 */
object ViewingTaste {
    /** Below this, there is not enough history for a preference to mean anything. */
    const val MINIMUM_WATCHED = 2

    /**
     * The genres someone watches most, strongest first.
     *
     * Counted across everything supplied, splitting on the separators providers use. A genre named
     * once carries as much weight as its single showing deserves — the ordering is by count, so one
     * accidental viewing never outranks a habit.
     */
    fun preferredGenres(watchedGenres: List<String?>): List<String> {
        val counts = mutableMapOf<String, Int>()
        watchedGenres.filterNotNull().forEach { raw ->
            raw.split(',', '/', '|', ';')
                .map { it.trim() }
                .filter(String::isNotBlank)
                .forEach { genre ->
                    val key = genre.lowercase()
                    counts[key] = (counts[key] ?: 0) + 1
                }
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
    }

    /**
     * The best unwatched suggestion from [candidates], or null when there is no basis for one.
     *
     * Null happens for a genuine reason — too little history, no candidate sharing a genre, nothing
     * unwatched left — and the caller must treat it as "do not suggest anything" rather than
     * falling back to something arbitrary. A suggestion nobody can explain is worse than none.
     *
     * Ties break on [BrowsableItem.id] so the same day produces the same pick; the caller supplies
     * candidates already ordered by whatever freshness rule it wants.
     */
    fun suggest(
        candidates: List<BrowsableItem>,
        watchedGenres: List<String?>,
        watchedIds: Set<String>,
    ): BrowsableItem? {
        if (watchedGenres.count { !it.isNullOrBlank() } < MINIMUM_WATCHED) return null
        val preferred = preferredGenres(watchedGenres).take(MAX_GENRES_CONSIDERED)
        if (preferred.isEmpty()) return null

        return candidates
            .asSequence()
            .filter { it.id !in watchedIds }
            .mapNotNull { candidate ->
                val genres =
                    candidate.genre
                        ?.split(',', '/', '|', ';')
                        ?.map { it.trim().lowercase() }
                        ?.filter(String::isNotBlank)
                        .orEmpty()
                // Scored by how strongly the shared genre is preferred, so a match on somebody's
                // favourite genre beats a match on one they have seen once.
                val score =
                    genres.sumOf { genre ->
                        val rank = preferred.indexOf(genre)
                        if (rank < 0) 0 else preferred.size - rank
                    }
                if (score <= 0) null else candidate to score
            }.sortedWith(
                compareByDescending<Pair<BrowsableItem, Int>> { it.second }
                    .thenBy { it.first.id },
            ).firstOrNull()
            ?.first
    }

    /** More than this and the tail is noise rather than taste. */
    private const val MAX_GENRES_CONSIDERED = 5
}
