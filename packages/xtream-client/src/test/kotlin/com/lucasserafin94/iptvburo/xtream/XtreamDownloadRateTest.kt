package com.lucasserafin94.iptvburo.xtream

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * That a real fetch reports how fast it is arriving, all the way out to the caller.
 *
 * The unit tests around [DownloadRate] pin the arithmetic; this pins the wiring, which is the part
 * that was silently absent before — the client read the body and nobody was told anything.
 */
class XtreamDownloadRateTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a catalogue fetch reports its download rate`() {
        // Enough categories to take several reads of the 32 KiB buffer, so there is a rate to
        // report rather than one block and an immediate end.
        val categories =
            (1..4_000).joinToString(
                prefix = "[",
                postfix = "]",
                separator = ",",
            ) { index ->
                """{"category_id":"$index","category_name":"Category number $index"}"""
            }
        server.enqueue(MockResponse().setResponseCode(200).setBody(categories))

        val reported = mutableListOf<Long?>()
        // A clock that advances on every read, so the window has elapsed time to divide by without
        // the test depending on how fast the machine running it happens to be.
        var fakeNow = 0L
        val client =
            XtreamClient(
                httpClient =
                    OkHttpClient.Builder()
                        .readTimeout(5, TimeUnit.SECONDS)
                        .followRedirects(false)
                        .build(),
                onDownloadRate = { reported += it },
                clock = {
                    fakeNow += 100
                    fakeNow
                },
            )

        val collection =
            client.categories(
                XtreamCredentials(
                    serverUrl = server.url("/").toString(),
                    username = "user",
                    password = "pass",
                ),
                XtreamContentType.MOVIE,
            )

        assertEquals(4_000, collection.items.size)
        assertTrue("the caller was never told a rate: $reported", reported.isNotEmpty())
        assertTrue(
            "at least one report should carry a figure, got $reported",
            reported.any { it != null && it > 0 },
        )
    }

    @Test
    fun `a body small enough for one read reports no rate rather than a fictional one`() {
        // One block in and straight out: there is no elapsed transfer to measure, and inventing a
        // number here is exactly what would put a wrong figure on the loading screen.
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"category_id":"1","category_name":"Only one"}]"""),
        )

        val reported = mutableListOf<Long?>()
        var fakeNow = 0L
        val client =
            XtreamClient(
                httpClient =
                    OkHttpClient.Builder()
                        .readTimeout(5, TimeUnit.SECONDS)
                        .followRedirects(false)
                        .build(),
                onDownloadRate = { reported += it },
                clock = {
                    fakeNow += 10
                    fakeNow
                },
            )

        client.categories(
            XtreamCredentials(
                serverUrl = server.url("/").toString(),
                username = "user",
                password = "pass",
            ),
            XtreamContentType.MOVIE,
        )

        assertTrue(
            "a single-block body must not claim a rate, got $reported",
            reported.all { it == null },
        )
    }
}
