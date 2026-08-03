package com.lucasserafin94.iptvburo.desktop.update

import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Probe against the real GitHub API, disabled by default.
 *
 * The MockWebServer tests prove parsing; only a live call proves the releases endpoint is readable
 * without a token, which is what broke while the repository was private. Enabled with
 * `-DburoLiveUpdaterProbe=true` so ordinary test runs stay offline and deterministic.
 */
class LiveUpdaterProbe {
    @Test
    fun `real github releases are reachable without a token`() = runBlocking {
        if (System.getProperty("buroLiveUpdaterProbe") != "true") return@runBlocking

        val updater = GitHubReleaseUpdater(currentVersion = "0.2.0-alpha.1")
        when (val result = updater.check()) {
            is UpdateCheckResult.Available ->
                println(
                    "PROBE AVAILABLE version=${result.release.version} " +
                        "asset=${result.release.assetName} sha256=${result.release.sha256}",
                )
            UpdateCheckResult.UpToDate -> println("PROBE UP_TO_DATE")
            is UpdateCheckResult.Failed -> println("PROBE FAILED ${result.userMessage}")
        }
    }
}
