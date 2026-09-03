package com.lucasserafin94.iptvburo.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasserafin94.iptvburo.domain.model.Reminder
import com.lucasserafin94.iptvburo.domain.model.SubscriptionExpiry
import com.lucasserafin94.iptvburo.domain.model.BannerTrailer
import com.lucasserafin94.iptvburo.domain.model.LiveGuide
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.core.logging.AppLogger
import com.lucasserafin94.iptvburo.data.download.AndroidDownloadManager
import com.lucasserafin94.iptvburo.data.download.DownloadResult
import com.lucasserafin94.iptvburo.data.discovery.ProviderLogoCatalogue
import com.lucasserafin94.iptvburo.data.discovery.StreamingDiscoveryRepository
import com.lucasserafin94.iptvburo.data.preferences.CatalogueGuard
import com.lucasserafin94.iptvburo.data.preferences.SourceMergeSettings
import com.lucasserafin94.iptvburo.data.preferences.NotificationCentreStore
import com.lucasserafin94.iptvburo.domain.model.NotificationCentre
import com.lucasserafin94.iptvburo.data.preferences.OnboardingPreferences
import com.lucasserafin94.iptvburo.data.cache.ArtworkCacheAccess
import com.lucasserafin94.iptvburo.data.cache.CacheFillWorker
import com.lucasserafin94.iptvburo.data.preferences.CacheFillMark
import com.lucasserafin94.iptvburo.data.preferences.CacheSettingsStore
import com.lucasserafin94.iptvburo.domain.model.CacheBudget
import com.lucasserafin94.iptvburo.domain.model.CacheFillProgress
import com.lucasserafin94.iptvburo.data.preferences.PlaybackSessionStore
import com.lucasserafin94.iptvburo.data.preferences.BannerSoundSettings
import com.lucasserafin94.iptvburo.data.preferences.SubtitleSettings
import com.lucasserafin94.iptvburo.data.licensing.AndroidLicenseService
import com.lucasserafin94.iptvburo.data.licensing.RedeemOutcome
import com.lucasserafin94.iptvburo.data.security.MetadataKeyStore
import com.lucasserafin94.iptvburo.data.repository.CatalogRepository
import com.lucasserafin94.iptvburo.data.diagnostics.ConnectionTester
import com.lucasserafin94.iptvburo.data.repository.DownloadRateReporter
import com.lucasserafin94.iptvburo.data.repository.LiveProgram
import com.lucasserafin94.iptvburo.data.repository.CatalogCursor
import com.lucasserafin94.iptvburo.data.repository.BuroProfile
import com.lucasserafin94.iptvburo.data.repository.ProfileType
import com.lucasserafin94.iptvburo.data.reminders.ReminderScheduling
import com.lucasserafin94.iptvburo.data.repository.ReminderRepository
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
import com.lucasserafin94.iptvburo.ui.cast.CastReceiver
import com.lucasserafin94.iptvburo.ui.cast.CastSender
import com.lucasserafin94.iptvburo.ui.cast.CastTarget
import com.lucasserafin94.iptvburo.ui.cast.CastUiState
import com.lucasserafin94.iptvburo.di.IoDispatcher
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.CatalogueFilter
import com.lucasserafin94.iptvburo.domain.model.CatalogueLayout
import com.lucasserafin94.iptvburo.domain.model.Category
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.DiscoveryVerdict
import com.lucasserafin94.iptvburo.domain.model.SessionTaste
import com.lucasserafin94.iptvburo.domain.model.DiscoveryCandidate
import com.lucasserafin94.iptvburo.domain.model.DiscoveryDeck
import com.lucasserafin94.iptvburo.domain.model.TasteProfile
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
import com.lucasserafin94.iptvburo.metadata.CriticScores
import com.lucasserafin94.iptvburo.metadata.CriticScoresClient
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

