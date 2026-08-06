package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.metadata.TmdbClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises shelf loading against the **real shapes TMDb returns**, rather than the tidied fixtures
 * the other tests use.
 *
 * The shelves came back empty in the running app while every existing test passed, which means the
 * fixtures were agreeing with the code instead of with the API. These cases are transcribed from
 * live responses.
 */
class ShelfLoadDiagnosticTest {
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

    private fun catalogue(maxServices: Int = 3) =
        TmdbStreamingCatalogue(
            client =
                TmdbClient(
                    apiKey = "test-key",
                    client = OkHttpClient(),
                    baseUrl = server.url("/3/").toString().toHttpUrl(),
                    imageBaseUrl = "https://images.test",
                ),
            region = "BR",
            maxServices = maxServices,
        )

    private fun json(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    /**
     * The live directory response, abbreviated but structurally exact.
     *
     * Note `display_priority` is 0 for the first entry. An implementation using `?: Int.MAX_VALUE`
     * on a *missing* key is fine, but one treating 0 as absent would reorder everything.
     */
    @Test
    fun `the real directory shape yields shelves`() {
        server.enqueue(
            json(
                """
                {"results":[
                  {"display_priorities":{},"display_priority":0,"logo_path":"/x.jpg","provider_name":"Netflix","provider_id":8},
                  {"display_priorities":{},"display_priority":1,"logo_path":"/y.jpg","provider_name":"Amazon Prime Video","provider_id":119},
                  {"display_priorities":{},"display_priority":2,"logo_path":"/z.jpg","provider_name":"Google Play Movies","provider_id":3}]}
                """.trimIndent(),
            ),
        )
        // The live discover response carries far more fields than the fixtures did.
        repeat(3) {
            server.enqueue(
                json(
                    """
                    {"page":1,"results":[
                      {"adult":false,"backdrop_path":"/b.jpg","genre_ids":[28,12],"id":1234,
                       "original_language":"en","original_title":"Some Film","overview":"Sinopse.",
                       "popularity":123.4,"poster_path":"/p.jpg","release_date":"2026-07-20",
                       "title":"O Cobrador de Dividas","video":false,"vote_average":6.7,"vote_count":42}],
                     "total_pages":244,"total_results":4871}
                    """.trimIndent(),
                ),
            )
        }

        val shelves = catalogue().shelves()

        assertEquals(3, shelves.size, "the live directory shape produced no shelves")
        assertEquals("Netflix", shelves.first().provider.displayName)
        assertEquals("O Cobrador de Dividas", shelves.first().titles.single().title)
        assertEquals("https://images.test/w342/p.jpg", shelves.first().titles.single().posterUrl)
    }

    /**
     * The directory is one request; each shelf is one more. If the code ever asked for the
     * directory per service, twelve shelves would be twenty-four calls.
     */
    @Test
    fun `loading asks for the directory once and each shelf once`() {
        server.enqueue(
            json(
                """{"results":[
                  {"provider_id":8,"provider_name":"Netflix","display_priority":0},
                  {"provider_id":119,"provider_name":"Prime Video","display_priority":1}]}""",
            ),
        )
        repeat(2) { server.enqueue(json("""{"results":[{"id":1,"title":"Filme","poster_path":"/p.jpg"}]}""")) }

        catalogue(maxServices = 2).shelves()

        assertEquals(3, server.requestCount, "expected 1 directory + 2 shelves")

        val paths = (1..3).map { server.takeRequest().requestUrl!!.encodedPath }
        assertTrue(paths.first().endsWith("/watch/providers/movie"), "first call was ${paths.first()}")
        assertTrue(paths.drop(1).all { it.endsWith("/discover/movie") }, "shelf calls were ${paths.drop(1)}")
    }

    /**
     * The failure the user actually saw: shelves empty with everything else working.
     *
     * If the directory succeeds but every discover call fails, the result is no shelves — correct
     * behaviour, but indistinguishable on screen from "nothing configured". This pins that the
     * directory is not silently consuming the shelf responses.
     */
    @Test
    fun `a working directory with failing shelves yields nothing rather than a partial mess`() {
        server.enqueue(json("""{"results":[{"provider_id":8,"provider_name":"Netflix","display_priority":0}]}"""))
        server.enqueue(MockResponse().setResponseCode(401))

        assertTrue(catalogue().shelves().isEmpty())
    }
}
