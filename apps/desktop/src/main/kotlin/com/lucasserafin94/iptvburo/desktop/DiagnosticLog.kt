package com.lucasserafin94.iptvburo.desktop

import java.io.OutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Mirrors the console to a file the user can send back.
 *
 * The installed app has no console: `println` goes to a stream nobody can read, so every diagnostic
 * the code already prints is lost precisely when it is needed. Reproducing a fault has meant running
 * the app from Gradle — which is not something a customer can do, and not something that reproduces
 * a fault that only happens on their machine.
 *
 * ## What may be written
 *
 * Only what the code already prints. Every existing line was written under the rule that no URL,
 * credential, token or provider address may appear in a log, and this changes where those lines go,
 * not what they say. The same rule binds anything added later: this file lives on the user's disk
 * for weeks and may be attached to an email.
 *
 * ## Why it is capped and rotated
 *
 * A log that grows without limit is a bug of its own. The previous session is kept as `.1` so a
 * fault that kills the app is still readable after the restart that follows it.
 */
internal object DiagnosticLog {
    private const val MAX_BYTES = 2L * 1024 * 1024

    /**
     * Starts mirroring, once.
     *
     * Failures are swallowed deliberately: a read-only disk or a locked file must cost the user
     * their diagnostics, never their app.
     */
    fun start(directory: Path = defaultDirectory()) {
        runCatching {
            Files.createDirectories(directory)
            val current = directory.resolve("iptvburo.log")
            rotateIfLarge(current, directory.resolve("iptvburo.log.1"))

            val file =
                Files.newOutputStream(
                    current,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE,
                )

            System.setOut(PrintStream(fanOut(System.out, file), true))
            System.setErr(PrintStream(fanOut(System.err, file), true))

            println(
                "=== IPTV BURO started ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)} ===",
            )
        }
    }

    /** Where the user can find the file, for the settings screen to show. */
    fun location(directory: Path = defaultDirectory()): Path = directory.resolve("iptvburo.log")

    private fun rotateIfLarge(current: Path, previous: Path) {
        runCatching {
            if (Files.isRegularFile(current) && Files.size(current) > MAX_BYTES) {
                Files.deleteIfExists(previous)
                Files.move(current, previous)
            }
        }
    }

    /**
     * One stream writing to both the console and the file.
     *
     * Console first: if the file write throws — a full disk, a removed drive — the line has already
     * reached the terminal, and the throw is swallowed so logging can never take the app down.
     */
    private fun fanOut(console: PrintStream, file: OutputStream): OutputStream =
        object : OutputStream() {
            /** Whether the next byte begins a line, and therefore wants a timestamp in front. */
            private var atLineStart = true

            private fun stamp(): String =
                java.time.LocalTime.now().format(STAMP_FORMAT) + " "

            override fun write(byte: Int) {
                console.write(byte)
                runCatching {
                    if (atLineStart) file.write(stamp().toByteArray(StandardCharsets.UTF_8))
                    file.write(byte)
                    atLineStart = byte == '\n'.code
                }
            }

            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                console.write(bytes, offset, length)
                runCatching {
                    // Stamped at the start of each line.
                    //
                    // Without this a log tells you what happened but not when, and a fault whose
                    // signature is its rhythm — a stall every thirty seconds is a segment boundary,
                    // one at random is a network problem — is indistinguishable from any other.
                    if (atLineStart) {
                        file.write(stamp().toByteArray(StandardCharsets.UTF_8))
                    }
                    file.write(bytes, offset, length)
                    atLineStart = length > 0 && bytes[offset + length - 1] == '\n'.code.toByte()
                }
            }

            override fun flush() {
                console.flush()
                runCatching { file.flush() }
            }
        }

    fun defaultDirectory(): Path = Path.of(System.getProperty("user.home"), ".iptvburo", "logs")

    /** Time of day only. The date is in the session banner, and a full stamp per line is noise. */
    private val STAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
}
