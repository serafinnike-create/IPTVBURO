package com.lucasserafin94.iptvburo.desktop.update

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
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

    /**
     * The upgrade the people testing this build will actually perform.
     *
     * Someone running 2.0.0-alpha.2 must be offered 2.0.0-alpha.3 by "Buscar atualização", and must
     * not be offered the build they are already on. Written with the real numbers rather than the
     * 0.2.x placeholders above, because that is the claim being made to those users.
     */
    @Test
    fun `an alpha 2 install is offered alpha 3 and not itself`() {
        assertTrue(isNewerVersion("2.0.0-alpha.3", "2.0.0-alpha.2"))
        assertFalse(isNewerVersion("2.0.0-alpha.2", "2.0.0-alpha.2"))
        assertFalse(isNewerVersion("2.0.0-alpha.1", "2.0.0-alpha.2"))
        // "2.0" is the *final* 2.0.0, and a final release outranks any preview of the same number,
        // so an alpha must not be offered to someone already on it. Asserted rather than assumed:
        // the first version of this test had it backwards.
        assertFalse(isNewerVersion("2.0.0-alpha.3", "2.0"))
    }

    /**
     * The version the app actually ships as must parse.
     *
     * DESKTOP_VERSION has been written as two numbers — "1.1", then "2.0" — while the parser
     * requires three. An unparseable *current* version made compareVersions return 1 for every
     * candidate, so the updater would have offered the running build to itself as an update, over
     * and over. Nothing in the suite caught it because every existing case used three numbers on
     * both sides.
     */
    @Test
    fun `the shipped version string is comparable`() {
        // The shape, not the value. Pinning the literal made this fail on every legitimate version
        // bump while detecting nothing: the bug it exists for is a version the parser cannot read,
        // and "2.0" is unparseable whatever release it belongs to.
        assertTrue(
            Regex("""^\d+\.\d+\.\d+(-.+)?$""").matches(DESKTOP_VERSION),
            "DESKTOP_VERSION must carry three numbers or the comparator misreads it: '$DESKTOP_VERSION'",
        )
        assertEquals(
            "https://api.github.com/repos/serafinnike-create/IPTVBURO/releases?per_page=20",
            DESKTOP_RELEASES_URL,
        )
        assertFalse(
            isNewerVersion(DESKTOP_VERSION, DESKTOP_VERSION),
            "the running build must never be newer than itself — DESKTOP_VERSION is '$DESKTOP_VERSION'",
        )
        assertTrue(isNewerVersion("99.0.0", DESKTOP_VERSION), "a genuinely newer release must still be offered")
        assertFalse(isNewerVersion("0.0.1", DESKTOP_VERSION), "an older release must not be offered")
    }

    /** Two-number versions are what this project writes, so they have to order correctly. */
    @Test
    fun `two-number versions compare like their three-number form`() {
        assertTrue(isNewerVersion("2.0", "1.1"))
        assertFalse(isNewerVersion("1.1", "2.0"))
        assertFalse(isNewerVersion("2.0", "2.0"))
        assertTrue(isNewerVersion("2.0.1", "2.0"))
        assertFalse(isNewerVersion("2.0", "2.0.1"))
    }

    @Test
    fun `release check accepts only a newer installer with github digest`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    [{
                      "draft": false,
                      "tag_name": "v0.2.0-alpha.5",
                      "name": "Preview 0.2 alpha 5",
                      "assets": [{
                        "name": "IPTVBURO-0.2.6.msi",
                        "browser_download_url": "https://github.com/serafinnike-create/IPTVBURO/releases/download/v0.2.0-alpha.5/IPTVBURO-0.2.6.msi",
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
                    currentVersion = "0.2.0-alpha.4",
                )
            val result = updater.check()
            assertIs<UpdateCheckResult.Available>(result)
            assertTrue(result.release.version == "0.2.0-alpha.5")
            val request = server.takeRequest()
            assertEquals("no-cache", request.getHeader("Cache-Control"))
            assertEquals("no-cache", request.getHeader("Pragma"))
        }
    }

    @Test
    fun `the version-stamped installer wins when a release carries several`() = runBlocking {
        // Real releases ship both the versioned artefact and a legacy name left by the packaging
        // task. Taking the first match meant installing whichever asset GitHub happened to list
        // first, which is not necessarily the build the tag advertises.
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    [{
                      "draft": false,
                      "tag_name": "v0.2.0-alpha.9",
                      "name": "Preview",
                      "assets": [
                        {
                          "name": "IPTVBURO-0.2.6.msi",
                          "browser_download_url": "https://github.com/serafinnike-create/IPTVBURO/releases/download/v0.2.0-alpha.9/IPTVBURO-0.2.6.msi",
                          "size": 10,
                          "digest": "sha256:${"a".repeat(64)}"
                        },
                        {
                          "name": "IPTV-BURO-v0.2.0-alpha.9-windows-x64.msi",
                          "browser_download_url": "https://github.com/serafinnike-create/IPTVBURO/releases/download/v0.2.0-alpha.9/IPTV-BURO-v0.2.0-alpha.9-windows-x64.msi",
                          "size": 20,
                          "digest": "sha256:${"b".repeat(64)}"
                        }
                      ]
                    }]
                    """.trimIndent(),
                ),
            )
            val updater =
                GitHubReleaseUpdater(
                    releasesUrl = server.url("/releases").toString(),
                    currentVersion = "0.2.0-alpha.5",
                )

            val result = updater.check()

            val available = assertIs<UpdateCheckResult.Available>(result)
            assertEquals(
                "IPTV-BURO-v0.2.0-alpha.9-windows-x64.msi",
                available.release.assetName,
            )
        }
    }

    @Test
    fun `an installer from another github repository is refused`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    [{
                      "draft": false,
                      "tag_name": "v99.0.0",
                      "assets": [{
                        "name": "IPTV-BURO-v99.0.0-windows-x64.msi",
                        "browser_download_url": "https://github.com/example/other/releases/download/v99.0.0/IPTV-BURO-v99.0.0-windows-x64.msi",
                        "size": 1234,
                        "digest": "sha256:${"c".repeat(64)}"
                      }]
                    }]
                    """.trimIndent(),
                ),
            )

            val result =
                GitHubReleaseUpdater(
                    releasesUrl = server.url("/releases").toString(),
                    currentVersion = "2.0.0-alpha.1",
                ).check()

            assertIs<UpdateCheckResult.Failed>(result)
        }
    }
}
