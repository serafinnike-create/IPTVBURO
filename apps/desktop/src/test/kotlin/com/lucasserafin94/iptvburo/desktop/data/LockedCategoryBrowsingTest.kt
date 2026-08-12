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
 * A PIN-locked category must be absent from every listing, not merely from its own rail.
 *
 * Guarding only the category rail is the obvious implementation and it protects nothing: with no
 * category selected the catalogue lists everything the provider carries, and the search box matches
 * across all of them. A child who typed part of a title would have reached exactly the content the
 * PIN was set to keep from them.
 *
 * The fixtures here are synthetic. Nothing in this file is derived from a real playlist, and the
 * "restricted" category is named neutrally — the mechanism is what is under test, not the wording.
 */
class LockedCategoryBrowsingTest {
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
        loadTwoCategories()
    }

    @AfterTest
    fun tearDown() {
        repository.clear()
        server.shutdown()
    }

    /** The baseline: with nothing locked, both categories are listed. */
    @Test
    fun `both categories are visible when nothing is locked`() {
        val page = pageOfEverything(locked = emptySet())

        assertEquals(4, page.items.size, "was ${page.items.map { it.name }}")
    }

    /** Browsing with no category selected must not surface a locked one's titles. */
    @Test
    fun `a locked category is absent from the unfiltered listing`() {
        val page = pageOfEverything(locked = setOf(RESTRICTED))

        assertEquals(2, page.items.size, "was ${page.items.map { it.name }}")
        assertTrue(
            page.items.none { item -> RESTRICTED in item.categoryIds },
            "a locked category's titles were listed anyway: ${page.items.map { it.name }}",
        )
    }

    /**
     * The hole that made the lock decorative: search ignored the category filter entirely.
     */
    @Test
    fun `search does not reach into a locked category`() {
        val page =
            repository.page(
                contentType = XtreamContentType.MOVIE,
                categoryId = null,
                // Matches titles in both categories, which is exactly the bypass.
                query = "title",
                requestedPage = 0,
                lockedCategoryIds = setOf(RESTRICTED),
            )

        assertTrue(
            page.items.none { item -> RESTRICTED in item.categoryIds },
            "search returned locked content: ${page.items.map { it.name }}",
        )
        assertEquals(2, page.items.size, "the open category's titles must still be findable")
    }

    /** Asking for the locked category by id must not be a way around the filter either. */
    @Test
    fun `selecting the locked category directly returns nothing`() {
        val page =
            repository.page(
                contentType = XtreamContentType.MOVIE,
                categoryId = RESTRICTED,
                query = "",
                requestedPage = 0,
                lockedCategoryIds = setOf(RESTRICTED),
            )

        assertTrue(page.items.isEmpty(), "was ${page.items.map { it.name }}")
    }

    /** Clamping an out-of-range page recursively must carry the lock into the retry. */
    @Test
    fun `last-page clamp preserves locked categories`() {
        val page =
            repository.page(
                contentType = XtreamContentType.MOVIE,
                categoryId = null,
                query = "",
                requestedPage = 99,
                pageSize = 1,
                lockedCategoryIds = setOf(RESTRICTED),
            )

        assertEquals(1, page.pageIndex)
        assertEquals(listOf("Open title two"), page.items.map { it.name })
        assertTrue(page.items.none { item -> RESTRICTED in item.categoryIds })
    }

    /** Once unlocked, the titles come back: that is what entering the PIN is for. */
    @Test
    fun `an unlocked category is listed again`() {
        val page = pageOfEverything(locked = emptySet())

        assertTrue(page.items.any { item -> RESTRICTED in item.categoryIds })
    }

    private fun pageOfEverything(locked: Set<String>) =
        repository.page(
            contentType = XtreamContentType.MOVIE,
            categoryId = null,
            query = "",
            requestedPage = 0,
            lockedCategoryIds = locked,
        )

    /**
     * One open category and one that will be locked, two titles each.
     *
     * Answered by action rather than in a fixed order: the repository's request sequence is its own
     * business, and a test that depends on it breaks whenever that changes for unrelated reasons.
     */
    private fun loadTwoCategories() {
        server.dispatcher =
            object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                    val action = request.requestUrl?.queryParameter("action")
                    return when (action) {
                        null ->
                            json("""{"user_info":{"auth":"1","status":"Active","allowed_output_formats":["ts"]}}""")
                        "get_vod_categories" ->
                            json(
                                """
                                [
                                  {"category_id": "$OPEN", "category_name": "Open category"},
                                  {"category_id": "$RESTRICTED", "category_name": "Restricted category"}
                                ]
                                """.trimIndent(),
                            )
                        "get_vod_streams" ->
                            json(
                                """
                                [
                                  {"stream_id":"1","name":"Open title one","category_id":"$OPEN","container_extension":"mp4"},
                                  {"stream_id":"3","name":"Restricted title one","category_id":"$RESTRICTED","container_extension":"mp4"},
                                  {"stream_id":"2","name":"Open title two","category_id":"$OPEN","container_extension":"mp4"},
                                  {"stream_id":"4","name":"Restricted title two","category_id":"$RESTRICTED","container_extension":"mp4"}
                                ]
                                """.trimIndent(),
                            )
                        // Live and series play no part here.
                        else -> json("[]")
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
    }

    private fun json(body: String): MockResponse =
        MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private companion object {
        const val OPEN = "category-open"
        const val RESTRICTED = "category-restricted"
    }
}
