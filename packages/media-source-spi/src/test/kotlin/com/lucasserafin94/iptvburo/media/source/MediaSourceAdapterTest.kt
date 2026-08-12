package com.lucasserafin94.iptvburo.media.source

import com.lucasserafin94.iptvburo.domain.model.MediaCapabilities
import com.lucasserafin94.iptvburo.domain.model.MediaIdentity
import com.lucasserafin94.iptvburo.domain.model.ContentKind
import com.lucasserafin94.iptvburo.domain.model.MediaKind
import com.lucasserafin94.iptvburo.domain.model.SourceCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class MediaSourceAdapterTest {
    @Test
    fun `compatibility adapter emits synthetic events without network`() = runTest {
        val config = SourceConfig("source", MediaSourceType.M3U, "https://user:secret@example.test/list")
        val events = FakeAdapter.scan(config).toList()

        assertEquals(MediaImportEvent.Started, events.first())
        assertEquals(MediaImportEvent.Completed(1), events.last())
        assertTrue(FakeAdapter.validate(config).valid)
        assertEquals(MediaKind.MOVIE, FakeAdapter.resolve(PlaybackLocator("source", "movie:1")).kind)
    }

    @Test
    fun `diagnostics redact configuration locator uri headers and references`() = runTest {
        val config = SourceConfig(
            "secret-source-id",
            MediaSourceType.XTREAM,
            "https://user:secret@example.test/player_api.php?password=secret",
            mapOf("password" to "secret"),
        )
        val locator = PlaybackLocator("secret-source-id", "movie:secret-reference")
        val resolved = FakeAdapter.resolve(locator)
        val diagnostics = "$config $locator $resolved"

        assertFalse("secret" in diagnostics)
        assertFalse("example.test" in diagnostics)
        assertFalse("Bearer" in diagnostics)
        assertTrue("optionCount=1" in diagnostics)
        assertTrue("headerCount=1" in diagnostics)
    }

    @Test
    fun `playback locator refuses a resolved URL`() {
        assertFailsWith<IllegalArgumentException> {
            PlaybackLocator("source", "https://user:secret@example.test/movie")
        }
    }

    @Test
    fun `import contract has an explicit cancellation event`() {
        assertEquals(MediaImportEvent.Cancelled, MediaImportEvent.Cancelled)
    }

    @Test
    fun `warning and failure accept codes but reject provider text`() {
        assertEquals("network.timeout", MediaImportEvent.Warning("network.timeout").code)
        assertFailsWith<IllegalArgumentException> {
            MediaImportEvent.Failed("https://user:secret@example.test/error", retryable = false)
        }
        assertFailsWith<IllegalArgumentException> {
            SourceValidation(valid = false, warningCodes = listOf("password=secret"))
        }
    }

    private object FakeAdapter : MediaSourceAdapter {
        override val sourceType: MediaSourceType = MediaSourceType.M3U

        override suspend fun validate(config: SourceConfig): SourceValidation =
            SourceValidation(
                valid = true,
                capabilities = SourceCapabilities(
                    supportedKinds = setOf(MediaKind.MOVIE),
                    media = MediaCapabilities(playable = true, seekable = true),
                ),
            )

        override fun scan(config: SourceConfig) =
            flowOf(
                MediaImportEvent.Started,
                MediaImportEvent.ItemDiscovered(
                    MediaDescriptor(
                        stableIdentity = MediaIdentity.video(ContentKind.MOVIE, "Synthetic", 2026),
                        kind = MediaKind.MOVIE,
                        title = "Synthetic",
                        locator = PlaybackLocator(config.sourceId, "movie:1"),
                    ),
                ),
                MediaImportEvent.Completed(1),
            )

        override suspend fun resolve(locator: PlaybackLocator): ResolvedMedia =
            ResolvedMedia(
                kind = MediaKind.MOVIE,
                uri = "https://user:secret@example.test/movie/1",
                headers = mapOf("Authorization" to "Bearer secret"),
                capabilities = MediaCapabilities(playable = true, seekable = true),
            )

        override suspend fun capabilities(config: SourceConfig): SourceCapabilities =
            validate(config).capabilities
    }
}
