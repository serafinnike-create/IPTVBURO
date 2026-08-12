package com.lucasserafin94.iptvburo.desktop

import com.lucasserafin94.iptvburo.desktop.data.InMemoryCatalogRepository
import com.lucasserafin94.iptvburo.desktop.data.SessionXtreamRepository
import com.lucasserafin94.iptvburo.desktop.data.contentIdentity
import com.lucasserafin94.iptvburo.desktop.playback.DesktopPlaybackProgressCoordinator
import com.lucasserafin94.iptvburo.desktop.playback.DesktopPlaybackProgressStore
import com.lucasserafin94.iptvburo.desktop.security.RememberedXtreamStore
import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import com.lucasserafin94.iptvburo.desktop.user.DesktopProfile
import com.lucasserafin94.iptvburo.desktop.user.DesktopUserStore
import com.lucasserafin94.iptvburo.desktop.user.StoredParentalLock
import com.lucasserafin94.iptvburo.domain.model.ParentalPin
import com.lucasserafin94.iptvburo.domain.model.PlaybackContentType
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgressIdentity
import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamClient
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import java.nio.file.Files
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/** Regression coverage for parental-policy changes while protected content is already visible. */
class ParentalBrowseInvalidationTest {
    @Test
    fun `locking a category removes stale home search details and history before reloading`() =
        withScenario(profiles = listOf(ADULT_PROFILE)) { state, progress ->
            state.selectXtreamContentType(XtreamContentType.MOVIE)
            state.loadDailyHome(TEST_DATE)
            assertTrue(state.setParentalPin(ADULT_PIN))
            waitForRefresh(state)

            val restricted = state.xtreamPage.items.single { item -> NEUTRAL_RESTRICTED in item.categoryIds }
            state.selectDailyItem(restricted)
            state.loadSelectedMovieDetails()
            assertIs<MovieDetailsStatus.Loaded>(state.movieDetailsStatus)

            progress.checkpoint(
                PlaybackProgressIdentity(
                    profileId = ADULT_PROFILE.id,
                    sourceId = "synthetic-library",
                    contentId = restricted.contentIdentity().key,
                    contentType = PlaybackContentType.MOVIE,
                ),
                positionMs = 60_000L,
                durationMs = 600_000L,
            )
            assertTrue(state.continueWatchingEntries.any { entry -> entry.item.providerId == restricted.providerId })
            assertTrue(state.historyEntries.any { entry -> entry.item.providerId == restricted.providerId })

            state.setCategoryLocked(NEUTRAL_RESTRICTED, locked = true)

            // The first observation after the setter must already be safe. The asynchronous rebuild
            // may have completed, so either an empty surface or a newly filtered one is acceptable.
            assertProtectedCategoryAbsent(state, NEUTRAL_RESTRICTED, XtreamContentType.MOVIE)
            assertTrue(state.movieDetailsStatus is MovieDetailsStatus.Idle)
            assertTrue(state.continueWatchingEntries.none { NEUTRAL_RESTRICTED in it.item.categoryIds })
            assertTrue(state.historyEntries.none { NEUTRAL_RESTRICTED in it.item.categoryIds })

            // Keep observing while the background page/Home rebuild runs: an older request must not
            // be able to republish the stale title between the clear and the final safe snapshot.
            repeat(40) {
                assertProtectedCategoryAbsent(state, NEUTRAL_RESTRICTED, XtreamContentType.MOVIE)
                delay(5)
            }
            waitForRefresh(state)
            assertProtectedCategoryAbsent(state, NEUTRAL_RESTRICTED, XtreamContentType.MOVIE)
            val refreshedHome = assertIs<DailyHomeStatus.Loaded>(state.dailyHomeStatus).snapshot
            assertTrue(
                allHomeItems(refreshedHome).any { item ->
                    item.contentType == XtreamContentType.SERIES && NEUTRAL_RESTRICTED in item.categoryIds
                },
                "locking a movie category must not lock a series category that reuses its id",
            )
        }

