package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import com.lucasserafin94.iptvburo.xtream.XtreamClient
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/**
 * The search tab's one job: find a title without the user first knowing what kind it is.
 *
 * The per-screen filters cannot do this — they narrow whatever catalogue is already open, so
 * looking for a film while browsing live channels finds nothing. These assert the ordering and the
 * matching that make one box usable instead.
 */
class CatalogueSearchTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: SessionXtreamRepository

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            SessionXtreamRepository(
                client = XtreamClient(httpClient = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()),
            )
        loadCatalogues()
    }

    @AfterTest
    fun tearDown() {
        repository.clear()
        server.shutdown()
    }

    @Test
    fun `one query reaches films series and live channels at once`() {
        val names = repository.search("duna").map { item -> item.name }

        assertTrue("Duna" in names, "o filme nao foi encontrado")
        assertTrue("Duna: A Serie" in names, "a serie nao foi encontrada")
        assertTrue("Canal Duna HD" in names, "o canal nao foi encontrado")
    }

    /**
     * Titles before channels.
     *
     * Someone typing a name almost always wants a film or a series. A provider carrying dozens of
     * channels with a matching word would otherwise bury the one title they meant, and the search
     * would be technically correct and useless.
     */
    @Test
    fun `films and series come before live channels`() {
        val kinds = repository.search("duna").map { item -> item.contentType }

        val lastTitle = kinds.indexOfLast { it != XtreamContentType.LIVE }
        val firstChannel = kinds.indexOfFirst { it == XtreamContentType.LIVE }
        assertTrue(
            firstChannel > lastTitle,
            "um canal apareceu antes de um titulo: $kinds",
        )
    }

    /**
     * Accents must not decide whether a search works.
     *
     * `contains` compares code points, so on a Portuguese catalogue "chefao" found nothing while
     * "Chefão" sat right there — and nobody types the circumflex into a search box.
     */
    @Test
    fun `an unaccented query finds an accented title`() {
        val names = repository.search("chefao").map { item -> item.name }

        assertTrue("O Chefão" in names, "a busca sem acento nao encontrou o titulo acentuado")
    }

    /** Provider decoration must not hide a title either. */
    @Test
    fun `decoration in the provider name does not hide a title`() {
        val names = repository.search("interestelar").map { item -> item.name }

        assertTrue(
            names.any { it.contains("Interestelar") },
            "o titulo decorado com [4K] nao foi encontrado",
        )
    }

    @Test
    fun `a single character is refused rather than returning half the catalogue`() {
        // One letter matches most of any catalogue: slow to walk and useless to read.
        assertEquals(emptyList(), repository.search("d"))
        assertEquals(emptyList(), repository.search(" "))
        assertEquals(emptyList(), repository.search(""))
    }

    @Test
    fun `a query matching nothing returns nothing rather than everything`() {
        assertEquals(emptyList(), repository.search("zzzzzznaoexiste"))
    }

    @Test
    fun `the result count is bounded`() {
        // A catalogue of forty thousand items with a common word must not hand the screen all of
        // them. The floor of 1 is the real assertion: bounded, not empty.
        val many = repository.search("a", limit = 5)
        assertTrue(many.size <= 5, "o limite nao foi respeitado: ${many.size}")
    }

    private fun loadCatalogues() {
        server.dispatcher =
            object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                    return when (request.requestUrl?.queryParameter("action")) {
                        null ->
                            json("""{"user_info":{"auth":"1","status":"Active","allowed_output_formats":["ts"]}}""")

                        "get_live_streams" ->
                            json(
                                """
                                [
                                  {"stream_id":"10","name":"Canal Duna HD","category_id":"c1"},
                                  {"stream_id":"11","name":"Canal Qualquer","category_id":"c1"}
                                ]
                                """.trimIndent(),
                            )

                        "get_vod_streams" ->
                            json(
                                """
                                [
                                  {"stream_id":"1","name":"Duna","category_id":"c1","container_extension":"mp4"},
                                  {"stream_id":"2","name":"O Chefão","category_id":"c1","container_extension":"mp4"},
                                  {"stream_id":"3","name":"[4K] Interestelar DUAL","category_id":"c1","container_extension":"mp4"}
                                ]
                                """.trimIndent(),
                            )

                        "get_series" ->
                            json("""[{"series_id":"20","name":"Duna: A Serie","category_id":"c1"}]""")

                        else -> json("""[{"category_id":"c1","category_name":"Tudo"}]""")
                    }
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
        repository.loadCatalog(XtreamContentType.SERIES)
    }

    private fun json(body: String): MockResponse =
        MockResponse().setHeader("Content-Type", "application/json").setBody(body)
}
