package com.lucasserafin94.iptvburo.ui

import android.content.Context
import com.lucasserafin94.iptvburo.core.logging.AppLogger
import com.lucasserafin94.iptvburo.data.download.AndroidDownloadManager
import com.lucasserafin94.iptvburo.data.preferences.OnboardingPreferences
import com.lucasserafin94.iptvburo.data.local.dao.FavoriteDao
import com.lucasserafin94.iptvburo.data.local.dao.ProfileDao
import com.lucasserafin94.iptvburo.data.local.entity.ChannelEntity
import com.lucasserafin94.iptvburo.data.local.entity.FavoriteEntity
import com.lucasserafin94.iptvburo.data.local.entity.ProfileEntity
import com.lucasserafin94.iptvburo.data.repository.UserLibraryRepository
import com.lucasserafin94.iptvburo.data.repository.CatalogPage
import com.lucasserafin94.iptvburo.data.repository.CatalogRepository
import com.lucasserafin94.iptvburo.data.repository.PlaylistImportResult
import com.lucasserafin94.iptvburo.data.repository.XtreamImportRequest
import com.lucasserafin94.iptvburo.data.repository.XtreamImportResult
import com.lucasserafin94.iptvburo.data.repository.XtreamImportStage
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.Category
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.Episode
import com.lucasserafin94.iptvburo.domain.model.MovieDetails
import com.lucasserafin94.iptvburo.domain.model.SeriesDetails
import com.lucasserafin94.iptvburo.domain.model.Source
import com.lucasserafin94.iptvburo.domain.model.SourceType
import java.io.InputStream
import javax.inject.Provider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
    fun `movies selects an Xtream source and filters movie content`() = runTest {
        val local = source("local", SourceType.LOCAL_M3U)
        val xtream = source("xtream", SourceType.XTREAM)
        val repository = FakeCatalogRepository(sources = listOf(local, xtream))
        val viewModel = createViewModel(repository)
        runCurrent()

        viewModel.selectSection(AppSection.MOVIES)
        runCurrent()

        assertEquals(
            AppContent.Categories(
                sourceId = xtream.id,
                sourceName = xtream.name,
                contentType = CatalogContentType.MOVIE,
            ),
            viewModel.state.value.content,
        )
        assertEquals(CatalogContentType.MOVIE, repository.lastObservedContentType)
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

    private fun TestScope.createViewModel(
        repository: FakeCatalogRepository,
        logger: AppLogger = NoOpLogger,
    ): MainViewModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val contextProvider =
            Provider<Context> {
                error("Android context is not used by these assertions")
            }
        return MainViewModel(
            contextProvider = contextProvider,
            catalogRepository = repository,
            onboardingPreferences = FakeOnboardingPreferences,
            userLibraryRepository = UserLibraryRepository(FakeProfileDao(), FakeFavoriteDao(), dispatcher),
            // The manager resolves storage lazily, so these navigation assertions never touch it.
            downloadManager = AndroidDownloadManager(contextProvider, OkHttpClient(), dispatcher),
            logger = logger,
            ioDispatcher = dispatcher,
        )
    }
}

private class FakeCatalogRepository(
    private val sources: List<Source> = emptyList(),
    private val categories: List<Category> = emptyList(),
    private val categoryCounts: Map<String?, Int> = emptyMap(),
    private val pageLoader: (offset: Int, limit: Int) -> CatalogPage =
        { offset, _ -> CatalogPage(emptyList(), offset, 0) },
    private val resolvedChannels: Map<String, Channel> = emptyMap(),
    private val resolvedEpisodes: Map<String, Channel> = emptyMap(),
    private val seriesDetails: SeriesDetails? = null,
    private val movieDetails: MovieDetails? = null,
    private val xtreamImportGate: CompletableDeferred<Unit>? = null,
) : CatalogRepository {
    var lastObservedContentType: CatalogContentType? = null
    var observeChannelsCalls: Int = 0
    val pageRequests = mutableListOf<Pair<Int, Int>>()
    val getChannelRequests = mutableListOf<String>()
    val resolveEpisodeRequests = mutableListOf<Episode>()
    var lastXtreamRequest: XtreamImportRequest? = null
    var afterXtreamProgress: ((XtreamImportStage) -> Unit)? = null

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