    @Test
    fun `switching from an unlocked adult profile to Kids clears unlocks and rebuilds Home`() {
        val adultLock = storedLock(ADULT_PIN, "adult-salt", setOf(NEUTRAL_RESTRICTED))
        val kidsLock = storedLock(KIDS_PIN, "kids-salt", setOf(NEUTRAL_RESTRICTED))

        withScenario(
            profiles = listOf(ADULT_PROFILE, KIDS_PROFILE),
            configureStore = { store ->
                store.setParentalLock(ADULT_PROFILE.id, adultLock)
                store.setParentalLock(KIDS_PROFILE.id, kidsLock)
            },
        ) { state, _ ->
            state.selectXtreamContentType(XtreamContentType.MOVIE)
            state.selectXtreamCategory(NEUTRAL_RESTRICTED)
            assertNotNull(state.pendingPinCategory)
            assertTrue(state.submitPendingPin(ADULT_PIN))
            assertEquals(setOf(NEUTRAL_RESTRICTED), state.xtreamPage.items.flatMap { it.categoryIds }.toSet())

            state.loadDailyHome(TEST_DATE)
            val adultHome = assertIs<DailyHomeStatus.Loaded>(state.dailyHomeStatus).snapshot
            assertTrue(
                allHomeItems(adultHome).any { item ->
                    NEUTRAL_RESTRICTED in item.categoryIds || EXPLICIT_ADULT in item.categoryIds
                },
                "the adult baseline did not contain the titles the Kids transition is meant to remove",
            )

            state.selectProfileAndRefresh(KIDS_PROFILE.id)

            // A session unlock belongs to the profile that supplied the PIN, never to the machine.
            assertTrue(NEUTRAL_RESTRICTED in state.lockedCategoryIdsForBrowsing())
            assertProtectedCategoryAbsent(state, NEUTRAL_RESTRICTED)
            waitForRefresh(state)

            val kidsHome = assertIs<DailyHomeStatus.Loaded>(state.dailyHomeStatus).snapshot
            assertTrue(
                allHomeItems(kidsHome).none { item ->
                    NEUTRAL_RESTRICTED in item.categoryIds || EXPLICIT_ADULT in item.categoryIds
                },
                "the Kids Home reused the adult profile's cached snapshot: ${allHomeItems(kidsHome).map { it.name }}",
            )
            assertProtectedCategoryAbsent(state, NEUTRAL_RESTRICTED)
        }
    }

    private fun withScenario(
        profiles: List<DesktopProfile>,
        configureStore: (DesktopUserStore) -> Unit = {},
        block: suspend (DesktopAppState, DesktopPlaybackProgressCoordinator) -> Unit,
    ) = runBlocking {
        val server = MockWebServer()
        server.dispatcher = catalogueDispatcher()
        server.start()
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        val progressNode = node.node("progress")
        val temporary = Files.createTempDirectory("iptvburo-parental-test")
        val store = DesktopUserStore(node)
        store.saveProfiles(profiles)
        store.setActiveProfile(profiles.first().id)
        configureStore(store)
        val progress = DesktopPlaybackProgressCoordinator(DesktopPlaybackProgressStore(progressNode))
        val repository =
            SessionXtreamRepository(
                XtreamClient(
                    httpClient = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
                ),
            )
        val state =
            DesktopAppState(
                localRepository = InMemoryCatalogRepository(),
                xtreamRepository = repository,
                rememberedXtreamStore = RememberedXtreamStore(temporary.resolve("remembered.dpapi")),
                userStore = store,
                playbackProgressCoordinator = progress,
            )
        try {
            state.connectXtream(
                XtreamLoginInput(
                    server = server.url("/player_api.php").toString().toCharArray(),
                    username = "synthetic-parental-user".toCharArray(),
                    password = "synthetic-parental-password".toCharArray(),
                ),
            )
            assertTrue(state.xtreamStatus is XtreamStatus.Connected, "was ${state.xtreamStatus}")
            block(state, progress)
        } finally {
            state.dispose()
            repository.clear()
            server.shutdown()
            node.removeNode()
            @OptIn(ExperimentalPathApi::class)
            temporary.deleteRecursively()
        }
    }

    private suspend fun waitForRefresh(state: DesktopAppState) {
        withTimeout(5_000L) {
            while (state.xtreamStatus !is XtreamStatus.Connected || state.dailyHomeStatus !is DailyHomeStatus.Loaded) {
                delay(10)
            }
        }
    }

