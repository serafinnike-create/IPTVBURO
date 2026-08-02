package com.lucasserafin94.iptvburo.desktop.update

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GitHubReleaseUpdaterTest {
    @Test
    fun `semantic preview versions are ordered safely`() {
        assertTrue(isNewerVersion("0.2.0-alpha.2", "0.2.0-alpha.1"))
        assertTrue(isNewerVersion("0.2.0", "0.2.0-alpha.9"))
        assertTrue(isNewerVersion("0.3.0-alpha.1", "0.2.9"))
        assertFalse(isNewerVersion("0.1.0", "0.2.0-alpha.2"))
        assertFalse(isNewerVersion("invalid", "0.2.0-alpha.2"))
    }

    @Test
    fun `release check accepts only a newer installer with github digest`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    [{
                      "draft": false,
                      "tag_name": "v0.2.0-alpha.3",
                      "name": "Preview 0.2 alpha 3",
                      "assets": [{
                        "name": "IPTVBURO-0.2.2.msi",
                        "browser_download_url": "https://github.com/lucasserafin94/IPTVBURO/releases/download/v0.2.0-alpha.3/IPTVBURO-0.2.2.msi",
                        "size": 1234,
                        "digest": "sha256:${"0".repeat(64)}"
                      }]
                    }]
                    """.trimIndent(),
                ),
            )
            val updater =
                GitHubReleaseUpdater(
                    releasesUrl = server.url("/releases").toString(),
                    currentVersion = "0.2.0-alpha.2",
                )
            val result = updater.check()
            assertIs<UpdateCheckResult.Available>(result)
            assertTrue(result.release.version == "0.2.0-alpha.3")
        }
    }
}
