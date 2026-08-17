package com.lucasserafin94.iptvburo.data.cache

import com.lucasserafin94.iptvburo.domain.model.CacheBudget
import com.lucasserafin94.iptvburo.domain.model.CachePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the cache setting promises, asserted at the boundary the fill actually uses.
 *
 * The pure rules have their own suite in the domain module. These are the two questions the Android
 * side gets wrong if the wiring drifts: that the viewer's ceiling is honoured, and that a
 * credential never reaches the disk because the fill decided to warm it.
 */
class CacheBudgetTest {
    @Test
    fun `the chosen size is the ceiling the fill stops at`() {
        val budget = CacheBudget.ofGigabytes(1)
        val nearlyFull = budget.bytes - 1_000

        assertFalse(
            "Writing past the chosen size would make the setting a suggestion.",
            CachePolicy.canWrite(budget, bytesUsed = nearlyFull, nextItemBytes = 110_000),
        )
        assertTrue(CachePolicy.canWrite(budget, bytesUsed = 0, nextItemBytes = 110_000))
    }

    /**
     * A full cache is a finished job, not an interrupted one.
     *
     * Found on a device: with the budget spent, the card said "Nada baixado ainda" and offered
     * Continuar — a button that stopped again the instant it was pressed, because there was no room
     * left to write into. The distinction matters to what the viewer is offered next.
     */
    @Test
    fun `a spent budget leaves no room for another item`() {
        val budget = CacheBudget.ofGigabytes(2)

        assertFalse(
            "A cache filled to its budget has to stop offering to download more.",
            CachePolicy.canWrite(budget, bytesUsed = budget.bytes, nextItemBytes = 110_000),
        )
        assertEquals(0L, CachePolicy.bytesToFree(budget, bytesUsed = budget.bytes))
    }

    @Test
    fun `zero keeps the app behaving as it did before the cache existed`() {
        assertFalse(CacheBudget.DISABLED.isEnabled)
        assertFalse(CachePolicy.canWrite(CacheBudget.DISABLED, bytesUsed = 0, nextItemBytes = 1))
    }

    @Test
    fun `the slider cannot ask for more than the product offers`() {
        assertEquals(CacheBudget.MAX_GIGABYTES, CacheBudget.ofGigabytes(4_096).gigabytes)
        assertEquals(0, CacheBudget.ofGigabytes(-5).gigabytes)
    }

    /**
     * Where a resumed fill picks up.
     *
     * Found on a device: pressing Continuar sent the counter from 800 back to 300, because the
     * worker always walked the list from the start. The offset is only trusted when it describes
     * the *same* list — a mark from a list that has since changed size would skip real work or
     * point past the end.
     */
    @Test
    fun `a resumed fill continues from the mark only when it still fits the list`() {
        assertEquals(800, resumeOffset(markDone = 800, markTotal = 29_314, urlCount = 29_314))

        // The list changed size, so the old position means nothing.
        assertEquals(0, resumeOffset(markDone = 800, markTotal = 29_314, urlCount = 12_000))
        // Finished, or past the end: start afresh rather than skip everything.
        assertEquals(0, resumeOffset(markDone = 29_314, markTotal = 29_314, urlCount = 29_314))
        assertEquals(0, resumeOffset(markDone = 0, markTotal = 0, urlCount = 29_314))
    }

    /**
     * The reason the disk cache could be turned back on at all.
     *
     * An ordinary Xtream poster is a static path and is kept; the provider's authenticated
     * endpoints carry `/<kind>/<username>/<password>/<id>` and must never be written to disk, no
     * matter which side asks for them.
     */
    @Test
    fun `the fill refuses to warm an address that carries a credential`() {
        assertTrue(isStorableArtwork("http://provider.example/images/abc.jpg"))

        assertFalse(isStorableArtwork("http://provider.example/movie/subscriber/hunter2/991.jpg"))
        assertFalse(isStorableArtwork("http://provider.example/Series/subscriber/hunter2/1.jpg"))
        assertFalse(isStorableArtwork("https://subscriber:hunter2@provider.example/p/1.jpg"))
        assertFalse(isStorableArtwork("https://provider.example/p.jpg?token=abc"))
    }
}
