package com.lucasserafin94.iptvburo.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import com.lucasserafin94.iptvburo.core.logging.AppLogger
import com.lucasserafin94.iptvburo.data.diagnostics.ConnectionTester
import com.lucasserafin94.iptvburo.data.preferences.SourceMergeSettings
import com.lucasserafin94.iptvburo.data.diagnostics.ProviderProbe
import com.lucasserafin94.iptvburo.data.discovery.NoShelfCache
import com.lucasserafin94.iptvburo.data.security.SourceConnectionStore
import com.lucasserafin94.iptvburo.stalker.StalkerCredentials
import com.lucasserafin94.iptvburo.xtream.XtreamCredentials
import com.lucasserafin94.iptvburo.data.discovery.ProviderLogoCatalogue
import com.lucasserafin94.iptvburo.data.discovery.StreamingDiscoveryRepository
import com.lucasserafin94.iptvburo.data.download.AndroidDownloadManager
import com.lucasserafin94.iptvburo.data.licensing.AndroidLicenseService
import com.lucasserafin94.iptvburo.data.licensing.AndroidLicenseStatus
import com.lucasserafin94.iptvburo.data.licensing.AssignedPlaylist
import com.lucasserafin94.iptvburo.data.licensing.RedeemFailure
import com.lucasserafin94.iptvburo.data.licensing.RedeemOutcome
import com.lucasserafin94.iptvburo.data.preferences.OnboardingPreferences
import com.lucasserafin94.iptvburo.data.local.dao.FavoriteDao
import com.lucasserafin94.iptvburo.data.local.dao.PlaybackProgressDao
import com.lucasserafin94.iptvburo.data.local.dao.ProfileDao
import com.lucasserafin94.iptvburo.data.local.entity.ChannelEntity
import com.lucasserafin94.iptvburo.data.local.entity.FavoriteEntity
import com.lucasserafin94.iptvburo.data.local.entity.PlaybackProgressEntity
import com.lucasserafin94.iptvburo.data.local.entity.ProfileEntity
import com.lucasserafin94.iptvburo.data.preferences.CatalogueGuard
import com.lucasserafin94.iptvburo.data.preferences.BannerSoundSettings
import com.lucasserafin94.iptvburo.data.preferences.SubtitleSettings
import com.lucasserafin94.iptvburo.data.local.dao.SeriesWatchDao
import com.lucasserafin94.iptvburo.data.local.entity.SeriesWatchEntity
import com.lucasserafin94.iptvburo.data.local.dao.ReminderDao
import com.lucasserafin94.iptvburo.data.local.entity.ReminderEntity
import com.lucasserafin94.iptvburo.data.preferences.PlaybackSession
import com.lucasserafin94.iptvburo.data.cache.ArtworkCacheAccess
import com.lucasserafin94.iptvburo.data.preferences.CacheFillMark
import com.lucasserafin94.iptvburo.data.preferences.CacheSettingsStore
import com.lucasserafin94.iptvburo.domain.model.CacheBudget
import com.lucasserafin94.iptvburo.data.preferences.NotificationCentreStore
import com.lucasserafin94.iptvburo.domain.model.AppNotification
import com.lucasserafin94.iptvburo.domain.model.NotificationCentre
import com.lucasserafin94.iptvburo.data.preferences.PlaybackSessionStore
import com.lucasserafin94.iptvburo.data.preferences.ReminderSchedule
import com.lucasserafin94.iptvburo.data.reminders.ReminderScheduling
import com.lucasserafin94.iptvburo.data.repository.ReminderRepository
import com.lucasserafin94.iptvburo.data.repository.UserLibraryRepository
import com.lucasserafin94.iptvburo.data.repository.CatalogPage
import com.lucasserafin94.iptvburo.data.repository.CatalogRepository
import com.lucasserafin94.iptvburo.data.repository.DownloadRateReporter
import com.lucasserafin94.iptvburo.data.repository.PlaylistImportResult
import com.lucasserafin94.iptvburo.data.repository.XtreamImportRequest
import com.lucasserafin94.iptvburo.data.repository.XtreamImportResult
import com.lucasserafin94.iptvburo.data.repository.XtreamImportStage
import com.lucasserafin94.iptvburo.data.security.MetadataKeyStore
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.CatalogueFilter
import com.lucasserafin94.iptvburo.domain.model.Category
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.Episode
import com.lucasserafin94.iptvburo.domain.model.MovieDetails
import com.lucasserafin94.iptvburo.domain.model.ReminderPolicy
import com.lucasserafin94.iptvburo.domain.model.SeriesDetails
import com.lucasserafin94.iptvburo.domain.model.Source
import com.lucasserafin94.iptvburo.domain.model.SourceType
import com.lucasserafin94.iptvburo.domain.model.LicenseDecision
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgress
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgressIdentity
import com.lucasserafin94.iptvburo.domain.model.ParentalLock
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgressRepository
import com.lucasserafin94.iptvburo.domain.model.SubtitlePresentation
import java.io.InputStream
import java.time.LocalTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import javax.inject.Provider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelNavigationTest {
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * These two assert ADR-008, which deliberately reversed the GDD 6 rule: VOD downloads are
     * offered on phones without the source declaring offline authorisation. They previously
     * asserted the opposite, from when the capability was off.
     *
     * The television case is not covered by that reversal and is asserted separately below.
     */
    /**
     * The banner starts silent, and the switch turns the sound on.
     *
     * Off by default because a television that starts talking on its own the moment it is switched
     * on is worse than a quiet one. What the switch cannot change is how a trailer *starts*: no
     * engine autoplays audio, so every one begins muted and the sound is raised once it is playing.
     */
    @Test
    fun `the banner trailer is silent until somebody asks for sound`() = runTest {
        val viewModel = createViewModel(FakeCatalogRepository())
        runCurrent()

        assertFalse(
            "a TV comeca a falar sozinha sem ninguem pedir",
            viewModel.state.value.bannerTrailerSound,
        )

        viewModel.toggleBannerTrailerSound()
        runCurrent()

        assertTrue(
            "o interruptor do som nao liga nada",
            viewModel.state.value.bannerTrailerSound,
        )
    }

    /** And it turns it off again, which is the half somebody reaches for in a hurry. */
    @Test
    fun `the switch silences the banner again`() = runTest {
        val viewModel = createViewModel(FakeCatalogRepository())
        runCurrent()

        viewModel.toggleBannerTrailerSound()
        runCurrent()
        viewModel.toggleBannerTrailerSound()
        runCurrent()

        assertFalse(
            "nao ha forma de calar o banner outra vez",
            viewModel.state.value.bannerTrailerSound,
        )
    }

    @Test
    fun `downloads destination opens on a phone under ADR-008`() = runTest {
        val viewModel = createViewModel(FakeCatalogRepository())
        runCurrent()

        viewModel.selectSection(AppSection.DOWNLOADS)

        assertEquals(AppSection.DOWNLOADS, viewModel.state.value.section)
        assertEquals(AppContent.Downloads, viewModel.state.value.content)
    }

    @Test
    fun `downloads destination stays rejected on a television`() = runTest {
        val viewModel = createViewModel(FakeCatalogRepository(), isTelevision = true)
        runCurrent()

        viewModel.selectSection(AppSection.DOWNLOADS)

        assertEquals(AppSection.HOME, viewModel.state.value.section)
        assertEquals(AppContent.Home, viewModel.state.value.content)
    }

    /**
     * Found on a device: deleting the profile you were using left the app on "opening your
     * catalogue" forever.
     *
     * The profile observation set the stage to CATALOGUE unconditionally, so with no active profile
     * the boot screen waited on a catalogue that had nobody to load for. With nothing left to
     * prepare, boot has to finish so the screen behind it can be reached.
     *
     * This no longer asserts that the profile stays null: a lone profile is now signed in
     * automatically, since asking "who is watching?" with one possible answer is a screen that
     * exists only to be dismissed. What the bug was about — boot reaching READY rather than hanging
     * — is what is asserted, and that holds either way.
     */
    @Test
    fun `boot finishes rather than hanging on a catalogue with nobody to load for`() = runTest {
        val viewModel = createViewModel(FakeCatalogRepository())
        runCurrent()
        assertEquals(
            "A boot stage short of READY holds the loading screen up with nothing to wait for.",
            BootStageUi.READY,
            viewModel.state.value.bootStage,
        )
    }

    @Test
    fun `live without a source keeps live selected and shows a treated state`() = runTest {
        val viewModel = createViewModel(FakeCatalogRepository())
        runCurrent()

        viewModel.selectSection(AppSection.LIVE)

        assertEquals(AppSection.LIVE, viewModel.state.value.section)
        assertEquals(
            AppContent.SectionPlaceholder(AppSection.LIVE),
            viewModel.state.value.content,
        )
    }

    @Test
    fun `movies selects an Xtream source and opens every title with the filter bar in view`() = runTest {
        val local = source("local", SourceType.LOCAL_M3U)
        val xtream = source("xtream", SourceType.XTREAM)
        val repository = FakeCatalogRepository(sources = listOf(local, xtream))
        val viewModel = createViewModel(repository)
        runCurrent()

        viewModel.selectSection(AppSection.MOVIES)
        runCurrent()

        // Skips the raw category grid (Action, 4K, …): Filmes/Séries open straight onto "every
        // title", categoryId null, so the filter bar is already in view instead of behind a tap.
        assertEquals(
            AppContent.Channels(
                sourceId = xtream.id,
                sourceName = xtream.name,
                categoryId = null,
                categoryName = "",
                contentType = CatalogContentType.MOVIE,
            ),
            viewModel.state.value.content,
        )
        // No category grid is fetched for this destination any more, so there is nothing left to
        // assert through observeCategories/lastObservedContentType — the destination assertion
        // above, which already pins contentType = MOVIE, is what carries that check now.
        //
        // The last page request, not the only one: createViewModel's own startup already loads
        // the home rails, which page independently at their own (smaller) size before this test
        // ever calls selectSection. 200 is MainViewModel's own private PAGE_SIZE constant, not
        // reachable from here.
        assertEquals(0 to 200, repository.pageRequests.last())
    }

    @Test
    fun `category counts do not load the complete channel catalog`() = runTest {
        val category =
            Category(
                id = "news",
                sourceId = "local",
                name = "News",
                contentType = CatalogContentType.UNKNOWN,
            )
        val repository =
            FakeCatalogRepository(
                sources = listOf(source("local", SourceType.LOCAL_M3U)),
                categories = listOf(category),
                categoryCounts = mapOf(category.id to 5),
            )
        val viewModel = createViewModel(repository)
        runCurrent()

        viewModel.selectSection(AppSection.LIVE)
        runCurrent()

        assertEquals(0, repository.observeChannelsCalls)
        assertEquals(5, viewModel.state.value.categories.first { it.id == null }.channelCount)
        assertEquals(5, viewModel.state.value.categories.first { it.id == category.id }.channelCount)
    }

    @Test
    fun `named categories keep their own representative artwork`() = runTest {
        // Named: the fourth positional parameter is sortOrder, not contentType.
        val releases = Category("releases", "local", "Lançamentos", contentType = CatalogContentType.MOVIE)
        val action = Category("action", "local", "Ação", contentType = CatalogContentType.MOVIE)
        val repository =
            FakeCatalogRepository(
                sources = listOf(source("local", SourceType.XTREAM)),
                categories = listOf(releases, action),
                categoryCounts = mapOf(releases.id to 4, action.id to 3),
                categoryArtwork =
                    mapOf(
                        releases.id to "https://example.test/releases.jpg",
                        action.id to "https://example.test/action.jpg",
                    ),
            )
        val viewModel = createViewModel(repository)
        runCurrent()

        // Live, not Movies: Movies/Series now skip the category grid entirely and open straight
        // onto every title, so this grid's own artwork logic is only still reachable through Live.
        viewModel.selectSection(AppSection.LIVE)
        runCurrent()

        assertEquals(
            "https://example.test/releases.jpg",
            viewModel.state.value.categories.first { it.id == releases.id }.artworkUrl,
        )
        assertEquals(
            "https://example.test/action.jpg",
            viewModel.state.value.categories.first { it.id == action.id }.artworkUrl,
        )
    }

    /**
     * Found on a device: "Series | Netflix · 1716 itens" opened onto "this source has no
     * compatible channels".
     *
     * A genre chosen in one category stayed in force in the next. Where the new category has no
     * title of that genre the grid narrows to nothing, while the count on the card — which is not
     * filtered — keeps saying there are more than a thousand. The filter describes one list and
     * must not outlive it.
     */
    @Test
    fun `opening a category clears the filter left over from the previous one`() = runTest {
        val repository =
            FakeCatalogRepository(
                pageLoader = { _, _ ->
                    CatalogPage(listOf(catalogChannel("item-0")), offset = 0, totalCount = 1)
                },
            )
        val viewModel = createViewModel(repository)
        runCurrent()
        viewModel.openSource(SourceUi("source", "Synthetic source", 1))
        viewModel.openCategory(CategoryUi(null, "", 1))
        runCurrent()

        viewModel.setCatalogueFilter(CatalogueFilter(genre = "a genre this category alone has"))
        assertTrue(viewModel.state.value.catalogueFilter.isActive)

        // Back to the category list and into another one — the path a user actually takes, and the
        // only one `openCategory` accepts: it reads the open Categories destination.
        viewModel.goBack()
        runCurrent()
        viewModel.openCategory(CategoryUi("other", "Another category", 1))
        runCurrent()

        assertFalse(
            "A stale genre empties the next category while its count still promises items.",
            viewModel.state.value.catalogueFilter.isActive,
        )
        assertEquals(1, viewModel.state.value.visibleChannels.size)
    }

    /**
     * Reported from a device: filter Filmes to 2026, open a film, press back — and the year was
     * gone and the list was back at the top, so browsing a filtered list meant choosing the year
     * again after every title.
     *
     * Two causes, both asserted here. The filter was cleared because the back press ran the same
     * "open a fresh list" path a new category uses. The scroll position was lost because that path
     * also refetched, and it fetches only the first page — so a viewer who had paged in more items
     * came back to a list too short to hold the place they had reached.
     */
    @Test
    fun `returning from a title keeps the filtered list that was left`() = runTest {
        val firstPage =
            (0 until 200).map { catalogChannel("item-$it", CatalogContentType.MOVIE) }
        val finalItem = catalogChannel("item-200", CatalogContentType.MOVIE)
        val repository =
            FakeCatalogRepository(
                pageLoader = { offset, _ ->
                    if (offset == 0) {
                        CatalogPage(firstPage, offset = 0, totalCount = 201)
                    } else {
                        CatalogPage(listOf(finalItem), offset = 200, totalCount = 201)
                    }
                },
                movieDetails =
                    MovieDetails(
                        sourceId = "source",
                        providerMovieId = "item-0",
                        title = "Synthetic feature",
                        plot = "Synthetic plot",
                        cast = "",
                        director = "",
                        genre = "Drama",
                        duration = "01:40:00",
                        releaseDate = "2026-02-12",
                        country = "BR",
                        rating = 8.2,
                        artworkUri = null,
                        backdropUris = emptyList(),
                        youtubeTrailerId = null,
                    ),
            )
        val viewModel = createViewModel(repository)
        runCurrent()
        viewModel.openSource(SourceUi("source", "Synthetic source", 201))
        viewModel.openCategory(CategoryUi(null, "", 201))
        runCurrent()

        // Paged in past the first two hundred, as somebody scrolling a long list does.
        viewModel.loadMoreChannels()
        runCurrent()
        assertEquals(201, viewModel.state.value.channels.size)

        viewModel.setCatalogueFilter(CatalogueFilter(year = 2026))
        assertTrue(viewModel.state.value.catalogueFilter.isActive)

        viewModel.openChannel(firstPage.first().toUi())
        runCurrent()
        assertTrue(
            "The test must reach a details screen; a live item would open the player instead.",
            viewModel.state.value.content is AppContent.MovieDetails,
        )

        assertTrue(viewModel.goBack())
        runCurrent()

        assertTrue(
            "The year was cleared, so the viewer had to choose it again after every title.",
            viewModel.state.value.catalogueFilter.isActive,
        )
        assertEquals(
            "Refetching dropped the pages already loaded, taking the scroll position with them.",
            201,
            viewModel.state.value.channels.size,
        )
    }

    @Test
    fun `channels load in pages of two hundred and append on demand`() = runTest {
        val firstPage = (0 until 200).map { catalogChannel("item-$it") }
        val finalItem = catalogChannel("item-200")
        val repository =
            FakeCatalogRepository(
                pageLoader = { offset, _ ->
                    if (offset == 0) {
                        CatalogPage(firstPage, offset = 0, totalCount = 201)
                    } else {
                        CatalogPage(listOf(finalItem), offset = 200, totalCount = 201)
                    }
                },
            )
        val viewModel = createViewModel(repository)
        runCurrent()
        viewModel.openSource(SourceUi("source", "Synthetic source", 201))
        viewModel.openCategory(CategoryUi(null, "", 201))
        runCurrent()

        assertEquals(200, viewModel.state.value.channels.size)
        assertTrue(viewModel.state.value.hasMoreChannels)

        viewModel.loadMoreChannels()
        runCurrent()

        assertEquals(201, viewModel.state.value.channels.size)
        assertFalse(viewModel.state.value.hasMoreChannels)
        assertEquals(listOf(0 to 200, 200 to 200), repository.pageRequests)
    }

    @Test
    fun `retry after append failure continues from the loaded item count`() = runTest {
        val firstPage = (0 until 200).map { catalogChannel("item-$it") }
        val finalItem = catalogChannel("item-200")
        var appendAttempts = 0
        val repository =
            FakeCatalogRepository(
                pageLoader = { offset, _ ->
                    if (offset == 0) {
                        CatalogPage(firstPage, offset = 0, totalCount = 201)
                    } else {
                        appendAttempts += 1
                        if (appendAttempts == 1) {
                            error("Synthetic append failure")
                        }
                        CatalogPage(listOf(finalItem), offset = 200, totalCount = 201)
                    }
                },
            )
        val viewModel = createViewModel(repository)
        runCurrent()
        viewModel.openSource(SourceUi("source", "Synthetic source", 201))
        viewModel.openCategory(CategoryUi(null, "", 201))
        runCurrent()

        viewModel.loadMoreChannels()
        runCurrent()

        assertEquals(200, viewModel.state.value.channels.size)
        assertTrue(viewModel.state.value.hasCatalogError)

        viewModel.retryCatalog()
        runCurrent()

        assertEquals(201, viewModel.state.value.channels.size)
        assertFalse(viewModel.state.value.hasCatalogError)
        assertEquals(listOf(0 to 200, 200 to 200, 200 to 200), repository.pageRequests)
    }

    @Test
    fun `playback resolves the selected item only immediately before player`() = runTest {
        val unresolved = catalogChannel("live-1")
        val resolved = unresolved.copy(streamUri = "https://stream.example/live-1.m3u8")
        val repository = FakeCatalogRepository(resolvedChannels = mapOf(resolved.id to resolved))
        val viewModel = createViewModel(repository)
        runCurrent()

        viewModel.openChannel(unresolved.toUi())
        assertTrue(viewModel.state.value.isResolvingPlayback)
        runCurrent()

        val player = viewModel.state.value.content as AppContent.Player
        assertEquals(resolved.streamUri, player.channel.streamUrl)
        assertEquals(listOf(resolved.id), repository.getChannelRequests)
        assertFalse(player.channel.toString().contains(resolved.streamUri))
    }

    @Test
    fun `series details expose metadata while episode uri stays out of episode ui`() = runTest {
        val seriesItem =
            catalogChannel(
                id = "series-1",
                contentType = CatalogContentType.SERIES,
                providerItemId = "provider-series-1",
            )
        val episode =
            Episode(
                id = "episode-1",
                sourceId = seriesItem.sourceId,
                providerEpisodeId = "provider-episode-1",
                title = "Synthetic episode",
                seasonNumber = 1,
                episodeNumber = 2,
                artworkUri = null,
                containerExtension = "mp4",
            )
        val resolvedEpisode =
            Channel(
                id = episode.id,
                sourceId = episode.sourceId,
                name = episode.title,
                streamUri = "https://stream.example/episode-1.mp4?token=synthetic-secret",
                contentType = CatalogContentType.EPISODE,
                providerItemId = episode.providerEpisodeId,
            )
        val repository =
            FakeCatalogRepository(
                seriesDetails =
                    SeriesDetails(
                        sourceId = seriesItem.sourceId,
                        providerSeriesId = requireNotNull(seriesItem.providerItemId),
                        title = "Synthetic series",
                        plot = "Synthetic plot",
                        artworkUri = null,
                        episodes = listOf(episode),
                    ),
                resolvedEpisodes = mapOf(episode.id to resolvedEpisode),
            )
        val viewModel = createViewModel(repository)
        runCurrent()

        viewModel.openChannel(seriesItem.toUi())
        runCurrent()

        assertTrue(viewModel.state.value.content is AppContent.SeriesDetails)
        val episodeUi = viewModel.state.value.seriesDetails!!.episodes.single()
        assertFalse(episode.toString().contains(resolvedEpisode.streamUri))
        assertFalse(episodeUi.toString().contains(resolvedEpisode.streamUri))

        viewModel.openEpisode(episodeUi)
        assertTrue(viewModel.state.value.isResolvingPlayback)
        assertTrue(viewModel.state.value.content is AppContent.SeriesDetails)
        runCurrent()

        val player = viewModel.state.value.content as AppContent.Player
        assertEquals(resolvedEpisode.streamUri, player.channel.streamUrl)
        assertEquals(listOf(episode.id), repository.resolveEpisodeRequests.map(Episode::id))
        assertFalse(player.channel.toString().contains(resolvedEpisode.streamUri))
    }

    @Test
    fun `movie opens rich details before resolving playback`() = runTest {
        val movie =
            catalogChannel(
                id = "movie-1",
                contentType = CatalogContentType.MOVIE,
                providerItemId = "provider-movie-1",
            )
        val resolved = movie.copy(streamUri = "https://stream.example/movie-1.mkv")
        val repository =
            FakeCatalogRepository(
                resolvedChannels = mapOf(movie.id to resolved),
                movieDetails =
                    MovieDetails(
                        sourceId = movie.sourceId,
                        providerMovieId = requireNotNull(movie.providerItemId),
                        title = "Synthetic feature",
                        plot = "Synthetic plot",
                        cast = "Actor One",
                        director = "Director One",
                        genre = "Drama",
                        duration = "01:40:00",
                        releaseDate = "2026-02-12",
                        country = "BR",
                        rating = 8.2,
                        artworkUri = "https://images.example/poster.jpg",
                        backdropUris = listOf("https://images.example/backdrop.jpg"),
                        youtubeTrailerId = "AbCdEf12345",
                    ),
            )
        val viewModel = createViewModel(repository)
        runCurrent()

        viewModel.openChannel(movie.toUi())
        runCurrent()

        assertTrue(viewModel.state.value.content is AppContent.MovieDetails)
        assertEquals("Synthetic plot", viewModel.state.value.movieDetails?.plot)
        assertTrue(repository.getChannelRequests.isEmpty())

        viewModel.playSelectedMovie()
        runCurrent()

        val player = viewModel.state.value.content as AppContent.Player
        assertEquals(resolved.streamUri, player.channel.streamUrl)
        assertEquals(listOf(movie.id), repository.getChannelRequests)
    }

    @Test
    fun `Xtream import forwards synthetic fields and reports a sanitized success`() = runTest {
        val repository = FakeCatalogRepository()
        val viewModel = createViewModel(repository)
        val observedStages = mutableListOf<XtreamImportStageUi?>()
        repository.afterXtreamProgress = {
            observedStages += viewModel.state.value.xtreamImportStage
        }
        runCurrent()

        viewModel.importXtreamSource(
            displayName = "Synthetic provider",
            serverUrl = "https://provider.example",
            username = "synthetic_user",
            password = "synthetic_password",
        )
        runCurrent()

        val request = requireNotNull(repository.lastXtreamRequest)
        assertEquals("Synthetic provider", request.displayName)
        assertFalse(request.toString().contains("synthetic_user"))
        assertFalse(request.toString().contains("synthetic_password"))
        assertEquals(SourceImportMethod.XTREAM, viewModel.state.value.lastImportMethod)
        assertEquals(6, viewModel.state.value.lastImportedChannelCount)
        assertEquals(1L, viewModel.state.value.importSuccessVersion)
        assertEquals(XtreamImportStageUi.entries.toList(), observedStages)
    }

    @Test
    fun `cancelling Xtream import clears progress without reporting an error`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeCatalogRepository(xtreamImportGate = gate)
        val logger = RecordingLogger()
        val viewModel = createViewModel(repository, logger)
        runCurrent()

        viewModel.importXtreamSource(
            displayName = "Synthetic provider",
            serverUrl = "https://provider.example",
            username = "synthetic_user",
            password = "synthetic_password",
        )
        runCurrent()

        assertTrue(viewModel.state.value.isImporting)
        assertEquals(XtreamImportStageUi.SAVING, viewModel.state.value.xtreamImportStage)

        viewModel.cancelXtreamImport()
        runCurrent()

        assertFalse(viewModel.state.value.isImporting)
        assertFalse(viewModel.state.value.hasImportError)
        assertNull(viewModel.state.value.xtreamImportStage)
        assertEquals(0L, viewModel.state.value.importSuccessVersion)
        assertEquals(0, logger.errorCount)
    }

    @Test
    fun `story remembers its item and back restores home`() = runTest {
        val viewModel = createViewModel(FakeCatalogRepository())
        runCurrent()

        viewModel.openStory("editorial-feature")

        assertEquals(
            AppContent.Story(itemId = "editorial-feature"),
            viewModel.state.value.content,
        )
        assertEquals("editorial-feature", viewModel.state.value.lastFocusedHomeItemId)

        assertTrue(viewModel.goBack())
        assertEquals(AppSection.HOME, viewModel.state.value.section)
        assertEquals(AppContent.Home, viewModel.state.value.content)
    }

    /**
     * Redeeming has to work while the licence is *valid*, which is where it silently did nothing.
     *
     * `redeemLicense` began with `license as? Blocked ?: return`, so during the seven-day trial —
     * the commonest moment to type a key, and the whole reason the Settings card has a field — the
     * key never left the phone. Reported as "I typed my key, nothing happened, still seven days".
     *
     * The fake below reports a trial, exactly as the real service does in that situation, and
     * records whether the server was reached at all.
     */
    @Test
    fun `a key is sent to the server during the trial, not silently dropped`() = runTest {
        val licence = RecordingLicenseService()
        val viewModel = createViewModel(FakeCatalogRepository(), licenseService = licence)
        runCurrent()

        viewModel.redeemLicense("PGRF-AWH5-5ZZK")
        runCurrent()

        assertEquals(listOf("PGRF-AWH5-5ZZK"), licence.redeemed)
    }

    /** Whatever the server answers has to reach the screen; silence is what the report was about. */
    @Test
    fun `the redemption outcome is published for the settings card`() = runTest {
        val licence = RecordingLicenseService()
        val viewModel = createViewModel(FakeCatalogRepository(), licenseService = licence)
        runCurrent()

        viewModel.redeemLicense("SOME-KEY-HERE")
        runCurrent()

        assertEquals(
            RedemptionUi.Failed(RedeemFailure.ALREADY_USED),
            viewModel.state.value.redemption,
        )
    }

    @Test
    fun `a blank key is not sent at all`() = runTest {
        val licence = RecordingLicenseService()
        val viewModel = createViewModel(FakeCatalogRepository(), licenseService = licence)
        runCurrent()

        viewModel.redeemLicense("   ")
        runCurrent()

        assertEquals(emptyList<String>(), licence.redeemed)
    }

    @Test
    fun `a playlist the seller assigned is imported automatically after a valid licence check`() = runTest {
        val assigned = AssignedPlaylist("http://provedor.example:8080", "cliente1", "senha1")
        val licence = RecordingLicenseService(assignedPlaylist = assigned)
        val repository = FakeCatalogRepository()
        val viewModel = createViewModel(repository, licenseService = licence)
        // These fixtures start with onboarding not yet accepted, which is what normally triggers
        // the first licence check; a real boot with onboarding already accepted takes the same path
        // through the same private checkLicense(), so calling the public entry point it shares with
        // "try again" on the licence gate screen exercises the same code this feature hooks into.
        viewModel.refreshLicense()
        advanceUntilIdle()

        assertEquals(1, repository.importXtreamCallCount)
        assertEquals("http://provedor.example:8080", repository.lastXtreamRequest?.serverUrl)
        assertEquals("cliente1", repository.lastXtreamRequest?.username)
        assertEquals("senha1", repository.lastXtreamRequest?.password)
        assertEquals(listOf(null), licence.confirmedFailureCodes)
    }

    @Test
    fun `an already-applied assignment is not imported again on the next boot`() = runTest {
        val assigned = AssignedPlaylist("http://provedor.example:8080", "cliente1", "senha1")
        val licence = RecordingLicenseService(assignedPlaylist = assigned)
        val repository =
            FakeCatalogRepository(
                existingXtreamSource = Triple("http://provedor.example:8080", "cliente1", "senha1"),
            )
        val viewModel = createViewModel(repository, licenseService = licence)
        viewModel.refreshLicense()
        advanceUntilIdle()

        assertEquals(0, repository.importXtreamCallCount)
        // Still confirmed: the customer already has it, so the panel should stop showing it pending.
        assertEquals(listOf(null), licence.confirmedFailureCodes)
    }

    @Test
    fun `no assignment on the server leaves an ordinary boot untouched`() = runTest {
        val licence = RecordingLicenseService(assignedPlaylist = null)
        val repository = FakeCatalogRepository()
        val viewModel = createViewModel(repository, licenseService = licence)
        viewModel.refreshLicense()
        advanceUntilIdle()

        assertEquals(0, repository.importXtreamCallCount)
        assertEquals(emptyList<String?>(), licence.confirmedFailureCodes)
    }

    @Test
    fun `a seller-supplied TMDb and OMDb key is applied when the customer has none`() = runTest {
        val assigned =
            AssignedPlaylist(
                "http://provedor.example:8080", "cliente1", "senha1",
                metadataKey = "tmdb-key-from-seller",
                criticsKey = "omdb-key-from-seller",
            )
        val licence = RecordingLicenseService(assignedPlaylist = assigned)
        val keyStore = RecordingMetadataKeyStore()
        val viewModel =
            createViewModel(FakeCatalogRepository(), licenseService = licence, metadataKeyStore = keyStore)
        viewModel.refreshLicense()
        advanceUntilIdle()

        assertEquals("tmdb-key-from-seller", keyStore.readShared())
        assertEquals("omdb-key-from-seller", keyStore.readCritics())
    }

    /**
     * A delivery that carries only a key.
     *
     * A seller whose customer already has a working list should be able to hand over a TMDb key
     * without retyping the address and password. Reported on the panel as "nao deixa eu enviar so
     * api tmdb preciso enviar tudo".
     */
    @Test
    fun `a delivery with only a key applies the key and imports nothing`() = runTest {
        val assigned =
            AssignedPlaylist(
                serverUrl = null,
                username = null,
                password = null,
                metadataKey = "tmdb-key-from-seller",
            )
        val licence = RecordingLicenseService(assignedPlaylist = assigned)
        val repository = FakeCatalogRepository()
        val keyStore = RecordingMetadataKeyStore()
        val viewModel = createViewModel(repository, licenseService = licence, metadataKeyStore = keyStore)
        viewModel.refreshLicense()
        advanceUntilIdle()

        assertEquals("tmdb-key-from-seller", keyStore.readShared())
        // No connection arrived, so there is nothing to import — and importing a blank source
        // would leave an unusable row the customer would have to delete.
        assertEquals(0, repository.importXtreamCallCount)
        // Still confirmed, or the panel keeps showing a delivery that was in fact applied.
        assertEquals(listOf(null), licence.confirmedFailureCodes)
    }

    /** The name the seller chose is what the customer sees, rather than the standing label. */
    @Test
    fun `the list is named by the seller when they chose a name`() = runTest {
        val assigned =
            AssignedPlaylist(
                "http://provedor.example:8080", "cliente1", "senha1",
                listLabel = "Plano Familia",
            )
        val licence = RecordingLicenseService(assignedPlaylist = assigned)
        val repository = FakeCatalogRepository()
        val viewModel = createViewModel(repository, licenseService = licence)
        viewModel.refreshLicense()
        advanceUntilIdle()

        assertEquals("Plano Familia", repository.lastXtreamRequest?.displayName)
    }

    @Test
    fun `a seller-supplied key never overwrites one the customer already set themselves`() = runTest {
        val assigned =
            AssignedPlaylist(
                "http://provedor.example:8080", "cliente1", "senha1",
                metadataKey = "tmdb-key-from-seller",
                criticsKey = "omdb-key-from-seller",
            )
        val licence = RecordingLicenseService(assignedPlaylist = assigned)
        val keyStore = RecordingMetadataKeyStore(shared = "customers-own-tmdb-key")
        val viewModel =
            createViewModel(FakeCatalogRepository(), licenseService = licence, metadataKeyStore = keyStore)
        viewModel.refreshLicense()
        advanceUntilIdle()

        assertEquals("customers-own-tmdb-key", keyStore.readShared())
        // No key of their own for OMDb, so the seller's still fills the gap.
        assertEquals("omdb-key-from-seller", keyStore.readCritics())
    }

    @Test
    fun `selecting a profile without sources opens source connection`() = runTest {
        val viewModel = createViewModel(FakeCatalogRepository())
        runCurrent()

        viewModel.selectProfile(viewModel.state.value.profiles.single().id)
        runCurrent()

        assertEquals(AppSection.SOURCES, viewModel.state.value.section)
        assertEquals(AppContent.Sources, viewModel.state.value.content)
    }

    @Test
    fun `selecting a profile with a source opens home`() = runTest {
        val repository = FakeCatalogRepository(sources = listOf(source("local", SourceType.LOCAL_M3U)))
        val viewModel = createViewModel(repository)
        runCurrent()

        viewModel.selectProfile(viewModel.state.value.profiles.single().id)
        runCurrent()

        assertEquals(AppSection.HOME, viewModel.state.value.section)
        assertEquals(AppContent.Home, viewModel.state.value.content)
    }

    /**
     * One profile is not a choice.
     *
     * A fresh install creates a single "Meu perfil", so the picker was appearing on every launch to
     * ask a question with one possible answer. With nobody stored as active and exactly one profile
     * present, that profile becomes the active one and the app opens straight into the catalogue.
     */
    @Test
    fun `a lone profile is signed in without showing the picker`() = runTest {
        val repository = FakeCatalogRepository(sources = listOf(source("local", SourceType.LOCAL_M3U)))
        val viewModel = createViewModel(repository)
        runCurrent()

        assertEquals(
            "The default install has exactly one profile; this asserts that case.",
            1,
            viewModel.state.value.profiles.size,
        )
        assertEquals(
            "With one profile there is nothing to choose, so it is already the active one.",
            viewModel.state.value.profiles.single().id,
            viewModel.state.value.activeProfile?.id,
        )
    }

    private fun TestScope.createViewModel(
        repository: FakeCatalogRepository,
        logger: AppLogger = NoOpLogger,
        isTelevision: Boolean = false,
        licenseService: AndroidLicenseService = FakeLicenseService,
        metadataKeyStore: MetadataKeyStore = FakeMetadataKeyStore,
    ): MainViewModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        // Only the ui-mode flag is read, by the offline capability gate. A throwing provider was
        // fine until that gate started consulting the device form factor; a phone-shaped
        // Configuration keeps these assertions on the branch a phone actually takes.
        val configuration =
            Configuration().apply {
                uiMode =
                    if (isTelevision) {
                        Configuration.UI_MODE_TYPE_TELEVISION
                    } else {
                        Configuration.UI_MODE_TYPE_NORMAL
                    }
            }
        val contextProvider =
            Provider<Context> {
                object : ContextWrapper(null) {
                    // Resources itself cannot be constructed off-device, and the gate only reads
                    // the configuration, so the accessor is what gets stubbed.
                    override fun getResources(): Resources =
                        object : Resources(null, null, null) {
                            override fun getConfiguration(): Configuration = configuration
                        }
                }
            }
        return MainViewModel(
            contextProvider = contextProvider,
            catalogRepository = repository,
            // Nothing collects a rate in these assertions; a real one is cheap and needs no setup.
            downloadRateReporter = DownloadRateReporter(),
            onboardingPreferences = FakeOnboardingPreferences,
            userLibraryRepository =
                UserLibraryRepository(
                    FakeProfileDao(),
                    FakeFavoriteDao(),
                    FakeReminderDao(),
                    NoPlaybackProgressDao,
                    NoSeriesWatchDao,
                    dispatcher,
                ),
            reminderRepository = ReminderRepository(FakeReminderDao(), dispatcher),
            // Records nothing: these assertions are about navigation, and the real scheduler would
            // need an initialised WorkManager to answer at all.
            reminderScheduler = NoReminderScheduling,
            playbackSessionPreferences = NoPlaybackSessionStore,
            notificationCentrePreferences = NoNotificationCentreStore,
            cacheSettings = NoCacheSettingsStore,
            artworkCache = NoArtworkCache,
            // The manager resolves storage lazily, so these navigation assertions never touch it.
            downloadManager = AndroidDownloadManager(contextProvider, OkHttpClient(), dispatcher),
            licenseService = licenseService,
            metadataKeyStore = metadataKeyStore,
            // Builds no catalogue: FakeMetadataKeyStore has no key, so every discovery call is a
            // no-op and these navigation assertions never reach the network.
            streamingDiscoveryRepository = StreamingDiscoveryRepository(OkHttpClient(), NoShelfCache, dispatcher),
            okHttpClient = OkHttpClient(),
            // Never loaded in these assertions: the badge catalogue needs a TMDb key and a
            // network, and navigation does not depend on a logo having arrived.
            providerLogoCatalogue = ProviderLogoCatalogue(OkHttpClient()),
            playbackProgressRepository = FakePlaybackProgressRepository,
            // Empty fakes: nothing hidden and no PIN, which is what these navigation assertions
            // assume. The DataStore-backed implementations cannot start on a plain JVM context.
            catalogueGuardPreferences = FakeCatalogueGuard,
            // Off, which is the default and what these navigation assertions assume.
            sourceMergeSettings = NoSourceMerge,
            subtitlePreferences = FakeSubtitleSettings,
            bannerSoundPreferences = FakeBannerSoundSettings(),
            logger = logger,
            // A real tester over fakes: these assertions never open the diagnostics screen, and a
            // null context degrades its local readings to "unknown" exactly as a device with no
            // connectivity service does.
            connectionTester =
                ConnectionTester(
                    context = null,
                    probe = NoNetworkProbe,
                    sourceConnectionStore = NoStoredCredentials,
                    ioDispatcher = dispatcher,
                ),
            ioDispatcher = dispatcher,
        )
    }
}

