package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import com.lucasserafin94.iptvburo.xtream.XtreamClient
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/**
 * A provider that files a whole category under one generic cover.
 *
 * Reported with a screenshot of an adult catalogue where every card carried the same red "XXX
 * ADULT" stamp. Measured on the real list behind it: 52,201 covers, 30,301 distinct, and one
 * address used 10,353 times — the next most repeated appeared six.
 *
 * The counting itself is covered by PlaceholderArtworkTest. What these assert is the wiring, which
 * is the half that broke last time: the detector existed, was correct, and nothing called it.
 */
class PlaceholderArtworkWiringTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: SessionXtreamRepository

    /** The one cover this synthetic provider hands out for its whole adult category. */
    private val stamp = "http://covers.invalid/xxx-adult.png"

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            SessionXtreamRepository(
                client = XtreamClient(httpClient = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()),
            )
        loadCatalogue()
    }

    @AfterTest
    fun tearDown() {
        repository.clear()
        server.shutdown()
    }

    @Test
    fun `a cover shared by a whole category is detected on load`() {
        assertEquals(setOf(stamp), repository.placeholderArtworkUrls())
    }

    /**
     * The grid, the search results and the details page all read items through the catalogue page,
     * so this is the assertion that covers all of them at once. Patching the screens one at a time
     * is exactly what missed the catalogue grid before.
     */
    @Test
    fun `the stamped titles reach the screen with no cover`() {
        val page = page(categoryId = "adult")

        assertTrue(page.items.size >= SHARED, "a categoria adulta nao foi carregada")
        page.items.forEach { item ->
            assertNull(item.artworkUrl, "«${item.name}» ainda traz o cartaz generico")
        }
    }

    /** A film with its own cover must not be swept up by the same pass. */
    @Test
    fun `a title with its own cover keeps it`() {
        val page = page(categoryId = "films")

        val duna = page.items.single { item -> item.name == "Duna" }
        assertEquals("http://covers.invalid/duna.jpg", duna.artworkUrl)
    }

    /** Signing out has to forget the set, or the next subscription inherits this one's verdict. */
    @Test
    fun `clearing the session forgets what was detected`() {
        repository.clear()

        assertEquals(emptySet(), repository.placeholderArtworkUrls())
    }

    /** Held to the concrete type rather than the interface, whose defaults an override cannot use. */
    private fun page(categoryId: String) =
        repository.page(
            contentType = XtreamContentType.MOVIE,
            categoryId = categoryId,
            query = "",
            requestedPage = 0,
        )

    private fun loadCatalogue() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when (request.requestUrl?.queryParameter("action")) {
                        null ->
                            json("""{"user_info":{"auth":"1","status":"Active","allowed_output_formats":["ts"]}}""")

                        "get_live_streams" -> json("[]")
                        "get_series" -> json("[]")
                        "get_vod_streams" -> json(vodStreams())
                        else ->
                            json(
                                """
                                [
                                  {"category_id":"films","category_name":"Filmes"},
                                  {"category_id":"adult","category_name":"Adultos"}
                                ]
                                """.trimIndent(),
                            )
                    }
            }
        repository.authenticateAndLoadInitial(
            XtreamLoginInput(
                server = server.url("/get.php").toString().toCharArray(),
                username = "synthetic-user".toCharArray(),
                password = "synthetic-password".toCharArray(),
            ),
        )
        repository.loadCatalog(XtreamContentType.MOVIE)
    }

    /** One ordinary film with its own cover, and a category that reuses [stamp] throughout. */
    private fun vodStreams(): String {
        val stamped =
            (1..SHARED).joinToString(",") { index ->
                """{"stream_id":"$index","name":"Adulto $index","category_id":"adult",""" +
                    """"stream_icon":"$stamp","container_extension":"mp4"}"""
            }
        return """
            [
              {"stream_id":"900","name":"Duna","category_id":"films",
               "stream_icon":"http://covers.invalid/duna.jpg","container_extension":"mp4"},
              $stamped
            ]
            """.trimIndent()
    }

    private fun json(body: String): MockResponse =
        MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private companion object {
        /**
         * Titles sharing the stamp — above the detector's threshold, and chosen to fail if that
         * threshold is ever raised without this test being reconsidered.
         */
        const val SHARED = 30
    }
}
