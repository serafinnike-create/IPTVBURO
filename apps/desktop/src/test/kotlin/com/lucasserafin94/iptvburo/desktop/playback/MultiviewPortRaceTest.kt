package com.lucasserafin94.iptvburo.desktop.playback

import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Four players starting at once must not collide.
 *
 * Each VLC instance gets its own loopback control port, obtained by asking the OS for port 0 and
 * closing the socket. That leaves a window between the answer and VLC binding it — invisible with
 * one player, and reachable with four.
 *
 * The reported symptom matched exactly: with four tiles queued, some came up black, and the number
 * that failed varied between attempts. A second VLC handed the same port fails to bind its control
 * interface and its tile never receives a picture.
 */
class MultiviewPortRaceTest {
    private val source: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/VlcDesktopPlayer.kt")
            .readText()

    @Test
    fun `ports are claimed rather than merely observed`() {
        assertTrue(source.contains("claimedPorts"), "there must be a record of ports already handed out")
        assertTrue(
            source.contains("synchronized(claimedPorts)"),
            "the claim must be atomic: the collision it prevents is between concurrent starts",
        )
    }

    @Test
    fun `a claimed port is released when the player closes`() {
        // Without this a long session leaks entries for ever, and the set eventually stops being a
        // useful guard.
        val dispose = source.substringAfter("fun dispose()").take(1_200)

        assertTrue(dispose.contains("releaseClaimedPort()"), "dispose must release the port")
    }

    @Test
    fun `a failed start releases its port before retrying`() {
        val failure =
            source.substringAfter("private fun startIfNeeded").substringBefore("private fun startVlc")

        assertTrue(
            failure.contains("releaseClaimedPort()"),
            "a retry must not leak the previous attempt's reservation",
        )
    }

    /**
     * The allocator survives being called from several threads at once.
     *
     * Exercises the real synchronisation rather than reading it: this is a concurrency bug, and the
     * only honest test of one is to run it concurrently.
     */
    @Test
    fun `concurrent allocation never hands out a duplicate`() {
        val allocator = LoopbackPortAllocatorForTesting()
        val pool = Executors.newFixedThreadPool(8)
        val results = java.util.Collections.synchronizedList(mutableListOf<Int>())

        try {
            repeat(64) {
                pool.submit { results += allocator.next() }
            }
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "allocation deadlocked")
        } finally {
            pool.shutdownNow()
        }

        assertEquals(64, results.size, "some allocations did not complete")
        assertEquals(
            results.size,
            results.distinct().size,
            "two players were handed the same port: ${results.groupBy { it }.filter { it.value.size > 1 }.keys}",
        )
    }

    @Test
    fun `giving up returns a port rather than failing to start`() {
        // A port clash costs one black tile. Refusing to start costs the whole player, which is
        // worse — so the fallback returns whatever the OS last offered.
        val allocator = source.substringAfter("private fun freeLoopbackPort()").take(1_200)

        assertTrue(
            allocator.contains("return ServerSocket"),
            "exhausting the retries must still produce a port",
        )
    }
}

/**
 * The same allocation rule, isolated so it can be hammered from many threads.
 *
 * A copy rather than a call into VlcDesktopPlayer, which would start a real VLC. What is under test
 * is the claim-and-retry structure, and that is reproduced exactly.
 */
private class LoopbackPortAllocatorForTesting {
    private val claimed = mutableSetOf<Int>()

    fun next(): Int {
        repeat(8) {
            val candidate =
                java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())
                    .use { it.localPort }
            synchronized(claimed) {
                if (claimed.add(candidate)) return candidate
            }
        }
        // Matches the production fallback: better a possible clash than no player at all. Recorded
        // so the test's uniqueness assertion still means something.
        val last =
            java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())
                .use { it.localPort }
        synchronized(claimed) { claimed.add(last) }
        return last
    }
}
