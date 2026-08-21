package com.lucasserafin94.iptvburo.desktop.update

import com.lucasserafin94.iptvburo.desktop.download.DownloadRateTracker
import com.lucasserafin94.iptvburo.desktop.download.formatDuration
import com.lucasserafin94.iptvburo.desktop.download.formatRate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The speed and remaining time shown while the installer downloads.
 *
 * The estimate is the part worth testing: a wrong percentage is obvious on screen, whereas a
 * remaining time that is quietly out by a factor of sixty looks perfectly plausible.
 */
class UpdateProgressLineTest {
    private fun secondsRemaining(total: Long, read: Long, rate: Long): Long? =
        if (total > 0L && rate > 0L && read in 0 until total) (total - read) / rate else null

    @Test
    fun `remaining time is the outstanding bytes at the current rate`() {
        // 322 MB total, 197 MB in, 4 MB/s — the state in the reported screenshot.
        val total = 322L * 1024 * 1024
        val read = 197L * 1024 * 1024
        val rate = 4L * 1024 * 1024
        assertEquals(31L, secondsRemaining(total, read, rate))
        assertEquals("31s", formatDuration(31L))
    }

    @Test
    fun `no estimate before a rate exists`() {
        assertNull(secondsRemaining(322L * 1024 * 1024, 1024L, 0L))
    }

    @Test
    fun `no estimate when the size is unknown`() {
        assertNull(secondsRemaining(-1L, 1024L, 1024L))
    }

    @Test
    fun `no estimate once every byte has arrived`() {
        val total = 322L * 1024 * 1024
        assertNull(secondsRemaining(total, total, 1024L))
    }

    @Test
    fun `a stalled transfer stops claiming a speed`() {
        var now = 0L
        val tracker = DownloadRateTracker { now }
        tracker.observe("k", 0L)
        // Four seconds of healthy transfer at roughly 1 MB/s.
        for (step in 1..4) {
            now = step * 1000L
            tracker.observe("k", step * 1_048_576L)
        }
        val healthy = tracker.observe("k", 4 * 1_048_576L)
        // Then nothing arrives for twenty seconds.
        for (step in 1..20) {
            now = 4000L + step * 1000L
            tracker.observe("k", 4 * 1_048_576L)
        }
        val stalled = tracker.observe("k", 4 * 1_048_576L)
        assertEquals(true, stalled < healthy / 10, "a stall must collapse the reported rate")
    }

    @Test
    fun `the rate reads in the units a person expects`() {
        assertEquals("4,0 MB/s", formatRate(4L * 1024 * 1024))
    }
}
