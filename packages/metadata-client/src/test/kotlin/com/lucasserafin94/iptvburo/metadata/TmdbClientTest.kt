package com.lucasserafin94.iptvburo.metadata

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TmdbClientTest {
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

    private fun client(apiKey: String? = "test-key"): TmdbClient =
        TmdbClient(
            apiKey = apiKey,
            client = OkHttpClient(),
            baseUrl = server.url("/3/").toString().toHttpUrl(),
            imageBaseUrl = "https://images.test",
        )

    private fun json(body: String) =
        MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    @Test
    fun `finds a person and builds the photo url`() {
        server.enqueue(
            json(
                """
                {"results":[{"id":42,"name":"Adam Byard","profile_path":"/abc.jpg",
                "known_for_department":"Acting"}]}
                """.trimIndent(),
            ),
        )

        val person = client().findPerson("Adam Byard")

        assertEquals(42, person?.id)
        assertEquals("Adam Byard", person?.name)
        assertEquals("https://images.test/w342/abc.jpg", person?.profileImageUrl)
    }

    /** A person with no photo on file must still resolve; only the image is absent. */
    @Test
    fun `a person without a photo still resolves`() {
        server.enqueue(json("""{"results":[{"id":7,"name":"Someone","profile_path":null}]}"""))

        val person = client().findPerson("Someone")

        assertEquals(7, person?.id)
        assertNull(person?.profileImageUrl)
    }

    @Test
    fun `no match returns nothing rather than guessing`() {
        server.enqueue(json("""{"results":[]}"""))

        assertNull(client().findPerson("Nobody At All"))
    }

    /**
     * The key is the user's own and the app ships without one. Every call must be inert until it is
     * supplied, and must not reach the network.
     */
    @Test
    fun `without an api key nothing is requested`() {
        val unconfigured = client(apiKey = null)

        assertEquals(false, unconfigured.isConfigured)
        assertNull(unconfigured.findPerson("Adam Byard"))
        assertTrue(unconfigured.filmography(42).isEmpty())
        assertEquals(0, server.requestCount, "no request may be made without a key")
    }

    @Test
    fun `filmography is ordered by popularity and de-duplicated`() {
        server.enqueue(
            json(
                """
                {"cast":[
                  {"title":"Quiet Film","release_date":"2011-01-01","popularity":2.0},
                  {"title":"Famous Film","release_date":"2015-06-02","popularity":90.0,
                   "poster_path":"/p.jpg","character":"Hero"},
                  {"title":"Famous Film","release_date":"2015-06-02","popularity":90.0}
                ]}
                """.trimIndent(),
            ),
        )

        val credits = client().filmography(42)

        assertEquals(listOf("Famous Film", "Quiet Film"), credits.map(TmdbCredit::title))
        assertEquals(2015, credits.first().year)
        assertEquals("Hero", credits.first().character)
        assertEquals("https://images.test/w185/p.jpg", credits.first().posterUrl)
    }

    /** A series credit uses name and first_air_date where a film uses title and release_date. */
    @Test
    fun `series credits are included`() {
        server.enqueue(
            json("""{"cast":[{"name":"A Series","first_air_date":"2019-03-04","popularity":5.0}]}"""),
        )

        val credits = client().filmography(42)

        assertEquals("A Series", credits.single().title)
        assertEquals(2019, credits.single().year)
    }

    /** Metadata is an enhancement: a failure must degrade quietly, never surface as an error. */
    @Test
    fun `a failing response yields nothing instead of throwing`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(429))

        assertNull(client().findPerson("Adam Byard"))
        assertTrue(client().filmography(42).isEmpty())
    }

    @Test
    fun `malformed json yields nothing instead of throwing`() {
        server.enqueue(json("this is not json"))

        assertNull(client().findPerson("Adam Byard"))
    }

    /** The key is a secret and must not be reachable through a log line. */
    @Test
    fun `the api key never appears in toString`() {
        assertEquals(false, client(apiKey = "super-secret").toString().contains("super-secret"))
    }

    @Test
    fun `the query and language reach the request`() {
        server.enqueue(json("""{"results":[]}"""))

        client().findPerson("Adam Byard")

        val requested = server.takeRequest().requestUrl!!
        assertEquals("Adam Byard", requested.queryParameter("query"))
        assertEquals("pt-BR", requested.queryParameter("language"))
        assertEquals("false", requested.queryParameter("include_adult"))
    }
}