    private fun assertProtectedCategoryAbsent(
        state: DesktopAppState,
        categoryId: String,
        contentType: XtreamContentType? = null,
    ) {
        assertTrue(state.xtreamPage.items.none { categoryId in it.categoryIds }, "page=${state.xtreamPage.items.map { it.name }}")
        assertTrue(state.selectedXtreamItem?.categoryIds?.contains(categoryId) != true)
        val home = state.dailyHomeStatus as? DailyHomeStatus.Loaded
        if (home != null) {
            assertTrue(
                allHomeItems(home.snapshot).none { item ->
                    (contentType == null || item.contentType == contentType) && categoryId in item.categoryIds
                },
                "home=${allHomeItems(home.snapshot).map { it.name }}",
            )
        }
    }

    private fun allHomeItems(snapshot: DailyHomeSnapshot): List<XtreamCatalogItem> =
        buildList {
            snapshot.hero?.let(::add)
            addAll(snapshot.heroRotation)
            addAll(snapshot.releasesThisYear)
            addAll(snapshot.seriesThisYear)
            addAll(snapshot.movies)
            addAll(snapshot.series)
            addAll(snapshot.live)
            snapshot.seasonal?.items?.let(::addAll)
        }

    private fun storedLock(
        pin: String,
        salt: String,
        categories: Set<String>,
    ): StoredParentalLock {
        val created = requireNotNull(ParentalPin.of(pin, salt))
        return StoredParentalLock(
            salt = created.salt,
            hash = created.hash,
            lockAdultCategories = false,
            lockedCategoryIds = categories,
        )
    }

    private fun catalogueDispatcher(): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.requestUrl?.queryParameter("action")) {
                    null -> json("""{"user_info":{"auth":"1","status":"Active","allowed_output_formats":["ts"]}}""")
                    "get_live_categories" -> categories()
                    "get_vod_categories" -> categories()
                    "get_series_categories" -> categories()
                    "get_live_streams" -> streams(idKey = "stream_id", kind = "Live")
                    "get_vod_streams" -> streams(idKey = "stream_id", kind = "Movie")
                    "get_series" -> streams(idKey = "series_id", kind = "Series")
                    "get_vod_info" ->
                        json(
                            """
                            {
                              "info": {
                                "name": "Synthetic restricted detail",
                                "plot": "Synthetic fixture.",
                                "youtube_trailer": "AbCdEf12345"
                              },
                              "movie_data": {"stream_id":"2","container_extension":"mp4"}
                            }
                            """.trimIndent(),
                        )
                    else -> json("[]")
                }
        }

    private fun categories(): MockResponse =
        json(
            """
            [
              {"category_id":"$OPEN","category_name":"Open category"},
              {"category_id":"$NEUTRAL_RESTRICTED","category_name":"Restricted category"},
              {"category_id":"$EXPLICIT_ADULT","category_name":"Adult 18+"}
            ]
            """.trimIndent(),
        )

    private fun streams(
        idKey: String,
        kind: String,
    ): MockResponse =
        json(
            """
            [
              {"$idKey":"1","name":"$kind open","category_id":"$OPEN","container_extension":"mp4","year":"2026"},
              {"$idKey":"2","name":"$kind restricted","category_id":"$NEUTRAL_RESTRICTED","container_extension":"mp4","year":"2026"},
              {"$idKey":"3","name":"$kind adult","category_id":"$EXPLICIT_ADULT","container_extension":"mp4","year":"2026"}
            ]
            """.trimIndent(),
        )

    private fun json(body: String): MockResponse =
        MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private companion object {
        val TEST_DATE: LocalDate = LocalDate.of(2026, 8, 8)
        val ADULT_PROFILE = DesktopProfile("adult-profile", "Adult", isKids = false)
        val KIDS_PROFILE = DesktopProfile("kids-profile", "Kids", isKids = true)
        const val ADULT_PIN = "1234"
        const val KIDS_PIN = "5678"
        const val OPEN = "category-open"
        const val NEUTRAL_RESTRICTED = "category-restricted"
        const val EXPLICIT_ADULT = "category-adult"
    }
}
