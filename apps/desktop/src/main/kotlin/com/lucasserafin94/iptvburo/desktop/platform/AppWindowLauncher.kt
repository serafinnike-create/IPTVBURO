package com.lucasserafin94.iptvburo.desktop.platform

import com.sun.jna.Platform
import java.nio.file.Files
import java.nio.file.Path

/**
 * Opens a web service's page in a window of its own, through Microsoft Edge or Google Chrome.
 *
 * The app's embedded Chromium cannot play the service's episodes: it is the open-source build,
 * which ships without the licensed video codecs (H.264, AAC) and without Widevine, and GoDorama
 * answers "Este vídeo não pode ser reproduzido neste navegador". Edge is on every Windows 10 and
 * 11 machine and has both. Its `--app` mode opens the page with no tabs and no address bar, so it
 * looks like part of BURO rather than a browser.
 *
 * Each BURO profile gets its own browser profile folder ([profileDirectory]): the sign-in is kept
 * between launches, and two adults on one computer never land in each other's account. Nothing is
 * read from that folder by BURO; it belongs to the browser.
 */
object AppWindowLauncher {
    /**
     * Opens [url] in an app window. False when no suitable browser is installed, and the caller
     * falls back to the default browser.
     */
    fun open(
        url: String,
        profileDirectory: Path,
    ): Boolean {
        if (!url.startsWith("https://")) return false
        val browser = installedBrowser() ?: return false
        return runCatching {
            Files.createDirectories(profileDirectory)
            ProcessBuilder(command(browser, url, profileDirectory))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
            true
        }.getOrDefault(false)
    }

    /** The command line for [browser]. Separate so the arguments can be checked without a browser. */
    internal fun command(
        browser: Path,
        url: String,
        profileDirectory: Path,
    ): List<String> =
        listOf(
            browser.toString(),
            "--app=$url",
            "--user-data-dir=$profileDirectory",
            "--no-first-run",
            "--no-default-browser-check",
        )

    /** Edge first on Windows, where it is always present; Chrome otherwise. */
    internal fun installedBrowser(candidates: List<Path> = candidates()): Path? =
        candidates.firstOrNull { path -> runCatching { Files.isRegularFile(path) }.getOrDefault(false) }

    private fun candidates(): List<Path> =
        when {
            Platform.isWindows() -> {
                val programFiles = listOfNotNull(System.getenv("ProgramFiles(x86)"), System.getenv("ProgramFiles"))
                val local = System.getenv("LOCALAPPDATA")
                programFiles.map { Path.of(it, "Microsoft", "Edge", "Application", "msedge.exe") } +
                    programFiles.map { Path.of(it, "Google", "Chrome", "Application", "chrome.exe") } +
                    listOfNotNull(local?.let { Path.of(it, "Google", "Chrome", "Application", "chrome.exe") })
            }
            Platform.isMac() ->
                listOf(
                    Path.of("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"),
                    Path.of("/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"),
                )
            else -> emptyList()
        }
}