/** Answers nothing, because these tests never ask. */
private object NoNetworkProbe : ProviderProbe {
    override fun transfer(
        credentials: XtreamCredentials,
        budgetMillis: Long,
    ): Pair<Long, Long>? = null

    override fun latency(
        credentials: XtreamCredentials,
        attempts: Int,
    ): List<Int> = emptyList()
}

private object NoStoredCredentials : SourceConnectionStore {
    override fun saveXtream(
        sourceId: String,
        credentials: XtreamCredentials,
    ) = Unit

    override fun readXtream(sourceId: String): XtreamCredentials? = null

    override fun saveStalker(
        sourceId: String,
        credentials: StalkerCredentials,
    ) = Unit

    override fun readStalker(sourceId: String): StalkerCredentials? = null

    override fun remove(sourceId: String) = Unit
}

/** Merging off, which is the default a fresh install has. */
private data object NoSourceMerge : SourceMergeSettings {
    override val mergeEverySource = flowOf(false)

    override suspend fun setMergeEverySource(enabled: Boolean) = Unit
}

/** Nothing hidden, nothing locked, no PIN: the state a fresh install is in. */
private data object FakeCatalogueGuard : CatalogueGuard {
    override val hiddenCategoryIds = flowOf(emptySet<String>())
    override val parentalLock = flowOf(ParentalLock(lockAdultCategories = false))
    override val hasPin = flowOf(false)

