package com.lucasserafin94.iptvburo.desktop

import com.lucasserafin94.iptvburo.desktop.data.InMemoryCatalogRepository
import com.lucasserafin94.iptvburo.desktop.data.SessionXtreamRepository
import com.lucasserafin94.iptvburo.desktop.security.RememberedXtreamStore
import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import com.lucasserafin94.iptvburo.desktop.user.DesktopProfile
import com.lucasserafin94.iptvburo.desktop.user.DesktopUserStore
import com.lucasserafin94.iptvburo.desktop.user.StoredParentalLock
import com.lucasserafin94.iptvburo.domain.model.ParentalPin
import com.lucasserafin94.iptvburo.xtream.XtreamClient
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
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
 * The locked-category answer is cached, and the cache never outlives what produced it.
 *
 * `lockedCategoryIdsForBrowsing` reads the preferences store and then walks every category the
 * provider publishes — several hundred on a real list — and it sits on the hot paths: twice per
 * read of `historyEntries`, which itself resolves up to two hundred titles and is read on every
 * keystroke in the history search box, and once per content type when the home screen is built.
 *
 * These assert the *correctness* of the cache rather than its speed. A timing assertion measures
 * the machine the suite runs on and turns into a flake on a loaded runner; what actually matters is
 * that every input which can change the answer does change it. This is a parental control, and a
 * stale answer means a category the household has just locked stays browsable — far worse than any
 * amount of work saved.
 */
class LockedCategoriesCacheTest {
    /** Repeated reads with nothing changed agree with each other. */
    @Test
    fun `repeated reads give the same answer`() =
        withScenario { state ->
            val first = state.lockedCategoryIdsForBrowsing(XtreamContentType.MOVIE)

            repeat(50) {
                assertEquals(
                    first,
                    state.lockedCategoryIdsForBrowsing(XtreamContentType.MOVIE),
                    "a repeated read disagreed with the first",
                )
            }
            assertTrue(LOCKED_CATEGORY in first, "the locked category was not reported: $first")
        }

    /**
     * Switching profile recomputes.
     *
     * The sharpest case: the whole point of a Kids profile is that it does not inherit what the
     * adult unlocked, so an answer cached for one profile must never be handed to another. The
     * adult here has a PIN and the Kids profile does not, so the two answers genuinely differ.
     */
    @Test
    fun `switching profile does not reuse the previous profile's answer`() =
        withScenario { state ->
            val adult = state.lockedCategoryIdsForBrowsing(XtreamContentType.MOVIE)
            assertTrue(LOCKED_CATEGORY in adult, "the adult profile saw no lock: $adult")

            state.selectProfile(KIDS_PROFILE.id)

            // Kids has no stored lock of its own, so this must not be the adult's set.
            assertEquals(
                emptySet(),
                state.lockedCategoryIdsForBrowsing(XtreamContentType.MOVIE),
                "one profile's locked categories were served to another",
            )
        }

    /** Unlocking with the PIN takes effect at once rather than after the cache happens to expire. */
    @Test
    fun `unlocking a category is reflected immediately`() =
        withScenario { state ->
            assertTrue(LOCKED_CATEGORY in state.lockedCategoryIdsForBrowsing(XtreamContentType.MOVIE))

            // The content type is passed rather than defaulted, and that is not a detail: an unlock
            // is scoped to one type — `xtreamContentType` defaults to LIVE — so unlocking under
            // Filmes deliberately leaves the same category id locked under Ao vivo. Omitting it
            // here unlocked live:c2 and then asked about movie:c2, which looked exactly like a
            // stale cache and was not one.
            assertTrue(
                state.unlockCategory(LOCKED_CATEGORY, PIN, XtreamContentType.MOVIE),
                "the PIN was refused",
            )

            assertTrue(
                LOCKED_CATEGORY !in state.lockedCategoryIdsForBrowsing(XtreamContentType.MOVIE),
                "an unlocked category was still reported as locked",
            )
        }

    private fun withScenario(block: suspend (DesktopAppState) -> Unit) = runBlocking {
        val server = MockWebServer()
        server.dispatcher = catalogueDispatcher()
        server.start()
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        val temporary = Files.createTempDirectory("iptvburo-locked-cache")
        val store = DesktopUserStore(node)
        store.saveProfiles(listOf(ADULT_PROFILE, KIDS_PROFILE))
        store.setActiveProfile(ADULT_PROFILE.id)
        // A PIN on the adult only: the function then does its expensive half for that profile and
        // returns early for Kids, so the two answers differ and a shared cache would show.
        val pin = requireNotNull(ParentalPin.of(PIN, "salt-locked-cache"))
        store.setParentalLock(
            ADULT_PROFILE.id,
            StoredParentalLock(
                salt = pin.salt,
                hash = pin.hash,
                lockAdultCategories = false,
                lockedCategoryIds = setOf(LOCKED_CATEGORY),
            ),
        )
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
                    username = "synthetic-cache-user".toCharArray(),
                    password = "synthetic-cache-password".toCharArray(),
                ),
            )
            assertTrue(state.xtreamStatus is XtreamStatus.Connected, "was ${state.xtreamStatus}")
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

    private fun catalogueDispatcher(): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val action = request.requestUrl?.queryParameter("action").orEmpty()
                val body =
                    when (action) {
                        "get_vod_categories", "get_series_categories", "get_live_categories" ->
                            """
                            [
                              {"category_id":"c1","category_name":"Aberta"},
                              {"category_id":"$LOCKED_CATEGORY","category_name":"Trancada"}
                            ]
                            """.trimIndent()
                        "get_vod_streams" ->
                            """
                            [
                              {"stream_id":"1","name":"Filme aberto","category_id":"c1","container_extension":"mp4","year":"2026"},
                              {"stream_id":"2","name":"Filme trancado","category_id":"$LOCKED_CATEGORY","container_extension":"mp4","year":"2026"}
                            ]
                            """.trimIndent()
                        "get_series" ->
                            """[{"series_id":"1","name":"Serie","category_id":"c1","year":"2026"}]"""
                        else -> """{"user_info":{"auth":1},"server_info":{}}"""
                    }
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(body)
            }
        }

    private companion object {
        const val PIN = "4321"
        const val LOCKED_CATEGORY = "c2"
        val ADULT_PROFILE = DesktopProfile("adult-cache", "Adulto", isKids = false)
        val KIDS_PROFILE = DesktopProfile("kids-cache", "Kids", isKids = true)
    }
}