@HiltViewModel
class MainViewModel @Inject constructor(
    @param:ApplicationContext private val contextProvider: Provider<Context>,
    private val catalogRepository: CatalogRepository,
    private val connectionTester: ConnectionTester,
    private val downloadRateReporter: DownloadRateReporter,
    private val onboardingPreferences: OnboardingPreferences,
    private val userLibraryRepository: UserLibraryRepository,
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduling,
    private val playbackSessionPreferences: PlaybackSessionStore,
    private val notificationCentrePreferences: NotificationCentreStore,
    private val cacheSettings: CacheSettingsStore,
    private val artworkCache: ArtworkCacheAccess,
    private val downloadManager: AndroidDownloadManager,
    private val licenseService: AndroidLicenseService,
    private val metadataKeyStore: MetadataKeyStore,
    private val streamingDiscoveryRepository: StreamingDiscoveryRepository,
    private val okHttpClient: OkHttpClient,
    private val providerLogoCatalogue: ProviderLogoCatalogue,
    private val playbackProgressRepository: PlaybackProgressRepository,
    private val catalogueGuardPreferences: CatalogueGuard,
    private val sourceMergeSettings: SourceMergeSettings,
    private val subtitlePreferences: SubtitleSettings,
    private val bannerSoundPreferences: BannerSoundSettings,
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
    private var personJob: Job? = null
    private var playbackJob: Job? = null
    private var movieDetailsJob: Job? = null
    private var seriesDetailsJob: Job? = null
    private var loadFavoritesJob: Job? = null
    private var providerLogoJob: Job? = null
    private var importJob: Job? = null
    private var homeJob: Job? = null
    private var bootBackdropJob: Job? = null

    /**
     * Whether the profile remembered from last time may see the catalogue's own covers.
     *
     * Started in `init` so the preference read and the single profile row resolve while Room is
     * still opening, rather than after the first source list arrives. On a real phone that opening
     * is most of the wait — the covers themselves take about a fifth of a second — so asking these
     * two questions in parallel with it is what lets the loading screen start on real artwork
     * instead of swapping to it a few seconds in. Awaited rather than polled, so a slow database
     * delays the covers and never the boot.
     */
    private val earlyBootProfileAllowed: Deferred<Boolean> =
        viewModelScope.async(start = CoroutineStart.LAZY) {
            val storedId = onboardingPreferences.activeProfileId.firstOrNull()
            val stored =
                storedId?.let { id ->
                    runCatching { userLibraryRepository.getProfile(id) }.getOrNull()
                }
            mayLoadEarlyBootBackdrop(stored?.type)
        }
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

    /** The artwork cache fill's own last reading, for the same rate measurement as a download. */
    private var cacheFillRateSample: DownloadRateSample? = null
    private var seriesEpisodes: Map<String, Episode> = emptyMap()
    private val knownMovieChannels = LinkedHashMap<String, ChannelUi>()
    private val actorMovieIds = LinkedHashMap<String, LinkedHashSet<String>>()
    private var nextChannelCursor: CatalogCursor? = null

    /**
     * Category names for the "every title" screen, by category id.
     *
     * Only that screen needs this: a single named category already knows its own name and passes
     * it straight through to every row. "Every title" mixes rows from every category at once, so
     * each row needs its *own* category resolved — the genre filter and the genre a card shows
     * both come from this, and both went blank when every row was stamped with one fixed name
     * ("" for this screen) instead.
     */
    private var channelCategoryNames: Map<String, String> = emptyMap()

    init {
        // Runs alongside Room opening rather than after it, which is where most of a cold start
        // goes. By the time the first source list arrives the answer is usually already in hand.
        earlyBootProfileAllowed.start()
        observeDownloadRate()
        // Kept current so a page turn can read it without collecting. Changing it takes effect the
        // next time a source is opened rather than immediately: rebuilding a catalogue under
        // somebody who is browsing would empty the screen they are looking at.
        viewModelScope.launch {
            sourceMergeSettings.mergeEverySource.collect { enabled ->
                mergeEverySource = enabled
                mutableState.update { it.copy(mergeEverySource = enabled) }
            }
        }
        observeOnboarding()
        observeProfiles()
        observeSources()
        observeCatalogueGuard()
        observeSubtitles()
        observeBannerSound()
        // Device-wide, like the notification hour below and unlike the bell: the cache is a
        // property of this machine's storage, not of whoever happens to be signed in.
        observeCacheSettings()
        observeProviderLogos()
        // Re-applied on every launch, not only when a reminder is marked.
        //
        // Scheduled work does not always survive: a force stop cancels it until the app is opened
        // again, and a phone restored from a backup arrives with the reminders but none of the
        // work that announces them. Without this, such a device stays silent until the user
        // happens to mark something new — which reads as the feature simply not working.
        //
        // Cheap to repeat: unique work with UPDATE replaces the existing schedule rather than
        // adding a second one.
        viewModelScope.launch {
            runCatching { reminderScheduler.sync() }
                .onFailure { error -> logger.error(TAG, "Could not schedule reminders", error) }
        }

        // Household-wide, so it is collected once here rather than per profile: unlike the reminder
        // list, switching profile does not change which hour the phone notifies at.
        viewModelScope.launch {
            reminderScheduler.schedule
                .catch { error -> logger.error(TAG, "Could not read the reminder schedule", error) }
                .collect { schedule ->
                    mutableState.update {
                        it.copy(
                            reminderNotify = schedule.notify,
                            reminderTime = schedule.time,
                        )
                    }
                }
        }
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

    private fun observeBannerSound() {
        viewModelScope.launch {
            bannerSoundPreferences.soundOn.collect { enabled ->
                mutableState.update { it.copy(bannerTrailerSound = enabled) }
            }
        }
    }

    /**
     * Turns the banner trailer's sound on or off, and remembers.
     *
     * Answered on the trailer already playing, not only on the next one — somebody reaching for the
     * switch to stop the noise means now. What it cannot change is how a trailer *starts*: no engine
     * autoplays audio, so every one begins muted whatever this says.
     */
    fun toggleBannerTrailerSound() {
        val enabled = !mutableState.value.bannerTrailerSound
        viewModelScope.launch { bannerSoundPreferences.setSoundOn(enabled) }
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
                if (status.allowsUse) applyAssignedPlaylistIfAny()
            }
    }

    /**
     * Applies the playlist the seller assigned to this device from the admin panel, if any.
     *
     * Fire-and-forget by design: this is the automatic side of provisioning, running once per
     * successful licence check, and nothing about the ordinary boot path may depend on it. A
     * seller who never used the panel gets a 204/null on every launch, which is indistinguishable
     * from a network hiccup and equally uninteresting — so failures here are logged, not surfaced.
     */
    private fun applyAssignedPlaylistIfAny() {
        viewModelScope.launch {
            runCatching {
                val assigned = withContext(ioDispatcher) { licenseService.fetchAssignedPlaylist() }
                    ?: return@runCatching
                // A delivery need not carry a connection at all: a seller whose customer already
                // has a working list may send only an API key, or only a name for it. Demanding
                // the address and password again just to hand over a key is what was reported.
                val serverUrl = assigned.serverUrl
                val username = assigned.username
                val password = assigned.password
                if (serverUrl != null && username != null && password != null) {
                    val alreadyApplied =
                        withContext(ioDispatcher) {
                            catalogRepository.hasXtreamSource(
                                serverUrl = serverUrl,
                                username = username,
                                password = password,
                            )
                        }
                    if (!alreadyApplied) {
                        catalogRepository.importXtream(
                            XtreamImportRequest(
                                // The name the seller chose, or the standing one when they chose
                                // none — never blank, which would leave an unnameable row.
                                displayName = assigned.listLabel ?: PROVISIONED_SOURCE_NAME,
                                serverUrl = serverUrl,
                                username = username,
                                password = password,
                            ),
                        )
                    }
                }
                // Never overwrites a key the customer already pasted in themselves: the seller's
                // key is a convenience for someone who has not configured one, not a takeover of a
                // choice the customer already made.
                withContext(ioDispatcher) {
                    assigned.metadataKey
                        ?.takeIf { metadataKeyStore.readShared() == null }
                        ?.let(metadataKeyStore::saveShared)
                    assigned.criticsKey
                        ?.takeIf { metadataKeyStore.readCritics() == null }
                        ?.let(metadataKeyStore::saveCritics)
                }
                withContext(ioDispatcher) { licenseService.confirmAssignedPlaylist() }
            }.onFailure { error ->
                logger.error(TAG, "Applying the seller-assigned playlist failed", error)
                runCatching {
                    withContext(ioDispatcher) {
                        licenseService.confirmAssignedPlaylist(failureCode = "apply_failed")
                    }
                }
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
            AppSection.GUIDE -> {
                updateDestination(section, AppContent.Guide)
                loadGuideChannels()
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

            // Nothing to load on the way in: the reminder observer is already running for the
            // active profile, started when that profile was selected, so the list is current before
            // the destination opens.
            AppSection.REMINDERS -> updateDestination(section, AppContent.Reminders)

            AppSection.PROFILE -> updateDestination(section, AppContent.Profiles)

            AppSection.DISCOVER -> {
                updateDestination(section, AppContent.Discover)
                // Dealt on the way in rather than kept: the deck is built from favourites and
                // watch history, and both change while the app is open.
                dealDiscoveryDeck()
            }

            AppSection.SEARCH -> updateDestination(section, AppContent.SectionPlaceholder(section))
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
    /**
     * Stores the OMDb key, which is what fills the critics' row.
     *
     * A blank value clears it, which is how the setting is switched off: with no key the lookup
     * never runs and the row falls back to the scores the app already had.
     */
    fun saveCriticsKey(apiKey: String) {
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) { metadataKeyStore.saveCritics(apiKey) }
            }.onSuccess {
                mutableState.update { it.copy(criticsKeyConfigured = apiKey.isNotBlank()) }
            }.onFailure { error ->
                logger.error(TAG, "Could not update the critics key", error)
            }
        }
    }

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

    // ---------------------------------------------------------------------------------------
    // Receiving a title from a computer on the same network
    // ---------------------------------------------------------------------------------------

    private val castReceiver by lazy {
        CastReceiver(
            context = contextProvider.get(),
            displayName = mutableState.value.activeProfile?.name.orEmpty(),
        )
    }

    /**
     * Starts listening and publishes the code to type into the sender.
     *
     * Called when the receive sheet opens and stopped when it closes, so the socket exists only
     * while somebody is looking at the code — see [CastReceiver] for why a phone does not listen in
     * the background.
     */
    fun startCastReceiver() {
        val code =
            castReceiver.start(existingCode = mutableState.value.castReceiverCode) { message ->
                // The same path a shared link takes, because both name a title rather than a
                // stream: the identity is resolved against *this* device's catalogue, so the
                // sender's row ids never travel and each side opens its own copy.
                openSharedLink(
                    TitleShareLink(
                        identity = message.identity,
                        title = message.title,
                        year = null,
                        artworkUrl = null,
                        description = null,
                    ).appUri(),
                )
            }
        mutableState.update { it.copy(castReceiverCode = code, isCastReceiverOn = code != null) }
    }

    fun stopCastReceiver() {
        castReceiver.stop()
        // The code itself is kept: it is this device's, reused next time so it does not have to be
        // retyped into the computer on every session. Only the listening state goes false.
        mutableState.update { it.copy(isCastReceiverOn = false) }
    }

    override fun onCleared() {
        super.onCleared()
        // The socket must not outlive the screen that showed its code.
        runCatching { castReceiver.stop() }
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

    private var expandedServiceJob: Job? = null

    /**
     * Opens one service's whole catalogue, from the "Ver mais" at the end of its shelf.
     *
     * The shelf holds twenty titles because that is what fits on a rail, and reaching its end is
     * where somebody asks what else is there. This walks the pages after the first rather than
     * widening every shelf, which would make the whole screen slower to fill for a question most
     * visits never ask.
     */
    fun expandService(shelf: ProviderShelfUi) {
        val providerId = shelf.tmdbProviderId ?: return
        openExpandedService(
            providerId = providerId,
            providerLabel = shelf.providerId,
            providerName = shelf.providerName,
            initialTitles = shelf.titles,
        )
    }

    /**
     * Opens a service's whole catalogue directly, from the shortcut row on Filmes/Séries.
     *
     * Unlike [expandService], there is no shelf here to draw while the request is in flight — the
     * shortcut is offered before Assinaturas has been visited at all — so it starts from an empty
     * list and a spinner instead of twenty titles already on hand.
     */
    fun openProviderShortcut(provider: com.lucasserafin94.iptvburo.data.discovery.DiscoveredProvider) {
        selectSection(AppSection.SUBSCRIPTIONS)
        openExpandedService(
            providerId = provider.tmdbProviderId,
            providerLabel = provider.label,
            providerName = provider.label,
            initialTitles = emptyList(),
        )
    }

    private fun openExpandedService(
        providerId: Int,
        providerLabel: String,
        providerName: String,
        initialTitles: List<SubscriptionTitleUi>,
    ) {
        expandedServiceJob?.cancel()
        // The shelf's own titles are shown immediately rather than a spinner over an empty page:
        // they are the first twenty of exactly the list being fetched, so the screen is useful
        // while the rest arrives.
        mutableState.update {
            it.copy(
                subscriptions =
                    it.subscriptions.copy(
                        expandedService =
                            ExpandedServiceUi(
                                providerId = providerLabel,
                                providerName = providerName,
                                titles = initialTitles,
                                isLoading = true,
                            ),
                    ),
            )
        }
        expandedServiceJob =
            viewModelScope.launch {
                val state = mutableState.value
                val titles =
                    runCatching {
                        streamingDiscoveryRepository.allTitlesOnService(
                            apiKey = activeMetadataKey(),
                            region = state.subscriptions.region,
                            tmdbProviderId = providerId,
                            kind = state.subscriptions.kind.toDiscoverKind(),
                        )
                    }.onFailure { error ->
                        logger.error(TAG, "Could not expand a service catalogue", error)
                    }.getOrDefault(emptyList())

                mutableState.update { current ->
                    // Guarded: the viewer can close this or open another service while the pages
                    // are still arriving, and a late answer must not reopen what they left.
                    val open = current.subscriptions.expandedService ?: return@update current
                    if (open.providerId != providerLabel) return@update current
                    current.copy(
                        subscriptions =
                            current.subscriptions.copy(
                                expandedService =
                                    open.copy(
                                        // Falls back to the shelf's own titles when the request
                                        // came back empty, so a failure shows twenty titles rather
                                        // than an empty page under a service's name.
                                        titles = titles.map { title -> title.toUi() }.ifEmpty { open.titles },
                                        isLoading = false,
                                    ),
                            ),
                    )
                }
            }
    }

    /** Returns from a service's full catalogue to the shelves. */
    fun closeExpandedService() {
        expandedServiceJob?.cancel()
        mutableState.update {
            it.copy(subscriptions = it.subscriptions.copy(expandedService = null))
        }
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
                // The moment the automatic first profile stops being needed: the household now has
                // one it named itself, so the scaffolding can come down. Only removed when it was
                // never used — see `removeUnusedDefaultProfile` for what that means.
                //
                // After creation rather than before, so a failure above leaves the default in place
                // rather than a picker with nothing in it.
                userLibraryRepository.removeUnusedDefaultProfile(
                    runCatching { context.getString(R.string.profile_default_name) }
                        .getOrDefault("Buro"),
                )
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
        // A marked title. Opening the card leads to the reminders page rather than to playback:
        // the rail exists to show what is coming, and an upcoming title has nothing to play yet.
        // Films already in the catalogue matched the lookup above and never reach this.
        if (itemId.startsWith(REMINDER_ITEM_PREFIX)) {
            selectSection(AppSection.REMINDERS)
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
                // Blank asks the repository for every subscription at once, each title kept only
                // from the largest list that has it.
                //
                // Set here rather than at each query: this is the one place a source is chosen, and
                // everything downstream — categories, pages, the cursor — carries the id from this
                // object. Threading a flag through all of them instead would leave one of them able
                // to disagree with the rest.
                sourceId = if (mergeEverySource) "" else source.id,
                sourceName = source.name,
                contentType = null,
            )
        navigate(content)
        observeCategories(content)
    }

    /**
     * Whether every configured subscription is browsed as one catalogue.
     *
     * Held rather than collected per query: it is read on every page turn, and a Flow sampled that
     * often would cost more than the setting is worth. Updated by the collector below.
     */
    @Volatile
    private var mergeEverySource = false

    /**
     * Stores the choice.
     *
     * It takes effect the next time a source is opened rather than immediately: rebuilding a
     * catalogue underneath somebody who is browsing would empty the screen they are looking at.
     */
    fun setMergeEverySource(enabled: Boolean) {
        viewModelScope.launch { sourceMergeSettings.setMergeEverySource(enabled) }
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

        personJob?.cancel()
        personJob =
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

    /**
     * Reaches a screen by its typed address, for a network where the search finds nothing.
     *
     * Suspends rather than launching into [castJob]: the field waits on the answer to say whether
     * the address worked, and a fire-and-forget call would leave it unable to tell "nothing there"
     * from "still trying".
     */
    suspend fun connectToScreenAt(address: String): Boolean {
        val connected = castController.connectTo(address)
        publishCastState()
        return connected
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
        playbackJob?.cancel()
        mutableState.update {
            it.copy(
                isResolvingPlayback = true,
                hasPlaybackError = false,
            )
        }
        playbackJob =
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

    // ---------------------------------------------------------------------------------------
    // The live guide
    // ---------------------------------------------------------------------------------------

    /** Channels already being fetched, so a D-pad sweep does not ask for the same one twice. */
    private val guideInFlight = mutableSetOf<String>()

    /** When each schedule was fetched, so a fresh one is not asked for again. */
    private val guideFetchedAt = mutableMapOf<String, Long>()

    /**
     * The live channels the guide lists.
     *
     * The same page the catalogue uses, so merging and the parental policy apply here exactly as
     * they do everywhere else rather than through a second path that could disagree.
     */
    private fun loadGuideChannels() {
        viewModelScope.launch {
            val source = mutableState.value.sources.firstOrNull() ?: return@launch
            runCatching {
                catalogRepository.loadChannelsPageAfter(
                    sourceId = if (mergeEverySource) "" else source.id,
                    categoryId = null,
                    contentType = CatalogContentType.LIVE,
                    cursor = null,
                    limit = GUIDE_CHANNEL_LIMIT,
                )
            }.onSuccess { page ->
                val channels =
                    page.items
                        .map { channel -> channel.toCatalogUi(channel.categoryId?.let(channelCategoryNames::get).orEmpty()) }
                        .filterKidsContentIfNeeded(mutableState.value.activeProfile)
                mutableState.update { state -> state.copy(channels = channels) }
                channels.firstOrNull()?.let { first -> focusGuideChannel(first) }
            }
        }
    }

    /**
     * Moves the guide to [channel] and fetches what it needs.
     *
     * The focused row first, then the few either side: moving down a list one row at a time is how
     * a guide is read, so the next rows are worth having in hand. See [LiveGuide].
     */
    fun focusGuideChannel(channel: ChannelUi) {
        mutableState.update { state -> state.copy(guideFocusedChannelId = channel.id) }
        val channels = mutableState.value.channels
        val index = channels.indexOfFirst { it.id == channel.id }
        if (index < 0) return
        fetchGuideSchedule(channel)
        LiveGuide.prefetchWindow(index, channels.size).forEach { neighbour ->
            channels.getOrNull(neighbour)?.let(::fetchGuideSchedule)
        }
    }

    private fun fetchGuideSchedule(channel: ChannelUi) {
        val streamId = channel.providerItemId ?: return
        val nowSeconds = System.currentTimeMillis() / 1_000L
        val fetchedAt = guideFetchedAt[channel.id]
        if (fetchedAt != null && LiveGuide.isFresh(fetchedAt, nowSeconds)) return
        if (!guideInFlight.add(channel.id)) return

        viewModelScope.launch {
            mutableState.update { state ->
                state.copy(guideLoadingChannelIds = state.guideLoadingChannelIds + channel.id)
            }
            try {
                val epg =
                    runCatching { catalogRepository.loadShortEpg(channel.sourceId, streamId) }
                        .getOrNull()
                // A channel with no schedule is recorded as empty rather than left unfetched, or
                // every pass over it would ask the provider again for the same answer.
                val entries = epg?.schedule.orEmpty().map { it.toUi() }
                guideFetchedAt[channel.id] = nowSeconds
                mutableState.update { state ->
                    // Bounded, because each entry is a few hours of programmes for one channel and
                    // an evening of browsing would otherwise hold the whole catalogue's schedule.
                    val trimmed =
                        if (state.guideSchedules.size >= LiveGuide.MAX_CACHED_SCHEDULES) {
                            state.guideSchedules.entries
                                .drop(state.guideSchedules.size - LiveGuide.MAX_CACHED_SCHEDULES + 1)
                                .associate { it.key to it.value }
                        } else {
                            state.guideSchedules
                        }
                    state.copy(
                        guideSchedules = trimmed + (channel.id to entries),
                        guideLoadingChannelIds = state.guideLoadingChannelIds - channel.id,
                    )
                }
            } finally {
                guideInFlight.remove(channel.id)
            }
        }
    }

    /**
     * How many channels the guide lists.
     *
     * A guide is read by moving through it, not by loading a whole four-hundred-channel catalogue
     * into one screen. Generous enough that the ordinary list arrives whole.
     */
    private val GUIDE_CHANNEL_LIMIT = 400

    // ---------------------------------------------------------------------------------------
    // The banner's trailer
    // ---------------------------------------------------------------------------------------

    /** Titles already being looked up, so a rotation does not ask twice for the same one. */
    private val heroTrailerLookups = mutableSetOf<String>()

    /**
     * The trailer for a banner title, or null when there is none worth playing.
     *
     * Every null is a reason the viewer would otherwise meet a failure on the opening screen. The
     * decision itself is the shared one, so the three apps cannot drift.
     */
    fun heroTrailerFor(itemId: String): String? {
        val state = mutableState.value
        val videoId = state.heroTrailers[itemId] ?: return null
        return videoId.takeIf {
            BannerTrailer.shouldPlay(
                videoId = it,
                failedAtEpochSeconds = state.heroTrailerFailures[itemId],
                nowEpochSeconds = System.currentTimeMillis() / 1_000L,
                // A trailer talking over the film somebody chose would not be a feature.
                somethingElseIsPlaying = state.content is AppContent.Player,
            )
        }
    }

    /**
     * Looks up the trailer for a banner title, once.
     *
     * Only for a title the banner has actually reached: the rotation holds twenty and most of them
     * are never seen, so looking all twenty up would be twenty requests for one viewing.
     */
    fun loadHeroTrailer(
        itemId: String,
        title: String,
        /**
         * The release year, and whether this is a series.
         *
         * TMDb keeps television and film in separate catalogues, so a series searched as a film
         * finds nothing at all — and the banner shows series as often as films. Windows had exactly
         * this defect and no trailer ever played for a series there. Defaulted so existing callers
         * keep working, and both are passed where they are known.
         */
        year: Int? = null,
        isSeries: Boolean = false,
    ) {
        if (mutableState.value.heroTrailers.containsKey(itemId)) return
        if (!heroTrailerLookups.add(itemId)) return

        viewModelScope.launch {
            val apiKey = activeMetadataKey()
            if (apiKey.isNullOrBlank()) {
                heroTrailerLookups.remove(itemId)
                return@launch
            }
            val client =
                TmdbClient(
                    apiKey = apiKey,
                    client = okHttpClient,
                    language = Locale.getDefault().toLanguageTag(),
                )
            val videoId =
                withContext(ioDispatcher) {
                    runCatching {
                        client.findTrailer(title = title, year = year, isSeries = isSeries)
                    }.getOrNull()
                }
            heroTrailerLookups.remove(itemId)
            // Stored even when null — blank reads as "asked, and there is none" — which is what
            // stops a miss being looked up again on every pass of the rotation.
            mutableState.update { state ->
                state.copy(heroTrailers = state.heroTrailers + (itemId to videoId.orEmpty()))
            }
        }
    }

    /**
     * Records that a banner trailer would not play.
     *
     * Called by the screen, because only the player knows. Remembered so the next rotation shows
     * the artwork immediately rather than failing again in front of the viewer.
     */
    fun rememberHeroTrailerFailure(itemId: String) {
        val nowSeconds = System.currentTimeMillis() / 1_000L
        mutableState.update { state ->
            state.copy(
                heroTrailerFailures =
                    BannerTrailer.pruneFailures(state.heroTrailerFailures, nowSeconds) +
                        (itemId to nowSeconds),
            )
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
        playbackJob?.cancel()
        mutableState.update {
            it.copy(
                isResolvingPlayback = true,
                hasPlaybackError = false,
            )
        }
        playbackJob =
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

    /**
     * Runs the connection test against the subscription now in use.
     *
     * Suspending rather than returning through the state: the screen shows the work happening while
     * it waits, and routing several seconds of measurement through the UI state would make every
     * other part of the screen recompose for readings only one panel wants.
     */
    suspend fun runDiagnostics(): ConnectionTester.Report =
        connectionTester.run(
            // The first Xtream subscription: an M3U file has no server to measure against, and
            // this app merges its sources rather than having one active at a time.
            sourceId =
                state.value.sources
                    .firstOrNull { source -> source.type == SourceType.XTREAM }
                    ?.id,
            loadedItems = state.value.channels.size,
        )

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

        // Saved across the cancellation below, which clears it along with the in-flight jobs.
        //
        // Without this, returning to a kept list left it unable to page any further: the rows were
        // on screen but the cursor that fetches the next two hundred had been thrown away, so
        // scrolling to the bottom simply stopped.
        val cursorBeforeCancel = nextChannelCursor
        cancelCatalogWork()
        if (previous is AppContent.Channels && mutableState.value.channels.isNotEmpty()) {
            nextChannelCursor = cursorBeforeCancel
        }
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
            is AppContent.Channels -> {
                // Reloaded only when there is nothing to come back to.
                //
                // The list the viewer left is still in state, so refetching it threw away work and
                // took the screen with it: the filter was cleared, and — because only the first
                // page is fetched — somebody who had scrolled past two hundred items came back to
                // a list too short to hold their position. Keeping what is already loaded restores
                // the screen they left, which is what a back press means.
                if (mutableState.value.channels.isEmpty()) {
                    loadInitialChannels(previous, keepFilter = true)
                }
            }
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
                downloadBytesPerSecond = null,
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
                                downloadBytesPerSecond = null,
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
                downloadBytesPerSecond = null,
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
                                downloadBytesPerSecond = null,
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
                downloadBytesPerSecond = null,
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

        // Movies and series skip the raw category grid (Action, 4K, …) and open straight onto
        // "every title", filter bar already in view — the picker inside it covers genre, which is
        // what that grid was a proxy for anyway. Live keeps the category grid: a live channel list
        // has no genre/year/sort worth filtering by, only which bouquet it belongs to.
        if (section == AppSection.MOVIES || section == AppSection.SERIES) {
            val content =
                AppContent.Channels(
                    sourceId = source.id,
                    sourceName = source.name,
                    // Empty, the same value the synthetic "All" category carries — null id plus
                    // this is the sentinel loadChannelsPageAfter and the title header both already
                    // understand as "every title", so this needs no new case in either.
                    categoryId = null,
                    categoryName = "",
                    contentType = contentType,
                )
            updateDestination(section, content)
            loadInitialChannels(content)
            return
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
                fallbackArtworkUrl = channel.logoUrl,
                channelId = channel.id,
                // Carried so the details page can name the streaming service. Providers file their
                // platform catalogues under series — "Series | Netflix", "Series | Max" — so this
                // is where the badge has the most to say.
                categoryName = channel.categoryName,
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
        movieDetailsJob?.cancel()
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
        movieDetailsJob =
            viewModelScope.launch {
                runCatching {
                    val providerDetails = catalogRepository.loadMovieDetails(
                        sourceId = content.sourceId,
                        providerMovieId = content.providerMovieId,
                    )
                    val metadataKey = activeMetadataKey()
                    // The audience score is worth fetching whether or not a trailer is needed: a
                    // provider sends a bare `rating` with no vote count, so the count — the thing
                    // that makes a score mean anything — can only come from here.
                    val needsTrailer = providerDetails.youtubeTrailerId.isNullOrBlank()
                    if (!metadataKey.isNullOrBlank()) {
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
                            val trailer =
                                if (needsTrailer) {
                                    client.findTrailer(title = cleaned, year = year)
                                        ?: client.findTrailer(
                                            title = providerDetails.title,
                                            year = year,
                                        )
                                        // Without the year as a last resort: a provider's year is
                                        // often the upload year rather than the release year, and a
                                        // wrong one excludes the correct film from the results.
                                        ?: year?.let {
                                            client.findTrailer(title = cleaned, year = null)
                                        }
                                } else {
                                    providerDetails.youtubeTrailerId
                                }
                            val score =
                                client.findAudienceScore(title = cleaned, year = year)
                                    ?: year?.let {
                                        client.findAudienceScore(title = cleaned, year = null)
                                    }
                            providerDetails.copy(
                                youtubeTrailerId = trailer,
                                // The provider's own figure stands when TMDb has nothing: it is
                                // still a score, just one without a count to qualify it.
                                rating = score?.average ?: providerDetails.rating,
                                voteCount = score?.voteCount,
                            )
                        }
                    } else {
                        providerDetails
                    }
                }.onSuccess { details ->
                    if (mutableState.value.content != content) return@onSuccess
                    mutableState.update {
                        it.copy(
                            movieDetails = details.toUi(),
                            // Cleared so the previous title's badge and scores never linger here.
                            openTitleProviderLogoUrl = null,
                            openTitleCriticScores = null,
                            openTitleSimilarTitles = emptyList(),
                            isMovieLoading = false,
                            hasMovieError = false,
                        )
                    }
                    loadProviderLogo(
                        content = content,
                        title = details.title,
                        releaseDate = details.releaseDate,
                        isSeries = false,
                    )
                    loadCriticScores(
                        content = content,
                        title = details.title,
                        releaseDate = details.releaseDate,
                        isSeries = false,
                    )
                    loadSimilarTitles(
                        content = content,
                        title = details.title,
                        releaseDate = details.releaseDate,
                        isSeries = false,
                    )
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

    /**
     * Fetches the streaming service's official mark for the open title.
     *
     * TMDb publishes the providers' own logos through `watch/providers` and licenses them for this
     * use with attribution, which is what makes showing a real Netflix or Prime mark legitimate
     * where an imitation would not be.
     *
     * Runs after the page is drawn and never blocks it: a logo is decoration, and a title whose
     * episode list waited on a second network round trip would be a worse page, not a better one.
     * Failure is silence — the badge falls back to the monogram on the service's colour.
     */
    /**
     * The critics' scores for the open title, from OMDb.
     *
     * Separate from the platform logo despite both starting with the same TMDb search, because they
     * fail independently: OMDb needs its own key, has its own daily limit, and a household that has
     * not pasted one should still see the logo. Silence on failure — the row simply shows what it
     * has, and shows nothing when it has nothing.
     */
    private fun loadCriticScores(
        content: AppContent,
        title: String,
        releaseDate: String?,
        isSeries: Boolean,
    ) {
        viewModelScope.launch {
            val criticsKey =
                withContext(ioDispatcher) {
                    runCatching { metadataKeyStore.readCritics() }.getOrNull()
                }
            if (criticsKey.isNullOrBlank()) return@launch
            val metadataKey = activeMetadataKey()
            if (metadataKey.isNullOrBlank()) return@launch

            val scores =
                withContext(ioDispatcher) {
                    runCatching {
                        val tmdb =
                            TmdbClient(
                                apiKey = metadataKey,
                                client = okHttpClient,
                                language = Locale.getDefault().toLanguageTag(),
                            )
                        val year = releaseDate?.take(4)?.toIntOrNull()
                        val cleaned = title.compatibilityTitlePrefix()
                        // OMDb is keyed by IMDb id, and the playlist has no idea what that is — so
                        // TMDb is asked to identify the title first, exactly as the logo lookup does.
                        val tmdbId =
                            tmdb.findAudienceScore(cleaned, year, isSeries)?.tmdbId
                                ?: tmdb.findAudienceScore(cleaned, null, isSeries)?.tmdbId
                                ?: return@runCatching null
                        val imdbId =
                            tmdb.titleDetails(tmdbId, isSeries)?.imdbId ?: return@runCatching null
                        CriticScoresClient(apiKey = criticsKey, client = okHttpClient)
                            .scoresFor(imdbId)
                            ?.takeIf(CriticScores::hasAny)
                    }.getOrNull()
                }
            // Guarded because the viewer can leave before this returns; scores arriving after they
            // opened something else would be attached to the wrong title.
            if (mutableState.value.content != content) return@launch
            mutableState.update { it.copy(openTitleCriticScores = scores) }
        }
    }

    private fun loadProviderLogo(
        content: AppContent,
        title: String,
        releaseDate: String?,
        isSeries: Boolean,
    ) {
        viewModelScope.launch {
            val metadataKey = activeMetadataKey()
            if (metadataKey.isNullOrBlank()) return@launch
            val logo =
                withContext(ioDispatcher) {
                    runCatching {
                        val client =
                            TmdbClient(
                                apiKey = metadataKey,
                                client = okHttpClient,
                                language = Locale.getDefault().toLanguageTag(),
                            )
                        val year = releaseDate?.take(4)?.toIntOrNull()
                        // Provider titles carry release tags — "[L]", "4K", "DUAL" — that TMDb has
                        // never heard of, so the cleaned name is tried first.
                        val cleaned = title.compatibilityTitlePrefix()
                        val tmdbId =
                            client.findAudienceScore(cleaned, year, isSeries)?.tmdbId
                                ?: client.findAudienceScore(cleaned, null, isSeries)?.tmdbId
                                ?: return@runCatching null
                        // Region matters: what a title streams on in Brazil is not what it streams
                        // on elsewhere, and the wrong region names the wrong service.
                        val region = Locale.getDefault().country.takeIf { it.isNotBlank() } ?: "BR"
                        val providers =
                            client.watchProviders(tmdbId, region, isSeries) ?: return@runCatching null
                        // The service itself, not just its logo.
                        //
                        // Films are filed by genre on most playlists — "Filmes | Lancamentos" —
                        // so the category names no platform and the badge had nothing to show,
                        // even though TMDb knows perfectly well where the film streams. This is
                        // the answer to "which service is this on", and it is the same answer the
                        // logo came from.
                        val best =
                            providers.subscription.firstOrNull()
                                ?: providers.free.firstOrNull()
                                ?: providers.withAds.firstOrNull()
                        best?.let { entry -> entry.name to entry.logoUrl }
                    }.getOrNull()
                }
            // Guarded because the viewer can leave before this returns; a logo arriving after they
            // opened something else would badge the wrong title.
            if (mutableState.value.content != content) return@launch
            mutableState.update {
                it.copy(
                    openTitleProviderName = logo?.first,
                    openTitleProviderLogoUrl = logo?.second,
                )
            }
        }
    }

    /**
     * The "you might also like" strip under the cast: franchise entries (Superman finds Superman
     * II, III, …) and genre lookalikes alike, from TMDb.
     *
     * Runs after the page is drawn, exactly like the logo and critics lookups beside it — this is
     * decoration on top of a page that already has its synopsis and cast, never something worth
     * making the viewer wait for.
     */
    private fun loadSimilarTitles(
        content: AppContent,
        title: String,
        releaseDate: String?,
        isSeries: Boolean,
    ) {
        viewModelScope.launch {
            val metadataKey = activeMetadataKey()
            if (metadataKey.isNullOrBlank()) {
                mutableState.update { it.copy(openTitleSimilarTitles = emptyList()) }
                return@launch
            }
            val similar =
                withContext(ioDispatcher) {
                    runCatching {
                        val client =
                            TmdbClient(
                                apiKey = metadataKey,
                                client = okHttpClient,
                                language = Locale.getDefault().toLanguageTag(),
                            )
                        val year = releaseDate?.take(4)?.toIntOrNull()
                        val cleaned = title.compatibilityTitlePrefix()
                        val tmdbId =
                            client.findAudienceScore(cleaned, year, isSeries)?.tmdbId
                                ?: client.findAudienceScore(cleaned, null, isSeries)?.tmdbId
                                ?: return@runCatching emptyList()
                        client.similarTitles(tmdbId, isSeries)
                    }.getOrDefault(emptyList())
                }
            // Guarded for the same reason the logo lookup is: leaving the page before this returns
            // must not attach another title's recommendations to whatever is open now.
            if (mutableState.value.content != content) return@launch
            mutableState.update {
                it.copy(
                    openTitleSimilarTitles =
                        similar.map { discovered ->
                            PersonCreditUi(
                                title = discovered.title,
                                year = discovered.year,
                                posterUrl = discovered.posterUrl,
                                character = null,
                                externalId = discovered.id.toString(),
                                isSeries = discovered.isSeries,
                            )
                        },
                )
            }
        }
    }

    private fun loadSeriesDetails(content: AppContent.SeriesDetails) {
        seriesDetailsJob?.cancel()
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
        seriesDetailsJob =
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
                            // Cleared so the previous series' strip never lingers on this one.
                            openTitleSimilarTitles = emptyList(),
                        )
                    }
                    hydrateDownloadStates(
                        seriesUi.episodes.map { episode ->
                            episodeDownloadKey(seriesUi.title, episode)
                        },
                    )
                    loadEpisodeProgress(content, details.episodes)
                    // The service's official mark, fetched after the page is already on screen.
                    //
                    // Deliberately not part of the load above: a logo is decoration, and making the
                    // episode list wait on a second network round trip would trade something the
                    // viewer came for against something they merely notice.
                    loadProviderLogo(content, seriesUi.title, seriesUi.releaseDate, isSeries = true)
                    loadCriticScores(content, seriesUi.title, seriesUi.releaseDate, isSeries = true)
                    loadSimilarTitles(content, seriesUi.title, seriesUi.releaseDate, isSeries = true)
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

    /**
     * Opens a catalogue list from scratch.
     *
     * [keepFilter] exists for the back press. Coming *back* to a list is not the same as opening a
     * new one: the viewer filtered to 2026, opened a film, and returning has to show them the list
     * they left rather than the unfiltered one. Clearing it meant choosing the year again on every
     * return, which made browsing a filtered list impractical.
     *
     * It stays false everywhere else, which is the case the clearing was written for — see below.
     */
    private fun loadInitialChannels(
        content: AppContent.Channels,
        keepFilter: Boolean = false,
    ) {
        catalogJob?.cancel()
        pageJob?.cancel()
        nextChannelCursor = null
        channelCategoryNames = emptyMap()
        mutableState.update {
            it.copy(
                channels = emptyList(),
                // Cleared with the list it described. A genre or year chosen in one category was
                // still in force in the next, and a genre that category does not have narrowed it
                // to nothing — so "Series | Netflix · 1716 itens" opened onto "this source has no
                // compatible channels" while the count on the card stayed right.
                //
                // That reasoning is about moving *between* lists, so it does not apply to a back
                // press returning to the one list the filter was chosen for.
                catalogueFilter = if (keepFilter) it.catalogueFilter else CatalogueFilter(),
                isCatalogLoading = true,
                isLoadingMore = false,
                hasMoreChannels = false,
                hasCatalogError = false,
                hasPlaybackError = false,
            )
        }
        if (content.categoryId == null) {
            // Awaited before the first page rather than fired in parallel: the category list is a
            // handful of rows, cheap next to the tens of thousands a catalogue can hold, and
            // resolving it after the first page landed would leave that page's own cards and the
            // genre filter both blank until the *second* page arrived.
            catalogJob =
                viewModelScope.launch {
                    channelCategoryNames =
                        runCatching {
                            catalogRepository.observeCategories(content.sourceId, content.contentType)
                                .first()
                                .associate { category -> category.id to category.name }
                        }.getOrDefault(emptyMap())
                    if (mutableState.value.content != content) return@launch
                    loadChannelsPage(content = content, cursor = null, append = false)
                }
        } else {
            loadChannelsPage(content = content, cursor = null, append = false)
        }
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
                                // "Every title" (categoryId null) mixes rows from every category,
                                // so each row's own category has to be resolved individually — the
                                // fixed content.categoryName is "" there and would blank out both
                                // the card's genre and the genre filter for every row.
                                val categoryName =
                                    if (content.categoryId == null) {
                                        channel.categoryId?.let(channelCategoryNames::get).orEmpty()
                                    } else {
                                        content.categoryName
                                    }
                                channel.toCatalogUi(categoryName)
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
        // And the same is true of a player that was open when Android killed the app: the row it
        // named has to be read back from the catalogue, so this is the earliest it can be reopened.
        // Ordered after the shared link deliberately — somebody who just tapped a link asked for
        // that title now, and it must not be pushed aside by what they were watching before.
        restorePlaybackSession()
    }

    /**
     * Carries the download rate from whoever is reading bytes to whichever screen is waiting.
     *
     * Collected for the life of the view model rather than started and stopped around each import:
     * the figure is published from the network layer and is null whenever nothing is arriving, so
     * an idle collector costs a single state write that nobody draws.
     */
    private fun observeDownloadRate() {
        viewModelScope.launch {
            downloadRateReporter.bytesPerSecond.collect { rate ->
                mutableState.update { it.copy(downloadBytesPerSecond = rate) }
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
                    if (accepted) checkLicense()
                }
        }
    }

    private fun observeProfiles() {
        viewModelScope.launch {
            val defaultProfileName =
                // The repository's own fallback covers a context that cannot resolve resources,
                // which is what the unit tests run against.
                runCatching { context.getString(R.string.profile_default_name) }
                    .getOrDefault("Buro")
            userLibraryRepository.ensureDefaultProfile(
                languageTag = Locale.getDefault().toLanguageTag(),
                defaultName = defaultProfileName,
            )
            // Also on the way in, not only when a profile is created: a device that named its own
            // profile before this existed is already carrying the leftover, and it would otherwise
            // sit in the picker for good. Does nothing on a fresh install, where the default is the
            // only profile there is.
            userLibraryRepository.removeUnusedDefaultProfile(defaultProfileName)
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
                    var active = profiles.firstOrNull { it.id == activeId }

                    // One profile means there is nothing to choose.
                    //
                    // Asking "who is watching?" when the answer can only be one person is a screen
                    // that exists to be dismissed. With two or more the picker still appears, which
                    // is the case it was built for.
                    //
                    // Deliberately not applied to a Kids profile: that one is a boundary somebody
                    // set on purpose, and walking into it without the picker having been shown
                    // would make a household's only child profile the silent default.
                    if (active == null && profiles.size == 1) {
                        val only = profiles.first()
                        if (!only.isKids) {
                            runCatching { onboardingPreferences.selectProfile(only.id) }
                            active = only
                        }
                    }
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
                    val effectiveKey =
                        streamingDiscoveryRepository.effectiveKey(profileKey, sharedKey)
                    val criticsConfigured =
                        withContext(ioDispatcher) {
                            runCatching { metadataKeyStore.readCritics() }.getOrNull()
                        }?.isNotBlank() == true
                    val metadataConfigured = effectiveKey != null
                    // One request for the whole region's services, so every badge in the app can
                    // draw a real mark without asking TMDb about each title it belongs to.
                    providerLogoJob?.cancel()
                    providerLogoJob = launch {
                        runCatching { providerLogoCatalogue.ensureLoaded(effectiveKey) }
                            .onFailure { error ->
                                logger.error(TAG, "Could not load the provider logos", error)
                            }
                    }
                    mutableState.update {
                        it.copy(
                            isProfilesLoading = false,
                            profiles = profiles,
                            activeProfile = active,
                            // A child's loading screen must never inherit covers selected for the
                            // previous adult profile. A regular profile fills this again from its
                            // own source as soon as the first movie and series pages are available.
                            //
                            // Arriving for the first time is not a profile change. On a cold start
                            // this collector runs with no active profile yet, so comparing ids alone
                            // treated the very first profile as a switch and cleared the covers the
                            // early load had just put up — the wall dropped back to the bundled
                            // images and then filled in again, which is the flicker this avoids.
                            // A Kids profile still clears them, whenever it appears.
                            bootBackdropUrls =
                                if (
                                    keepBootBackdropForArrivingProfile(
                                        currentActiveProfileId = it.activeProfile?.id,
                                        arrivingProfileId = active?.id,
                                        arrivingIsKids = active?.isKids == true,
                                    )
                                ) {
                                    it.bootBackdropUrls
                                } else {
                                    emptyList()
                                },
                            tmdbKeyConfigured = metadataConfigured,
                            criticsKeyConfigured = criticsConfigured,
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
                    observeReminders(active?.id)
                    observeNotifications(active?.id)
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

    private var remindersJob: Job? = null

    /**
     * Watches what the active profile asked to be reminded about.
     *
     * Mirrors the favourites observer, including the swallow-and-log on failure: a reminder list
     * that cannot be read is a reason to show none, never a reason to take the screen down.
     */
    private fun observeReminders(profileId: String?) {
        remindersJob?.cancel()
        if (profileId == null) {
            mutableState.update { it.copy(reminders = emptyList(), reminderKeys = emptySet()) }
            return
        }
        remindersJob =
            viewModelScope.launch {
                reminderRepository
                    .observe(profileId)
                    .catch { error ->
                        logger.error(TAG, "Could not observe reminders", error)
                        emit(emptyList())
                    }.collect { reminders ->
                        // Guarded because the profile can be switched while this collect is live:
                        // the old flow emits once more before its job is cancelled, and without the
                        // check one profile's reminders land in the other's state.
                        if (mutableState.value.activeProfile?.id != profileId) return@collect
                        mutableState.update {
                            it.copy(
                                reminders = reminders,
                                // Derived here rather than at each read site: the button asks "is
                                // this one marked" on every recomposition, and scanning the list
                                // for it would be a linear search inside layout.
                                reminderKeys = reminders.mapTo(mutableSetOf()) { r -> r.identity.key },
                            )
                        }
                    }
            }
    }

    /**
     * Marks or unmarks whichever details page is open.
     *
     * The identity is built the same way sharing and casting build theirs, so a reminder made today
     * still names the same title after the playlist is replaced — which is the whole reason
     * reminders are keyed by identity rather than by a catalogue row.
     *
     * The release date is carried when the catalogue knows one: that is what turns "remind me about
     * this" into "tell me when it comes out".
     */
    fun toggleReminder() {
        val profileId = mutableState.value.activeProfile?.id ?: return
        val state = mutableState.value
        val (identity, title, artwork, releaseDate) =
            when (val content = state.content) {
                is AppContent.MovieDetails -> {
                    val name = state.movieDetails?.title ?: content.fallbackTitle
                    val year =
                        state.movieDetails?.releaseDate?.let(::yearFromReleaseDate)
                            ?: ContentIdentity.yearFromTitle(name)
                    ReminderTarget(
                        ContentIdentity.of(ContentKind.MOVIE, name, year),
                        name,
                        // The catalogue row's poster when the full record has not arrived, the same
                        // way the title falls back to `fallbackTitle` two lines up. The button is
                        // deliberately enabled while the record loads, so marking a title quickly
                        // used to store a reminder with no artwork at all — and the reminders page
                        // and home rail then had nothing to draw for it, permanently.
                        state.movieDetails?.artworkUrl ?: content.fallbackArtworkUrl,
                        state.movieDetails?.releaseDate?.let(::parseReleaseDate),
                    )
                }

                is AppContent.SeriesDetails -> {
                    val name = state.seriesDetails?.title ?: content.fallbackTitle
                    val year = state.seriesDetails?.releaseDate?.let(::yearFromReleaseDate)
                    ReminderTarget(
                        ContentIdentity.of(ContentKind.SERIES, name, year),
                        name,
                        state.seriesDetails?.artworkUrl ?: content.fallbackArtworkUrl,
                        state.seriesDetails?.releaseDate?.let(::parseReleaseDate),
                    )
                }

                // A title on the "where to watch" page, which is the one place an unreleased film
                // can be reached at all: it is in nobody's playlist, so it has no details page to
                // mark it from. TMDB's poster is a public URL, so unlike a provider's artwork it
                // survives the repository's credential filter and the reminder keeps its cover.
                AppContent.Subscriptions -> {
                    val selected = state.subscriptions.selected ?: return
                    ReminderTarget(
                        ContentIdentity.of(
                            if (selected.isSeries) ContentKind.SERIES else ContentKind.MOVIE,
                            selected.title,
                            selected.year,
                        ),
                        selected.title,
                        selected.posterUrl,
                        selected.releaseDate?.let(::parseReleaseDate),
                    )
                }

                else -> return
            }

        viewModelScope.launch {
            runCatching {
                reminderRepository.toggle(
                    profileId = profileId,
                    identity = identity,
                    title = title,
                    artworkUrl = artwork,
                    // Converted at the boundary: this view model's own ReminderTarget stays
                    // java.time.LocalDate, while the repository below speaks kotlinx.datetime.
                    releaseDate = releaseDate?.let {
                        kotlinx.datetime.LocalDate(it.year, it.monthValue, it.dayOfMonth)
                    },
                )
            }.onFailure { error -> logger.error(TAG, "Could not toggle the reminder", error) }
            // Re-applied on every toggle rather than only on the first. The daily digest is unique
            // periodic work, so this replaces the existing schedule rather than adding a second —
            // and it means a phone whose scheduled work was cleared, by a force stop or by being
            // restored onto a new device, starts notifying again as soon as anything is marked.
            reminderScheduler.sync()
        }
    }

    /**
     * Turns the daily notice on or off, from the reminders page.
     *
     * The marks themselves are left alone: switching the notification off is "stop telling me",
     * not "forget what I marked", and deleting the list here would lose it for good.
     */
    fun setReminderNotify(notify: Boolean) {
        viewModelScope.launch {
            runCatching { reminderScheduler.setNotify(notify) }
                .onFailure { error -> logger.error(TAG, "Could not change the reminder notice", error) }
        }
    }

    /** Moves the daily notice to another hour. */
    fun setReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            runCatching { reminderScheduler.setTime(hour, minute) }
                .onFailure { error -> logger.error(TAG, "Could not change the reminder hour", error) }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Descobrir
    // ---------------------------------------------------------------------------------------

    private var discoverJob: Job? = null

    /**
     * Builds a hand from what this profile already keeps and watches.
     *
     * The taste is gathered from real behaviour rather than asked for: favourites, and the genres of
     * everything in the watch history. A profile that has done neither gets the best-rated titles
     * the catalogue has, which is a better opening hand than a random one.
     *
     * Reads a bounded slice of the catalogue rather than all of it — see [DISCOVER_POOL] — because a
     * playlist can hold tens of thousands of rows and the deck is fifteen.
     */
    fun dealDiscoveryDeck() {
        val profileId = mutableState.value.activeProfile?.id ?: return
        val sourceId =
            mutableState.value.sources.firstOrNull { it.type == SourceType.XTREAM }?.id
                ?: mutableState.value.sources.firstOrNull()?.id
        if (sourceId == null) {
            mutableState.update { it.copy(discoverDeck = emptyList(), isDiscoverLoading = false) }
            return
        }

        discoverJob?.cancel()
        mutableState.update { it.copy(isDiscoverLoading = true) }
        discoverJob =
            viewModelScope.launch {
                val state = mutableState.value
                val pool =
                    withContext(ioDispatcher) {
                        runCatching {
                            // Films and series only. A live channel has no synopsis, no genre and
                            // nothing to decide about at leisure, so it is not what this is for.
                            listOf(CatalogContentType.MOVIE, CatalogContentType.SERIES)
                                .flatMap { kind ->
                                    catalogRepository
                                        .loadChannelsPage(
                                            sourceId = sourceId,
                                            contentType = kind,
                                            limit = DISCOVER_POOL,
                                        ).items
                                }
                        }.onFailure { error ->
                            logger.error(TAG, "Could not read the catalogue for Descobrir", error)
                        }.getOrDefault(emptyList())
                    }

                // Category names, resolved once. A row carries only the id, and the genre is the
                // one signal this whole feature runs on.
                val categoryNames =
                    state.categories.mapNotNull { category ->
                        category.id?.let { id -> id to category.name }
                    }.toMap()
                val cards = pool.map { channel -> channel.toCatalogUi(channel.categoryId?.let(categoryNames::get).orEmpty()) }
                val byId = cards.associateBy { card -> card.id }
                // Genres come from the catalogue row's category, which is what a playlist actually
                // carries — a per-title genre would need one metadata call each, for fifteen cards.
                val favouriteGenres =
                    state.favoriteIds.mapNotNull { id -> byId[id]?.categoryName }
                val watchedGenres =
                    (state.watchHistory.map { it.channel } + state.continueWatching.map { it.channel })
                        .mapNotNull { channel -> channel.categoryName }
                val taste =
                    TasteProfile(
                        favouriteGenres = favouriteGenres,
                        watchedGenres = watchedGenres,
                        // Never offer what they already keep, already watched, or judged this
                        // session: a card they dismissed coming back reads as being ignored.
                        seenIds =
                            state.favoriteIds +
                                state.discoverJudgedIds +
                                state.watchHistory.map { it.channel.id } +
                                state.continueWatching.map { it.channel.id },
                    )

                val deck =
                    DiscoveryDeck
                        .build(
                            candidates = cards.map { card -> card.toDiscoveryCandidate() },
                            taste = taste,
                            session = state.discoverSessionTaste,
                        ).mapNotNull { candidate -> byId[candidate.id] }

                if (mutableState.value.activeProfile?.id != profileId) return@launch
                mutableState.update {
                    it.copy(
                        discoverDeck = deck,
                        discoverDealtCount = deck.size,
                        isDiscoverLoading = false,
                    )
                }
            }
    }

    /**
     * Keeps the top card: it becomes a favourite and leaves the deck.
     *
     * The favourite is what the swipe is *for*, so it is filed through the same path the heart
     * button uses — a second route to favourites would be a second thing to keep in step.
     */
    fun keepDiscoveryCard(channel: ChannelUi) {
        val profileId = mutableState.value.activeProfile?.id ?: return
        viewModelScope.launch {
            runCatching { userLibraryRepository.toggleFavorite(profileId, channel.id, false) }
                .onFailure { error -> logger.error(TAG, "Could not keep a Descobrir card", error) }
        }
        advanceDiscoveryDeck(channel, DiscoveryVerdict.KEPT)
    }

    /** Skips the top card. Remembered for this session only — see `discoverJudgedIds`. */
    fun skipDiscoveryCard(channel: ChannelUi) {
        advanceDiscoveryDeck(channel, DiscoveryVerdict.SKIPPED)
    }

    private fun advanceDiscoveryDeck(channel: ChannelUi, verdict: DiscoveryVerdict) {
        mutableState.update { state ->
            state.copy(
                discoverDeck = state.discoverDeck.filterNot { card -> card.id == channel.id },
                discoverJudgedIds = state.discoverJudgedIds + channel.id,
                // Taught immediately, so the *next* hand reflects what was just said rather than
                // waiting for the watch history to catch up — which it never would for a title
                // that was skipped and therefore never watched.
                discoverSessionTaste =
                    state.discoverSessionTaste.after(
                        channel.categoryName?.split('|', '/', ',')?.map(String::trim).orEmpty(),
                        verdict,
                    ),
            )
        }
    }

    private fun ChannelUi.toDiscoveryCandidate() =
        DiscoveryCandidate(
            id = id,
            title = name,
            // The category is the only genre a playlist row carries, and providers write it as one
            // string — "Ação | Aventura" — so it is split rather than taken whole.
            genres = categoryName?.split('|', '/', ',')?.map(String::trim).orEmpty(),
            year = year,
            rating = rating,
            isSeries = contentType == CatalogContentType.SERIES,
        )


    // ---------------------------------------------------------------------------------------
    // The bell
    // ---------------------------------------------------------------------------------------

    private var notificationsJob: Job? = null

    /**
     * Watches what the bell holds for the active profile.
     *
     * Per profile, like favourites and reminders: one household member's new episode must never
     * appear under another's bell.
     */
    private fun observeNotifications(profileId: String?) {
        notificationsJob?.cancel()
        if (profileId == null) {
            mutableState.update { it.copy(notifications = NotificationCentre()) }
            return
        }
        notificationsJob =
            viewModelScope.launch {
                notificationCentrePreferences
                    .observe(profileId)
                    .catch { error ->
                        logger.error(TAG, "Could not read the notification centre", error)
                        emit(NotificationCentre())
                    }.collect { centre ->
                        // Guarded because the profile can be switched while this collect is live:
                        // the old flow emits once more before its job is cancelled.
                        if (mutableState.value.activeProfile?.id != profileId) return@collect
                        mutableState.update { it.copy(notifications = centre) }
                    }
            }
    }

    /**
     * Watches the cache setting and the fill, for the settings card and the first-run offer.
     *
     * Started once at construction rather than per profile: the budget belongs to the device, not
     * to whoever is signed in.
     */
    /**
     * Publishes the services' official logos as they arrive.
     *
     * Started once at construction: the directory belongs to the device's region, not to whoever is
     * signed in, and every screen that draws a badge reads the same map.
     */
    private fun observeProviderLogos() {
        viewModelScope.launch {
            providerLogoCatalogue.logosByLabel
                .catch { emit(emptyMap()) }
                .collect { logos ->
                    mutableState.update { it.copy(providerLogos = logos) }
                }
        }
        viewModelScope.launch {
            providerLogoCatalogue.discoveredProviders
                .catch { emit(emptyList()) }
                .collect { providers ->
                    mutableState.update { it.copy(discoveredProviders = providers) }
                }
        }
    }

    private fun observeCacheSettings() {
        viewModelScope.launch {
            cacheSettings.observeBudget()
                .catch { error ->
                    logger.error(TAG, "Could not read the cache budget", error)
                    emit(CacheBudget.DEFAULT)
                }.collect { budget ->
                    mutableState.update { it.copy(cacheBudget = budget) }
                }
        }
        viewModelScope.launch {
            cacheSettings.observeChoicePending()
                .catch { emit(false) }
                .collect { pending ->
                    mutableState.update { it.copy(cacheChoicePending = pending) }
                }
        }
        viewModelScope.launch {
            // Combined with the stored mark because WorkManager throws a cancelled worker's
            // progress away: without the mark, pressing Pausar would empty the bar and read as the
            // download having been discarded rather than held.
            combine(
                cacheFillProgress().catch { emit(CacheFillProgress()) },
                cacheSettings.observeMark().catch { emit(CacheFillMark()) },
            ) { progress, mark ->
                if (progress.total > 0) progress
                else progress.copy(done = mark.done, total = mark.total)
            }.collect { progress ->
                mutableState.update { it.copy(cacheProgress = progress.copy(bytesPerSecond = measureCacheFillRate(progress))) }
                // Re-measured when the fill settles rather than on every tick: walking the
                // directory is far dearer than the progress update that prompted it.
                if (!progress.isRunning) refreshCacheUsage()
            }
        }
        refreshCacheUsage()
    }

    /**
     * What the fill is doing, read from WorkManager so a reopened screen sees the real position.
     *
     * Built inside a `flow { }` rather than returned directly so touching WorkManager happens when
     * the flow is collected, not when this view model is constructed. A plain JVM test has no
     * Android framework behind it, and reaching for WorkManager eagerly made every navigation
     * assertion fail on a `getApplicationContext` that is not mocked. Failing quietly here is also
     * the honest behaviour: a cache is an optimisation, and the app works without its progress bar.
     */
    /**
     * Bytes per second for the artwork cache fill, measured between this reading and the last one
     * — the same rule the video download rate uses, and for the same reason: a rate averaged from
     * the very start would keep reporting an early fast burst long after the connection slowed.
     *
     * Null while not running (nothing to measure) or before two readings far enough apart have
     * arrived (an interval is required, not invented from one sample).
     */
    private fun measureCacheFillRate(progress: CacheFillProgress): Long? {
        if (!progress.isRunning) {
            cacheFillRateSample = null
            return null
        }
        val now = System.currentTimeMillis()
        val previous = cacheFillRateSample
        val elapsedMillis = previous?.let { now - it.atEpochMillis } ?: 0L
        if (previous == null || elapsedMillis < RATE_SAMPLE_MIN_MILLIS) {
            if (previous == null) cacheFillRateSample = DownloadRateSample(progress.bytesWritten, now)
            // Keeps the last figure while the next sample accumulates, so the number does not
            // blink in and out between reports.
            return mutableState.value.cacheProgress.bytesPerSecond
        }
        cacheFillRateSample = DownloadRateSample(progress.bytesWritten, now)
        val delta = progress.bytesWritten - previous.bytes
        return if (delta > 0L) delta * 1000L / elapsedMillis else null
    }

    private fun cacheFillProgress(): Flow<CacheFillProgress> =
        flow {
            val progress =
                runCatching { CacheFillWorker.observeProgress(context) }.getOrNull()
                    ?: flowOf(CacheFillProgress())
            emitAll(progress)
        }

    private fun startCacheFillWork() {
        runCatching { CacheFillWorker.start(context) }
            .onFailure { error -> logger.error(TAG, "Could not start the cache fill", error) }
    }

    private fun stopCacheFillWork() {
        runCatching { CacheFillWorker.stop(context) }
            .onFailure { error -> logger.error(TAG, "Could not stop the cache fill", error) }
    }

    fun refreshCacheUsage() {
        viewModelScope.launch {
            val used = withContext(ioDispatcher) { artworkCache.bytesUsed() }
            mutableState.update { it.copy(cacheBytesUsed = used) }
        }
    }

    /**
     * Records the viewer's chosen size.
     *
     * Choosing zero clears what is held: somebody who turns the cache off to reclaim storage and
     * finds nothing reclaimed has been ignored. Choosing a size starts the fill, because that is
     * what the setting is for — asking again with a second button would be asking twice.
     */
    fun chooseCacheBudget(gigabytes: Int) {
        viewModelScope.launch {
            cacheSettings.chooseBudget(gigabytes)
            if (gigabytes <= 0) {
                stopCacheFill()
                clearArtworkCache()
            } else {
                startCacheFill()
            }
        }
    }

    /** Records that the viewer declined the first-run offer, so it is not made again. */
    fun declineCacheOffer() {
        chooseCacheBudget(0)
    }

    fun startCacheFill() {
        if (!mutableState.value.cacheBudget.isEnabled) return
        startCacheFillWork()
    }

    fun stopCacheFill() {
        stopCacheFillWork()
    }

    /**
     * Starts a fresh pass over the whole list.
     *
     * Distinct from [startCacheFill], which continues where a paused download left off. This is for
     * a list that has gained titles since the last full pass: the counter goes back to zero so the
     * bar describes the new pass rather than the old one, and what is already on disk is kept — the
     * fill skips it in moments, so re-checking is cheap and re-downloading would not be.
     */
    fun refreshCacheFill() {
        if (!mutableState.value.cacheBudget.isEnabled) return
        viewModelScope.launch {
            cacheSettings.rememberMark(done = 0, total = 0)
            mutableState.update { it.copy(cacheProgress = CacheFillProgress()) }
            runCatching { CacheFillWorker.restart(context) }
                .onFailure { error -> logger.error(TAG, "Could not restart the cache fill", error) }
        }
    }

    fun clearArtworkCache() {
        viewModelScope.launch {
            withContext(ioDispatcher) { artworkCache.clear() }
            mutableState.update {
                it.copy(cacheBytesUsed = 0L, cacheProgress = CacheFillProgress())
            }
        }
    }

    /** Opening the bell is reading it. */
    fun markNotificationsRead() {
        val profileId = mutableState.value.activeProfile?.id ?: return
        viewModelScope.launch { notificationCentrePreferences.markAllRead(profileId) }
    }

    /** Forgets one notice. The viewer asked for it to go, so it goes rather than being hidden. */
    fun dismissNotification(id: String) {
        val profileId = mutableState.value.activeProfile?.id ?: return
        viewModelScope.launch { notificationCentrePreferences.remove(profileId, id) }
    }

    fun clearNotifications() {
        val profileId = mutableState.value.activeProfile?.id ?: return
        viewModelScope.launch { notificationCentrePreferences.clear(profileId) }
    }

    /** Removes one reminder from the reminders page, without needing its details screen open. */
    /**
     * Opens the title a reminder refers to.
     *
     * Tapping a reminder did nothing at all: the row had no click handler and there was no way from
     * a stored identity back to a catalogue row. A reminder names a title the viewer is waiting for,
     * so opening its page is the obvious thing for a tap to do.
     *
     * The lookup is the one a shared link already uses — search by a fragment of the name, then let
     * the identity decide — because the same problem applies here. A reminder keeps the name it was
     * marked under, and the catalogue's copy may be decorated differently ("Duna 4K [L]" against
     * "Duna"), so only the identity comparison is trustworthy.
     */
    fun openReminder(reminder: Reminder) {
        viewModelScope.launch {
            val match =
                withContext(ioDispatcher) {
                    runCatching {
                        catalogRepository
                            .findLibraryCandidates(sharedTitleSearchFragment(reminder.title))
                            .firstOrNull { candidate -> candidate.matches(reminder.identity) }
                    }.getOrNull()
                }
            if (match == null) {
                // Ordinary for a title that has not been released yet, which is much of what a
                // reminders page holds. Said plainly rather than silently ignored, so a tap that
                // cannot lead anywhere does not look like a broken button.
                mutableState.update { it.copy(sharedTitleMissing = true) }
                return@launch
            }
            openChannel(match.toCatalogUi(""))
        }
    }

    fun removeReminder(identity: ContentIdentity) {
        val profileId = mutableState.value.activeProfile?.id ?: return
        viewModelScope.launch {
            runCatching { reminderRepository.remove(profileId, identity) }
                .onFailure { error -> logger.error(TAG, "Could not remove the reminder", error) }
        }
    }

    /** What a details page contributes to a reminder, gathered in one place per kind. */
    private data class ReminderTarget(
        val identity: ContentIdentity,
        val title: String,
        val artworkUrl: String?,
        val releaseDate: java.time.LocalDate?,
    )

    /**
     * Reads a provider's release date, or null when it is not a date.
     *
     * Providers send this field as anything from `2026-08-06` to an empty string to a year on its
     * own. A wrong date is worse than none — it would announce a release that has not happened — so
     * anything that does not parse is treated as "no date known".
     */
    private fun parseReleaseDate(value: String): java.time.LocalDate? =
        runCatching { java.time.LocalDate.parse(value.trim().take(10)) }.getOrNull()

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
        loadFavoritesJob?.cancel()
        mutableState.update { it.copy(isCatalogLoading = true, hasCatalogError = false) }
        loadFavoritesJob = viewModelScope.launch {
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
                    val activeSource =
                        sources.firstOrNull { source -> source.id == active?.sourceId }
                            ?: sources.firstOrNull { it.type == SourceType.XTREAM }
                            ?: sources.firstOrNull()
                    val sourceId = activeSource?.id
                    // The active source's own subscription, not the app's licence.
                    //
                    // Recomputed on every source emission rather than once, so the count keeps
                    // moving while the app sits open across midnight rather than freezing at
                    // whatever it read on launch.
                    mutableState.update {
                        it.copy(
                            subscriptionDaysLeft =
                                SubscriptionExpiry.daysLeft(
                                    expiresAtEpochSeconds = activeSource?.subscriptionExpiresAtEpochSeconds,
                                    nowEpochSeconds = System.currentTimeMillis() / 1000L,
                                ),
                        )
                    }
                    if (active != null) {
                        loadBootBackdrop(sourceId = sourceId, profile = active)
                    } else {
                        // Sources are observed from `init`, in parallel with the profile list, and
                        // they usually arrive first. Waiting for the mapped profile before asking
                        // for any artwork is what made the loading screen start on the four bundled
                        // images and visibly swap to real covers the moment the profile resolved.
                        //
                        // The stored id plus a single row read answer "is this a child's profile?"
                        // well before `observeProfiles` finishes, so the wall can start on the real
                        // catalogue without ever guessing at that boundary.
                        loadEarlyBootBackdrop(sourceId)
                    }
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
                    val candidates = loadBootBackdropCandidates(sourceId)
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

    /**
     * Fills the loading screen's wall before the profile list has been mapped.
     *
     * Waits on [earlyBootProfileAllowed], which has been resolving since `init` and answers the
     * only question this decision needs: whether the person coming back is on a Kids profile. A
     * child's loading screen keeps the neutral bundled artwork, so anything short of a confirmed
     * non-Kids profile loads nothing at all — including the case where no profile has been chosen
     * yet, where the picker is next and the covers would belong to nobody in particular.
     *
     * [loadBootBackdrop] still runs once `observeProfiles` settles and overwrites this with the
     * selection made for the profile that actually became active.
     */
    private fun loadEarlyBootBackdrop(sourceId: String?) {
        bootBackdropJob?.cancel()
        if (sourceId == null) {
            mutableState.update { it.copy(bootBackdropUrls = emptyList()) }
            return
        }
        bootBackdropJob =
            viewModelScope.launch {
                try {
                    if (!earlyBootProfileAllowed.await()) return@launch
                    val candidates = loadBootBackdropCandidates(sourceId)
                    // The profile list may have arrived while the covers were being read. Whoever it
                    // made active owns the screen from that point on, and its own load is already on
                    // the way, so this early guess must not overwrite the decision made for them.
                    if (mutableState.value.activeProfile == null) {
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
                    logger.error(TAG, "Could not prepare loading-screen artwork", error)
                }
            }
    }

    /** The first page of films and series, read together, for the loading screen's wall. */
    private suspend fun loadBootBackdropCandidates(sourceId: String): List<Channel> =
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
        downloadRateReporter.clear()
        mutableState.update {
            it.copy(
                isImporting = false,
                lastImportedChannelCount = importedItemCount,
                hasImportError = false,
                lastImportMethod = method,
                stalkerFailure = null,
                xtreamImportStage = null,
                downloadBytesPerSecond = null,
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
        downloadRateReporter.clear()
        mutableState.update {
            it.copy(
                isImporting = false,
                lastImportedChannelCount = null,
                hasImportError = true,
                lastImportMethod = method,
                stalkerFailure = stalkerFailure,
                xtreamImportStage = null,
                downloadBytesPerSecond = null,
            )
        }
    }

    private fun navigate(content: AppContent) {
        val current = mutableState.value.content
        if (current != content) {
            backStack.addLast(current)
        }
        mutableState.update { it.copy(content = content) }
        rememberOrForgetPlaybackSession(content)
    }

    private fun navigate(channel: ChannelUi) {
        navigate(AppContent.Player(channel))
    }

    /**
     * Keeps the stored session in step with whether a player is open.
     *
     * Every route into and out of playback passes through here, which is the point: an explicit
     * call at each call site is one that eventually gets forgotten at a new one, and a session left
     * behind reopens a film the viewer deliberately closed.
     *
     * Written on the way in and cleared on the way out, so what survives a killed process is only
     * ever a player that was still open when Android took the app away.
     */
    /**
     * Whether the stored session has already been considered this launch.
     *
     * `markBootReady` runs whenever the catalogue finishes loading, which includes every profile
     * switch and every playlist refresh — without this, changing profile mid-session would reopen
     * the player on top of whatever the viewer was doing.
     */
    private var restoredPlaybackSession = false

    private fun rememberOrForgetPlaybackSession(content: AppContent) {
        val profileId = mutableState.value.activeProfile?.id
        viewModelScope.launch {
            runCatching {
                if (content is AppContent.Player && profileId != null) {
                    playbackSessionPreferences.remember(content.channel.id, profileId)
                } else {
                    playbackSessionPreferences.clear()
                }
            }.onFailure { error ->
                // Never fatal: failing to remember costs the restore, not the playback.
                logger.error(TAG, "Could not record the playback session", error)
            }
        }
    }

    /**
     * Reopens the player when the process died with one on screen.
     *
     * Called once the catalogue is ready, because the channel has to be read back from it: the
     * stored session holds a row id rather than the row, for the same reason a reminder holds an
     * identity — a snapshot written hours ago would describe a catalogue that has since reloaded.
     *
     * Silent when there is nothing to restore, which is the ordinary case. A session belonging to
     * another profile is dropped rather than opened: the phone may have been handed over, and
     * resuming somebody else's film under the current profile would file the progress wrongly too.
     */
    private fun restorePlaybackSession() {
        // Only from an untouched home screen. Anywhere else means the viewer has already gone
        // somewhere themselves, and pulling them into a film they did not just ask for would be the
        // app overriding them.
        if (mutableState.value.content !is AppContent.Home) return
        // A link tapped from a cold start is still resolving and is the more recent request, so it
        // wins: reopening the old player would land on top of the title they actually tapped.
        if (pendingSharedTitle != null) return
        if (restoredPlaybackSession) return
        restoredPlaybackSession = true
        val profileId = mutableState.value.activeProfile?.id ?: return
        viewModelScope.launch {
            val session =
                runCatching { playbackSessionPreferences.current() }
                    .onFailure { error ->
                        logger.error(TAG, "Could not read the playback session", error)
                    }.getOrNull() ?: return@launch
            if (session.profileId != profileId) {
                playbackSessionPreferences.clear()
                return@launch
            }
            val channel =
                withContext(ioDispatcher) {
                    runCatching { catalogRepository.getChannel(session.channelId) }.getOrNull()
                }
            if (channel == null) {
                // The row is gone — a replaced playlist, a deleted source. Nothing to reopen, and
                // the stale session must not be tried again on every launch.
                playbackSessionPreferences.clear()
                return@launch
            }
            openChannel(channel.toCatalogUi(""))
        }
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
        personJob?.cancel()
        playbackJob?.cancel()
        movieDetailsJob?.cancel()
        seriesDetailsJob?.cancel()
        loadFavoritesJob?.cancel()
        catalogJob = null
        pageJob = null
        personJob = null
        playbackJob = null
        movieDetailsJob = null
        seriesDetailsJob = null
        loadFavoritesJob = null
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
            voteCount = voteCount,
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

        /** Display name for a source the seller assigned remotely, never typed by the customer. */
        const val PROVISIONED_SOURCE_NAME = "IPTV BURO"

        /**
         * How many home titles get a real synopsis fetched.
         *
         * Matches the banner's rotation: one provider call per title, so fetching the whole home
         * screen would be dozens of requests for text that is never seen.
         */
        const val HERO_SYNOPSIS_COUNT = 10

        /**
         * Catalogue rows read per kind when dealing a Descobrir hand.
         *
         * A playlist can hold tens of thousands and the deck is fifteen. Enough to rank
         * meaningfully, small enough that opening the tab is not a full catalogue scan.
         */
        const val DISCOVER_POOL = 400

        /** Must match the id `RealHomeCatalog` builds for a service-shelf title. */
        const val STREAMING_ITEM_PREFIX = "streaming:"

        /** Must match the id `RealHomeCatalog` builds for a reminder card. */
        const val REMINDER_ITEM_PREFIX = "reminder:"

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

    /**
     * A provider's title, cleaned enough for TMDb to match it.
     *
     * Playlists decorate names with things TMDb has never heard of — "4K", "[L]", "[DV]", and a
     * trailing release year — and an exact search on the raw name simply finds nothing. That is
     * not cosmetic: it is what silently cost these titles their trailer, their score and their
     * platform logo, since every one of those needs the search to match first.
     */
    private fun String.compatibilityTitlePrefix(): String =
        replace(HIGH_RISK_VIDEO_TAG, " ")
            .replace(BRACKETED_TAG, " ")
            .withoutTrailingYear()
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

/**
 * A trailing year in brackets, as providers append it: "Minha Carreira Brilhante (2026)".
 *
 * Stripped before a TMDb search because TMDb matches on the title alone and treats the year as a
 * separate filter — sent as part of the query it simply found nothing, which is why the platform
 * logo never arrived for titles named this way. The year is not thrown away: the caller passes it
 * as the year parameter, where it does the intended narrowing.
 *
 * Bounded to years a title can plausibly have been released in — 1900 to 2039 — and only at the
 * end. A wider 20xx range stripped the "(2049)" from *Blade Runner 2049*, whose title contains a
 * year that is not its release date; a test caught it. Anything past this decade is part of the
 * name, not a tag somebody appended.
 */
private val TRAILING_YEAR = Regex("\\s*\\((?:19\\d{2}|20[0-3]\\d)\\)\\s*$")
private val WHITESPACE_RUN = Regex("\\s+")

private val TITLE_KEY_BRACKETED = Regex("\\[[^]]{1,12}]")
private val TITLE_KEY_DECORATION = Regex("\\b(4k|uhd|fhd|hd|sd|h\\.?265|hevc|multi|dual)\\b")
private val TITLE_KEY_NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")

/**
 * Whether the loading screen may show real covers for the profile remembered from last time.
 *
 * Asked before the profile list has been mapped, so that the wall can start on the user's own
 * catalogue rather than swapping to it once the profile resolves. The permissive answer requires
 * positive proof of an adult profile: a Kids profile keeps the neutral bundled artwork, and so does
 * a start with no remembered profile, where the picker comes next and the covers would be nobody's.
 */
internal fun mayLoadEarlyBootBackdrop(storedProfileType: ProfileType?): Boolean =
    storedProfileType != null && storedProfileType != ProfileType.KIDS

/**
 * Whether covers already on the loading screen survive the profile list arriving.
 *
 * A cold start reaches this with no active profile yet, and the profile that arrives is not a
 * change of profile — treating it as one cleared the covers the early load had just put up and made
 * the wall fall back to the bundled images before filling in again. A Kids profile clears them
 * whenever it appears, and so does an actual switch to a different profile.
 */
internal fun keepBootBackdropForArrivingProfile(
    currentActiveProfileId: String?,
    arrivingProfileId: String?,
    arrivingIsKids: Boolean,
): Boolean =
    !arrivingIsKids && (currentActiveProfileId == null || currentActiveProfileId == arrivingProfileId)

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

/**
 * Drops a release year a provider appended in brackets: "Minha Carreira Brilhante (2026)".
 *
 * Its own function so the rule can be asserted directly. TMDb matches on the title alone and takes
 * the year as a separate filter, so a year left in the query simply found nothing — which is what
 * left these titles with no platform logo until it was noticed on a device.
 *
 * The year is not discarded: the caller still passes it as the year parameter, where it does the
 * narrowing that was intended.
 */
internal fun String.withoutTrailingYear(): String = replace(TRAILING_YEAR, "")