    override suspend fun setCategoryHidden(categoryId: String, hidden: Boolean) = Unit

    override suspend fun setCategoryLocked(categoryId: String, locked: Boolean) = Unit

    override suspend fun setLockAdultCategories(locked: Boolean) = Unit

    override suspend fun setPin(newPin: String, currentPin: String?): Boolean = false

    override suspend fun clearPin(currentPin: String): Boolean = false

    override suspend fun checkPin(candidate: String): Boolean = false
}

private data object FakeSubtitleSettings : SubtitleSettings {
    override val presentation = flowOf(SubtitlePresentation())

    override suspend fun save(presentation: SubtitlePresentation) = Unit
}

/**
 * Remembers what it was told, rather than answering a fixed no.
 *
 * A store that always says "off" would let a toggle that saves nothing pass every test — the state
 * would read false before and after, exactly as if it worked and the viewer had turned it back.
 */
private class FakeBannerSoundSettings : BannerSoundSettings {
    private val state = MutableStateFlow(false)

    override val soundOn: Flow<Boolean> = state

    override suspend fun setSoundOn(enabled: Boolean) {
        state.value = enabled
    }
}

private data object FakeLicenseService : AndroidLicenseService {
    override fun check(now: Instant): AndroidLicenseStatus =
        AndroidLicenseStatus(
            decision = LicenseDecision.Allowed(7.days, isTrial = true),
            deviceId = "TEST-TEST-TEST",
            offline = false,
            clockSuspect = false,
        )

