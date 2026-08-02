package com.lucasserafin94.iptvburo.playlist

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in smoke test for a user-authorized local playlist.
 *
 * The source path stays in an environment variable. Its path, URLs and metadata are never printed
 * or copied into test reports, and CI skips this test because the variable is intentionally absent.
 */
class M3uPrivateCompatibilityTest {
    @Test
    fun `authorized private playlist satisfies the production parser contract`() {
        val rawPath = System.getenv(ENV_PLAYLIST_PATH)
        assumeTrue(
            "Private M3U environment is not configured.",
            !rawPath.isNullOrBlank(),
        )
        val path = Path.of(requireNotNull(rawPath))
        assumeTrue(
            "Private M3U file is unavailable.",
            Files.isRegularFile(path),
        )

        var emittedItems = 0
        val summary =
            Files.newInputStream(path).use { input ->
                M3uParser().parseStreaming(input) {
                    emittedItems += 1
                }
            }

        assertTrue(emittedItems > 0)
        assertTrue(summary.channelCount == emittedItems)
    }

    private companion object {
        const val ENV_PLAYLIST_PATH = "IPTV_BURO_PRIVATE_M3U_FILE"
    }
}
