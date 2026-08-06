package com.lucasserafin94.iptvburo.desktop

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lucasserafin94.iptvburo.desktop.data.InMemoryCatalogRepository
import com.lucasserafin94.iptvburo.desktop.data.MusicLibraryLoader
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
import com.lucasserafin94.iptvburo.desktop.user.ListeningHistoryStore
import com.lucasserafin94.iptvburo.desktop.user.MusicPlayCountStore
import com.lucasserafin94.iptvburo.desktop.user.MusicPlaylistStore
import com.lucasserafin94.iptvburo.desktop.user.ProfilePhotoStore
import com.lucasserafin94.iptvburo.metadata.TmdbClient
import com.lucasserafin94.iptvburo.desktop.build.BUNDLED_TMDB_KEY
import com.lucasserafin94.iptvburo.desktop.download.DesktopDownloadManager
import com.lucasserafin94.iptvburo.desktop.download.DownloadRateTracker
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
import com.lucasserafin94.iptvburo.domain.model.ListeningHistoryEntry
import com.lucasserafin94.iptvburo.domain.model.ListeningKind
import com.lucasserafin94.iptvburo.domain.model.MusicLibrary
import com.lucasserafin94.iptvburo.domain.model.MusicPlaylist
import com.lucasserafin94.iptvburo.domain.model.MusicPlaylistExportResult
import com.lucasserafin94.iptvburo.domain.model.MusicPlaylistExportWarning
import com.lucasserafin94.iptvburo.domain.model.MusicPlaylistExporter
import com.lucasserafin94.iptvburo.domain.model.MusicPlaylistKind
import com.lucasserafin94.iptvburo.domain.model.MusicTrack
import com.lucasserafin94.iptvburo.domain.model.SmartPlaylistRule
import com.lucasserafin94.iptvburo.domain.model.SmartPlaylists
import com.lucasserafin94.iptvburo.playlist.MusicPlaylistMapper
import com.lucasserafin94.iptvburo.domain.model.PlaybackQueue
import com.lucasserafin94.iptvburo.domain.model.QueueEntry
import com.lucasserafin94.iptvburo.domain.model.QueueMediaKind
import com.lucasserafin94.iptvburo.domain.model.PlaybackContentType
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgressIdentity
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgress
import com.lucasserafin94.iptvburo.domain.model.ResumeDecision
import com.lucasserafin94.iptvburo.domain.model.BestOfferPolicy
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleDetails
import com.lucasserafin94.iptvburo.domain.model.HeroCandidate
import com.lucasserafin94.iptvburo.domain.model.HeroSelection
import com.lucasserafin94.iptvburo.domain.model.LibraryCandidate
import com.lucasserafin94.iptvburo.domain.model.LibraryOfferPolicy
import com.lucasserafin94.iptvburo.domain.model.asExternalCandidate
import com.lucasserafin94.iptvburo.domain.model.asLibraryCandidate
import com.lucasserafin94.iptvburo.domain.model.OfferRanking
import com.lucasserafin94.iptvburo.domain.model.SeasonalCollection
import com.lucasserafin94.iptvburo.domain.model.SeasonalCollections
import com.lucasserafin94.iptvburo.domain.model.StreamingDiscoveryCapability
import com.lucasserafin94.iptvburo.domain.model.StreamingDiscoveryProvider
import com.lucasserafin94.iptvburo.domain.model.UserStreamingPreference
import com.lucasserafin94.iptvburo.desktop.data.TmdbServiceShelf
import com.lucasserafin94.iptvburo.metadata.TmdbDiscoverKind
import com.lucasserafin94.iptvburo.desktop.data.TmdbStreamingCatalogue
import com.lucasserafin94.iptvburo.domain.model.ExternalTitle
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
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.time.LocalDate
import java.util.Arrays
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

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
    private val musicLoader: MusicLibraryLoader = MusicLibraryLoader(),
    private val playCountStore: MusicPlayCountStore = MusicPlayCountStore(),
    /**
     * Listening history, which owns the play count that [playCountStore] used to hold.
     *
     * The legacy store is still injected because the history store migrates from it on first read;
     * nothing writes counts through it any more. See [ListeningHistoryStore].
     */
    private val listeningHistoryStore: ListeningHistoryStore = ListeningHistoryStore(legacyCounts = playCountStore),
    private val musicPlaylistStore: MusicPlaylistStore = MusicPlaylistStore(),
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
        val effectiveKey = value.takeIf(String::isNotBlank) ?: BUNDLED_TMDB_KEY.ifBlank { null }
        metadataClient = TmdbClient(effectiveKey)
        // Assinaturas has its own client and would otherwise keep using the previous key: pasting a
        // personal key fixed cast photos while leaving the shelves on the old one, which is exactly
        // the kind of half-applied setting a user cannot diagnose.
        streamingCatalogue = buildStreamingCatalogue(effectiveKey, streamingRegion)
        shelfCache.clear()
        streamingShelves = emptyList()
        loadStreamingShelves(force = true)
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

    /**
     * How the catalogue is laid out, remembered per profile across restarts.
     *
     * Restored from the store rather than defaulted, so the choice survives closing the app - the
     * point of offering it at all.
     */
    var catalogLayout by mutableStateOf(
        CatalogLayout.fromId(userStore.catalogLayout(initialUserSnapshot.activeProfileId)),
    )
        private set

    fun selectCatalogLayout(layout: CatalogLayout) {
        catalogLayout = layout
        activeProfileId?.let { profileId -> userStore.setCatalogLayout(profileId, layout.id) }
    }

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
        catalogLayout = CatalogLayout.fromId(userStore.catalogLayout(activeProfileId))
        xtreamCategories = visibleXtreamCategories(xtreamContentType)
        if (selectedXtreamCategoryId !in xtreamCategories.map(XtreamCategory::providerId)) {
            selectedXtreamCategoryId = null
        }
    }

    suspend fun selectProfileAndRefresh(id: String?) {
        selectProfile(id)
        // The music playlist belongs to the profile, so switching who is watching switches the
        // library along with the favourites.
        loadMusicLibrary()
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
        /** Optional music M3U. Null is the ordinary case and leaves the app unchanged. */
        musicPlaylistPath: Path? = null,
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
                    musicPlaylistPath = musicPlaylistPath?.toString(),
                )
            profiles = listOf(profile) + profiles.filterNot { it.name == profile.name }
            userStore.saveProfiles(profiles)
            attachPendingPhoto(profile.id)
            selectProfile(profile.id)
            loadMusicLibrary()
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
        /** Optional music M3U, chosen per profile even when the video playlist is shared. */
        musicPlaylistPath: Path? = null,
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
                musicPlaylistPath = musicPlaylistPath?.toString(),
            )
        profiles = profiles + profile
        userStore.saveProfiles(profiles)
        attachPendingPhoto(profile.id)
        selectProfile(profile.id)
        loadMusicLibrary()
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

    // ---------------------------------------------------------------------------------------
    // Music
    // ---------------------------------------------------------------------------------------

    /**
     * The active profile's music library, or [MusicLibrary.EMPTY] when none is configured.
     *
     * Empty is the ordinary case. The music playlist is optional, and a user who never supplies one
     * must see no trace of the feature — which is what [hasMusicLibrary] gates.
     */
    var musicLibrary by mutableStateOf(MusicLibrary.EMPTY)
        private set

    var musicSection by mutableStateOf(MusicSection.HOME)
        private set

    /** Play counts for the active profile, used to rank the "most played" shelf. */
    var musicPlayCounts by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    /**
     * The active profile's listening history, keyed by track id — GDD 8 section 18.
     *
     * Held in state because the smart playlists are evaluated from it: "never played" and
     * "recently played" are both functions of this map, so the shelves recompute when it changes.
     */
    var listeningHistory by mutableStateOf<Map<String, ListeningHistoryEntry>>(emptyMap())
        private set

    /** The profile's own playlists — manual, imported and saved queues. */
    var musicPlaylists by mutableStateOf<List<MusicPlaylist>>(emptyList())
        private set

    /** Which playlist is open, or null while the playlist index is showing. */
    var selectedMusicPlaylistId by mutableStateOf<String?>(null)
        private set

    /**
     * The pending export awaiting the user's answer to the sensitive-URL warning.
     *
     * GDD 8 section 17 permits export only with authorisation and a warning about sensitive URLs.
     * Holding the request here means the file is written only after the dialog is confirmed — the
     * warning cannot be bypassed by a caller that forgets to raise it.
     */
    var pendingMusicExport by mutableStateOf<PendingMusicExport?>(null)
        private set

    /** Which artist's tracks are open, or null while the artist grid is showing. */
    var selectedMusicArtist by mutableStateOf<String?>(null)
        private set

    /**
     * Whether this profile has any music at all.
     *
     * Drives the sidebar entry. A configured playlist that turned out to be empty or unreadable
     * counts as no music: an entry leading to a blank section is worse than no entry.
     */
    val hasMusicLibrary: Boolean
        get() = !musicLibrary.isEmpty

    /**
     * Loads the active profile's music playlist from disk.
     *
     * Called whenever the profile changes, because the playlist belongs to the profile. Failure is
     * silent by design: the library is simply left empty and the section stays hidden.
     */
    suspend fun loadMusicLibrary() {
        // The queue holds identities from the library being replaced, and GDD 8 §16 scopes it to one
        // profile. Carrying it across would leave rows that either resolve to nothing or, worse,
        // resolve to a different track that happens to reuse the id.
        playbackQueue = PlaybackQueue.EMPTY
        val path = activeProfile?.musicPlaylistPath
        if (path.isNullOrBlank()) {
            musicLibrary = MusicLibrary.EMPTY
            return
        }
        val loaded =
            withContext(Dispatchers.IO) {
                runCatching { musicLoader.load(Path.of(path)) }.getOrNull()
            }
        musicLibrary = loaded ?: MusicLibrary.EMPTY
        reloadMusicUserData()
    }

    /**
     * Reloads the per-profile music data that lives beside the library: history and playlists.
     *
     * Counts come from the history store rather than [playCountStore]; the history is now the only
     * writer of that fact, and reading it from two places is how they would drift apart.
     */
    private fun reloadMusicUserData() {
        listeningHistory = listeningHistoryStore.historyFor(activeProfileId)
        musicPlayCounts = listeningHistoryStore.playCountsFor(activeProfileId)
        musicPlaylists = musicPlaylistStore.playlistsFor(activeProfileId)
        selectedMusicPlaylistId = null
    }

    /**
     * Attaches a music playlist to the active profile.
     *
     * Adding one had to be done when creating a profile, which meant an existing user could not add
     * music at all without making a second profile.
     */
    suspend fun attachMusicPlaylist(path: Path) {
        val profileId = activeProfileId ?: return
        profiles =
            profiles.map { profile ->
                if (profile.id == profileId) {
                    profile.copy(musicPlaylistPath = path.toString())
                } else {
                    profile
                }
            }
        userStore.saveProfiles(profiles)
        loadMusicLibrary()
    }

    /**
     * Whether the Assinaturas area may be shown, and in what state.
     *
     * AVAILABLE once a metadata key is configured, since that is what lets TMDb answer "what is on
     * this service" for real. Without a key there is no catalogue, real or otherwise, and the entry
     * disappears rather than opening onto nothing.
     *
     * The DEMO_ONLY state remains reachable through [demoStreamingProvider], but nothing sets it in
     * a shipping build any more — it exists so the screen can be exercised without a key.
     */
    val streamingDiscoveryCapability: StreamingDiscoveryCapability
        get() =
            StreamingDiscoveryCapability.of(
                hasRealProvider = streamingCatalogue != null,
                hasFixtureProvider = demoStreamingProvider != null,
            )

    /**
     * The region whose services and availability are shown.
     *
     * Per profile, because a household can span countries and the catalogues genuinely differ.
     * Defaults to Brazil rather than being guessed from the machine's locale: pt-BR and pt-PT are
     * different catalogues, and someone reading Italian may well be in Switzerland.
     */
    var streamingRegion by mutableStateOf(
        userStore.streamingPreference(initialUserSnapshot.activeProfileId).region
            ?: TmdbStreamingCatalogue.DEFAULT_REGION,
    )
        private set

    /**
     * Where shelf loading runs.
     *
     * Its own scope, declared *above* the functions that use it. `downloadScope` is defined near the
     * bottom of this class, and a property is null until its own initialiser runs — so launching on
     * it from here left the shelves silently empty, the failure swallowed by the surrounding
     * runCatching. Same reason it is not a composable's scope: leaving the screen mid-load must not
     * cancel the work and strand the section empty.
     */
    private val streamingScope =
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + Dispatchers.Default,
        )

    /** Measures how fast each download is going, so the row can say more than a percentage. */
    private val rateTracker = DownloadRateTracker()

    /**
     * The real catalogue, or null when no metadata key is configured.
     *
     * Rebuilt whenever the key or the region changes, since both are baked into every request it
     * makes.
     */
    private var streamingCatalogue: TmdbStreamingCatalogue? =
        buildStreamingCatalogue(userStore.metadataApiKey() ?: BUNDLED_TMDB_KEY.ifBlank { null }, streamingRegion)

    private fun buildStreamingCatalogue(
        key: String?,
        region: String,
    ): TmdbStreamingCatalogue? =
        key?.takeIf(String::isNotBlank)?.let { configured ->
            TmdbStreamingCatalogue(client = TmdbClient(configured), region = region)
        }

    /**
     * The demo catalogue, kept only for builds with no key at all.
     *
     * Null in the ordinary case. When it is not null the capability becomes DEMO_ONLY, which forces
     * the DEMO badge onto every row — there is no state where invented listings appear unlabelled.
     */
    private val demoStreamingProvider: StreamingDiscoveryProvider? = null

    /**
     * The offers to show, ranked. Empty until a title is opened.
     *
     * Ranked through [BestOfferPolicy] with the profile's own stated services, so the ordering the
     * user sees is the ordering the policy decided — the screen never re-sorts.
     */
    var streamingOffers by mutableStateOf(OfferRanking.EMPTY)
        private set

    /** The service shelves, once loaded. Empty while loading and when nothing is available. */
    var streamingShelves by mutableStateOf<List<TmdbServiceShelf>>(emptyList())
        private set

    /** Which kind of title the shelves are showing. */
    var streamingKind by mutableStateOf(TmdbDiscoverKind.MOVIES)
        private set

    /**
     * Shelves already fetched, by kind, so switching back and forth is instant.
     *
     * Each kind is a full round of requests — one per service — and re-fetching on every tap of a
     * filter would make the buttons feel broken on a slow connection.
     */
    private val shelfCache = mutableMapOf<TmdbDiscoverKind, List<TmdbServiceShelf>>()

    /** Switches the shelves between films, series and upcoming releases. */
    fun selectStreamingKind(kind: TmdbDiscoverKind) {
        if (kind == streamingKind) return
        streamingKind = kind
        // Shown at once if already fetched; otherwise the shelves empty and the loader fills them,
        // which reads as a load rather than as the previous kind lingering under a new label.
        streamingShelves = shelfCache[kind].orEmpty()
        loadStreamingShelves()
    }

    var streamingLoading by mutableStateOf(false)
        private set

    /**
     * Loads the shelves for the current region.
     *
     * Runs on [downloadScope] rather than a composable's own scope: leaving the screen mid-load
     * used to cancel the work and leave the section permanently empty, which is the bug that cost a
     * day on downloads. Skips when shelves are already loaded, so revisiting the section is
     * instant and does not re-hit TMDb.
     */
    fun loadStreamingShelves(force: Boolean = false) {
        // Each of these returns used to be silent, so "nothing happened" and "nothing to do" looked
        // identical from outside — which is how an empty section survived two builds.
        val catalogue = streamingCatalogue
        if (catalogue == null) {
            println("[streaming] no catalogue: metadata key missing or blank")
            return
        }
        if (streamingLoading) {
            println("[streaming] already loading, skipping")
            return
        }
        val kind = streamingKind
        if (!force && shelfCache[kind]?.isNotEmpty() == true) {
            println("[streaming] $kind already cached, skipping")
            return
        }
        println("[streaming] loading $kind shelves for region $streamingRegion")

        streamingLoading = true
        streamingScope.launch {
            val loaded =
                runCatching { catalogue.shelves(kind) }
                    .onFailure { error ->
                        // Printed rather than swallowed. An empty section and a crashed load look
                        // identical on screen, and this one hid a null scope for a whole build.
                        // The message carries no URL or key — only the failure type.
                        println("[streaming] shelf load failed: ${error::class.simpleName}: ${error.message}")
                    }.getOrDefault(emptyList())
            println("[streaming] loaded ${loaded.size} $kind shelves")
            shelfCache[kind] = loaded
            // Only if the user has not switched filters while this was in flight — otherwise a slow
            // request would overwrite the shelves they are now looking at.
            if (streamingKind != kind) return@launch
            // Assigned directly, not through withContext(Dispatchers.Main). Compose Desktop has no
            // Main dispatcher unless kotlinx-coroutines-swing is on the classpath, so that call
            // never ran its body: the shelves loaded, the log said so, and the screen stayed empty
            // because the state was never actually set. Snapshot state is safe to write from any
            // thread; every other loader in this class does the same.
            run {
                streamingShelves = loaded
                streamingLoading = false
            }
        }
    }

    /**
     * Switches region, which changes both the services shown and their availability.
     *
     * Named `changeStreamingRegion` rather than `setStreamingRegion` because the latter collides on
     * the JVM with the generated setter for [streamingRegion].
     */
    fun changeStreamingRegion(region: String) {
        val clean = region.trim().uppercase().takeIf(String::isNotBlank) ?: return
        if (clean == streamingRegion) return

        streamingRegion = clean
        activeProfileId?.let { profileId ->
            val stored = userStore.streamingPreference(profileId)
            userStore.setStreamingPreference(profileId, stored.copy(region = clean))
        }
        streamingCatalogue =
            buildStreamingCatalogue(userStore.metadataApiKey() ?: BUNDLED_TMDB_KEY.ifBlank { null }, clean)
        // The old region's shelves are wrong now, so they go rather than lingering under a new
        // label — including the cached ones for the filters the user is not currently looking at.
        shelfCache.clear()
        streamingShelves = emptyList()
        streamingOffers = OfferRanking.EMPTY
        loadStreamingShelves(force = true)
    }

    /**
     * Opens the Assinaturas area and ranks the offers for [title].
     *
     * The user's own catalogue is searched first, so "you already have this" can outrank every paid
     * option — the one answer no comparison site can give. Only a confident match produces that
     * row; see [LibraryOfferPolicy].
     */
    fun showStreamingOffers(title: ExternalTitleDetails) {
        val withLibrary =
            LibraryOfferPolicy.withLibraryOffer(
                offers = title.offers,
                external = title.title.asExternalCandidate(),
                library = libraryMatchCandidates(),
            )
        streamingOffers = BestOfferPolicy.rank(withLibrary, streamingPreference)
        selectedStreamingTitle = title
        destination = DesktopDestination.SUBSCRIPTIONS
    }

    /**
     * Opens a title picked off a service's shelf, fetching where it can be watched.
     *
     * The shelf only knows the film; the availability is a second call. The user's own library is
     * consulted locally and shown immediately, so "you already have this" never waits on the
     * network — and still stands if TMDb says nothing.
     */
    fun openStreamingTitle(title: ExternalTitle) {
        val catalogue = streamingCatalogue ?: return
        val candidate = title.asExternalCandidate()

        // Shown at once from what is already known locally, then replaced when the network answers.
        val localOnly = LibraryOfferPolicy.withLibraryOffer(emptyList(), candidate, libraryMatchCandidates())
        selectedStreamingTitle = ExternalTitleDetails(title = title, offers = localOnly)
        streamingOffers = BestOfferPolicy.rank(localOnly, streamingPreference)
        destination = DesktopDestination.SUBSCRIPTIONS
        streamingLoading = true

        streamingScope.launch {
            val details = runCatching { catalogue.detailsFor(title) }.getOrNull()
            val offers =
                LibraryOfferPolicy.withLibraryOffer(
                    offers = details?.offers.orEmpty(),
                    external = candidate,
                    library = libraryMatchCandidates(),
                )
            // Direct, for the same reason as the shelves above: there is no Main dispatcher here.
            selectedStreamingTitle = ExternalTitleDetails(title = title, offers = offers)
            streamingOffers = BestOfferPolicy.rank(offers, streamingPreference)
            streamingLoading = false
        }
    }

    /** The title whose offers are on screen, so the local copy can be resolved when pressed. */
    var selectedStreamingTitle by mutableStateOf<ExternalTitleDetails?>(null)
        private set

    /**
     * Opens the user's own copy of the title currently on screen, on its details page.
     *
     * Returns false when there is no confident match — the same bar the "in your list" row is shown
     * behind, so a row that appears always leads somewhere.
     */
    fun openInLibrary(): Boolean {
        val title = selectedStreamingTitle ?: return false
        val found =
            LibraryOfferPolicy.findInLibrary(
                external = title.title.asExternalCandidate(),
                library = libraryMatchCandidates(),
            ) ?: return false
        // "MOVIE:1234" — the content type is carried because provider ids are numbered per
        // catalogue and a bare one cannot say which list it came from.
        val contentType =
            runCatching { XtreamContentType.valueOf(found.localContentId.substringBefore(':')) }.getOrNull()
                ?: return false
        val providerId = found.localContentId.substringAfter(':').takeIf(String::isNotBlank) ?: return false
        val item = xtreamRepository.itemByProviderId(contentType, providerId) ?: return false

        // Opened, not played. Starting the film the instant the row is pressed skips the synopsis,
        // the cast and the episode list — everything the user opened the title to read. They press
        // play from that page when they have decided, which is one more click and a much better
        // answer to "is this the film I meant?".
        //
        // This applies to films as much as to series: a series has no single stream to start, and a
        // film has a page worth seeing first.
        // selectDailyItem, not selectedXtreamItemId alone. selectedXtreamItem resolves against
        // xtreamPage — one page of eighty — so a title from anywhere else in a 40,000-item
        // catalogue was not found there and fell through to `firstOrNull()`, opening whatever
        // happened to be first on the page. This is the same route the home screen's rails use for
        // exactly that reason.
        selectDailyItem(item)
        xtreamContentType = contentType
        destination = DesktopDestination.CATALOG
        // The details page is opened by the catalogue screen, whose "is it open" flag is its own
        // local state. Without this the navigation landed on the grid instead — the item was
        // selected, but nothing told the screen to show it.
        pendingDetailsRequest = providerId
        return true
    }

    /**
     * A provider id whose details page should open as soon as the catalogue screen is composed.
     *
     * Consumed once by that screen and cleared, so returning to the catalogue later does not
     * re-open a page the user has already dismissed.
     */
    var pendingDetailsRequest by mutableStateOf<String?>(null)
        private set

    fun consumePendingDetailsRequest(): String? = pendingDetailsRequest?.also { pendingDetailsRequest = null }

    /**
     * The local catalogue in the terms matching needs.
     *
     * Live channels are dropped: a channel is a stream, not a work, and matching one against a film
     * would only ever produce noise.
     */
    /**
     * The whole loaded catalogue in the terms matching needs.
     *
     * Asked of the repository, not of [selectedCatalog]: that holds one page of eighty items, so
     * matching against it answered "you do not have this" for almost every title the user actually
     * owns. The question is about the library, so it has to be asked of the library.
     */
    private fun libraryMatchCandidates(): List<LibraryCandidate> =
        runCatching { xtreamRepository.libraryMatchCandidates() }.getOrDefault(emptyList())

    /** Opens the Assinaturas area at its shelves, loading them if this is the first visit. */
    fun openSubscriptions() {
        favoritesOnly = false
        streamingOffers = OfferRanking.EMPTY
        selectedStreamingTitle = null
        destination = DesktopDestination.SUBSCRIPTIONS
        loadStreamingShelves()
    }

    /**
     * The active profile's stated streaming services, in the form the ranking policy consumes.
     *
     * A statement by the user, never a verified entitlement — see [StoredStreamingPreference]. Read
     * fresh rather than cached because switching profile must switch the answer.
     */
    val streamingPreference: UserStreamingPreference
        get() =
            userStore.streamingPreference(activeProfileId).let { stored ->
                UserStreamingPreference(
                    subscribedProviderIds = stored.subscribedProviderIds,
                    preferredCurrency = stored.currency,
                )
            }

    /** Records which services this profile says it pays for. */
    fun setStreamingServices(providerIds: Set<String>) {
        val profileId = activeProfileId ?: return
        val stored = userStore.streamingPreference(profileId)
        userStore.setStreamingPreference(profileId, stored.copy(subscribedProviderIds = providerIds))
    }

    /** Opens the music workspace at its home section. */
    fun openMusic() {
        favoritesOnly = false
        destination = DesktopDestination.MUSIC
        musicSection = MusicSection.HOME
        selectedMusicArtist = null
    }

    fun selectMusicSection(section: MusicSection) {
        musicSection = section
        // Cleared on every section change, so leaving Artistas and coming back shows the grid
        // rather than whichever artist happened to be open last time.
        selectedMusicArtist = null
    }

    fun selectMusicArtist(name: String?) {
        selectedMusicArtist = name
    }

    /** Tracks by [artistName], in playlist order. */
    fun tracksForArtist(artistName: String): List<MusicTrack> =
        musicLibrary.songs.filter { track ->
            track.artistOrUnknown.equals(artistName, ignoreCase = true)
        }

    /**
     * Attaches a music playlist to a profile, or removes it when [path] is null.
     *
     * Reloading immediately rather than at the next profile switch: the user has just chosen the
     * file and expects the section to appear.
     */
    suspend fun setMusicPlaylist(profileId: String, path: Path?) {
        profiles =
            profiles.map { profile ->
                if (profile.id == profileId) {
                    profile.copy(musicPlaylistPath = path?.toString())
                } else {
                    profile
                }
            }
        userStore.saveProfiles(profiles)
        if (profileId == activeProfileId) loadMusicLibrary()
    }

    /**
     * Playback request for a track, opening its history row.
     *
     * Music reuses the video player untouched: a track is a URI like any other, and the overlay
     * already handles a stream that never ends. No progress identity is attached — GDD 8 section 18
     * says music does not resume its position, and a radio station has no position to resume from
     * at all.
     *
     * Starting playback no longer counts a play. Section 18 counts one only after a configured
     * threshold, so this records a zero-count row and [reportMusicListened] converts it into a play
     * once the listener actually stays. Counting on click is what let skipping through a playlist
     * manufacture a "most played" ranking out of tracks nobody heard.
     */
    fun prepareMusicPlayback(track: MusicTrack): DesktopPlaybackRequest? =
        runCatching {
            val uri = URI(track.streamUri)
            require(uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https", "file"))
            reportMusicListened(track, listenedMillis = 0L)
            DesktopPlaybackRequest(
                title = musicPlaybackTitle(track).take(180),
                uri = uri,
                progressIdentity = null,
                startPositionMillis = 0L,
            )
        }.getOrNull()

    /**
     * Reports how long [track] has been listened to, counting a play once the threshold is passed.
     *
     * Idempotent in the sense that matters: the store folds each report through
     * [ListeningHistoryRules], so a caller reporting progress repeatedly still increments the count
     * only when a report crosses the threshold. Radio is recorded as radio so it gains history
     * without ever gaining a position.
     */
    fun reportMusicListened(
        track: MusicTrack,
        listenedMillis: Long,
    ) {
        val profileId = activeProfileId ?: return
        val kind = if (track.isRadio) ListeningKind.RADIO else ListeningKind.MUSIC
        listeningHistory =
            listeningHistoryStore.record(
                profileId = profileId,
                mediaIdentity = track.id,
                kind = kind,
                listenedMillis = listenedMillis,
                durationMillis = track.durationSeconds?.times(1_000L),
                sourceId = activeProfile?.sourceId,
            )
        musicPlayCounts = listeningHistoryStore.playCountsFor(profileId)
    }

    // ---------------------------------------------------------------------------------------
    // Music playlists (GDD 8 §17)
    // ---------------------------------------------------------------------------------------

    /**
     * The smart playlists, evaluated against the current library and history.
     *
     * Recomputed from state rather than stored, which is the whole point of a smart playlist: a
     * track played today must appear in "recently played" without anything having been written to
     * a member list. Only rules with at least one track are returned, so the UI never shows a shelf
     * that promises tracks and delivers none.
     */
    val smartMusicPlaylists: List<SmartMusicPlaylist>
        get() {
            if (musicLibrary.isEmpty) return emptyList()
            val favourites = favoriteKeys
            val rules =
                buildList {
                    add(SmartPlaylistRule.Favourites)
                    add(SmartPlaylistRule.RecentlyPlayed)
                    add(SmartPlaylistRule.MostPlayed)
                    add(SmartPlaylistRule.NeverPlayed)
                    add(SmartPlaylistRule.RecentlyAdded)
                    // Genre and decade are per-value rules, so the available values come from the
                    // library itself rather than from a fixed list.
                    SmartPlaylists.genresIn(musicLibrary.tracks).forEach { add(SmartPlaylistRule.ByGenre(it)) }
                    SmartPlaylists.decadesIn(musicLibrary.tracks).forEach {
                        add(SmartPlaylistRule.ByDecade(it.startYear))
                    }
                }
            return rules.mapNotNull { rule ->
                val tracks =
                    SmartPlaylists.evaluate(
                        rule = rule,
                        tracks = musicLibrary.tracks,
                        history = listeningHistory,
                        favouriteIds = favourites,
                    )
                if (tracks.isEmpty()) null else SmartMusicPlaylist(rule = rule, tracks = tracks)
            }
        }

    fun createMusicPlaylist(name: String) {
        musicPlaylists = musicPlaylistStore.create(activeProfileId, name)
    }

    fun renameMusicPlaylist(
        playlistId: String,
        name: String,
    ) {
        musicPlaylists =
            musicPlaylistStore.update(activeProfileId, playlistId) { it.renamed(name, System.currentTimeMillis()) }
    }

    fun deleteMusicPlaylist(playlistId: String) {
        musicPlaylists = musicPlaylistStore.delete(activeProfileId, playlistId)
        if (selectedMusicPlaylistId == playlistId) selectedMusicPlaylistId = null
    }

    /**
     * Duplicates a stored playlist.
     *
     * The copy is created through the store so it gets its own identity, then filled with the
     * original's tracks — a duplicate that shared an id would be the same playlist twice.
     */
    fun duplicateMusicPlaylist(
        playlistId: String,
        copyName: String,
    ) {
        val original = musicPlaylists.firstOrNull { it.id == playlistId } ?: return
        musicPlaylists =
            musicPlaylistStore.create(
                profileId = activeProfileId,
                name = copyName,
                kind = MusicPlaylistKind.MANUAL,
                trackIds = original.trackIds,
            )
    }

    fun addTrackToMusicPlaylist(
        playlistId: String,
        trackId: String,
    ) {
        musicPlaylists =
            musicPlaylistStore.update(activeProfileId, playlistId) {
                it.withTrackAdded(trackId, System.currentTimeMillis())
            }
    }

    fun removeTrackFromMusicPlaylist(
        playlistId: String,
        trackId: String,
    ) {
        musicPlaylists =
            musicPlaylistStore.update(activeProfileId, playlistId) {
                it.withTrackRemoved(trackId, System.currentTimeMillis())
            }
    }

    fun reorderMusicPlaylist(
        playlistId: String,
        fromIndex: Int,
        toIndex: Int,
    ) {
        musicPlaylists =
            musicPlaylistStore.update(activeProfileId, playlistId) {
                it.reordered(fromIndex, toIndex, System.currentTimeMillis())
            }
    }

    fun selectMusicPlaylist(playlistId: String?) {
        selectedMusicPlaylistId = playlistId
    }

    /** The open playlist's tracks, resolved against the library in the user's chosen order. */
    fun tracksForMusicPlaylist(playlistId: String): List<MusicTrack> =
        musicPlaylists.firstOrNull { it.id == playlistId }?.resolve(musicLibrary).orEmpty()

    /**
     * Saves an arbitrary list of track ids as a playlist.
     *
     * This is the hook the playback queue calls for "save queue as playlist" (GDD 8 section 16).
     * It is defined here, on the playlist side, so the queue owner needs no knowledge of playlist
     * storage — it passes identities and gets a playlist.
     */
    fun saveTrackIdsAsMusicPlaylist(
        name: String,
        trackIds: List<String>,
    ) {
        if (trackIds.isEmpty()) return
        musicPlaylists =
            musicPlaylistStore.create(
                profileId = activeProfileId,
                name = name,
                kind = MusicPlaylistKind.SAVED_QUEUE,
                trackIds = trackIds,
            )
    }

    /**
     * Imports an M3U as a playlist, adding its tracks to the library for this session.
     *
     * The imported entries are appended to the in-memory library so the playlist's ids resolve;
     * nothing is written back to the user's own M3U file, which is theirs to edit.
     */
    suspend fun importMusicPlaylist(path: Path) {
        val profileId = activeProfileId ?: return
        val imported =
            withContext(Dispatchers.IO) {
                runCatching { musicLoader.load(path) }.getOrNull()
            } ?: return
        if (imported.isEmpty) return

        val existingIds = musicLibrary.tracks.map(MusicTrack::id).toSet()
        val fresh = imported.tracks.filterNot { it.id in existingIds }
        if (fresh.isNotEmpty()) {
            val merged = musicLibrary.tracks + fresh
            musicLibrary =
                MusicLibrary(
                    tracks = merged,
                    artists = MusicPlaylistMapper.artistsFrom(merged),
                    genres = MusicPlaylistMapper.genresFrom(merged),
                )
        }
        musicPlaylists =
            musicPlaylistStore.create(
                profileId = profileId,
                name = path.fileName?.toString()?.substringBeforeLast('.').orEmpty().ifBlank { "M3U" },
                kind = MusicPlaylistKind.IMPORTED,
                trackIds = imported.tracks.map(MusicTrack::id),
            )
    }

    /**
     * Begins an export, raising the sensitive-URL warning when one is needed.
     *
     * Nothing is written here. GDD 8 section 17 requires the warning before an export that would
     * disclose credential-bearing URLs, so this only prepares the request; [confirmMusicExport]
     * writes the file once the user has answered.
     */
    fun beginMusicExport(
        playlistName: String,
        tracks: List<MusicTrack>,
        destination: Path,
    ) {
        if (tracks.isEmpty()) return
        pendingMusicExport =
            PendingMusicExport(
                playlistName = playlistName,
                tracks = tracks,
                destination = destination,
                warning = MusicPlaylistExportWarning.forTracks(tracks),
            )
    }

    fun cancelMusicExport() {
        pendingMusicExport = null
    }

    /**
     * Writes the pending export, having been acknowledged.
     *
     * Returns false when the exporter refuses — which it does if this is ever called without the
     * acknowledgement — so a failure surfaces rather than looking like a silent success.
     */
    suspend fun confirmMusicExport(): Boolean {
        val pending = pendingMusicExport ?: return false
        pendingMusicExport = null
        val result =
            MusicPlaylistExporter.export(
                playlistName = pending.playlistName,
                tracks = pending.tracks,
                acknowledgedSensitiveUris = true,
            )
        val written = result as? MusicPlaylistExportResult.Written ?: return false
        return withContext(Dispatchers.IO) {
            runCatching {
                Files.writeString(pending.destination, written.content)
                true
            }.getOrDefault(false)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Playback queue (GDD 8 §16)
    // ---------------------------------------------------------------------------------------

    /**
     * The active profile's queue.
     *
     * GDD 8 §16 scopes it "por perfil e por sessão", which is exactly what a field on this state
     * object gives: it is dropped when the profile changes and when the app closes. It holds
     * identities only — resolving a track to its URI happens in [playQueueCurrent], at the moment of
     * playback, so no stream URL is ever held in queue state.
     */
    var playbackQueue by mutableStateOf(PlaybackQueue.EMPTY)
        private set

    /** Whether the queue panel is showing. Closed by default; the queue works without it. */
    var queuePanelVisible by mutableStateOf(false)
        private set

    /**
     * A monotonic source for [QueueEntry.handle].
     *
     * Queueing the same track twice is legitimate, so the media id cannot serve as a list key; this
     * gives every queued position one that is genuinely unique.
     */
    private var queueHandleSeed = 0L

    fun toggleQueuePanel() {
        queuePanelVisible = !queuePanelVisible
    }

    /**
     * Named `showQueuePanel` rather than `setQueuePanelVisible`: the latter collides on the JVM with
     * the setter Compose generates for [queuePanelVisible].
     */
    fun showQueuePanel(visible: Boolean) {
        queuePanelVisible = visible
    }

    /** Wraps a library track as a queue entry. Deliberately drops `streamUri` — see [QueueEntry]. */
    private fun queueEntryFor(track: MusicTrack): QueueEntry =
        QueueEntry(
            mediaId = track.id,
            kind = if (track.isRadio) QueueMediaKind.RADIO else QueueMediaKind.MUSIC,
            title = track.title,
            subtitle = if (track.isRadio) track.genre else track.artistOrUnknown,
            handle = ++queueHandleSeed,
        )

    /** Looks an identity back up in the library. Null once the playlist has been replaced. */
    private fun trackForQueueEntry(entry: QueueEntry): MusicTrack? =
        musicLibrary.tracks.firstOrNull { it.id == entry.mediaId }

    /**
     * Builds the playback request for whatever the queue is pointing at.
     *
     * This is the "resolver no playback" half of GDD 8 §16: the URI is produced here, from the live
     * library, and never stored in the queue. An entry whose track has vanished — the user swapped
     * their playlist mid-session — is dropped from the queue rather than left to fail on every
     * attempt, and the next entry is tried instead.
     */
    private fun resolveQueueCurrent(): DesktopPlaybackRequest? {
        // Bounded by the queue length: each miss removes an entry, so this cannot spin.
        repeat(playbackQueue.size) {
            val entry = playbackQueue.current ?: return null
            val track = trackForQueueEntry(entry)
            val request = track?.let(::prepareMusicPlayback)
            if (request != null) return request
            playbackQueue = playbackQueue.removeFirst(entry.mediaId)
        }
        return null
    }

    /**
     * Starts [track], with [context] becoming the queue behind it.
     *
     * Passing the surrounding shelf is what makes playback continue into the next track instead of
     * stopping after one. [PlaybackQueue.playNow] applies the §16 rules to the batch — a station
     * keeps only itself, a chapter drops the songs around it.
     */
    fun playMusicNow(
        track: MusicTrack,
        context: List<MusicTrack> = listOf(track),
    ): DesktopPlaybackRequest? {
        val entries = context.map(::queueEntryFor)
        val start = context.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playbackQueue = PlaybackQueue.EMPTY.playNow(entries, start)
        return resolveQueueCurrent()
    }

    /**
     * Queues [track] to play after the current one.
     *
     * Returns a request only when the queue was empty and this therefore starts playback; adding to
     * a queue that is already playing must not interrupt it.
     */
    fun queueMusicNext(track: MusicTrack): DesktopPlaybackRequest? {
        val wasEmpty = playbackQueue.isEmpty
        val before = playbackQueue.current
        playbackQueue = playbackQueue.playNext(queueEntryFor(track))
        // playNext replaces the queue for radio and for a mismatched kind, so "what is playing"
        // can change even though the caller only asked to queue something.
        return if (wasEmpty || before != playbackQueue.current) resolveQueueCurrent() else null
    }

    /** Queues [track] at the end. Same non-interrupting contract as [queueMusicNext]. */
    fun queueMusicLast(track: MusicTrack): DesktopPlaybackRequest? {
        val wasEmpty = playbackQueue.isEmpty
        val before = playbackQueue.current
        playbackQueue = playbackQueue.addToEnd(queueEntryFor(track))
        return if (wasEmpty || before != playbackQueue.current) resolveQueueCurrent() else null
    }

    /** Queues several tracks at the end, for "queue this artist". */
    fun queueMusicLast(tracks: List<MusicTrack>): DesktopPlaybackRequest? {
        val wasEmpty = playbackQueue.isEmpty
        val before = playbackQueue.current
        playbackQueue = playbackQueue.addAllToEnd(tracks.map(::queueEntryFor))
        return if (wasEmpty || before != playbackQueue.current) resolveQueueCurrent() else null
    }

    /** Plays the queued row at [position]. */
    fun playQueuePosition(position: Int): DesktopPlaybackRequest? {
        val next = playbackQueue.jumpTo(position)
        if (next == playbackQueue) return null
        playbackQueue = next
        return resolveQueueCurrent()
    }

    /**
     * Removes a queued row.
     *
     * Returns a request when the removed row was the one playing, because the queue has then moved
     * on to the next entry and the player has to follow it. Removing anything else returns null and
     * leaves playback alone.
     */
    fun removeQueuePosition(position: Int): DesktopPlaybackRequest? {
        val before = playbackQueue
        val next = before.removeAt(position)
        if (next == before) return null
        playbackQueue = next
        return if (before.currentChangedBy(next) && !next.isEmpty) resolveQueueCurrent() else null
    }

    /** Drag-reorder. Never returns a request: moving rows around must not interrupt the song. */
    fun moveQueuePosition(from: Int, to: Int) {
        playbackQueue = playbackQueue.reorder(from, to)
    }

    /** GDD 8 §16 "limpar". The player is left running; clearing the queue is not a stop command. */
    fun clearQueue() {
        playbackQueue = PlaybackQueue.EMPTY
    }

    /** Advances to the next entry, or returns null at the end of the queue. */
    fun playQueueNext(): DesktopPlaybackRequest? {
        val next = playbackQueue.advance() ?: return null
        playbackQueue = next
        return resolveQueueCurrent()
    }

    /** Steps back one entry, or returns null at the head. */
    fun playQueuePrevious(): DesktopPlaybackRequest? {
        val previous = playbackQueue.back() ?: return null
        playbackQueue = previous
        return resolveQueueCurrent()
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

    suspend fun loadDailyHome(
        date: LocalDate = LocalDate.now(),
        /**
         * Reports the slow parts of building the home, for the splash screen.
         *
         * Defaulted away because every other caller — a date change, a manual refresh — happens
         * behind an already-drawn screen with no bar to move.
         */
        onCatalogueStage: (progress: Float, message: String) -> Unit = { _, _ -> },
    ) {
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
                // These two are the slowest part of a cold start by a wide margin — each pulls a
                // provider's entire catalogue, and a large list is tens of thousands of items. They
                // report themselves so the splash does not sit on one number for the whole wait.
                if (XtreamContentType.MOVIE !in latestSummary?.loadedContentTypes.orEmpty()) {
                    onCatalogueStage(0.75f, "Baixando a lista de filmes…")
                    latestSummary = xtreamRepository.loadCatalog(XtreamContentType.MOVIE)
                }
                if (XtreamContentType.SERIES !in latestSummary?.loadedContentTypes.orEmpty()) {
                    onCatalogueStage(0.88f, "Baixando a lista de séries…")
                    latestSummary = xtreamRepository.loadCatalog(XtreamContentType.SERIES)
                }
                onCatalogueStage(0.96f, "Montando a tela inicial…")
                val kidsMode = activeProfile?.isKids == true
                val movies = dailyPage(XtreamContentType.MOVIE, date.dayOfYear * 31 + date.year, kidsMode, 18)
                val series = dailyPage(XtreamContentType.SERIES, date.dayOfYear * 17 + date.year, kidsMode, 18)
                val live = dailyPage(XtreamContentType.LIVE, date.dayOfYear * 7 + date.year, kidsMode, 14)
                // Scored rather than picked by date arithmetic. The old rule was
                // `dayOfYear % poolSize`, which changed daily and did nothing else — it could put
                // an unrated piece of catalogue filler in the largest slot on the screen.
                val heroRotation =
                    HeroSelection.rotationFor(
                        candidates =
                            (movies + series).map { item ->
                                HeroCandidate(
                                    id = "${item.contentType}:${item.providerId}",
                                    title = item.name,
                                    year = item.year,
                                    rating = item.rating,
                                    hasArtwork = !item.artworkUrl.isNullOrBlank(),
                                )
                            },
                        dayOfEpoch = date.toEpochDay(),
                    )
                val heroPool =
                    heroRotation.mapNotNull { chosen ->
                        (movies + series).firstOrNull { item ->
                            "${item.contentType}:${item.providerId}" == chosen.id
                        }
                    }
                DailyHomeSnapshot(
                    sourceId = sourceId,
                    date = date,
                    // First of the rotation, already scored and ordered by HeroSelection.
                    hero = heroPool.firstOrNull(),
                    heroRotation = heroPool,
                    movies = movies,
                    series = series,
                    live = live,
                    seasonal = seasonalShelf(date, kidsMode),
                ) to latestSummary
            }
        }.onSuccess { (snapshot, latestSummary) ->
            // Reported once per load so the real counts can be read from the log rather than
            // inferred. The shelves have been "fixed" twice against a cause never observed.
            println(
                "BURO home: movies=${snapshot.movies.size} series=${snapshot.series.size} " +
                    "live=${snapshot.live.size} hero=${snapshot.hero != null}",
            )
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

    suspend fun connectXtream(
        input: XtreamLoginInput,
        /**
         * Reports what the connection is doing, for the splash screen.
         *
         * Defaulted so the ordinary login path — where the user is watching a form, not a progress
         * bar — needs no callback of its own.
         */
        onProgress: (fraction: Float, stage: SessionXtreamRepository.XtreamLoadStage) -> Unit = { _, _ -> },
    ) {
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
                    xtreamRepository.authenticateAndLoadInitial(input, onProgress)
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

    /**
     * Moves the bar *inside* one phase, between [from] and [to].
     *
     * The connect phase alone can take most of a slow start, and a bar parked at one value for that
     * long reads as a hang however honest the label is. [fraction] is the phase's own 0..1.
     *
     * Never goes backwards: phases report their own progress and a later one starting at 0 would
     * otherwise yank the bar left, which looks like a failure and a restart.
     */
    private fun startupProgressWithin(
        from: Float,
        to: Float,
        fraction: Float,
        message: String,
    ) {
        val target = from + (to - from) * fraction.coerceIn(0f, 1f)
        startupProgress = maxOf(startupProgress, target).coerceIn(0f, 1f)
        startupMessage = message
    }

    suspend fun restoreRememberedXtream() {
        try {
            // Before the early return below: a returning user whose session is already open still
            // needs their music playlist read, and the Músicas entry is hidden until it has been.
            loadMusicLibrary()
            if (xtreamStatus !is XtreamStatus.Disconnected || xtreamSummary != null) return
            // Swept before anything else: a chunk from a transfer that died with the app is not a
            // download, and leaving it makes the library claim a title it cannot play.
            downloadManager.discardInterruptedDownloads()
            startupStep(1, "Abrindo a sua sessão…")
            val input = withContext(Dispatchers.IO) { rememberedXtreamStore.load() } ?: return

            // The connect phase is four network round trips and used to sit under one unchanging
            // message, which on a slow provider is indistinguishable from a hang. It now reports
            // each one, and the bar moves within the phase rather than between phases.
            connectXtream(input) { fraction, stage ->
                startupProgressWithin(
                    from = 0.2f,
                    to = 0.7f,
                    fraction = fraction,
                    message =
                        when (stage) {
                            is SessionXtreamRepository.XtreamLoadStage.Authenticating -> "Autenticando…"
                            is SessionXtreamRepository.XtreamLoadStage.Categories ->
                                when (stage.contentType) {
                                    XtreamContentType.LIVE -> "Carregando categorias de canais…"
                                    XtreamContentType.MOVIE -> "Carregando categorias de filmes…"
                                    XtreamContentType.SERIES -> "Carregando categorias de séries…"
                                }
                            is SessionXtreamRepository.XtreamLoadStage.Channels -> "Carregando canais…"
                        },
                )
            }
            // The home is built from the catalogue, so loading it here means the first screen is
            // complete when the splash clears rather than filling in afterwards.
            if (xtreamStatus !is XtreamStatus.Error) {
                startupStep(4, "Organizando filmes e séries…")
                loadDailyHome(LocalDate.now()) { progress, message ->
                    startupProgressWithin(from = progress, to = progress, fraction = 1f, message = message)
                }
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
                // Most providers leave the trailer field empty even for films that plainly have
                // one, so without this the button simply never appeared. Looked up after the
                // details are on screen: the page must not wait on a network call for an extra.
                if (details.youtubeTrailerId.isNullOrBlank() && metadataClient.isConfigured) {
                    val found =
                        withContext(Dispatchers.IO) {
                            metadataClient.findTrailer(
                                title = selected.name.editorialCatalogTitle(),
                                year = selected.year,
                            )
                        }
                    if (found != null && selectedXtreamItemId == selected.providerId) {
                        movieDetailsStatus =
                            MovieDetailsStatus.Loaded(details.copy(youtubeTrailerId = found))
                    }
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
    /**
     * Downloads outlive the screen that started them.
     *
     * They used to run in the details page's own scope, so leaving that page cancelled the transfer
     * mid-flight. The file was already on disk - one user had a complete 983 MB episode - but the
     * sidecar that records it is written after the copy returns, and that line never ran: the
     * library kept showing "downloading" for something already finished and could not play it.
     *
     * SupervisorJob so one failed download does not cancel the others.
     */
    private val downloadScope =
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + Dispatchers.Default,
        )

    /** Starts a download that survives navigation. Callers no longer need a scope of their own. */
    fun enqueueDownload(
        target: XtreamPlaybackTarget,
        title: String,
        artworkUrl: String? = null,
    ) {
        downloadScope.launch { startDownload(target, title, artworkUrl) }
    }

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
                downloads =
                    downloads + (
                        contentKey to
                            DownloadState.Running(
                                fraction = fraction,
                                bytesPerSecond = rateTracker.observe(contentKey, read),
                                downloadedBytes = read,
                                totalBytes = total ?: -1L,
                            )
                    )
            }
        // A refused duplicate must not touch the state: the download already in flight owns this
        // key, and overwriting its Running progress with Failed made the first copy look broken
        // while it was still downloading perfectly well.
        if (result is DownloadResult.Failed && result.reason == FailureReason.ALREADY_RUNNING) return
        // Dropped whatever the outcome, so a retry measures itself rather than inheriting the rate
        // of the attempt that failed.
        rateTracker.forget(contentKey)
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

        // The photo and biography come first. The local sweep below costs one provider request per
        // film across the whole catalogue and takes minutes on a large list; running it first meant
        // the face and the filmography - a single fast lookup - waited behind it, and in practice
        // the user had given up and navigated away before either arrived.
        if (metadataClient.isConfigured) {
            val enriched =
                withContext(Dispatchers.IO) {
                    val person = metadataClient.findPerson(cleanName) ?: return@withContext null
                    Triple(
                        person.profileImageUrl,
                        metadataClient.personDetails(person.id)?.biography,
                        metadataClient.filmography(person.id, MAX_FILMOGRAPHY_ITEMS),
                    )
                }
            if (selectedPerson?.name != cleanName) return
            enriched?.let { (photo, biography, credits) ->
                selectedPerson =
                    selectedPerson?.copy(
                        photoUrl = photo,
                        biography = biography,
                        credits =
                            credits.map { credit ->
                                PersonCredit(
                                    title = credit.title,
                                    year = credit.year,
                                    posterUrl = credit.posterUrl,
                                    character = credit.character,
                                )
                            },
                    )
            }
        }

        val discovered =
            withContext(Dispatchers.Default) {
                xtreamRepository.findByCastMember(cleanName, MAX_FILMOGRAPHY_ITEMS)
            }
        // The user may have navigated away while the sweep ran.
        if (selectedPerson?.name != cleanName) return
        val merged = (known + discovered).distinctBy { it.providerId }
        // Copied rather than rebuilt: a fresh PersonFilmography would discard the photo and credits
        // that the lookup above already put on screen.
        selectedPerson = selectedPerson?.copy(items = merged, isLoading = false)
    }

    /**
     * Opens a title from a filmography, if this playlist happens to carry it.
     *
     * The credit comes from the metadata service and names a film that may not be in the user's
     * catalogue at all; matching is on the editorial title, so the provider's "Narcos 4K [DUB]"
     * still resolves to a credit called "Narcos". Returns false when there is no match, and the
     * caller says so rather than appearing to do nothing.
     */
    suspend fun openTitleFromCredit(title: String): Boolean {
        val wanted = title.editorialCatalogTitle().lowercase(Locale.ROOT).trim()
        if (wanted.isBlank()) return false

        val found =
            withContext(Dispatchers.Default) {
                // Films first, then series: a credit does not say which it is, and a viewer looking
                // for "Narcos" wants the series rather than a documentary of the same name.
                sequenceOf(XtreamContentType.MOVIE, XtreamContentType.SERIES)
                    .mapNotNull { type ->
                        xtreamRepository
                            .page(type, null, wanted, 0, pageSize = 20)
                            .items
                            .firstOrNull { item ->
                                item.name.editorialCatalogTitle().lowercase(Locale.ROOT).trim() == wanted
                            }
                    }.firstOrNull()
            } ?: return false

        closePerson()
        selectXtreamItem(found.providerId)
        return true
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

/**
 * The shapes the catalogue grid can take.
 *
 * Three, not two: posters are how people recognise a film, the compact grid is how they scan a
 * category of four hundred, and the list is how they read titles that artwork does not distinguish -
 * live channels, or the numbered episodes of a long-running show.
 */
enum class CatalogLayout(val id: String) {
    /** Large artwork with the title underneath. The default, and how a catalogue is browsed. */
    POSTER("poster"),

    /** Smaller artwork, more per row: for scanning a long category rather than admiring it. */
    COMPACT("compact"),

    /** One row per title, artwork small and the name given room. Best where names carry the meaning. */
    LIST("list"),
    ;

    companion object {
        fun fromId(id: String?): CatalogLayout = entries.firstOrNull { it.id == id } ?: POSTER
    }
}

enum class DesktopDestination { HOME, CATALOG, FAVORITES, DOWNLOADS, CONTINUE, MUSIC, SUBSCRIPTIONS }

/** Sections of the music workspace, mirroring the sidebar's own ordering. */
enum class MusicSection { HOME, ARTISTS, PLAYLISTS, RADIO, DOWNLOADS }

/**
 * A smart playlist with its evaluated contents — GDD 8 section 17.
 *
 * The rule travels with the tracks so the UI can label the shelf from the rule rather than from a
 * stored name; a smart playlist has no name of its own, only the rule that defines it.
 */
data class SmartMusicPlaylist(
    val rule: SmartPlaylistRule,
    val tracks: List<MusicTrack>,
)

/**
 * An export waiting on the user's answer to the sensitive-URL warning.
 *
 * Holds the resolved tracks rather than re-reading them at confirmation time, so what the warning
 * counted is exactly what gets written.
 */
data class PendingMusicExport(
    val playlistName: String,
    val tracks: List<MusicTrack>,
    val destination: Path,
    val warning: MusicPlaylistExportWarning,
)

data class DailyHomeSnapshot(
    val sourceId: String,
    val date: LocalDate,
    val hero: XtreamCatalogItem?,
    /**
     * The banner rotation, best first — [hero] is simply its first entry.
     *
     * Several rather than one so the banner can cycle: a single daily pick meant the same image all
     * day, and a user who came back an hour later saw nothing new on the largest surface in the app.
     */
    val heroRotation: List<XtreamCatalogItem> = emptyList(),
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

/**
 * What the player's title bar shows for a track.
 *
 * "Artist — Title" when both are known, because the player overlay has one line and a bare song
 * title tells the user nothing about which version is playing. A radio station keeps its own name:
 * the mapper deliberately never split one into artist and title.
 */
internal fun musicPlaybackTitle(track: MusicTrack): String =
    when {
        track.isRadio -> track.title
        track.artist.isNullOrBlank() -> track.title
        else -> "${track.artist} — ${track.title}"
    }

internal fun rotatingPageIndex(seed: Int, pageCount: Int): Int =
    Math.floorMod(seed, pageCount.coerceAtLeast(1))

/**
 * The title as a metadata service would know it.
 *
 * "72 Horas em Miami 4K [DV][HDR]" is the provider's shelf label, not a film name: searched
 * verbatim it matches nothing.
 */
internal fun String.editorialCatalogTitle(): String =
    replace(Regex("""\[[^]]{1,12}]"""), " ")
        .replace(
            Regex("""(?i)\b(4k|uhd|fhd|hd|sd|dv|hdr|h\.?265|hevc|multi|dual|leg|dub)\b"""),
            " ",
        ).replace(Regex("""\s+"""), " ")
        .trim()

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

    /**
     * [fraction] is negative when the server did not report a content length.
     *
     * The rest is what makes a long transfer bearable: a bar that only moves says nothing about
     * whether the download is healthy, and a stalled one looks identical to a slow one. The speed
     * answers "is it working", the remaining time answers "should I wait".
     */
    data class Running(
        val fraction: Float,
        val bytesPerSecond: Long = 0L,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = -1L,
    ) : DownloadState {
        /** Seconds left at the current rate, or null when it cannot be known. */
        val secondsRemaining: Long?
            get() =
                if (totalBytes > 0 && bytesPerSecond > 0 && downloadedBytes in 0 until totalBytes) {
                    (totalBytes - downloadedBytes) / bytesPerSecond
                } else {
                    null
                }
    }

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
