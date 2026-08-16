package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.domain.model.SourceType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A playlist on a server, imported end to end.
 *
 * Separate from the reader's own tests because this is the join: the remote path has to arrive at
 * the same catalogue the local path produces, through the same parser. A remote import that quietly
 * built a different shape of catalogue would show up as odd behaviour much later, in whichever
 * screen happened to read the field that differed.
 */
class RemotePlaylistImportTest {
    private lateinit var server: MockWebServer

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private val playlist =
        """
        #EXTM3U
        #EXTINF:-1 group-title="Notícias",Canal Um
        http://example.test/one.ts
        #EXTINF:-1 group-title="Notícias",Canal Dois
        http://example.test/two.ts
        #EXTINF:-1 group-title="Esportes",Canal Três
        http://example.test/three.ts
        """.trimIndent()

    @Test
    fun `a remote playlist becomes a catalogue like any other`() {
        server.enqueue(MockResponse().setBody(playlist))
        val repository = InMemoryCatalogRepository()

        val catalog =
            repository.importRemote(
                source = WebDavPlaylistReader(url = server.url("/list.m3u").toString()),
                sourceLabel = "nas.example.test",
            )

        assertEquals(3, catalog.channels.size)
        assertEquals(2, catalog.categories.size, "the two group-titles become two categories")
        assertEquals("Canal Um", catalog.channels.first().name)
    }

    /** The type is what tells the rest of the app this list came from a server rather than a file. */
    @Test
    fun `the source is recorded as remote`() {
        server.enqueue(MockResponse().setBody(playlist))
        val repository = InMemoryCatalogRepository()

        val catalog =
            repository.importRemote(
                source = WebDavPlaylistReader(url = server.url("/list.m3u").toString()),
                sourceLabel = "nas.example.test",
            )

        assertEquals(SourceType.REMOTE_M3U, catalog.source.type)
    }

    /**
     * The sidebar shows the source name, so it must be the host rather than the address: a URL can
     * carry a password in its userinfo, and this string is on screen for the whole session.
     */
    @Test
    fun `the catalogue is named without the address`() {
        server.enqueue(MockResponse().setBody(playlist))
        val repository = InMemoryCatalogRepository()
        val reader =
            WebDavPlaylistReader(
                url = server.url("/list.m3u").toString(),
                username = "maria",
                password = "hunter2",
            )

        val catalog = repository.importRemote(source = reader, sourceLabel = reader.displayName)

        assertFalse(catalog.source.name.contains("hunter2"), "the password must not name the source")
        assertFalse(catalog.source.name.contains("/list.m3u"), "the path must not name it either")
        assertTrue(catalog.source.name.isNotBlank())
    }

    /** A server that rejects the credentials must not produce an empty catalogue that looks valid. */
    @Test
    fun `a rejected fetch imports nothing at all`() {
        server.enqueue(MockResponse().setResponseCode(401))
        val repository = InMemoryCatalogRepository()

        runCatching {
            repository.importRemote(
                source = WebDavPlaylistReader(url = server.url("/list.m3u").toString()),
                sourceLabel = "nas.example.test",
            )
        }

        assertEquals(0, repository.sourceCount(), "a failed fetch must leave no source behind")
    }
}