    // These navigation tests never redeem; the reason is arbitrary but has to be a real one.
    override fun redeem(key: String, now: Instant): RedeemOutcome =
        RedeemOutcome.Failed(RedeemFailure.UNREACHABLE)
}

/**
 * Reports a live trial and records every key it is asked to redeem.
 *
 * A trial is the state the silent-drop bug lived in, so it is the state worth testing from. The
 * refusal it answers with is arbitrary — what matters is that the call happened at all and that the
 * answer reaches the state.
 */
private class RecordingLicenseService(
    private val assignedPlaylist: AssignedPlaylist? = null,
) : AndroidLicenseService {
    val redeemed = mutableListOf<String>()
    val confirmedFailureCodes = mutableListOf<String?>()

    override fun check(now: Instant): AndroidLicenseStatus =
        AndroidLicenseStatus(
            decision = LicenseDecision.Allowed(7.days, isTrial = true),
            deviceId = "TEST-TEST-TEST",
            offline = false,
            clockSuspect = false,
        )

    override fun redeem(key: String, now: Instant): RedeemOutcome {
        redeemed += key
        return RedeemOutcome.Failed(RedeemFailure.ALREADY_USED)
    }

    override fun fetchAssignedPlaylist(): AssignedPlaylist? = assignedPlaylist

    override fun confirmAssignedPlaylist(failureCode: String?) {
        confirmedFailureCodes += failureCode
    }
}

