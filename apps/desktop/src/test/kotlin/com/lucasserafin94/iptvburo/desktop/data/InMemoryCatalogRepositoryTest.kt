package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.domain.model.SourceType
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryCatalogRepositoryTest {
    @Test
    fun `imports categories and channels without exposing URLs through toString`() {
        val playlist =
            Files.createTempFile("iptv-buro-desktop-", ".m3u8").apply {
                writeText(
                    """
                    #EXTM3U
                    #EXTINF:-1 group-title="Notícias",Canal Um
                    https://example.invalid/live/user-secret/password-secret/index.m3u8
                    #EXTINF:-1 group-title="Esportes",Canal Dois
                    https://example.invalid/sports/index.m3u8|User-Agent=IPTV%20BURO
                    """.trimIndent(),
                )
            }
        val repository = InMemoryCatalogRepository()

        try {
            val catalog = repository.importLocal(playlist, "Lista local 1")

            assertEquals(SourceType.LOCAL_M3U, catalog.source.type)
            assertEquals(2, catalog.source.channelCount)
            assertEquals(listOf("Notícias", "Esportes"), catalog.categories.map { it.name })
            assertTrue(catalog.channels.first().toString().contains("streamUri=<redacted>"))
            assertFalse(catalog.channels.first().toString().contains("user-secret"))
            assertEquals(setOf("User-Agent"), catalog.channels.last().requestHeaders.keys)
            assertEquals(1, repository.sourceCount())
        } finally {
            repository.clear()
            Files.deleteIfExists(playlist)
        }
    }

    @Test
    fun `forget and clear discard session catalogs`() {
        val playlist =
            Files.createTempFile("iptv-buro-desktop-", ".m3u").apply {
                writeText(
                    """
                    #EXTM3U
                    #EXTINF:-1,Canal
                    https://example.invalid/live.m3u8
                    """.trimIndent(),
                )
            }
        val repository = InMemoryCatalogRepository()

        try {
            val first = repository.importLocal(playlist, "Lista local 1")
            repository.importLocal(playlist, "Lista local 2")
            assertEquals(2, repository.sourceCount())

            repository.forget(first.source.id)
            assertEquals(1, repository.sourceCount())

            repository.clear()
            assertEquals(0, repository.sourceCount())
        } finally {
            repository.clear()
            Files.deleteIfExists(playlist)
        }
    }
}
