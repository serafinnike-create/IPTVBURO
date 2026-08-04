package com.lucasserafin94.iptvburo.desktop

import com.lucasserafin94.iptvburo.desktop.data.SessionXtreamRepository
import com.lucasserafin94.iptvburo.xtream.XtreamClient
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/**
 * The home's film and series shelves, built from the catalogue.
 *
 * They kept coming back empty on a real machine while every unit test passed, so this exercises the
 * path the app actually takes on launch: connect, then page the catalogue for the shelves — rather
 * than testing the paging helper in isolation, which was already known to work.
 */
class DailyHomeShelvesTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: SessionXtreamRepository

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            SessionXtreamRepository(
                client = XtreamClient(httpClient = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()),
            )
    }

    @AfterTest
    fun tearDown() {
        repository.clear()
        server.shutdown()
    }

    /**
     * The exact sequence startup performs. If the movie catalogue is reachable through the
     * repository right after loadCatalog, the shelves have data to draw; when this failed, the home
     * had none.
     */
    @Test
    fun `films are pageable immediately after the catalogue loads`() {
        enqueueAuthentication()
        enqueueCategories()
        enqueueCategories()
        enqueueCategories()
        server.enqueue(jsonResponse(liveCatalog(2)))
        repository.authenticateAndLoadInitial(loginInput())

        server.enqueue(jsonResponse(movieCatalog(30)))
        repository.loadCatalog(XtreamContentType.MOVIE)

        val page = repository.page(XtreamContentType.MOVIE, null, "", 0, pageSize = 18)
        assertTrue(page.items.isNotEmpty(), "the home shelf pages this and got nothing")
    }

    /** The summary must report the type as loaded, which is what stops a second fetch. */
    @Test
    fun `the summary reports the loaded types the home checks`() {
        enqueueAuthentication()
        enqueueCategories()
        enqueueCategories()
        enqueueCategories()
        server.enqueue(jsonResponse(liveCatalog(2)))
        repository.authenticateAndLoadInitial(loginInput())

        server.enqueue(jsonResponse(movieCatalog(10)))
        repository.loadCatalog(XtreamContentType.MOVIE)

        val loaded = repository.summary()?.loadedContentTypes.orEmpty()
        assertTrue(XtreamContentType.MOVIE in loaded, "was $loaded")
    }

    private fun enqueueAuthentication() {
        server.enqueue(
            jsonResponse(
                """
                {"user_info":{"auth":1,"status":"Active","max_connections":"1",
                "allowed_output_formats":["ts"]},
                "server_info":{"url":"${server.hostName}","port":"${server.port}"}}
                """.trimIndent(),
            ),
        )
    }

    private fun enqueueCategories() {
        server.enqueue(jsonResponse("""[{"category_id":"1","category_name":"Geral"}]"""))
    }

    private fun liveCatalog(size: Int): String =
        (1..size).joinToString(",", "[", "]") { index ->
            """{"stream_id":$index,"name":"Canal $index","category_id":"1"}"""
        }

    private fun movieCatalog(size: Int): String =
        (1..size).joinToString(",", "[", "]") { index ->
            """{"stream_id":${1000 + index},"name":"Filme $index","category_id":"1",
            "container_extension":"mp4","rating":"4.5","year":"2026"}"""
        }

    private fun loginInput(): XtreamLoginInput =
        XtreamLoginInput(
            server.url("/").toString().toCharArray(),
            "synthetic-user".toCharArray(),
            "synthetic-password".toCharArray(),
        )

    private fun jsonResponse(body: String): MockResponse =
        MockResponse().setBody(body).setHeader("Content-Type", "application/json")
}
