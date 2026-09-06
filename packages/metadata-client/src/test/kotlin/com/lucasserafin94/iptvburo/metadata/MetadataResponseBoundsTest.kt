package com.lucasserafin94.iptvburo.metadata

import java.io.IOException
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

/**
 * The two bounds every metadata client must hold: a response body has a size ceiling, and an
 * interrupted thread keeps its interrupt flag.
 *
 * Metadata is an enhancement, so both failures cost the enhancement and nothing else — but they
 * must cost it *visibly* to the machinery (a recorded failure, a preserved flag), not by hanging
 * the thread or exhausting the heap first.
 */
class MetadataResponseBoundsTest {
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

    private fun json(body: String) =
        MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    @Test
    fun `a tmdb response beyond the byte ceiling yields nothing rather than exhausting memory`() {
        val client =
            TmdbClient(
                apiKey = "test-key",
                client = OkHttpClient(),
                baseUrl = server.url("/3/").toString().toHttpUrl(),
                maximumResponseBytes = 256,
            )
        // Chunked, so no Content-Length is declared: the streaming cap, not the declared-length
        // check, is what must stop it.
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setChunkedBody("""{"results":[{"name":"${"x".repeat(4_096)}"}]}""", 64),
        )

        assertNull(client.findPerson("Anyone"))
    }

    @Test
    fun `a tmdb response under the ceiling is still read`() {
        val client =
            TmdbClient(
                apiKey = "test-key",
                client = OkHttpClient(),
                baseUrl = server.url("/3/").toString().toHttpUrl(),
                maximumResponseBytes = 4_096,
            )
        server.enqueue(json("""{"results":[{"id":7,"name":"Someone","known_for":[]}]}"""))

        assertEquals("Someone", client.findPerson("Someone")?.name)
    }

    @Test
    fun `a critics response beyond the byte ceiling yields nothing`() {
        val client =
            CriticScoresClient(
                apiKey = "test-key",
                client = OkHttpClient(),
                baseUrl = server.url("/").toString().toHttpUrl(),
                maximumResponseBytes = 256,
            )
        server.enqueue(json("""{"Response":"True","Ratings":${"x".repeat(4_096)}}"""))

        assertNull(client.scoresFor("tt0133093"))
    }

    @Test
    fun `an artwork response beyond the byte ceiling yields nothing`() {
        val client =
            AdultArtworkClient(
                apiKey = "test-key",
                client = OkHttpClient(),
                baseUrl = server.url("/").toString().toHttpUrl(),
                maximumResponseBytes = 256,
            )
        server.enqueue(json("""{"data":[{"poster":"https://img.test/${"x".repeat(4_096)}.jpg"}]}"""))

        assertNull(client.posterFor("Algum Titulo"))
    }

    @Test
    fun `an interrupt during a tmdb call keeps the thread interrupted`() {
        // An interceptor can only declare IOException, but nothing stops one throwing
        // InterruptedException at runtime — which is exactly the shape a cooperative-cancellation
        // library produces. The client must restore the flag it swallowed.
        val interrupting =
            OkHttpClient.Builder()
                .addInterceptor { throw InterruptedException("cancelled") }
                .build()
        val client =
            TmdbClient(
                apiKey = "test-key",
                client = interrupting,
                baseUrl = server.url("/3/").toString().toHttpUrl(),
            )

        try {
            assertNull(client.findPerson("Anyone"))
            assertTrue(
                Thread.currentThread().isInterrupted,
                "the interrupt flag must be restored for the caller that asked to stop",
            )
        } finally {
            // Clear the flag so the shared JUnit worker thread is not left poisoned.
            Thread.interrupted()
        }
    }

    @Test
    fun `a declared content length beyond the ceiling is refused before reading`() {
        val client =
            CriticScoresClient(
                apiKey = "test-key",
                client = OkHttpClient(),
                baseUrl = server.url("/").toString().toHttpUrl(),
                maximumResponseBytes = 64,
            )
        server.enqueue(json("""{"Response":"True","Ratings":[]}"""))

        assertNull(client.scoresFor("tt0133093"))
    }
}
