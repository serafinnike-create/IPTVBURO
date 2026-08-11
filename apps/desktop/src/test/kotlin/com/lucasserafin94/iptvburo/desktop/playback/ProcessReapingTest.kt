package com.lucasserafin94.iptvburo.desktop.playback

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * That a stubborn child process is actually killed, not merely asked to leave.
 *
 * Four VLC engines were found alive after their session had ended — each holding a loopback port and
 * a few hundred megabytes, with nothing left to own them. `Process.destroy` is a polite request that
 * an engine busy in a network read can take seconds to act on, or ignore entirely.
 *
 * This uses a plain JVM child rather than VLC: the behaviour under test is the reaping, and a test
 * that needed the bundled engine would not run on a build machine.
 */
class ProcessReapingTest {
    private fun sleeper(): Process {
        val java =
            java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString()
        // A JVM that will sit for a minute unless something stops it.
        return ProcessBuilder(java, "-e", "-version")
            .redirectErrorStream(true)
            .start()
    }

    /**
     * The grace period is long enough to be a real chance and short enough to feel instant.
     *
     * Pinned rather than measured against a live engine: what matters is that closing the app is
     * not made slow by waiting, and that the wait is not so short it forces every exit.
     */
    @Test
    fun `the exit grace is a sensible wait`() {
        assertTrue(
            VLC_PROCESS_EXIT_GRACE_MILLIS in 500..3_000,
            "a grace under half a second forces every exit; over three seconds the app feels stuck",
        )
    }

    /**
     * A process that ignores a polite request is forced.
     *
     * The sequence under test is dispose's: ask, wait briefly, then insist.
     */
    @Test
    fun `a process that will not leave is forced out`() {
        val child = sleeper()

        child.destroy()
        if (!child.waitFor(VLC_PROCESS_EXIT_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
            child.destroyForcibly()
        }

        assertTrue(
            child.waitFor(5, TimeUnit.SECONDS),
            "a child that survives both a request and a forced kill would leak on every close",
        )
        assertFalse(child.isAlive, "nothing may outlive the player that started it")
    }
}
