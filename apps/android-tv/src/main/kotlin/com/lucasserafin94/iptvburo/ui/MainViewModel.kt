package com.lucasserafin94.iptvburo.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.core.logging.AppLogger
import com.lucasserafin94.iptvburo.data.preferences.OnboardingPreferences
import com.lucasserafin94.iptvburo.data.licensing.AndroidDeviceIdentityProvider
import com.lucasserafin94.iptvburo.data.repository.CatalogRepository
import com.lucasserafin94.iptvburo.data.repository.CatalogCursor
import com.lucasserafin94.iptvburo.data.repository.BuroProfile
import com.lucasserafin94.iptvburo.data.repository.ProfileType
import com.lucasserafin94.iptvburo.data.repository.UserLibraryRepository
import com.lucasserafin94.iptvburo.data.repository.toProfile
import com.lucasserafin94.iptvburo.data.repository.XtreamImportRequest
import com.lucasserafin94.iptvburo.data.repository.XtreamImportStage
import com.lucasserafin94.iptvburo.di.IoDispatcher
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.Category
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.Episode
import com.lucasserafin94.iptvburo.domain.model.FamilyContentPolicy
import com.lucasserafin94.iptvburo.domain.model.MovieDetails
import com.lucasserafin94.iptvburo.domain.model.SeriesDetails
import com.lucasserafin94.iptvburo.domain.model.Source
import com.lucasserafin94.iptvburo.domain.model.SourceType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.ArrayDeque
import java.util.Locale
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class MainViewModel @Inject constructor(
    @param:ApplicationContext private val contextProvider: Provider<Context>,
    private val catalogRepository: CatalogRepository,
    private val onboardingPreferences: OnboardingPreferences,
    private val userLibraryRepository: UserLibraryRepository,
    private val logger: AppLogger,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val context: Context
        get() = contextProvider.get()

    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()

    private val backStack = ArrayDeque<AppContent>()
    private var catalogJob: Job? = null
    private var pageJob: Job? = null
    private var actionJob: Job? = null
    private var importJob: Job? = null
    private var homeJob: Job? = null
    private var favoritesJob: Job? = null
    private var seriesEpisodes: Map<String, Episode> = emptyMap()
    private val knownMovieChannels = LinkedHashMap<String, ChannelUi>()
    private val actorMovieIds = LinkedHashMap<String, LinkedHashSet<String>>()
    private var nextChannelCursor: CatalogCursor? = null

    init {
        observeOnboarding()
        observeProfiles()
        observeSources()
    }

    private fun loadDeviceIdentity() {
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) { AndroidDeviceIdentityProvider.getOrCreate(context).deviceId }
            }.onSuccess { deviceId -> mutableState.update { it.copy(deviceId = deviceId) } }
                .onFailure { logger.warn(TAG, "Installation identity is not available on this device") }
        }
    }

    fun acceptLegalNotice() {
        viewModelScope.launch {
            onboardingPreferences.acceptLegalNotice()
        }
    }

    fun selectSection(section: AppSection) {
        cancelCatalogWork()
        backStack.clear()
        clearEphemeralSeries()

        when (section) {
            AppSection.HOME -> updateDestination(section, AppContent.Home)
            AppSection.SOURCES -> updateDestination(section, AppContent.Sources)
            AppSection.SETTINGS -> updateDestination(section, AppContent.Settings)
            AppSection.LIVE,
            AppSection.MOVIES,
            AppSection.SERIES,
            -> openPrimaryCatalog(section)

            AppSection.MY_BURO -> {
                updateDestination(section, AppContent.Favorites)
                loadFavorites()
            }

            AppSection.PROFILE -> updateDestination(section, AppContent.Settings)

            AppSection.DISCOVER,
            AppSection.SEARCH,
            -> updateDestination(section, AppContent.SectionPlaceholder(section))
        }
    }

    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            val profile = userLibraryRepository.getProfile(profileId) ?: return@launch
            onboardingPreferences.selectProfile(profile.id)
        }
    }

    fun requestProfileSelection() {
        viewModelScope.launch { onboardingPreferences.selectProfile(null) }
    }

    fun createProfile(name: String, isKids: Boolean) {
        viewModelScope.launch {
            runCatching {
                userLibraryRepository.createProfile(
                    name = name,
                    type = if (isKids) ProfileType.KIDS else ProfileType.ADULT,
                    languageTag = Locale.getDefault().toLanguageTag(),
                )
            }.onFailure { error ->
                logger.error(TAG, "Could not create a family profile", error)
            }
        }
    }

    fun openStory(itemId: String) {
        if (itemId.isBlank() || mutableState.value.content != AppContent.Home) return

        rememberLastFocusedHomeItem(itemId)
        mutableState.value.homeItems.firstOrNull { it.id == itemId }?.let { item ->
            openChannel(item)
            return
        }
        navigate(AppContent.Story(itemId))
    }

    fun openSources() {
        selectSection(AppSection.SOURCES)
    }

    fun rememberLastFocusedHomeItem(itemId: String?) {
        mutableState.update {
            it.copy(lastFocusedHomeItemId = itemId?.takeIf(String::isNotBlank))
        }
    }

    fun openSource(source: SourceUi) {
        val content =
            AppContent.Categories(
                sourceId = source.id,
                sourceName = source.name,
                contentType = null,
            )
        navigate(content)
        observeCategories(content)
    }

    fun openCategory(category: CategoryUi) {
        val categoriesContent = mutableState.value.content as? AppContent.Categories ?: return
        val content =
            AppContent.Channels(
                sourceId = categoriesContent.sourceId,
                sourceName = categoriesContent.sourceName,
                categoryId = category.id,
                categoryName = category.name,
                contentType = categoriesContent.contentType,
            )
        navigate(content)
        loadInitialChannels(content)
    }

    fun openChannel(channel: ChannelUi) {
        if (mutableState.value.isResolvingPlayback) return
        if (channel.contentType == CatalogContentType.SERIES) {
            openSeries(channel)
            return
        }
        if (channel.contentType == CatalogContentType.MOVIE) {
            openMovie(channel)
            return
        }

        val originContent = mutableState.value.content
        resolveCatalogItemForPlayback(channel.id, channel.categoryName, originContent)
    }

    fun openPerson(name: String) {
        val safeName = name.trim().takeIf(String::isNotEmpty) ?: return
        val movieIds = actorMovieIds[safeName.lowercase()].orEmpty()
        navigate(AppContent.Person(safeName))
        mutableState.update {
            it.copy(personMovies = movieIds.mapNotNull(knownMovieChannels::get))
        }
    }

    fun playSelectedMovie() {
        val content = mutableState.value.content as? AppContent.MovieDetails ?: return
        if (mutableState.value.isResolvingPlayback) return
        mutableState.update { it.copy(isResolvingPlayback = true, hasPlaybackError = false) }
        viewModelScope.launch {
            val alternative =
                if (content.fallbackTitle.hasHighRiskVideoTag()) {
                    runCatching {
                        catalogRepository.findCompatibleMovieAlternative(
                            sourceId = content.sourceId,
                            titlePrefix = content.fallbackTitle.compatibilityTitlePrefix(),
                            excludeChannelId = content.channelId,
                        )
                    }.getOrNull()
                } else {
                    null
                }
            if (mutableState.value.content != content) return@launch
            mutableState.update { it.copy(isResolvingPlayback = false) }
            resolveCatalogItemForPlayback(
                channelId = alternative?.id ?: content.channelId,
                categoryName = content.categoryName,
                originContent = content,
            )
        }
    }

    fun toggleSelectedMovieFavorite() {
        val content = mutableState.value.content as? AppContent.MovieDetails ?: return
        val profileId = mutableState.value.activeProfile?.id ?: return
        val currentlyFavorite = content.channelId in mutableState.value.favoriteIds
        viewModelScope.launch {
            userLibraryRepository.toggleFavorite(profileId, content.channelId, currentlyFavorite)
        }
    }

    fun toggleChannelFavorite(channel: ChannelUi) {
        val profileId = mutableState.value.activeProfile?.id ?: return
        val currentlyFavorite = channel.id in mutableState.value.favoriteIds
        viewModelScope.launch {
            userLibraryRepository.toggleFavorite(profileId, channel.id, currentlyFavorite)
        }
    }

    private fun resolveCatalogItemForPlayback(
        channelId: String,
        categoryName: String?,
        originContent: AppContent,
    ) {
        actionJob?.cancel()
        mutableState.update {
            it.copy(
                isResolvingPlayback = true,
                hasPlaybackError = false,
            )
        }
        actionJob =
            viewModelScope.launch {
                runCatching {
                    requireNotNull(catalogRepository.getChannel(channelId)) {
                        "The selected catalog item is unavailable."
                    }
                }.onSuccess { resolved ->
                    if (mutableState.value.content != originContent) return@onSuccess
                    if (resolved.streamUri.isBlank()) {
                        mutableState.update {
                            it.copy(
                                isResolvingPlayback = false,
                                hasPlaybackError = true,
                            )
                        }
                    } else {
                        mutableState.update {
                            it.copy(
                                isResolvingPlayback = false,
                                hasPlaybackError = false,
                                liveNow = null,
                                liveNext = null,
                                isLiveEpgLoading = resolved.contentType == CatalogContentType.LIVE,
                            )
                        }
                        val playbackChannel = resolved.toPlaybackUi(categoryName)
                        navigate(playbackChannel)
                        val providerStreamId = resolved.providerItemId
                        if (resolved.contentType == CatalogContentType.LIVE && providerStreamId != null) {
                            loadLiveEpg(playbackChannel, providerStreamId)
                        }
                    }
                }.onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    logger.error(TAG, "Could not resolve the selected item for playback", error)
                    mutableState.update {
                        it.copy(
                            isResolvingPlayback = false,
                            hasPlaybackError = true,
                        )
                    }
                }
            }
    }

    private fun loadLiveEpg(channel: ChannelUi, providerStreamId: String) {
        viewModelScope.launch {
            val epg = runCatching {
                catalogRepository.loadShortEpg(channel.sourceId, providerStreamId)
            }.getOrNull()
            val currentPlayer = mutableState.value.content as? AppContent.Player
            if (currentPlayer?.channel?.id != channel.id) return@launch
            mutableState.update { state ->
                state.copy(
                    liveNow = epg?.now?.let { LiveProgramUi(it.title, it.description) },
                    liveNext = epg?.next?.let { LiveProgramUi(it.title, it.description) },
                    isLiveEpgLoading = false,
                )
            }
        }
    }

    fun openEpisode(episode: EpisodeUi) {
        if (mutableState.value.isResolvingPlayback) return
        val resolved = seriesEpisodes[episode.id]
        if (resolved == null) {
            mutableState.update { it.copy(hasPlaybackError = true) }
            return
        }
        val originContent = mutableState.value.content as? AppContent.SeriesDetails ?: return
        actionJob?.cancel()
        mutableState.update {
            it.copy(
                isResolvingPlayback = true,
                hasPlaybackError = false,
            )
        }
        actionJob =
            viewModelScope.launch {
                runCatching {
                    catalogRepository.resolveEpisode(resolved)
                }.onSuccess { playback ->
                    if (mutableState.value.content != originContent) return@onSuccess
                    if (playback.streamUri.isBlank()) {
                        mutableState.update {
                            it.copy(
                                isResolvingPlayback = false,
                                hasPlaybackError = true,
                            )
                        }
                    } else {
                        mutableState.update {
                            it.copy(
                                isResolvingPlayback = false,
                                hasPlaybackError = false,
                            )
                        }
                        navigate(
                            playback.toPlaybackUi(episode.seasonLabel()).copy(
                                seriesId = originContent.providerSeriesId,
                                seasonNumber = episode.seasonNumber,
                                episodeNumber = episode.episodeNumber,
                            ),
                        )
                    }
                }.onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    logger.error(
                        TAG,
                        "Could not resolve the selected episode for playback",
                        null,
                    )
                    if (mutableState.value.content == originContent) {
                        mutableState.update {
                            it.copy(
                                isResolvingPlayback = false,
                                hasPlaybackError = true,
                            )
                        }
                    }
                }
            }
    }

    fun loadMoreChannels() {
        val content = mutableState.value.content as? AppContent.Channels ?: return
        val currentState = mutableState.value
        if (
            currentState.isCatalogLoading ||
            currentState.isLoadingMore ||
            !currentState.hasMoreChannels
        ) {
            return
        }
        loadChannelsPage(
            content = content,
            cursor = nextChannelCursor,
            append = true,
        )
    }

    fun retryCatalog() {
        when (val content = mutableState.value.content) {
            is AppContent.Categories -> observeCategories(content)
            is AppContent.Channels -> {
                val loadedItemCount = mutableState.value.channels.size
                if (loadedItemCount == 0) {
                    loadInitialChannels(content)
                } else {
                    loadChannelsPage(
                        content = content,
                        cursor = nextChannelCursor,
                        append = true,
                    )
                }
            }
            is AppContent.MovieDetails -> loadMovieDetails(content)
            is AppContent.SeriesDetails -> loadSeriesDetails(content)
            else -> Unit
        }
    }

    fun goBack(): Boolean {
        val current = mutableState.value.content
        val previous = backStack.pollLast()
        if (previous == null) {
            if (current != AppContent.Home) {
                selectSection(AppSection.HOME)
                return true
            }
            return false
        }

        cancelCatalogWork()
        if (current is AppContent.SeriesDetails || current is AppContent.MovieDetails) {
            clearEphemeralSeries()
        }
        mutableState.update {
            it.copy(
                content = previous,
                isCatalogLoading = false,
                isLoadingMore = false,
                hasCatalogError = false,
                isResolvingPlayback = false,
                hasPlaybackError = false,
                isMovieLoading = false,
                hasMovieError = false,
                isSeriesLoading = false,
                hasSeriesError = false,
            )
        }

        when (previous) {
            is AppContent.Categories -> observeCategories(previous)
            is AppContent.Channels -> loadInitialChannels(previous)
            is AppContent.MovieDetails -> Unit
            is AppContent.SeriesDetails -> Unit
            else -> cancelCatalogWork()
        }
        return true
    }

    fun importPlaylist(uri: Uri) {
        if (mutableState.value.isImporting) return

        mutableState.update {
            it.copy(
                isImporting = true,
                lastImportedChannelCount = null,
                hasImportError = false,
                lastImportMethod = SourceImportMethod.M3U_FILE,
                xtreamImportStage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    val displayName = resolveDisplayName(uri)
                    val inputStream = requireNotNull(context.contentResolver.openInputStream(uri)) {
                        "The selected document could not be opened."
                    }
                    inputStream.use { stream ->
                        catalogRepository.importPlaylist(displayName, stream)
                    }
                }
            }.onSuccess { result ->
                finishSuccessfulImport(
                    importedItemCount = result.importedChannelCount,
                    method = SourceImportMethod.M3U_FILE,
                )
            }.onFailure { error ->
                logger.error(TAG, "Playlist import failed", error)
                finishFailedImport(SourceImportMethod.M3U_FILE)
            }
        }
    }

    fun importXtreamSource(
        displayName: String,
        serverUrl: String,
        username: String,
        password: String,
    ) {
        if (mutableState.value.isImporting) return
        if (
            displayName.isBlank() ||
            serverUrl.isBlank() ||
            username.isBlank() ||
            password.isBlank()
        ) {
            finishFailedImport(SourceImportMethod.XTREAM)
            return
        }

        mutableState.update {
            it.copy(
                isImporting = true,
                lastImportedChannelCount = null,
                hasImportError = false,
                lastImportMethod = SourceImportMethod.XTREAM,
                xtreamImportStage = XtreamImportStageUi.AUTHENTICATING,
            )
        }

        importJob =
            viewModelScope.launch {
                val runningJob = currentCoroutineContext()[Job]
                try {
                    val result =
                        catalogRepository.importXtream(
                            request =
                                XtreamImportRequest(
                                    displayName = displayName.trim(),
                                    serverUrl = serverUrl.trim(),
                                    username = username.trim(),
                                    password = password,
                                ),
                            onProgress = { stage ->
                                mutableState.update { state ->
                                    if (
                                        state.isImporting &&
                                        state.lastImportMethod == SourceImportMethod.XTREAM
                                    ) {
                                        state.copy(xtreamImportStage = stage.toUi())
                                    } else {
                                        state
                                    }
                                }
                            },
                        )
                    finishSuccessfulImport(
                        importedItemCount = result.totalItemCount,
                        method = SourceImportMethod.XTREAM,
                    )
                } catch (cancelled: CancellationException) {
                    mutableState.update { state ->
                        if (
                            state.isImporting &&
                            state.lastImportMethod == SourceImportMethod.XTREAM
                        ) {
                            state.copy(
                                isImporting = false,
                                lastImportedChannelCount = null,
                                hasImportError = false,
                                xtreamImportStage = null,
                            )
                        } else {
                            state
                        }
                    }
                    throw cancelled
                } catch (error: Exception) {
                    logger.error(TAG, "Xtream source import failed", error)
                    finishFailedImport(SourceImportMethod.XTREAM)
                } finally {
                    if (importJob === runningJob) importJob = null
                }
            }
    }

    fun cancelXtreamImport() {
        val current = mutableState.value
        if (
            !current.isImporting ||
            current.lastImportMethod != SourceImportMethod.XTREAM
        ) {
            return
        }
        val runningJob = importJob
        importJob = null
        runningJob?.cancel(CancellationException("Xtream import cancelled by user."))
        mutableState.update {
            it.copy(
                isImporting = false,
                lastImportedChannelCount = null,
                hasImportError = false,
                xtreamImportStage = null,
            )
        }
    }

    private fun openPrimaryCatalog(section: AppSection) {
        val sources = mutableState.value.sources
        val source =
            when (section) {
                AppSection.MOVIES,
                AppSection.SERIES,
                -> sources.firstOrNull { it.type == SourceType.XTREAM }

                else -> sources.firstOrNull()
            }
        if (source == null) {
            updateDestination(section, AppContent.SectionPlaceholder(section))
            return
        }

        val contentType =
            when (section) {
                AppSection.LIVE ->
                    if (source.type == SourceType.XTREAM) {
                        CatalogContentType.LIVE
                    } else {
                        null
                    }

                AppSection.MOVIES -> CatalogContentType.MOVIE
                AppSection.SERIES -> CatalogContentType.SERIES
                else -> null
            }
        val content =
            AppContent.Categories(
                sourceId = source.id,
                sourceName = source.name,
                contentType = contentType,
            )
        updateDestination(section, content)
        observeCategories(content)
    }

    private fun openSeries(channel: ChannelUi) {
        val providerSeriesId = channel.providerItemId
        if (providerSeriesId.isNullOrBlank() || channel.sourceId.isBlank()) {
            mutableState.update { it.copy(hasCatalogError = true) }
            return
        }
        pageJob?.cancel()
        val content =
            AppContent.SeriesDetails(
                sourceId = channel.sourceId,
                providerSeriesId = providerSeriesId,
                fallbackTitle = channel.name,
            )
        navigate(content)
        loadSeriesDetails(content)
    }

    private fun openMovie(channel: ChannelUi) {
        val providerMovieId = channel.providerItemId
        if (providerMovieId.isNullOrBlank() || channel.sourceId.isBlank()) {
            mutableState.update { it.copy(hasCatalogError = true) }
            return
        }
        pageJob?.cancel()
        knownMovieChannels[channel.id] = channel
        val content =
            AppContent.MovieDetails(
                sourceId = channel.sourceId,
                providerMovieId = providerMovieId,
                channelId = channel.id,
                fallbackTitle = channel.name,
                fallbackArtworkUrl = channel.logoUrl,
                categoryName = channel.categoryName,
            )
        navigate(content)
        loadMovieDetails(content)
    }

    private fun loadMovieDetails(content: AppContent.MovieDetails) {
        actionJob?.cancel()
        clearEphemeralSeries()
        mutableState.update {
            it.copy(
                movieDetails = null,
                isMovieLoading = true,
                hasMovieError = false,
                isResolvingPlayback = false,
                hasPlaybackError = false,
            )
        }
        actionJob =
            viewModelScope.launch {
                runCatching {
                    catalogRepository.loadMovieDetails(
                        sourceId = content.sourceId,
                        providerMovieId = content.providerMovieId,
                    )
                }.onSuccess { details ->
                    if (mutableState.value.content != content) return@onSuccess
                    mutableState.update {
                        it.copy(
                            movieDetails = details.toUi(),
                            isMovieLoading = false,
                            hasMovieError = false,
                        )
                    }
                    details.cast
                        ?.split(Regex("[,;]|\\s/\\s"))
                        ?.map(String::trim)
                        ?.filter { it.length in 2..100 }
                        ?.take(MAX_INDEXED_CAST)
                        ?.forEach { actor ->
                            actorMovieIds
                                .getOrPut(actor.lowercase()) { LinkedHashSet() }
                                .add(content.channelId)
                        }
                }.onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    logger.error(TAG, "Could not load movie details", error)
                    if (mutableState.value.content == content) {
                        mutableState.update {
                            it.copy(
                                movieDetails = null,
                                isMovieLoading = false,
                                hasMovieError = true,
                            )
                        }
                    }
                }
            }
    }

    private fun loadSeriesDetails(content: AppContent.SeriesDetails) {
        actionJob?.cancel()
        clearEphemeralSeries()
        mutableState.update {
            it.copy(
                seriesDetails = null,
                isSeriesLoading = true,
                hasSeriesError = false,
                personMovies = emptyList(),
                isResolvingPlayback = false,
                hasPlaybackError = false,
            )
        }
        actionJob =
            viewModelScope.launch {
                runCatching {
                    catalogRepository.loadSeriesDetails(
                        sourceId = content.sourceId,
                        providerSeriesId = content.providerSeriesId,
                    )
                }.onSuccess { details ->
                    if (mutableState.value.content != content) return@onSuccess
                    seriesEpisodes = details.episodes.associateBy(Episode::id)
                    mutableState.update {
                        it.copy(
                            seriesDetails = details.toUi(),
                            isSeriesLoading = false,
                            hasSeriesError = false,
                        )
                    }
                }.onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    logger.error(TAG, "Could not load series details", error)
                    if (mutableState.value.content == content) {
                        mutableState.update {
                            it.copy(
                                seriesDetails = null,
                                isSeriesLoading = false,
                                hasSeriesError = true,
                            )
                        }
                    }
                }
            }
    }

    private fun observeCategories(content: AppContent.Categories) {
        catalogJob?.cancel()
        pageJob?.cancel()
        mutableState.update {
            it.copy(
                categories = emptyList(),
                channels = emptyList(),
                isCatalogLoading = true,
                hasCatalogError = false,
                hasMoreChannels = false,
            )
        }
        catalogJob =
            viewModelScope.launch {
                combine(
                    catalogRepository.observeCategories(
                        sourceId = content.sourceId,
                        contentType = content.contentType,
                    ),
                    catalogRepository.observeCategoryItemCounts(
                        sourceId = content.sourceId,
                        contentType = content.contentType,
                    ),
                ) { categories, counts ->
                    val kidsMode = mutableState.value.activeProfile?.isKids == true
                    val visibleCategories =
                        if (kidsMode) categories.filterNot { FamilyContentPolicy.isExplicitAdultLabel(it.name) } else categories
                    buildList {
                        if (!kidsMode) {
                            add(
                                CategoryUi(
                                    id = null,
                                    name = "",
                                    channelCount = counts.values.sum(),
                                ),
                            )
                        }
                        addAll(
                            visibleCategories.map { category ->
                                category.toUi(counts[category.id] ?: 0)
                            },
                        )
                    }
                }.catch { error ->
                    logger.error(TAG, "Could not observe catalog categories", error)
                    mutableState.update {
                        it.copy(
                            categories = emptyList(),
                            isCatalogLoading = false,
                            hasCatalogError = true,
                        )
                    }
                }.collect { categories ->
                    mutableState.update {
                        it.copy(
                            categories = categories,
                            isCatalogLoading = false,
                            hasCatalogError = false,
                        )
                    }
                }
            }
    }

    private fun loadInitialChannels(content: AppContent.Channels) {
        catalogJob?.cancel()
        pageJob?.cancel()
        nextChannelCursor = null
        mutableState.update {
            it.copy(
                channels = emptyList(),
                isCatalogLoading = true,
                isLoadingMore = false,
                hasMoreChannels = false,
                hasCatalogError = false,
                hasPlaybackError = false,
            )
        }
        loadChannelsPage(content = content, cursor = null, append = false)
    }

    private fun loadChannelsPage(
        content: AppContent.Channels,
        cursor: CatalogCursor?,
        append: Boolean,
    ) {
        pageJob?.cancel()
        mutableState.update {
            if (append) {
                it.copy(isLoadingMore = true, hasCatalogError = false)
            } else {
                it
            }
        }
        pageJob =
            viewModelScope.launch {
                runCatching {
                    catalogRepository.loadChannelsPageAfter(
                        sourceId = content.sourceId,
                        categoryId = content.categoryId,
                        contentType = content.contentType,
                        cursor = cursor,
                        limit = PAGE_SIZE,
                    )
                }.onSuccess { page ->
                    if (mutableState.value.content != content) return@onSuccess
                    nextChannelCursor = page.nextCursor
                    mutableState.update { state ->
                        val incoming =
                            page.items.map { channel ->
                                channel.toCatalogUi(content.categoryName)
                            }.filterKidsContentIfNeeded(state.activeProfile)
                        state.copy(
                            channels =
                                if (append) {
                                    (state.channels + incoming).distinctBy(ChannelUi::id)
                                } else {
                                    incoming
                                },
                            isCatalogLoading = false,
                            isLoadingMore = false,
                            hasMoreChannels = page.hasMore,
                            hasCatalogError = false,
                        )
                    }
                }.onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    logger.error(TAG, "Could not load a catalog page", error)
                    if (mutableState.value.content == content) {
                        mutableState.update {
                            it.copy(
                                isCatalogLoading = false,
                                isLoadingMore = false,
                                hasCatalogError = true,
                            )
                        }
                    }
                }
            }
    }

    private fun observeOnboarding() {
        viewModelScope.launch {
            onboardingPreferences.accepted
                .catch { error ->
                    logger.error(TAG, "Could not read onboarding state", error)
                    emit(false)
                }
                .collect { accepted ->
                    mutableState.update {
                        it.copy(
                            isInitializing = false,
                            hasAcceptedLegalNotice = accepted,
                        )
                    }
                    if (accepted && mutableState.value.deviceId == null) loadDeviceIdentity()
                }
        }
    }

    private fun observeProfiles() {
        viewModelScope.launch {
            userLibraryRepository.ensureDefaultProfile(Locale.getDefault().toLanguageTag())
            combine(
                userLibraryRepository.observeProfiles(),
                onboardingPreferences.activeProfileId,
            ) { profiles, activeId -> profiles to activeId }
                .catch { error ->
                    logger.error(TAG, "Could not load family profiles", error)
                    emit(emptyList<com.lucasserafin94.iptvburo.data.local.entity.ProfileEntity>() to null)
                }
                .collect { (entities, activeId) ->
                    val profiles = entities.map { it.toProfile().toUi() }
                    val active = profiles.firstOrNull { it.id == activeId }
                    mutableState.update {
                        it.copy(
                            isProfilesLoading = false,
                            profiles = profiles,
                            activeProfile = active,
                            favoriteIds = if (active?.id == it.activeProfile?.id) it.favoriteIds else emptySet(),
                            favoriteItems = if (active?.id == it.activeProfile?.id) it.favoriteItems else emptyList(),
                        )
                    }
                    observeFavorites(active?.id)
                }
        }
    }

    private fun observeFavorites(profileId: String?) {
        favoritesJob?.cancel()
        if (profileId == null) return
        favoritesJob = viewModelScope.launch {
            userLibraryRepository.observeFavoriteIds(profileId)
                .catch { error ->
                    logger.error(TAG, "Could not observe favorites", error)
                    emit(emptyList())
                }
                .collect { ids ->
                    mutableState.update { it.copy(favoriteIds = ids.toSet()) }
                    if (mutableState.value.content == AppContent.Favorites) loadFavorites()
                }
        }
    }

    private fun loadFavorites() {
        val profileId = mutableState.value.activeProfile?.id ?: return
        actionJob?.cancel()
        mutableState.update { it.copy(isCatalogLoading = true, hasCatalogError = false) }
        actionJob = viewModelScope.launch {
            runCatching { userLibraryRepository.loadFavorites(profileId) }
                .onSuccess { items ->
                    mutableState.update {
                        it.copy(
                            favoriteItems =
                                items.map { channel -> channel.toCatalogUi("Minha BURO") }
                                    .filterKidsContentIfNeeded(it.activeProfile),
                            isCatalogLoading = false,
                        )
                    }
                }
                .onFailure { error ->
                    logger.error(TAG, "Could not load favorites", error)
                    mutableState.update { it.copy(isCatalogLoading = false, hasCatalogError = true) }
                }
        }
    }

    private fun observeSources() {
        viewModelScope.launch {
            catalogRepository.observeSources()
                .catch { error ->
                    logger.error(TAG, "Could not observe catalog sources", error)
                    emit(emptyList())
                }
                .collect { sources ->
                    mutableState.update {
                        it.copy(sources = sources.map { source -> source.toUi() })
                    }
                    loadHomeItems(
                        sources.firstOrNull { it.type == SourceType.XTREAM }?.id
                            ?: sources.firstOrNull()?.id,
                    )
                }
        }
    }

    private fun loadHomeItems(sourceId: String?) {
        homeJob?.cancel()
        if (sourceId == null) {
            mutableState.update { it.copy(homeItems = emptyList()) }
            return
        }
        homeJob = viewModelScope.launch {
            runCatching {
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val releasesCurrent = catalogRepository.loadForReleaseYear(sourceId, currentYear, HOME_RELEASE_LIMIT)
                val releasesPrevious = catalogRepository.loadForReleaseYear(sourceId, currentYear - 1, HOME_RELEASE_LIMIT)
                val recent = catalogRepository.loadRecentlyAdded(sourceId, HOME_RECENT_LIMIT)
                val movies = catalogRepository.loadChannelsPage(
                    sourceId = sourceId,
                    contentType = CatalogContentType.MOVIE,
                    limit = HOME_ITEM_LIMIT,
                ).items
                val series = catalogRepository.loadChannelsPage(
                    sourceId = sourceId,
                    contentType = CatalogContentType.SERIES,
                    limit = HOME_ITEM_LIMIT,
                ).items
                (releasesCurrent + releasesPrevious + recent + movies + series)
                    .filter { it.logoUri != null }
                    .distinctBy(Channel::id)
                    .distinctBy { channel -> dailyCatalogTitleKey(channel.name) }
                    .sortedWith(
                        compareBy<Channel> { channel ->
                            dailyEditorialRank(channel.id, localEditorialDay())
                        }.thenBy(Channel::id),
                    )
                    .map { channel ->
                        channel.toCatalogUi(
                            if (channel.contentType == CatalogContentType.MOVIE) "Filme" else "Série",
                        )
                    }
            }.onSuccess { items ->
                mutableState.update { state ->
                    state.copy(homeItems = items.filterKidsContentIfNeeded(state.activeProfile))
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                logger.error(TAG, "Could not compose the local home catalog", error)
            }
        }
    }

    private fun finishSuccessfulImport(
        importedItemCount: Int,
        method: SourceImportMethod,
    ) {
        backStack.clear()
        mutableState.update {
            it.copy(
                isImporting = false,
                lastImportedChannelCount = importedItemCount,
                hasImportError = false,
                lastImportMethod = method,
                xtreamImportStage = null,
                importSuccessVersion = it.importSuccessVersion + 1,
                section = AppSection.SOURCES,
                content = AppContent.Sources,
            )
        }
    }

    private fun finishFailedImport(method: SourceImportMethod) {
        mutableState.update {
            it.copy(
                isImporting = false,
                lastImportedChannelCount = null,
                hasImportError = true,
                lastImportMethod = method,
                xtreamImportStage = null,
            )
        }
    }

    private fun navigate(content: AppContent) {
        val current = mutableState.value.content
        if (current != content) {
            backStack.addLast(current)
        }
        mutableState.update { it.copy(content = content) }
    }

    private fun navigate(channel: ChannelUi) {
        navigate(AppContent.Player(channel))
    }

    private fun updateDestination(
        section: AppSection,
        content: AppContent,
    ) {
        mutableState.update {
            it.copy(
                section = section,
                content = content,
                categories = emptyList(),
                channels = emptyList(),
                isCatalogLoading = false,
                isLoadingMore = false,
                hasMoreChannels = false,
                hasCatalogError = false,
                isResolvingPlayback = false,
                hasPlaybackError = false,
                seriesDetails = null,
                movieDetails = null,
                isMovieLoading = false,
                hasMovieError = false,
                isSeriesLoading = false,
                hasSeriesError = false,
            )
        }
    }

    private fun cancelCatalogWork() {
        catalogJob?.cancel()
        pageJob?.cancel()
        actionJob?.cancel()
        catalogJob = null
        pageJob = null
        actionJob = null
        nextChannelCursor = null
    }

    private fun clearEphemeralSeries() {
        seriesEpisodes = emptyMap()
        mutableState.update {
            it.copy(
                seriesDetails = null,
                isSeriesLoading = false,
                hasSeriesError = false,
                isResolvingPlayback = false,
                hasPlaybackError = false,
            )
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        val name =
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            }
        return name
            ?.takeIf(String::isNotBlank)
            ?.removePlaylistExtension()
            ?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.sources_default_name)
    }

    private fun String.removePlaylistExtension(): String =
        when {
            endsWith(".m3u8", ignoreCase = true) -> dropLast(5)
            endsWith(".m3u", ignoreCase = true) -> dropLast(4)
            else -> this
        }

    private fun Source.toUi(): SourceUi =
        SourceUi(
            id = id,
            name =
                name
                    .removePlaylistExtension()
                    .ifBlank { name },
            channelCount = channelCount,
            type = type,
        )

    private fun Category.toUi(channelCount: Int): CategoryUi =
        CategoryUi(
            id = id,
            name = name,
            channelCount = channelCount,
        )

    private fun Channel.toCatalogUi(categoryName: String): ChannelUi =
        ChannelUi(
            id = id,
            sourceId = sourceId,
            name = name,
            categoryName = categoryName.takeIf(String::isNotBlank),
            logoUrl = logoUri,
            contentType = contentType,
            providerItemId = providerItemId,
            year = year,
            rating = rating,
        )

    private fun Channel.toPlaybackUi(categoryName: String?): ChannelUi =
        ChannelUi(
            id = id,
            sourceId = sourceId,
            name = name,
            categoryName = categoryName,
            streamUrl = streamUri,
            logoUrl = logoUri,
            requestHeaders = requestHeaders,
            contentType = contentType,
            providerItemId = providerItemId,
            year = year,
            rating = rating,
        )

    private fun List<ChannelUi>.filterKidsContentIfNeeded(profile: ProfileUi?): List<ChannelUi> =
        if (profile?.isKids == true) {
            filter { item -> FamilyContentPolicy.isAllowedForKids(item.name, listOf(item.categoryName)) }
        } else {
            this
        }

    private fun SeriesDetails.toUi(): SeriesDetailsUi =
        SeriesDetailsUi(
            title = title,
            plot = plot,
            artworkUrl = artworkUri,
            backdropUrl = backdropUris.firstOrNull(),
            cast = cast,
            director = director,
            genre = genre,
            releaseDate = releaseDate,
            rating = rating,
            youtubeTrailerId = youtubeTrailerId,
            episodes =
                episodes.map { episode ->
                    EpisodeUi(
                        id = episode.id,
                        title = episode.title,
                        seasonNumber = episode.seasonNumber,
                        episodeNumber = episode.episodeNumber,
                        artworkUrl = episode.artworkUri,
                    )
                },
        )

    private fun MovieDetails.toUi(): MovieDetailsUi =
        MovieDetailsUi(
            title = title,
            plot = plot,
            cast = cast,
            director = director,
            genre = genre,
            duration = duration,
            releaseDate = releaseDate,
            country = country,
            rating = rating,
            artworkUrl = artworkUri,
            backdropUrl = backdropUris.firstOrNull(),
            youtubeTrailerId = youtubeTrailerId,
        )

    private fun BuroProfile.toUi(): ProfileUi =
        ProfileUi(
            id = id,
            name = name,
            avatarKey = avatarKey,
            isKids = type == ProfileType.KIDS,
        )

    private fun EpisodeUi.seasonLabel(): String =
        buildString {
            append("S")
            append(seasonNumber)
            episodeNumber?.let {
                append(" E")
                append(it)
            }
        }

    private fun XtreamImportStage.toUi(): XtreamImportStageUi =
        when (this) {
            XtreamImportStage.AUTHENTICATING -> XtreamImportStageUi.AUTHENTICATING
            XtreamImportStage.CATEGORIES -> XtreamImportStageUi.CATEGORIES
            XtreamImportStage.LIVE -> XtreamImportStageUi.LIVE
            XtreamImportStage.MOVIES -> XtreamImportStageUi.MOVIES
            XtreamImportStage.SERIES -> XtreamImportStageUi.SERIES
            XtreamImportStage.SAVING -> XtreamImportStageUi.SAVING
        }

    private companion object {
        const val TAG = "MainViewModel"
        const val PAGE_SIZE = 200
        const val HOME_ITEM_LIMIT = 16
        const val HOME_RELEASE_LIMIT = 18
        const val HOME_RECENT_LIMIT = 18
        const val MAX_INDEXED_CAST = 32
        val HIGH_RISK_VIDEO_TAG = Regex("(?i)(?:\\b4k\\b|\\buhd\\b|\\bhevc\\b|\\bh\\.?265\\b|\\[dv]|\\[hdr])")
    }

    private fun String.hasHighRiskVideoTag(): Boolean = HIGH_RISK_VIDEO_TAG.containsMatchIn(this)

    private fun String.compatibilityTitlePrefix(): String =
        replace(HIGH_RISK_VIDEO_TAG, " ")
            .replace(Regex("\\[[^]]+]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}

private fun localEditorialDay(): Long =
    Calendar.getInstance().let { calendar ->
        calendar.get(Calendar.YEAR).toLong() * 400L + calendar.get(Calendar.DAY_OF_YEAR)
    }

internal fun dailyEditorialRank(itemId: String, epochDay: Long): Long {
    var value = itemId.hashCode().toLong() xor (epochDay * -7046029254386353131L)
    value = value xor (value ushr 30)
    value *= -4658895280553007687L
    value = value xor (value ushr 27)
    return value xor (value ushr 31)
}

internal fun dailyCatalogTitleKey(title: String): String =
    title
        .lowercase(Locale.ROOT)
        .replace(Regex("\\[[^]]{1,12}]"), " ")
        .replace(Regex("\\b(4k|uhd|fhd|hd|sd|h\\.?265|hevc|multi|dual)\\b"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
