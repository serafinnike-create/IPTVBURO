package com.lucasserafin94.iptvburo.data.repository

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchUpPolicyTest {
    private val now = 1_790_000_000L
    private val hour = 3_600L
    private val day = 86_400L

    @Test
    fun `a finished programme inside the window can be replayed`() {
        assertTrue(CatchUpPolicy.isReplayable(3, now - 2 * hour, now - hour, now))
    }

    @Test
    fun `a programme still on air can be watched from its start`() {
        assertTrue(CatchUpPolicy.isReplayable(1, now - hour, now + hour, now))
    }

    @Test
    fun `nothing is replayable without an archive, before it starts, or past the window`() {
        assertFalse(CatchUpPolicy.isReplayable(null, now - 2 * hour, now - hour, now))
        assertFalse(CatchUpPolicy.isReplayable(0, now - 2 * hour, now - hour, now))
        assertFalse(CatchUpPolicy.isReplayable(3, now + hour, now + 2 * hour, now))
        assertFalse(CatchUpPolicy.isReplayable(3, now - 4 * day, now - 4 * day + hour, now))
    }

    @Test
    fun `a programme with a missing or backwards time is not offered`() {
        assertFalse(CatchUpPolicy.isReplayable(3, null, now - hour, now))
        assertFalse(CatchUpPolicy.isReplayable(3, now - 2 * hour, null, now))
        assertFalse(CatchUpPolicy.isReplayable(3, now - hour, now - 2 * hour, now))
    }

    @Test
    fun `the start is local wall-clock time in the shape the timeshift path takes`() {
        // 2026-08-24 23:30 UTC is 20:30 in São Paulo.
        val start = 1_787_614_200L
        assertEquals("2026-08-24:20-30", CatchUpPolicy.startLocal(start, ZoneId.of("America/Sao_Paulo")))
        assertEquals("2026-08-24:23-30", CatchUpPolicy.startLocal(start, ZoneId.of("UTC")))
    }

    @Test
    fun `the duration is the programme's length, clamped to what the client accepts`() {
        assertEquals(45, CatchUpPolicy.durationMinutes(0L, 45 * 60L))
        assertEquals(1, CatchUpPolicy.durationMinutes(0L, 30L))
        assertEquals(720, CatchUpPolicy.durationMinutes(0L, 3 * day))
    }
}
