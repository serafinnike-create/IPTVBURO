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
import com.lucasserafin94.iptvburo.desktop.playback.DesktopPlaybackProgressCoordinator
import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import com.lucasserafin94.iptvburo.desktop.security.RememberedXtreamStore
import com.lucasserafin94.iptvburo.desktop.security.XtreamSource
import com.lucasserafin94.iptvburo.desktop.security.XtreamSourceLibrary
import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import com.lucasserafin94.iptvburo.desktop.user.DesktopProfile
import com.lucasserafin94.iptvburo.desktop.user.DesktopUserStore
import com.lucasserafin94.iptvburo.desktop.user.ProfilePhotoStore
import com.lucasserafin94.iptvburo.metadata.TmdbClient
import com.lucasserafin94.iptvburo.desktop.build.BUNDLED_TMDB_KEY
import com.lucasserafin94.iptvburo.desktop.download.DesktopDownloadManager
import com.lucasserafin94.iptvburo.desktop.download.DownloadResult
import com.lucasserafin94.iptvburo.desktop.download.FailureReason
import com.lucasserafin94.iptvburo.desktop.download.StoredDownload
import com.lucasserafin94.iptvburo.desktop.download.toReadableTitle
import com.lucasserafin94.iptvburo.desktop.data.contentIdentity
import com.lucasserafin94.iptvburo.desktop.data.migrateFavoriteKeys
import com.lucasserafin94.iptvburo.domain.model.Category
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.FamilyContentPolicy
import com.lucasserafin94.iptvburo.domain.model.PlaybackContentType
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgressIdentity
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgress
import com.lucasserafin94.iptvburo.domain.model.ResumeDecision
import com.lucasserafin94.iptvburo.domain.model.SeasonalCollection
import com.lucasserafin94.iptvburo.domain.model.SeasonalCollections
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
import java.time.LocalDate
import java.util.Arrays
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Stable
class DesktopAppState(
    private val localRepository: InMemoryCatalogRepository,
    private val xtreamRepository: SessionXtreamRepository,
    private val rememberedXtreamStore: RememberedXtreamStore,
    private val userStore: DesktopUserStore = DesktopUserStore(),
    private val playbackProgressCoordinator: DesktopPlaybackProgressCoordinator = DesktopPlaybackProgressCoordinator(),
    private val downloadManager: DesktopDownloadManager = DesktopDownloadManager(),
    private val sourceLibrary: XtreamSourceLibrary = XtreamSourceLibrary(),
    private val photoStore: ProfilePhotoStore = ProfilePhotoStore(),
) {
    /**
     * Cast metadata, keyed by the user's own TMDb key.
     *
     * Rebuilt whenever the key changes so pasting one takes effect without a restart.
     */
    private var metadataClient = TmdbClient(userStore.metadataApiKey() ?: BUNDLED_TMDB_KEY.ifBlank { null })

    var metadataApiKey by mutableStateOf(userStore.metadataApiKey().orEmpty())
        private set

    val isMetadataConfigured: Boolean
        get() = metadataClient.isConfigured

    /**
     * Cast photos already looked up, by lower-cased name.
     *
     * The details page shows a dozen faces and is rebuilt on every recomposition, so each name is
     * resolved once per session; without this the same twelve lookups would run on every frame.
     */
    var castPhotos by mutableStateOf<Map<String, String?>>(emptyMap())
        private set

    private val castLookupsInFlight = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Fetches the photo for [name] if it has not been tried yet.
     *
     * A miss is cached as null, so a person TMDb does not know is not asked for again every time
     * the page is drawn.
     */
    suspend fun ensureCastPhoto(name: String) {
        val key = name.trim().lowercase(Locale.ROOT)
        if (key.isBlank() || key in castPhotos || !metadataClient.isConfigured) return
        if (!castLookupsInFlight.add(key)) return
        val photo = withContext(Dispatchers.IO) { metadataClient.findPerson(name)?.profileImageUrl }
        castPhotos = castPhotos + (key to photo)
        castLookupsInFlight.remove(key)
    }

    fun castPhotoFor(name: String): String? = castPhotos[name.trim().lowercase(Locale.ROOT)]

    fun updateMetadataApiKey(value: String) {
        metadataApiKey = value
        userStore.setMetadataApiKey(value)
        // Falling back to the bundled key rather than to nothing: clearing the field should restore
        // the default behaviour, not switch cast photos off entirely.
        metadataClient = TmdbClient(value.takeIf(String::isNotBlank) ?: BUNDLED_TMDB_KEY.ifBlank { null })
    }
    /**
     * Photo chosen during setup, before the profile it belongs to exists.
     *
     * Held as a source path until [completeSetup] creates the profile and can store it under that
     * profile's id.
     */
    var pendingProfilePhoto by mutableStateOf<java.nio.file.Path?>(null)
        private set

    fun choosePendingPhoto(source: java.nio.file.Path?) {
        pendingProfilePhoto = source
    }

    /** The stored photo for a profile, or null when it uses a drawn avatar. */
    fun photoFor(profileId: String?): java.nio.file.Path? =
        profileId?.let(photoStore::photoFor)

    /**
     * Replaces a profile's photo, or clears it when [source] is null.
     *
     * Bumping [photoRevision] is what makes the change visible: the file path does not change, so
     * nothing else would tell Compose that the picture behind it is now different.
     */
    fun setProfilePhoto(profileId: String, source: java.nio.file.Path?) {
        if (source == null) photoStore.remove(profileId) else photoStore.store(profileId, source)
        photoRevision += 1
    }

    /** Incremented whenever a photo is written or removed, so avatars recompose. */
    var photoRevision by mutableStateOf(0)
        private set

    /**
     * Moves the photo chosen during setup onto the profile that setup just created.
     *
     * The pending path is cleared either way: keeping it would apply the same picture to the next
     * profile created in this session.
     */
    private fun attachPendingPhoto(profileId: String) {
        pendingProfilePhoto?.let { source -> setProfilePhoto(profileId, source) }
        pendingProfilePhoto = null
    }
    var destination by mutableStateOf(DesktopDestination.HOME)
        private set

    var dailyHomeStatus by mutableStateOf<DailyHomeStatus>(DailyHomeStatus.Idle)
        private set

    /**
     * Bumped whenever the Home must be rebuilt from a catalogue that has changed underneath it.
     *
     * The Home's own effect is keyed on this. Without it, a manual refresh had no way to ask for a
     * reload: the source id it was keyed on does not change, so the effect never re-ran.
     */
    var dailyHomeRevision by mutableStateOf(0)
        private set

    private var dailySelectedItem: XtreamCatalogItem? = null

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

    /** Lowest rating a title may have to appear, or null for no rating filter. */
    var selectedXtreamMinimumRating by mutableStateOf<Double?>(null)
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

    fun createProfile(name: String, isKids: Boolean, avatarIndex: Int = 0) {
        if (profiles.size >= 5) return
        val clean = name.trim().take(24)
        if (clean.isBlank()) return
        profiles =
            profiles +
            DesktopProfile(
                id = java.util.UUID.randomUUID().toString(),
                name = clean,
                isKids = isKids,
                avatarIndex = avatarIndex,
            )
        userStore.saveProfiles(profiles)
    }

    /**
     * Opens the account step so a new profile can be given a playlist.
     *
     * Creating a profile from the gate only ever asked for a name and an avatar, so the choice
     * between reusing an existing playlist and adding another one — the whole point of per-profile
     * playlists — was reachable only during first-run setup.
     */
    fun startAddingProfile() {
        onboarding = OnboardingStep.Account
    }

    /**
     * Leaves the account step without creating anything.
     *
     * Only valid once a profile exists; during first-run setup there is nothing to go back to, so
     * the step stays modal.
     */
    fun cancelAddingProfile() {
        if (activeProfileId != null) onboarding = OnboardingStep.Done
    }

    /**
     * Removes a profile along with the data that belonged only to it.
     *
     * The last profile is kept: with none, the app has nowhere to store favourites and would show
     * the profile gate with nothing to pick. Downloads are deliberately untouched — they are shared
     * between profiles and are the user's own files.
     */
    fun deleteProfile(profileId: String) {
        if (profiles.size <= 1) return
        val remaining = profiles.filterNot { it.id == profileId }
        if (remaining.size == profiles.size) return

        profiles = remaining
        userStore.saveProfiles(remaining)
        userStore.setFavorites(profileId, emptySet())
        photoStore.remove(profileId)
        photoRevision += 1
        // Switching away from a deleted profile, rather than leaving the app pointing at one that
        // no longer exists.
        if (activeProfileId == profileId) selectProfile(remaining.first().id)
    }

    /**
     * Restores the application to a first-run state.
     *
     * Clears profiles, favourites, language and the active profile, and disconnects the current
     * source so nothing from the previous session survives in memory. Downloaded files are kept —
     * they are the user's own media, not a setting.
     */
    fun resetEverything() {
        userStore.resetAll()
        favoriteKeys = emptySet()
        downloads = emptyMap()
        profiles = emptyList()
        activeProfileId = null
        forgetSelectedSource()
    }

    /**
     * True until the user has picked a language for the first time.
     *
     * Drives the first-run language step. Without it the app silently defaults to Portuguese and
     * the only way to change it was a control in the header, which a first-time user has no reason
     * to look at.
     */
    var needsLanguageSetup by mutableStateOf(!userStore.hasChosenLanguage())
        private set

    fun updateLanguage(value: DesktopLanguage) {
        language = value
        userStore.setLanguage(value)
        needsLanguageSetup = false
    }

    /**
     * Where first-run setup currently is.
     *
     * Modelled as one state rather than a chain of booleans because the steps are ordered and
     * exactly one is on screen at a time; separate flags made it possible to show two at once.
     */
    var onboarding by mutableStateOf(initialOnboardingStep())
        private set

    private fun initialOnboardingStep(): OnboardingStep =
        when {
            !userStore.hasChosenLanguage() -> OnboardingStep.Language
            !userStore.hasAcceptedTerms() -> OnboardingStep.Terms
            // A profile with no playlist and no saved source means setup never finished.
            sourceLibrary.sources().isEmpty() -> OnboardingStep.Account
            else -> OnboardingStep.Done
        }

    fun acceptTerms() {
        userStore.setAcceptedTerms()
        onboarding =
            if (sourceLibrary.sources().isEmpty()) OnboardingStep.Account else OnboardingStep.Done
    }

    fun advanceOnboardingAfterLanguage() {
        onboarding = if (userStore.hasAcceptedTerms()) OnboardingStep.Account else OnboardingStep.Terms
    }

    /**
     * Creates the profile and its playlist together, proving the credentials before either is kept.
     *
     * The provider is contacted first: a playlist that never loaded is worse than no playlist, since
     * it leaves the app in a signed-in state that cannot show anything. On failure the message is
     * the provider's own, already stripped of the host and credentials.
     */
    suspend fun completeSetup(
        profileName: String,
        avatarIndex: Int,
        listLabel: String,
        input: XtreamLoginInput,
    ) {
        onboarding = OnboardingStep.Connecting
        val server = input.copyServer()
        val username = input.copyUsername()
        val password = input.copyPassword()
        try {
            connectXtream(input)
            val status = xtreamStatus
            if (status is XtreamStatus.Error) {
                onboarding = OnboardingStep.Failed(status.message)
                return
            }
            val source = sourceLibrary.create(listLabel.ifBlank { profileName })
            sourceLibrary.store(source.id).save(server, username, password)
            val profile =
                DesktopProfile(
                    id = UUID.randomUUID().toString(),
                    name = profileName.trim().ifBlank { "Meu perfil" },
                    isKids = false,
                    avatarIndex = avatarIndex,
                    sourceId = source.id,
                )
            profiles = listOf(profile) + profiles.filterNot { it.name == profile.name }
            userStore.saveProfiles(profiles)
            attachPendingPhoto(profile.id)
            selectProfile(profile.id)
            onboarding = OnboardingStep.Done
        } finally {
            Arrays.fill(server, ZERO_CHAR)
            Arrays.fill(username, ZERO_CHAR)
            Arrays.fill(password, ZERO_CHAR)
        }
    }

    /** Returns to the form so the user can correct the address or credentials. */
    fun retrySetup() {
        onboarding = OnboardingStep.Account
    }

    /** Playlists already configured, offered so a new profile can reuse one. */
    fun savedSources(): List<XtreamSource> = sourceLibrary.sources()

    /**
     * Signs in to an existing playlist and attaches it to a new profile.
     *
     * This is the household case: one subscription, several people, separate favourites.
     */
    suspend fun completeSetupWithSavedSource(
        profileName: String,
        avatarIndex: Int,
        sourceId: String,
    ) {
        val input = sourceLibrary.store(sourceId).load()
        if (input == null) {
            onboarding = OnboardingStep.Failed(null)
            return
        }
        onboarding = OnboardingStep.Connecting
        connectXtream(input)
        val status = xtreamStatus
        if (status is XtreamStatus.Error) {
            onboarding = OnboardingStep.Failed(status.message)
            return
        }
        val profile =
            DesktopProfile(
                id = UUID.randomUUID().toString(),
                name = profileName.trim().ifBlank { "Meu perfil" },
                isKids = false,
                avatarIndex = avatarIndex,
                sourceId = sourceId,
            )
        profiles = profiles + profile
        userStore.saveProfiles(profiles)
        attachPendingPhoto(profile.id)
        selectProfile(profile.id)
        onboarding = OnboardingStep.Done
    }

    fun isFavorite(item: XtreamCatalogItem): Boolean = favoriteKey(item) in favoriteKeys

    fun toggleFavorite(item: XtreamCatalogItem) {
        val profileId = activeProfileId ?: return
        val key = favoriteKey(item)
        favoriteKeys = if (key in favoriteKeys) favoriteKeys - key else favoriteKeys + key
        userStore.setFavorites(profileId, favoriteKeys)
        if (favoritesOnly && favoriteKey(item) !in favoriteKeys) {
            xtreamPage =
                xtreamPage.copy(
                    items = xtreamPage.items.filterNot { visible -> visible.providerId == item.providerId },
                    totalMatches = (xtreamPage.totalMatches - 1).coerceAtLeast(0),
                )
        }
    }

    suspend fun setFavoritesOnly(enabled: Boolean) {
        favoritesOnly = enabled
        destination = if (enabled) DesktopDestination.FAVORITES else DesktopDestination.CATALOG
        if (isXtreamSelected) refreshXtreamPage(pageIndex = 0)
    }

    /**
     * Favourite key, derived from what the content *is*.
     *
     * This used to be `contentType:providerId`. Provider ids are per-list numbering, so after the
     * user replaced their playlist the stored keys still matched — but matched unrelated titles,
     * silently marking the wrong films as favourite.
     */
    private fun favoriteKey(item: XtreamCatalogItem): String = item.contentIdentity().key

    private fun favoriteIdentities(): Set<ContentIdentity> =
        favoriteKeys.mapNotNullTo(LinkedHashSet()) { key ->
            runCatching { ContentIdentity(key) }.getOrNull()
        }

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
        get() = dailySelectedItem ?: if (destination == DesktopDestination.HOME) {
            null
        } else {
            xtreamPage.items.firstOrNull { it.providerId == selectedXtreamItemId }
                ?: xtreamPage.items.firstOrNull()
        }

    val continueWatchingEntries: List<DesktopContinueWatchingEntry>
        get() {
            // Read so Compose re-runs this when an entry is forgotten. The list comes from the
            // progress store, which is not observable, so without this the row stayed on screen.
            @Suppress("UNUSED_EXPRESSION")
            continueWatchingRevision
            val profileId = activeProfileId ?: return emptyList()
            return playbackProgressCoordinator.continueWatching(profileId)
                .asSequence()
                // No source filter. Progress is recorded against LIBRARY_SCOPE so it survives a
                // change of playlist; comparing it to the connected source's hash matched nothing
                // and left this list permanently empty.
                .mapNotNull { progress ->
                    val item = when (progress.identity.contentType) {
                        // contentId is the content key, not a provider id: the same film keeps its
                        // key across playlists, which is the whole point of recording it that way.
                        PlaybackContentType.MOVIE ->
                            xtreamRepository.itemByContentKey(
                                XtreamContentType.MOVIE,
                                progress.identity.contentId,
                            )
                        PlaybackContentType.EPISODE ->
                            progress.identity.seriesId?.let {
                                xtreamRepository.itemByProviderId(XtreamContentType.SERIES, it)
                            }
                    }
                    item?.let { DesktopContinueWatchingEntry(it, progress) }
                }
                .toList()
        }

    /**
     * Drops a title from the continue list.
     *
     * Recorded as completed rather than deleted: the progress store's own notion of "done" is what
     * every other screen already consults, so one concept covers both finishing something and
     * choosing to stop tracking it.
     */
    fun forgetProgress(entry: DesktopContinueWatchingEntry) {
        playbackProgressCoordinator.ended(entry.progress.identity, entry.progress.durationMs)
        continueWatchingRevision += 1
    }

    /** Bumped so the continue list rebuilds after an entry is removed. */
    var continueWatchingRevision by mutableStateOf(0)
        private set

    /** Opens the list of part-watched titles. */
    fun openContinueWatching() {
        favoritesOnly = false
        destination = DesktopDestination.CONTINUE
    }

    /** Opens the offline library and reconciles it with disk. */
    fun openDownloads() {
        refreshDownloadStates()
        destination = DesktopDestination.DOWNLOADS
    }

    fun openHome() {
        destination = DesktopDestination.HOME
        favoritesOnly = false
        dailySelectedItem = null
    }

    suspend fun openCatalog(contentType: XtreamContentType) {
        destination = DesktopDestination.CATALOG
        favoritesOnly = false
        selectXtreamContentType(contentType)
    }

    fun selectDailyItem(item: XtreamCatalogItem) {
        dailySelectedItem = item
        selectedXtreamItemId = item.providerId
        seriesDetailsStatus = SeriesDetailsStatus.Idle
        movieDetailsStatus = MovieDetailsStatus.Idle
        liveEpgStatus = LiveEpgStatus.Idle
    }

    suspend fun loadDailyHome(date: LocalDate = LocalDate.now()) {
        if (dailyHomeStatus is DailyHomeStatus.Loading) return
        val existing = dailyHomeStatus as? DailyHomeStatus.Loaded
        if (existing?.snapshot?.date == date && existing.snapshot.sourceId == xtreamSummary?.sourceId) return
        // Checked before the status is set to Loading. Returning after it left the Home showing its
        // skeleton for ever, because nothing else ever moves that status off Loading.
        val sourceId = xtreamSummary?.sourceId
        if (sourceId == null) {
            dailyHomeStatus = DailyHomeStatus.Idle
            return
        }
        dailyHomeStatus = DailyHomeStatus.Loading
        runCatching {
            withContext(Dispatchers.IO) {
                // Asked of the repository, not of the cached summary. The summary is only
                // published back to state in onSuccess, so a home built during startup read a
                // stale copy, believed the catalogues were still missing - or already present when
                // they were not - and paged an empty catalogue into empty shelves.
                var latestSummary = xtreamRepository.summary() ?: xtreamSummary
                if (XtreamContentType.MOVIE !in latestSummary?.loadedContentTypes.orEmpty()) {
                    latestSummary = xtreamRepository.loadCatalog(XtreamContentType.MOVIE)
                }
                if (XtreamContentType.SERIES !in latestSummary?.loadedContentTypes.orEmpty()) {
                    latestSummary = xtreamRepository.loadCatalog(XtreamContentType.SERIES)
                }
                val kidsMode = activeProfile?.isKids == true
                val movies = dailyPage(XtreamContentType.MOVIE, date.dayOfYear * 31 + date.year, kidsMode, 18)
                val series = dailyPage(XtreamContentType.SERIES, date.dayOfYear * 17 + date.year, kidsMode, 18)
                val live = dailyPage(XtreamContentType.LIVE, date.dayOfYear * 7 + date.year, kidsMode, 14)
                val heroPool = (movies + series).filter { !it.artworkUrl.isNullOrBlank() }
                DailyHomeSnapshot(
                    sourceId = sourceId,
                    date = date,
                    hero = heroPool.getOrNull(Math.floorMod(date.toEpochDay(), heroPool.size.coerceAtLeast(1).toLong()).toInt()),
                    movies = movies,
                    series = series,
                    live = live,
                    seasonal = seasonalShelf(date, kidsMode),
                ) to latestSummary
            }
        }.onSuccess { (snapshot, latestSummary) ->
            xtreamSummary = latestSummary
            // A snapshot with no films and no series is not a loaded home, it is a home built
            // before the catalogues arrived. Storing it as Loaded made the screen's own effect
            // return early for the rest of the session, so the shelves stayed empty until restart.
            if (snapshot.movies.isEmpty() && snapshot.series.isEmpty()) {
                dailyHomeStatus = DailyHomeStatus.Idle
            } else {
                dailyHomeStatus = DailyHomeStatus.Loaded(snapshot)
            }
        }
            .onFailure { error ->
                error.rethrowIfCancellation()
                dailyHomeStatus = DailyHomeStatus.Error(error.toSafeXtreamMessage())
            }
    }

    /**
     * The themed shelf for [date], or null when the calendar has nothing to offer.
     *
     * Null is also returned when the occasion is in season but this catalogue holds none of it: the
     * shelf is a promise of content, and an empty row reads as a fault rather than as an absence.
     *
     * Each term costs a full catalogue sweep, so the search stops as soon as it has enough titles
     * to fill a rail instead of collecting every possible match. Films and series are both swept
     * because providers file "O Grinch" under one and "Christmas specials" under the other.
     */
    private fun seasonalShelf(
        date: LocalDate,
        kidsMode: Boolean,
    ): DailySeasonalShelf? {
        val collection = SeasonalCollections.primaryCollectionFor(date) ?: return null
        val found = LinkedHashMap<String, XtreamCatalogItem>()
        for (term in collection.searchTerms) {
            for (type in listOf(XtreamContentType.MOVIE, XtreamContentType.SERIES)) {
                xtreamRepository
                    .page(type, null, term, 0, pageSize = SEASONAL_TERM_PAGE_SIZE, kidsMode = kidsMode)
                    .items
                    // Keyed on the editorial title so the same film shipped as "HD" and "4K" does
                    // not take two slots on a shelf that only has room for a handful.
                    .forEach { item -> found.putIfAbsent(editorialCatalogKey(item.name), item) }
            }
            if (found.size >= SEASONAL_SHELF_SIZE) break
        }
        if (found.isEmpty()) return null
        return DailySeasonalShelf(
            collection = collection,
            items = found.values.take(SEASONAL_SHELF_SIZE),
        )
    }

    private fun dailyPage(
        type: XtreamContentType,
        seed: Int,
        kidsMode: Boolean,
        pageSize: Int,
    ): List<XtreamCatalogItem> {
        val fetchSize = (pageSize * 4).coerceAtMost(80)
        val first = xtreamRepository.page(type, null, "", 0, pageSize = fetchSize, kidsMode = kidsMode)
        val pageIndex = rotatingPageIndex(seed, first.pageCount)
        val candidates = if (pageIndex == 0) first.items else {
            xtreamRepository.page(type, null, "", pageIndex, pageSize = fetchSize, kidsMode = kidsMode).items
        }
        return candidates.distinctBy { editorialCatalogKey(it.name) }.take(pageSize)
    }

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

    /**
     * True while the app is still preparing itself on launch.
     *
     * Drives the splash screen. A returning user used to meet an empty library that filled in
     * piecemeal, which reads as a broken app rather than one that is loading.
     */
    var isStarting by mutableStateOf(true)
        private set

    /** Session, connect, catalogues, home, done. */
    private val STARTUP_STEPS = 5

    /** What the splash screen is currently waiting on, so a slow provider looks like progress. */
    var startupMessage by mutableStateOf("")
        private set

    /**
     * How far startup has got, 0..1.
     *
     * Counted from the steps that actually happen — session, channels, films, series, home — rather
     * than animated against a guess. The provider never says how long a catalogue will take, so a
     * timed fake bar would be a lie the user could catch by watching it stall at 90%.
     */
    var startupProgress by mutableStateOf(0f)
        private set

    private fun startupStep(step: Int, message: String) {
        startupProgress = (step.toFloat() / STARTUP_STEPS).coerceIn(0f, 1f)
        startupMessage = message
    }

    suspend fun restoreRememberedXtream() {
        try {
            if (xtreamStatus !is XtreamStatus.Disconnected || xtreamSummary != null) return
            startupStep(1, "Abrindo a sua sessão…")
            val input = withContext(Dispatchers.IO) { rememberedXtreamStore.load() } ?: return
            startupStep(2, "Conectando ao provedor…")
            connectXtream(input)
            // The home is built from the catalogue, so loading it here means the first screen is
            // complete when the splash clears rather than filling in afterwards.
            if (xtreamStatus !is XtreamStatus.Error) {
                startupStep(3, "Carregando filmes e séries…")
                loadDailyHome(LocalDate.now())
                startupStep(5, "Pronto")
            }
        } finally {
            // In a finally so no path - no saved session, a provider that refuses, an exception -
            // can leave the app stuck behind its own splash screen.
            isStarting = false
        }
    }


    /**
     * Re-fetches every loaded list from the provider.
     *
     * The catalogue is normally fetched once on connect and then answered from memory, so a title
     * added by the provider during the session never appeared. This is the manual override; the
     * automatic fetch on start still covers the common case.
     */
    suspend fun refreshCatalog() {
        if (xtreamStatus is XtreamStatus.LoadingCatalog) return
        val loaded = xtreamSummary?.loadedContentTypes.orEmpty().ifEmpty { setOf(xtreamContentType) }
        val current = xtreamContentType
        xtreamStatus = XtreamStatus.LoadingCatalog(current)

        runCatching {
            withContext(Dispatchers.IO) {
                // Dropping the cache first is what makes this a refresh rather than a no-op: with
                // the entries still present, loadCatalog would answer from them.
                xtreamRepository.clearCatalogCache()
                var latest: XtreamSessionSummary? = null
                // The type on screen goes first so the visible grid is repopulated soonest.
                for (contentType in listOf(current) + (loaded - current)) {
                    latest = xtreamRepository.loadCatalog(contentType)
                }
                requireNotNull(latest)
            }
        }.onSuccess { summary ->
            xtreamSummary = summary
            xtreamCategories = visibleXtreamCategories(current)
            refreshXtreamPage(pageIndex = 0)
            xtreamStatus = XtreamStatus.Connected
            // The Home is built from its own snapshot, so refreshing only the catalogue left the
            // screen the user was looking at unchanged — the button appeared to do nothing.
            // Cleared to Idle rather than reloaded here: the Home's own effect is keyed on the
            // summary and reloads it, and calling in from two places raced to leave the status on
            // Loading with nothing left to move it off.
            if (destination == DesktopDestination.HOME) {
                dailyHomeStatus = DailyHomeStatus.Idle
                dailyHomeRevision += 1
            }
        }.onFailure { error ->
            error.rethrowIfCancellation()
            xtreamStatus = XtreamStatus.Error(error.toSafeXtreamMessage())
        }
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
            selectedXtreamMinimumRating = null
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

    suspend fun selectXtreamMinimumRating(rating: Double?) {
        selectedXtreamMinimumRating = rating
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
        dailySelectedItem = null
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

    fun resumeDecision(target: XtreamPlaybackTarget): ResumeDecision =
        playbackProgressCoordinator.resumeDecision(playbackIdentity(target))

    fun prepareXtreamPlayback(
        target: XtreamPlaybackTarget,
        title: String,
        startPositionMillis: Long = 0L,
    ): DesktopPlaybackRequest? =
        runCatching {
            DesktopPlaybackRequest(
                title = title.take(180),
                uri = xtreamRepository.buildConfirmedPlaybackUri(target),
                progressIdentity = playbackIdentity(target),
                startPositionMillis = startPositionMillis.coerceAtLeast(0L),
            )
        }.getOrNull()

    // ---------------------------------------------------------------------------------------
    // Downloads
    // ---------------------------------------------------------------------------------------

    var downloads by mutableStateOf<Map<String, DownloadState>>(emptyMap())
        private set

    /**
     * Title and poster kept alongside state so the library can present a download without the
     * catalogue, including after a restart when nothing is loaded yet.
     */
    private var downloadMetadata by mutableStateOf<Map<String, StoredDownload>>(emptyMap())

    /** Ordered view for the sidebar: running first, so active work is never scrolled out of sight. */
    val downloadEntries: List<DownloadEntry>
        get() =
            downloads.entries
                .map { (key, state) ->
                    val metadata = downloadMetadata[key]
                    DownloadEntry(
                        contentKey = key,
                        title = metadata?.title ?: key.toReadableTitle(),
                        artworkUrl = metadata?.artworkUrl,
                        state = state,
                    )
                }.sortedBy { entry ->
                    when (entry.state) {
                        is DownloadState.Running -> 0
                        DownloadState.Failed -> 1
                        DownloadState.Idle -> 2
                        DownloadState.Completed -> 3
                    }
                }

    fun isDownloadable(target: XtreamPlaybackTarget): Boolean = downloadManager.isDownloadable(target)

    fun downloadState(contentKey: String): DownloadState =
        downloads[contentKey]
            ?: if (downloadManager.isDownloaded(contentKey)) DownloadState.Completed else DownloadState.Idle

    fun cancelDownload(contentKey: String) {
        downloadManager.cancel(contentKey)
    }

    /**
     * Playback request for a stored copy.
     *
     * The whole point of downloading is watching without the provider, so this resolves the local
     * file and never touches the network. Returns null when the file has gone — a user can delete
     * it from Explorer, and offering playback for something that is not there would fail with a
     * confusing player error instead of an honest empty state.
     */
    fun prepareOfflinePlayback(contentKey: String): DesktopPlaybackRequest? {
        val file = downloadManager.downloadedFile(contentKey) ?: return null
        return DesktopPlaybackRequest(
            title = (downloadMetadata[contentKey]?.title ?: contentKey.toReadableTitle()).take(180),
            uri = file.toUri(),
            progressIdentity = null,
            startPositionMillis = 0L,
        )
    }

    /**
     * Reconciles in-memory state with what is actually on disk.
     *
     * A copy finished in an earlier session leaves no trace in [downloads], so without this the
     * list would claim nothing had ever been downloaded.
     */
    fun refreshDownloadStates() {
        val stored = downloadManager.storedDownloads()
        if (stored.isEmpty() && downloads.isEmpty()) return
        // Disk wins for keys we know nothing about; in-memory wins for the rest, since a running
        // download already has the live title and the sidecar is only written on completion.
        downloadMetadata = stored + downloadMetadata
        // Anything on disk is complete, including a key still marked Running in memory: the file
        // being there is proof the transfer finished. Skipping those left the finished download
        // stuck at 100% with a Cancel button while its own copy appeared as a second row.
        downloads = downloads + stored.keys.associateWith { DownloadState.Completed }
    }

    fun deleteDownload(contentKey: String) {
        downloadManager.delete(contentKey)
        downloads = downloads - contentKey
    }

    /**
     * Starts an offline copy of a VOD target.
     *
     * The signed URL is resolved here and handed straight to the downloader; it is never stored in
     * state, so it cannot leak into a recomposition dump or a crash report.
     */
    suspend fun startDownload(
        target: XtreamPlaybackTarget,
        title: String,
        artworkUrl: String? = null,
    ) {
        if (!downloadManager.isDownloadable(target)) return
        val contentKey = target.contentKey
        if (downloads[contentKey] is DownloadState.Running) return

        val uri = runCatching { xtreamRepository.buildConfirmedPlaybackUri(target) }.getOrNull()
        if (uri == null) {
            downloads = downloads + (contentKey to DownloadState.Failed)
            return
        }
        val metadata = StoredDownload(title = title, artworkUrl = artworkUrl)
        downloadMetadata = downloadMetadata + (contentKey to metadata)
        downloads = downloads + (contentKey to DownloadState.Running(0f))

        // An episode carries its own container. Falling back to mp4 for it wrote an mkv under the
        // wrong extension, which the player then refused to open from the library.
        val containerExtension =
            when (target) {
                is XtreamPlaybackTarget.CatalogItem -> target.containerExtension
                is XtreamPlaybackTarget.Episode -> target.episode.containerExtension
            }
        val result =
            downloadManager.download(
                contentKey = contentKey,
                displayName = title,
                uri = uri,
                containerExtension = containerExtension,
            ) { read, total ->
                val fraction = if (total != null && total > 0L) read.toFloat() / total else -1f
                downloads = downloads + (contentKey to DownloadState.Running(fraction))
            }
        // A refused duplicate must not touch the state: the download already in flight owns this
        // key, and overwriting its Running progress with Failed made the first copy look broken
        // while it was still downloading perfectly well.
        if (result is DownloadResult.Failed && result.reason == FailureReason.ALREADY_RUNNING) return
        downloads =
            downloads + (
                contentKey to
                    when (result) {
                        is DownloadResult.Completed -> DownloadState.Completed
                        DownloadResult.Cancelled -> DownloadState.Idle
                        is DownloadResult.Failed -> DownloadState.Failed
                    }
            )
        // Only a finished copy earns a sidecar. Writing it earlier would leave a library entry
        // pointing at a file that a cancellation then deleted.
        if (result is DownloadResult.Completed) {
            downloadManager.remember(contentKey, metadata.title, metadata.artworkUrl)
            // Reconciled with disk right away. Without this the finished download stayed on screen
            // as a running one at 100% with a Cancel button, while the copy recovered from disk
            // appeared as a second row - the same episode listed twice, neither playable.
            refreshDownloadStates()
        }
    }

    fun checkpointPlayback(request: DesktopPlaybackRequest, positionMs: Long, durationMs: Long) {
        playbackProgressCoordinator.checkpoint(request.progressIdentity, positionMs, durationMs)
    }

    fun completePlayback(request: DesktopPlaybackRequest, durationMs: Long) {
        playbackProgressCoordinator.ended(request.progressIdentity, durationMs)
    }

    private fun playbackIdentity(target: XtreamPlaybackTarget): PlaybackProgressIdentity? {
        val profileId = activeProfileId ?: return null
        // Deliberately not the per-source hash. sourceId used to be SHA-256(server + username), so
        // a new playlist produced a new hash and every progress row was orphaned on disk. Progress
        // belongs to the user's library, not to whichever list is currently connected.
        return when (target) {
            is XtreamPlaybackTarget.CatalogItem -> {
                if (target.contentType != XtreamContentType.MOVIE) return null
                PlaybackProgressIdentity(
                    profileId = profileId,
                    sourceId = LIBRARY_SCOPE,
                    contentId = target.contentKey,
                    contentType = PlaybackContentType.MOVIE,
                )
            }
            is XtreamPlaybackTarget.Episode -> PlaybackProgressIdentity(
                profileId = profileId,
                sourceId = LIBRARY_SCOPE,
                contentId = target.contentKey,
                contentType = PlaybackContentType.EPISODE,
                seriesId = target.seriesId,
                seasonNumber = target.episode.seasonNumber,
                episodeNumber = target.episode.episodeNumber,
            )
        }
    }

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

    /**
     * Opens a person's filmography.
     *
     * [movieAppearances] only holds films whose details the user already opened, so on a fresh
     * session it is almost always empty and the page appeared blank. Anything missing is looked up
     * across the catalogue instead, which is what the user expects from tapping a name.
     */
    suspend fun openPerson(name: String) {
        val cleanName = name.trim().take(100)
        if (cleanName.isBlank()) return
        val key = cleanName.lowercase(Locale.ROOT)
        val known = movieAppearances[key]?.values?.toList().orEmpty()

        // Show what is already known immediately; a catalogue sweep takes a moment and a blank
        // page in the meantime is worse than a partial one.
        selectedPerson = PersonFilmography(name = cleanName, items = known, isLoading = true)

        val discovered =
            withContext(Dispatchers.Default) {
                xtreamRepository.findByCastMember(cleanName, MAX_FILMOGRAPHY_ITEMS)
            }
        // The user may have navigated away while the sweep ran.
        if (selectedPerson?.name != cleanName) return
        val merged = (known + discovered).distinctBy { it.providerId }
        selectedPerson = PersonFilmography(name = cleanName, items = merged, isLoading = false)

        // The photo and the full filmography can only come from outside: the provider sends the
        // cast as a bare list of names. Fetched after the local sweep so the page is never blank
        // while waiting on a network call that may not be configured at all.
        if (!metadataClient.isConfigured) return
        val enriched =
            withContext(Dispatchers.IO) {
                val person = metadataClient.findPerson(cleanName) ?: return@withContext null
                Triple(
                    person.profileImageUrl,
                    metadataClient.personDetails(person.id)?.biography,
                    metadataClient.filmography(person.id, MAX_FILMOGRAPHY_ITEMS),
                )
            } ?: return
        if (selectedPerson?.name != cleanName) return
        selectedPerson =
            selectedPerson?.copy(
                photoUrl = enriched.first,
                biography = enriched.second,
                credits =
                    enriched.third.map { credit ->
                        PersonCredit(
                            title = credit.title,
                            year = credit.year,
                            posterUrl = credit.posterUrl,
                            character = credit.character,
                        )
                    },
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
        dailyHomeStatus = DailyHomeStatus.Idle
        dailySelectedItem = null
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
        val minimumRating = selectedXtreamMinimumRating
        val allowedIdentities = if (favoritesOnly) favoriteIdentities() else null
        val page =
            withContext(Dispatchers.Default) {
                xtreamRepository.page(
                    contentType = type,
                    categoryId = category,
                    query = query,
                    requestedPage = pageIndex,
                    releaseYear = releaseYear,
                    minimumRating = minimumRating,
                    allowedIdentities = allowedIdentities,
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
        dailyHomeStatus = DailyHomeStatus.Idle
        dailySelectedItem = null
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

        /** Enough to fill a filmography page without a long sweep. */
        const val MAX_FILMOGRAPHY_ITEMS = 24

        /**
         * Scope for stored watch progress.
         *
         * A fixed value, because progress is scoped to the user library rather than to the
         * playlist that happened to be connected when they watched something.
         */
        const val LIBRARY_SCOPE = "buro-library"
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
    /** True while the catalogue is still being searched, so the page can say so. */
    val isLoading: Boolean = false,
    /** Photo from the metadata service, absent when it is not configured or found nobody. */
    val photoUrl: String? = null,
    val biography: String? = null,
    /**
     * Everything the person is credited in, beyond what this playlist happens to carry.
     *
     * The provider only knows the titles in its own catalogue, which is why clicking an actor used
     * to show the single film you had just come from.
     */
    val credits: List<PersonCredit> = emptyList(),
)

/** One entry of a person's filmography, from the metadata service rather than the playlist. */
data class PersonCredit(
    val title: String,
    val year: Int?,
    val posterUrl: String?,
    val character: String?,
)

data class DesktopContinueWatchingEntry(
    val item: XtreamCatalogItem,
    val progress: PlaybackProgress,
) {
    /**
     * The target to hand playback.
     *
     * An episode resolves to its series item, so resuming one opens the series rather than the
     * exact episode: the provider id recorded in progress belongs to the episode, which the
     * catalogue cannot look up on its own.
     */
    fun playbackTarget(): XtreamPlaybackTarget =
        XtreamPlaybackTarget.CatalogItem(
            providerId = item.providerId,
            contentType = item.contentType,
            containerExtension = item.containerExtension,
            contentKey = item.contentIdentity().key,
        )
}

enum class DesktopDestination { HOME, CATALOG, FAVORITES, DOWNLOADS, CONTINUE }

data class DailyHomeSnapshot(
    val sourceId: String,
    val date: LocalDate,
    val hero: XtreamCatalogItem?,
    val movies: List<XtreamCatalogItem>,
    val series: List<XtreamCatalogItem>,
    val live: List<XtreamCatalogItem>,
    /** Null outside the seasonal windows, and also when this catalogue matched nothing. */
    val seasonal: DailySeasonalShelf? = null,
)

/**
 * A themed rail built from titles the user's own catalogue already contains.
 *
 * The collection travels rather than a finished heading so the rail can be retitled when the user
 * switches language, without paging the catalogue again.
 */
data class DailySeasonalShelf(
    val collection: SeasonalCollection,
    val items: List<XtreamCatalogItem>,
)

/** Titles per rail. Matches the eighteen the daily rails carry, so the rows read as one family. */
private const val SEASONAL_SHELF_SIZE = 18

/**
 * Titles requested per search term.
 *
 * Deliberately small: a dozen terms sweep the catalogue a dozen times, and only enough matches to
 * fill the rail are ever shown.
 */
private const val SEASONAL_TERM_PAGE_SIZE = 12

sealed interface DailyHomeStatus {
    data object Idle : DailyHomeStatus

    data object Loading : DailyHomeStatus

    data class Loaded(val snapshot: DailyHomeSnapshot) : DailyHomeStatus

    data class Error(val message: String) : DailyHomeStatus
}

internal fun rotatingPageIndex(seed: Int, pageCount: Int): Int =
    Math.floorMod(seed, pageCount.coerceAtLeast(1))

internal fun editorialCatalogKey(title: String): String =
    title
        .lowercase(Locale.ROOT)
        .replace(Regex("\\[[^]]{1,12}]"), " ")
        .replace(Regex("\\b(4k|uhd|fhd|hd|sd|h\\.?265|hevc|multi|dual)\\b"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

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

/** UI-facing state of an offline copy. */
sealed interface DownloadState {
    data object Idle : DownloadState

    /** [fraction] is negative when the server did not report a content length. */
    data class Running(val fraction: Float) : DownloadState

    data object Completed : DownloadState

    data object Failed : DownloadState
}

/** The ordered steps a first-time user passes through before reaching the catalogue. */
sealed interface OnboardingStep {
    data object Language : OnboardingStep

    /** Copyright notice. The app plays what the user's own provider serves; it hosts nothing. */
    data object Terms : OnboardingStep

    /** Name, avatar and playlist, entered together. */
    data object Account : OnboardingStep

    data object Connecting : OnboardingStep

    /** [message] is the provider's reason, already stripped of host and credentials. */
    data class Failed(val message: String?) : OnboardingStep

    data object Done : OnboardingStep
}

/** A download as the sidebar needs it: identity, a human name, and current state. */
data class DownloadEntry(
    val contentKey: String,
    val title: String,
    val artworkUrl: String?,
    val state: DownloadState,
)