private data object FakeMetadataKeyStore : MetadataKeyStore {
    override fun save(profileId: String, apiKey: String) = Unit
    override fun read(profileId: String): String? = null
    override fun isConfigured(profileId: String): Boolean = false
}

/** Records what was saved, and lets a test seed a key the customer supposedly typed in already. */
private class RecordingMetadataKeyStore(
    private var shared: String? = null,
    private var critics: String? = null,
) : MetadataKeyStore {
    override fun save(profileId: String, apiKey: String) = Unit
    override fun read(profileId: String): String? = null
    override fun isConfigured(profileId: String): Boolean = false
    override fun saveShared(apiKey: String) { shared = apiKey }
    override fun readShared(): String? = shared
    override fun saveCritics(apiKey: String) { critics = apiKey }
    override fun readCritics(): String? = critics
}

private data object FakePlaybackProgressRepository : PlaybackProgressRepository {
    override fun find(identity: PlaybackProgressIdentity): PlaybackProgress? = null
    override fun save(progress: PlaybackProgress) = Unit
    override fun remove(identity: PlaybackProgressIdentity) = Unit
    override fun continueWatching(profileId: String, limit: Int): List<PlaybackProgress> = emptyList()
}

private class FakeCatalogRepository(
    private val sources: List<Source> = emptyList(),
    private val categories: List<Category> = emptyList(),
    private val categoryCounts: Map<String?, Int> = emptyMap(),
    private val categoryArtwork: Map<String, String> = emptyMap(),
    private val pageLoader: (offset: Int, limit: Int) -> CatalogPage =
        { offset, _ -> CatalogPage(emptyList(), offset, 0) },
    private val resolvedChannels: Map<String, Channel> = emptyMap(),
    private val resolvedEpisodes: Map<String, Channel> = emptyMap(),
    private val seriesDetails: SeriesDetails? = null,
    private val movieDetails: MovieDetails? = null,
    private val xtreamImportGate: CompletableDeferred<Unit>? = null,
    private val existingXtreamSource: Triple<String, String, String>? = null,
) : CatalogRepository {
    var lastObservedContentType: CatalogContentType? = null
    var observeChannelsCalls: Int = 0
    val pageRequests = mutableListOf<Pair<Int, Int>>()
    val getChannelRequests = mutableListOf<String>()
    val resolveEpisodeRequests = mutableListOf<Episode>()
    var lastXtreamRequest: XtreamImportRequest? = null
    var afterXtreamProgress: ((XtreamImportStage) -> Unit)? = null
    var importXtreamCallCount: Int = 0

    override fun observeSources(): Flow<List<Source>> = flowOf(sources)

    override fun observeCategories(
        sourceId: String,
        contentType: CatalogContentType?,
    ): Flow<List<Category>> {
        lastObservedContentType = contentType
        return flowOf(categories)
    }

    override fun observeCategoryItemCounts(
        sourceId: String,
        contentType: CatalogContentType?,
    ): Flow<Map<String?, Int>> = flowOf(categoryCounts)

    override fun observeCategoryArtwork(
        sourceId: String,
        contentType: CatalogContentType?,
    ): Flow<Map<String, String>> = flowOf(categoryArtwork)

    override fun observeChannels(
        sourceId: String,
        categoryId: String?,
        contentType: CatalogContentType?,
    ): Flow<List<Channel>> {
        observeChannelsCalls += 1
        return emptyFlow()
    }

    override suspend fun loadChannelsPage(
        sourceId: String,
        categoryId: String?,
        contentType: CatalogContentType?,
        offset: Int,
        limit: Int,
    ): CatalogPage {
        pageRequests += offset to limit
        return pageLoader(offset, limit)
    }

    // No local library in these navigation fixtures, so nothing is ever claimed as owned.
    override suspend fun findLibraryCandidates(titleFragment: String, limit: Int): List<Channel> =
        emptyList()

    override suspend fun getChannel(id: String): Channel? {
        getChannelRequests += id
        return resolvedChannels[id]
    }

    override suspend fun resolveEpisode(episode: Episode): Channel {
        resolveEpisodeRequests += episode
        return requireNotNull(resolvedEpisodes[episode.id]) {
            "Synthetic resolved episode was not configured."
        }
    }

    override suspend fun importPlaylist(
        displayName: String,
        inputStream: InputStream,
    ): PlaylistImportResult = error("Not used by these tests")

    override suspend fun importXtream(
        request: XtreamImportRequest,
        onProgress: (XtreamImportStage) -> Unit,
    ): XtreamImportResult {
        importXtreamCallCount += 1
        lastXtreamRequest = request
        XtreamImportStage.entries.forEach { stage ->
            onProgress(stage)
            afterXtreamProgress?.invoke(stage)
        }
        xtreamImportGate?.await()
        return XtreamImportResult(
            sourceId = "synthetic-source",
            liveCount = 2,
            movieCount = 2,
            seriesCount = 2,
            categoryCount = 3,
            skippedItemCount = 0,
        )
    }

    override suspend fun hasXtreamSource(
        serverUrl: String,
        username: String,
        password: String,
    ): Boolean = existingXtreamSource == Triple(serverUrl, username, password)

    override suspend fun loadSeriesDetails(
        sourceId: String,
        providerSeriesId: String,
    ): SeriesDetails =
        requireNotNull(seriesDetails) {
            "Synthetic series details were not configured."
        }

    override suspend fun loadMovieDetails(
        sourceId: String,
        providerMovieId: String,
    ): MovieDetails =
        requireNotNull(movieDetails) {
            "Synthetic movie details were not configured."
        }
}

