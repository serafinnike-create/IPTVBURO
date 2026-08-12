package com.lucasserafin94.iptvburo.desktop.security

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Nothing the app prints may carry a credential, a stream address, or an API key.
 *
 * This repository is public and the app talks to a provider account. Two things make that easy to
 * get wrong by accident:
 *
 * - A provider stream URL embeds the username and password. Printing the MRL, or an exception whose
 *   message contains it, publishes the user's subscription.
 * - TMDb takes its API key as a **query parameter**, and OkHttp puts the full request URL into its
 *   IOException messages. So `println(error.message)` on a failed metadata call can print the key —
 *   which is exactly what one line in DesktopAppState did until this test was written.
 *
 * The rule enforced here is narrow and mechanical: a `println` may not interpolate anything that
 * looks like a URL, a request, or an exception message. Diagnostics stay possible — failure types,
 * state names, sizes and counts are all still printable, and that is what the existing logs use.
 */
class LogRedactionTest {
    private val sources: List<Path> =
        Files.walk(Path.of("src/main/kotlin")).use { stream ->
            stream.filter { path -> path.extension == "kt" }.toList()
        }

    @Test
    fun `no println interpolates a URL, a request, or an exception message`() {
        val offenders = mutableListOf<String>()

        sources.forEach { path ->
            path.readText().lines().forEachIndexed { index, line ->
                if (!line.contains("println(")) return@forEachIndexed
                FORBIDDEN.forEach { (name, pattern) ->
                    if (pattern.containsMatchIn(line)) {
                        offenders += "  ${path.fileName}:${index + 1} [$name] ${line.trim()}"
                    }
                }
            }
        }

        assertTrue(
            offenders.isEmpty(),
            "These log lines can publish a credential, a stream address or an API key:\n" +
                offenders.joinToString("\n") +
                "\n\nPrint the failure type, a state name or a count instead.",
        )
    }

    /**
     * VLC's own output stays discarded.
     *
     * VLC logs the MRL it was handed, and for a provider source that string contains the username
     * and password in the clear. Redirecting its streams anywhere but DISCARD would write those to
     * a file or a console.
     */
    @Test
    fun `the VLC process has both its streams discarded`() {
        val player = Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/VlcDesktopPlayer.kt").readText()

        assertTrue(
            player.contains("redirectOutput(ProcessBuilder.Redirect.DISCARD)"),
            "VLC's stdout must be discarded: it echoes the MRL, which carries the credentials",
        )
        assertTrue(
            player.contains("redirectError(ProcessBuilder.Redirect.DISCARD)"),
            "VLC's stderr must be discarded for the same reason",
        )
    }

    private companion object {
        /**
         * Interpolations that can carry a secret into a log line.
         *
         * Matched on the interpolation itself rather than on the whole line, so a message that
         * merely mentions the word "url" in prose is not flagged.
         */
        val FORBIDDEN =
            listOf(
                "url" to Regex("""\$\{?[A-Za-z0-9_.]*[Uu]rl\b"""),
                "uri" to Regex("""\$\{?[A-Za-z0-9_.]*[Uu]ri\b"""),
                "mrl" to Regex("""\$\{?[A-Za-z0-9_.]*[Mm]rl\b"""),
                "request" to Regex("""\$\{?[A-Za-z0-9_.]*[Rr]equest\b"""),
                "exception message" to Regex("""\$\{[A-Za-z0-9_.]*(error|exception|throwable|it)\.message"""),
                "credentials" to Regex("""\$\{?[A-Za-z0-9_.]*(password|username|credential|apiKey|api_key)\b""", RegexOption.IGNORE_CASE),
            )
    }
}
