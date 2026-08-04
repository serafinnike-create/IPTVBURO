package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.desktop.model.XtreamPlaybackTarget
import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import com.lucasserafin94.iptvburo.xtream.XtreamClient
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class SessionXtreamRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: SessionXtreamRepository

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        val httpClient =
            OkHttpClient.Builder()
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        repository =
            SessionXtreamRepository(
                client =
                    XtreamClient(
                        httpClient = httpClient,
                    ),
            )
    }

    @AfterTest
    fun tearDown() {
        repository.clear()
        server.shutdown()
    }

    @Test
    fun `session loads live initially and pages a large synthetic catalog`() {
        enqueueAuthentication()
        enqueueCategories("live")
        enqueueCategories("movie")
        enqueueCategories("series")
        server.enqueue(jsonResponse(syntheticLiveCatalog(size = 205)))

        val summary = repository.authenticateAndLoadInitial(loginInput())
        val firstPage =
            repository.page(
                contentType = XtreamContentType.LIVE,
                categoryId = null,
                query = "",
                requestedPage = 0,
            )
        val finalPage =
            repository.page(
                contentType = XtreamContentType.LIVE,
                categoryId = null,
                query = "",
                requestedPage = 2,
            )

        assertEquals(setOf(XtreamContentType.LIVE), summary.loadedContentTypes)
        assertEquals(205, summary.loadedItemCount)
        assertEquals(80, firstPage.items.size)
        assertEquals(3, firstPage.pageCount)
        assertEquals(45, finalPage.items.size)
        assertEquals(1, repository.categories(XtreamContentType.LIVE).size)
    }

    /**
     * "Atualizar listas" drops the cached catalogues so they are re-fetched. Categories are loaded
     * during connect, not by loadCatalog, so clearing them too left the rail showing only "Todas"
     * with nothing able to bring them back.
     */
    @Test
    fun `refreshing the catalogue keeps the categories`() {
        enqueueAuthentication()
        enqueueCategories("live")
        enqueueCategories("movie")
        enqueueCategories("series")
        server.enqueue(jsonResponse(syntheticLiveCatalog(size = 10)))
        repository.authenticateAndLoadInitial(loginInput())
        assertEquals(1, repository.categories(XtreamContentType.LIVE).size)

        repository.clearCatalogCache()

        assertEquals(
            1,
            repository.categories(XtreamContentType.LIVE).size,
            "categories must survive a catalogue refresh",
        )
    }

    /** The point of the refresh: the items themselves are fetched again rather than answered from memory. */
    @Test
    fun `refreshing the catalogue re-fetches the items`() {
        enqueueAuthentication()
        enqueueCategories("live")
        enqueueCategories("movie")
        enqueueCategories("series")
        server.enqueue(jsonResponse(syntheticLiveCatalog(size = 10)))
        repository.authenticateAndLoadInitial(loginInput())
        val requestsAfterConnect = server.requestCount

        repository.clearCatalogCache()
        server.enqueue(jsonResponse(syntheticLiveCatalog(size = 12)))
        val summary = repository.loadCatalog(XtreamContentType.LIVE)

        assertEquals(12, summary.loadedItemCount, "the new item count must be visible")
        assertEquals(
            requestsAfterConnect + 1,
            server.requestCount,
            "the provider must actually be asked again",
        )
    }

    /**
     * The Home effect loads MOVIE and SERIES while a user clicking a content tab loads the same
     * type. The cache check and the store were separate critical sections, so both callers missed
     * the cache together and each streamed the whole catalogue - two full downloads of up to
     * 30,000 items, and the loser's copy then replaced the winner's under the same key.
     */
    @Test
    fun `concurrent loads of one content type fetch it only once`() {
        enqueueAuthentication()
        enqueueCategories("live")
        enqueueCategories("movie")
        enqueueCategories("series")
        server.enqueue(jsonResponse(syntheticLiveCatalog(size = 5)))
        repository.authenticateAndLoadInitial(loginInput())
        val requestsAfterConnect = server.requestCount
        // Only one movie catalogue is ever enqueued: a second fetch would block on an empty queue
        // and be visible as an extra request below.
        server.enqueue(jsonResponse(syntheticMovieCatalog(size = 40)))

        val threads =
            (1..4).map {
                Thread { repository.loadCatalog(XtreamContentType.MOVIE) }
            }
        threads.forEach(Thread::start)
        threads.forEach { it.join(30_000) }

        assertEquals(
            requestsAfterConnect + 1,
            server.requestCount,
            "the movie catalogue must be streamed exactly once",
        )
        assertEquals(40, repository.page(XtreamContentType.MOVIE, null, "", 0).totalMatches)
    }

    @Test
    fun `playback URL is built only through the explicit confirmed method and session clears`() {
        enqueueAuthentication(allowedFormatsJson = """["m3u8"]""")
        enqueueCategories("live")
        enqueueCategories("movie")
        enqueueCategories("series")
        server.enqueue(jsonResponse(syntheticLiveCatalog(size = 1)))
        repository.authenticateAndLoadInitial(loginInput())
        val item = repository.page(XtreamContentType.LIVE, null, "", 0).items.single()

        val target =
            XtreamPlaybackTarget.CatalogItem(
                providerId = item.providerId,
                contentType = item.contentType,
                containerExtension = null,
                contentKey = item.contentIdentity().key,
            )
        val playbackUri = repository.buildConfirmedPlaybackUri(target)

        assertTrue(playbackUri.path.contains("/live/"))
        assertTrue(playbackUri.path.endsWith("/1.m3u8"))
        assertFalse(target.toString().contains("synthetic-password"))

        repository.clear()
        assertNull(repository.summary())
    }

    @Test
    fun `kids page excludes explicit adult categories and titles`() {
        enqueueAuthentication()
        server.enqueue(
            jsonResponse(
                """
                [
                  {"category_id":"family","category_name":"Família"},
                  {"category_id":"adult","category_name":"Adult 18+"}
                ]
                """.trimIndent(),
            ),
        )
        enqueueCategories("movie")
        enqueueCategories("series")
        server.enqueue(
            jsonResponse(
                """
                [
                  {"stream_id":"1","name":"Aventura legal","category_id":"family"},
                  {"stream_id":"2","name":"Canal comum","category_id":"adult"},
                  {"stream_id":"3","name":"XXX privado","category_id":"family"}
                ]
                """.trimIndent(),
            ),
        )
        repository.authenticateAndLoadInitial(loginInput())

        val unrestricted = repository.page(XtreamContentType.LIVE, null, "", 0)
        val kids = repository.page(XtreamContentType.LIVE, null, "", 0, kidsMode = true)

        assertEquals(3, unrestricted.totalMatches)
        assertEquals(listOf("Aventura legal"), kids.items.map { it.name })
        assertEquals(1, kids.totalMatches)
    }

    private fun enqueueAuthentication(
        allowedFormatsJson: String = """["ts", "m3u8"]""",
    ) {
        server.enqueue(
            jsonResponse(
                """
                {
                  "user_info": {
                    "auth": "1",
                    "status": "Active",
                    "allowed_output_formats": $allowedFormatsJson
                  }
                }
                """.trimIndent(),
            ),
        )
    }

    private fun enqueueCategories(label: String) {
        server.enqueue(
            jsonResponse(
                """
                [
                  {
                    "category_id": "category-$label",
                    "category_name": "Synthetic $label"
                  }
                ]
                """.trimIndent(),
            ),
        )
    }

    private fun syntheticLiveCatalog(size: Int): String =
        (1..size).joinToString(
            prefix = "[",
            postfix = "]",
            separator = ",",
        ) { index ->
            """
            {
              "stream_id": "$index",
              "name": "Synthetic channel $index",
              "category_id": "category-live",
              "container_extension": "ts"
            }
            """.trimIndent()
        }

    private fun syntheticMovieCatalog(size: Int): String =
        (1..size).joinToString(
            prefix = "[",
            postfix = "]",
            separator = ",",
        ) { index ->
            """
            {
              "stream_id": "$index",
              "name": "Synthetic movie $index",
              "category_id": "category-movie",
              "container_extension": "mp4"
            }
            """.trimIndent()
        }

    private fun loginInput(): XtreamLoginInput =
        XtreamLoginInput(
            server = server.url("/get.php").toString().toCharArray(),
            username = "synthetic-user".toCharArray(),
            password = "synthetic-password".toCharArray(),
        )

    private fun jsonResponse(body: String): MockResponse =
        MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(body)
}
