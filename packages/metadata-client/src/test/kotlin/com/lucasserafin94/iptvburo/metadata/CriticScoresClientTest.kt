package com.lucasserafin94.iptvburo.metadata

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CriticScoresClientTest {
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

    private fun client(apiKey: String? = "test-key"): CriticScoresClient =
        CriticScoresClient(
            apiKey = apiKey,
            client = OkHttpClient(),
            baseUrl = server.url("/").toString().toHttpUrl(),
        )

    private fun json(body: String) =
        MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    /** The shape OMDb actually returns, taken from a real response for The Matrix. */
    private val fullBody =
        """
        {
          "Title": "The Matrix",
          "Response": "True",
          "Ratings": [
            {"Source": "Internet Movie Database", "Value": "8.7/10"},
            {"Source": "Rotten Tomatoes", "Value": "83%"},
            {"Source": "Metacritic", "Value": "73/100"}
          ]
        }
        """.trimIndent()

    @Test
    fun `all three scores are read from one response`() {
        server.enqueue(json(fullBody))

        val scores = client().scoresFor("tt0133093")

        assertNotNull(scores)
        assertEquals(83, scores.tomatometer)
        assertEquals(73, scores.metascore)
        assertEquals(8.7, scores.imdbRating)
    }

    /**
     * The three sources use two different notations for the same idea — "83%" and "73/100" — and
     * both have to arrive as a percentage, or the row would show 73% beside a raw 73.
     */
    @Test
    fun `a metascore out of a hundred becomes a percentage`() {
        server.enqueue(json(fullBody))

        assertEquals(73, client().scoresFor("tt0133093")?.metascore)
    }

    /**
     * OMDb often has an IMDb rating for a film no critic aggregated. The absent scores must stay
     * absent rather than borrow another company's number, so the row shows what exists.
     */
    @Test
    fun `a title with only an IMDb rating keeps the other scores null`() {
        server.enqueue(
            json(
                """
                {"Response":"True","Ratings":[{"Source":"Internet Movie Database","Value":"6.1/10"}]}
                """.trimIndent(),
            ),
        )

        val scores = client().scoresFor("tt0133093")

        assertNotNull(scores)
        assertEquals(6.1, scores.imdbRating)
        assertNull(scores.tomatometer)
        assertNull(scores.metascore)
    }

    /** Nothing to show is null, so the caller draws no row rather than a row of gaps. */
    @Test
    fun `a response with no ratings at all is null`() {
        server.enqueue(json("""{"Response":"True","Ratings":[]}"""))

        assertNull(client().scoresFor("tt0133093"))
    }

    /**
     * OMDb reports failure inside a 200 body rather than with a status code. Read carelessly, the
     * error object parses as a title with no ratings — which is the same as a real title with no
     * ratings, and would have been indistinguishable had this not been checked explicitly.
     */
    @Test
    fun `an error body answered with HTTP 200 is not treated as a title`() {
        server.enqueue(json("""{"Response":"False","Error":"Incorrect IMDb ID."}"""))

        assertNull(client().scoresFor("tt0133093"))
    }

    /** A page already showing the user their film must not break over a ratings lookup. */
    @Test
    fun `a server error costs the scores and nothing else`() {
        server.enqueue(MockResponse().setResponseCode(500))

        assertNull(client().scoresFor("tt0133093"))
    }

    @Test
    fun `malformed json is discarded rather than thrown`() {
        server.enqueue(json("not json at all"))

        assertNull(client().scoresFor("tt0133093"))
    }

    /** No key is the default state of the app, and must be silent rather than an error. */
    @Test
    fun `without a key nothing is requested`() {
        assertNull(client(apiKey = null).scoresFor("tt0133093"))
        assertFalse(client(apiKey = null).isConfigured)
        assertEquals(0, server.requestCount, "no request should have been made")
    }

    /**
     * OMDb answers a malformed id with a 200 and an error body, so a request would be spent to be
     * told no. The daily allowance on a free key is a thousand.
     */
    @Test
    fun `an id that is not an IMDb id is rejected without a request`() {
        assertNull(client().scoresFor("603"))
        assertNull(client().scoresFor(""))
        assertNull(client().scoresFor("tt12"))
        assertEquals(0, server.requestCount, "no request should have been made")
    }

    @Test
    fun `the id and key are sent as OMDb expects them`() {
        server.enqueue(json(fullBody))

        client().scoresFor("tt0133093")

        val request = server.takeRequest()
        assertEquals("tt0133093", request.requestUrl?.queryParameter("i"))
        assertEquals("test-key", request.requestUrl?.queryParameter("apikey"))
    }

    /** The key travels in the URL, so it must never reach a log line through toString. */
    @Test
    fun `toString does not carry the key`() {
        val description = client(apiKey = "secret-key-value").toString()

        assertFalse(description.contains("secret-key-value"), "the key must not appear in toString")
        assertTrue(description.contains("configured=true"))
    }

    /** A score outside 0..100 is a parsing mistake, not a verdict worth showing. */
    @Test
    fun `an out of range percentage is discarded`() {
        server.enqueue(
            json(
                """
                {"Response":"True","Ratings":[{"Source":"Rotten Tomatoes","Value":"830%"}]}
                """.trimIndent(),
            ),
        )

        assertNull(client().scoresFor("tt0133093"))
    }
}
