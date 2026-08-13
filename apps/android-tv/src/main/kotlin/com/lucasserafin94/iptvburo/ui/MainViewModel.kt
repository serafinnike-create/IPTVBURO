package com.lucasserafin94.iptvburo.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.core.logging.AppLogger
import com.lucasserafin94.iptvburo.data.download.AndroidDownloadManager
import com.lucasserafin94.iptvburo.data.download.DownloadResult
import com.lucasserafin94.iptvburo.data.discovery.StreamingDiscoveryRepository
import com.lucasserafin94.iptvburo.data.preferences.CatalogueGuard
import com.lucasserafin94.iptvburo.data.preferences.OnboardingPreferences
import com.lucasserafin94.iptvburo.data.preferences.SubtitleSettings
import com.lucasserafin94.iptvburo.data.licensing.AndroidLicenseService
import com.lucasserafin94.iptvburo.data.licensing.RedeemOutcome
import com.lucasserafin94.iptvburo.data.security.MetadataKeyStore
import com.lucasserafin94.iptvburo.data.repository.CatalogRepository
import com.lucasserafin94.iptvburo.data.repository.LiveProgram
import com.lucasserafin94.iptvburo.data.repository.CatalogCursor
import com.lucasserafin94.iptvburo.data.repository.BuroProfile
import com.lucasserafin94.iptvburo.data.repository.ProfileType
import com.lucasserafin94.iptvburo.data.repository.UserLibraryRepository
import com.lucasserafin94.iptvburo.data.repository.toProfile
import com.lucasserafin94.iptvburo.data.repository.StalkerImportRequest
import com.lucasserafin94.iptvburo.data.repository.XtreamImportRequest
import com.lucasserafin94.iptvburo.data.repository.XtreamImportStage
import com.lucasserafin94.iptvburo.stalker.StalkerClientException
import com.lucasserafin94.iptvburo.stalker.StalkerFailureReason
import com.lucasserafin94.iptvburo.stalker.StalkerMacAddress
import com.lucasserafin94.iptvburo.ui.capabilities.AndroidPlatformCapabilities
import com.lucasserafin94.iptvburo.ui.screens.playbackProgressIdentity
import com.lucasserafin94.iptvburo.ui.screens.yearFromReleaseDate
import com.lucasserafin94.iptvburo.ui.cast.CastController
import com.lucasserafin94.iptvburo.ui.cast.CastSender
import com.lucasserafin94.iptvburo.ui.cast.CastTarget
import com.lucasserafin94.iptvburo.ui.cast.CastUiState
import com.lucasserafin94.iptvburo.di.IoDispatcher
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.CatalogueFilter
import com.lucasserafin94.iptvburo.domain.model.CatalogueLayout
import com.lucasserafin94.iptvburo.domain.model.Category
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.ContentKind
import com.lucasserafin94.iptvburo.domain.model.TitleShareLink
import com.lucasserafin94.iptvburo.domain.model.Episode
import com.lucasserafin94.iptvburo.domain.model.FamilyContentPolicy
import com.lucasserafin94.iptvburo.domain.model.LibraryOfferPolicy
import com.lucasserafin94.iptvburo.domain.model.asExternalCandidate
import com.lucasserafin94.iptvburo.domain.model.MovieDetails
import com.lucasserafin94.iptvburo.domain.model.ParentalPin
import com.lucasserafin94.iptvburo.domain.model.PlaybackContentType
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgress
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgressIdentity
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgressRepository
import com.lucasserafin94.iptvburo.domain.model.SeriesDetails
import com.lucasserafin94.iptvburo.domain.model.StreamingOffer
import com.lucasserafin94.iptvburo.domain.model.SubtitlePresentation
import com.lucasserafin94.iptvburo.metadata.TmdbServiceShelf
import com.lucasserafin94.iptvburo.domain.model.Source
import com.lucasserafin94.iptvburo.domain.model.SourceType
import com.lucasserafin94.iptvburo.metadata.TmdbClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.ArrayDeque
import java.util.Locale
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

