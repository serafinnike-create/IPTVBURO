package com.lucasserafin94.iptvburo.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRateTest {
    @Test
    fun `a steady megabyte a second reads as a megabyte a second`() {
        val rate = DownloadRate()
        // 64 KiB every 64 ms is a MiB per second.
        var now = 0L
        repeat(20) {
            now += 64
            rate.record(64 * 1024, now)
        }

        val measured = requireNotNull(rate.bytesPerSecond(now))

        // Within a few percent: the window holds whole samples, so its edge lands where it lands.
        val expected = 1024L * 1024L
        assertTrue(
            "expected about $expected bytes per second, measured $measured",
            measured > expected * 9 / 10 && measured < expected * 11 / 10,
        )
    }

    @Test
    fun `one sample is a size, not a rate`() {
        val rate = DownloadRate()
        rate.record(64 * 1024, 100)

        assertNull(rate.bytesPerSecond(100))
    }

    @Test
    fun `nothing recorded reports nothing rather than zero`() {
        // Zero on screen reads as "stopped", which is a claim this cannot make.
        assertNull(DownloadRate().bytesPerSecond(1_000))
    }

    @Test
    fun `samples too close together do not invent a figure`() {
        val rate = DownloadRate()
        rate.record(1_024, 0)
        rate.record(1_024, 10)

        assertNull(rate.bytesPerSecond(10))
    }

    @Test
    fun `a stalled connection is reflected, not averaged away`() {
        val rate = DownloadRate(windowMillis = 2_000)
        var now = 0L
        // A healthy start.
        repeat(20) {
            now += 50
            rate.record(64 * 1024, now)
        }
        val healthy = requireNotNull(rate.bytesPerSecond(now))

        // Then it crawls: the same bytes take twenty times as long.
        repeat(20) {
            now += 1_000
            rate.record(64 * 1024, now)
        }
        val crawling = requireNotNull(rate.bytesPerSecond(now))

        assertTrue(
            "a stall must lower the reading: healthy=$healthy crawling=$crawling",
            crawling < healthy / 5,
        )
    }

    @Test
    fun `the total counts every byte, beyond the window`() {
        val rate = DownloadRate(windowMillis = 100)
        var now = 0L
        repeat(10) {
            now += 500
            rate.record(1_000, now)
        }

        assertEquals(10_000L, rate.totalBytes())
        // And the rate still answers, having kept enough of a tail to divide by.
        assertNotNull(rate.bytesPerSecond(now))
    }

    @Test
    fun `an empty read changes nothing`() {
        val rate = DownloadRate()
        rate.record(0, 100)
        rate.record(-1, 200)

        assertEquals(0L, rate.totalBytes())
        assertNull(rate.bytesPerSecond(200))
    }
}
