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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TmdbStreamingCatalogueTest {
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

    private fun catalogue(
        apiKey: String? = "test-key",
        maxServices: Int = 12,
    ) = TmdbStreamingCatalogue(
        client =
            TmdbClient(
                apiKey = apiKey,
                client = OkHttpClient(),
                baseUrl = server.url("/3/").toString().toHttpUrl(),
                imageBaseUrl = "https://images.test",
            ),
        region = "BR",
        maxServices = maxServices,
    )

    private fun json(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    private fun directory(vararg services: Pair<Int, String>) =
        json(
            """{"results":[${services.joinToString(",") { (id, name) ->
                """{"provider_id":$id,"provider_name":"$name","display_priority":$id}"""
            }}]}""",
        )

    private fun shelfOf(vararg titles: String) =
        json(
            """{"results":[${titles.mapIndexed { index, title ->
                """{"id":${index + 1},"title":"$title","release_date":"2021-01-01","poster_path":"/p$index.jpg"}"""
            }.joinToString(",")}]}""",
        )

    @Test
    fun `every service in the region gets its own shelf of covers`() {
        server.enqueue(directory(8 to "Netflix", 9 to "Prime Video"))
        server.enqueue(shelfOf("Filme A", "Filme B"))
        server.enqueue(shelfOf("Filme C"))

        val shelves = catalogue().shelves()

        assertEquals(listOf("Netflix", "Prime Video"), shelves.map { it.provider.displayName })
        assertEquals(2, shelves.first().titles.size)
        assertEquals(1, shelves.last().titles.size)
    }

    @Test
    fun `covers come through as poster urls`() {
        server.enqueue(directory(8 to "Netflix"))
        server.enqueue(shelfOf("Filme A"))

        val title = catalogue().shelves().single().titles.single()

        assertEquals("https://images.test/w342/p0.jpg", title.posterUrl)
        assertEquals(2021, title.year)
    }

    /**
     * Real listings must not wear the DEMO badge — it would tell the user that genuine availability
     * is invented, which is as misleading as the reverse.
     */
    @Test
    fun `real titles are not marked as demo`() {
        server.enqueue(directory(8 to "Netflix"))
        server.enqueue(shelfOf("Filme A"))

        assertFalse(catalogue().shelves().single().titles.single().isDemo)
    }

    @Test
    fun `a service with nothing to show gets no shelf`() {
        server.enqueue(directory(8 to "Netflix", 99 to "Servico Vazio"))
        server.enqueue(shelfOf("Filme A"))
        server.enqueue(json("""{"results":[]}"""))

        val shelves = catalogue().shelves()

        assertEquals(1, shelves.size, "an empty service must not become a heading over blank space")
        assertEquals("Netflix", shelves.single().provider.displayName)
    }

    @Test
    fun `the number of services is capped`() {
        server.enqueue(directory(1 to "A", 2 to "B", 3 to "C", 4 to "D"))
        repeat(2) { server.enqueue(shelfOf("Filme")) }

        val shelves = catalogue(maxServices = 2).shelves()

        assertEquals(2, shelves.size)
    }

    @Test
    fun `no services means no shelves rather than an error`() {
        server.enqueue(json("""{"results":[]}"""))

        val result = assertIs<TmdbShelfLoadResult.Loaded>(catalogue().loadShelves())
        assertTrue(result.shelves.isEmpty())
    }

    @Test
    fun `an unreachable catalogue shows nothing rather than failing`() {
        server.enqueue(MockResponse().setResponseCode(500))

        assertTrue(catalogue().shelves().isEmpty())
    }

    @Test
    fun `an unreachable catalogue is distinguishable from a genuinely empty one`() {
        server.enqueue(MockResponse().setResponseCode(500))

        assertIs<TmdbShelfLoadResult.Unavailable>(catalogue().loadShelves())
    }

    @Test
    fun `a rejected key is reported as such rather than as a network failure`() {
        // 401 is what TMDb answers for an invalid or not-yet-active key. The user in BUG-021 was
        // told to check a connection that was working perfectly, and went looking in the wrong
        // place; the distinction exists so the screen can name the key instead.
        server.enqueue(MockResponse().setResponseCode(401))

        val result = assertIs<TmdbShelfLoadResult.Unavailable>(catalogue().loadShelves())
        assertTrue(result.keyRejected)
    }

    @Test
    fun `a suspended key is reported as a rejected key too`() {
        server.enqueue(MockResponse().setResponseCode(403))

        val result = assertIs<TmdbShelfLoadResult.Unavailable>(catalogue().loadShelves())
        assertTrue(result.keyRejected)
    }

    @Test
    fun `a server fault does not blame the key`() {
        // The other half of the distinction, and the one that keeps it honest: a 500 must not send
        // the user off to re-check a key that is fine.
        server.enqueue(MockResponse().setResponseCode(500))

        val result = assertIs<TmdbShelfLoadResult.Unavailable>(catalogue().loadShelves())
        assertFalse(result.keyRejected)
    }

    @Test
    fun `malformed catalogue data is reported as unavailable`() {
        server.enqueue(json("[]"))

        assertIs<TmdbShelfLoadResult.Unavailable>(catalogue().loadShelves())
    }

    @Test
    fun `without a key nothing is requested at all`() {
        assertTrue(catalogue(apiKey = null).shelves().isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `opening a title returns where it can be watched`() {
        server.enqueue(directory(8 to "Netflix"))
        server.enqueue(shelfOf("Duna"))
        val built = catalogue()
        val title = built.shelves().single().titles.single()

        // detailsFor reads the providers directly, using the id the shelf already carries. It used
        // to search by title first — one request more, and the wrong film whenever the name did not
        // match exactly.
        server.enqueue(json("""{"results":{"BR":{"flatrate":[{"provider_id":8,"provider_name":"Netflix"}]}}}"""))

        val details = built.detailsFor(title)

        assertNotNull(details)
        assertEquals(1, details.offers.size)
        assertEquals("Netflix", details.offers.single().provider.displayName)
    }

    /**
     * "TMDb has nothing to say" is not "this film is unavailable". The caller must be able to tell
     * them apart, so an unknown title yields null rather than an empty details object.
     */
    @Test
    fun `a title TMDb knows nothing about yields nothing rather than an empty answer`() {
        server.enqueue(directory(8 to "Netflix"))
        server.enqueue(shelfOf("Duna"))
        val built = catalogue()
        val title = built.shelves().single().titles.single()

        // TMDb knows the title but lists it in no region — the shape of "we cannot say" now that
        // the lookup is by id and there is no search step to come back empty.
        server.enqueue(json("""{"id":42,"results":{}}"""))

        assertNull(built.detailsFor(title))
    }

    @Test
    fun `the default region is the one this build targets`() {
        assertEquals("BR", TmdbStreamingCatalogue.DEFAULT_REGION)
        assertTrue(TmdbStreamingCatalogue.DEFAULT_REGION in TmdbStreamingCatalogue.SUPPORTED_REGIONS)
    }
}
