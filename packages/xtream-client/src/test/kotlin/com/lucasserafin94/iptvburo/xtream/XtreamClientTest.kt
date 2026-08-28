package com.lucasserafin94.iptvburo.xtream

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class XtreamClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: XtreamClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            XtreamClient(
                httpClient =
                    OkHttpClient.Builder()
                        .readTimeout(5, TimeUnit.SECONDS)
                        .followRedirects(false)
                        .build(),
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `authenticate accepts numeric strings without exposing credentials`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "user_info": {
                        "auth": "1",
                        "status": "Active",
                        "is_trial": "0",
                        "active_cons": "1",
                        "max_connections": "2",
                        "allowed_output_formats": ["ts", "m3u8"]
                      }
                    }
                    """.trimIndent(),
                )
                .setHeader("Content-Type", "application/json"),
        )
        val credentials = credentials()

        val account = client.authenticate(credentials)

        assertTrue(account.authenticated)
        assertEquals("Active", account.status)
        assertEquals(2, account.maximumConnections)
        assertEquals(setOf("ts", "m3u8"), account.allowedOutputFormats)
        val recorded = server.takeRequest()
        assertEquals("IPTV BURO/0.2", recorded.headers["User-Agent"])
        assertEquals("sample-user", recorded.requestUrl?.queryParameter("username"))
        assertEquals("sample-pass", recorded.requestUrl?.queryParameter("password"))
        assertFalse(credentials.toString().contains("sample-user"))
        assertFalse(credentials.toString().contains("sample-pass"))
    }

    @Test
    fun `catalog tolerates schema drift and skips invalid entries`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    [
                      {
                        "stream_id": 42,
                        "name": "Legal test channel",
                        "category_id": "7",
                        "category_ids": [7, "8"],
                        "container_extension": "TS",
                        "stream_icon": "https://images.example/channel.png",
                        "added": "1720000000"
                      },
                      {"stream_id": null, "name": "Invalid"}
                    ]
                    """.trimIndent(),
                )
                .setHeader("Content-Type", "application/json"),
        )

        val result = client.catalog(credentials(), XtreamContentType.LIVE)

        assertEquals(1, result.items.size)
        assertEquals(1, result.skippedItemCount)
        assertEquals("42", result.items.single().providerId)
        assertEquals(listOf("7", "8"), result.items.single().categoryIds)
        assertEquals("ts", result.items.single().containerExtension)
    }

    @Test
    fun `catalog accepts numeric keyed provider objects`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "0": {
                        "stream_id": "51",
                        "name": "Synthetic movie",
                        "category_id": "9",
                        "container_extension": "mp4"
                      }
                    }
                    """.trimIndent(),
                )
                .setHeader("Content-Type", "application/json"),
        )

        val result = client.catalog(credentials(), XtreamContentType.MOVIE)

        assertEquals(1, result.items.size)
        assertEquals("51", result.items.single().providerId)
    }

    @Test
    fun `catalog accepts empty false responses and textual category arrays`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    [
                      {
                        "stream_id": "54",
                        "name": "Synthetic multi-category item",
                        "category_id": "7",
                        "category_ids": "[7, \"8\"]"
                      }
                    ]
                    """.trimIndent(),
                )
                .setHeader("Content-Type", "application/json"),
        )
        server.enqueue(
            MockResponse()
                .setBody("false")
                .setHeader("Content-Type", "application/json"),
        )

        val populated = client.catalog(credentials(), XtreamContentType.LIVE)
        val empty = client.catalog(credentials(), XtreamContentType.MOVIE)

        assertEquals(listOf("7", "8"), populated.items.single().categoryIds)
        assertTrue(empty.items.isEmpty())
    }

    @Test
    fun `catalog drops credential bearing artwork URLs`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    [
                      {
                        "stream_id": "52",
                        "name": "Synthetic protected artwork",
                        "stream_icon": "https://images.example/sample-user/poster.jpg"
                      },
                      {
                        "stream_id": "53",
                        "name": "Synthetic tokenized artwork",
                        "stream_icon": "https://images.example/poster.jpg?token=private"
                      }
                    ]
                    """.trimIndent(),
                )
                .setHeader("Content-Type", "application/json"),
        )

        val result = client.catalog(credentials(), XtreamContentType.LIVE)

        assertEquals(2, result.items.size)
        assertTrue(result.items.all { it.artworkUrl == null })
    }

    @Test
    fun `series details parse episode maps`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "info": {
                        "name": "Legal sample series",
                        "plot": "Synthetic",
                        "cast": "Actor One",
                        "genre": "Drama",
                        "release_date": "2026-01-05",
                        "rating": "7.8",
                        "youtube_trailer": "AbCdEf12345"
                      },
                      "episodes": {
                        "1": [
                          {
                            "id": "9001",
                            "title": "Episode 1",
                            "season": 1,
                            "episode_num": "1",
                            "container_extension": "mp4",
                            "info": {"movie_image": "https://images.example/e1.png"}
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                )
                .setHeader("Content-Type", "application/json"),
        )

        val details = client.seriesDetails(credentials(), "77")

        assertEquals("Legal sample series", details.title)
        assertEquals(1, details.episodes.size)
        assertEquals("9001", details.episodes.single().providerId)
        assertEquals(1, details.episodes.single().episodeNumber)
        assertEquals("Actor One", details.cast)
        assertEquals("Drama", details.genre)
        assertEquals("2026-01-05", details.releaseDate)
        assertEquals(7.8, details.rating)
        assertEquals("AbCdEf12345", details.youtubeTrailerId)
    }

    @Test
    fun `series details use the season map key when episode season is absent`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "info": {"name": "Synthetic series"},
                      "episodes": {
                        "3": [
                          {
                            "id": "9100",
                            "title": "Episode without season field",
                            "episode_num": 2,
                            "container_extension": "mkv"
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                )
                .setHeader("Content-Type", "application/json"),
        )

        val details = client.seriesDetails(credentials(), "78")

        assertEquals(3, details.episodes.single().seasonNumber)
        assertEquals(2, details.episodes.single().episodeNumber)
    }

    @Test
    fun `movie details parse rich provider metadata and safe trailer id`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "info": {
                        "name": "Synthetic feature",
                        "plot": "A legal fixture used only by tests.",
                        "cast": "Actor One, Actor Two",
                        "director": "Director One",
                        "genre": "Drama, Mystery",
                        "duration": "01:42:00",
                        "release_date": "2026-02-12",
                        "country": "BR",
                        "rating": "8.4",
                        "movie_image": "https://images.example/poster.jpg",
                        "backdrop_path": ["https://images.example/backdrop.jpg"],
                        "youtube_trailer": "https://www.youtube.com/watch?v=AbCdEf12345"
                      },
                      "movie_data": {
                        "stream_id": "501",
                        "container_extension": "MKV"
                      }
                    }
                    """.trimIndent(),
                ).setHeader("Content-Type", "application/json"),
        )

        val details = client.movieDetails(credentials(), "501")

        assertEquals("Synthetic feature", details.title)
        assertEquals("Actor One, Actor Two", details.cast)
        assertEquals("Director One", details.director)
        assertEquals("Drama, Mystery", details.genre)
        assertEquals("2026-02-12", details.releaseDate)
        assertEquals(8.4, details.rating)
        assertEquals("https://images.example/poster.jpg", details.artworkUrl)
        assertEquals(listOf("https://images.example/backdrop.jpg"), details.backdropUrls)
        assertEquals("AbCdEf12345", details.youtubeTrailerId)
        assertEquals("mkv", details.containerExtension)
    }

    @Test
    fun `movie details tolerate scalar backdrop and alternate fields`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "info": {
                        "description": "Alternate plot",
                        "actors": "Alternate cast",
                        "releasedate": "2025",
                        "cover_big": "https://images.example/cover.jpg",
                        "backdrop_path": "https://images.example/background.jpg",
                        "youtube_trailer": "not a safe trailer reference"
                      },
                      "movie_data": {"title": "Alternate title"}
                    }
                    """.trimIndent(),
                ).setHeader("Content-Type", "application/json"),
        )

        val details = client.movieDetails(credentials(), "502")

        assertEquals("Alternate title", details.title)
        assertEquals("Alternate plot", details.plot)
        assertEquals("Alternate cast", details.cast)
        assertEquals("2025", details.releaseDate)
        assertEquals(listOf("https://images.example/background.jpg"), details.backdropUrls)
        assertEquals(null, details.youtubeTrailerId)
    }

    @Test
    fun `short epg decodes provider text and selects now and next`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "epg_listings": [
                        {
                          "title": "UHJvZ3JhbWEgYXR1YWw=",
                          "description": "RGVzY3Jpw6fDo28gc2VndXJh",
                          "start_timestamp": "1760000000",
                          "stop_timestamp": "1760003600"
                        },
                        {
                          "title": "Próximo programa",
                          "start_timestamp": 1760003600,
                          "stop_timestamp": 1760007200
                        },
                        {"title": ""}
                      ]
                    }
                    """.trimIndent(),
                )
                .setHeader("Content-Type", "application/json"),
        )

        val epg = client.shortEpg(credentials(), "42")
        val (now, next) = epg.nowAndNext(1760000100)

        assertEquals(2, epg.programs.size)
        assertEquals(1, epg.skippedProgramCount)
        assertEquals("Programa atual", now?.title)
        assertEquals("Descrição segura", now?.description)
        assertEquals("Próximo programa", next?.title)
        val recorded = server.takeRequest()
        assertEquals("get_short_epg", recorded.requestUrl?.queryParameter("action"))
        assertEquals("42", recorded.requestUrl?.queryParameter("stream_id"))
        assertEquals("8", recorded.requestUrl?.queryParameter("limit"))
    }

    @Test
    fun `short epg rejects unsafe limits and ignores invalid rows`() {
        assertThrows(IllegalArgumentException::class.java) {
            client.shortEpg(credentials(), "42", limit = 0)
        }
        server.enqueue(MockResponse().setBody("false").setHeader("Content-Type", "application/json"))

        val epg = client.shortEpg(credentials(), "42")

        assertTrue(epg.programs.isEmpty())
    }

    @Test
    fun `xmltv guide is fetched, parsed and indexed by channel id`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """<?xml version="1.0" encoding="UTF-8"?><tv>
                    <programme channel="42" start="20260824203000 +0000" stop="20260824213000 +0000">
                    <title>Jornal</title><desc>As noticias da noite.</desc></programme>
                    <programme channel="42" start="20260825203000 +0000" stop="20260825213000 +0000">
                    <title>Jornal (dia seguinte)</title></programme>
                    <programme channel="7" start="20260824203000 +0000" stop="20260824213000 +0000">
                    <title>Outro canal</title></programme>
                    </tv>""",
                )
                .setHeader("Content-Type", "application/xml"),
        )

        val guide = client.xmltvGuide(credentials())

        assertEquals(2, guide.keys.size)
        val channel42 = guide["42"].orEmpty()
        assertEquals(2, channel42.size)
        assertEquals("Jornal", channel42[0].title)
        assertEquals("As noticias da noite.", channel42[0].description)
        assertEquals("Jornal (dia seguinte)", channel42[1].title)
        assertEquals(1, guide["7"]?.size)
        val recorded = server.takeRequest()
        assertEquals("/xmltv.php", recorded.path?.substringBefore('?'))
        assertEquals("sample-user", recorded.requestUrl?.queryParameter("username"))
        assertEquals("sample-pass", recorded.requestUrl?.queryParameter("password"))
        assertNull(
            "xmltv.php has no action parameter — it is a different endpoint from player_api.php",
            recorded.requestUrl?.queryParameter("action"),
        )
    }

    @Test
    fun `xmltv guide matching is case and whitespace insensitive`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """<?xml version="1.0" encoding="UTF-8"?><tv>
                    <programme channel=" ABC-1 " start="20260824203000 +0000" stop="20260824213000 +0000">
                    <title>Jornal</title></programme>
                    </tv>""",
                )
                .setHeader("Content-Type", "application/xml"),
        )

        val guide = client.xmltvGuide(credentials())

        assertEquals(1, guide["abc-1"]?.size)
    }

    @Test
    fun `xmltv guide is empty rather than thrown when the provider does not publish one`() {
        server.enqueue(MockResponse().setResponseCode(404))

        val guide = client.xmltvGuide(credentials())

        assertTrue(guide.isEmpty())
    }

    @Test
    fun `xmltv guide with blank credentials never reaches the network`() {
        val guide = client.xmltvGuide(XtreamCredentials(serverUrl = server.url("/").toString(), username = "", password = ""))

        assertTrue(guide.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `copied playlist URL is normalized and playback path is encoded`() {
        val credentials =
            XtreamCredentials(
                serverUrl =
                    "${server.url("/")}get.php?username=ignored&password=ignored&type=m3u_plus",
                username = "sample user",
                password = "sample/pass",
            )

        val url =
            client.buildPlaybackUrl(
                credentials = credentials,
                contentType = XtreamContentType.MOVIE,
                providerId = "123",
                containerExtension = ".MP4",
            )

        assertEquals("/movie/sample%20user/sample%2Fpass/123.mp4", url.encodedPath)
        assertEquals(null, url.query)
    }

    @Test
    fun `server without a scheme defaults to HTTPS`() {
        val endpoint = XtreamEndpointParser.parse("media.example.test:8443/get.php")

        assertEquals("https", endpoint.baseUrl.scheme)
        assertEquals("media.example.test", endpoint.baseUrl.host)
        assertEquals(8443, endpoint.baseUrl.port)
        assertEquals("/", endpoint.baseUrl.encodedPath)
    }

    @Test
    fun `copied endpoint with a trailing slash is normalized`() {
        val endpoint =
            XtreamEndpointParser.parse(
                "https://media.example.test/panel/get.php/?username=ignored",
            )

        assertEquals("/panel", endpoint.baseUrl.encodedPath)
        assertEquals(null, endpoint.baseUrl.query)
    }

    @Test
    fun `HTML and HTTP errors never include secret values`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("<html>forbidden sample-user sample-pass</html>"),
        )
        val error =
            assertThrows(XtreamClientException::class.java) {
                client.authenticate(credentials())
            }

        assertEquals(XtreamFailureReason.HTTP, error.reason)
        assertFalse(error.message.orEmpty().contains("sample-user"))
        assertFalse(error.message.orEmpty().contains("sample-pass"))
    }

    @Test
    fun `transient server failure is retried within a strict budget`() {
        client =
            XtreamClient(
                httpClient = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
                maximumTransientRetries = 1,
                retryDelayMillis = 0,
            )
        server.enqueue(MockResponse().setResponseCode(503).setBody("temporarily unavailable"))
        server.enqueue(
            MockResponse()
                .setBody("""{"user_info":{"auth":"1","status":"Active"}}""")
                .setHeader("Content-Type", "application/json"),
        )

        val account = client.authenticate(credentials())

        assertTrue(account.authenticated)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `authentication style http failures are never retried`() {
        client =
            XtreamClient(
                httpClient = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
                maximumTransientRetries = 2,
                retryDelayMillis = 0,
            )
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))

        assertThrows(XtreamClientException::class.java) { client.authenticate(credentials()) }

        assertEquals(1, server.requestCount)
    }

    // ---------------------------------------------------------------------------------------
    // Categories
    //
    // These moved from the buffered request path to the streaming one, because buffering the whole
    // response held it as bytes, as a String and as a JSON tree at the same time under a 512 MiB
    // ceiling — a very large allocation on a big provider, during startup. The behaviour below is
    // what the buffered version did and must not change.
    // ---------------------------------------------------------------------------------------

    @Test
    fun `categories are read from a JSON array`() {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  {"category_id": "1", "category_name": "Filmes | Ação"},
                  {"category_id": "2", "category_name": "Filmes | Drama"}
                ]
                """.trimIndent(),
            ),
        )

        val categories = client.categories(credentials(), XtreamContentType.MOVIE)

        assertEquals(2, categories.items.size)
        assertEquals("1", categories.items.first().providerId)
        assertEquals("Filmes | Ação", categories.items.first().name)
        assertEquals(XtreamContentType.MOVIE, categories.items.first().contentType)
    }

    /** Panels disagree on whether an id is quoted; the numeric form must not throw. */
    @Test
    fun `a numeric category id is accepted`() {
        server.enqueue(
            MockResponse().setBody("""[{"category_id": 7, "category_name": "Infantil"}]"""),
        )

        val categories = client.categories(credentials(), XtreamContentType.SERIES)

        assertEquals("7", categories.items.single().providerId)
    }

    /** Some panels answer with an object keyed by index rather than an array. */
    @Test
    fun `categories are read from an index-keyed object`() {
        server.enqueue(
            MockResponse().setBody(
                """{"0": {"category_id": "1", "category_name": "Ao vivo"}}""",
            ),
        )

        assertEquals("Ao vivo", client.categories(credentials(), XtreamContentType.LIVE).items.single().name)
    }

    @Test
    fun `entries without an id or a name are skipped rather than failing the request`() {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  {"category_id": "1", "category_name": "Válida"},
                  {"category_id": "", "category_name": "Sem id"},
                  {"category_id": "3"},
                  "não é um objeto"
                ]
                """.trimIndent(),
            ),
        )

        val categories = client.categories(credentials(), XtreamContentType.MOVIE)

        assertEquals(1, categories.items.size)
        assertEquals(3, categories.skippedItemCount)
    }

    /** `false` is how several panels say "none", and an empty list is an ordinary answer. */
    @Test
    fun `a false body is an empty category list rather than an error`() {
        server.enqueue(MockResponse().setBody("false"))

        assertTrue(client.categories(credentials(), XtreamContentType.MOVIE).items.isEmpty())
    }

    @Test
    fun `HTML instead of JSON is reported as an invalid response`() {
        server.enqueue(MockResponse().setBody("<html><body>login</body></html>"))

        val error =
            assertThrows(XtreamClientException::class.java) {
                client.categories(credentials(), XtreamContentType.MOVIE)
            }

        assertEquals(XtreamFailureReason.INVALID_RESPONSE, error.reason)
    }

    /**
     * The buffered path is bounded far below the streamed one.
     *
     * `request` holds the body as bytes, as a String and as a JSON tree at the same time. The app
     * runs on a 768 MB heap, so sharing the 512 MB streamed ceiling meant one oversized response
     * could take the entire heap — an OutOfMemoryError reported to the user as their provider
     * sending a malformed catalogue.
     */
    @Test
    fun `a buffered response is refused well below the streamed ceiling`() {
        val overLimit = 2 * 1024 * 1024
        val client =
            XtreamClient(
                httpClient = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
                maximumBufferedBytes = 1024 * 1024,
            )
        // Valid JSON, simply too large to hold.
        server.enqueue(MockResponse().setBody("""{"user_info":{"auth":1},"padding":"${"x".repeat(overLimit)}"}"""))

        val error =
            assertThrows(XtreamClientException::class.java) { client.authenticate(credentials()) }

        assertEquals(XtreamFailureReason.RESPONSE_TOO_LARGE, error.reason)
    }

    /** The buffered ceiling may never exceed the streamed one, or the split protects nothing. */
    @Test
    fun `the buffered ceiling cannot exceed the streamed one`() {
        assertThrows(IllegalArgumentException::class.java) {
            XtreamClient(maximumResponseBytes = 1024, maximumBufferedBytes = 2048)
        }
    }

    private fun credentials(): XtreamCredentials =
        XtreamCredentials(
            serverUrl = server.url("/").toString(),
            username = "sample-user",
            password = "sample-pass",
        )
}
