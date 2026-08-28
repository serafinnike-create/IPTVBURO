package com.lucasserafin94.iptvburo.metadata

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The shelf of related titles, which came back empty on a live catalogue.
 *
 * Reported as missing from series, but the app's own log showed it empty for a film too — with a
 * valid TMDb id, against a service that returns twenty results for that exact request. So these
 * exercise the client's own path rather than the endpoint: what the requests carry, and what it
 * makes of an answer.
 */
class SimilarTitlesShelfTest {
    private lateinit var server: MockWebServer
    private val paths = mutableListOf<String>()

    /** A v4 Read Access Token: three dot-separated parts, starting eyJ. */
    private val v4Token = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJzaW50ZXRpY28ifQ.c2ludGV0aWNv"

    @Before
    fun setUp() {
        paths.clear()
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    paths += request.path.orEmpty()
                    // TMDb rejects a v4 token sent as the api_key parameter, which is what the
                    // live service does and what this reproduces.
                    if (request.path.orEmpty().contains("api_key=eyJ")) {
                        return MockResponse()
                            .setResponseCode(401)
                            .setBody("""{"status_code":7,"status_message":"Invalid API key"}""")
                    }
                    return MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {"results":[
                              {"id":11,"title":"Duna","release_date":"2021-09-15","poster_path":"/a.jpg"},
                              {"id":12,"title":"Matrix","release_date":"1999-03-31","poster_path":"/b.jpg"}
                            ]}
                            """.trimIndent(),
                        )
                }
            }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(key: String) =
        TmdbClient(apiKey = key, baseUrl = server.url("/").toString().toHttpUrl())

    /**
     * A v4 token must travel as a header and never as the api_key parameter.
     *
     * TMDb answers 401 on the bad parameter even when the header alone would have been accepted,
     * so leaving it alongside turns every lookup into nothing.
     */
    @Test
    fun `a v4 token never reaches the query string`() {
        client(v4Token).similarTitles(tmdbId = 2096, isSeries = false)

        assertTrue("nenhum pedido foi feito", paths.isNotEmpty())
        paths.forEach { path ->
            assertTrue("a chave v4 foi enviada como parametro: $path", !path.contains("api_key=eyJ"))
        }
    }

    /** And with the token in the header, the shelf actually fills. */
    @Test
    fun `a v4 token produces a shelf`() {
        val titles = client(v4Token).similarTitles(tmdbId = 2096, isSeries = false)

        assertEquals(2, titles.size)
        assertEquals("Duna", titles.first().title)
    }

    /** A v3 key is the other form, and still belongs in the query string. */
    @Test
    fun `a v3 key still travels as a parameter`() {
        val titles = client("0123456789abcdef0123456789abcdef").similarTitles(tmdbId = 2096)

        assertEquals(2, titles.size)
        assertTrue(
            "a chave v3 devia ir no parametro",
            paths.any { it.contains("api_key=0123456789abcdef") },
        )
    }

    /** Series use TMDb's television catalogue, which is a different path entirely. */
    @Test
    fun `a series asks the television catalogue`() {
        client(v4Token).similarTitles(tmdbId = 2096, isSeries = true)

        assertTrue("uma serie foi pedida ao catalogo de filmes: $paths", paths.all { it.contains("/tv/") })
    }

    /** A film asks the film catalogue, and its collection as well. */
    @Test
    fun `a film asks the film catalogue`() {
        client(v4Token).similarTitles(tmdbId = 2096, isSeries = false)

        assertTrue("um filme foi pedido ao catalogo de series: $paths", paths.none { it.contains("/tv/") })
    }
}
