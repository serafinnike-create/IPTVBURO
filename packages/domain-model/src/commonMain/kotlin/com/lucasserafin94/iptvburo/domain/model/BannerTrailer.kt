package com.lucasserafin94.iptvburo.domain.model

/**
 * When the home banner may play a trailer instead of showing artwork.
 *
 * The banner is the first thing anybody sees. A trailer that fails there is worse than no trailer
 * at all: artwork that never moves looks deliberate, while a black rectangle or an error card on
 * the opening screen looks like a broken app. So the rule is not "play it if there is one" but
 * "play it once it is known to work, and fall back the moment it is not".
 *
 * ## What "known to work" means
 *
 * Three things, in order of cost:
 *
 * 1. There is an id at all, and it has the shape of one.
 * 2. It has not already failed on this machine — a trailer that was pulled or made private stays
 *    unavailable, and asking again on every rotation is a wait the viewer pays for repeatedly.
 * 3. Nothing else is playing that the viewer chose. A trailer that talks over a film is not a
 *    feature.
 *
 * The network check itself belongs to the platform, because each has its own player. What lives
 * here is the decision that consumes its answer, so all three agree on what to do with it.
 */
object BannerTrailer {
    /**
     * A YouTube video id: eleven characters of the URL-safe alphabet.
     *
     * Checked because the id is pasted into a player URL. A malformed one produces an error frame
     * on the opening screen, which is the exact thing this is here to prevent.
     */
    private val VIDEO_ID = Regex("""^[A-Za-z0-9_-]{11}$""")

    /**
     * How long a failure is remembered.
     *
     * A trailer that was pulled, made private or region-locked stays that way, and retrying on
     * every rotation costs the viewer a wait each time for the same answer. A day, because the
     * other direction — a video that comes back — is worth picking up without needing a reinstall.
     */
    const val FAILURE_MEMORY_SECONDS = 86_400L

    /**
     * How long the banner waits on a title before starting its trailer.
     *
     * The banner rotates on its own and the viewer scrolls past it, so starting the instant a title
     * appears would open and abandon a video per rotation. Long enough that only a title somebody
     * is actually looking at begins to play.
     */
    const val SETTLE_MILLIS = 1_200L

    /** Whether [videoId] is shaped like something a player can be handed. */
    fun isPlayableId(videoId: String?): Boolean =
        videoId != null && VIDEO_ID.matches(videoId)

    /**
     * Whether the banner should play [videoId] now.
     *
     * Every reason to say no is a reason the viewer would otherwise see a failure on the opening
     * screen, or hear a trailer over something they chose.
     */
    fun shouldPlay(
        videoId: String?,
        failedAtEpochSeconds: Long?,
        nowEpochSeconds: Long,
        somethingElseIsPlaying: Boolean = false,
        viewerIsScrolling: Boolean = false,
    ): Boolean {
        if (!isPlayableId(videoId)) return false
        // Muted-and-off-screen is not a compromise worth making: the viewer scrolled away from the
        // banner, so it is not what they are looking at.
        if (viewerIsScrolling) return false
        if (somethingElseIsPlaying) return false
        return !hasRecentlyFailed(failedAtEpochSeconds, nowEpochSeconds)
    }

    /**
     * Whether a past failure still stands.
     *
     * A timestamp from the future — a clock corrected backwards, a machine waking from sleep — is
     * treated as expired rather than trusted, or the trailer would stay suppressed indefinitely
     * for a video that works.
     */
    fun hasRecentlyFailed(
        failedAtEpochSeconds: Long?,
        nowEpochSeconds: Long,
        memorySeconds: Long = FAILURE_MEMORY_SECONDS,
    ): Boolean {
        val failedAt = failedAtEpochSeconds ?: return false
        val age = nowEpochSeconds - failedAt
        return age in 0 until memorySeconds
    }

    /**
     * The failures worth keeping, dropping the ones that have expired.
     *
     * Bounded by time rather than by count: a viewer whose provider carries many titles with dead
     * trailers should not have the oldest entry evicted and immediately retried.
     */
    fun pruneFailures(
        failures: Map<String, Long>,
        nowEpochSeconds: Long,
        memorySeconds: Long = FAILURE_MEMORY_SECONDS,
    ): Map<String, Long> =
        failures.filterValues { failedAt ->
            hasRecentlyFailed(failedAt, nowEpochSeconds, memorySeconds)
        }
}
