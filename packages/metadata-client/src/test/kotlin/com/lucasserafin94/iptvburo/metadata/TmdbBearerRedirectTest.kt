package com.lucasserafin94.iptvburo.metadata

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The v4 token travels in an `Authorization` header, so where that header can end up matters.
 *
 * A bearer credential in a header follows the request, and requests can be redirected. If OkHttp
 * carried the header across a redirect to another host, anyone able to answer for TMDb — or to
 * inject a 302 — would be handed the user's token. That is a different exposure from the v3 key,
 * which is a query parameter and never reaches a host the client did not address.
 *
 * Asserted against a real redirect rather than assumed from documentation, because the answer is
 * OkHttp's rather than ours and it is the kind of default that can change under a version bump.
 */
class TmdbBearerRedirectTest {
    private val tmdb = MockWebServer()
    private val elsewhere = MockWebServer()

    @AfterTest
    fun tearDown() {
        tmdb.shutdown()
        elsewhere.shutdown()
    }

    @Test
    fun `the bearer token does not follow a redirect to another host`() {
        tmdb.start()
        elsewhere.start()

        // TMDb's address answers with a redirect pointing at a host the client never addressed.
        tmdb.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", elsewhere.url("/stolen").toString()),
        )
        elsewhere.enqueue(
            MockResponse().setBody("""{"results":[]}""").setHeader("Content-Type", "application/json"),
        )

        val token = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJzeW50aGV0aWMifQ.c3ludGhldGljLXNpZ25hdHVyZQ"
        TmdbClient(
            apiKey = token,
            client = OkHttpClient(),
            baseUrl = tmdb.url("/3/").toString().toHttpUrl(),
            imageBaseUrl = "https://images.test",
        ).findPerson("Alguém")

        // The first request carried it, which is the point of the feature.
        assertEquals("Bearer $token", tmdb.takeRequest().getHeader("Authorization"))

        // The redirect was followed — so this assertion is about a request that really happened,
        // not about one that never left.
        val redirected = elsewhere.takeRequest()
        assertNull(
            redirected.getHeader("Authorization"),
            "the TMDb token was handed to a host the client never addressed",
        )
    }
}
