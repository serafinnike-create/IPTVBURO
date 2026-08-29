package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rules behind the live guide.
 *
 * The screen itself needs a window and a running stream, so what is pinned here is the arithmetic
 * it depends on: which channels are worth fetching, how far into a programme the viewer is, and
 * what belongs on screen from now onwards.
 */
class LiveGuideTest {
    private val hour = 3_600L
    private val now = 100_000L

    private fun entry(
        title: String,
        start: Long?,
        end: Long?,
    ) = EpgEntry(title = title, startEpochSeconds = start, endEpochSeconds = end)

    // -------------------------------------------------------------------------------------------
    // Progress through a programme
    // -------------------------------------------------------------------------------------------

    @Test
    fun `progress is measured across the programme`() {
        val program = entry("Filme", now - hour, now + hour)

        assertEquals(0.5f, LiveGuide.progressOf(program, now))
    }

    @Test
    fun `a programme that has not started is at zero and one that ended is at one`() {
        assertEquals(0f, LiveGuide.progressOf(entry("Depois", now + hour, now + 2 * hour), now))
        assertEquals(1f, LiveGuide.progressOf(entry("Antes", now - 2 * hour, now - hour), now))
    }

    /**
     * Null rather than zero when the provider sent no clock.
     *
     * A bar sitting at the start claims the programme just began, which is a fact the guide would
     * be inventing.
     */
    @Test
    fun `a programme with no times has no progress`() {
        assertNull(LiveGuide.progressOf(entry("Sem horas", null, null), now))
        assertNull(LiveGuide.progressOf(entry("So inicio", now, null), now))
        assertNull(LiveGuide.progressOf(entry("So fim", null, now), now))
    }

    /** A zero-length or reversed programme cannot be measured either. */
    @Test
    fun `a programme that ends before it starts has no progress`() {
        assertNull(LiveGuide.progressOf(entry("Invertido", now, now - hour), now))
        assertNull(LiveGuide.progressOf(entry("Instantaneo", now, now), now))
    }

    // -------------------------------------------------------------------------------------------
    // Which channels to fetch
    // -------------------------------------------------------------------------------------------

    @Test
    fun `the window covers the rows either side of the focus`() {
        assertEquals(6..14, LiveGuide.prefetchWindow(focusedIndex = 10, channelCount = 400))
    }

    /**
     * Clamped at both ends.
     *
     * The first and last rows are exactly where an unclamped range asks for channels that are not
     * there.
     */
    @Test
    fun `the window stops at the ends of the list`() {
        assertEquals(0..4, LiveGuide.prefetchWindow(focusedIndex = 0, channelCount = 400))
        assertEquals(395..399, LiveGuide.prefetchWindow(focusedIndex = 399, channelCount = 400))
    }

    /** A short list is covered whole rather than overrun. */
    @Test
    fun `a list shorter than the window is covered whole`() {
        assertEquals(0..2, LiveGuide.prefetchWindow(focusedIndex = 1, channelCount = 3))
    }

    @Test
    fun `an empty list asks for nothing`() {
        assertTrue(LiveGuide.prefetchWindow(focusedIndex = 0, channelCount = 0).isEmpty())
    }

    // -------------------------------------------------------------------------------------------
    // Freshness
    // -------------------------------------------------------------------------------------------

    @Test
    fun `a schedule fetched a moment ago is fresh and an old one is not`() {
        assertTrue(LiveGuide.isFresh(fetchedAtEpochSeconds = now - 60, nowEpochSeconds = now))
        assertFalse(LiveGuide.isFresh(fetchedAtEpochSeconds = now - 600, nowEpochSeconds = now))
    }

    /**
     * A timestamp from the future is stale, not fresh.
     *
     * A machine waking from sleep or a timezone correction moves the clock backwards, and trusting
     * an age that is negative would leave the guide showing yesterday's listing indefinitely.
     */
    @Test
    fun `a timestamp from the future is treated as stale`() {
        assertFalse(LiveGuide.isFresh(fetchedAtEpochSeconds = now + 600, nowEpochSeconds = now))
    }

    // -------------------------------------------------------------------------------------------
    // What belongs on screen
    // -------------------------------------------------------------------------------------------

    /** What is running comes first, then what follows; what finished is gone. */
    @Test
    fun `the schedule starts with what is on now`() {
        val programs =
            listOf(
                entry("Terminado", now - 3 * hour, now - 2 * hour),
                entry("A dar", now - hour, now + hour),
                entry("A seguir", now + hour, now + 2 * hour),
                entry("Mais tarde", now + 2 * hour, now + 3 * hour),
            )

        val shown = LiveGuide.upcoming(programs, now).map { it.title }

        assertEquals(listOf("A dar", "A seguir", "Mais tarde"), shown)
    }

    /** Out-of-order input is sorted rather than shown as it arrived. */
    @Test
    fun `the schedule is ordered by start time`() {
        val programs =
            listOf(
                entry("Mais tarde", now + 2 * hour, now + 3 * hour),
                entry("A dar", now - hour, now + hour),
                entry("A seguir", now + hour, now + 2 * hour),
            )

        assertEquals(
            listOf("A dar", "A seguir", "Mais tarde"),
            LiveGuide.upcoming(programs, now).map { it.title },
        )
    }

    /**
     * A programme with no clock is kept at the end rather than dropped.
     *
     * A provider that sends a title and no times is still telling the viewer something, and losing
     * it would make the guide claim the channel has nothing on.
     */
    @Test
    fun `programmes with no times are kept at the end`() {
        val programs =
            listOf(
                entry("Sem horas", null, null),
                entry("A dar", now - hour, now + hour),
            )

        assertEquals(
            listOf("A dar", "Sem horas"),
            LiveGuide.upcoming(programs, now).map { it.title },
        )
    }

    /** And a channel whose listing is all in the past shows nothing rather than yesterday. */
    @Test
    fun `a schedule entirely in the past shows nothing`() {
        val programs = listOf(entry("Ontem", now - 5 * hour, now - 4 * hour))

        assertTrue(LiveGuide.upcoming(programs, now).isEmpty())
    }

    @Test
    fun `the limit caps how much is returned`() {
        val programs = (0..9).map { index -> entry("P$index", now + index * hour, now + (index + 1) * hour) }

        assertEquals(3, LiveGuide.upcoming(programs, now, limit = 3).size)
    }

    @Test
    fun `what is on now is recognised and what is not is refused`() {
        assertTrue(LiveGuide.isOnNow(entry("A dar", now - hour, now + hour), now))
        assertFalse(LiveGuide.isOnNow(entry("Terminado", now - 2 * hour, now - hour), now))
        assertFalse(LiveGuide.isOnNow(entry("Sem horas", null, null), now))
    }
}
