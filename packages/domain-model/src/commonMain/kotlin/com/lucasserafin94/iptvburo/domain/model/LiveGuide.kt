package com.lucasserafin94.iptvburo.domain.model

/**
 * The rules behind a live guide: a channel list, a schedule beside it, and the focused channel
 * playing while you move.
 *
 * The catalogue answers "what channels are there"; a guide answers "what is on". Those are
 * different questions and the second one is what somebody reaches for when they sit down — which is
 * why every satellite box and every IPTV app has this screen and a grid of logos is not a
 * substitute.
 *
 * ## Why the schedule is fetched per channel
 *
 * The provider offers no "give me the schedule for these forty channels" call — the endpoint takes
 * one stream id. A guide showing forty rows would be forty requests, which on a modest connection
 * is how a screen turns into a wait. So the schedule is fetched for the channel in focus and a
 * small window around it, and moving the focus fetches the next one.
 */
object LiveGuide {
    /**
     * How many channels either side of the focused one are fetched ahead of being looked at.
     *
     * Moving down a list one row at a time is the ordinary way to browse a guide, so the next few
     * rows are worth having in hand. Beyond that it is speculation paid for with the viewer's
     * connection: the far end of a four-hundred-channel list is not where anybody is going next.
     */
    const val PREFETCH_RADIUS = 4

    /**
     * How many schedules are kept before the oldest are dropped.
     *
     * Each is a few hours of programmes for one channel. Enough that moving back up a list finds
     * what was already fetched, bounded so an evening of browsing does not accumulate the whole
     * catalogue's schedule in memory.
     */
    const val MAX_CACHED_SCHEDULES = 60

    /**
     * How long a fetched schedule is trusted.
     *
     * A programme listing changes when the next one starts, not by the second. Re-fetching on every
     * focus change would make moving down a list a request per row for data that has not changed;
     * five minutes is short enough that "now" stays honest.
     */
    const val SCHEDULE_FRESHNESS_SECONDS = 300L

    /**
     * How far into a programme the viewer is, 0..1, or null when it cannot be said.
     *
     * Null rather than zero for a programme with no times: a bar sitting at the start is a claim
     * that it just began, and the guide would be inventing a fact the provider did not send.
     */
    fun progressOf(
        program: EpgEntry,
        nowEpochSeconds: Long,
    ): Float? {
        val start = program.startEpochSeconds ?: return null
        val end = program.endEpochSeconds ?: return null
        if (end <= start) return null
        if (nowEpochSeconds <= start) return 0f
        if (nowEpochSeconds >= end) return 1f
        return ((nowEpochSeconds - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
    }

    /** Whether [program] is what is on right now. */
    fun isOnNow(
        program: EpgEntry,
        nowEpochSeconds: Long,
    ): Boolean {
        val start = program.startEpochSeconds ?: return false
        val end = program.endEpochSeconds ?: return false
        return nowEpochSeconds in start until end
    }

    /**
     * The channels whose schedules are worth having for a focus at [focusedIndex].
     *
     * A window rather than the whole list, and clamped to it — the first and last rows are where an
     * unclamped range would ask for channels that do not exist.
     */
    fun prefetchWindow(
        focusedIndex: Int,
        channelCount: Int,
        radius: Int = PREFETCH_RADIUS,
    ): IntRange {
        if (channelCount <= 0) return IntRange.EMPTY
        val safeFocus = focusedIndex.coerceIn(0, channelCount - 1)
        val first = (safeFocus - radius).coerceAtLeast(0)
        val last = (safeFocus + radius).coerceAtMost(channelCount - 1)
        return first..last
    }

    /**
     * Whether a schedule fetched at [fetchedAtEpochSeconds] still describes the present.
     *
     * A clock that went backwards — a machine waking from sleep, a timezone correction — makes the
     * age negative, which is not freshness. Treated as stale so the guide asks again rather than
     * trusting a timestamp from the future.
     */
    fun isFresh(
        fetchedAtEpochSeconds: Long,
        nowEpochSeconds: Long,
        freshnessSeconds: Long = SCHEDULE_FRESHNESS_SECONDS,
    ): Boolean {
        val age = nowEpochSeconds - fetchedAtEpochSeconds
        return age in 0 until freshnessSeconds
    }

    /**
     * The programmes worth showing beside a channel, from [nowEpochSeconds] onwards.
     *
     * What has already finished is dropped: a guide is about what is coming. The one still running
     * is kept and comes first, because "what am I watching" is the question the top row answers.
     *
     * Programmes with no start time keep their given order at the end rather than being discarded —
     * a provider that sends a title and no clock is still telling the viewer something.
     */
    fun upcoming(
        programs: List<EpgEntry>,
        nowEpochSeconds: Long,
        limit: Int = 0,
    ): List<EpgEntry> {
        val timed = programs.filter { it.startEpochSeconds != null }
        val untimed = programs.filter { it.startEpochSeconds == null }
        val ordered = timed.sortedBy { it.startEpochSeconds }
        val current = ordered.lastOrNull { isOnNow(it, nowEpochSeconds) }
        val later =
            ordered.filter { program ->
                val start = program.startEpochSeconds ?: return@filter false
                start > (current?.startEpochSeconds ?: (nowEpochSeconds - 1))
            }
        val result = listOfNotNull(current) + later + untimed
        return if (limit > 0) result.take(limit) else result
    }
}

/**
 * One programme, as the guide needs it.
 *
 * Its own type rather than the Xtream client's, because the guide is a shared idea and the Tizen
 * app reaches its schedule through XMLTV rather than through Xtream. Both fill this in.
 */
data class EpgEntry(
    val title: String,
    val description: String? = null,
    val startEpochSeconds: Long? = null,
    val endEpochSeconds: Long? = null,
)
