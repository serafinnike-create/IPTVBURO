package com.lucasserafin94.iptvburo.data.repository

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Which past programmes a channel can replay, and how to ask the provider for one.
 *
 * Kept apart from the repository so the rules can be tested without a database or a network: they
 * are where a replay quietly goes wrong — an hour off, or a request for a recording the provider
 * has already thrown away.
 */
internal object CatchUpPolicy {
    /**
     * Whether a programme from [startEpochSeconds] to [endEpochSeconds] is still in the archive.
     *
     * It must have started — a future programme has no recording yet — and its start must fall
     * inside the [catchUpDays] the provider keeps. A programme that is still on air qualifies: the
     * recorder has its beginning, and replaying from the start is what "watch from the beginning"
     * means.
     */
    fun isReplayable(
        catchUpDays: Int?,
        startEpochSeconds: Long?,
        endEpochSeconds: Long?,
        nowEpochSeconds: Long,
    ): Boolean {
        val days = catchUpDays?.takeIf { it > 0 } ?: return false
        val start = startEpochSeconds ?: return false
        val end = endEpochSeconds ?: return false
        if (end <= start) return false
        if (start >= nowEpochSeconds) return false
        return start >= nowEpochSeconds - days * SECONDS_PER_DAY
    }

    /**
     * The start as the timeshift path wants it: `YYYY-MM-DD:HH-MM`, in local wall-clock time.
     *
     * Local, not UTC, and deliberately the device's own zone — the same choice the desktop makes.
     * The guide the viewer pressed is expressed in that clock, and Xtream's timeshift endpoint
     * takes the provider's wall-clock time; converting to UTC asked for a programme hours away.
     */
    fun startLocal(startEpochSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        START_FORMAT.format(Instant.ofEpochSecond(startEpochSeconds).atZone(zone))

    /** The programme's length, clamped to what the client will request. */
    fun durationMinutes(startEpochSeconds: Long, endEpochSeconds: Long): Int =
        ((endEpochSeconds - startEpochSeconds) / 60L).toInt().coerceIn(1, MAX_MINUTES)

    private val START_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm")
    private const val SECONDS_PER_DAY = 86_400L

    /** Matches XtreamClient's own ceiling, so a clamp here cannot still be refused there. */
    private const val MAX_MINUTES = 720
}
