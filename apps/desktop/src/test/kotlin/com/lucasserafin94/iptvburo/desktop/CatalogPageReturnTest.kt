package com.lucasserafin94.iptvburo.desktop

import com.lucasserafin94.iptvburo.desktop.data.InMemoryCatalogRepository
import com.lucasserafin94.iptvburo.desktop.data.SessionXtreamRepository
import com.lucasserafin94.iptvburo.desktop.security.RememberedXtreamStore
import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import com.lucasserafin94.iptvburo.desktop.user.DesktopProfile
import com.lucasserafin94.iptvburo.desktop.user.DesktopUserStore
import com.lucasserafin94.iptvburo.xtream.XtreamClient
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/**
 * Coming back from a title's page must land on the page the user left.
 *
 * Reported from a second machine: page forward in the catalogue, open a film, press back, and the
 * grid is at page one again. On a catalogue of forty thousand titles that is not a small annoyance —
 * everything past the first eighty is only reachable by paging through it, and every look at a film
 * costs that walk again.
 *
 * The page index lives in `xtreamPage`, which is app state and survives the details screen. What can
 * lose it is anything that calls `refreshXtreamPage(pageIndex = 0)`, so these tests assert that the
 * ordinary open-and-return sequence does not.
 */
class CatalogPageReturnTest {
    /** The reported sequence: page forward, open a title, come back. */
    @Test
    fun `returning from a title keeps the page the user was on`() =
        withScenario { state ->
            state.applyXtreamSearch()
            state.nextXtreamPage()
            val paged = state.xtreamPage.pageIndex
            assertTrue(paged > 0, "The fixture must page at all; was $paged")

            // Opening a title. `detailsOpen` is the screen's own flag, so what the state sees of
            // this journey is the selection alone.
            val opened = state.xtreamPage.items.firstOrNull()?.providerId
            assertTrue(opened != null, "The page came back empty.")
            state.selectXtreamItem(opened)

            assertEquals(
                paged,
                state.xtreamPage.pageIndex,
                "Opening a title moved the catalogue off the page the user was on.",
            )
        }

    /**
     * The search debounce must not reset the page on its own.
     *
     * `applyXtreamSearch` goes to page zero, which is right when the query changes and wrong at any
     * other time. It runs from a `LaunchedEffect` keyed on the query and the content type, and this
     * pins that an unchanged query leaves the page alone.
     */
    @Test
    fun `re-applying an unchanged search keeps the page`() =
        withScenario { state ->
            // The workspace's LaunchedEffect fires once when the screen is first composed. Paging
            // happens after that, exactly as it does for a user.
            state.applyXtreamSearch()
            state.nextXtreamPage()
            val paged = state.xtreamPage.pageIndex
            assertTrue(paged > 0, "The fixture must page at all; was $paged")

            // Re-entering the workspace — which is what pressing back from a title's page does —
            // fires the same effect again with the query untouched.
            state.applyXtreamSearch()

            assertEquals(
                paged,
                state.xtreamPage.pageIndex,
                "Re-applying the same search sent the catalogue back to the first page.",
            )
        }

    /** Changing the query does reset it, which is the behaviour worth keeping. */
    @Test
    fun `a new search does go back to the first page`() =
        withScenario { state ->
            state.applyXtreamSearch()
            state.nextXtreamPage()
            assertTrue(state.xtreamPage.pageIndex > 0)

            state.updateXtreamSearch("filme")
            state.applyXtreamSearch()

            assertEquals(0, state.xtreamPage.pageIndex, "A new query should start at the first page.")
        }

    private fun withScenario(block: suspend (DesktopAppState) -> Unit) = runBlocking {
        val server = MockWebServer()
        server.dispatcher = catalogueDispatcher()
        server.start()
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        val temporary = Files.createTempDirectory("iptvburo-page-return")
        val store = DesktopUserStore(node)
        store.saveProfiles(listOf(PROFILE))
        store.setActiveProfile(PROFILE.id)
        val repository =
            SessionXtreamRepository(
                XtreamClient(httpClient = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()),
            )
        val state =
            DesktopAppState(
                localRepository = InMemoryCatalogRepository(),
                xtreamRepository = repository,
                rememberedXtreamStore = RememberedXtreamStore(temporary.resolve("remembered.dpapi")),
                userStore = store,
            )
        try {
            state.connectXtream(
                XtreamLoginInput(
                    server = server.url("/player_api.php").toString().toCharArray(),
                    username = "synthetic-page-user".toCharArray(),
                    password = "synthetic-page-password".toCharArray(),
                ),
            )
            assertTrue(state.xtreamStatus is XtreamStatus.Connected, "was ${state.xtreamStatus}")
            state.selectXtreamContentType(com.lucasserafin94.iptvburo.xtream.XtreamContentType.MOVIE)
            block(state)
        } finally {
            state.dispose()
            repository.clear()
            server.shutdown()
            node.removeNode()
            @OptIn(ExperimentalPathApi::class)
            temporary.deleteRecursively()
        }
    }

    /**
     * Enough films to fill several pages.
     *
     * The default page is eighty, and paging is the whole subject here, so the fixture carries two
     * hundred distinct titles — distinct because the catalogue now collapses repeated copies of one
     * film, and two hundred copies of the same name would collapse to a single page.
     */
    private fun catalogueDispatcher(): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val action = request.requestUrl?.queryParameter("action").orEmpty()
                val body =
                    when (action) {
                        "get_vod_categories", "get_series_categories", "get_live_categories" ->
                            """[{"category_id":"c1","category_name":"Aberta"}]"""
                        "get_vod_streams" ->
                            (1..200).joinToString(",", prefix = "[", postfix = "]") { index ->
                                """{"stream_id":"$index","name":"Filme sintetico $index",""" +
                                    """"category_id":"c1","container_extension":"mp4","year":"2026"}"""
                            }
                        "get_series" -> "[]"
                        else -> """{"user_info":{"auth":1},"server_info":{}}"""
                    }
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(body)
            }
        }

    private companion object {
        val PROFILE = DesktopProfile("page-return", "Adulto", isKids = false)
    }
}
