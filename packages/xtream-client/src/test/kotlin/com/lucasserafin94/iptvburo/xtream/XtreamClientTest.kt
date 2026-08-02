package com.lucasserafin94.iptvburo.xtream

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun credentials(): XtreamCredentials =
        XtreamCredentials(
            serverUrl = server.url("/").toString(),
            username = "sample-user",
            password = "sample-pass",
        )
}
