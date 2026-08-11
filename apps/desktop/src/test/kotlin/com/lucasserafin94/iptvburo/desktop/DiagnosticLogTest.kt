package com.lucasserafin94.iptvburo.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The console, mirrored to a file the user can send back.
 *
 * The installed app has no console, so every diagnostic already printed by the code was lost exactly
 * when it mattered. Reproducing a fault meant running from Gradle, which a customer cannot do — and
 * which does not reproduce a fault that only happens on their machine.
 */
class DiagnosticLogTest {
    private val directory: Path = createTempDirectory("buro-log-test")
    private val originalOut = System.out
    private val originalErr = System.err

    @AfterTest
    fun cleanUp() {
        // Restored first. Leaving the redirect in place would send every later test's output into a
        // deleted temporary file, and the failure would surface somewhere unrelated.
        System.setOut(originalOut)
        System.setErr(originalErr)
        Files.walk(directory).sorted(Comparator.reverseOrder()).forEach { path ->
            runCatching { Files.deleteIfExists(path) }
        }
    }

    @Test
    fun `what is printed reaches the file`() {
        DiagnosticLog.start(directory)

        println("[tile1] state=playing length=-1")
        System.out.flush()

        val contents = Files.readString(DiagnosticLog.location(directory))
        assertTrue(
            "[tile1] state=playing length=-1" in contents,
            "a printed diagnostic must be readable afterwards: $contents",
        )
    }

    /**
     * The console keeps working.
     *
     * Mirroring must add a destination, not move one: a developer running from Gradle needs the
     * terminal, and silently losing it would be traded for the file without anyone noticing.
     */
    @Test
    fun `printing still reaches the console`() {
        val captured = java.io.ByteArrayOutputStream()
        System.setOut(java.io.PrintStream(captured, true))

        DiagnosticLog.start(directory)
        println("visible in both places")
        System.out.flush()

        assertTrue("visible in both places" in captured.toString())
    }

    /**
     * A session is marked, so two runs are not read as one.
     *
     * A file that appends across launches is what makes a crash readable after the restart that
     * follows it, and without a boundary the lines from before and after run together.
     */
    @Test
    fun `each start marks the session`() {
        DiagnosticLog.start(directory)
        System.out.flush()

        assertTrue("IPTV BURO started" in Files.readString(DiagnosticLog.location(directory)))
    }

    /**
     * A log that grows without limit is a bug of its own.
     *
     * The previous session is kept rather than discarded: a fault that kills the app is usually
     * diagnosed after the restart, and by then the current file has already begun again.
     */
    @Test
    fun `an oversized log is rotated rather than grown`() {
        val current = directory.resolve("iptvburo.log")
        Files.createDirectories(directory)
        Files.write(current, ByteArray(3 * 1024 * 1024))

        DiagnosticLog.start(directory)
        System.out.flush()

        assertTrue(Files.isRegularFile(directory.resolve("iptvburo.log.1")), "the old log is kept")
        assertTrue(
            Files.size(current) < 1024 * 1024,
            "the new log starts fresh rather than continuing the old one",
        )
    }

    /**
     * A directory that cannot be written costs diagnostics, never the app.
     *
     * Logging is the least important thing this process does, and taking the app down because it
     * could not open a file would be the worst possible trade.
     */
    @Test
    fun `a broken destination does not take the app down`() {
        // A path whose parent is a file, so createDirectories cannot succeed.
        val blocker = directory.resolve("blocker")
        Files.writeString(blocker, "not a directory")

        DiagnosticLog.start(blocker.resolve("logs"))

        println("this must not throw")
    }

    /**
     * Nothing secret is written by the mechanism itself.
     *
     * The content rule lives with each `println`, and every existing one was written under it. This
     * pins the one line this file adds — the session banner — so a future change that put a user or
     * an account in it would fail here rather than on somebody's disk.
     */
    @Test
    fun `the session banner carries nothing identifying`() {
        DiagnosticLog.start(directory)
        System.out.flush()

        val banner =
            Files.readAllLines(DiagnosticLog.location(directory))
                .first { line -> "IPTV BURO started" in line }
                .lowercase()

        listOf("http", "password", "token", "username", "@", "key=").forEach { secret ->
            assertEquals(
                false,
                secret in banner,
                "the session banner must never contain '$secret': $banner",
            )
        }
    }
}
