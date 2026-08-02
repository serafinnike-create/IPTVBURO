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
            )
        val playbackUri = repository.buildConfirmedPlaybackUri(target)

        assertTrue(playbackUri.path.contains("/live/"))
        assertTrue(playbackUri.path.endsWith("/1.m3u8"))
        assertFalse(target.toString().contains("synthetic-password"))

        repository.clear()
        assertNull(repository.summary())
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
