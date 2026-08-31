package com.lucasserafin94.iptvburo.metadata

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Finding a trailer for something the banner is showing.
 *
 * The home banner played no trailer at all, ever. Instrumenting the real app showed the lookup
 * running and returning nothing every time — and the reason was that it searched only TMDb's film
 * catalogue, while the banner shows series as often as films. TMDb keeps the two apart, so a series
 * searched as a film finds nothing rather than being redirected.
 */
class TrailerLookupTest {
    private lateinit var server: MockWebServer
    private val paths = mutableListOf<String>()

    @Before
    fun setUp() {
        paths.clear()
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    paths += path
                    val body =
                        when {
                            // The client asks YouTube whether the video is still public before
                            // handing it back. Answered here so these tests stay off the network:
                            // what they are about is which endpoint is asked, not availability.
                            path.startsWith("/oembed") -> """{"title":"ok"}"""
                            path.startsWith("/search/") ->
                                """{"results":[{"id":77,"title":"Encontrado"}]}"""
                            path.contains("/videos") ->
                                """{"results":[
                                     {"site":"YouTube","type":"Trailer","key":"abc123XYZ_-"}
                                   ]}"""
                            else -> "{}"
                        }
                    return MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(body)
                }
            }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client() =
        TmdbClient(
            apiKey = "0123456789abcdef0123456789abcdef",
            baseUrl = server.url("/").toString().toHttpUrl(),
            youtubeOEmbedUrl = server.url("/oembed").toString(),
        )

    /**
     * A series is searched in the television catalogue.
     *
     * This is the defect the banner had: every series it showed asked TMDb's film catalogue, which
     * has no such title, so the answer was always nothing.
     */
    @Test
    fun `a series is searched as a series`() {
        client().findTrailer(title = "Raul Seixas Eu Sou", year = 2025, isSeries = true)

        assertTrue(
            "uma serie foi procurada no catalogo de filmes: $paths",
            paths.any { it.startsWith("/search/tv") },
        )
        assertTrue(
            "os videos da serie foram pedidos ao caminho dos filmes: $paths",
            paths.none { it.startsWith("/movie/") },
        )
    }

    /** And its videos come from the television path, which is where TMDb keeps them. */
    @Test
    fun `a series takes its videos from the television path`() {
        val found = client().findTrailer(title = "Uma Serie", year = null, isSeries = true)

        assertEquals("abc123XYZ_-", found)
        assertTrue(
            "os videos nao vieram do caminho de televisao: $paths",
            paths.any { it.startsWith("/tv/77/videos") },
        )
    }

    /** A film still goes to the film catalogue, which is what it always did. */
    @Test
    fun `a film is searched as a film`() {
        val found = client().findTrailer(title = "Um Filme", year = 1973)

        assertEquals("abc123XYZ_-", found)
        assertTrue(
            "um filme foi procurado no catalogo de series: $paths",
            paths.any { it.startsWith("/search/movie") },
        )
        assertTrue(
            "os videos do filme vieram do caminho errado: $paths",
            paths.any { it.startsWith("/movie/77/videos") },
        )
    }

    /**
     * A series filters by first air date, not by release year.
     *
     * Sending `year` on a television search filters every result away rather than being ignored,
     * which would have turned this fix into a different way of finding nothing.
     */
    @Test
    fun `a series filters by its own year parameter`() {
        client().findTrailer(title = "Uma Serie", year = 2025, isSeries = true)

        val search = paths.first { it.startsWith("/search/tv") }
        assertTrue("a serie foi filtrada por year: $search", !search.contains("year=2025&"))
        assertTrue(
            "a serie nao foi filtrada pelo ano de estreia: $search",
            search.contains("first_air_date_year=2025"),
        )
    }

    /**
     * A video YouTube will not serve is skipped, and the next one is offered instead.
     *
     * TMDb keeps entries for videos that have since been made private or deleted. An embed for one
     * of those does not fail — it loads a card reading "This video is private", which on the home
     * banner is exactly the error that must never appear. Reported with a screenshot of it.
     */
    @Test
    fun `a private video is skipped for one that plays`() {
        val privado = "privado1234"
        val bom = "abc123XYZ_-"
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    paths += path
                    return when {
                        // YouTube refuses the first one, as it does for a private video.
                        path.startsWith("/oembed") && path.contains(privado) ->
                            MockResponse().setResponseCode(401)
                        path.startsWith("/oembed") ->
                            MockResponse().setBody("""{"title":"ok"}""")
                        path.startsWith("/search/") ->
                            MockResponse().setBody("""{"results":[{"id":77}]}""")
                        else ->
                            MockResponse().setBody(
                                """{"results":[
                                     {"site":"YouTube","type":"Trailer","key":"$privado"},
                                     {"site":"YouTube","type":"Trailer","key":"$bom"}
                                   ]}""",
                            )
                    }.setHeader("Content-Type", "application/json")
                }
            }

        assertEquals(bom, client().findTrailer(title = "Um Filme", year = null))
    }

    /** And when none of them plays, the answer is nothing rather than a broken embed. */
    @Test
    fun `no playable video means no trailer`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    paths += path
                    return when {
                        path.startsWith("/oembed") -> MockResponse().setResponseCode(401)
                        path.startsWith("/search/") ->
                            MockResponse().setBody("""{"results":[{"id":77}]}""")
                        else ->
                            MockResponse().setBody(
                                """{"results":[{"site":"YouTube","type":"Trailer","key":"privado1234"}]}""",
                            )
                    }.setHeader("Content-Type", "application/json")
                }
            }

        assertEquals(null, client().findTrailer(title = "Um Filme", year = null))
    }

    /**
     * A series' plot is searched in the television catalogue, filtered by its own year parameter.
     *
     * Providers leave the description empty constantly, and the banner then falls back to a fixed
     * line about the daily selection — which reads as a description of the title and describes
     * nothing. Reported with a 2024 series showing exactly that. The search shape matters as much
     * as the lookup: sending a film's `year` to a television search filters every result away
     * rather than being ignored, so a wrong shape here is a different way of finding nothing.
     */
    @Test
    fun `a series overview is searched as a series`() {
        client().findOverview(title = "Uma Serie", year = 2024, isSeries = true)

        val search = paths.first { it.startsWith("/search/") }
        assertTrue("a sinopse da serie foi procurada nos filmes: $paths", "/search/tv" in search)
        assertTrue(
            "a serie foi filtrada pelo parametro errado: $search",
            "first_air_date_year=2024" in search,
        )
    }

    /** And a film's plot goes to the film catalogue. */
    @Test
    fun `a film overview is searched as a film`() {
        client().findOverview(title = "Um Filme", year = 1973)

        val search = paths.first { it.startsWith("/search/") }
        assertTrue("a sinopse do filme foi procurada nas series: $paths", "/search/movie" in search)
    }

    /** A title TMDb does not carry answers nothing, rather than an empty paragraph. */
    @Test
    fun `a title with no overview answers nothing`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    paths += request.path.orEmpty()
                    return MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"results":[{"id":77,"overview":"   "}]}""")
                }
            }

        assertEquals(null, client().findOverview(title = "Um Filme", year = null))
    }

    /** A blank title asks nothing at all. */
    @Test
    fun `a blank title is not searched`() {
        assertEquals(null, client().findTrailer(title = "   ", year = null))
        assertTrue("pediu alguma coisa para um titulo vazio: $paths", paths.isEmpty())
    }
}
