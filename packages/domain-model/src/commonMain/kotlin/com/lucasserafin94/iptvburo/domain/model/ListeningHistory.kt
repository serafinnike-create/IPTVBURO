package com.lucasserafin94.iptvburo.domain.model

/**
 * The kind of audio an entry is, which decides how its progress is treated.
 *
 * GDD 8 section 18 gives each kind different rules, and they genuinely differ rather than being
 * shades of one behaviour: a song that stops 30 seconds in should start again from the beginning
 * next time, while an audiobook that stops 30 seconds in and restarts from zero has lost the
 * listener's place in a twelve-hour recording.
 *
 * Video is deliberately absent. Section 18 leaves it governed by GDD 7 and its existing
 * [PlaybackProgress] path, and pulling it in here would mean two systems writing one fact.
 */
enum class ListeningKind {
    /** Counts a play once a threshold is passed; position is not resumed. */
    MUSIC,

    /** Position persists, so an episode resumes where it stopped. */
    PODCAST,

    /** Position persists per chapter and per book. */
    AUDIOBOOK,

    /** Appears in history, but has no position to store — a live stream has no offset to return to. */
    RADIO,
    ;

    /**
     * Whether a stored position should be written and offered back.
     *
     * False for [MUSIC] and [RADIO]. For music this is a product decision from the GDD, not a
     * technical limit; for radio there is no meaningful position at all.
     */
    val resumesPosition: Boolean
        get() = this == PODCAST || this == AUDIOBOOK

    /**
     * Whether a play is only counted after listening past a threshold.
     *
     * Music alone. A podcast or audiobook is counted when opened because the listener returns to
     * the same item repeatedly and a "play count" for them means sessions, not completions.
     */
    val countsPlaysAfterThreshold: Boolean
        get() = this == MUSIC
}

/**
 * One row of listening history, per profile and per media identity.
 *
 * Field-for-field the record listed in GDD 8 section 18. [mediaIdentity] is a stable identity
 * string, never a resolved URL: section 23 forbids persisting a credential-bearing address, and an
 * identity survives a re-import whereas a signed URL does not.
 *
 * The profile isolation is the existing one — history is stored under the active profile exactly as
 * favourites are, so a household sharing one subscription does not share one listening history.
 */
