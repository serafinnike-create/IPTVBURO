package com.lucasserafin94.iptvburo.domain.model

/**
 * How far ahead of the picture the player should keep reading.
 *
 * A film that stops because the connection stumbled for ten seconds is the most visible failure
 * this app has, and it is avoidable: a film is a file, so the player can be minutes ahead of what
 * is on screen and simply not notice a gap that short.
 *
 * ## Why live is different, and must stay different
 *
 * A live channel has no ahead. What has not been broadcast cannot be read early, so a large buffer
 * there buys nothing and costs two things that matter: the channel starts that much later, and the
 * picture sits that far behind — which turns a football match into a neighbour's shout arriving
 * before the goal. So live keeps the small reservoir it already had, tuned for stalls rather than
 * for outages.
 *
 * The distinction is the whole design. Applying one number to both would either make live
 * unwatchable or leave films as fragile as they are now.
 */
object PlaybackBuffering {
    /**
     * How far ahead a film or an episode reads, in milliseconds.
     *
     * Two minutes, as asked. That is long enough to cover a connection dropping and coming back —
     * a router restarting, a phone changing cell, a provider hiccuping — without the picture ever
     * pausing, which is the whole point.
     *
     * It is not free: the player waits longer before the first frame, and holds more in memory. Both
     * are worth it for a file, and neither is acceptable for live.
     */
    const val ON_DEMAND_MILLIS = 120_000

    /**
     * How far ahead a live channel reads.
     *
     * Small, deliberately. A live stream cannot be read ahead of the broadcast, so this is a
     * cushion against jitter rather than a reserve against an outage, and every millisecond added
     * here is a millisecond the viewer falls behind the moment it happens.
     */
    const val LIVE_MILLIS = 1_500

    /**
     * How far ahead each tile reads when several channels share one connection.
     *
     * Larger than a single live stream and still nowhere near a film's. Four channels compete for
     * the same connection, disk and processor, and a starved buffer showed as every tile stalling
     * and recovering in turn.
     */
    const val MULTIVIEW_MILLIS = 6_000

    /**
     * The read-ahead for a piece of content.
     *
     * The only question that decides it is whether the stream is live, which the caller knows and
     * the player cannot infer from an address.
     */
    fun millisFor(
        isLive: Boolean,
        isMultiview: Boolean = false,
    ): Int =
        when {
            isMultiview -> MULTIVIEW_MILLIS
            isLive -> LIVE_MILLIS
            else -> ON_DEMAND_MILLIS
        }

    /**
     * Whether a read-ahead this large is safe to ask for.
     *
     * A guard rather than a preference: a buffer beyond this is not a more robust player, it is a
     * player that appears not to start. Anything larger is a mistake in the caller.
     */
    fun isWithinLimit(millis: Int): Boolean = millis in 0..MAXIMUM_MILLIS

    /**
     * The largest read-ahead the player will accept.
     *
     * Three minutes: half again beyond what a film asks for, so the intended value is comfortably
     * inside, and far short of a wait somebody would read as a hang.
     */
    const val MAXIMUM_MILLIS = 180_000
}