private fun source(
    id: String,
    type: SourceType,
): Source =
    Source(
        id = id,
        name = "Synthetic $id",
        type = type,
        channelCount = 1,
    )

private fun catalogChannel(
    id: String,
    contentType: CatalogContentType = CatalogContentType.LIVE,
    providerItemId: String? = id,
): Channel =
    Channel(
        id = id,
        sourceId = "source",
        name = "Synthetic $id",
        streamUri = "xtream://unresolved/$id",
        contentType = contentType,
        providerItemId = providerItemId,
    )

private fun Channel.toUi(): ChannelUi =
    ChannelUi(
        id = id,
        sourceId = sourceId,
        name = name,
        categoryName = null,
        logoUrl = null,
        contentType = contentType,
        providerItemId = providerItemId,
    )

private data object FakeOnboardingPreferences : OnboardingPreferences {
    override val accepted: Flow<Boolean> = flowOf(false)
    override val activeProfileId: Flow<String?> = flowOf(null)

    override suspend fun acceptLegalNotice() = Unit

    override suspend fun selectProfile(profileId: String?) = Unit
}

private class FakeProfileDao : ProfileDao {
    private val profiles = MutableStateFlow<List<ProfileEntity>>(emptyList())

    override fun observeAll(): Flow<List<ProfileEntity>> = profiles
    override suspend fun getById(id: String): ProfileEntity? = profiles.value.firstOrNull { it.id == id }
    override suspend fun count(): Int = profiles.value.size
    override suspend fun maxSortOrder(): Int = profiles.value.maxOfOrNull(ProfileEntity::sortOrder) ?: -1
    override suspend fun upsert(profile: ProfileEntity) {
        profiles.value = (profiles.value.filterNot { it.id == profile.id } + profile).sortedBy(ProfileEntity::sortOrder)
    }
    override suspend fun delete(id: String) {
        profiles.value = profiles.value.filterNot { it.id == id }
    }
}

