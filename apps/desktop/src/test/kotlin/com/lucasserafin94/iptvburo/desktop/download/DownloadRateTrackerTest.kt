package com.lucasserafin94.iptvburo.desktop.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadRateTrackerTest {
    /** A clock the test drives, so nothing depends on how fast the machine runs. */
    private class FakeClock(var now: Long = 0L) {
        fun advance(millis: Long) {
            now += millis
        }
    }

    private fun trackerWith(clock: FakeClock) = DownloadRateTracker(clock = { clock.now })

    @Test
    fun `the first sample reports nothing rather than an absurd rate`() {
        val clock = FakeClock()
        val tracker = trackerWith(clock)

        // A rate computed across zero elapsed time would be infinite.
        assertEquals(0L, tracker.observe("a", 1_000_000))
    }

    @Test
    fun `a steady transfer reports its true rate`() {
        val clock = FakeClock()
        val tracker = trackerWith(clock)

        tracker.observe("a", 0)
        clock.advance(1000)
        val rate = tracker.observe("a", 1_048_576) // exactly 1 MiB in one second

        assertEquals(1_048_576L, rate)
    }

    @Test
    fun `a stall is visible rather than hidden behind the average`() {
        val clock = FakeClock()
        val tracker = trackerWith(clock)

        tracker.observe("a", 0)
        clock.advance(1000)
        val healthy = tracker.observe("a", 5_000_000)
        assertTrue(healthy > 0)

        // Nothing arrives for the next several seconds. A cumulative average would still be
        // reporting megabytes per second here, which is exactly the lie this class exists to avoid.
        repeat(6) {
            clock.advance(1000)
            tracker.observe("a", 5_000_000)
        }

        assertTrue(tracker.observe("a", 5_000_000) < healthy / 4, "a stalled transfer still looks healthy")
    }

    @Test
    fun `samples closer together than the floor keep the previous answer`() {
        val clock = FakeClock()
        val tracker = trackerWith(clock)

        tracker.observe("a", 0)
        clock.advance(1000)
        val settled = tracker.observe("a", 2_000_000)

        // Two callbacks a few milliseconds apart must not produce a wild number.
        clock.advance(10)
        assertEquals(settled, tracker.observe("a", 2_010_000))
    }

    @Test
    fun `concurrent downloads are measured separately`() {
        val clock = FakeClock()
        val tracker = trackerWith(clock)

        tracker.observe("fast", 0)
        tracker.observe("slow", 0)
        clock.advance(1000)

        val fast = tracker.observe("fast", 10_000_000)
        val slow = tracker.observe("slow", 100_000)

        assertTrue(fast > slow * 10, "one download's rate leaked into the other: fast=$fast slow=$slow")
    }

    @Test
    fun `a rewound transfer never reports a negative rate`() {
        val clock = FakeClock()
        val tracker = trackerWith(clock)

        tracker.observe("a", 5_000_000)
        clock.advance(1000)

        // A restarted download reports fewer bytes than before.
        assertTrue(tracker.observe("a", 1_000) >= 0)
    }

    @Test
    fun `forgetting a key starts the next attempt clean`() {
        val clock = FakeClock()
        val tracker = trackerWith(clock)

        tracker.observe("a", 0)
        clock.advance(1000)
        tracker.observe("a", 5_000_000)

        tracker.forget("a")

        assertEquals(0L, tracker.observe("a", 0), "a retry inherited the previous attempt's rate")
    }

    @Test
    fun `byte sizes read the way a file manager shows them`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1 KB", formatBytes(1024))
        // Comma, not point: Portuguese decimal separator, pinned so the build renders the same
        // whatever locale the machine is set to.
        assertEquals("1,0 MB", formatBytes(1024 * 1024))
        assertEquals("1,50 GB", formatBytes((1.5 * 1024 * 1024 * 1024).toLong()))
        assertEquals("—", formatBytes(-1))
    }

    @Test
    fun `an unknown rate shows a dash rather than zero`() {
        assertEquals("—", formatRate(0))
        assertEquals("1,0 MB/s", formatRate(1024 * 1024))
    }

    @Test
    fun `durations get coarser as they get longer`() {
        assertEquals("45s", formatDuration(45))
        assertEquals("5 min", formatDuration(300))
        assertEquals("2 h 14 min", formatDuration(8040))
        assertEquals("—", formatDuration(-1))
    }
}
