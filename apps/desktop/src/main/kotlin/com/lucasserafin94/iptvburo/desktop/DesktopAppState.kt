package com.lucasserafin94.iptvburo.desktop

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lucasserafin94.iptvburo.desktop.data.InMemoryCatalogRepository
import com.lucasserafin94.iptvburo.desktop.data.SessionXtreamRepository
import com.lucasserafin94.iptvburo.desktop.model.DesktopSourceKind
import com.lucasserafin94.iptvburo.desktop.model.DesktopSourceSummary
import com.lucasserafin94.iptvburo.desktop.model.ImportedCatalog
import com.lucasserafin94.iptvburo.desktop.model.XtreamCatalogPage
import com.lucasserafin94.iptvburo.desktop.model.XtreamPlaybackTarget
import com.lucasserafin94.iptvburo.desktop.model.XtreamSessionSummary
import com.lucasserafin94.iptvburo.desktop.platform.ExternalOpenResult
import com.lucasserafin94.iptvburo.desktop.platform.openUriExternally
import com.lucasserafin94.iptvburo.desktop.playback.DesktopPlaybackRequest
import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import com.lucasserafin94.iptvburo.desktop.security.RememberedXtreamStore
import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import com.lucasserafin94.iptvburo.desktop.user.DesktopProfile
import com.lucasserafin94.iptvburo.desktop.user.DesktopUserStore
import com.lucasserafin94.iptvburo.domain.model.Category
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.FamilyContentPolicy
import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamCategory
import com.lucasserafin94.iptvburo.xtream.XtreamClientException
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import com.lucasserafin94.iptvburo.xtream.XtreamFailureReason
import com.lucasserafin94.iptvburo.xtream.XtreamMovieDetails
import com.lucasserafin94.iptvburo.xtream.XtreamEpgProgram
import com.lucasserafin94.iptvburo.xtream.XtreamSeriesDetails
import java.net.URI
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.Arrays
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Stable
class DesktopAppState(
    private val localRepository: InMemoryCatalogRepository,
    private val xtreamRepository: SessionXtreamRepository,
    private val rememberedXtreamStore: RememberedXtreamStore,
    private val userStore: DesktopUserStore = DesktopUserStore(),
) {
    private val initialUserSnapshot = userStore.load()
    var profiles by mutableStateOf(initialUserSnapshot.profiles)
        private set
    var activeProfileId by mutableStateOf(initialUserSnapshot.activeProfileId)
        private set
    var language by mutableStateOf(initialUserSnapshot.language)
        private set
    var favoriteKeys by mutableStateOf(initialUserSnapshot.favoriteKeys)
        private set
    var favoritesOnly by mutableStateOf(false)
        private set

    val activeProfile: DesktopProfile?
        get() = profiles.firstOrNull { it.id == activeProfileId }
    var catalogs by mutableStateOf<List<ImportedCatalog>>(emptyList())
        private set

    var xtreamSummary by mutableStateOf<XtreamSessionSummary?>(null)
        private set

    var selectedSourceId by mutableStateOf<String?>(null)
        private set

    var selectedCategoryId by mutableStateOf<String?>(null)
        private set

    var selectedChannelId by mutableStateOf<String?>(null)
        private set

    var searchQuery by mutableStateOf("")
        private set

    var importStatus by mutableStateOf<ImportStatus>(ImportStatus.Idle)
        private set

    var xtreamStatus by mutableStateOf<XtreamStatus>(XtreamStatus.Disconnected)
        private set

    var xtreamContentType by mutableStateOf(XtreamContentType.LIVE)
        private set

    var xtreamCategories by mutableStateOf<List<XtreamCategory>>(emptyList())
        private set

    var selectedXtreamCategoryId by mutableStateOf<String?>(null)
        private set

    var selectedXtreamYear by mutableStateOf<Int?>(null)
        private set

    var xtreamSearchQuery by mutableStateOf("")
        private set

    var xtreamPage by mutableStateOf(XtreamCatalogPage.empty())
        private set

    var selectedXtreamItemId by mutableStateOf<String?>(null)
        private set

    var seriesDetailsStatus by mutableStateOf<SeriesDetailsStatus>(SeriesDetailsStatus.Idle)
        private set

    var movieDetailsStatus by mutableStateOf<MovieDetailsStatus>(MovieDetailsStatus.Idle)
        private set

    var liveEpgStatus by mutableStateOf<LiveEpgStatus>(LiveEpgStatus.Idle)
        private set

    var selectedPerson by mutableStateOf<PersonFilmography?>(null)
        private set

    private val movieAppearances = LinkedHashMap<String, LinkedHashMap<String, XtreamCatalogItem>>()

    private var xtreamPageRequestGeneration = 0L

    fun selectProfile(id: String?) {
        activeProfileId = id?.takeIf { candidate -> profiles.any { it.id == candidate } }
        userStore.setActiveProfile(activeProfileId)
        favoriteKeys = userStore.favoritesForProfile(activeProfileId)
        xtreamCategories = visibleXtreamCategories(xtreamContentType)
        if (selectedXtreamCategoryId !in xtreamCategories.map(XtreamCategory::providerId)) {
            selectedXtreamCategoryId = null
        }
    }

    suspend fun selectProfileAndRefresh(id: String?) {
        selectProfile(id)
        if (xtreamSummary != null) refreshXtreamPage(pageIndex = 0)
    }

    fun createProfile(name: String, isKids: Boolean) {
        if (profiles.size >= 5) return
        val clean = name.trim().take(24)
        if (clean.isBlank()) return
        profiles = profiles + DesktopProfile(java.util.UUID.randomUUID().toString(), clean, isKids)
        userStore.saveProfiles(profiles)
    }

    fun updateLanguage(value: DesktopLanguage) {
        language = value
        userStore.setLanguage(value)
    }

    fun isFavorite(item: XtreamCatalogItem): Boolean = favoriteKey(item) in favoriteKeys

    fun toggleFavorite(item: XtreamCatalogItem) {
        val profileId = activeProfileId ?: return
        val key = favoriteKey(item)
        favoriteKeys = if (key in favoriteKeys) favoriteKeys - key else favoriteKeys + key
        userStore.setFavorites(profileId, favoriteKeys)
        if (favoritesOnly && key !in favoriteKeys) {
            xtreamPage =
                xtreamPage.copy(
                    items = xtreamPage.items.filterNot { visible -> visible.providerId == item.providerId },
                    totalMatches = (xtreamPage.totalMatches - 1).coerceAtLeast(0),
                )
        }
    }

    suspend fun setFavoritesOnly(enabled: Boolean) {
        favoritesOnly = enabled
        if (isXtreamSelected) refreshXtreamPage(pageIndex = 0)
    }

    private fun favoriteKey(item: XtreamCatalogItem): String = "${item.contentType.name}:${item.providerId}"

    val sourceSummaries: List<DesktopSourceSummary>
        get() =
            buildList {
                catalogs.forEach { catalog ->
                    add(
                        DesktopSourceSummary(
                            id = catalog.source.id,
                            name = catalog.source.name,
                            itemCount = catalog.source.channelCount,
                            kind = DesktopSourceKind.LOCAL_PLAYLIST,
                        ),
                    )
                }
                xtreamSummary?.let { summary ->
                    add(
                        DesktopSourceSummary(
                            id = summary.sourceId,
                            name = "Sessão Xtream",
                            itemCount = summary.loadedItemCount,
                            kind = DesktopSourceKind.XTREAM_SESSION,
                        ),
                    )
                }
            }

    val isXtreamSelected: Boolean
        get() {
            val xtreamSourceId = xtreamSummary?.sourceId ?: return false
            return xtreamSourceId == selectedSourceId
        }

    val selectedCatalog: ImportedCatalog?
        get() =
            if (isXtreamSelected) {
                null
            } else {
                catalogs.firstOrNull { it.source.id == selectedSourceId }
                    ?: catalogs.firstOrNull()
            }

    val hasSelectedSource: Boolean
        get() = isXtreamSelected || selectedCatalog != null

    val selectedSourceItemCount: Int
        get() =
            if (isXtreamSelected) {
                xtreamSummary?.loadedItemCount ?: 0
            } else {
                selectedCatalog?.source?.channelCount ?: 0
            }

    val categories: List<Category>
        get() = selectedCatalog?.categories.orEmpty()

    val visibleChannels: List<Channel>
        get() {
            val normalizedQuery = searchQuery.trim()
            return selectedCatalog
                ?.channels
                .orEmpty()
                .asSequence()
                .filter { channel ->
                    selectedCategoryId == null || channel.categoryId == selectedCategoryId
                }.filter { channel ->
                    normalizedQuery.isEmpty() ||
                        channel.name.contains(normalizedQuery, ignoreCase = true) ||
                        channel.tvgName?.contains(normalizedQuery, ignoreCase = true) == true
                }.toList()
        }

    val selectedChannel: Channel?
        get() =
            selectedCatalog
                ?.channels
                ?.firstOrNull { it.id == selectedChannelId }
                ?: visibleChannels.firstOrNull()

    val selectedXtreamItem: XtreamCatalogItem?
        get() =
            xtreamPage.items.firstOrNull { it.providerId == selectedXtreamItemId }
                ?: xtreamPage.items.firstOrNull()

    suspend fun importLocalPlaylist(path: Path) {
        if (importStatus is ImportStatus.Loading) return
        importStatus = ImportStatus.Loading

        runCatching {
            val sourceLabel = "Lista local ${catalogs.size + 1}"
            withContext(Dispatchers.IO) {
                localRepository.importLocal(path = path, sourceLabel = sourceLabel)
            }
        }.onSuccess { catalog ->
            catalogs = catalogs + catalog
            selectedSourceId = catalog.source.id
            selectedCategoryId = null
            selectedChannelId = catalog.channels.firstOrNull()?.id
            searchQuery = ""
            importStatus =
                ImportStatus.Success(
                    channelCount = catalog.source.channelCount,
                    warningCount = catalog.warnings.size,
                )
        }.onFailure { error ->
            error.rethrowIfCancellation()
            importStatus = ImportStatus.Error(error.toSafeImportMessage())
        }
    }

    suspend fun connectXtream(input: XtreamLoginInput) {
        if (xtreamStatus is XtreamStatus.Connecting) {
            input.clear()
            return
        }
        xtreamStatus = XtreamStatus.Connecting
        seriesDetailsStatus = SeriesDetailsStatus.Idle
        movieDetailsStatus = MovieDetailsStatus.Idle
        movieAppearances.clear()
        selectedPerson = null

        val rememberedServer = input.copyServer()
        val rememberedUsername = input.copyUsername()
        val rememberedPassword = input.copyPassword()

        try {
            runCatching {
                withContext(Dispatchers.IO) {
                    xtreamRepository.authenticateAndLoadInitial(input)
                }
            }.onSuccess { summary ->
                // A transient DPAPI/filesystem problem must not discard an already authenticated
                // in-memory session. The next successful login can retry persistence.
                runCatching {
                    withContext(Dispatchers.IO) {
                        rememberedXtreamStore.save(
                            server = rememberedServer,
                            username = rememberedUsername,
                            password = rememberedPassword,
                        )
                    }
                }
                xtreamSummary = summary
                selectedSourceId = summary.sourceId
                xtreamContentType = XtreamContentType.LIVE
                xtreamCategories = visibleXtreamCategories(XtreamContentType.LIVE)
                selectedXtreamCategoryId = null
                selectedXtreamYear = null
                xtreamSearchQuery = ""
                xtreamPage = xtreamRepository.page(XtreamContentType.LIVE, null, "", 0)
                selectedXtreamItemId = xtreamPage.items.firstOrNull()?.providerId
                xtreamStatus = XtreamStatus.Connected
            }.onFailure { error ->
                input.clear()
                error.rethrowIfCancellation()
                xtreamRepository.clear()
                clearXtreamUiState()
                if (sourceSummaries.none { source -> source.id == selectedSourceId }) {
                    selectedSourceId = catalogs.firstOrNull()?.source?.id
                }
                xtreamStatus = XtreamStatus.Error(error.toSafeXtreamMessage())
            }
        } finally {
            Arrays.fill(rememberedServer, ZERO_CHAR)
            Arrays.fill(rememberedUsername, ZERO_CHAR)
            Arrays.fill(rememberedPassword, ZERO_CHAR)
        }
    }

    suspend fun restoreRememberedXtream() {
        if (xtreamStatus !is XtreamStatus.Disconnected || xtreamSummary != null) return
        val input = withContext(Dispatchers.IO) { rememberedXtreamStore.load() } ?: return
        connectXtream(input)
    }

    suspend fun selectXtreamContentType(contentType: XtreamContentType) {
        if (contentType == xtreamContentType && contentType in xtreamSummary?.loadedContentTypes.orEmpty()) {
            return
        }
        if (xtreamStatus is XtreamStatus.LoadingCatalog) return
        xtreamStatus = XtreamStatus.LoadingCatalog(contentType)

        runCatching {
            val summary =
                if (contentType in xtreamSummary?.loadedContentTypes.orEmpty()) {
                    xtreamSummary
                } else {
                    withContext(Dispatchers.IO) {
                        xtreamRepository.loadCatalog(contentType)
                    }
                }
            requireNotNull(summary)
        }.onSuccess { summary ->
            xtreamSummary = summary
            xtreamContentType = contentType
            xtreamCategories = visibleXtreamCategories(contentType)
            selectedXtreamCategoryId = null
            selectedXtreamYear = null
            xtreamSearchQuery = ""
            seriesDetailsStatus = SeriesDetailsStatus.Idle
            movieDetailsStatus = MovieDetailsStatus.Idle
            refreshXtreamPage(pageIndex = 0)
            xtreamStatus = XtreamStatus.Connected
        }.onFailure { error ->
            error.rethrowIfCancellation()
            xtreamStatus = XtreamStatus.Error(error.toSafeXtreamMessage())
        }
    }

    suspend fun selectXtreamCategory(categoryId: String?) {
        selectedXtreamCategoryId = categoryId
        seriesDetailsStatus = SeriesDetailsStatus.Idle
        movieDetailsStatus = MovieDetailsStatus.Idle
        refreshXtreamPage(pageIndex = 0)
    }

    suspend fun selectXtreamYear(year: Int?) {
        selectedXtreamYear = year
        seriesDetailsStatus = SeriesDetailsStatus.Idle
        movieDetailsStatus = MovieDetailsStatus.Idle
        refreshXtreamPage(pageIndex = 0)
    }

    fun updateXtreamSearch(query: String) {
        xtreamSearchQuery = query.take(MAX_SEARCH_LENGTH)
    }

    suspend fun applyXtreamSearch() {
        seriesDetailsStatus = SeriesDetailsStatus.Idle
        movieDetailsStatus = MovieDetailsStatus.Idle
        refreshXtreamPage(pageIndex = 0)
    }

    suspend fun previousXtreamPage() {
        if (xtreamPage.hasPrevious) {
            refreshXtreamPage(xtreamPage.pageIndex - 1)
        }
    }

    suspend fun nextXtreamPage() {
        if (xtreamPage.hasNext) {
            refreshXtreamPage(xtreamPage.pageIndex + 1)
        }
    }

    fun selectXtreamItem(providerId: String) {
        if (selectedXtreamItemId != providerId) {
            selectedXtreamItemId = providerId
            seriesDetailsStatus = SeriesDetailsStatus.Idle
            movieDetailsStatus = MovieDetailsStatus.Idle
            liveEpgStatus = LiveEpgStatus.Idle
        }
    }

    suspend fun loadSelectedLiveEpg() {
        val selected = selectedXtreamItem ?: return
        if (selected.contentType != XtreamContentType.LIVE) return
        if (liveEpgStatus is LiveEpgStatus.Loading) return
        liveEpgStatus = LiveEpgStatus.Loading
        runCatching {
            withContext(Dispatchers.IO) { xtreamRepository.shortEpg(selected.providerId) }
        }.onSuccess { epg ->
            if (selectedXtreamItemId == selected.providerId) {
                val (now, next) = epg.nowAndNext(System.currentTimeMillis() / 1_000L)
                liveEpgStatus = LiveEpgStatus.Loaded(now, next)
            }
        }.onFailure { error ->
            error.rethrowIfCancellation()
            if (selectedXtreamItemId == selected.providerId) {
                liveEpgStatus = LiveEpgStatus.Unavailable
            }
        }
    }

    suspend fun loadSelectedMovieDetails() {
        val selected = selectedXtreamItem ?: return
        if (selected.contentType != XtreamContentType.MOVIE) return
        if (movieDetailsStatus is MovieDetailsStatus.Loading) return
        movieDetailsStatus = MovieDetailsStatus.Loading

        runCatching {
            withContext(Dispatchers.IO) {
                xtreamRepository.movieDetails(selected.providerId)
            }
        }.onSuccess { details ->
            if (selectedXtreamItemId == selected.providerId) {
                movieDetailsStatus = MovieDetailsStatus.Loaded(details)
                details.cast.castNames().forEach { person ->
                    movieAppearances
                        .getOrPut(person.lowercase(Locale.ROOT), ::LinkedHashMap)[selected.providerId] = selected
                }
            }
        }.onFailure { error ->
            error.rethrowIfCancellation()
            movieDetailsStatus = MovieDetailsStatus.Error(error.toSafeXtreamMessage())
        }
    }

    suspend fun loadSelectedSeriesDetails() {
        val selected = selectedXtreamItem ?: return
        if (selected.contentType != XtreamContentType.SERIES) return
        if (seriesDetailsStatus is SeriesDetailsStatus.Loading) return
        seriesDetailsStatus = SeriesDetailsStatus.Loading

        runCatching {
            withContext(Dispatchers.IO) {
                xtreamRepository.seriesDetails(selected.providerId)
            }
        }.onSuccess { details ->
            if (selectedXtreamItemId == selected.providerId) {
                seriesDetailsStatus = SeriesDetailsStatus.Loaded(details)
            }
        }.onFailure { error ->
            error.rethrowIfCancellation()
            seriesDetailsStatus = SeriesDetailsStatus.Error(error.toSafeXtreamMessage())
        }
    }

    fun openXtreamAfterConfirmation(target: XtreamPlaybackTarget): ExternalOpenResult =
        runCatching {
            val oneTimeUri = xtreamRepository.buildConfirmedPlaybackUri(target)
            openUriExternally(oneTimeUri)
        }.getOrDefault(ExternalOpenResult.Failed)

    fun prepareXtreamPlayback(target: XtreamPlaybackTarget, title: String): DesktopPlaybackRequest? =
        runCatching {
            DesktopPlaybackRequest(
                title = title.take(180),
                uri = xtreamRepository.buildConfirmedPlaybackUri(target),
            )
        }.getOrNull()

    fun prepareLocalPlayback(channel: Channel): DesktopPlaybackRequest? =
        runCatching {
            val uri = URI(channel.streamUri)
            require(uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https", "file"))
            DesktopPlaybackRequest(channel.name.take(180), uri)
        }.getOrNull()

    fun openPublicTrailer(youtubeTrailerId: String): ExternalOpenResult {
        if (!youtubeTrailerId.matches(Regex("[A-Za-z0-9_-]{6,32}"))) {
            return ExternalOpenResult.Failed
        }
        return runCatching {
            openUriExternally(URI("https://www.youtube.com/watch?v=$youtubeTrailerId"))
        }.getOrDefault(ExternalOpenResult.Failed)
    }

    fun openPerson(name: String) {
        val cleanName = name.trim().take(100)
        if (cleanName.isBlank()) return
        selectedPerson =
            PersonFilmography(
                name = cleanName,
                items = movieAppearances[cleanName.lowercase(Locale.ROOT)]?.values?.toList().orEmpty(),
            )
    }

    fun closePerson() {
        selectedPerson = null
    }

    fun selectSource(sourceId: String) {
        selectedSourceId = sourceId
        if (xtreamSummary?.sourceId == sourceId) {
            return
        }
        selectedCategoryId = null
        selectedChannelId =
            catalogs
                .firstOrNull { it.source.id == sourceId }
                ?.channels
                ?.firstOrNull()
                ?.id
        searchQuery = ""
    }

    fun selectCategory(categoryId: String?) {
        selectedCategoryId = categoryId
        selectedChannelId = visibleChannels.firstOrNull()?.id
    }

    fun selectChannel(channelId: String) {
        selectedChannelId = channelId
    }

    fun updateSearch(query: String) {
        searchQuery = query.take(MAX_SEARCH_LENGTH)
        selectedChannelId = visibleChannels.firstOrNull()?.id
    }

    fun forgetSelectedSource() {
        if (isXtreamSelected) {
            disconnectXtream()
            return
        }
        val sourceId = selectedCatalog?.source?.id ?: return
        localRepository.forget(sourceId)
        catalogs = catalogs.filterNot { it.source.id == sourceId }
        selectedSourceId = catalogs.firstOrNull()?.source?.id ?: xtreamSummary?.sourceId
        selectedCategoryId = null
        selectedChannelId = catalogs.firstOrNull()?.channels?.firstOrNull()?.id
        searchQuery = ""
        importStatus = ImportStatus.Idle
    }

    fun disconnectXtream() {
        rememberedXtreamStore.clear()
        xtreamRepository.clear()
        val wasSelected = isXtreamSelected
        clearXtreamUiState()
        xtreamStatus = XtreamStatus.Disconnected
        if (wasSelected) {
            selectedSourceId = catalogs.firstOrNull()?.source?.id
            selectedCategoryId = null
            selectedChannelId = catalogs.firstOrNull()?.channels?.firstOrNull()?.id
        }
    }

    fun clearSensitiveData() {
        xtreamRepository.clear()
        localRepository.clear()
        catalogs = emptyList()
        selectedSourceId = null
        clearXtreamUiState()
        xtreamStatus = XtreamStatus.Disconnected
    }

    fun dismissStatus() {
        importStatus = ImportStatus.Idle
        if (xtreamStatus is XtreamStatus.Error) {
            xtreamStatus =
                if (xtreamSummary == null) {
                    XtreamStatus.Disconnected
                } else {
                    XtreamStatus.Connected
                }
        }
    }

    private suspend fun refreshXtreamPage(pageIndex: Int) {
        val requestGeneration = ++xtreamPageRequestGeneration
        val type = xtreamContentType
        val category = selectedXtreamCategoryId
        val query = xtreamSearchQuery
        val releaseYear = selectedXtreamYear
        val allowedProviderIds =
            if (favoritesOnly) {
                val prefix = "${type.name}:"
                favoriteKeys.mapNotNullTo(LinkedHashSet()) { key -> key.removePrefix(prefix).takeIf { key.startsWith(prefix) } }
            } else {
                null
            }
        val page =
            withContext(Dispatchers.Default) {
                xtreamRepository.page(
                    contentType = type,
                    categoryId = category,
                    query = query,
                    requestedPage = pageIndex,
                    releaseYear = releaseYear,
                    allowedProviderIds = allowedProviderIds,
                    kidsMode = activeProfile?.isKids == true,
                )
            }
        if (requestGeneration == xtreamPageRequestGeneration) {
            xtreamPage = page
            selectedXtreamItemId = page.items.firstOrNull()?.providerId
        }
    }

    private fun clearXtreamUiState() {
        xtreamPageRequestGeneration += 1
        xtreamSummary = null
        xtreamContentType = XtreamContentType.LIVE
        xtreamCategories = emptyList()
        selectedXtreamCategoryId = null
        selectedXtreamYear = null
        xtreamSearchQuery = ""
        xtreamPage = XtreamCatalogPage.empty()
        selectedXtreamItemId = null
        seriesDetailsStatus = SeriesDetailsStatus.Idle
        movieDetailsStatus = MovieDetailsStatus.Idle
        liveEpgStatus = LiveEpgStatus.Idle
        movieAppearances.clear()
        selectedPerson = null
        favoritesOnly = false
    }

    private fun visibleXtreamCategories(contentType: XtreamContentType): List<XtreamCategory> =
        xtreamRepository.categories(contentType).let { categories ->
            if (activeProfile?.isKids == true) {
                categories.filterNot { FamilyContentPolicy.isExplicitAdultLabel(it.name) }
            } else {
                categories
            }
        }

    private fun Throwable.toSafeImportMessage(): String =
        when (this) {
            is NoSuchFileException -> "O arquivo selecionado não existe mais."
            is AccessDeniedException -> "O sistema não permitiu ler esse arquivo."
            is SecurityException -> "O acesso ao arquivo foi bloqueado pelo sistema."
            else ->
                "Não foi possível importar a lista. Verifique se o arquivo é M3U/M3U8 válido e tente novamente."
        }

    private fun Throwable.rethrowIfCancellation() {
        if (this is CancellationException) throw this
    }

    private fun Throwable.toSafeXtreamMessage(): String =
        when ((this as? XtreamClientException)?.reason) {
            XtreamFailureReason.INVALID_SERVER -> "O endereço do servidor não é válido."
            XtreamFailureReason.AUTHENTICATION -> "O servidor recusou o usuário ou a senha."
            XtreamFailureReason.NETWORK -> "Não foi possível alcançar o servidor."
            XtreamFailureReason.HTTP -> "O servidor respondeu com um erro HTTP."
            XtreamFailureReason.RESPONSE_TOO_LARGE ->
                "O catálogo excedeu o limite seguro desta prévia."
            XtreamFailureReason.INVALID_RESPONSE ->
                "O servidor não retornou um catálogo Xtream compatível."
            null -> "A operação Xtream não pôde ser concluída."
        }

    private companion object {
        const val MAX_SEARCH_LENGTH = 120
        const val ZERO_CHAR = '\u0000'
    }
}

private fun String?.castNames(): List<String> =
    this
        ?.split(',', ';', '|')
        ?.map(String::trim)
        ?.filter { it.length in 2..100 }
        ?.distinctBy { it.lowercase(Locale.ROOT) }
        .orEmpty()

data class PersonFilmography(
    val name: String,
    val items: List<XtreamCatalogItem>,
)

sealed interface ImportStatus {
    data object Idle : ImportStatus

    data object Loading : ImportStatus

    data class Success(
        val channelCount: Int,
        val warningCount: Int,
    ) : ImportStatus

    data class Error(
        val message: String,
    ) : ImportStatus
}

sealed interface XtreamStatus {
    data object Disconnected : XtreamStatus

    data object Connecting : XtreamStatus

    data object Connected : XtreamStatus

    data class LoadingCatalog(
        val contentType: XtreamContentType,
    ) : XtreamStatus

    data class Error(
        val message: String,
    ) : XtreamStatus
}

sealed interface SeriesDetailsStatus {
    data object Idle : SeriesDetailsStatus

    data object Loading : SeriesDetailsStatus

    data class Loaded(
        val details: XtreamSeriesDetails,
    ) : SeriesDetailsStatus

    data class Error(
        val message: String,
    ) : SeriesDetailsStatus
}

sealed interface MovieDetailsStatus {
    data object Idle : MovieDetailsStatus

    data object Loading : MovieDetailsStatus

    data class Loaded(
        val details: XtreamMovieDetails,
    ) : MovieDetailsStatus

    data class Error(
        val message: String,
    ) : MovieDetailsStatus
}

sealed interface LiveEpgStatus {
    data object Idle : LiveEpgStatus

    data object Loading : LiveEpgStatus

    data class Loaded(
        val now: XtreamEpgProgram?,
        val next: XtreamEpgProgram?,
    ) : LiveEpgStatus

    /** EPG is optional and must never block channel playback. */
    data object Unavailable : LiveEpgStatus
}
