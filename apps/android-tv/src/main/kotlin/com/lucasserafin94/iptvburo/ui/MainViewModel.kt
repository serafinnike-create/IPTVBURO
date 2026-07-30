package com.lucasserafin94.iptvburo.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.core.logging.AppLogger
import com.lucasserafin94.iptvburo.data.preferences.OnboardingPreferences
import com.lucasserafin94.iptvburo.data.repository.CatalogRepository
import com.lucasserafin94.iptvburo.di.IoDispatcher
import com.lucasserafin94.iptvburo.domain.model.Category
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.Source
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
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
    private val logger: AppLogger,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val context: Context
        get() = contextProvider.get()

    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()

    private val backStack = ArrayDeque<AppContent>()
    private var catalogJob: Job? = null

    init {
        observeOnboarding()
        observeSources()
    }

    fun acceptLegalNotice() {
        viewModelScope.launch {
            onboardingPreferences.acceptLegalNotice()
        }
    }

    fun selectSection(section: AppSection) {
        catalogJob?.cancel()
        backStack.clear()

        when (section) {
            AppSection.HOME -> updateDestination(section, AppContent.Home)
            AppSection.SOURCES -> updateDestination(section, AppContent.Sources)
            AppSection.SETTINGS -> updateDestination(section, AppContent.Settings)
            AppSection.LIVE -> {
                val firstSource = mutableState.value.sources.firstOrNull()
                if (firstSource == null) {
                    updateDestination(
                        section = AppSection.LIVE,
                        content = AppContent.SectionPlaceholder(AppSection.LIVE),
                    )
                } else {
                    updateDestination(
                        section = AppSection.LIVE,
                        content = AppContent.Categories(
                            sourceId = firstSource.id,
                            sourceName = firstSource.name,
                        ),
                    )
                    observeCategories(firstSource)
                }
            }

            AppSection.MOVIES,
            AppSection.SERIES,
            AppSection.DISCOVER,
            AppSection.MY_BURO,
            AppSection.SEARCH,
            AppSection.PROFILE,
            -> {
                updateDestination(section, AppContent.SectionPlaceholder(section))
            }
        }
    }

    fun openStory(itemId: String) {
        if (itemId.isBlank()) return
        if (mutableState.value.content != AppContent.Home) return

        rememberLastFocusedHomeItem(itemId)
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
        navigate(
            AppContent.Categories(
                sourceId = source.id,
                sourceName = source.name,
            ),
        )
        observeCategories(source)
    }

    fun openCategory(category: CategoryUi) {
        val categoriesContent = mutableState.value.content as? AppContent.Categories ?: return
        navigate(
            AppContent.Channels(
                sourceId = categoriesContent.sourceId,
                sourceName = categoriesContent.sourceName,
                categoryId = category.id,
                categoryName = category.name,
            ),
        )
        observeChannels(
            sourceId = categoriesContent.sourceId,
            categoryId = category.id,
        )
    }

    fun openChannel(channel: ChannelUi) {
        navigate(AppContent.Player(channel))
    }

    fun goBack(): Boolean {
        val previous = backStack.pollLast()
        if (previous == null) {
            if (mutableState.value.content != AppContent.Home) {
                updateDestination(AppSection.HOME, AppContent.Home)
                return true
            }
            return false
        }
        mutableState.update { it.copy(content = previous) }

        when (previous) {
            is AppContent.Categories -> {
                val source = mutableState.value.sources.firstOrNull { it.id == previous.sourceId }
                if (source != null) observeCategories(source)
            }

            is AppContent.Channels -> observeChannels(previous.sourceId, previous.categoryId)
            else -> catalogJob?.cancel()
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
                backStack.clear()
                mutableState.update {
                    it.copy(
                        isImporting = false,
                        lastImportedChannelCount = result.importedChannelCount,
                        hasImportError = false,
                        section = AppSection.SOURCES,
                        content = AppContent.Sources,
                    )
                }
            }.onFailure { error ->
                logger.error(TAG, "Playlist import failed", error)
                mutableState.update {
                    it.copy(
                        isImporting = false,
                        lastImportedChannelCount = null,
                        hasImportError = true,
                    )
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
                }
        }
    }

    private fun observeCategories(source: SourceUi) {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            combine(
                catalogRepository.observeCategories(source.id),
                catalogRepository.observeChannels(source.id),
            ) { categories, channels ->
                val counts = channels.groupingBy(Channel::categoryId).eachCount()
                buildList {
                    add(
                        CategoryUi(
                            id = null,
                            name = context.getString(R.string.categories_all),
                            channelCount = channels.size,
                        ),
                    )
                    addAll(
                        categories.map { category ->
                            category.toUi(counts[category.id] ?: 0)
                        },
                    )
                }
            }.catch { error ->
                logger.error(TAG, "Could not observe categories", error)
                emit(emptyList())
            }.collect { categories ->
                mutableState.update { it.copy(categories = categories) }
            }
        }
    }

    private fun observeChannels(
        sourceId: String,
        categoryId: String?,
    ) {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            catalogRepository.observeChannels(sourceId, categoryId)
                .catch { error ->
                    logger.error(TAG, "Could not observe channels", error)
                    emit(emptyList())
                }
                .collect { channels ->
                    mutableState.update {
                        it.copy(channels = channels.map { channel -> channel.toUi() })
                    }
                }
        }
    }

    private fun navigate(content: AppContent) {
        val current = mutableState.value.content
        if (current != content) {
            backStack.addLast(current)
        }
        mutableState.update { it.copy(content = content) }
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
            )
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        val name = context.contentResolver.query(
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
            name = name
                .removePlaylistExtension()
                .ifBlank { name },
            channelCount = channelCount,
        )

    private fun Category.toUi(channelCount: Int): CategoryUi =
        CategoryUi(
            id = id,
            name = name,
            channelCount = channelCount,
        )

    private fun Channel.toUi(): ChannelUi =
        ChannelUi(
            id = id,
            name = name,
            categoryName = null,
            streamUrl = streamUri,
            logoUrl = logoUri,
            requestHeaders = requestHeaders,
        )

    private companion object {
        const val TAG = "MainViewModel"
    }
}
