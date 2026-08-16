package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rules behind the cache setting.
 *
 * What matters here is that the number the viewer chose is honoured in both directions: a budget
 * that quietly overruns is a disk filling up behind somebody's back, and one that ignores being
 * lowered is a setting that does not work.
 */
class CachePolicyTest {
    /** Zero is a real answer, and it has to mean "store nothing" rather than "store a little". */
    @Test
    fun `a budget of zero stores nothing`() {
        val budget = CacheBudget.ofGigabytes(0)

        assertFalse(budget.isEnabled)
        assertFalse(CachePolicy.canWrite(budget, bytesUsed = 0, nextItemBytes = 1_000))
    }

    @Test
    fun `writing is allowed while the budget has room`() {
        val budget = CacheBudget.ofGigabytes(1)

        assertTrue(CachePolicy.canWrite(budget, bytesUsed = 0, nextItemBytes = 100_000))
        assertTrue(
            CachePolicy.canWrite(budget, bytesUsed = budget.bytes - 100_000, nextItemBytes = 100_000),
        )
    }

    /**
     * The budget is a ceiling, not a target.
     *
     * A library whose artwork is larger than the chosen size fills to that size and stops. Anything
     * else would be the app deciding how much of somebody's disk to take.
     */
    @Test
    fun `writing stops at the ceiling rather than overrunning it`() {
        val budget = CacheBudget.ofGigabytes(1)

        assertFalse(
            CachePolicy.canWrite(budget, bytesUsed = budget.bytes - 50_000, nextItemBytes = 100_000),
            "an item that does not fit was allowed",
        )
        assertFalse(CachePolicy.canWrite(budget, bytesUsed = budget.bytes, nextItemBytes = 1))
    }

    /** Lowering the setting has to free space, or the person who lowered it has been ignored. */
    @Test
    fun `lowering the budget asks for the difference to be freed`() {
        val used = 5 * CacheBudget.BYTES_PER_GB

        assertEquals(
            3 * CacheBudget.BYTES_PER_GB,
            CachePolicy.bytesToFree(CacheBudget.ofGigabytes(2), used),
        )
        // Raising it frees nothing.
        assertEquals(0, CachePolicy.bytesToFree(CacheBudget.ofGigabytes(10), used))
        // And so does a budget that already fits exactly.
        assertEquals(0, CachePolicy.bytesToFree(CacheBudget.ofGigabytes(5), used))
    }

    /** Turning the cache off entirely frees all of it. */
    @Test
    fun `disabling the cache frees everything held`() {
        val used = 3 * CacheBudget.BYTES_PER_GB

        assertEquals(used, CachePolicy.bytesToFree(CacheBudget.DISABLED, used))
    }

    /** The slider cannot produce a value outside the range, and the type must not accept one. */
    @Test
    fun `a budget is clamped to the offered range`() {
        assertEquals(0, CacheBudget.ofGigabytes(-5).gigabytes)
        assertEquals(CacheBudget.MAX_GIGABYTES, CacheBudget.ofGigabytes(9_999).gigabytes)
        assertEquals(16, CacheBudget.ofGigabytes(16).gigabytes)
    }

    /**
     * Progress reports "unknown" differently from "nothing done".
     *
     * A bar stuck at the left and a bar that has not been told its length look identical on screen
     * and mean opposite things, so the model keeps them apart.
     */
    @Test
    fun `progress with no total has no fraction`() {
        assertNull(CacheFillProgress(done = 0, total = 0).fraction)
        assertEquals(0.5f, CacheFillProgress(done = 50, total = 100).fraction)
        assertEquals(1f, CacheFillProgress(done = 100, total = 100).fraction)
    }

    /** More done than total is a counting mistake, and must not draw past the end of the bar. */
    @Test
    fun `progress never exceeds one`() {
        assertEquals(1f, CacheFillProgress(done = 150, total = 100).fraction)
    }

    /**
     * The estimate is approximate and must be a plausible order of magnitude.
     *
     * It exists so the first-run screen can say "about 4 GB" rather than asking somebody to choose
     * blind. A number wrong by a factor of ten would be worse than no number.
     */
    @Test
    fun `the estimate is in the right order of magnitude`() {
        val fortyThousand = CachePolicy.estimatedBytesFor(40_000)

        assertTrue(
            fortyThousand in (2 * CacheBudget.BYTES_PER_GB)..(8 * CacheBudget.BYTES_PER_GB),
            "40.000 titles estimated at ${fortyThousand / CacheBudget.BYTES_PER_GB} GB",
        )
        assertEquals(0, CachePolicy.estimatedBytesFor(0))
        assertEquals(0, CachePolicy.estimatedBytesFor(-10))
    }

    /**
     * The figure the viewer reads while it downloads.
     *
     * Rounded down on purpose: a bar that says 100% with images still arriving is the one reading
     * that would be a lie, so 999 of 1000 has to stay at 99.
     */
    @Test
    fun `the percentage reports how far the fill has got`() {
        assertEquals(0, CacheFillProgress(done = 0, total = 1_000).percent)
        assertEquals(50, CacheFillProgress(done = 500, total = 1_000).percent)
        assertEquals(99, CacheFillProgress(done = 999, total = 1_000).percent)
        assertEquals(100, CacheFillProgress(done = 1_000, total = 1_000).percent)
    }

    /** Nothing to measure yet reads as unknown, not as zero: the two look identical and are not. */
    @Test
    fun `the percentage is absent until a total is known`() {
        assertNull(CacheFillProgress(done = 0, total = 0).percent)
    }

    @Test
    fun `a paused fill with work left is resumable`() {
        val paused = CacheFillProgress(done = 20, total = 100, state = CacheFillState.PAUSED)
        assertTrue(paused.isResumable)

        val finished = CacheFillProgress(done = 100, total = 100, state = CacheFillState.COMPLETE)
        assertFalse(finished.isResumable)
    }
}