private class FakeFavoriteDao : FavoriteDao {
    override fun observeIds(profileId: String): Flow<List<String>> = flowOf(emptyList())
    override suspend fun loadChannels(profileId: String, limit: Int): List<ChannelEntity> = emptyList()
    override suspend fun add(favorite: FavoriteEntity) = Unit
    override suspend fun remove(profileId: String, channelId: String) = Unit
    override suspend fun removeAllForProfile(profileId: String) = Unit
}

private data object NoOpLogger : AppLogger {
    override fun debug(tag: String, message: String) = Unit

    override fun info(tag: String, message: String) = Unit

    override fun warn(tag: String, message: String) = Unit

    override fun error(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) = Unit
}

private class RecordingLogger : AppLogger {
    var errorCount: Int = 0

    override fun debug(tag: String, message: String) = Unit

    override fun info(tag: String, message: String) = Unit

    override fun warn(tag: String, message: String) = Unit

    override fun error(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        errorCount += 1
    }
}

/** In-memory reminders, enough for the view model to observe and toggle against. */
/**
 * Nothing watched, ever.
 *
 * Only reached when the library repository checks whether the automatic first profile was used;
 * these assertions are about navigation and never write progress.
 */
private data object NoPlaybackProgressDao : PlaybackProgressDao {
    override fun find(
        profileId: String,
        sourceId: String,
        contentId: String,
        contentType: String,
    ): PlaybackProgressEntity? = null