@HiltViewModel
class MainViewModel @Inject constructor(
    @param:ApplicationContext private val contextProvider: Provider<Context>,
    private val catalogRepository: CatalogRepository,
    private val onboardingPreferences: OnboardingPreferences,
    private val userLibraryRepository: UserLibraryRepository,
    private val downloadManager: AndroidDownloadManager,
    private val licenseService: AndroidLicenseService,
    private val metadataKeyStore: MetadataKeyStore,
    private val streamingDiscoveryRepository: StreamingDiscoveryRepository,
    private val okHttpClient: OkHttpClient,
    private val playbackProgressRepository: PlaybackProgressRepository,
    private val catalogueGuardPreferences: CatalogueGuard,
    private val subtitlePreferences: SubtitleSettings,
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
    private var bootBackdropJob: Job? = null
    private var favoritesJob: Job? = null
    private var licenseJob: Job? = null
    private var subscriptionsJob: Job? = null
    private var subscriptionSelectionJob: Job? = null
    private var sharedLinkJob: Job? = null
    private var keyInspectionJob: Job? = null
    private var searchJob: Job? = null

    /**
     * How many downloads may be transferring at once.
     *
     * Downloading a season turns one tap into dozens of coroutines. Without this they would each
     * open a connection to the provider simultaneously, which saturates the user's line, makes
     * every file slower than fetching them in turn, and looks indistinguishable from abuse to a
     * provider — the sort of thing that gets an account throttled or closed.
     *
     * Three rather than one: a single slot wastes bandwidth whenever a transfer is waiting on the
     * server, and a phone on wifi comfortably handles a few at once.
     */
    private val downloadSlots = Semaphore(DOWNLOAD_SLOTS)

    /**
     * A shared title waiting for a catalogue to look it up in.
     *
     * A link tapped from a cold start arrives before the database has any rows, so it is held here
     * and retried once the catalogue is ready. Cleared only when it actually opens — a link that
     * finds nothing stays pending, so importing the list afterwards still lands on the film.
     */
    private var pendingSharedTitle: TitleShareLink? = null

    /**
     * Names already looked up this session, so a details screen redrawn on every recomposition does
     * not re-ask TMDb for the same dozen faces. A miss is cached as null: a person TMDb does not
     * know must not be searched for again on every frame.
     */
    private val castPhotoLookups = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** Last progress reading per download, so a transfer rate can be measured between reports. */
    private val downloadRateSamples = java.util.concurrent.ConcurrentHashMap<String, DownloadRateSample>()

    /** One progress reading: how many bytes had arrived, and when. */
    private data class DownloadRateSample(val bytes: Long, val atEpochMillis: Long)
    private var seriesEpisodes: Map<String, Episode> = emptyMap()
    private val knownMovieChannels = LinkedHashMap<String, ChannelUi>()
    private val actorMovieIds = LinkedHashMap<String, LinkedHashSet<String>>()
    private var nextChannelCursor: CatalogCursor? = null

    init {
        observeOnboarding()
        observeProfiles()
        observeSources()
        observeCatalogueGuard()
        observeSubtitles()
    }

    /**
     * Hidden categories, locked categories and whether a PIN exists.
     *
     * Collected here rather than read on demand so hiding a category takes effect on the catalogue
     * immediately: the list already on screen is re-filtered from the same state the switch wrote.
     */
    private fun observeCatalogueGuard() {
        viewModelScope.launch {
            catalogueGuardPreferences.hiddenCategoryIds.collect { hidden ->
                mutableState.update { state ->
                    // Re-derived from the unfiltered list rather than added to the filtered one,
                    // so unhiding restores a category in its original position instead of
                    // appending it to the end, and the "all" entry (a null id, never hidden) is
                    // kept wherever the catalogue put it.
                    val visible =
                        if (state.allCategories.isEmpty()) {
                            state.categories
                        } else {
                            state.categories.filter { category -> category.id == null } +
                                state.allCategories.filterNot { category ->
                                    category.id != null && category.id in hidden
                                }
                        }
                    state.copy(hiddenCategoryIds = hidden, categories = visible)
                }
            }
        }
        viewModelScope.launch {
            catalogueGuardPreferences.parentalLock.collect { lock ->
                mutableState.update { it.copy(parentalLock = lock) }
            }
        }
        viewModelScope.launch {
            catalogueGuardPreferences.hasPin.collect { hasPin ->
                mutableState.update { it.copy(hasParentalPin = hasPin) }
            }
        }
    }

    private fun observeSubtitles() {
        viewModelScope.launch {
            subtitlePreferences.presentation.collect { presentation ->
                mutableState.update { it.copy(subtitles = presentation) }
            }
        }
    }

    /**
     * Every category from every section, for the settings list.
     *
     * Loaded on request rather than with the catalogue, because settings needs all three sections
     * at once while the catalogue only ever shows one. Reading whichever section happened to be
     * open meant a user in Filmes could not reach a series category at all — the switch for it was
     * simply not on the screen, with nothing to say why.
     */
    fun loadAllCategoriesForSettings() {
        val sourceId = mutableState.value.sources.firstOrNull()?.id ?: return
        viewModelScope.launch {
            val loaded =
                listOf(
                    CatalogContentType.LIVE,
                    CatalogContentType.MOVIE,
                    CatalogContentType.SERIES,
                ).flatMap { type ->
                    runCatching {
                        catalogRepository
                            .observeCategories(sourceId = sourceId, contentType = type)
                            .first()
                    }.getOrDefault(emptyList())
                        .map { category ->
                            CategoryUi(
                                id = category.id,
                                name = category.name,
                                channelCount = 0,
                            )
                        }
                }
            // A category id can repeat across sections; the switches are keyed by id, so a
            // duplicate would give the list two rows that disagree with each other.
            mutableState.update { it.copy(allCategories = loaded.distinctBy(CategoryUi::id)) }
        }
    }

    fun setCategoryHidden(
        categoryId: String,
        hidden: Boolean,
    ) {
        viewModelScope.launch { catalogueGuardPreferences.setCategoryHidden(categoryId, hidden) }
    }

    fun setCategoryLocked(
        categoryId: String,
        locked: Boolean,
    ) {
        viewModelScope.launch { catalogueGuardPreferences.setCategoryLocked(categoryId, locked) }
    }

    fun setLockAdultCategories(locked: Boolean) {
        viewModelScope.launch { catalogueGuardPreferences.setLockAdultCategories(locked) }
    }

    /** Sets or changes the PIN. Reports the reason rather than failing silently. */
    fun setParentalPin(
        newPin: String,
        currentPin: String?,
    ) {
        viewModelScope.launch {
            if (!ParentalPin.isWellFormed(newPin)) {
                mutableState.update { it.copy(parentalMessage = ParentalMessage.BAD_FORMAT) }
                return@launch
            }
            val saved = catalogueGuardPreferences.setPin(newPin, currentPin)
            mutableState.update {
                it.copy(parentalMessage = if (saved) null else ParentalMessage.WRONG_PIN)
            }
        }
    }

    fun clearParentalPin(currentPin: String) {
        viewModelScope.launch {
            val cleared = catalogueGuardPreferences.clearPin(currentPin)
            mutableState.update {
                it.copy(parentalMessage = if (cleared) null else ParentalMessage.WRONG_PIN)
            }
        }
    }

    fun saveSubtitlePresentation(presentation: SubtitlePresentation) {
        viewModelScope.launch { subtitlePreferences.save(presentation) }
    }

    /**
     * Answers the PIN prompt. A right answer opens the category that was waiting on it.
     *
     * The unlock lasts for this attempt only — it is not remembered — because a lock that stays
     * open after one use is a lock that is open whenever the child picks the device up next.
     */
    fun submitParentalPin(pin: String) {
        val pending = mutableState.value.pendingUnlock ?: return
        viewModelScope.launch {
            if (catalogueGuardPreferences.checkPin(pin)) {
                mutableState.update {
                    it.copy(pendingUnlock = null, parentalMessage = null)
                }
                // Rebuilt from the pending prompt rather than held as a CategoryUi: only the id
                // and name decide where it opens, and the count and artwork would be stale.
                openCategory(
                    CategoryUi(
                        id = pending.categoryId,
                        name = pending.categoryName,
                        channelCount = 0,
                    ),
                    bypassLock = true,
                )
            } else {
                mutableState.update { it.copy(parentalMessage = ParentalMessage.WRONG_PIN) }
            }
        }
    }

    fun dismissParentalPrompt() {
        mutableState.update { it.copy(pendingUnlock = null, parentalMessage = null) }
    }

    private fun checkLicense() {
        licenseJob?.cancel()
        val knownDevice = mutableState.value.deviceId
        mutableState.update { it.copy(license = LicenseUiState.Checking(knownDevice)) }
        licenseJob =
            viewModelScope.launch {
                val status = withContext(ioDispatcher) { licenseService.check() }
                mutableState.update {
                    it.copy(
                        license = status.toUiState(),
                        deviceId = status.deviceId.takeIf(String::isNotBlank),
                    )
                }
            }
    }

    fun acceptLegalNotice() {
        viewModelScope.launch {
            onboardingPreferences.acceptLegalNotice()
        }
    }

    fun refreshLicense() {
        checkLicense()
    }

    /**
     * Looks up what the typed key is, without spending it.
     *
     * Called as the field settles rather than on every keystroke: the request is cheap but it is
     * still a network call, and a half-typed key is not a question worth asking. Nothing is granted
     * from the answer — [redeemLicense] remains the only thing that changes a licence.
     */
    fun inspectKey(key: String) {
        val blocked = mutableState.value.license as? LicenseUiState.Blocked ?: return
        val clean = key.trim()
        if (clean.length < MIN_INSPECTABLE_KEY) {
            // Too short to mean anything yet: clear a previous answer rather than leaving a stale
            // verdict next to a key the user has since edited.
            if (blocked.typedKeyState != null) {
                mutableState.update { it.copy(license = blocked.copy(typedKeyState = null)) }
            }
            return
        }

        keyInspectionJob?.cancel()
        keyInspectionJob =
            viewModelScope.launch {
                val state = withContext(ioDispatcher) { licenseService.keyState(clean) }
                mutableState.update { current ->
                    // Ignore an answer that arrived after the user changed the key or got in.
                    val stillBlocked = current.license as? LicenseUiState.Blocked ?: return@update current
                    current.copy(license = stillBlocked.copy(typedKeyState = state))
                }
            }
    }

    fun redeemLicense(key: String) {
        if (key.isBlank()) return
        // Deliberately *not* gated on the licence being Blocked.
        //
        // It used to start with `license as? Blocked ?: return`, which meant redeeming did nothing
        // at all while the app was inside its trial — the commonest moment to type a key, and the
        // one the Settings card exists for. The key never left the phone, nothing was shown, and
        // the trial ran on: reported as "I typed my key, nothing happened, still seven days".
        //
        // Someone extending a licence they can currently use is the normal case, not an edge one.
        val blocked = mutableState.value.license as? LicenseUiState.Blocked
        if (blocked?.isWorking == true || mutableState.value.redemption == RedemptionUi.Working) return

        licenseJob?.cancel()
        mutableState.update {
            it.copy(
                license = blocked?.copy(isWorking = true, activationFailed = false) ?: it.license,
                // The one place the Settings card can read, since it never sees the gate's state.
                redemption = RedemptionUi.Working,
            )
        }
        licenseJob =
            viewModelScope.launch {
                when (val outcome = withContext(ioDispatcher) { licenseService.redeem(key) }) {
                    is RedeemOutcome.Failed ->
                        mutableState.update {
                            it.copy(
                                license =
                                    blocked?.copy(
                                        isWorking = false,
                                        activationFailed = true,
                                        // Carried so the gate can say *which* problem it was. A
                                        // mistyped key, a key already bound to another device and
                                        // a dead connection need three different actions from the
                                        // user, and one sentence for all three told them nothing.
                                        activationFailure = outcome.reason,
                                    ) ?: it.license,
                                redemption = RedemptionUi.Failed(outcome.reason),
                            )
                        }

                    is RedeemOutcome.Activated ->
                        mutableState.update {
                            it.copy(
                                license = outcome.status.toUiState(),
                                deviceId = outcome.status.deviceId.takeIf(String::isNotBlank),
                                redemption =
                                    RedemptionUi.Activated(outcome.status.daysRemaining),
                            )
                        }
                }
            }
    }

    /** Clears the redemption notice once the user has read it. */
    fun dismissRedemptionNotice() {
        if (mutableState.value.redemption != RedemptionUi.Idle) {
            mutableState.update { it.copy(redemption = RedemptionUi.Idle) }
        }
    }

    /**
     * Searches the local catalogue for [query].
     *
     * Debounced by the screen rather than here: this runs whenever it is called, and the caller
     * decides how often that is. Cancelling the previous job means a fast typist produces one
     * query against the database rather than one per keystroke left running.
     *
     * Searches only what is already imported. Nothing is asked of the provider, so this works
     * offline and cannot leak the query to anyone.
     */
    fun search(query: String) {
        val clean = query.trim()
        searchJob?.cancel()
        if (clean.isEmpty()) {
            mutableState.update {
                it.copy(searchQuery = query, searchResults = emptyList(), isSearching = false)
            }
            return
        }

        mutableState.update { it.copy(searchQuery = query, isSearching = true) }
        searchJob =
            viewModelScope.launch {
                val found =
                    withContext(ioDispatcher) {
                        runCatching { catalogRepository.search(clean) }.getOrDefault(emptyList())
                    }
                val visible =
                    found
                        // Blank rather than the category *id*, which is what was passed here: the
                        // field is a display name, and a UUID was being printed under every
                        // result. The search screen labels rows by kind instead, and nothing else
                        // reads this for a search result.
                        .map { channel -> channel.toCatalogUi("") }
                        .filterKidsContentIfNeeded(mutableState.value.activeProfile)
                mutableState.update { state ->
                    // Ignore an answer for a query the user has already moved on from.
                    if (state.searchQuery.trim() != clean) {
                        state
                    } else {
                        state.copy(searchResults = visible, isSearching = false)
                    }
                }
            }
    }

    fun selectSection(section: AppSection) {
        if (section == AppSection.DOWNLOADS && !AndroidPlatformCapabilities.offlineSupported(context)) return
        // Same guard as Downloads: the navigation already hides this, but a destination must not be
        // openable by any other route while the thing behind it does not exist.
        if (section == AppSection.SUBSCRIPTIONS && !mutableState.value.subscriptions.capability.isVisible) return
        cancelCatalogWork()
        backStack.clear()
        clearEphemeralSeries()

        when (section) {
            AppSection.HOME -> {
                updateDestination(section, AppContent.Home)
                // The service shelves are drawn on the home screen too, so they are loaded here
                // rather than only when Assinaturas is opened. Cached after the first call, so
                // returning home does not re-fetch them.
                loadSubscriptionShelves()
            }
            AppSection.SOURCES -> updateDestination(section, AppContent.Sources)
            AppSection.SETTINGS -> {
                // The category list is settings-only, so this is the moment to fetch it.
                loadAllCategoriesForSettings()
                updateDestination(section, AppContent.Settings)
            }
            AppSection.LIVE,
            AppSection.MOVIES,
            AppSection.SERIES,
            -> openPrimaryCatalog(section)

            AppSection.MY_BURO -> {
                updateDestination(section, AppContent.Favorites)
                loadFavorites()
                loadContinueWatching(mutableState.value.activeProfile?.id)
            }

            // Re-checks disk on the way in: a copy finished in a previous session is only known to
            // be stored once the file is looked for, and this is the one screen that claims to list
            // everything the user has offline.
            AppSection.DOWNLOADS -> {
                updateDestination(section, AppContent.Downloads)
                restoreDownloads()
            }

            // Always returns to the shelves rather than to whatever title was open last: the
            // destination answers "what is on these services", and reopening someone's earlier
            // lookup is not what pressing Assinaturas asks for.
            AppSection.SUBSCRIPTIONS -> {
                updateDestination(section, AppContent.Subscriptions)
                closeSubscriptionTitle()
                loadSubscriptionShelves()
            }

            // Both read the same store as Favourites, so both refresh it on the way in: a title
            // finished on another device is only known once it is asked for.
            AppSection.CONTINUE_WATCHING -> {
                updateDestination(section, AppContent.ContinueWatching)
                loadContinueWatching(mutableState.value.activeProfile?.id)
            }

            AppSection.HISTORY -> {
                updateDestination(section, AppContent.History)
                loadContinueWatching(mutableState.value.activeProfile?.id)
            }

            AppSection.PROFILE -> updateDestination(section, AppContent.Profiles)

            AppSection.DISCOVER,
            AppSection.SEARCH,
            -> updateDestination(section, AppContent.SectionPlaceholder(section))
        }
    }

    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            val profile = userLibraryRepository.getProfile(profileId) ?: return@launch
            onboardingPreferences.selectProfile(profile.id)
            if (mutableState.value.sources.isEmpty()) {
                updateDestination(AppSection.SOURCES, AppContent.Sources)
            } else {
                updateDestination(AppSection.HOME, AppContent.Home)
            }
        }
    }

    fun requestProfileSelection() {
        selectSection(AppSection.PROFILE)
    }

    fun saveTmdbKey(apiKey: String) {
        val profileId = mutableState.value.activeProfile?.id ?: return
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) { metadataKeyStore.save(profileId, apiKey) }
            }.onSuccess {
                val configured = apiKey.trim().isNotEmpty()
                mutableState.update {
                    it.copy(
                        tmdbKeyConfigured = configured,
                        // Saving a key is what makes Assinaturas real, so the destination appears
                        // or disappears here rather than only after the next profile reload.
                        //
                        // Every cached result is dropped with it: shelves fetched under the old key
                        // answer a different question, and leaving them would show the previous
                        // key's catalogue under the new one.
                        subscriptions =
                            it.subscriptions.copy(
                                capability =
                                    streamingDiscoveryRepository.capabilityFor(
                                        if (configured) CONFIGURED_KEY_SENTINEL else null,
                                    ),
                                shelves = emptyList(),
                                selected = null,
                                offers = emptyList(),
                                isLoading = false,
                                isSelectionLoading = false,
                                selectionUnknown = false,
                            ),
                    )
                }
                // The desktop calls rebuildMetadataClients() here. Without the equivalent, pasting
                // a key updated a flag and nothing else: the destination appeared but stayed empty
                // until the app was restarted, which is exactly what "nada aconteceu" looked like.
                if (configured) loadSubscriptionShelves(force = true, refresh = true)
            }.onFailure { error ->
                logger.error(TAG, "Could not update the encrypted metadata key", error)
            }
        }
    }

    /**
     * Sets or clears the household key that every profile falls back to.
     *
     * Separate from [saveTmdbKey], which writes this profile's own: the two are different scopes
     * and collapsing them into one call would make it impossible to tell which the user meant.
     */
    fun saveSharedTmdbKey(apiKey: String) {
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) { metadataKeyStore.saveShared(apiKey) }
            }.onSuccess {
                refreshMetadataKeyState()
                loadSubscriptionShelves(force = true, refresh = true)
            }.onFailure { error ->
                logger.error(TAG, "Could not update the shared metadata key", error)
            }
        }
    }

    /**
     * Re-reads which keys exist and republishes the capability.
     *
     * One place, because the destination's visibility and the two "configured" labels all follow
     * from the same question and must never disagree.
     */
    private suspend fun refreshMetadataKeyState() {
        val profileId = mutableState.value.activeProfile?.id
        val keys =
            withContext(ioDispatcher) {
                val profileKey = profileId?.let { runCatching { metadataKeyStore.read(it) }.getOrNull() }
                val sharedKey = runCatching { metadataKeyStore.readShared() }.getOrNull()
                profileKey to sharedKey
            }
        val effective = streamingDiscoveryRepository.effectiveKey(keys.first, keys.second)
        mutableState.update {
            it.copy(
                tmdbKeyConfigured = !keys.first.isNullOrBlank(),
                sharedTmdbKeyConfigured = !keys.second.isNullOrBlank(),
                subscriptions =
                    it.subscriptions.copy(
                        capability =
                            streamingDiscoveryRepository.capabilityFor(
                                if (effective != null) CONFIGURED_KEY_SENTINEL else null,
                            ),
                        shelves = emptyList(),
                        selected = null,
                        offers = emptyList(),
                    ),
            )
        }
    }

    /**
     * Loads the service shelves for the active profile.
     *
     * Reloads only when there is nothing to show, so returning to the destination does not refetch a
     * catalogue the user is already looking at. [force] is for an explicit refresh and for a change
     * of filter, where the existing shelves answer a different question.
     */
    fun loadSubscriptionShelves(
        force: Boolean = false,
        /**
         * Bypasses today's cached shelves as well as the in-memory ones.
         *
         * Only for something the user did that should change the answer — saving a key. Changing
         * the filter or the region is a different question with its own cache entry, so it must
         * not throw away a perfectly good cached answer.
         */
        refresh: Boolean = false,
    ) {
        val current = mutableState.value.subscriptions
        if (!current.capability.isVisible) return
        if (!force && (current.shelves.isNotEmpty() || current.isLoading)) return

        subscriptionsJob?.cancel()
        mutableState.update { it.copy(subscriptions = it.subscriptions.copy(isLoading = true)) }
        subscriptionsJob =
            viewModelScope.launch {
                val key = activeMetadataKey()
                val shelves =
                    streamingDiscoveryRepository.shelves(
                        apiKey = key,
                        region = current.region,
                        kind = current.kind.toDiscoverKind(),
                        refresh = refresh,
                    )
                mutableState.update {
                    it.copy(
                        subscriptions =
                            it.subscriptions.copy(
                                isLoading = false,
                                shelves = shelves.map(TmdbServiceShelf::toUi),
                            ),
                    )
                }
            }
    }

    /**
     * Changes which country's listings Assinaturas shows.
     *
     * Everything cached was answered for the previous region, so it is dropped rather than shown
     * under the new heading: "on Netflix" means something different in Brazil and in Germany.
     */
    fun selectSubscriptionRegion(region: String) {
        if (mutableState.value.subscriptions.region == region) return
        mutableState.update {
            it.copy(
                subscriptions =
                    it.subscriptions.copy(
                        region = region,
                        shelves = emptyList(),
                        selected = null,
                        offers = emptyList(),
                    ),
            )
        }
        loadSubscriptionShelves(force = true)
    }

    /** Switches the shelf filter and reloads, since the existing shelves answer a different question. */
    fun selectSubscriptionKind(kind: SubscriptionsKindUi) {
        if (mutableState.value.subscriptions.kind == kind) return
        mutableState.update {
            it.copy(subscriptions = it.subscriptions.copy(kind = kind, shelves = emptyList()))
        }
        loadSubscriptionShelves(force = true)
    }

    /**
     * Opens one title's offers.
     *
     * Availability and the title's own page are fetched separately and neither is allowed to lose
     * the other: a title TMDb has no availability for still shows its synopsis and cast, and a page
     * that fails to load still shows where the title can be watched.
     */
    fun openSubscriptionTitle(title: SubscriptionTitleUi) {
        subscriptionSelectionJob?.cancel()
        mutableState.update {
            it.copy(
                subscriptions =
                    it.subscriptions.copy(
                        selected = title,
                        offers = emptyList(),
                        isSelectionLoading = true,
                        selectionUnknown = false,
                    ),
            )
        }
        subscriptionSelectionJob =
            viewModelScope.launch {
                val key = activeMetadataKey()
                val region = mutableState.value.subscriptions.region
                val external = title.toExternalTitle()
                val details = streamingDiscoveryRepository.offersFor(key, region, external)
                val page = streamingDiscoveryRepository.pageFor(key, region, external)

                // "You already have this" is the one row only BURO can produce, and the one most
                // damaging to get wrong: a false claim sends the user to a title that is not there.
                // LibraryMatchingPolicy sets the bar — only a confident match becomes an offer, and
                // anything weaker produces nothing rather than a maybe.
                val libraryLookup =
                    withContext(ioDispatcher) {
                        runCatching {
                            val candidates =
                                catalogRepository
                                    .findLibraryCandidates(title.title)
                                    .map(Channel::toLibraryCandidate)
                            LibraryOfferPolicy.findInLibrary(external.asExternalCandidate(), candidates)
                        }.getOrNull()
                    }
                mutableState.update { state ->
                    // Ignore a result that arrived after the user moved on to another title.
                    if (state.subscriptions.selected?.externalId != title.externalId) {
                        state
                    } else {
                        state.copy(
                            subscriptions =
                                state.subscriptions.copy(
                                    isSelectionLoading = false,
                                    selected = page?.let { title.mergedWith(it) } ?: title,
                                    offers =
                                        buildList {
                                            // First in the list as well as first in rank: someone
                                            // who owns the film should not have to read past three
                                            // ways to pay for it.
                                            libraryLookup?.let { found ->
                                                add(found.offer.toUi().copy(localContentId = found.localContentId))
                                            }
                                            addAll(details?.offers.orEmpty().map(StreamingOffer::toUi))
                                        },
                                    // Null means TMDb could not answer, which is not the same as
                                    // "available nowhere" and must not be rendered as it.
                                    // Knowing the user owns it is knowing something, even when
                                    // TMDb had nothing to say about where else it plays.
                                    selectionUnknown = details == null && libraryLookup == null,
                                ),
                        )
                    }
                }
            }
    }

    /**
     * Opens a local item by its catalogue id — the "you already have this" row in Assinaturas.
     *
     * Resolved through the repository rather than from anything held on screen: the row came from a
     * match against the database, and the item may not be in any list currently loaded.
     */
    fun openChannelById(channelId: String) {
        viewModelScope.launch {
            val channel =
                withContext(ioDispatcher) {
                    runCatching { catalogRepository.getChannel(channelId) }.getOrNull()
                } ?: return@launch
            // The category is only a label on the details header; opening does not need it.
            openChannel(channel.toCatalogUi(""))
        }
    }

    /**
     * Opens a title someone shared, resolving it against **this** device's catalogue.
     *
     * The link cannot carry a catalogue row id: that id is minted when a playlist is imported, so
     * the sender's id names a row in their database and either nothing or the wrong film in the
     * recipient's. What travels instead is a [ContentIdentity] — the normalised title, kind and
     * year — and the row is found here by recomputing the same identity over the local catalogue.
     * Two people on different providers can then share a film and each open their own copy.
     *
     * Held until the catalogue is ready rather than answered immediately: a link tapped from a cold
     * start arrives long before the database has rows to search, and reporting "not in your list"
     * at that moment would be wrong for a film the user does have.
     */
    fun openSharedLink(rawLink: String) {
        val shared = TitleShareLink.parse(rawLink) ?: return
        pendingSharedTitle = shared
        resolvePendingSharedTitle()
    }

    /**
     * Resolves a link that is waiting on the catalogue, if there is one.
     *
     * Called both when the link arrives and when the catalogue finishes loading, because either can
     * happen first and only the later of the two can succeed.
     */
    private fun resolvePendingSharedTitle() {
        val shared = pendingSharedTitle ?: return
        if (mutableState.value.activeProfile == null) return

        sharedLinkJob?.cancel()
        sharedLinkJob =
            viewModelScope.launch {
                mutableState.update { it.copy(isResolvingSharedTitle = true) }
                val match =
                    withContext(ioDispatcher) {
                        runCatching {
                            // A fragment, not the whole title: the local copy is decorated
                            // differently from the sender's ("[4K] Duna DUAL" against "Duna 1080p"),
                            // so a query on the sender's exact name would miss it. The identity
                            // comparison below is what actually decides.
                            catalogRepository
                                .findLibraryCandidates(sharedTitleSearchFragment(shared.title))
                                .firstOrNull { candidate -> candidate.matches(shared.identity) }
                        }.getOrNull()
                    }

                if (match == null) {
                    // Kept pending: the catalogue may still be importing, and a later refresh can
                    // resolve the same link without the user tapping it again.
                    mutableState.update {
                        it.copy(
                            isResolvingSharedTitle = false,
                            sharedTitleMissing = true,
                        )
                    }
                    return@launch
                }

                pendingSharedTitle = null
                mutableState.update {
                    it.copy(isResolvingSharedTitle = false, sharedTitleMissing = false)
                }
                openChannel(match.toCatalogUi(""))
            }
    }

    /** Dismisses the "not in your list" notice without discarding the pending link. */
    fun dismissSharedTitleNotice() {
        mutableState.update { it.copy(sharedTitleMissing = false) }
    }

    /** Returns from a title's offers to the shelves. */
    fun closeSubscriptionTitle() {
        subscriptionSelectionJob?.cancel()
        mutableState.update {
            it.copy(
                subscriptions =
                    it.subscriptions.copy(
                        selected = null,
                        offers = emptyList(),
                        isSelectionLoading = false,
                        selectionUnknown = false,
                    ),
            )
        }
    }

    /**
     * The key to use right now: the profile's own, else the build's.
     *
     * Read at the point of use and never held in state, so the secret does not travel through the
     * UI layer or survive in a snapshot.
     */
    private suspend fun activeMetadataKey(): String? {
        val profileId = mutableState.value.activeProfile?.id
        val keys =
            withContext(ioDispatcher) {
                val profileKey = profileId?.let { runCatching { metadataKeyStore.read(it) }.getOrNull() }
                val sharedKey = runCatching { metadataKeyStore.readShared() }.getOrNull()
                profileKey to sharedKey
            }
        return streamingDiscoveryRepository.effectiveKey(keys.first, keys.second)
    }

    fun createProfile(name: String, isKids: Boolean, sourceId: String? = null) {
        viewModelScope.launch {
            runCatching {
                val created =
                    userLibraryRepository.createProfile(
                        name = name,
                        type = if (isKids) ProfileType.KIDS else ProfileType.ADULT,
                        languageTag = Locale.getDefault().toLanguageTag(),
                    )
                // Applied after creation rather than as a parameter: the playlist is a preference
                // the profile points at, and keeping it a separate write means a failure here
                // leaves a usable profile rather than none at all.
                if (sourceId != null) {
                    userLibraryRepository.setProfileSource(created.id, sourceId)
                }
                created
            }.onFailure { error ->
                logger.error(TAG, "Could not create a family profile", error)
            }
        }
    }

    /**
     * Renames a profile and changes its avatar and kind.
     *
     * The profile list is observed, so the picker repaints itself once the write lands; there is no
     * state update here to keep in step with the database.
     */
    fun updateProfile(
        profileId: String,
        name: String,
        avatarKey: String,
        isKids: Boolean,
        photoUri: String? = null,
        clearPhoto: Boolean = false,
    ) {
        viewModelScope.launch {
            runCatching {
                userLibraryRepository.updateProfile(
                    id = profileId,
                    name = name,
                    avatarKey = avatarKey,
                    type = if (isKids) ProfileType.KIDS else ProfileType.ADULT,
                    photoUri = photoUri,
                    clearPhoto = clearPhoto,
                )
            }.onFailure { error ->
                logger.error(TAG, "Could not update a family profile", error)
            }
        }
    }

    /**
     * Deletes a profile, and moves off it when it was the one in use.
     *
     * Switching first would leave a window where the active profile no longer exists; doing it
     * after the delete means the picker is only ever pointed at a profile that is really there.
     */
    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            val wasActive = mutableState.value.activeProfile?.id == profileId
            val removed =
                runCatching { userLibraryRepository.deleteProfile(profileId) }
                    .onFailure { error -> logger.error(TAG, "Could not delete a family profile", error) }
                    .getOrDefault(false)
            if (removed && wasActive) {
                // Hand the session to another profile rather than to none. Clearing it left the
                // boot screen waiting on a profile that would never arrive, so deleting the one
                // you were using hung the app on "opening your catalogue".
                val next = mutableState.value.profiles.firstOrNull { it.id != profileId }
                onboardingPreferences.selectProfile(next?.id)
            }
        }
    }

    /**
     * Points a profile at a playlist, or clears the choice.
     *
     * The catalogue is reloaded for the active profile, because the change decides which source
     * the next Films or Series press opens — leaving the old one on screen would show the previous
     * playlist under the new setting.
     */
    fun selectProfileSource(profileId: String, sourceId: String?) {
        viewModelScope.launch {
            runCatching { userLibraryRepository.setProfileSource(profileId, sourceId) }
                .onFailure { error -> logger.error(TAG, "Could not set the profile playlist", error) }
        }
    }

    /** The avatars the editor offers. */
    fun availableAvatars(): List<String> = userLibraryRepository.availableAvatars()

    fun openStory(itemId: String) {
        if (itemId.isBlank() || mutableState.value.content != AppContent.Home) return

        rememberLastFocusedHomeItem(itemId)
        (mutableState.value.homeItems + mutableState.value.continueWatching.map { it.channel })
            .firstOrNull { it.id == itemId }
            ?.let { item ->
            openChannel(item)
            return
        }
        // A title from a service shelf. Its id is not a catalogue row, so the lookup above never
        // matches and it used to fall through to the placeholder story — which is what made a
        // Netflix poster open "demonstration item unavailable" instead of where to watch it.
        subscriptionTitleForHomeItem(itemId)?.let { title ->
            selectSection(AppSection.SUBSCRIPTIONS)
            openSubscriptionTitle(title)
            return
        }
        navigate(AppContent.Story(itemId))
    }

    /**
     * The service-shelf title a home item id refers to, or null when it is not one.
     *
     * The id is built by the home catalogue as `streaming:<provider>:<externalId>`. Matching on the
     * trailing external id rather than parsing the whole thing keeps this working if the provider
     * segment ever changes shape, and an id containing colons still resolves because the search is
     * for a title whose external id the string ends with.
     */
    private fun subscriptionTitleForHomeItem(itemId: String): SubscriptionTitleUi? {
        if (!itemId.startsWith(STREAMING_ITEM_PREFIX)) return null
        return mutableState.value.subscriptions.shelves
            .asSequence()
            .flatMap { shelf -> shelf.titles.asSequence() }
            .firstOrNull { title -> itemId.endsWith(":" + title.externalId) }
    }

    /**
     * Opens a filmography entry on the "where to watch" page.
     *
     * The same destination a service shelf leads to, and for the same reason: the app has no idea
     * whether this title is in the user's playlist until it asks, and that page is what answers —
     * showing the user's own copy first when there is one, and the services otherwise.
     */
    fun openPersonCredit(credit: PersonCreditUi) {
        val externalId = credit.externalId?.takeIf(String::isNotBlank) ?: return
        selectSection(AppSection.SUBSCRIPTIONS)
        openSubscriptionTitle(
            SubscriptionTitleUi(
                // Left blank on purpose: films and series are numbered separately at TMDb, and the
                // mapping already picks the right namespace from `isSeries`. Naming one here would
                // be a second place for that choice to be made, and to be made wrongly.
                externalNamespace = "",
                externalId = externalId,
                title = credit.title,
                year = credit.year,
                posterUrl = credit.posterUrl,
                isSeries = credit.isSeries,
                isDemo = false,
            ),
        )
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
        openCategory(category, bypassLock = false)
    }

    /**
     * Opens [category], asking for the PIN first when it is locked.
     *
     * [bypassLock] is set only by [submitParentalPin], after the PIN has actually been checked —
     * it is never a way for the UI to decide a category is open.
     */
    private fun openCategory(
        category: CategoryUi,
        bypassLock: Boolean,
    ) {
        val state = mutableState.value
        if (!bypassLock &&
            state.hasParentalPin &&
            state.parentalLock.requiresPin(category.id, category.name)
        ) {
            mutableState.update {
                it.copy(
                    pendingUnlock = PendingUnlockUi(category.id, category.name),
                    parentalMessage = null,
                )
            }
            return
        }
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

    /**
     * Fetches the photo for each name in [names] that has not been tried yet.
     *
     * The provider sends the cast as a bare comma-separated string — names and nothing else — so a
     * face can only come from outside. Without this the strip could only ever draw initials, which
     * is what the user saw.
     *
     * Every name is looked up at most once per session, hit or miss, and results land in state as
     * they arrive so the strip fills in progressively rather than waiting for the slowest one.
     */
    fun ensureCastPhotos(names: List<String>) {
        if (!mutableState.value.tmdbKeyConfigured) return
        val pending =
            names.asSequence()
                .map { it.trim() }
                .filter(String::isNotEmpty)
                .map { it to it.lowercase(Locale.ROOT) }
                .filter { (_, key) -> key !in mutableState.value.castPhotos }
                .filter { (_, key) -> castPhotoLookups.add(key) }
                .take(MAX_INDEXED_CAST)
                .toList()
        if (pending.isEmpty()) return

        viewModelScope.launch {
            val apiKey = activeMetadataKey()
            if (apiKey.isNullOrBlank()) {
                // Release the claims so a later attempt, once a key exists, can retry these names.
                pending.forEach { (_, key) -> castPhotoLookups.remove(key) }
                return@launch
            }
            val client =
                TmdbClient(
                    apiKey = apiKey,
                    client = okHttpClient,
                    language = Locale.getDefault().toLanguageTag(),
                )
            pending.forEach { (name, key) ->
                val photo =
                    withContext(ioDispatcher) {
                        runCatching { client.findPerson(name)?.profileImageUrl }.getOrNull()
                    }
                // Stored even when null, which is what stops a miss being re-fetched every frame.
                mutableState.update { state -> state.copy(castPhotos = state.castPhotos + (key to photo)) }
            }
        }
    }

    /**
     * Reads how far the viewer already is into [channel], for the bar on the details page.
     *
     * Null for anything with no stored progress, which is the ordinary case and must render as no
     * bar at all rather than as a bar at zero — an empty bar reads as "started and got nowhere".
     */
    /**
     * How far into each episode of the open series the viewer already is.
     *
     * Read once when the series opens rather than per row: a season is a handful of lookups, and
     * doing it inside the list would run them again on every scroll.
     */
    /**
     * Real synopses for the titles the banner rotates through.
     *
     * Only the first few, and only films: the plot lives behind a provider call per title, and
     * fetching it for a whole home screen would be dozens of requests for text nobody reads. A
     * failure is silent — the banner keeps the generic line, which is what it had before.
     */
    private fun loadHeroSynopses(candidates: List<ChannelUi>) {
        val wanted =
            candidates.filter { channel ->
                channel.contentType in setOf(CatalogContentType.MOVIE, CatalogContentType.SERIES) &&
                    !channel.providerItemId.isNullOrBlank() &&
                    channel.id !in mutableState.value.heroSynopses
            }
        if (wanted.isEmpty()) return
        viewModelScope.launch {
            val loaded =
                withContext(ioDispatcher) {
                    wanted.mapNotNull { channel ->
                        val providerId = requireNotNull(channel.providerItemId)
                        // Films and series are fetched through different calls, and the banner
                        // rotates through both — covering only films left every series showing the
                        // stock sentence, which on a catalogue full of them is most of the time.
                        val plot =
                            runCatching {
                                if (channel.contentType == CatalogContentType.SERIES) {
                                    catalogRepository.loadSeriesDetails(
                                        sourceId = channel.sourceId,
                                        providerSeriesId = providerId,
                                    ).plot
                                } else {
                                    catalogRepository.loadMovieDetails(
                                        sourceId = channel.sourceId,
                                        providerMovieId = providerId,
                                    ).plot
                                }
                            }.getOrNull()
                                ?.trim()
                                ?.takeIf(String::isNotBlank)
                                ?: return@mapNotNull null
                        channel.id to plot
                    }.toMap()
                }
            if (loaded.isEmpty()) return@launch
            mutableState.update { it.copy(heroSynopses = it.heroSynopses + loaded) }
        }
    }

    private fun loadEpisodeProgress(
        content: AppContent.SeriesDetails,
        episodes: List<Episode>,
    ) {
        val profileId = mutableState.value.activeProfile?.id
        if (profileId == null || episodes.isEmpty()) {
            mutableState.update { it.copy(episodeProgress = emptyMap()) }
            return
        }
        viewModelScope.launch {
            val progress =
                withContext(ioDispatcher) {
                    episodes.mapNotNull { episode ->
                        // Assembled exactly as the player does when the episode is opened —
                        // `providerEpisodeId` becomes the channel's `providerItemId`, and the
                        // series id comes from the open destination. A key that differs by one
                        // field finds nothing and every episode looks unwatched.
                        val identity =
                            PlaybackProgressIdentity(
                                profileId = profileId,
                                sourceId = episode.sourceId,
                                contentId = episode.providerEpisodeId,
                                contentType = PlaybackContentType.EPISODE,
                                seriesId = content.providerSeriesId,
                                seasonNumber = episode.seasonNumber,
                                episodeNumber = episode.episodeNumber,
                            )
                        val stored =
                            runCatching { playbackProgressRepository.find(identity) }.getOrNull()
                                ?: return@mapNotNull null
                        // Already a fraction — `PlaybackProgress.progressPercent` is 0..1 despite
                        // the name, and a completed entry stores exactly 1.0. Dividing by a
                        // hundred here would put every episode at under one per cent.
                        episode.id to stored.progressPercent.toFloat().coerceIn(0f, 1f)
                    }.toMap()
                }
            mutableState.update { it.copy(episodeProgress = progress) }
        }
    }

    private fun loadOpenTitleProgress(channel: ChannelUi?) {
        val profileId = mutableState.value.activeProfile?.id
        if (channel == null || profileId == null) {
            mutableState.update { it.copy(openTitleProgress = null) }
            return
        }
        viewModelScope.launch {
            val identity = playbackProgressIdentity(profileId, channel)
            val progress =
                identity?.let {
                    withContext(ioDispatcher) {
                        runCatching { playbackProgressRepository.find(it) }.getOrNull()
                    }
                }
            mutableState.update { state ->
                state.copy(
                    // A fraction already, not a percentage: dividing by a hundred is what made the
                    // details page report "0% assistido" on a film that was half watched.
                    openTitleProgress =
                        progress
                            ?.progressPercent
                            ?.takeIf { it > 0.0 }
                            ?.let { fraction -> fraction.toFloat().coerceIn(0f, 1f) },
                )
            }
        }
    }

    /** Narrows the open catalogue. The filtered view is derived in state, so nothing reloads. */
    fun setCatalogueFilter(filter: CatalogueFilter) {
        mutableState.update { it.copy(catalogueFilter = filter) }
    }

    fun setCatalogueLayout(layout: CatalogueLayout) {
        mutableState.update { it.copy(catalogueLayout = layout) }
    }

    fun openPerson(name: String) {
        val safeName = name.trim().take(100).takeIf(String::isNotEmpty) ?: return
        val movieIds = actorMovieIds[safeName.lowercase()].orEmpty()
        val content = AppContent.Person(safeName)
        navigate(content)
        mutableState.update {
            it.copy(
                personMovies = movieIds.mapNotNull(knownMovieChannels::get),
                personDetails =
                    PersonDetailsUi(
                        name = safeName,
                        isLoading = it.tmdbKeyConfigured,
                        metadataConfigured = it.tmdbKeyConfigured,
                    ),
            )
        }
        if (!mutableState.value.tmdbKeyConfigured) return

        actionJob?.cancel()
        actionJob =
            viewModelScope.launch {
                val apiKey = activeMetadataKey()
                val enriched =
                    runCatching {
                        withContext(ioDispatcher) {
                            if (apiKey.isNullOrBlank()) return@withContext null
                            val client =
                                TmdbClient(
                                    apiKey = apiKey,
                                    client = okHttpClient,
                                    language = Locale.getDefault().toLanguageTag(),
                                )
                            val person = client.findPerson(safeName) ?: return@withContext null
                            val details = client.personDetails(person.id)
                            PersonDetailsUi(
                                name = person.name,
                                photoUrl = person.profileImageUrl,
                                biography = details?.biography,
                                birthday = details?.birthday,
                                placeOfBirth = details?.placeOfBirth,
                                credits =
                                    client.filmography(person.id, MAX_FILMOGRAPHY_ITEMS).map { credit ->
                                        PersonCreditUi(
                                            title = credit.title,
                                            year = credit.year,
                                            posterUrl = credit.posterUrl,
                                            character = credit.character,
                                            externalId = credit.id?.toString(),
                                            isSeries = credit.isSeries,
                                        )
                                    },
                                metadataConfigured = true,
                            )
                        }
                    }.getOrNull()
                if (mutableState.value.content != content) return@launch
                mutableState.update { state ->
                    state.copy(
                        personDetails =
                            enriched
                                ?: state.personDetails?.copy(isLoading = false)
                                ?: PersonDetailsUi(
                                    name = safeName,
                                    metadataConfigured = state.tmdbKeyConfigured,
                                ),
                    )
                }
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

    /**
     * Favourites whatever details page is open, film or series.
     *
     * One method for both: a series is favourited by the same catalogue row a film is, and a
     * second near-identical method would be a second place for the rule to drift.
     */
    fun toggleSelectedMovieFavorite() {
        val channelId =
            when (val content = mutableState.value.content) {
                is AppContent.MovieDetails -> content.channelId
                is AppContent.SeriesDetails -> content.channelId
                else -> return
            }
        if (channelId.isBlank()) return
        val profileId = mutableState.value.activeProfile?.id ?: return
        val currentlyFavorite = channelId in mutableState.value.favoriteIds
        viewModelScope.launch {
            userLibraryRepository.toggleFavorite(profileId, channelId, currentlyFavorite)
        }
    }

    fun toggleChannelFavorite(channel: ChannelUi) {
        val profileId = mutableState.value.activeProfile?.id ?: return
        val currentlyFavorite = channel.id in mutableState.value.favoriteIds
        viewModelScope.launch {
            userLibraryRepository.toggleFavorite(profileId, channel.id, currentlyFavorite)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Sending a title to another screen
    // ---------------------------------------------------------------------------------------

    /**
     * Built on first use rather than injected, because it needs a [Context] and most sessions never
     * cast at all. [CastController] itself is free of Android types; only the transport is not.
     */
    private val castController: CastController by lazy {
        CastController(sender = CastSender(context), io = ioDispatcher)
    }

    private var castJob: Job? = null

    /**
     * Opens the cast sheet for whichever details page is open and starts looking for screens.
     *
     * The identity is what gets sent, never a URL: the receiving screen finds the title in its own
     * list and plays from the provider directly, so this device's credentials stay on it. A title
     * the app cannot identify is refused here rather than sent as something the receiver will fail
     * to match — see [ContentIdentity].
     */
    fun openCast() {
        val state = mutableState.value
        val request =
            when (val content = state.content) {
                is AppContent.MovieDetails -> {
                    val title = state.movieDetails?.title ?: content.fallbackTitle
                    val year =
                        state.movieDetails?.releaseDate?.let(::yearFromReleaseDate)
                            ?: ContentIdentity.yearFromTitle(title)
                    CastRequestUi(
                        identity = ContentIdentity.of(ContentKind.MOVIE, title, year),
                        title = title,
                    )
                }

                is AppContent.SeriesDetails -> {
                    val title = state.seriesDetails?.title ?: content.fallbackTitle
                    val year = state.seriesDetails?.releaseDate?.let(::yearFromReleaseDate)
                    CastRequestUi(
                        identity = ContentIdentity.of(ContentKind.SERIES, title, year),
                        title = title,
                    )
                }

                else -> return
            }

        mutableState.update { current -> current.copy(castRequest = request) }
        loadCastResumePosition()
        searchForScreens()
    }

    /**
     * Fills in where the viewer already is, so the other screen picks the film up rather than
     * restarting it.
     *
     * Read here rather than carried in [AppUiState], which holds only a fraction for the progress
     * bar — a percentage cannot be turned back into a position without the duration. Runs while
     * discovery is happening, so it costs no extra waiting; the sheet cannot send before a screen
     * has been chosen and a code typed, which is far longer than this takes.
     *
     * Failure leaves it at zero: starting from the beginning is a fair answer, and refusing to cast
     * because a resume point could not be read would be worse.
     */
    private fun loadCastResumePosition() {
        val profileId = mutableState.value.activeProfile?.id ?: return
        // Only films: a series has no single position, and the episode a viewer would resume is a
        // question this sheet does not ask. Series are sent at zero, which is honest.
        val content = mutableState.value.content as? AppContent.MovieDetails ?: return
        val channel = knownMovieChannels[content.channelId] ?: return
        viewModelScope.launch {
            val identity = playbackProgressIdentity(profileId, channel) ?: return@launch
            val position =
                withContext(ioDispatcher) {
                    runCatching { playbackProgressRepository.find(identity)?.positionMs }.getOrNull()
                } ?: return@launch
            mutableState.update { current ->
                // Only if the sheet is still open on the same title: closing it and opening another
                // while this was in flight would otherwise stamp one film's position onto another.
                val pending = current.castRequest ?: return@update current
                if (pending.positionMillis != 0L) return@update current
                current.copy(castRequest = pending.copy(positionMillis = position.coerceAtLeast(0L)))
            }
        }
    }

    /** Looks for screens again, for the "search again" action and when the sheet opens. */
    fun searchForScreens() {
        castJob?.cancel()
        castJob =
            viewModelScope.launch {
                castController.search()
                publishCastState()
            }
        publishCastState()
    }

    fun chooseCastTarget(target: CastTarget) {
        castController.choose(target)
        publishCastState()
    }

    fun backToCastTargets() {
        castController.back()
        publishCastState()
    }

    fun closeCast() {
        castJob?.cancel()
        castJob = null
        castController.close()
        mutableState.update { current -> current.copy(cast = CastUiState.Idle, castRequest = null) }
    }

    /** Sends the open title with the code shown on the chosen screen. */
    fun sendToCastTarget(code: String) {
        val request = mutableState.value.castRequest ?: return
        castJob?.cancel()
        castJob =
            viewModelScope.launch {
                castController.send(
                    code = code,
                    identity = request.identity,
                    title = request.title,
                    positionMillis = request.positionMillis,
                )
                publishCastState()
            }
        publishCastState()
    }

    /**
     * Copies the controller's state into the flow.
     *
     * The controller keeps a plain `var` so it stays testable without Compose; this is the single
     * place that turns it into something the screen observes. Called both before and after the
     * suspending calls above, so "searching" and "sending" are visible while they happen rather
     * than only once they finish.
     */
    private fun publishCastState() {
        mutableState.update { current -> current.copy(cast = castController.state) }
    }

    // ---------------------------------------------------------------------------------------
    // Downloads
    // ---------------------------------------------------------------------------------------

    /**
     * Starts an offline copy of the movie whose details are open.
     *
     * The signed URL is resolved here and handed straight to the downloader; it is never placed in
     * [AppUiState], so it cannot leak into a state dump or a crash report.
     */
    fun downloadSelectedMovie() {
        val content = mutableState.value.content as? AppContent.MovieDetails ?: return
        val title = mutableState.value.movieDetails?.title ?: content.fallbackTitle
        startDownload(
            contentKey = movieDownloadKey(title),
            title = title,
            artworkUrl = mutableState.value.movieDetails?.artworkUrl ?: content.fallbackArtworkUrl,
        ) {
            catalogRepository.getChannel(content.channelId)
        }
    }

    /** Starts an offline copy of one episode of the series whose details are open. */
    /**
     * Queues every episode of one season.
     *
     * Built on [downloadEpisode] rather than beside it, so a season download and a single tap are
     * the same operation repeated: the same content key, the same late URL resolution, the same
     * refusal to start something already running. Nothing here is special-cased, which is what
     * keeps a half-finished season indistinguishable from episodes picked by hand.
     *
     * Whether to ask first is the screen's decision — the confirmation names the count, and by the
     * time this is called the user has said yes.
     */
    fun downloadSeason(seasonNumber: Int) {
        episodesWorthDownloading { episode -> episode.seasonNumber == seasonNumber }
            .forEach(::downloadEpisode)
    }

    /**
     * Queues every episode the open series has.
     *
     * Ordered by season and then episode so a viewer who starts watching before the last file
     * lands gets the beginning first — and, more importantly, so the transfer *queue* is in that
     * order, because [DOWNLOAD_SLOTS] means most of these are waiting rather than running.
     */
    fun downloadWholeSeries() {
        episodesWorthDownloading().forEach(::downloadEpisode)
    }

    /**
     * The episodes a bulk download would actually fetch, in playing order.
     *
     * Episodes already on disk are left out. [startDownload] refuses one that is *running*, but
     * says nothing about one already stored — so without this, "Baixar temporada" on a season the
     * user already has would re-fetch every file, spending their data and the provider's bandwidth
     * to produce bytes that are already there.
     *
     * Deliberately not applied to a single "Baixar": tapping one episode's own button on a stored
     * file is a person asking for it again, most often because the copy is broken. A bulk button is
     * not that — nobody taps "download the whole season" meaning "fetch the forty I already have".
     *
     * Used for the count in the confirmation too, so the number the dialog promises is the number
     * of transfers that start.
     */
    private fun episodesWorthDownloading(
        where: (EpisodeUi) -> Boolean = { true },
    ): List<EpisodeUi> = episodesWorthDownloading(mutableState.value, where)

    fun downloadEpisode(episode: EpisodeUi) {
        val content = mutableState.value.content as? AppContent.SeriesDetails ?: return
        val resolved = seriesEpisodes[episode.id] ?: return
        val seriesTitle = mutableState.value.seriesDetails?.title ?: content.fallbackTitle
        val title = "$seriesTitle ${episode.seasonLabel()}"
        startDownload(
            contentKey = episodeDownloadKey(seriesTitle, episode),
            title = title,
            artworkUrl = episode.artworkUrl ?: mutableState.value.seriesDetails?.artworkUrl,
        ) {
            catalogRepository.resolveEpisode(resolved)
        }
    }

    fun cancelDownload(contentKey: String) {
        downloadManager.cancel(contentKey)
    }

    /**
     * Marks keys already present on disk as completed.
     *
     * Called when a details screen opens, so a copy stored in a previous session shows as stored
     * rather than as never-downloaded. Keys with work in flight are left alone.
     *
     * External storage can be absent or unmounted, in which case there are simply no stored copies
     * to report — that must never stop a details screen from opening, so the failure is swallowed.
     */
    private fun hydrateDownloadStates(contentKeys: List<String>) {
        if (contentKeys.isEmpty()) return
        viewModelScope.launch {
            val stored =
                withContext(ioDispatcher) {
                    runCatching { contentKeys.filter(downloadManager::isDownloaded) }
                        .getOrDefault(emptyList())
                }
            if (stored.isEmpty()) return@launch
            mutableState.update { state ->
                state.copy(
                    downloads =
                        state.downloads +
                            stored
                                .filterNot { key -> state.downloads[key] is DownloadStateUi.Running }
                                .associateWith { DownloadStateUi.Completed },
                )
            }
        }
    }

    fun deleteDownload(contentKey: String) {
        viewModelScope.launch {
            withContext(ioDispatcher) { downloadManager.delete(contentKey) }
            mutableState.update {
                it.copy(
                    downloads = it.downloads - contentKey,
                    downloadTitles = it.downloadTitles - contentKey,
                )
            }
        }
    }

    /** Opens a completed copy without resolving the provider URL again. */
    fun playDownload(contentKey: String) {
        if (!AndroidPlatformCapabilities.offlineSupported(context)) return
        viewModelScope.launch {
            val stored =
                withContext(ioDispatcher) {
                    downloadManager.storedDownloads().firstOrNull { it.contentKey == contentKey }
                }
            if (stored == null) {
                markDownload(contentKey, DownloadStateUi.Failed)
                return@launch
            }
            navigate(
                ChannelUi(
                    id = "offline:$contentKey",
                    sourceId = stored.sourceId.ifBlank { OFFLINE_SOURCE_ID },
                    name = stored.title,
                    categoryName = context.getString(R.string.downloads_title),
                    streamUrl = Uri.fromFile(stored.file).toString(),
                    logoUrl = null,
                    contentType = stored.contentType,
                    providerItemId = stored.providerItemId ?: contentKey,
                ),
            )
        }
    }

    private fun restoreDownloads() {
        viewModelScope.launch {
            val stored =
                withContext(ioDispatcher) {
                    runCatching(downloadManager::storedDownloads).getOrDefault(emptyList())
                }
            if (stored.isEmpty()) return@launch
            mutableState.update { state ->
                state.copy(
                    downloads = state.downloads + stored.associate { it.contentKey to DownloadStateUi.Completed },
                    downloadTitles = state.downloadTitles + stored.associate { it.contentKey to it.title },
                )
            }
        }
    }

    /**
     * Shared download pipeline for both movies and episodes.
     *
     * [resolve] is what produces the short-lived signed URL; it runs inside the coroutine so no
     * caller ever holds the URL, and it is only invoked once the target is known to be downloadable.
     */
    private fun startDownload(
        contentKey: String,
        title: String,
        /** Captured now: the catalogue row this came from may be gone by the time it finishes. */
        artworkUrl: String? = null,
        resolve: suspend () -> Channel?,
    ) {
        if (!AndroidPlatformCapabilities.offlineSupported(context)) return
        val inFlight = mutableState.value.downloads[contentKey]
        if (inFlight is DownloadStateUi.Running || inFlight == DownloadStateUi.Preparing) return
        mutableState.update {
            it.copy(
                // Preparing, not Running(0f): the button must say something changed the instant it
                // is pressed, and no byte has been fetched yet.
                downloads = it.downloads + (contentKey to DownloadStateUi.Preparing),
                downloadTitles = it.downloadTitles + (contentKey to title),
                downloadArtwork =
                    artworkUrl?.let { url -> it.downloadArtwork + (contentKey to url) }
                        ?: it.downloadArtwork,
            )
        }
        viewModelScope.launch {
            // Only a few transfers run at once; the rest wait here.
            //
            // Downloading a whole season or series turns one tap into dozens of these coroutines,
            // and without a limit every one of them would open its own connection to the provider
            // at the same moment. That saturates the user's line, makes each file slower than if
            // they had been fetched in turn, and looks to a provider exactly like abuse — the sort
            // of thing that gets an account throttled or closed.
            //
            // The permit covers URL resolution as well as the transfer, because resolving is
            // itself a provider request for authenticated sources.
            downloadSlots.withPermit {
                runDownload(contentKey, title, resolve)
            }
        }
    }

    /** The body of one download, once [startDownload] has been given a transfer slot. */
    private suspend fun runDownload(
        contentKey: String,
        title: String,
        resolve: suspend () -> Channel?,
    ) {
        run {
            val resolved = runCatching { resolve() }.getOrNull()
            // A live stream never ends, so downloading one would grow until storage fills. This is
            // a technical limit rather than a policy one — see ADR-008.
            if (
                resolved == null ||
                resolved.streamUri.isBlank() ||
                !downloadManager.isDownloadable(resolved.contentType)
            ) {
                logger.warn(TAG, "The selected item could not be prepared for offline storage")
                markDownload(contentKey, DownloadStateUi.Failed)
                return
            }

            val result =
                downloadManager.download(
                    contentKey = contentKey,
                    url = resolved.streamUri,
                    containerExtension = resolved.containerExtension,
                ) { read, total ->
                    val fraction = if (total != null && total > 0L) read.toFloat() / total else -1f
                    // Measured between consecutive reports rather than averaged from the start, so
                    // the figure reflects the connection now — an average would keep showing a fast
                    // opening burst long after the line had slowed.
                    val now = System.currentTimeMillis()
                    val previous = downloadRateSamples[contentKey]
                    val elapsedMillis = previous?.let { now - it.atEpochMillis } ?: 0L
                    val rate =
                        if (previous != null && elapsedMillis >= RATE_SAMPLE_MIN_MILLIS) {
                            val delta = read - previous.bytes
                            downloadRateSamples[contentKey] = DownloadRateSample(read, now)
                            if (delta > 0L) delta * 1000L / elapsedMillis else null
                        } else {
                            if (previous == null) {
                                downloadRateSamples[contentKey] = DownloadRateSample(read, now)
                            }
                            // Keeps the last figure while the next sample accumulates, so the
                            // number does not blink in and out between reports.
                            (mutableState.value.downloads[contentKey] as? DownloadStateUi.Running)
                                ?.bytesPerSecond
                        }
                    markDownload(contentKey, DownloadStateUi.Running(fraction, rate))
                }
            if (result is DownloadResult.Completed) {
                withContext(ioDispatcher) {
                    downloadManager.rememberCompleted(
                        contentKey = contentKey,
                        title = title,
                        sourceId = resolved.sourceId,
                        providerItemId = resolved.providerItemId,
                        contentType = resolved.contentType,
                    )
                }
            }
            markDownload(
                contentKey,
                when (result) {
                    is DownloadResult.Completed -> DownloadStateUi.Completed
                    DownloadResult.Cancelled -> DownloadStateUi.Idle
                    is DownloadResult.Failed -> DownloadStateUi.Failed
                },
            )
        }
    }

    private fun markDownload(contentKey: String, state: DownloadStateUi) {
        // The rate sample only means something while bytes are moving. Left behind, it would make
        // a resumed download compute its first rate against an interval of hours.
        if (state !is DownloadStateUi.Running) downloadRateSamples.remove(contentKey)
        mutableState.update { it.copy(downloads = it.downloads + (contentKey to state)) }
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
                                liveSchedule = emptyList(),
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
                    liveNow = epg?.now?.toUi(),
                    liveNext = epg?.next?.toUi(),
                    liveSchedule = epg?.schedule.orEmpty().map { it.toUi() },
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

    /** Explicit user refresh: starts the current catalogue page again instead of appending. */
    fun refreshCatalog() {
        when (val content = mutableState.value.content) {
            is AppContent.Categories -> observeCategories(content)
            is AppContent.Channels -> loadInitialChannels(content)
            is AppContent.MovieDetails -> loadMovieDetails(content)
            is AppContent.SeriesDetails -> loadSeriesDetails(content)
            AppContent.Favorites -> loadFavorites()
            // The home fell into the `else` branch and did nothing at all, so the refresh button
            // in the top bar was inert. It rebuilds the rails, the continue-watching list and the
            // service shelves — everything the home is made of.
            AppContent.Home -> {
                val active = mutableState.value.activeProfile
                val sources = mutableState.value.sources
                loadHomeItems(
                    sources.firstOrNull { source -> source.id == active?.sourceId }?.id
                        ?: sources.firstOrNull { source -> source.type == SourceType.XTREAM }?.id
                        ?: sources.firstOrNull()?.id,
                )
                loadContinueWatching(active?.id)
                loadSubscriptionShelves(force = true)
            }
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
        if (current is AppContent.Player) {
            loadContinueWatching(mutableState.value.activeProfile?.id)
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

    /**
     * Connects a Stalker/Ministra portal.
     *
     * [macAddress] is the whole credential on such a portal, so it is never logged and never
     * echoed into an error: only the failure *reason* travels back to the screen.
     */
    fun importStalkerSource(
        displayName: String,
        portalUrl: String,
        macAddress: String,
        username: String,
        password: String,
    ) {
        if (mutableState.value.isImporting) return
        if (displayName.isBlank() || portalUrl.isBlank()) {
            finishFailedImport(SourceImportMethod.STALKER, StalkerFailureUi.INVALID_INPUT)
            return
        }
        val normalisedMac = StalkerMacAddress.normalise(macAddress)
        if (normalisedMac == null) {
            finishFailedImport(SourceImportMethod.STALKER, StalkerFailureUi.INVALID_INPUT)
            return
        }

        mutableState.update {
            it.copy(
                isImporting = true,
                lastImportedChannelCount = null,
                hasImportError = false,
                lastImportMethod = SourceImportMethod.STALKER,
                stalkerFailure = null,
                xtreamImportStage = XtreamImportStageUi.AUTHENTICATING,
            )
        }

        importJob =
            viewModelScope.launch {
                val runningJob = currentCoroutineContext()[Job]
                try {
                    val result =
                        catalogRepository.importStalker(
                            request =
                                StalkerImportRequest(
                                    displayName = displayName.trim(),
                                    portalUrl = portalUrl.trim(),
                                    macAddress = normalisedMac,
                                    username = username.trim().ifBlank { null },
                                    password = password.ifBlank { null },
                                ),
                            onProgress = { stage ->
                                mutableState.update { state ->
                                    if (
                                        state.isImporting &&
                                        state.lastImportMethod == SourceImportMethod.STALKER
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
                        method = SourceImportMethod.STALKER,
                    )
                } catch (cancelled: CancellationException) {
                    mutableState.update { state ->
                        if (
                            state.isImporting &&
                            state.lastImportMethod == SourceImportMethod.STALKER
                        ) {
                            state.copy(
                                isImporting = false,
                                lastImportedChannelCount = null,
                                hasImportError = false,
                                stalkerFailure = null,
                                xtreamImportStage = null,
                            )
                        } else {
                            state
                        }
                    }
                    throw cancelled
                } catch (error: Exception) {
                    // Logged by reason only: the message of a wrapped failure could carry the
                    // portal URL, and the MAC must never reach a log at all.
                    val failure = error.toStalkerFailureUi()
                    logger.error(TAG, "Stalker portal import failed: $failure")
                    finishFailedImport(SourceImportMethod.STALKER, failure)
                } finally {
                    if (importJob === runningJob) importJob = null
                }
            }
    }

    fun cancelStalkerImport() {
        val current = mutableState.value
        if (
            !current.isImporting ||
            current.lastImportMethod != SourceImportMethod.STALKER
        ) {
            return
        }
        val runningJob = importJob
        importJob = null
        runningJob?.cancel(CancellationException("Stalker import cancelled by user."))
        mutableState.update {
            it.copy(
                isImporting = false,
                lastImportedChannelCount = null,
                hasImportError = false,
                stalkerFailure = null,
                xtreamImportStage = null,
            )
        }
    }

    private fun openPrimaryCatalog(section: AppSection) {
        // The active profile's own playlist comes first, where it chose one and that playlist is
        // still present. A profile pointed at a source the user has since deleted falls back
        // rather than opening onto nothing: the id is a preference, not a promise.
        val preferredSourceId = mutableState.value.activeProfile?.sourceId
        val sources =
            mutableState.value.sources.let { all ->
                val preferred = all.filter { it.id == preferredSourceId }
                if (preferred.isEmpty()) all else preferred + all.filterNot { it.id == preferredSourceId }
            }
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
        // Remembered for the same reason a film is: the favourite toggle needs the catalogue row,
        // and the details page carries only the provider's own id.
        knownMovieChannels[channel.id] = channel
        val content =
            AppContent.SeriesDetails(
                sourceId = channel.sourceId,
                providerSeriesId = providerSeriesId,
                fallbackTitle = channel.name,
                channelId = channel.id,
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
                    val providerDetails = catalogRepository.loadMovieDetails(
                        sourceId = content.sourceId,
                        providerMovieId = content.providerMovieId,
                    )
                    val metadataKey = activeMetadataKey()
                    if (
                        providerDetails.youtubeTrailerId.isNullOrBlank() &&
                        !metadataKey.isNullOrBlank()
                    ) {
                        val trailer =
                            withContext(ioDispatcher) {
                                val client =
                                    TmdbClient(
                                        apiKey = metadataKey,
                                        client = okHttpClient,
                                        language = Locale.getDefault().toLanguageTag(),
                                    )
                                val year = providerDetails.releaseDate?.take(4)?.toIntOrNull()
                                // Provider titles carry release tags — "[L]", "4K", "DUAL" — that
                                // TMDb has never heard of, so an exact search on the raw name found
                                // nothing and the Trailer button silently disappeared. Try the
                                // cleaned name first, then the original in case the cleaning was
                                // the thing that broke the match.
                                val cleaned = providerDetails.title.compatibilityTitlePrefix()
                                client.findTrailer(title = cleaned, year = year)
                                    ?: client.findTrailer(title = providerDetails.title, year = year)
                                    // Without the year as a last resort: a provider's year is often
                                    // the upload year rather than the release year, and a wrong one
                                    // excludes the correct film from the results entirely.
                                    ?: year?.let { client.findTrailer(title = cleaned, year = null) }
                            }
                        providerDetails.copy(youtubeTrailerId = trailer)
                    } else {
                        providerDetails
                    }
                }.onSuccess { details ->
                    if (mutableState.value.content != content) return@onSuccess
                    mutableState.update {
                        it.copy(
                            movieDetails = details.toUi(),
                            isMovieLoading = false,
                            hasMovieError = false,
                        )
                    }
                    hydrateDownloadStates(listOf(movieDownloadKey(details.title)))
                    loadOpenTitleProgress(knownMovieChannels[content.channelId])
                    details.cast
                        ?.split(CAST_SEPARATOR)
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
                    val seriesUi = details.toUi()
                    mutableState.update {
                        it.copy(
                            seriesDetails = seriesUi,
                            isSeriesLoading = false,
                            hasSeriesError = false,
                        )
                    }
                    hydrateDownloadStates(
                        seriesUi.episodes.map { episode ->
                            episodeDownloadKey(seriesUi.title, episode)
                        },
                    )
                    loadEpisodeProgress(content, details.episodes)
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
                    catalogRepository.observeCategoryArtwork(
                        sourceId = content.sourceId,
                        contentType = content.contentType,
                    ),
                ) { categories, counts, artwork ->
                    val kidsMode = mutableState.value.activeProfile?.isKids == true
                    val visibleCategories =
                        if (kidsMode) categories.filterNot { FamilyContentPolicy.isExplicitAdultLabel(it.name) } else categories
                    val usedArtwork = LinkedHashSet<String>()
                    fun uniqueArtwork(categoryId: String): String? {
                        val candidate = artwork[categoryId]
                        return candidate?.takeIf(usedArtwork::add)
                    }
                    // Reserve each real category's own representative first. "All" is allowed to
                    // reuse one; letting it consume the first image made the first named category
                    // fall back to the old generic atlas again.
                    val mappedCategories =
                        visibleCategories.map { category ->
                            category.toUi(
                                channelCount = counts[category.id] ?: 0,
                                artworkUrl = uniqueArtwork(category.id),
                            )
                        }
                    val allArtwork =
                        artwork.values.firstOrNull { candidate -> candidate !in usedArtwork }
                            ?: artwork.values.firstOrNull()
                    buildList {
                        if (!kidsMode) {
                            add(
                                CategoryUi(
                                    id = null,
                                    name = "",
                                    channelCount = counts.values.sum(),
                                    artworkUrl = allArtwork,
                                ),
                            )
                        }
                        addAll(mappedCategories)
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
                    mutableState.update { state ->
                        state.copy(
                            // Merged rather than replaced: this is one section's categories, and
                            // settings lists all three. Replacing here would empty the settings
                            // list every time the catalogue moved to a different section.
                            allCategories =
                                (state.allCategories + categories.filter { it.id != null })
                                    .distinctBy(CategoryUi::id),
                            categories =
                                categories.filterNot { category ->
                                    category.id != null && category.id in state.hiddenCategoryIds
                                },
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
                // Cleared with the list it described. A genre or year chosen in one category was
                // still in force in the next, and a genre that category does not have narrowed it
                // to nothing — so "Series | Netflix · 1716 itens" opened onto "this source has no
                // compatible channels" while the count on the card stayed right.
                catalogueFilter = CatalogueFilter(),
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

    /** Marks the boot screen's last step; the shell replaces it as soon as the home has content. */
    private fun markBootReady() {
        if (mutableState.value.bootStage != BootStageUi.READY) {
            mutableState.update { it.copy(bootStage = BootStageUi.READY) }
        }
        // A link tapped from a cold start has been waiting for rows to search. This is the first
        // moment there are any, so it is the first moment the lookup can honestly say "not found".
        resolvePendingSharedTitle()
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
                    if (accepted) checkLicense()
                }
        }
    }

    private fun observeProfiles() {
        viewModelScope.launch {
            userLibraryRepository.ensureDefaultProfile(
                languageTag = Locale.getDefault().toLanguageTag(),
                // The repository's own fallback covers a context that cannot resolve resources,
                // which is what the unit tests run against.
                defaultName =
                    runCatching { context.getString(R.string.profile_default_name) }
                        .getOrDefault("Buro"),
            )
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
                    // The profile's own key, or the one baked into the build. Reading it through
                    // the repository is what lets a build that shipped with a key work out of the
                    // box: asking the key store alone reported "not configured" and switched
                    // trailers, cast photos and Assinaturas off despite a usable key being present.
                    val profileKey =
                        active?.id?.let { profileId ->
                            withContext(ioDispatcher) {
                                runCatching { metadataKeyStore.read(profileId) }.getOrNull()
                            }
                        }
                    val sharedKey =
                        withContext(ioDispatcher) {
                            runCatching { metadataKeyStore.readShared() }.getOrNull()
                        }
                    val metadataConfigured =
                        streamingDiscoveryRepository.effectiveKey(profileKey, sharedKey) != null
                    mutableState.update {
                        it.copy(
                            isProfilesLoading = false,
                            profiles = profiles,
                            activeProfile = active,
                            // A child's loading screen must never inherit covers selected for the
                            // previous adult profile. A regular profile fills this again from its
                            // own source as soon as the first movie and series pages are available.
                            bootBackdropUrls =
                                if (active?.id == it.activeProfile?.id && active?.isKids != true) {
                                    it.bootBackdropUrls
                                } else {
                                    emptyList()
                                },
                            tmdbKeyConfigured = metadataConfigured,
                            sharedTmdbKeyConfigured = !sharedKey.isNullOrBlank(),
                            favoriteIds = if (active?.id == it.activeProfile?.id) it.favoriteIds else emptySet(),
                            favoriteItems = if (active?.id == it.activeProfile?.id) it.favoriteItems else emptyList(),
                            // The key is per profile, so switching profiles can turn the
                            // Assinaturas destination on or off. Shelves loaded for the previous
                            // profile are cleared with it rather than shown under the new one.
                            subscriptions =
                                SubscriptionsUi(
                                    capability =
                                        streamingDiscoveryRepository.capabilityFor(
                                            if (metadataConfigured) CONFIGURED_KEY_SENTINEL else null,
                                        ),
                                    region = it.subscriptions.region,
                                ),
                        )
                    }
                    // Only while there is a profile whose catalogue can actually load. Setting it
                    // unconditionally sent the stage back from READY when the active profile was
                    // deleted, and the boot screen then waited forever on a catalogue that had
                    // nobody to load for — deleting the profile you were using hung the app.
                    mutableState.update {
                        it.copy(
                            bootStage =
                                if (active == null) BootStageUi.READY else BootStageUi.CATALOGUE,
                        )
                    }
                    observeFavorites(active?.id)
                    loadContinueWatching(active?.id)
                    // Rebuild the home for whoever is now watching. The stage was set to CATALOGUE
                    // just above, and `loadHomeItems` is what carries it to READY — but it is
                    // otherwise only reached from the source observation, which does not re-emit
                    // when the profile changes because the sources themselves did not change. So
                    // creating a profile, or switching to one, left the boot screen on "opening
                    // your catalogue" with nothing on its way to release it.
                    //
                    // It also has to run for its own sake: a profile can point at a different
                    // playlist, so the rails belong to the profile and not to the app.
                    if (active != null) {
                        val sources = mutableState.value.sources
                        val sourceId =
                            sources.firstOrNull { source -> source.id == active.sourceId }?.id
                                ?: sources.firstOrNull { source -> source.type == SourceType.XTREAM }?.id
                                ?: sources.firstOrNull()?.id
                        loadBootBackdrop(sourceId = sourceId, profile = active)
                        loadHomeItems(sourceId)
                    }
                    // The home screen draws the service shelves too, and it is reached on start-up
                    // without ever going through selectSection(HOME) — so loading them only there
                    // meant they appeared after visiting Assinaturas and never before.
                    loadSubscriptionShelves()
                }
        }
    }

    /**
     * The catalogue row a progress entry refers to, or null when it can no longer be found.
     *
     * An episode resolves to **its series**, not to itself. Individual episodes are never written
     * to the channel table — only series are — so looking one up by its own provider id always
     * failed and every part-watched series was silently dropped from Continue watching and from
     * History. The series is also the right thing to show: it is what the viewer recognises, and
     * opening it leads to the episode list.
     */
    private suspend fun storedContentFor(progress: PlaybackProgress): Channel? {
        val identity = progress.identity
        return when (identity.contentType) {
            PlaybackContentType.MOVIE ->
                catalogRepository.findStoredContent(
                    sourceId = identity.sourceId,
                    providerItemId = identity.contentId,
                    contentType = CatalogContentType.MOVIE,
                )

            PlaybackContentType.EPISODE ->
                identity.seriesId?.let { seriesId ->
                    catalogRepository.findStoredContent(
                        sourceId = identity.sourceId,
                        providerItemId = seriesId,
                        contentType = CatalogContentType.SERIES,
                    )
                }
        }
    }

    private fun loadContinueWatching(profileId: String?) {
        if (profileId == null) {
            mutableState.update { it.copy(continueWatching = emptyList(), watchHistory = emptyList()) }
            return
        }
        viewModelScope.launch {
            val (entries, historyEntries) =
                withContext(ioDispatcher) {
                    playbackProgressRepository.continueWatching(profileId, HOME_CONTINUE_LIMIT) to
                        playbackProgressRepository.history(profileId, HISTORY_LIMIT)
                }
            val items =
                entries.mapNotNull { progress ->
                    val channel = storedContentFor(progress) ?: return@mapNotNull null
                    ContinueWatchingUi(
                        channel = channel.toCatalogUi("Continue assistindo"),
                        progress = progress.progressPercent.toFloat().coerceIn(0f, 1f),
                    )
                }.filter { item ->
                    listOf(item.channel)
                        .filterKidsContentIfNeeded(mutableState.value.activeProfile)
                        .isNotEmpty()
                }
            val history =
                historyEntries.mapNotNull { progress ->
                    val channel = storedContentFor(progress) ?: return@mapNotNull null
                    WatchHistoryUi(
                        channel = channel.toCatalogUi("Histórico"),
                        progress = progress.progressPercent.toFloat().coerceIn(0f, 1f),
                        completed = progress.completedAtEpochMillis != null,
                        lastWatchedAtEpochMillis = progress.lastWatchedAtEpochMillis,
                    )
                }.filter { item ->
                    listOf(item.channel)
                        .filterKidsContentIfNeeded(mutableState.value.activeProfile)
                        .isNotEmpty()
                }
            if (mutableState.value.activeProfile?.id == profileId) {
                mutableState.update { it.copy(continueWatching = items, watchHistory = history) }
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
                    val active = mutableState.value.activeProfile
                    val sourceId =
                        sources.firstOrNull { source -> source.id == active?.sourceId }?.id
                            ?: sources.firstOrNull { it.type == SourceType.XTREAM }?.id
                            ?: sources.firstOrNull()?.id
                    loadBootBackdrop(sourceId = sourceId, profile = active)
                    loadHomeItems(sourceId)
                }
        }
    }

    /**
     * Loads just enough real covers for the boot animation, independently of Home construction.
     *
     * The profile and source observers can each restart Home while they settle during a cold
     * launch. A separate job prevents that churn from cancelling the cover query, which is why the
     * previous implementation kept falling back to the four bundled abstract images.
     */
    private fun loadBootBackdrop(sourceId: String?, profile: ProfileUi?) {
        bootBackdropJob?.cancel()
        if (sourceId == null || profile == null || profile.isKids) {
            mutableState.update { it.copy(bootBackdropUrls = emptyList()) }
            return
        }
        bootBackdropJob =
            viewModelScope.launch {
                try {
                    val candidates =
                        coroutineScope {
                            val movies =
                                async {
                                    catalogRepository.loadChannelsPage(
                                        sourceId = sourceId,
                                        contentType = CatalogContentType.MOVIE,
                                        limit = BOOT_BACKDROP_QUERY_LIMIT,
                                    ).items
                                }
                            val series =
                                async {
                                    catalogRepository.loadChannelsPage(
                                        sourceId = sourceId,
                                        contentType = CatalogContentType.SERIES,
                                        limit = BOOT_BACKDROP_QUERY_LIMIT,
                                    ).items
                                }
                            movies.await() + series.await()
                        }
                    if (mutableState.value.activeProfile?.id == profile.id) {
                        mutableState.update { state ->
                            state.copy(
                                bootBackdropUrls =
                                    selectBootBackdropUrls(candidates.map(Channel::logoUri)),
                            )
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    // Decorative artwork is optional. Keep the bundled wall and continue startup.
                    logger.error(TAG, "Could not prepare loading-screen artwork", error)
                }
            }
    }

    private fun loadHomeItems(sourceId: String?) {
        homeJob?.cancel()
        if (sourceId == null) {
            // Nothing to load, so nothing to wait for: release the boot screen or a user with no
            // source configured would sit on a spinner instead of reaching the import screen.
            mutableState.update { it.copy(homeItems = emptyList(), bootBackdropUrls = emptyList()) }
            markBootReady()
            return
        }
        homeJob = viewModelScope.launch {
            runCatching {
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                // Run together rather than one after another. These five queries do not depend on
                // each other, and awaiting each in turn over a catalogue of forty thousand items
                // added up to roughly ten seconds on the boot screen — long enough that switching
                // or deleting a profile looked like the app had frozen. In parallel the wait is
                // the slowest query rather than the sum of all five.
                coroutineScope {
                    val releasesCurrentTask =
                        async { catalogRepository.loadForReleaseYear(sourceId, currentYear, HOME_RELEASE_LIMIT) }
                    val releasesPreviousTask =
                        async { catalogRepository.loadForReleaseYear(sourceId, currentYear - 1, HOME_RELEASE_LIMIT) }
                    val recentTask = async { catalogRepository.loadRecentlyAdded(sourceId, HOME_RECENT_LIMIT) }
                    val moviesTask =
                        async {
                            catalogRepository.loadChannelsPage(
                                sourceId = sourceId,
                                contentType = CatalogContentType.MOVIE,
                                limit = HOME_ITEM_LIMIT,
                            ).items
                        }
                    val seriesTask =
                        async {
                            catalogRepository.loadChannelsPage(
                                sourceId = sourceId,
                                contentType = CatalogContentType.SERIES,
                                limit = HOME_ITEM_LIMIT,
                            ).items
                        }
                    // These two indexed page reads normally finish before the release and recent
                    // queries. Publish their real covers immediately instead of making the boot
                    // screen wait for the complete home composition. The URLs already live in the
                    // app's private Room database; this adds no second playlist or credential
                    // store. Kids profiles deliberately keep the neutral bundled artwork.
                    val movies = moviesTask.await()
                    val series = seriesTask.await()
                    val activeProfile = mutableState.value.activeProfile
                    mutableState.update { state ->
                        state.copy(
                            bootBackdropUrls =
                                if (activeProfile != null && !activeProfile.isKids) {
                                    selectBootBackdropUrls((movies + series).map(Channel::logoUri))
                                } else {
                                    emptyList()
                                },
                        )
                    }
                    HomeSources(
                        releasesCurrent = releasesCurrentTask.await(),
                        releasesPrevious = releasesPreviousTask.await(),
                        recent = recentTask.await(),
                        movies = movies,
                        series = series,
                    )
                }.let { sources ->
                // Composed off the main thread, which is where all of this used to run.
                //
                // `viewModelScope.launch` dispatches to Main. The six queries above were already on
                // IO, but everything below — a regex-keyed dedup, a sort and a mapping, each O(n)
                // over every row those queries returned — ran on the UI thread. On a real catalogue
                // that showed up during start-up as frames of 1.4 seconds and runs of 120 skipped
                // frames. None of this work touches Compose state, so none of it belongs on Main.
                withContext(ioDispatcher) { sources.run {
                // This year's releases split by kind rather than this year's films followed by last
                // year's. A rail of the previous year reads as old stock next to one labelled with
                // the current year; series of the same year is the shelf people actually look for.
                // Last year's releases stay as the fallback, so a source with few current titles
                // still fills the home rather than showing a gap.
                val releasesSeries =
                    releasesCurrent.filter { it.contentType == CatalogContentType.SERIES }
                val releasesMovies =
                    releasesCurrent.filter { it.contentType != CatalogContentType.SERIES }
                (
                    releasesMovies.map { it to "Lançamento $currentYear" } +
                        releasesSeries.map { it to "Série $currentYear" } +
                        releasesPrevious.map { it to "Lançamento ${currentYear - 1}" } +
                        recent.map { it to "Adicionado recentemente" } +
                        movies.map { it to "Filme" } +
                        series.map { it to "Série" }
                )
                    .filter { (channel, _) -> channel.logoUri != null }
                    .distinctBy { (channel, _) -> channel.id }
                    .distinctBy { (channel, _) -> dailyCatalogTitleKey(channel.name) }
                    .sortedWith(
                        compareBy<Pair<Channel, String>> { (channel, _) ->
                            dailyEditorialRank(channel.id, localEditorialDay())
                        }.thenBy { (channel, _) -> channel.id },
                    )
                    .map { (channel, editorialLabel) ->
                        channel.toCatalogUi(editorialLabel)
                    }
                } }
            }
            }.onSuccess { items ->
                val visible = items.filterKidsContentIfNeeded(mutableState.value.activeProfile)
                mutableState.update { state ->
                    state.copy(
                        homeItems = visible,
                        // Artwork is what the home screen is mostly made of, so its arrival is the
                        // honest end of start-up rather than the database opening.
                        bootStage = BootStageUi.READY,
                    )
                }
                // After READY, never before: a real synopsis is worth having but not worth holding
                // the boot screen for, and the banner reads perfectly well until it arrives.
                loadHeroSynopses(visible.take(HERO_SYNOPSIS_COUNT))
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                logger.error(TAG, "Could not compose the local home catalog", error)
                // The boot screen is held until this reports ready, so a failure has to release it
                // too. Leaving it set would trap the user on a spinner with no way forward — worse
                // than an empty home, which at least has navigation.
                markBootReady()
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
                stalkerFailure = null,
                xtreamImportStage = null,
                importSuccessVersion = it.importSuccessVersion + 1,
                section = AppSection.SOURCES,
                content = AppContent.Sources,
            )
        }
    }

    private fun finishFailedImport(
        method: SourceImportMethod,
        stalkerFailure: StalkerFailureUi? = null,
    ) {
        mutableState.update {
            it.copy(
                isImporting = false,
                lastImportedChannelCount = null,
                hasImportError = true,
                lastImportMethod = method,
                stalkerFailure = stalkerFailure,
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

    private fun Category.toUi(channelCount: Int, artworkUrl: String?): CategoryUi =
        CategoryUi(
            id = id,
            name = name,
            channelCount = channelCount,
            artworkUrl = artworkUrl,
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

    /** Keeps the times, which is what lets the guide print a clock against each programme. */
    private fun LiveProgram.toUi(): LiveProgramUi =
        LiveProgramUi(
            title = title,
            description = description,
            startEpochSeconds = startEpochSeconds,
            endEpochSeconds = endEpochSeconds,
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
            photoUri = photoUri,
            sourceId = sourceId,
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

    /**
     * Maps a portal failure onto the reason the screen shows.
     *
     * The repository validates locally before it reaches the network, so an
     * [IllegalArgumentException] here means the form got past validation with something the
     * portal contract rejects, not that the portal refused the subscription.
     */
    private fun Throwable.toStalkerFailureUi(): StalkerFailureUi {
        val stalkerFailure = generateSequence(this, Throwable::cause)
            .filterIsInstance<StalkerClientException>()
            .firstOrNull()
        if (stalkerFailure != null) {
            return when (stalkerFailure.reason) {
                StalkerFailureReason.UNAUTHORISED -> StalkerFailureUi.UNAUTHORISED
                StalkerFailureReason.BLOCKED -> StalkerFailureUi.BLOCKED
                StalkerFailureReason.NETWORK -> StalkerFailureUi.NETWORK
                StalkerFailureReason.MALFORMED -> StalkerFailureUi.MALFORMED
            }
        }
        return if (this is IllegalArgumentException || this is IllegalStateException) {
            StalkerFailureUi.INVALID_INPUT
        } else {
            StalkerFailureUi.NETWORK
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

    /** The five catalogue queries the home rails are built from, fetched together. */
    private data class HomeSources(
        val releasesCurrent: List<Channel>,
        val releasesPrevious: List<Channel>,
        val recent: List<Channel>,
        val movies: List<Channel>,
        val series: List<Channel>,
    )

    private companion object {
        const val TAG = "MainViewModel"

        /** Concurrent transfers. See [downloadSlots] for why this is not unbounded. */
        const val DOWNLOAD_SLOTS = 3

        /**
         * How much of a key has to be typed before it is worth asking the server about.
         *
         * Keys are far longer than this; the point is only to skip the first few keystrokes, where
         * every answer would be "unknown" and would flash a scary verdict at someone who is simply
         * still typing.
         */
        const val MIN_INSPECTABLE_KEY = 6

        /**
         * How many home titles get a real synopsis fetched.
         *
         * Matches the banner's rotation: one provider call per title, so fetching the whole home
         * screen would be dozens of requests for text that is never seen.
         */
        const val HERO_SYNOPSIS_COUNT = 10

        /** Must match the id `RealHomeCatalog` builds for a service-shelf title. */
        const val STREAMING_ITEM_PREFIX = "streaming:"

        /**
         * Stands in for "a key exists" when deriving the capability.
         *
         * The capability only asks whether a key is configured, and reading the real key here would
         * pull a secret into the profile-observation path for no reason. Never sent anywhere: every
         * call that talks to TMDb reads the actual key from the encrypted store at the point of use.
         */
        const val CONFIGURED_KEY_SENTINEL = "configured"

        /**
         * Shortest interval a rate is measured over.
         *
         * Progress reports arrive far more often than this; dividing by a few milliseconds would
         * produce a wildly swinging figure that is accurate and useless.
         */
        const val RATE_SAMPLE_MIN_MILLIS = 700L
        const val PAGE_SIZE = 200
        const val HOME_ITEM_LIMIT = 16
        const val BOOT_BACKDROP_QUERY_LIMIT = 12
        const val HOME_RELEASE_LIMIT = 18
        const val HOME_RECENT_LIMIT = 18
        const val HOME_CONTINUE_LIMIT = 20
        const val HISTORY_LIMIT = 60
        const val OFFLINE_SOURCE_ID = "offline-vault"
        const val MAX_INDEXED_CAST = 32
        const val MAX_FILMOGRAPHY_ITEMS = 24
        val HIGH_RISK_VIDEO_TAG = Regex("(?i)(?:\\b4k\\b|\\buhd\\b|\\bhevc\\b|\\bh\\.?265\\b|\\[dv]|\\[hdr])")
    }

    private fun String.hasHighRiskVideoTag(): Boolean = HIGH_RISK_VIDEO_TAG.containsMatchIn(this)

    private fun String.compatibilityTitlePrefix(): String =
        replace(HIGH_RISK_VIDEO_TAG, " ")
            .replace(BRACKETED_TAG, " ")
            .replace(WHITESPACE_RUN, " ")
            .trim()
}

internal fun localEditorialDay(): Long =
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
        .replace(TITLE_KEY_BRACKETED, " ")
        .replace(TITLE_KEY_DECORATION, " ")
        .replace(TITLE_KEY_NON_ALPHANUMERIC, " ")
        .trim()

/*
 * Compiled once, not per title.
 *
 * These three literals used to sit inside the function above, which meant `Regex(...)` — parsing a
 * pattern and building a state machine — ran on every call. The function is the key selector for a
 * `distinctBy` over the whole home composition, so a catalogue of forty thousand items compiled a
 * hundred and twenty thousand regular expressions during start-up, on the main thread. That is a
 * large part of what made the boot drop over a hundred frames in a row.
 */
/** Cast lists, split on the separators providers actually use. Shared with the details screen. */
private val CAST_SEPARATOR = Regex("[,;]|\\s/\\s")

/** Any bracketed tag, and a run of whitespace: both used when reducing a title to a prefix. */
private val BRACKETED_TAG = Regex("\\[[^]]+]")
private val WHITESPACE_RUN = Regex("\\s+")

private val TITLE_KEY_BRACKETED = Regex("\\[[^]]{1,12}]")
private val TITLE_KEY_DECORATION = Regex("\\b(4k|uhd|fhd|hd|sd|h\\.?265|hevc|multi|dual)\\b")
private val TITLE_KEY_NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")

/** Real, non-empty, non-repeating covers used by the custom startup screen. */
internal fun selectBootBackdropUrls(candidates: Iterable<String?>): List<String> =
    candidates
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .distinct()
        .take(20)

/**
 * A SQL `LIKE` fragment for finding candidate rows for a shared title.
 *
 * Read from the shared *display* title rather than from the identity slug, and the difference is
 * not cosmetic. The slug is unaccented — `O Sítio [L]` becomes `o-sitio` — while the query runs
 * against the provider's raw `name`, which still reads `Sítio`. SQLite's `NOCASE` folds case but
 * not accents, so searching the slug's `sitio` inside `Sítio` matched nothing, and every accented
 * title silently failed to resolve. That is most of a Portuguese catalogue.
 *
 * So the fragment is the longest run of letters and digits **as written**, which is a literal
 * substring of the name whatever accents it carries: `Sítio` yields `tio`, and `%tio%` finds it.
 * Short and imprecise on purpose — this only gathers candidates, and [Channel.matches] decides.
 *
 * The longest run rather than the first: leading articles ("o", "a", "the") appear in thousands of
 * rows and would return a candidate page that does not contain the film at all.
 */
internal fun sharedTitleSearchFragment(title: String): String {
    val runs = title.split(NON_SEARCHABLE).filter(String::isNotEmpty)
    return runs.maxByOrNull(String::length).orEmpty()
}

/**
 * Anything that is not a plain unaccented letter or digit.
 *
 * Accented characters are separators here rather than content: they are exactly the characters the
 * query cannot rely on matching, so a fragment is cut around them instead of through them.
 */
private val NON_SEARCHABLE = Regex("""[^A-Za-z0-9]""")

/**
 * Whether this catalogue row is the work [identity] names.
 *
 * The row's own identity is recomputed and compared, so the same normalisation runs on both sides:
 * the sender's "[4K] Duna (2021) DUAL" and the recipient's "Duna 1080p LEG" reduce to one key. A
 * year written into the name is read out of it when the provider left the field empty, which is the
 * common case in an Xtream list.
 */
internal fun Channel.matches(identity: ContentIdentity): Boolean {
    val kind =
        when (contentType) {
            CatalogContentType.SERIES -> ContentKind.SERIES
            CatalogContentType.MOVIE -> ContentKind.MOVIE
            else -> return false
        }
    val resolvedYear = year ?: ContentIdentity.yearFromTitle(name)
    if (ContentIdentity.of(kind, name, resolvedYear) == identity) return true

    // A row whose year is unknown still matches a link that carries one, provided the names agree.
    // Playlists frequently omit the year entirely, and refusing those would fail on exactly the
    // catalogues this feature is for. The reverse is never allowed: a row with a *different* year
    // is a remake, and opening the wrong film is worse than opening nothing.
    return resolvedYear == null && ContentIdentity.of(kind, name) == identity.withoutYear()
}

/** The identity without its trailing year, for comparing against a row that states none. */
private fun ContentIdentity.withoutYear(): ContentIdentity {
    val trailing = key.substringAfterLast(':', missingDelimiterValue = "")
    return if (trailing.toIntOrNull() != null) {
        ContentIdentity(key.substringBeforeLast(':'))
    } else {
        this
    }
}