data class ListeningHistoryEntry(
    val profileId: String,
    val mediaIdentity: String,
    val kind: ListeningKind,
    val startedAtEpochMillis: Long,
    val lastPlayedAtEpochMillis: Long,
    val playCount: Int = 0,
    val completedCount: Int = 0,
    /** Null for kinds that do not resume, and for those that simply have no position yet. */
    val lastPositionMillis: Long? = null,
    val durationMillis: Long? = null,
    val sourceId: String? = null,
) {
    /**
     * Whether this entry has ever counted as played.
     *
     * The distinction matters to the "never played" smart playlist: an entry can exist — because
     * playback was started and abandoned below the threshold — while still never having been
     * played. Treating the row's existence as "played" is exactly the naive reading that would put
     * a skipped track into the wrong list.
     */
    val hasBeenPlayed: Boolean
        get() = playCount > 0

    /**
     * The fraction listened, or null when the duration is unknown.
     *
     * Radio has no duration and therefore no progress, which is why this is nullable rather than
     * defaulting to zero.
     */
    val progressFraction: Float?
        get() {
            val duration = durationMillis ?: return null
            val position = lastPositionMillis ?: return null
            if (duration <= 0L) return null
            return (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        }

    /**
     * Redacted deliberately. [mediaIdentity] is derived from the user's own playlist and
     * [sourceId] names their provider; neither belongs in a log or a crash report.
     */
    override fun toString(): String =
        "ListeningHistoryEntry(" +
            "profileId=<redacted>, " +
            "mediaIdentity=<redacted>, " +
            "kind=$kind, " +
            "startedAtEpochMillis=$startedAtEpochMillis, " +
            "lastPlayedAtEpochMillis=$lastPlayedAtEpochMillis, " +
            "playCount=$playCount, " +
            "completedCount=$completedCount, " +
            "lastPositionMillis=$lastPositionMillis, " +
            "durationMillis=$durationMillis, " +
            "sourceId=${if (sourceId == null) "null" else "<redacted>"}" +
            ")"
}

/**
 * The rules that turn a playback report into a history row.
 *
 * Pure and separate from any store so the per-kind behaviour in GDD 8 section 18 can be tested
 * without a disk, a clock or a player.
 */
object ListeningHistoryRules {
    /**
     * How long a track must play before it counts as played.
     *
     * Thirty seconds is the long-standing convention across music services, and the GDD calls for a
     * "configured threshold" rather than naming one. It exists so that skipping through a playlist
     * does not manufacture a "most played" ranking out of tracks nobody listened to.
     */
    const val DEFAULT_MUSIC_PLAY_THRESHOLD_MILLIS: Long = 30_000L

    /**
     * The fraction of an item that counts as having completed it.
     *
     * Below 1.0 because almost nothing is listened to through its trailing silence or credits.
     */
    const val COMPLETION_FRACTION: Float = 0.95f

    /**
     * Whether [listenedMillis] of a [kind] item counts as a play.
     *
     * Strictly greater than or equal to the threshold, so a threshold of 30s counts at exactly 30s.
     * Kinds that do not gate on a threshold count as soon as playback is reported at all, which is
     * why zero elapsed time still counts for a podcast but not for a song.
     */
    fun countsAsPlay(
        kind: ListeningKind,
        listenedMillis: Long,
        thresholdMillis: Long = DEFAULT_MUSIC_PLAY_THRESHOLD_MILLIS,
    ): Boolean =
        if (kind.countsPlaysAfterThreshold) {
            listenedMillis >= thresholdMillis
        } else {
            listenedMillis >= 0L
        }

    /**
     * The position to store for [kind], or null when the kind does not resume.
     *
     * Enforced here rather than at each call site so that a caller which happens to know a music
     * position cannot persist one: section 18 says music does not resume, and the single place that
     * decides it is this function.
     */
    fun positionToStore(
        kind: ListeningKind,
        positionMillis: Long?,
    ): Long? {
        if (!kind.resumesPosition) return null
        return positionMillis?.takeIf { it > 0L }
    }

    /**
     * Folds a playback report into [existing], or creates the first row for this identity.
     *
     * Returns a row even when the play did not count, because "started but not counted" is real
     * information: it is what lets [ListeningHistoryEntry.hasBeenPlayed] distinguish a skipped
     * track from an untouched one, and it is what keeps "recently played" honest.
     */
    fun record(
        existing: ListeningHistoryEntry?,
        profileId: String,
        mediaIdentity: String,
        kind: ListeningKind,
        atEpochMillis: Long,
        listenedMillis: Long = 0L,
        positionMillis: Long? = null,
        durationMillis: Long? = null,
        sourceId: String? = null,
        thresholdMillis: Long = DEFAULT_MUSIC_PLAY_THRESHOLD_MILLIS,
        completed: Boolean = false,
    ): ListeningHistoryEntry {
        val counted = countsAsPlay(kind, listenedMillis, thresholdMillis)
        val storedPosition = positionToStore(kind, positionMillis)
        val duration = durationMillis ?: existing?.durationMillis

        // Completion is either reported by the caller or inferred from how far in the listener got.
        // Inferring it means a podcast that runs to its end counts as completed without the player
        // having to emit a separate event.
        val reachedEnd =
            completed ||
                (
                    duration != null && duration > 0L && storedPosition != null &&
                        storedPosition.toFloat() / duration.toFloat() >= COMPLETION_FRACTION
                )

        if (existing == null) {
            return ListeningHistoryEntry(
                profileId = profileId,
                mediaIdentity = mediaIdentity,
                kind = kind,
                startedAtEpochMillis = atEpochMillis,
                lastPlayedAtEpochMillis = atEpochMillis,
                playCount = if (counted) 1 else 0,
                completedCount = if (reachedEnd) 1 else 0,
                lastPositionMillis = storedPosition,
                durationMillis = duration,
                sourceId = sourceId,
            )
        }

        return existing.copy(
            kind = kind,
            // startedAt is never rewritten: it is the first time this profile touched the item, and
            // "recently added to history" would be meaningless if every play reset it.
            lastPlayedAtEpochMillis = maxOf(existing.lastPlayedAtEpochMillis, atEpochMillis),
            playCount = existing.playCount + if (counted) 1 else 0,
            completedCount = existing.completedCount + if (reachedEnd) 1 else 0,
            // A null position leaves the stored one alone rather than clearing it: a report that
            // carries no position (a stop event, say) must not erase where the listener was.
            lastPositionMillis = storedPosition ?: existing.lastPositionMillis,
            durationMillis = duration,
            sourceId = sourceId ?: existing.sourceId,
        )
    }
}
