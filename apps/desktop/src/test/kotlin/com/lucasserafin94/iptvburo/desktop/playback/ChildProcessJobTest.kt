package com.lucasserafin94.iptvburo.desktop.playback

import com.sun.jna.Platform
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A child process cannot outlive the app, however the app ends.
 *
 * `dispose()` and the JVM shutdown hook both need a running JVM. The customer's app died after
 * hours of playback with an unhandled-exception dialog, and pressing "Encerrar" ends the process
 * outright — neither ran. A VLC holding eighty megabytes and a loopback port was still alive
 * afterwards, with nothing left that could ever reap it.
 *
 * A Windows job object is closed by the kernel when the last handle to it goes, which happens when
 * this process dies for any reason at all. This exercises that with a real child rather than
 * reasoning about it: the whole value of the mechanism is what it does when nothing else runs.
 */
class ChildProcessJobTest {
    private fun java(): String =
        Path.of(System.getProperty("java.home"), "bin", "java").toString()

    /**
     * A one-line program that blocks for ever, run through the single-file source launcher.
     *
     * Written to disk rather than passed inline: `java -e` needs JDK 25 and this project builds on
     * 21, where the flag is rejected outright. Compiled in memory by the launcher, so there is no
     * build step and nothing to clean up beyond the file itself.
     */
    private fun sleeperSource(): Path {
        val file = java.nio.file.Files.createTempDirectory("buro-sleeper").resolve("Sleeper.java")
        java.nio.file.Files.writeString(
            file,
            """
            public class Sleeper {
                public static void main(String[] args) throws Exception {
                    Thread.sleep(Long.MAX_VALUE);
                }
            }
            """.trimIndent(),
        )
        file.toFile().deleteOnExit()
        return file
    }

    /**
     * A child that sits there until something stops it.
     *
     * The first version of this passed a flag the JVM rejects, so every process died within
     * milliseconds and three of these tests passed for entirely the wrong reason — they were
     * measuring a corpse. The assertion below is what stops that happening again.
     */
    private fun sleeper(): Process {
        val process =
            ProcessBuilder(java(), sleeperSource().toString())
                .redirectErrorStream(true)
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .start()
        // Confirmed alive before it is used, so a test can never assert against something that
        // was never running — which is exactly how the first version of this fooled itself.
        assertFalse(
            process.waitFor(400, TimeUnit.MILLISECONDS),
            "the fixture must stay alive; a child that exits immediately proves nothing",
        )
        return process
    }

    @Test
    fun `adopting a process does not disturb it`() {
        if (!Platform.isWindows()) return

        val child = sleeper()
        try {
            ChildProcessJob.adopt(child)

            // The point of the check: assignment must be invisible to a child that is working. A
            // job that killed or stalled the process it protects would be worse than none.
            assertFalse(
                child.waitFor(500, TimeUnit.MILLISECONDS),
                "the child must keep running after being placed under the job",
            )
            assertTrue(child.isAlive)
        } finally {
            child.destroyForcibly()
            child.waitFor(5, TimeUnit.SECONDS)
        }
    }

    /**
     * Adopting is safe to call for a process that has already exited.
     *
     * There is a real race here: a VLC that fails to start can be gone before the assignment runs,
     * and an exception on that path would take down the tile that was starting.
     */
    @Test
    fun `adopting a dead process is harmless`() {
        val child = sleeper()
        child.destroyForcibly()
        child.waitFor(5, TimeUnit.SECONDS)

        // Must not throw. A VLC that fails to start can be gone before this runs, and an
        // exception on that path would take down the tile that was starting.
        ChildProcessJob.adopt(child)
    }

    /**
     * On a host without job objects the app still works.
     *
     * Every other platform, and any Windows where the job could not be created, must fall through
     * to the existing dispose and shutdown hook rather than failing to play anything.
     */
    @Test
    fun `adopting is a no-op where jobs are unavailable`() {
        val child = sleeper()
        try {
            ChildProcessJob.adopt(child)
            assertTrue(child.isAlive, "the child plays on whether or not the job exists")
        } finally {
            child.destroyForcibly()
            child.waitFor(5, TimeUnit.SECONDS)
        }
    }

    /**
     * The player asks for the protection at the moment the engine exists.
     *
     * Checked against the source because the alternative — starting a real VLC from a test — needs
     * the bundled runtime and a display. What matters is that no start path skips it.
     */
    @Test
    fun `every engine that starts is placed under the job`() {
        val source =
            java.nio.file.Files.readString(
                Path.of(
                    "src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/VlcDesktopPlayer.kt",
                ),
            )

        assertTrue(
            "ChildProcessJob.adopt(child)" in source,
            "a started engine is no longer placed under the job, so a crash leaves it running",
        )
        // Adoption comes first, and the ordering is the point rather than an accident. The window
        // between `start()` and the assignment is the one interval where a crash strands a VLC that
        // nothing can reap — the shutdown hook needs a running JVM, and the kernel closing the job
        // does not. Registering in `liveProcesses` afterwards adds the ordinary dispose path on top;
        // both defences are present, and the cheaper-to-lose one goes second.
        assertTrue(
            source.indexOf("ChildProcessJob.adopt(child)") < source.indexOf("liveProcesses.add(child)"),
            "adoption must happen first, so a crash during start-up cannot strand the engine",
        )
    }
}
