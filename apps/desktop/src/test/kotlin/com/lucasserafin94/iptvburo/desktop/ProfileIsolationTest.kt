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
import com.lucasserafin94.iptvburo.domain.model.PlaybackContentType
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgressIdentity
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/**
 * What belongs to a profile must not follow the viewer into the next one.
 *
 * A household shares one machine and one subscription: the whole point of a profile is that what it
 * remembers is that person's. A favourite, a reminder or a half-watched film leaking across is not
 * a cosmetic fault — it is one person's viewing shown to another, in a product whose Kids profile is
 * a promise about exactly that.
 *
 * These are asserted through [DesktopAppState] rather than the store, because the store being right
 * was never the doubt. The doubt is whether switching profile *reloads* from it: state held in a
 * field carries on holding the previous profile's answer until something tells it not to, and that
 * is the failure this covers.
 */
class ProfileIsolationTest {
    @Test
    fun `favourites and reminders do not follow the viewer into another profile`() =
        withScenario { state ->
            val film = state.xtreamPage.items.first()

            state.toggleFavorite(film)
            state.toggleReminder(film)
            assertTrue(state.isFavorite(film), "the film was not marked for the first profile")
            assertTrue(state.hasReminder(film), "the reminder was not marked for the first profile")

            state.selectProfile(SECOND_PROFILE.id)

            assertFalse(state.isFavorite(film), "a favourite followed the viewer into another profile")
            assertFalse(state.hasReminder(film), "a reminder followed the viewer into another profile")
            assertTrue(state.reminders.isEmpty(), "reminders=${state.reminders}")

            // And back: the first profile's marks are still its own, not lost by the visit.
            state.selectProfile(FIRST_PROFILE.id)
            assertTrue(state.isFavorite(film), "the first profile lost its favourite")
            assertTrue(state.hasReminder(film), "the first profile lost its reminder")
        }

    /**
     * Watch history is per profile, and so is everything derived from it.
     *
     * The home screen personalises itself from what this viewer has watched — see `viewerAffinity`,
     * which reads `historyEntries` — so history leaking would take the recommendations and the
     * banner with it, and one person's evening would shape another's home screen.
     */
    @Test
    fun `history and continue watching belong to the profile that watched`() =
        withScenario { state, progress ->
            val film = state.xtreamPage.items.first()
            progress.checkpoint(
                PlaybackProgressIdentity(
                    profileId = FIRST_PROFILE.id,
                    sourceId = state.xtreamSummary?.sourceId.orEmpty(),
                    contentId = film.contentIdentity().key,
                    contentType = PlaybackContentType.MOVIE,
                ),
                positionMs = 60_000,
                durationMs = 600_000,
            )

            assertTrue(state.historyEntries.isNotEmpty(), "the first profile recorded nothing")

            state.selectProfile(SECOND_PROFILE.id)

            assertTrue(
                state.historyEntries.isEmpty(),
                "another profile saw this history: ${state.historyEntries.map { it.item.name }}",
            )
            assertTrue(
                state.continueWatchingEntries.isEmpty(),
                "another profile could resume this film: ${state.continueWatchingEntries.map { it.item.name }}",
            )

            state.selectProfile(FIRST_PROFILE.id)
            assertEquals(1, state.historyEntries.size, "the first profile lost its own history")
        }

    /**
     * The home screen is rebuilt on a switch rather than left showing the previous viewer's.
     *
     * `selectProfile` puts the daily home back to Idle through `invalidateParentalBrowseSurfaces`.
     * Without that the banner, the rails and the personalised shelves would all still be the ones
     * built for whoever was watching before — the most visible leak of the lot, and the one a Kids
     * profile must never show.
     */
    @Test
    fun `switching profile discards the home built for the previous one`() =
        withScenario { state ->
            state.loadDailyHome(TEST_DATE)
            // Asserted before the switch, and it is not a formality: with a catalogue too thin to
            // build a home this stays Idle, and the assertion below would then pass without the
            // switch having discarded anything.
            val built = state.dailyHomeStatus
            assertTrue(built is DailyHomeStatus.Loaded, "the home never loaded, so this proves nothing: $built")

            state.selectProfile(SECOND_PROFILE.id)

            assertFalse(
                state.dailyHomeStatus is DailyHomeStatus.Loaded,
                "the previous profile's home survived the switch: ${state.dailyHomeStatus}",
            )
        }

    private fun withScenario(block: suspend (DesktopAppState) -> Unit) =
        withScenario { state, _ -> block(state) }

    private fun withScenario(
        block: suspend (DesktopAppState, DesktopPlaybackProgressCoordinator) -> Unit,
    ) = runBlocking {
        val server = MockWebServer()
        server.dispatcher = catalogueDispatcher()
        server.start()
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        val progressNode = node.node("progress")
        val temporary = Files.createTempDirectory("iptvburo-profile-isolation")
        val store = DesktopUserStore(node)
        store.saveProfiles(listOf(FIRST_PROFILE, SECOND_PROFILE))
        store.setActiveProfile(FIRST_PROFILE.id)
        val progress = DesktopPlaybackProgressCoordinator(DesktopPlaybackProgressStore(progressNode))
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
                playbackProgressCoordinator = progress,
            )
        try {
            state.connectXtream(
                XtreamLoginInput(
                    server = server.url("/player_api.php").toString().toCharArray(),
                    username = "synthetic-profile-user".toCharArray(),
                    password = "synthetic-profile-password".toCharArray(),
                ),
            )
            assertTrue(state.xtreamStatus is XtreamStatus.Connected, "was ${state.xtreamStatus}")
            state.selectXtreamContentType(XtreamContentType.MOVIE)
            assertTrue(state.xtreamPage.items.isNotEmpty(), "the synthetic catalogue paged nothing")
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

    /**
     * A catalogue big enough to build a home from.
     *
     * Deliberately more than the two rows these assertions need. The daily home refuses to publish
     * a snapshot it cannot fill — the first version of this had one film per kind, the home failed
     * with an IllegalArgumentException, and the switching test then passed for the wrong reason:
     * the home was never Loaded before the switch either, so "not Loaded afterwards" proved
     * nothing at all.
     *
     * The Content-Type header matters as much as the body. Without it the client discards the
     * response and every list arrives empty.
     */
    private fun catalogueDispatcher(): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val action = request.requestUrl?.queryParameter("action").orEmpty()
                val body =
                    when (action) {
                        "get_vod_categories", "get_series_categories", "get_live_categories" ->
                            """[{"category_id":"c1","category_name":"Filmes"}]"""
                        "get_vod_streams" -> streams("stream_id", "Filme")
                        "get_series" -> streams("series_id", "Serie")
                        "get_live_streams" -> streams("stream_id", "Canal")
                        else -> """{"user_info":{"auth":1},"server_info":{}}"""
                    }
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(body)
            }
        }

    /** Eight rows per kind, which is enough for the home to fill its shelves. */
    private fun streams(idKey: String, kind: String): String =
        (1..8).joinToString(prefix = "[", postfix = "]") { index ->
            """{"$idKey":"$index","name":"$kind $index","category_id":"c1","container_extension":"mp4","year":"2026"}"""
        }

    private companion object {
        val TEST_DATE: java.time.LocalDate = java.time.LocalDate.of(2026, 8, 15)
        val FIRST_PROFILE = DesktopProfile("first-profile", "Lucas", isKids = false)
        val SECOND_PROFILE = DesktopProfile("second-profile", "Outro", isKids = false)
    }
}
