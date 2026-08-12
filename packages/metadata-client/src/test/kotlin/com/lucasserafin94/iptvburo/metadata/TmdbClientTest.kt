package com.lucasserafin94.iptvburo.metadata

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    /**
     * Most providers leave the trailer field empty even for films that plainly have one, so this
     * lookup is the difference between the button existing and not.
     */
    @Test
    fun `finds a trailer by title`() {
        server.enqueue(json("""{"results":[{"id":42,"title":"A Film"}]}"""))
        server.enqueue(
            json(
                """{"results":[
                {"site":"YouTube","type":"Featurette","key":"wrong1"},
                {"site":"YouTube","type":"Trailer","key":"right1"}]}""",
            ),
        )

        assertEquals("right1", client().findTrailer("A Film", 2026))
    }

    /** TMDb returns an empty list for a language rather than falling back, so the client must. */
    @Test
    fun `falls back to any language when the preferred one has none`() {
        server.enqueue(json("""{"results":[{"id":42,"title":"A Film"}]}"""))
        server.enqueue(json("""{"results":[]}"""))
        server.enqueue(json("""{"results":[{"site":"YouTube","type":"Trailer","key":"any1"}]}"""))

        assertEquals("any1", client().findTrailer("A Film", null))
    }

    @Test
    fun `a teaser counts when no full trailer exists`() {
        server.enqueue(json("""{"results":[{"id":42,"title":"A Film"}]}"""))
        server.enqueue(json("""{"results":[{"site":"YouTube","type":"Teaser","key":"teas1"}]}"""))

        assertEquals("teas1", client().findTrailer("A Film", null))
    }

    /** A video hosted somewhere the app cannot play is worse than none: it would open a dead panel. */
    @Test
    fun `a non-YouTube video is ignored`() {
        server.enqueue(json("""{"results":[{"id":42,"title":"A Film"}]}"""))
        server.enqueue(json("""{"results":[{"site":"Vimeo","type":"Trailer","key":"vim1"}]}"""))
        server.enqueue(json("""{"results":[{"site":"Vimeo","type":"Trailer","key":"vim1"}]}"""))

        assertNull(client().findTrailer("A Film", null))
    }

    @Test
    fun `an unknown title yields no trailer`() {
        server.enqueue(json("""{"results":[]}"""))

        assertNull(client().findTrailer("Nothing At All", null))
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

    // -------------------------------------------------------------------------------------------
    // Watch providers (GDD 9)
    // -------------------------------------------------------------------------------------------


    @Test
    fun `reads the services carrying a title in one region`() {
        server.enqueue(
            json(
                """
                {"id":42,"results":{
                  "BR":{"link":"https://www.themoviedb.org/movie/42/watch?locale=BR",
                        "flatrate":[{"provider_id":8,"provider_name":"Service A","logo_path":"/a.jpg"}],
                        "rent":[{"provider_id":2,"provider_name":"Store A","logo_path":"/b.jpg"}],
                        "buy":[{"provider_id":3,"provider_name":"Store B"}]},
                  "US":{"flatrate":[{"provider_id":9,"provider_name":"Service B"}]}}}
                """.trimIndent(),
            ),
        )

        val providers = client().watchProviders(42, "BR")

        assertNotNull(providers)
        assertEquals("BR", providers.region)
        assertEquals(listOf("Service A"), providers.subscription.map(TmdbWatchProvider::name))
        assertEquals(listOf("Store A"), providers.rent.map(TmdbWatchProvider::name))
        assertEquals(listOf("Store B"), providers.buy.map(TmdbWatchProvider::name))
        // The other region in the same response must not leak into this one.
        assertTrue(providers.subscription.none { it.name == "Service B" })
    }

    /**
     * The gap that shapes the whole feature: TMDb returns no prices, in any bucket. If this ever
     * starts failing because a price appeared, the ranking could offer "cheapest rental" again.
     */
    @Test
    fun `rental entries carry no price because the API returns none`() {
        server.enqueue(
            json(
                """
                {"results":{"BR":{"rent":[{"provider_id":2,"provider_name":"Store A"}]}}}
                """.trimIndent(),
            ),
        )

        val rental = client().watchProviders(42, "BR")?.rent?.single()

        assertNotNull(rental)
        assertEquals("Store A", rental.name)
        // TmdbWatchProvider has no price field at all — there is nothing to read.
        assertEquals(2, rental.providerId)
    }

    @Test
    fun `a region with no listing is unknown rather than unavailable`() {
        server.enqueue(json("""{"results":{"US":{"flatrate":[{"provider_id":9,"provider_name":"Service B"}]}}}"""))

        // Null means "we cannot say", which the caller must not render as "not available anywhere".
        assertNull(client().watchProviders(42, "BR"))
    }

    @Test
    fun `a region whose buckets are all empty is treated as unknown`() {
        server.enqueue(json("""{"results":{"BR":{"link":"https://example.invalid/watch"}}}"""))

        assertNull(client().watchProviders(42, "BR"))
    }

    @Test
    fun `missing buckets do not fail the parse`() {
        // TMDb omits a bucket entirely rather than sending an empty array.
        server.enqueue(json("""{"results":{"BR":{"ads":[{"provider_id":7,"provider_name":"Free Service"}]}}}"""))

        val providers = client().watchProviders(42, "BR")

        assertNotNull(providers)
        assertEquals(listOf("Free Service"), providers.withAds.map(TmdbWatchProvider::name))
        assertTrue(providers.subscription.isEmpty())
        assertTrue(providers.rent.isEmpty())
    }

    /**
     * "Free" and "with ads" are different claims. Collapsing them would promise adverts on
     * something that may have none, so the client keeps them apart.
     */
    @Test
    fun `free and ad-funded are kept apart`() {
        server.enqueue(
            json(
                """
                {"results":{"BR":{
                  "free":[{"provider_id":1,"provider_name":"Free One"}],
                  "ads":[{"provider_id":2,"provider_name":"Ads One"}]}}}
                """.trimIndent(),
            ),
        )

        val providers = client().watchProviders(42, "BR")

        assertEquals(listOf("Free One"), providers?.free?.map(TmdbWatchProvider::name))
        assertEquals(listOf("Ads One"), providers?.withAds?.map(TmdbWatchProvider::name))
    }

    @Test
    fun `the region is matched regardless of the casing asked for`() {
        server.enqueue(json("""{"results":{"BR":{"flatrate":[{"provider_id":8,"provider_name":"Service A"}]}}}"""))

        assertNotNull(client().watchProviders(42, "br"))
    }

    /**
     * A series is asked of the series endpoint.
     *
     * This is the bug that made the subscriptions screen report titles as unavailable: the path was
     * hardcoded to `movie/`, so a series was looked up among films, found nothing, and the empty
     * result was rendered as "not available here".
     */
    @Test
    fun `a series is asked of the tv endpoint`() {
        server.enqueue(
            json("""{"id":7,"results":{"BR":{"flatrate":[{"provider_id":8,"provider_name":"Service A"}]}}}"""),
        )

        val providers = client().watchProviders(7, "BR", isSeries = true)

        assertEquals(listOf("Service A"), providers?.subscription?.map(TmdbWatchProvider::name))
        assertTrue(server.takeRequest().path!!.startsWith("/3/tv/7/watch/providers"))
    }

    /** And a film still goes to the film endpoint. */
    @Test
    fun `a film is asked of the movie endpoint`() {
        server.enqueue(json("""{"id":42,"results":{"BR":{}}}"""))

        client().watchProviders(42, "BR")

        assertTrue(server.takeRequest().path!!.startsWith("/3/movie/42/watch/providers"))
    }

    /** One request, not two: the id is already known, so nothing is searched for. */
    @Test
    fun `the id is used directly rather than searched for`() {
        server.enqueue(json("""{"id":42,"results":{"BR":{}}}"""))

        client().watchProviders(42, "BR")

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `no key means no request at all`() {
        assertNull(client(apiKey = null).watchProviders(42, "BR"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a blank region is refused before any request`() {
        assertNull(client().watchProviders(42, "  "))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a rate limited response is silent rather than fatal`() {
        server.enqueue(MockResponse().setResponseCode(429))

        assertNull(client().watchProviders(42, "BR"))
    }

    @Test
    fun `the attribution required by JustWatch is available to callers`() {
        // Their terms require this on every item showing the data; access is revoked otherwise.
        assertTrue(WATCH_PROVIDER_ATTRIBUTION.contains("JustWatch"))
    }

    // -------------------------------------------------------------------------------------------
    // Browsing a service's shelf
    // -------------------------------------------------------------------------------------------

    @Test
    fun `reads a service's titles with their posters`() {
        server.enqueue(
            json(
                """
                {"results":[
                  {"id":1,"title":"Filme Um","release_date":"2021-10-21","poster_path":"/one.jpg",
                   "overview":"Sinopse","vote_average":7.5},
                  {"id":2,"title":"Filme Dois","release_date":"2019-03-01","poster_path":"/two.jpg"}]}
                """.trimIndent(),
            ),
        )

        val titles = client().titlesOnProvider(providerId = 8, region = "BR")

        assertEquals(2, titles.size)
        assertEquals("Filme Um", titles.first().title)
        assertEquals(2021, titles.first().year)
        assertEquals("https://images.test/w342/one.jpg", titles.first().posterUrl)
        assertEquals(7.5, titles.first().rating)
    }

    @Test
    fun `the service and region filters reach the request`() {
        server.enqueue(json("""{"results":[]}"""))

        client().titlesOnProvider(providerId = 8, region = "br")

        val requested = server.takeRequest().requestUrl!!
        assertEquals("8", requested.queryParameter("with_watch_providers"))
        // TMDb ignores the provider filter unless watch_region accompanies it.
        assertEquals("BR", requested.queryParameter("watch_region"))
        // Newest first, not most popular: a shelf sorted by popularity mixes a 2000 film in among
        // this year's releases, which is not what "what is on this service" is asking.
        assertEquals("primary_release_date.desc", requested.queryParameter("sort_by"))
        assertEquals("false", requested.queryParameter("include_adult"))
        // Bounded at today, or titles dated years ahead sort to the front and crowd out everything
        // the user can actually watch.
        assertNotNull(requested.queryParameter("primary_release_date.lte"))
    }

    @Test
    fun `series come from the tv endpoint and read their own field names`() {
        server.enqueue(
            json("""{"results":[{"id":5,"name":"Serie Um","first_air_date":"2024-02-01","poster_path":"/s.jpg"}]}"""),
        )

        val titles = client().titlesOnProvider(8, "BR", kind = TmdbDiscoverKind.SERIES)

        val requested = server.takeRequest().requestUrl!!
        assertTrue(requested.encodedPath.endsWith("/discover/tv"), "used ${requested.encodedPath}")
        assertEquals("first_air_date.desc", requested.queryParameter("sort_by"))
        // `name` and `first_air_date`, not `title` and `release_date`.
        assertEquals("Serie Um", titles.single().title)
        assertEquals(2024, titles.single().year)
        assertTrue(titles.single().isSeries)
    }

    @Test
    fun `upcoming asks for releases after today, soonest first`() {
        server.enqueue(json("""{"results":[]}"""))

        client().titlesOnProvider(8, "BR", kind = TmdbDiscoverKind.UPCOMING)

        val requested = server.takeRequest().requestUrl!!
        assertEquals("primary_release_date.asc", requested.queryParameter("sort_by"))
        assertNotNull(requested.queryParameter("primary_release_date.gte"))
        // No upper bound here — the whole point is titles that have not come out yet.
        assertNull(requested.queryParameter("primary_release_date.lte"))
    }

    @Test
    fun `a title without a poster is still listed`() {
        server.enqueue(json("""{"results":[{"id":1,"title":"Sem Capa"}]}"""))

        val title = client().titlesOnProvider(providerId = 8, region = "BR").single()

        assertEquals("Sem Capa", title.title)
        assertNull(title.posterUrl)
        assertNull(title.year)
    }

    @Test
    fun `a shelf is capped at the requested size`() {
        val many = (1..40).joinToString(",") { """{"id":$it,"title":"Filme $it"}""" }
        server.enqueue(json("""{"results":[$many]}"""))

        assertEquals(5, client().titlesOnProvider(providerId = 8, region = "BR", limit = 5).size)
    }

    @Test
    fun `an empty shelf is empty rather than an error`() {
        server.enqueue(json("""{"results":[]}"""))

        assertTrue(client().titlesOnProvider(providerId = 8, region = "BR").isEmpty())
    }

    @Test
    fun `a failed shelf request is silent`() {
        server.enqueue(MockResponse().setResponseCode(429))

        assertTrue(client().titlesOnProvider(providerId = 8, region = "BR").isEmpty())
    }

    @Test
    fun `no key means no shelf request`() {
        assertTrue(client(apiKey = null).titlesOnProvider(providerId = 8, region = "BR").isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the provider directory comes back in TMDb's own priority order`() {
        server.enqueue(
            json(
                """
                {"results":[
                  {"provider_id":3,"provider_name":"Third","display_priority":30},
                  {"provider_id":1,"provider_name":"First","display_priority":10},
                  {"provider_id":2,"provider_name":"Second","display_priority":20}]}
                """.trimIndent(),
            ),
        )

        val directory = client().watchProviderDirectory("BR")

        assertEquals(listOf("First", "Second", "Third"), directory.map(TmdbWatchProvider::name))
    }

    @Test
    fun `a directory entry missing its priority sorts last rather than failing`() {
        server.enqueue(
            json(
                """
                {"results":[
                  {"provider_id":1,"provider_name":"No Priority"},
                  {"provider_id":2,"provider_name":"First","display_priority":5}]}
                """.trimIndent(),
            ),
        )

        assertEquals(listOf("First", "No Priority"), client().watchProviderDirectory("BR").map(TmdbWatchProvider::name))
    }

    /**
     * A credit carries the id and kind needed to open it outside the user's own playlist.
     *
     * Without these a credit was only a title, so clicking one could do nothing but search the
     * playlist for a matching name — and a film the playlist did not have led nowhere at all. The
     * response has always carried both; nothing read them.
     */
    @Test
    fun `credits carry the catalogue id and whether they are a series`() {
        server.enqueue(
            json(
                """{"cast":[
                  {"id":603,"media_type":"movie","title":"The Matrix","popularity":9.0},
                  {"id":1399,"media_type":"tv","name":"Game of Thrones","popularity":8.0}
                ]}""",
            ),
        )

        val credits = client().filmography(42)

        assertEquals(603, credits[0].id)
        assertEquals(false, credits[0].isSeries)
        assertEquals(1399, credits[1].id)
        assertEquals(true, credits[1].isSeries, "a tv credit must be resolvable as a series")
    }

    /**
     * A credit without an id is listed rather than discarded.
     *
     * Dropping it would silently remove a film from an actor's filmography over a field that only
     * affects one of the two ways the credit can be opened — it can still be matched against the
     * user's own playlist by name.
     */
    @Test
    fun `a credit missing its id is still listed`() {
        server.enqueue(json("""{"cast":[{"title":"Untitled","popularity":1.0}]}"""))

        val credits = client().filmography(42)

        assertEquals(1, credits.size)
        assertNull(credits.single().id)
    }
}