    override fun upsert(entity: PlaybackProgressEntity) = Unit

    override fun remove(profileId: String, sourceId: String, contentId: String, contentType: String) = Unit

    override fun continueWatching(profileId: String, limit: Int): List<PlaybackProgressEntity> = emptyList()

    override fun history(profileId: String, limit: Int): List<PlaybackProgressEntity> = emptyList()
}

private class FakeReminderDao : ReminderDao {
    private val rows = MutableStateFlow<List<ReminderEntity>>(emptyList())

    override fun observeForProfile(profileId: String): Flow<List<ReminderEntity>> =
        rows.map { all -> all.filter { it.profileId == profileId } }

    override suspend fun forProfile(profileId: String): List<ReminderEntity> =
        rows.value.filter { it.profileId == profileId }

    override suspend fun upsert(reminder: ReminderEntity) {
        rows.value =
            rows.value.filterNot {
                it.profileId == reminder.profileId && it.contentKey == reminder.contentKey
            } + reminder
    }

    override suspend fun remove(profileId: String, contentKey: String) {
        rows.value =
            rows.value.filterNot { it.profileId == profileId && it.contentKey == contentKey }
    }

    override suspend fun isMarked(profileId: String, contentKey: String): Boolean =
        rows.value.any { it.profileId == profileId && it.contentKey == contentKey }

    override suspend fun all(): List<ReminderEntity> = rows.value
}

/** Accepts every scheduling call and does nothing, so no WorkManager is needed. */
/**
 * Never has a session to restore, and records none.
 *
 * These assertions are about navigation, and a stored session would reopen a player on top of
 * whatever destination each test just asserted.
 */
private data object NoPlaybackSessionStore : PlaybackSessionStore {
    override suspend fun current(now: Long): PlaybackSession? = null

    override suspend fun remember(channelId: String, profileId: String, now: Long) = Unit

    override suspend fun clear() = Unit
}

private data object NoReminderScheduling : ReminderScheduling {
    // The stored default, so a view model built on this fake reports the same hour the real
    // preference store would before anything has been written to it.
    override val schedule: Flow<ReminderSchedule> =
        flowOf(ReminderSchedule(notify = true, time = LocalTime.of(ReminderPolicy.DEFAULT_HOUR, 0)))

    override suspend fun sync() = Unit

    override suspend fun setTime(hour: Int, minute: Int) = Unit

    override suspend fun setNotify(notify: Boolean) = Unit
}

/** Follows nothing: these assertions never exercise the new-episode notice. */
private data object NoSeriesWatchDao : SeriesWatchDao {
    override suspend fun find(profileId: String, channelId: String): SeriesWatchEntity? = null

    override suspend fun upsert(state: SeriesWatchEntity) = Unit

    override suspend fun all(): List<SeriesWatchEntity> = emptyList()

    override suspend fun remove(profileId: String, channelId: String) = Unit
}

/** An empty bell that records nothing: these assertions never open it. */
private data object NoNotificationCentreStore : NotificationCentreStore {
    override fun observe(profileId: String): Flow<NotificationCentre> = flowOf(NotificationCentre())

    override suspend fun add(profileId: String, notification: AppNotification) = Unit

    override suspend fun markAllRead(profileId: String) = Unit

    override suspend fun remove(profileId: String, id: String) = Unit

    override suspend fun clear(profileId: String) = Unit
}

/** The cache turned off, which is what these navigation assertions assume: nothing pre-fetched. */
private data object NoCacheSettingsStore : CacheSettingsStore {
    override fun observeBudget(): Flow<CacheBudget> = flowOf(CacheBudget.DISABLED)

    /** Already answered, so the first-run offer never appears over a navigation assertion. */
    override fun observeChoicePending(): Flow<Boolean> = flowOf(false)

    override suspend fun chooseBudget(gigabytes: Int) = Unit

    override fun observeMark(): Flow<CacheFillMark> = flowOf(CacheFillMark())

    override suspend fun rememberMark(done: Int, total: Int) = Unit
}

/** An empty cache: nothing stored, nothing fetched. */
private data object NoArtworkCache : ArtworkCacheAccess {
    override suspend fun warm(url: String): Boolean = false

    override fun bytesUsed(): Long = 0L

    override fun clear() = Unit
}
