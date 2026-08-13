package com.lucasserafin94.iptvburo.desktop

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lucasserafin94.iptvburo.desktop.data.InMemoryCatalogRepository
import com.lucasserafin94.iptvburo.desktop.data.MusicLibraryLoader
import com.lucasserafin94.iptvburo.desktop.data.SessionXtreamRepository
import com.lucasserafin94.iptvburo.desktop.data.StreamingShelfDiskCache
import com.lucasserafin94.iptvburo.metadata.TMDB_SERIES_NAMESPACE
import com.lucasserafin94.iptvburo.metadata.TMDB_NAMESPACE
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleKind
import com.lucasserafin94.iptvburo.domain.model.ExternalContentId
import com.lucasserafin94.iptvburo.desktop.model.DesktopSourceKind
import com.lucasserafin94.iptvburo.desktop.model.DesktopSourceSummary
import com.lucasserafin94.iptvburo.desktop.model.ImportedCatalog
import com.lucasserafin94.iptvburo.desktop.model.XtreamCatalogPage
import com.lucasserafin94.iptvburo.desktop.model.XtreamPlaybackTarget
import com.lucasserafin94.iptvburo.desktop.model.XtreamSessionSummary
import com.lucasserafin94.iptvburo.desktop.platform.ExternalOpenResult
import com.lucasserafin94.iptvburo.desktop.platform.openUriExternally
import com.lucasserafin94.iptvburo.desktop.playback.DesktopPlaybackRequest
import com.lucasserafin94.iptvburo.desktop.playback.MultiviewTile
import com.lucasserafin94.iptvburo.desktop.playback.SubtitleColour
import com.lucasserafin94.iptvburo.desktop.playback.SubtitleSize
import com.lucasserafin94.iptvburo.desktop.playback.SubtitleStyle
import com.lucasserafin94.iptvburo.desktop.playback.DesktopPlaybackProgressCoordinator
import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import com.lucasserafin94.iptvburo.desktop.security.RememberedXtreamStore
import com.lucasserafin94.iptvburo.desktop.security.XtreamSource
import com.lucasserafin94.iptvburo.desktop.security.XtreamSourceLibrary
import com.lucasserafin94.iptvburo.desktop.license.LicenseClient
import com.lucasserafin94.iptvburo.desktop.license.LicenseStatus
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.RememberedScroll
import com.lucasserafin94.iptvburo.desktop.user.MusicCorrection
import com.lucasserafin94.iptvburo.desktop.user.MusicCorrectionStore
import com.lucasserafin94.iptvburo.desktop.user.CategoryPreferenceIdentity
import com.lucasserafin94.iptvburo.domain.model.MusicTidyProposal
import com.lucasserafin94.iptvburo.domain.model.MusicTidying
import com.lucasserafin94.iptvburo.domain.model.shelfDeduplicationKey
import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import com.lucasserafin94.iptvburo.desktop.user.DesktopProfile
import com.lucasserafin94.iptvburo.desktop.user.DesktopUserStore
import com.lucasserafin94.iptvburo.desktop.user.StoredParentalLock
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
import com.lucasserafin94.iptvburo.domain.model.ParentalLock
import com.lucasserafin94.iptvburo.domain.model.ParentalPin
import com.lucasserafin94.iptvburo.domain.model.PlaybackContentType
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgressIdentity
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgress
import com.lucasserafin94.iptvburo.desktop.platform.CastReceiver
import com.lucasserafin94.iptvburo.domain.model.AudioOutputMode
import com.lucasserafin94.iptvburo.domain.model.ResumeDecision
import com.lucasserafin94.iptvburo.domain.model.TitleShareLink
import com.lucasserafin94.iptvburo.domain.model.BestOfferPolicy
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleDetails
import com.lucasserafin94.iptvburo.domain.model.HeroCandidate
import com.lucasserafin94.iptvburo.domain.model.HeroSelection
import com.lucasserafin94.iptvburo.domain.model.ViewerAffinity
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
import com.lucasserafin94.iptvburo.metadata.TmdbServiceShelf
import com.lucasserafin94.iptvburo.metadata.TmdbDiscoverKind
import com.lucasserafin94.iptvburo.metadata.TmdbTitleDetails
import com.lucasserafin94.iptvburo.metadata.TmdbStreamingCatalogue
import com.lucasserafin94.iptvburo.metadata.TmdbShelfLoadResult
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** Provider ids are reused between film and series catalogues, so both parts form the cache key. */
internal fun heroSynopsisKey(
    contentType: XtreamContentType,
    providerId: String,
): String = "${contentType.name}:$providerId"

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
    /** Names the user corrected, overlaid on the playlist each time it is read. */
    private val correctionStore: MusicCorrectionStore = MusicCorrectionStore(),
    val licenseClient: LicenseClient = LicenseClient(),
) {
    /**
     * What the licence server last said about this machine.
     *
     * Null while the first check is in flight, which is what the splash screen covers. Anything else
     * is a decision: [LicenseStatus.allowsUse] tells the shell whether to show the app or the gate.
     *
     * Held here rather than checked where it is needed, because it must be asked exactly once per
     * launch. A check per screen would be a network round trip every time the user navigated.
     */
    var licenseStatus by mutableStateOf<LicenseStatus?>(null)
        private set

    /**
     * Runs the launch check.
     *
     * Off the main thread: it makes a network call, and a frozen window while somebody waits on a
     * server is the thing that makes an application feel broken.
     */
    suspend fun checkLicense() {
        val result = withContext(Dispatchers.IO) { licenseClient.check() }
        licenseStatus = result
    }

    /** After the gate rechecks or redeems a key, so a newly paid customer gets in without a restart. */
    fun onLicenseRechecked(status: LicenseStatus) {
        licenseStatus = status
    }

    /**
     * The activation key this installation redeemed, shown back to its owner in Options.
     *
     * A customer who loses the key has to buy another — it binds to one device and nothing in the
     * app used to display it, so the only copy was wherever they happened to paste it after the
     * purchase.
     */
    var activationKey by mutableStateOf(userStore.activationKey())
        private set

    fun rememberActivationKey(key: String) {
        val clean = key.trim().uppercase(Locale.ROOT).takeIf(String::isNotBlank) ?: return
        userStore.setActivationKey(clean)
        activationKey = clean
    }
    /**
     * Cast metadata, keyed by the user's own TMDb key.
     *
     * Rebuilt whenever the key changes so pasting one takes effect without a restart.
     */
    private var metadataClient = TmdbClient(userStore.metadataApiKey() ?: BUNDLED_TMDB_KEY.ifBlank { null })

    var metadataApiKey by mutableStateOf(userStore.metadataApiKey().orEmpty())
        private set

    /**
     * The key actually used for requests: this profile's own, then the shared one, then the
     * bundled one.
     *
     * Three levels rather than two because each answers a different need. The bundled key makes the
     * app work out of the box; the shared key is a household that pasted its own; a per-profile key
     * is for the case where one person wants their own TMDb account's quota used — TMDb rate-limits
     * per key, so several heavy users on one key throttle each other.
     */
    private fun effectiveMetadataKey(): String? =
        userStore.effectiveMetadataApiKey(activeProfileId) ?: BUNDLED_TMDB_KEY.ifBlank { null }

    /**
     * This profile's own key, empty when it uses the shared one.
     *
     * Separate from [metadataApiKey], which is the shared value: the settings screen shows both, so
     * it can say which is in use rather than leaving the user to guess.
     */
    // Starts empty rather than reading the store: `activeProfileId` is declared further down and is
    // not initialised yet at this point. It is filled by the init block below, once the profile is
    // known, and by every later profile switch.
    var profileMetadataApiKey by mutableStateOf("")
        private set

    /**
     * Sets or clears this profile's own key.
     *
     * Clearing it falls back to the shared key rather than switching metadata off, which is what
     * makes "usar a mesma" the natural default: leave the field empty and the profile inherits.
     */
    fun updateProfileMetadataApiKey(value: String) {
        val profileId = activeProfileId ?: return
        profileMetadataApiKey = value
        userStore.setProfileMetadataApiKey(profileId, value)
        rebuildMetadataClients()
    }

    /**
     * Rebuilds everything that bakes the key into its requests.
     *
     * Both clients, not one: Assinaturas has its own, and updating only the cast-photo client left
     * the shelves on the previous key — a half-applied setting the user cannot diagnose.
     */
    private fun rebuildMetadataClients() {
        val key = effectiveMetadataKey()
        metadataClient = TmdbClient(key)
        // Dropped with the client that produced them.
        //
        // A miss is cached as null so an unknown person is not looked up on every recomposition —
        // but every name attempted before a key was set, or while a wrong one was in place, is a
        // miss. Keeping those entries meant `key in castPhotos` refused to try again, so the cast
        // of any film opened before the key was configured stayed faceless for the whole session.
        // That is precisely the "photos do elenco não aparecem" report, and it survives fixing the
        // key, which is what makes it look like the feature is simply broken.
        castPhotos = emptyMap()
        castLookupsInFlight.clear()
        streamingCatalogue = buildStreamingCatalogue(key, streamingRegion)
        shelfCache.clear()
        // The disk cache is deliberately left alone. A key identifies who is asking, not what TMDb
        // answers: the same region returns the same catalogue whoever holds the key, so the stored
        // shelves are still correct. The forced load below fetches fresh ones regardless, and it
        // bypasses the disk — so a user who has just fixed a broken key sees the result of the fix
        // rather than yesterday's file.
        streamingShelves = emptyList()
        streamingLoadFailed = false
        loadStreamingShelves(force = true)
    }

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
     * How many faces to remember before dropping the oldest.
     *
     * Generous enough that a normal evening never evicts anything — a details page shows about a
     * dozen — and finite so a session left open for a day cannot grow without bound.
     */
    private val MAX_CAST_PHOTOS = 600

    /**
     * Cap on remembered hero synopses.
     *
     * Far more than a day of rotation reaches, so the cache still answers instantly for anything
     * recently on screen, while a session left open for days cannot grow without limit.
     */
    private val MAX_HERO_SYNOPSES = 200

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
        // The in-flight marker is released whatever happens.
        //
        // It used to be removed only on the line after a successful lookup, so a TMDb request that
        // threw — a timeout, a reset connection, a cancelled screen — left the name marked as
        // in flight for the rest of the session. That face never loaded again however many times
        // the user reopened the film, and the set grew by one entry per failure with nothing ever
        // clearing it.
        val photo =
            try {
                withContext(Dispatchers.IO) { metadataClient.findPerson(name)?.profileImageUrl }
            } catch (error: Throwable) {
                castLookupsInFlight.remove(key)
                // Cancellation is not a failure to record: the screen simply went away, and the
                // next visit should be free to ask again.
                error.rethrowIfCancellation()
                return
            }
        // Bounded, because this app is left running for hours.
        //
        // Every actor ever seen was kept for the life of the session, along with its in-flight
        // marker: browsing a few hundred titles in an evening accumulated thousands of entries that
        // nothing ever released. Small individually, but it only ever grew.
        //
        // The oldest half goes when the cap is reached rather than one entry at a time — dropping a
        // single name per insertion would evict a face that is on screen right now.
        castPhotos =
            if (castPhotos.size >= MAX_CAST_PHOTOS) {
                castPhotos.entries.drop(castPhotos.size / 2).associate { it.key to it.value } +
                    (key to photo)
            } else {
                castPhotos + (key to photo)
            }
        castLookupsInFlight.remove(key)
    }

    fun castPhotoFor(name: String): String? = castPhotos[name.trim().lowercase(Locale.ROOT)]

    /**
     * Sets the shared key, used by every profile that has not set its own.
     *
     * Clearing it falls back to the bundled key rather than to nothing: emptying the field should
     * restore the default behaviour, not switch cast photos off entirely.
     */
    fun updateMetadataApiKey(value: String) {
        metadataApiKey = value
        userStore.setMetadataApiKey(value)
        rebuildMetadataClients()
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

    /** Rejects a Home snapshot built under an older profile or parental policy. */
    private var dailyHomeRequestGeneration = 0L

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
    /**
     * The layout of each section, kept apart.
     *
     * One shared value meant switching Films to a list also switched Series and Live — but the
     * three are browsed differently. Posters suit films, a dense list suits four hundred live
     * channels, and someone who wants both had to keep changing it back.
     */
    private var catalogLayouts by mutableStateOf(
        XtreamContentType.entries.associateWith { type ->
            CatalogLayout.fromId(userStore.catalogLayout(initialUserSnapshot.activeProfileId, type.name))
        },
    )

    /** The layout of the section currently open. */
    val catalogLayout: CatalogLayout
        get() = catalogLayouts[xtreamContentType] ?: CatalogLayout.POSTER

    fun selectCatalogLayout(layout: CatalogLayout) {
        val type = xtreamContentType
        catalogLayouts = catalogLayouts + (type to layout)
        activeProfileId?.let { profileId -> userStore.setCatalogLayout(profileId, type.name, layout.id) }
    }

    private fun reloadCatalogLayouts() {
        catalogLayouts =
            XtreamContentType.entries.associateWith { type ->
                CatalogLayout.fromId(userStore.catalogLayout(activeProfileId, type.name))
            }
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
        val nextProfileId = id?.takeIf { candidate -> profiles.any { it.id == candidate } }
        if (nextProfileId != activeProfileId) RememberedScroll.clear()
        activeProfileId = nextProfileId
        userStore.setActiveProfile(activeProfileId)
        favoriteKeys = userStore.favoritesForProfile(activeProfileId)
        reloadCatalogLayouts()
        xtreamCategories = visibleXtreamCategories(xtreamContentType)
        if (selectedXtreamCategoryId !in xtreamCategories.map(XtreamCategory::providerId)) {
            selectedXtreamCategoryId = null
        }
        // The TMDb key can be per profile, so switching who is watching switches which key the
        // requests carry. Without this the new profile would keep using the previous one's — and
        // silently spend their quota.
        profileMetadataApiKey = userStore.profileMetadataApiKey(activeProfileId).orEmpty()
        rebuildMetadataClients()
        // The parental lock and the hidden categories are per profile too, and both are read
        // through the preferences store rather than from observed state.
        // Session unlocks are per profile as well. Carrying one into the next profile let a Kids
        // profile inherit a category the adult had opened with their PIN.
        unlockedCategories.clear()
        parentalRevision += 1
        hiddenCategoriesRevision += 1
        invalidateParentalBrowseSurfaces()
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
     * Renames a profile and changes its avatar or Kids setting.
     *
     * Everything here is cosmetic or local. Changing the *playlist* is a different operation with
     * different consequences — it swaps the account the profile signs in to — and lives in
     * [startEditingProfileSource] so that a rename cannot disconnect somebody by accident.
     */
    fun updateProfile(
        profileId: String,
        name: String,
        isKids: Boolean,
        avatarIndex: Int,
    ) {
        val clean = name.trim().take(24)
        if (clean.isBlank()) return

        val updated = profiles.map { profile ->
            if (profile.id == profileId) {
                profile.copy(name = clean, isKids = isKids, avatarIndex = avatarIndex)
            } else {
                profile
            }
        }
        if (updated == profiles) return

        // `profiles` is itself Compose state, so assigning it redraws every screen reading it —
        // including the catalogue, which is what makes a Kids change take effect immediately rather
        // than at the next launch.
        profiles = updated
        userStore.saveProfiles(profiles)
    }

    /**
     * Opens the account step so an existing profile can be pointed at a different playlist.
     *
     * The same screen used when adding a profile, because the decision is the same one: reuse a
     * connected account or enter another. [editingProfileId] is what tells that screen it is editing
     * rather than creating, so it saves onto the existing profile instead of making a sixth.
     */
    fun startEditingProfileSource(profileId: String) {
        if (profiles.none { it.id == profileId }) return
        editingProfileId = profileId
        onboarding = OnboardingStep.Account
    }

    /**
     * The profile whose playlist is being changed, or null when the account step is adding one.
     *
     * Cleared whenever the step is left by any route, including cancelling — a stale value here
     * would make the *next* profile creation silently overwrite this one instead.
     */
    var editingProfileId by mutableStateOf<String?>(null)
        private set

    /**
     * Opens the account step so a new profile can be given a playlist.
     *
     * Creating a profile from the gate only ever asked for a name and an avatar, so the choice
     * between reusing an existing playlist and adding another one — the whole point of per-profile
     * playlists — was reachable only during first-run setup.
     */
    fun startAddingProfile() {
        editingProfileId = null
        onboarding = OnboardingStep.Account
    }

    /**
     * Leaves the account step without creating anything.
     *
     * Only valid once a profile exists; during first-run setup there is nothing to go back to, so
     * the step stays modal.
     */
    fun cancelAddingProfile() {
        // Cleared on every exit, not only on success. A left-over value would make the next profile
        // creation overwrite the profile that was being edited instead of adding one.
        editingProfileId = null
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
        unlockedCategories.clear()
        forgetSelectedSource()

        // Cleared unconditionally, because forgetSelectedSource does not.
        //
        // That function returns early when no source is selected, and only its Xtream branch clears
        // the remembered credentials — so resetting while nothing was connected left the DPAPI blob
        // and the on-disk catalogue behind. Verified on a real machine after a reset:
        // remembered-source.dpapi and catalog-cache/live.burocat were both still there, which is
        // how the app came back holding a catalogue with no session to go with it.
        rememberedXtreamStore.clear()
        xtreamRepository.clearIncludingDiskCache()
        activationKey = null
        audioOutput = AudioOutputMode.SYSTEM

        // Deliberately *not* deleted: device-identity.dpapi.
        //
        // It is the licence identity, not a setting. Removing it gives the machine a new device
        // code, which strands a paid entitlement and drops the customer back to the trial — the
        // fault that cost somebody thirty days. "Reset everything" reads like an invitation to
        // include it; this comment is here to refuse that invitation.
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
        /** This profile's own TMDb key. Blank is the ordinary case: it inherits the shared one. */
        metadataKey: String = "",
    ) {
        onboarding = OnboardingStep.Connecting
        val server = input.copyServer()
        val username = input.copyUsername()
        val password = input.copyPassword()
        try {
            // Reported, because this is the longest wait the app ever asks for: a first catalogue
            // read on a new account. Without a callback the preparation screen's bar sat at 0% for
            // the whole of it, which is worse than showing no bar at all.
            connectXtream(input) { fraction, stage ->
                // Translated, unlike the older startup messages beside it: this screen is the first
                // thing a new user of any language sees, and a Portuguese sentence under a German
                // setup flow is simply a bug.
                val text = DesktopStrings.of(language).settingsText
                startupProgressWithin(
                    from = 0f,
                    to = 0.9f,
                    fraction = fraction,
                    message =
                        when (stage) {
                            is SessionXtreamRepository.XtreamLoadStage.Authenticating -> text.startupAuthenticating
                            else -> text.startupOrganising
                        },
                )
            }
            val status = xtreamStatus
            if (status is XtreamStatus.Error) {
                onboarding = OnboardingStep.Failed(status.message)
                return
            }
            val source = sourceLibrary.create(listLabel.ifBlank { profileName })
            sourceLibrary.store(source.id).save(server, username, password)

            // Editing an existing profile rather than creating one.
            //
            // Only the playlist changes. Name, avatar and Kids belong to the editor dialog and are
            // not on this screen, so taking them from here would silently overwrite them with this
            // form's defaults — a user who came to change their playlist would find their profile
            // renamed and no longer a Kids profile.
            val existing = editingProfileId?.let { id -> profiles.firstOrNull { it.id == id } }
            val profile =
                existing?.copy(sourceId = source.id)
                    ?: DesktopProfile(
                        id = UUID.randomUUID().toString(),
                        name = profileName.trim().ifBlank { "Meu perfil" },
                        isKids = false,
                        avatarIndex = avatarIndex,
                        sourceId = source.id,
                        musicPlaylistPath = musicPlaylistPath?.toString(),
                    )

            profiles =
                if (existing != null) {
                    profiles.map { if (it.id == profile.id) profile else it }
                } else {
                    listOf(profile) + profiles.filterNot { it.name == profile.name }
                }
            editingProfileId = null
            userStore.saveProfiles(profiles)
            attachPendingPhoto(profile.id)
            // Stored before selectProfile, which rebuilds the metadata clients: written after, the
            // new profile's first requests would still carry the previous key.
            if (metadataKey.isNotBlank()) userStore.setProfileMetadataApiKey(profile.id, metadataKey)
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
        /** This profile's own TMDb key. Blank is the ordinary case: it inherits the shared one. */
        metadataKey: String = "",
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
        // Editing points the existing profile at this playlist instead of adding another. Only
        // sourceId changes: the rest of the profile belongs to the editor dialog, not to this form.
        val existing = editingProfileId?.let { id -> profiles.firstOrNull { it.id == id } }
        val profile =
            existing?.copy(sourceId = sourceId)
                ?: DesktopProfile(
                    id = UUID.randomUUID().toString(),
                    name = profileName.trim().ifBlank { "Meu perfil" },
                    isKids = false,
                    avatarIndex = avatarIndex,
                    sourceId = sourceId,
                    musicPlaylistPath = musicPlaylistPath?.toString(),
                )

        profiles =
            if (existing != null) {
                profiles.map { if (it.id == profile.id) profile else it }
            } else {
                profiles + profile
            }
        editingProfileId = null
        userStore.saveProfiles(profiles)
        attachPendingPhoto(profile.id)
        // Before selectProfile, which rebuilds the metadata clients from whichever key applies.
        if (metadataKey.isNotBlank()) userStore.setProfileMetadataApiKey(profile.id, metadataKey)
        selectProfile(profile.id)
        loadMusicLibrary()
        onboarding = OnboardingStep.Done
    }

    fun isFavorite(item: XtreamCatalogItem): Boolean = favoriteKey(item) in favoriteKeys

    /**
     * Whether the title currently playing is a favourite, or null when it cannot be one.
     *
     * Null for a downloaded file or a local playlist entry: those have no catalogue item behind
     * them, and a heart that cannot be pressed is worse than no heart.
     */
    val playingIsFavorite: Boolean?
        get() = selectedXtreamItem?.let { item -> isFavorite(item) }

    /** Adds or removes the title currently playing from favourites. */
    fun togglePlayingFavorite() {
        selectedXtreamItem?.let(::toggleFavorite)
    }

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

    /**
     * Everything this profile has watched, most recent first.
     *
     * Unlike Continue watching, finished titles stay: the question is "what did I watch?", and a
     * film seen to the end is the clearest answer there is.
     */
    /**
     * What this profile tends to watch, for the home banner.
     *
     * Built from the categories of what has actually been opened — nothing more. It never leaves
     * the machine, is rebuilt from history the user can clear at any time, and falls back to
     * "unknown" until there are a few titles to learn from, which is the state a new installation
     * has to look right in.
     *
     * Read on the calling thread and handed to the selection as a value, so the selection itself
     * stays pure and the same day always produces the same banner.
     */
    private val viewerAffinity: ViewerAffinity
        get() =
            ViewerAffinity.from(
                historyEntries.map { entry -> entry.item.categoryIds },
            )

    val historyEntries: List<DesktopContinueWatchingEntry>
        get() {
            // Read so Compose re-runs this when an entry is forgotten; the store is not observable.
            @Suppress("UNUSED_EXPRESSION")
            continueWatchingRevision
            val profileId = activeProfileId ?: return emptyList()
            val lockedByType =
                mapOf(
                    XtreamContentType.MOVIE to lockedCategoryIdsForBrowsing(XtreamContentType.MOVIE),
                    XtreamContentType.SERIES to lockedCategoryIdsForBrowsing(XtreamContentType.SERIES),
                )
            val kidsMode = activeProfile?.isKids == true
            return playbackProgressCoordinator
                .history(profileId, HISTORY_LIMIT)
                .mapNotNull { progress ->
                    val item =
                        when (progress.identity.contentType) {
                            PlaybackContentType.MOVIE ->
                                xtreamRepository.itemByContentKey(
                                    XtreamContentType.MOVIE,
                                    progress.identity.contentId,
                                )
                            PlaybackContentType.EPISODE ->
                                progress.identity.seriesId?.let { seriesId ->
                                    xtreamRepository.itemByProviderId(XtreamContentType.SERIES, seriesId)
                                }
                        }
                    item
                        ?.takeIf { found ->
                            xtreamRepository.isAllowedForBrowsing(
                                found,
                                kidsMode,
                                lockedByType[found.contentType].orEmpty(),
                            )
                        }?.let { found -> DesktopContinueWatchingEntry(found, progress) }
                }
        }

    /** Forgets one title. The file and any download are untouched — this is only the record. */
    fun forgetHistoryEntry(entry: DesktopContinueWatchingEntry) {
        playbackProgressCoordinator.forget(entry.progress.identity)
        continueWatchingRevision += 1
    }

    /** Forgets everything this profile has watched. */
    fun clearHistory() {
        val profileId = activeProfileId ?: return
        playbackProgressCoordinator.clearHistory(profileId)
        continueWatchingRevision += 1
    }

    /** Opens the history section. */
    fun openHistory() {
        favoritesOnly = false
        destination = DesktopDestination.HISTORY
    }

    val continueWatchingEntries: List<DesktopContinueWatchingEntry>
        get() {
            // Read so Compose re-runs this when an entry is forgotten. The list comes from the
            // progress store, which is not observable, so without this the row stayed on screen.
            @Suppress("UNUSED_EXPRESSION")
            continueWatchingRevision
            val profileId = activeProfileId ?: return emptyList()
            val lockedByType =
                mapOf(
                    XtreamContentType.MOVIE to lockedCategoryIdsForBrowsing(XtreamContentType.MOVIE),
                    XtreamContentType.SERIES to lockedCategoryIdsForBrowsing(XtreamContentType.SERIES),
                )
            val kidsMode = activeProfile?.isKids == true
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
                    item
                        ?.takeIf { found ->
                            xtreamRepository.isAllowedForBrowsing(
                                found,
                                kidsMode,
                                lockedByType[found.contentType].orEmpty(),
                            )
                        }?.let { found -> DesktopContinueWatchingEntry(found, progress) }
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
        val profileId = activeProfileId
        val path = activeProfile?.musicPlaylistPath
        if (path.isNullOrBlank()) {
            musicLibrary = MusicLibrary.EMPTY
            return
        }
        val loaded =
            withContext(Dispatchers.IO) {
                runCatching {
                    applyCorrections(
                        musicLoader.load(Path.of(path)) ?: MusicLibrary.EMPTY,
                        profileId,
                    )
                }.getOrNull()
            }
        // A fast profile switch can leave the older disk read finishing last. Never publish that
        // library (or its corrections) into the profile that is now active.
        if (activeProfileId != profileId) return
        // Corrections are applied once, here, rather than at each place a name is displayed.
        // Anything downstream — shelves, artist grouping, search, the queue — then works from the
        // corrected names without knowing corrections exist, which is what stops a track appearing
        // under its tidy name on one screen and its filename on another.
        musicLibrary = loaded ?: MusicLibrary.EMPTY
        reloadMusicUserData()
    }

    /**
     * Overlays this profile's corrections onto a freshly parsed library.
     *
     * The artist and genre groupings are rebuilt rather than carried over, because they are derived
     * from the names: correcting "01 - Pink Floyd - Time.mp3" and leaving the groupings alone would
     * fix the track's label while still filing it under an artist called "01".
     *
     * Returns the library untouched when there is nothing to apply, so the ordinary case costs one
     * map lookup rather than a rebuild.
     */
    private fun applyCorrections(
        library: MusicLibrary,
        profileId: String? = activeProfileId,
    ): MusicLibrary {
        val corrections = correctionStore.correctionsFor(profileId)
        if (corrections.isEmpty() || library.isEmpty) return library

        var changed = false
        val tracks = library.tracks.map { track ->
            val correction = corrections[track.id] ?: return@map track
            changed = true
            track.copy(title = correction.title, artist = correction.artist)
        }
        if (!changed) return library

        return MusicLibrary(
            tracks = tracks,
            artists = MusicPlaylistMapper.artistsFrom(tracks),
            genres = MusicPlaylistMapper.genresFrom(tracks),
        )
    }

    /**
     * What a bulk tidy would change, without changing anything.
     *
     * Computed on demand rather than held, because it is only wanted while the workshop is open and
     * it changes with every correction the user makes.
     */
    fun musicTidyProposals(): List<MusicTidyProposal> =
        MusicTidying.proposalsFor(musicLibrary.tracks)

    /** Groups of tracks that appear to be the same recording, for the workshop's duplicate view. */
    fun musicDuplicateGroups(): List<List<MusicTrack>> =
        MusicTidying.duplicateGroups(musicLibrary.tracks)

    /** Groups sharing one stream address, which is a certainty rather than a judgement. */
    fun musicSameAddressGroups(): List<List<MusicTrack>> =
        MusicTidying.sameAddressGroups(musicLibrary.tracks)

    /** Applies one correction and rebuilds the library so every screen sees it at once. */
    suspend fun correctMusicTrack(
        trackId: String,
        title: String,
        artist: String?,
    ): Boolean {
        val clean = title.trim()
        if (clean.isBlank()) return false

        val profileId = activeProfileId
        val stored =
            withContext(Dispatchers.IO) {
                correctionStore.put(
                    profileId,
                    MusicCorrection(trackId = trackId, title = clean, artist = artist?.trim()?.takeIf(String::isNotBlank)),
                )
            }
        if (!stored || activeProfileId != profileId) return false
        rebuildMusicLibrary()
        return true
    }

    /** Applies every proposal from a tidy at once. */
    suspend fun applyMusicTidy(proposals: List<MusicTidyProposal>): Int {
        if (proposals.isEmpty()) return 0
        val profileId = activeProfileId
        val stored =
            withContext(Dispatchers.IO) {
                correctionStore.putAll(
                    profileId,
                    proposals.map { proposal ->
                        MusicCorrection(proposal.trackId, proposal.title, proposal.artist)
                    },
                )
            }
        if (activeProfileId != profileId) return stored
        rebuildMusicLibrary()
        return stored
    }

    /** Restores one track to whatever the playlist says. */
    suspend fun undoMusicCorrection(trackId: String) {
        val profileId = activeProfileId
        withContext(Dispatchers.IO) { correctionStore.remove(profileId, trackId) }
        if (activeProfileId != profileId) return
        rebuildMusicLibrary()
    }

    /** Undoes every correction, which is the way back from a tidy the user did not want. */
    suspend fun undoAllMusicCorrections() {
        val profileId = activeProfileId
        withContext(Dispatchers.IO) { correctionStore.clear(profileId) }
        if (activeProfileId != profileId) return
        rebuildMusicLibrary()
    }

    /** How many tracks currently carry a correction, for the workshop to report. */
    fun musicCorrectionCount(): Int = correctionStore.correctionsFor(activeProfileId).size

    /**
     * Re-reads the playlist and re-applies corrections.
     *
     * From disk rather than from memory: an undo has to recover the original name, and the only
     * place that still holds it is the file.
     */
    private suspend fun rebuildMusicLibrary() {
        val profileId = activeProfileId
        val path = activeProfile?.musicPlaylistPath ?: return
        val rebuilt =
            withContext(Dispatchers.IO) {
                runCatching {
                    musicLoader.load(Path.of(path))?.let { parsed ->
                        applyCorrections(parsed, profileId)
                    }
                }.getOrNull()
            } ?: return
        if (activeProfileId != profileId) return
        musicLibrary = rebuilt
        musicRevision += 1
    }

    /** Bumped whenever corrections change, so screens reading the library redraw. */
    var musicRevision by mutableStateOf(0)
        private set

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

    /**
     * True when the current shelf request failed *because TMDb rejected the key*.
     *
     * Separate from [streamingLoadFailed] so the screen can name the cause. Telling someone whose
     * playlist is loading 41.924 items to "check the connection" was the actual harm in BUG-021:
     * the connection was fine and the key was the thing to fix.
     */
    var streamingKeyRejected by mutableStateOf(false)
        private set

    /**
     * True only when the current shelf request failed, never for a valid empty catalogue.
     *
     * Clearing this always clears [streamingKeyRejected]: the reason only means anything while
     * there is a failure to explain, and leaving it set would keep accusing a key that has since
     * worked. Going through one setter is what guarantees the two cannot drift apart — there are
     * seven places that reset the failure, and remembering to reset both in each was a bug waiting
     * to be written.
     */
    var streamingLoadFailed: Boolean
        get() = streamingLoadFailedState
        private set(value) {
            streamingLoadFailedState = value
            if (!value) streamingKeyRejected = false
        }

    private var streamingLoadFailedState by mutableStateOf(false)
        private set

    /**
     * Synopsis for the title currently in the home banner, keyed by content type and provider id.
     *
     * The banner had a fixed sentence about the daily selection where the film's own description
     * belongs — the largest text on the screen said nothing about what the user was looking at. The
     * catalogue item carries no plot; it comes from a separate details call, so it is fetched in the
     * background and the banner shows the fixed line until it lands.
     */
    private var heroSynopsis by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    fun heroSynopsisFor(item: XtreamCatalogItem): String? =
        heroSynopsis[heroSynopsisKey(item.contentType, item.providerId)]

    /**
     * Fetches the synopsis for a banner title, once.
     *
     * Cached by content type plus provider id so the ten-second rotation does not re-fetch the same
     * five titles all day. Provider ids alone are unsafe because Xtream commonly reuses them across
     * film and series catalogues. Failures are silent: the banner falls back to its fixed line.
     */
    fun loadHeroSynopsis(item: XtreamCatalogItem) {
        // Series as well as films. Only films were fetched, so a series in the banner — which the
        // daily selection shows as often as a film — always fell back to the fixed line about the
        // selection itself, which reads as a description of the title and describes nothing.
        //
        // Live channels are excluded: a channel has no plot, and asking for one is a request that
        // can only fail.
        val fetch: suspend () -> String? =
            when (item.contentType) {
                XtreamContentType.MOVIE -> {
                    { xtreamRepository.movieDetails(item.providerId).plot }
                }
                XtreamContentType.SERIES -> {
                    { xtreamRepository.seriesDetails(item.providerId).plot }
                }
                else -> return
            }

        val cacheKey = heroSynopsisKey(item.contentType, item.providerId)
        if (heroSynopsis.containsKey(cacheKey)) return
        val requestGeneration = dailyHomeRequestGeneration

        streamingScope.launch {
            val plot =
                runCatching { withContext(Dispatchers.IO) { fetch() } }
                    .getOrNull()
                    ?.takeIf(String::isNotBlank)
                    ?: return@launch
            // A profile/provider/policy switch invalidates the Home while this network request is
            // running. Its old synopsis must not be published into the new catalogue afterwards.
            if (requestGeneration != dailyHomeRequestGeneration) return@launch
            // Bounded, for the same reason the cast photos are.
            //
            // Each entry is a full plot paragraph and the only thing that emptied this map was
            // changing source. The hero rotates, and a session left open for a day accumulates a
            // synopsis per title it ever showed — small individually, unbounded in aggregate.
            heroSynopsis =
                if (heroSynopsis.size >= MAX_HERO_SYNOPSES) {
                    heroSynopsis.entries.drop(heroSynopsis.size / 2).associate { it.key to it.value } +
                        (cacheKey to plot)
                } else {
                    heroSynopsis + (cacheKey to plot)
                }
        }
    }

    /** Which kind of title the shelves are showing. */
    var streamingKind by mutableStateOf(TmdbDiscoverKind.MOVIES)
        private set

    /**
     * Shelves already fetched, by kind, so switching back and forth is instant.
     *
     * Each kind is a full round of requests — one per service — and re-fetching on every tap of a
     * filter would make the buttons feel broken on a slow connection.
     */
    private val shelfCache =
        // Concurrent, not a plain HashMap. It is written from the loader coroutine on
        // Dispatchers.Default and read and cleared from the UI thread — switching filter, changing
        // region, pasting a new API key. Two threads on a HashMap can corrupt its internal table,
        // and the classic symptom is a lookup that never returns.
        java.util.concurrent.ConcurrentHashMap<TmdbDiscoverKind, List<TmdbServiceShelf>>()

    /**
     * The same shelves, kept between sessions.
     *
     * [shelfCache] lives only as long as the window, so every launch re-fetched the whole section —
     * one request per service, per kind — before anything could be shown, and threw the result away
     * on close. What a service is carrying changes over days, so a day-old answer is the right one
     * to open with while a fresh one is fetched behind it.
     */
    private val shelfDiskCache = StreamingShelfDiskCache()

    /**
     * Stops background work that outlives the window.
     *
     * [streamingScope] is deliberately not tied to a composable — leaving the screen mid-load must
     * not strand the section empty — which means nothing else ever cancels it. On close that leaves
     * TMDb requests in flight against a window that is gone, holding the process open until they
     * time out.
     */
    fun dispose() {
        streamingScope.cancel()
    }

    /** Switches the shelves between films, series and upcoming releases. */
    fun selectStreamingKind(kind: TmdbDiscoverKind) {
        if (kind == streamingKind) return
        streamingKind = kind
        // Shown at once if already fetched; otherwise the shelves empty and the loader fills them,
        // which reads as a load rather than as the previous kind lingering under a new label.
        streamingShelves = shelfCache[kind].orEmpty()
        streamingLoadFailed = false
        loadStreamingShelves()
    }

    var streamingLoading by mutableStateOf(false)
        private set

    /**
     * Which kind is being fetched, or null when nothing is.
     *
     * Paired with [streamingLoading] so a load in flight only blocks a *duplicate* of itself.
     * Guarding on the boolean alone meant that clicking Séries while Filmes was still loading threw
     * the click away, and nothing ever retried it — the tab simply stayed empty.
     */
    private var loadingKind: TmdbDiscoverKind? = null

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
            streamingLoadFailed = false
            return
        }
        val kind = streamingKind
        // A load already in flight for *this* kind is the only one worth skipping.
        //
        // This used to drop the request whenever anything was loading, which is exactly what
        // happens when someone opens Assinaturas and clicks Séries while Filmes is still fetching:
        // the click was swallowed, nothing retried it, and the tab stayed empty for the rest of the
        // session. The in-flight kind is tracked rather than a bare boolean so switching tabs
        // during a load still fetches the one the user asked for.
        if (streamingLoading && loadingKind == kind) {
            println("[streaming] $kind already loading, skipping")
            return
        }
        if (!force && shelfCache[kind]?.isNotEmpty() == true) {
            println("[streaming] $kind already cached, skipping")
            if (streamingKind == kind) streamingLoadFailed = false
            return
        }

        // Yesterday's answer, shown at once.
        //
        // Read before anything is fetched, so a returning user opens the section on a full page of
        // covers instead of on a spinner. The disk cache expires itself after a day, so a hit here
        // is by definition still current — there is nothing further to fetch.
        if (!force) {
            val region = streamingRegion
            val stored = shelfDiskCache.read(kind, region)
            if (!stored.isNullOrEmpty()) {
                println("[streaming] $kind restored ${stored.size} shelves from disk")
                shelfCache[kind] = stored
                if (streamingKind == kind) {
                    streamingShelves = stored
                    streamingLoadFailed = false
                }
                return
            }
        }
        println("[streaming] loading $kind shelves for region $streamingRegion")

        streamingLoading = true
        if (streamingKind == kind) streamingLoadFailed = false
        loadingKind = kind
        // Captured here, on the UI thread, for the same reason the kind is: both can change while
        // the request is in flight, and the cache is keyed on them.
        val requestedRegion = streamingRegion
        streamingScope.launch {
            val result =
                runCatching { catalogue.loadShelves(kind) }
                    .onFailure { error ->
                        // Printed rather than swallowed. An empty section and a crashed load look
                        // identical on screen, and this one hid a null scope for a whole build.
                        //
                        // The type only, never the message. TMDb takes the API key as a query
                        // parameter, and OkHttp puts the full request URL into its IOException
                        // messages — so printing `error.message` could put the user's own key on
                        // the console. The failure type is what distinguishes the cases anyone
                        // actually needs to tell apart.
                        println("[streaming] shelf load failed: ${error::class.simpleName}")
                    }.getOrDefault(TmdbShelfLoadResult.Unavailable())
            val unavailable = result as? TmdbShelfLoadResult.Unavailable
            val failed = unavailable != null
            val keyRejected = unavailable?.keyRejected == true
            val loaded = (result as? TmdbShelfLoadResult.Loaded)?.shelves.orEmpty()
            // The reason is logged too: "unavailable" alone cost a diagnosis round-trip with the
            // user, because a rejected key and an unreachable network look identical in the log.
            if (failed) {
                println("[streaming] shelf load unavailable (key rejected: $keyRejected)")
            }
            println("[streaming] loaded ${loaded.size} $kind shelves")
            if (!failed) shelfCache[kind] = loaded
            // Kept for tomorrow. The region is the one this request was issued for, read before the
            // suspend rather than after: a user who changes country mid-fetch would otherwise have
            // the old country's shelves written under the new country's name, and see the wrong
            // catalogue every launch for a day.
            shelfDiskCache.write(kind, requestedRegion, loaded)
            // Cleared unconditionally, before the early return below. It used to be cleared only on
            // the path that also assigned the shelves, so switching filter mid-load left the flag
            // set for ever — and the guard at the top of this function then refused every later
            // load, leaving the streaming section permanently empty for that session.
            streamingLoading = false
            if (loadingKind == kind) loadingKind = null
            // Only if the user has not switched filters while this was in flight — otherwise a slow
            // request would overwrite the shelves they are now looking at.
            //
            // The switched-to kind is fetched by its own call from selectStreamingKind, which the
            // guard at the top of this function no longer refuses.
            if (streamingKind != kind) return@launch
            // Assigned directly, not through withContext(Dispatchers.Main). Compose Desktop has no
            // Main dispatcher unless kotlinx-coroutines-swing is on the classpath, so that call
            // never ran its body: the shelves loaded, the log said so, and the screen stayed empty
            // because the state was never actually set. Snapshot state is safe to write from any
            // thread; every other loader in this class does the same.
            streamingShelves = loaded
            // Order matters: assigning the failure clears the reason, so the reason goes second.
            streamingLoadFailed = failed
            streamingKeyRejected = keyRejected
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
        // The stored ones too. They carry the old region in their header and would be rejected on
        // read anyway, but leaving them means a user who switches back and forth gets an answer for
        // a country they left — and the files would sit there unread for ever otherwise.
        shelfDiskCache.clear()
        streamingShelves = emptyList()
        streamingLoadFailed = false
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
        // Dropped before the new lookup starts, or the previous film's artwork shows under this
        // film's name for as long as the request takes.
        streamingPage = null
        destination = DesktopDestination.SUBSCRIPTIONS
        streamingLoading = true
        // Not a shelf load, so it claims no kind. Leaving loadingKind null means opening a title
        // cannot make the shelf loader think that kind is already in flight and refuse it.
        loadingKind = null

        streamingScope.launch {
            // The page and the availability are independent: a synopsis is worth showing even when
            // nobody can say where the film streams, and the reverse.
            streamingPage = runCatching { catalogue.pageFor(title) }.getOrNull()
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
     * Artwork, synopsis, cast and trailer for the title on screen.
     *
     * Null while loading and when TMDb knows nothing. The screen draws what it has rather than
     * waiting for everything — most of the page is worth showing without the rest.
     */
    var streamingPage by mutableStateOf<TmdbTitleDetails?>(null)
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
        runCatching {
            xtreamRepository.libraryMatchCandidates(
                kidsMode = activeProfile?.isKids == true,
                lockedCategoryIdsByContentType =
                    mapOf(
                        XtreamContentType.MOVIE to lockedCategoryIdsForBrowsing(XtreamContentType.MOVIE),
                        XtreamContentType.SERIES to lockedCategoryIdsForBrowsing(XtreamContentType.SERIES),
                    ),
            )
        }.getOrDefault(emptyList())

    /** Opens the Assinaturas area at its shelves, loading them if this is the first visit. */
    fun openSubscriptions() {
        favoritesOnly = false
        streamingOffers = OfferRanking.EMPTY
        selectedStreamingTitle = null
        // Cleared with the selection, or the previous film's artwork and synopsis would sit behind
        // the next one until its own lookup returned.
        streamingPage = null
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

    /**
     * Bumped whenever the parental lock changes, so the settings screen redraws.
     *
     * The preferences store is not observable state: everything below reads through it, so setting
     * a PIN or locking a category wrote to disk and changed nothing on screen. The switches simply
     * did not move.
     */
    var parentalRevision by mutableStateOf(0)
        private set

    /** Rejects parental refreshes started before a newer setting was saved. */
    private var parentalPolicyGeneration = 0L

    /**
     * The active profile's parental lock, as the policy consumes it.
     *
     * Read fresh rather than cached: switching profile must switch the lock, and a stale copy here
     * would leave a child's session governed by the parent's settings.
     */
    val parentalLock: ParentalLock
        get() {
            // Read so Compose re-runs this when the lock changes.
            @Suppress("UNUSED_EXPRESSION")
            parentalRevision
            return userStore.parentalLock(activeProfileId).let { stored ->
                ParentalLock(
                    lockedCategoryIds = explicitCategoryIds(xtreamContentType, stored.lockedCategoryIds),
                    lockAdultCategories = stored.lockAdultCategories,
                )
            }
        }

    /** Whether this profile has a PIN at all. Without one nothing can be asked for. */
    val hasParentalPin: Boolean
        get() {
            @Suppress("UNUSED_EXPRESSION")
            parentalRevision
            return userStore.parentalLock(activeProfileId).hasPin
        }

    /**
     * Category ids unlocked for this session.
     *
     * Held in memory only: entering the PIN opens a category until the app closes, and persisting
     * that would mean a lock that quietly stopped applying after the first time it was satisfied.
     */
    private val unlockedCategories = mutableStateListOf<String>()

    /**
     * Every category whose content must stay out of listings and search this session.
     *
     * A category the user has already unlocked is absent, which is the whole point of unlocking:
     * once the PIN has been given, its titles behave like any other.
     */
    fun lockedCategoryIdsForBrowsing(
        contentType: XtreamContentType = xtreamContentType,
    ): Set<String> {
        // Read once, not once per category. `hasParentalPin` and `parentalLock` each hit the Java
        // Preferences store and re-parse a packed string, and this runs on every page of the
        // catalogue — with a provider's several hundred categories that was several hundred
        // preference reads per keystroke in the search box.
        val stored = userStore.parentalLock(activeProfileId)
        if (!stored.hasPin) return emptySet()
        val lock = ParentalLock(lockAdultCategories = stored.lockAdultCategories)
        return xtreamRepository.categories(contentType)
            .filter { category ->
                val identity = CategoryPreferenceIdentity.scoped(contentType, category.providerId)
                identity !in unlockedCategories &&
                    (CategoryPreferenceIdentity.matches(stored.lockedCategoryIds, contentType, category.providerId) ||
                        lock.requiresPin(null, category.name))
            }.map(XtreamCategory::providerId)
            .toSet()
    }

    private fun explicitCategoryIds(
        contentType: XtreamContentType,
        storedIds: Set<String>,
    ): Set<String> =
        xtreamRepository.categories(contentType)
            .asSequence()
            .filter { category ->
                CategoryPreferenceIdentity.matches(storedIds, contentType, category.providerId)
            }.map(XtreamCategory::providerId)
            .toSet()

    /**
     * Channels chosen for the multiview grid, in the order they were added.
     *
     * Live only, and deliberately so. Four films at once is not something anyone wants; four
     * matches at once is the thing people buy a second screen for, and it is the one case where
     * running several engines is worth what it costs.
     *
     * Provider ids rather than items, so a catalogue refresh underneath does not leave the grid
     * holding stale copies.
     */
    var multiviewChannelIds by mutableStateOf<List<String>>(emptyList())
        private set

    /** Whether the grid is on screen. Separate from its contents, which survive closing it. */
    var multiviewOpen by mutableStateOf(false)
        private set

    /**
     * How many tiles this subscription can actually sustain.
     *
     * Providers cap simultaneous connections per account, and exceeding the cap does not produce an
     * error — the provider simply stops sending on the older streams. From inside the app that looks
     * exactly like tiles going black for no reason, which is what it looked like for days: four
     * channels started, two kept playing, two ended after about five seconds each.
     *
     * The provider reports both `max_connections` and `active_cons`. Four permitted connections
     * with two already in use on a television or phone leaves room for two tiles here, not four.
     * Ignoring the active count lets every tile start and then makes the provider turn older ones
     * black a few seconds later.
     *
     * Where the maximum is unknown, the app's own cap applies: no reliable subtraction is possible.
     */
    val multiviewCapacity: Int
        get() {
            val account = xtreamSummary?.account
            return availableMultiviewConnections(
                maximumConnections = account?.maximumConnections,
                activeConnections = account?.activeConnections,
            )
        }

    /**
     * Adds or removes a channel from the grid.
     *
     * Capped at four: beyond that each tile is too small to follow and the machine is running four
     * decoders for pictures nobody can read. Capped again at what the subscription allows, because
     * queueing a fifth stream a provider will refuse only produces a black rectangle.
     */
    fun toggleMultiviewChannel(providerId: String) {
        multiviewChannelIds =
            when {
                providerId in multiviewChannelIds -> multiviewChannelIds - providerId
                multiviewChannelIds.size >= multiviewCapacity -> multiviewChannelIds
                else -> multiviewChannelIds + providerId
            }
    }

    fun openMultiview() {
        // Logged because this has now failed to open for three separate reasons, each looking the
        // same from outside: the screen simply did not change. The counts say which stage lost it —
        // nothing queued, or queued but nothing resolvable.
        val queued = multiviewChannelIds.size
        val resolvable = multiviewTiles().size
        println("multiview: opening with $queued queued, $resolvable playable")

        // Opened whatever the state, so the overlay can explain itself.
        //
        // Returning early is what made this look dead: with nothing queued the button did nothing
        // at all, and the user had no way to learn that channels must be added first. The overlay
        // says so instead.
        multiviewOpen = true
    }

    fun closeMultiview() {
        multiviewOpen = false
    }

    /** Empties the grid, which also closes it: an empty multiview has nothing to show. */
    fun clearMultiview() {
        multiviewChannelIds = emptyList()
        multiviewOpen = false
    }

    /**
     * The grid's tiles, resolved to playable requests.
     *
     * Built on demand rather than stored: a channel that has left the catalogue since it was chosen
     * simply drops out, instead of leaving a tile that plays nothing.
     */
    fun multiviewTiles(): List<MultiviewTile> =
        multiviewChannelIds.mapNotNull { providerId ->
            val item =
                xtreamRepository.itemByProviderId(XtreamContentType.LIVE, providerId)
                    // Logged, because a tile that vanishes here takes the whole feature with it when
                    // every channel does the same: the overlay opens with nothing in it and the user
                    // sees a button that appears to do nothing. Only the reason, never the provider
                    // id's stream URL.
                    ?: run {
                        println("multiview: channel no longer in catalogue")
                        return@mapNotNull null
                    }
            val request =
                prepareXtreamPlayback(
                    XtreamPlaybackTarget.CatalogItem(
                        providerId = item.providerId,
                        contentType = item.contentType,
                        containerExtension = item.containerExtension,
                        contentKey = item.contentIdentity().key,
                    ),
                    item.name,
                    0L,
                ) ?: run {
                    println("multiview: could not resolve a stream for a queued channel")
                    return@mapNotNull null
                }
            MultiviewTile(providerId = item.providerId, request = request, title = item.name)
        }

    /** Whether [categoryId] is currently behind the PIN. */
    fun isCategoryLocked(
        categoryId: String?,
        categoryName: String?,
        contentType: XtreamContentType = xtreamContentType,
    ): Boolean {
        if (!hasParentalPin) return false
        val identity = categoryId?.let { id -> CategoryPreferenceIdentity.scoped(contentType, id) }
        if (identity != null && identity in unlockedCategories) return false
        val stored = userStore.parentalLock(activeProfileId)
        if (categoryId != null &&
            CategoryPreferenceIdentity.matches(stored.lockedCategoryIds, contentType, categoryId)
        ) {
            return true
        }
        return stored.lockAdultCategories && ParentalLock(lockAdultCategories = true).requiresPin(null, categoryName)
    }

    /**
     * Checks [pin] and, when it is right, unlocks [categoryId] for this session.
     *
     * Returns false for a wrong PIN and for a profile with none — the caller shows the same refusal
     * either way, because telling them apart would say whether a PIN exists.
     */
    fun unlockCategory(
        categoryId: String?,
        pin: String,
        contentType: XtreamContentType = xtreamContentType,
    ): Boolean {
        val stored = userStore.parentalLock(activeProfileId)
        val salt = stored.salt ?: return false
        val hash = stored.hash ?: return false
        if (!ParentalPin(salt, hash).matches(pin)) return false
        categoryId?.let { id ->
            val identity = CategoryPreferenceIdentity.scoped(contentType, id)
            if (identity !in unlockedCategories) unlockedCategories += identity
        }
        return true
    }

    /**
     * The category waiting on the PIN, or null when nothing is.
     *
     * Kept here rather than in a screen so the lock cannot be bypassed by reaching the catalogue
     * some other way: every path into a category goes through [selectXtreamCategory], and that is
     * where the question is asked.
     */
    var pendingPinCategory by mutableStateOf<XtreamCategory?>(null)
        private set

    /** Abandons the PIN prompt, leaving the category unopened. */
    fun dismissPinPrompt() {
        pendingPinCategory = null
    }

    /**
     * Answers the pending prompt.
     *
     * On success the category is unlocked for this session and opened; on failure the prompt stays
     * up so the attempt can be repeated, and the caller reports a wrong PIN.
     */
    suspend fun submitPendingPin(pin: String): Boolean {
        val category = pendingPinCategory ?: return false
        if (!unlockCategory(category.providerId, pin, category.contentType)) return false
        pendingPinCategory = null
        openXtreamCategory(category.providerId)
        return true
    }

    /**
     * Sets or changes the PIN.
     *
     * Returns false when [pin] is not four digits, or when a PIN already exists and [currentPin]
     * does not match it — changing a lock must require opening it first.
     */
    fun setParentalPin(
        pin: String,
        currentPin: String? = null,
    ): Boolean {
        val profileId = activeProfileId ?: return false
        val stored = userStore.parentalLock(profileId)

        if (stored.hasPin) {
            val salt = stored.salt ?: return false
            val hash = stored.hash ?: return false
            if (currentPin == null || !ParentalPin(salt, hash).matches(currentPin)) return false
        }

        val salt = java.util.UUID.randomUUID().toString()
        val created = ParentalPin.of(pin, salt) ?: return false
        userStore.setParentalLock(profileId, stored.copy(salt = created.salt, hash = created.hash))
        parentalRevision += 1
        // A newly created PIN activates the default adult-category rule immediately. Keeping the
        // already-rendered Home or catalogue here exposed exactly what the PIN had just protected.
        invalidateParentalBrowseSurfaces()
        return true
    }

    /** Removes the PIN and every category lock, once the current PIN is given. */
    fun clearParentalPin(currentPin: String): Boolean {
        val profileId = activeProfileId ?: return false
        val stored = userStore.parentalLock(profileId)
        val salt = stored.salt ?: return false
        val hash = stored.hash ?: return false
        if (!ParentalPin(salt, hash).matches(currentPin)) return false

        userStore.setParentalLock(profileId, StoredParentalLock())
        unlockedCategories.clear()
        parentalRevision += 1
        invalidateParentalBrowseSurfaces()
        return true
    }

    /** Locks or unlocks a category by id. Takes effect only while a PIN exists. */
    fun setCategoryLocked(
        categoryId: String,
        locked: Boolean,
        contentType: XtreamContentType = xtreamContentType,
    ) {
        val profileId = activeProfileId ?: return
        val stored = userStore.parentalLock(profileId)
        val migrated = CategoryPreferenceIdentity.migrateLegacy(stored.lockedCategoryIds)
        val identity = CategoryPreferenceIdentity.scoped(contentType, categoryId)
        val next = if (locked) migrated + identity else migrated - identity
        if (next == stored.lockedCategoryIds) return
        userStore.setParentalLock(profileId, stored.copy(lockedCategoryIds = next))
        // A category the user just unlocked in settings must not stay open from an earlier PIN
        // entry, and one they just locked must ask again immediately.
        unlockedCategories.remove(identity)
        parentalRevision += 1
        invalidateParentalBrowseSurfaces()
    }

    /** Whether adult-named categories are locked without being listed one by one. */
    fun setLockAdultCategories(enabled: Boolean) {
        val profileId = activeProfileId ?: return
        val stored = userStore.parentalLock(profileId)
        if (stored.lockAdultCategories == enabled) return
        userStore.setParentalLock(profileId, stored.copy(lockAdultCategories = enabled))
        unlockedCategories.clear()
        parentalRevision += 1
        invalidateParentalBrowseSurfaces()
    }

    /**
     * Removes every already-rendered surface before rebuilding it under the new parental policy.
     *
     * Clearing first is the security boundary. Reloading a filtered page afterwards is not enough:
     * on a large catalogue the previous Home, search result and details page can stay visible for
     * seconds while that work runs. Generations also stop an older in-flight Home/page request from
     * publishing its stale, less restrictive result after the settings change.
     */
    private fun invalidateParentalBrowseSurfaces() {
        val policyGeneration = ++parentalPolicyGeneration
        xtreamPageRequestGeneration += 1
        dailyHomeRequestGeneration += 1

        xtreamPage = XtreamCatalogPage.empty()
        selectedXtreamItemId = null
        dailySelectedItem = null
        pendingDetailsRequest = null
        pendingPinCategory = null
        seriesDetailsStatus = SeriesDetailsStatus.Idle
        movieDetailsStatus = MovieDetailsStatus.Idle
        liveEpgStatus = LiveEpgStatus.Idle
        selectedPerson = null
        movieAppearances.clear()
        dailyHomeStatus = DailyHomeStatus.Idle
        heroSynopsis = emptyMap()
        // History and Continue watching resolve items directly instead of reading xtreamPage. Their
        // getters now apply the policy too; this bump makes a visible row recompute immediately.
        continueWatchingRevision += 1

        if (xtreamSummary == null) return
        val contentType = xtreamContentType
        val rebuildHome = destination == DesktopDestination.HOME
        xtreamStatus = XtreamStatus.LoadingCatalog(contentType)
        streamingScope.launch {
            try {
                refreshXtreamPage(pageIndex = 0)
                if (rebuildHome) loadDailyHome()
                if (policyGeneration == parentalPolicyGeneration) {
                    xtreamStatus = XtreamStatus.Connected
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (policyGeneration == parentalPolicyGeneration) {
                    xtreamStatus = XtreamStatus.Error(error.toSafeFailureMessage())
                }
            }
        }
    }

    /**
     * Records which services this profile says it pays for.
     *
     * No screen calls this yet. When one does, it will need a revision counter like
     * [parentalRevision]: [streamingPreference] reads straight out of the preferences store, which
     * Compose does not watch, so the switches would not move. That exact omission shipped twice in
     * the settings screen — see [PreferenceRecompositionTest].
     */
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

    // -----------------------------------------------------------------------------------------
    // Shared links
    // -----------------------------------------------------------------------------------------

    /**
     * A share link waiting for a catalogue to resolve it against.
     *
     * A link almost always arrives before the catalogue is ready: Windows starts the process with
     * the URI on the command line, so it is in hand while the app is still authenticating. Holding
     * it here lets the resolution be retried once the catalogue lands, instead of being dropped
     * because it was early.
     */
    var pendingShareLink by mutableStateOf<TitleShareLink?>(null)
        private set

    /** What happened to the last incoming link, for the message shown to the recipient. */
    var shareLinkOutcome by mutableStateOf<ShareLinkOutcome?>(null)
        private set

    // -----------------------------------------------------------------------------------------
    // Receiving a title from a phone on the same network
    // -----------------------------------------------------------------------------------------

    private val castReceiver by lazy { CastReceiver(displayName = machineDisplayName()) }

    /** The code to show on screen while receiving is on, or null when it is off. */
    var castPairingCode by mutableStateOf<String?>(null)
        private set

    /**
     * Starts or stops listening for a phone.
     *
     * Off by default and never started implicitly. Everything else this app does reaches outwards;
     * this listens, and a feature that opens a socket should be one the user asked for rather than
     * one they discover they have been running.
     */
    fun toggleCastReceiver() {
        if (castPairingCode != null) {
            castReceiver.stop()
            castPairingCode = null
            return
        }
        castPairingCode =
            castReceiver.start { message ->
                // Resolved through the same path a shared link takes: both name a title rather than
                // a stream, and both have to find it in *this* machine's catalogue.
                //
                // `message.positionMillis` is deliberately dropped for now. That path opens the
                // title's page rather than starting playback, so there is nowhere to apply a resume
                // point yet; carrying it into a field nothing reads would look like a working
                // feature. The protocol already carries it, so honouring it later costs nothing
                // here. See BUG-020 in the queue.
                submitShareLink(
                    TitleShareLink(
                        identity = message.identity,
                        title = message.title,
                        year = null,
                        artworkUrl = null,
                        description = null,
                    ),
                )
            }
    }

    /** How this machine introduces itself to a phone looking for screens. */
    private fun machineDisplayName(): String =
        (System.getenv("COMPUTERNAME") ?: System.getProperty("user.name"))
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: "IPTV BURO"

    fun submitShareLink(link: TitleShareLink) {
        pendingShareLink = link
        resolvePendingShareLink()
    }

    fun clearShareLinkOutcome() {
        shareLinkOutcome = null
    }

    /**
     * Opens the shared title if this user's own catalogue has it.
     *
     * The link names a title, not a stream — see [TitleShareLink] — so "resolving" it means looking
     * up the sender's content identity in the recipient's list. A film the recipient's provider
     * does not carry is a perfectly ordinary outcome and is reported as such: there is nothing to
     * play, and nothing about the sender's source can help.
     *
     * Called both when a link arrives and after the catalogue loads, so it must be safe to run
     * repeatedly and with nothing pending.
     */
    fun resolvePendingShareLink() {
        val link = pendingShareLink ?: return
        // Nothing to search yet. The link stays pending for the caller that runs after loading.
        if (xtreamSummary == null) return

        // Both kinds are searched rather than just the one the key names. The identity's kind comes
        // from how the *sender's* provider classified the title, and providers disagree — a
        // mini-series filed under films by one list is a series in another.
        val resolved =
            listOf(XtreamContentType.MOVIE, XtreamContentType.SERIES, XtreamContentType.LIVE)
                .firstNotNullOfOrNull { contentType ->
                    xtreamRepository.itemByContentKey(contentType, link.identity.key)
                }

        pendingShareLink = null
        if (resolved == null) {
            shareLinkOutcome = ShareLinkOutcome.NotInYourList(link.title)
            return
        }
        destination = DesktopDestination.CATALOG
        favoritesOnly = false
        selectDailyItem(resolved)
        shareLinkOutcome = ShareLinkOutcome.Opened(resolved.name)
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
        //
        // The summary is asked of the repository when the cached copy is missing. It is null while
        // the catalogues have not been read yet — which is exactly the state left behind by a
        // failed load — and returning here made "Tentar novamente" a button that did nothing at
        // all: it set Idle, returned, and the same error card came straight back. A user with a
        // provider that failed once could not recover without restarting the app.
        val sourceId = xtreamSummary?.sourceId ?: xtreamRepository.summary()?.sourceId
        if (sourceId == null) {
            dailyHomeStatus = DailyHomeStatus.Idle
            return
        }
        val requestGeneration = ++dailyHomeRequestGeneration
        // Snapshot-backed state and preferences are captured before entering the IO dispatcher.
        // More importantly, one immutable policy is used for the whole Home build: a parental
        // change invalidates this generation instead of allowing a half-old snapshot to publish.
        val kidsMode = activeProfile?.isKids == true
        val lockedCategoriesByType =
            XtreamContentType.entries.associateWith(::lockedCategoryIdsForBrowsing)
        // Read here for the same reason as everything above it: `historyEntries` walks Compose
        // snapshot state and the parental policy, neither of which belongs on an IO dispatcher.
        val affinity = viewerAffinity
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
                val movies =
                    dailyPage(
                        XtreamContentType.MOVIE,
                        date.dayOfYear * 31 + date.year,
                        kidsMode,
                        lockedCategoriesByType.getValue(XtreamContentType.MOVIE),
                        18,
                    )
                val series =
                    dailyPage(
                        XtreamContentType.SERIES,
                        date.dayOfYear * 17 + date.year,
                        kidsMode,
                        lockedCategoriesByType.getValue(XtreamContentType.SERIES),
                        18,
                    )
                val live =
                    dailyPage(
                        XtreamContentType.LIVE,
                        date.dayOfYear * 7 + date.year,
                        kidsMode,
                        lockedCategoriesByType.getValue(XtreamContentType.LIVE),
                        14,
                    )
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
                                    categoryIds = item.categoryIds,
                                )
                            },
                        dayOfEpoch = date.toEpochDay(),
                        // What this profile has been watching, so the banner leans towards it.
                        //
                        // Captured on the calling thread with the rest of the inputs, and passed as
                        // a value: the selection stays pure, so the same day and the same catalogue
                        // always produce the same banner rather than reshuffling on recomposition.
                        affinity = affinity,
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
                    // What came out this year, scanned across the whole catalogue rather than a
                    // page. A provider back-fills older films constantly, so "recently added" and
                    // "new" are different questions — this answers the second.
                    releasesThisYear =
                        xtreamRepository.releasesForYear(
                            XtreamContentType.MOVIE,
                            date.year,
                            18,
                            kidsMode,
                            lockedCategoriesByType.getValue(XtreamContentType.MOVIE),
                        ),
                    seriesThisYear =
                        xtreamRepository.releasesForYear(
                            XtreamContentType.SERIES,
                            date.year,
                            18,
                            kidsMode,
                            lockedCategoriesByType.getValue(XtreamContentType.SERIES),
                        ),
                    movies = movies,
                    series = series,
                    live = live,
                    seasonal = seasonalShelf(date, kidsMode, lockedCategoriesByType),
                ) to latestSummary
            }
        }.onSuccess { (snapshot, latestSummary) ->
            if (requestGeneration != dailyHomeRequestGeneration) return@onSuccess
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
                // Kept for the *next* launch's loading screen. Storing them now, from a home that
                // is already built, is the only way the backdrop can be on screen during a wait
                // rather than after it.
                userStore.setBackdropPosters(
                    (snapshot.movies + snapshot.series)
                        .mapNotNull { item -> item.artworkUrl?.takeIf(String::isNotBlank) }
                        .distinct(),
                )
            }
        }
            .onFailure { error ->
                // A request invalidated by a profile or parental-policy change owns none of the
                // current state. In particular, it must not clear the newer request's Loading flag.
                if (requestGeneration != dailyHomeRequestGeneration) {
                    error.rethrowIfCancellation()
                    return@onFailure
                }
                // Cleared *before* the rethrow, not after: a rethrow leaves this lambda immediately,
                // so anything below it never runs on the cancellation path.
                //
                // This is the worst instance of the pattern the film, series, EPG and import loaders
                // all had. A cancelled build of the home — which happens whenever the composable
                // that launched it goes away — left the first screen of the app showing its skeleton
                // with nothing in flight and no way back until a restart.
                if (dailyHomeStatus is DailyHomeStatus.Loading) dailyHomeStatus = DailyHomeStatus.Idle
                error.rethrowIfCancellation()
                // Logged, because the card on screen says almost nothing useful.
                //
                // A customer hit this on a real install and the log held no trace of it at all —
                // the success path reports its counts, the failure path reported nothing, so the
                // one run that needed explaining was the one that left none. The type and the
                // Xtream reason are recorded; the message is not, because OkHttp puts the full
                // request URL into its IOException text and that URL carries the credentials.
                val reason = (error as? XtreamClientException)?.reason?.name ?: "-"
                println("BURO home FAILED: ${error::class.simpleName} reason=$reason")
                dailyHomeStatus = DailyHomeStatus.Error(error.toSafeFailureMessage())
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
        lockedCategoryIdsByType: Map<XtreamContentType, Set<String>>,
    ): DailySeasonalShelf? {
        val collection = SeasonalCollections.primaryCollectionFor(date) ?: return null
        val found = LinkedHashMap<String, XtreamCatalogItem>()
        for (term in collection.searchTerms) {
            for (type in listOf(XtreamContentType.MOVIE, XtreamContentType.SERIES)) {
                xtreamRepository
                    .page(
                        type,
                        null,
                        term,
                        0,
                        pageSize = SEASONAL_TERM_PAGE_SIZE,
                        kidsMode = kidsMode,
                        lockedCategoryIds = lockedCategoryIdsByType[type].orEmpty(),
                    )
                    .items
                    // Keyed so the same film shipped as "HD" and "4K" does not take two slots on a
                    // shelf that only has room for a handful — and likewise for the channel
                    // prefixes and language tags that the weaker key used to let through.
                    .forEach { item -> found.putIfAbsent(item.name.shelfDeduplicationKey(), item) }
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
        lockedCategoryIds: Set<String>,
        pageSize: Int,
    ): List<XtreamCatalogItem> {
        val fetchSize = (pageSize * 4).coerceAtMost(80)
        val first =
            xtreamRepository.page(
                type,
                null,
                "",
                0,
                pageSize = fetchSize,
                kidsMode = kidsMode,
                lockedCategoryIds = lockedCategoryIds,
            )
        val pageIndex = rotatingPageIndex(seed, first.pageCount)
        val candidates = if (pageIndex == 0) first.items else {
            xtreamRepository.page(
                type,
                null,
                "",
                pageIndex,
                pageSize = fetchSize,
                kidsMode = kidsMode,
                lockedCategoryIds = lockedCategoryIds,
            ).items
        }
        // The same key the releases shelf uses. `editorialCatalogKey` did a weaker version of this
        // job — no accents, no pipes, no trailing single-letter tags — so the daily shelves carried
        // the same duplicate pairs that were reported on Lançamentos, just less visibly.
        return candidates.distinctBy { it.name.shelfDeduplicationKey() }.take(pageSize)
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
            // Reset before rethrowing, for the same reason as the film, series and EPG loaders: a
            // cancelled import — closing the dialog, or the screen that launched it going away —
            // otherwise left importStatus on Loading, and the guard at the top of this function
            // then refused every later import. The user was left with a spinner and an app that
            // silently ignored the file picker.
            if (importStatus is ImportStatus.Loading) importStatus = ImportStatus.Idle
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
                }.onFailure { error ->
                    // Swallowed on purpose — this must not fail a working login — but no longer
                    // silently. A user whose session was never persisted starts the next session
                    // with a catalogue on disk and no credentials, and every action that needs the
                    // provider then fails in a way that reads as their list being broken. The type
                    // only: the message can name the file, and the DPAPI blob's path is not worth
                    // putting in a log that gets pasted into chats.
                    println("BURO session not remembered: ${error::class.simpleName}")
                }
                xtreamSummary = summary
                selectedSourceId = summary.sourceId
                xtreamContentType = XtreamContentType.LIVE
                xtreamCategories = visibleXtreamCategories(XtreamContentType.LIVE)
                selectedXtreamCategoryId = null
                selectedXtreamYear = null
                xtreamSearchQuery = ""
                xtreamPage =
                    xtreamRepository.page(
                        XtreamContentType.LIVE,
                        null,
                        "",
                        0,
                        kidsMode = activeProfile?.isKids == true,
                        lockedCategoryIds = lockedCategoryIdsForBrowsing(),
                    )
                selectedXtreamItemId = xtreamPage.items.firstOrNull()?.providerId
                xtreamStatus = XtreamStatus.Connected
            }.onFailure { error ->
                input.clear()
                // The same reset the other loaders needed, and the most damaging place to omit it:
                // the guard at the top of this function returns early while the status is
                // Connecting, so a cancelled connection left the app permanently unable to connect
                // to anything — every later attempt returned immediately and silently.
                //
                // Disconnected rather than Idle, because that is this status's resting state: the
                // attempt did not happen, and there is no session.
                if (xtreamStatus is XtreamStatus.Connecting) xtreamStatus = XtreamStatus.Disconnected
                error.rethrowIfCancellation()
                xtreamRepository.clear()
                clearXtreamUiState()
                if (sourceSummaries.none { source -> source.id == selectedSourceId }) {
                    selectedSourceId = catalogs.firstOrNull()?.source?.id
                }
                xtreamStatus = XtreamStatus.Error(error.toSafeFailureMessage())
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

    /**
     * Whether this launch is the first that will read a catalogue on this machine.
     *
     * Read once at construction, not on every recomposition: the flag is written when the load
     * finishes, and re-reading it would make the explanation vanish from under the user's eyes at
     * the exact moment the wait ends.
     */
    val isFirstStartup: Boolean = !userStore.hasCompletedFirstStartup()

    /**
     * Artwork for the loading screen's backdrop.
     *
     * Read from the previous session, not from this one. The backdrop has to be on screen *during*
     * the wait, and at that moment nothing is loaded yet — a value derived from the current home
     * would only arrive once the home was built, which is precisely when the loading screen goes
     * away. Written at the end of each load for the next launch to use.
     *
     * Empty on a true first run, when the wall uses the bundled fictional artwork.
     */
    val backdropPosters: List<String> = userStore.backdropPosters()

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
                // Only here, where a catalogue really did load. Marking it in the `finally` would
                // count a failed connection as a completed first run, and the user who then fixed
                // their credentials would never get the explanation they were owed.
                userStore.markFirstStartupComplete()
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
                    // forceRefresh, or this would answer from the disk cache and the button would
                    // appear to do nothing — the user pressed it precisely because they want what
                    // the provider has now.
                    latest = xtreamRepository.loadCatalog(contentType, forceRefresh = true)
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
            xtreamStatus = XtreamStatus.Error(error.toSafeFailureMessage())
        }
    }

    suspend fun selectXtreamContentType(contentType: XtreamContentType) {
        if (contentType == xtreamContentType && contentType in xtreamSummary?.loadedContentTypes.orEmpty()) {
            return
        }
        if (xtreamStatus is XtreamStatus.LoadingCatalog) return

        // Only when something is actually going to be fetched.
        //
        // This was set unconditionally, so switching between films and series with both catalogues
        // already in memory raised the loading banner and dropped it again within a frame — the
        // flicker reported on the favourites screen. Nothing was loading; the app was only saying
        // so. A progress indicator that appears when there is no progress teaches the viewer that
        // it means nothing.
        val alreadyLoaded = contentType in xtreamSummary?.loadedContentTypes.orEmpty()
        if (!alreadyLoaded) xtreamStatus = XtreamStatus.LoadingCatalog(contentType)

        runCatching {
            val summary =
                if (alreadyLoaded) {
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
            xtreamStatus = XtreamStatus.Error(error.toSafeFailureMessage())
        }
    }

    /**
     * Opens a category, asking for the PIN first when one guards it.
     *
     * The check lives here rather than in the rail because this is the single door into a
     * category: a locked one must not open and then hide itself, since the page underneath has
     * already loaded by then and the titles have already been seen.
     */
    suspend fun selectXtreamCategory(categoryId: String?) {
        val locked =
            categoryId?.let { id ->
                xtreamRepository.categories(xtreamContentType).firstOrNull { it.providerId == id }
            }?.takeIf(::categoryNeedsPin)
        if (locked != null) {
            pendingPinCategory = locked
            return
        }
        openXtreamCategory(categoryId)
    }

    /** Opens a category with no questions asked. Every caller must have cleared the lock first. */
    private suspend fun openXtreamCategory(categoryId: String?) {
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
                val nowSeconds = System.currentTimeMillis() / 1_000L
                val (now, next) = epg.nowAndNext(nowSeconds)
                liveEpgStatus =
                    LiveEpgStatus.Loaded(
                        now = now,
                        next = next,
                        // Everything still ahead, in order. What has already finished is dropped:
                        // a schedule that opens on this morning's programmes makes the viewer
                        // scroll to reach the part they are asking about.
                        schedule =
                            epg.programs
                                .filter { program ->
                                    (program.endEpochSeconds ?: Long.MAX_VALUE) > nowSeconds
                                }
                                .sortedBy { program -> program.startEpochSeconds ?: Long.MAX_VALUE },
                    )
            }
        }.onFailure { error ->
            // Same reason as the film and series loaders: a cancelled fetch must not leave the
            // status on Loading, or the guard above refuses every retry.
            if (liveEpgStatus is LiveEpgStatus.Loading) liveEpgStatus = LiveEpgStatus.Idle
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
            // Reset before rethrowing. A coroutine cancelled mid-fetch — which happens whenever the
            // effect that started it recomposes, and reliably when the page is opened from
            // Assinaturas — used to leave the status on Loading. The guard at the top then refused
            // every retry, so the page kept its spinner with nothing in flight.
            if (movieDetailsStatus is MovieDetailsStatus.Loading) movieDetailsStatus = MovieDetailsStatus.Idle
            error.rethrowIfCancellation()
            movieDetailsStatus = MovieDetailsStatus.Error(error.toSafeFailureMessage())
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
            // Same reason as the film loader above: a cancelled fetch must not strand the status on
            // Loading, or the guard refuses every retry and the page keeps a spinner for ever.
            if (seriesDetailsStatus is SeriesDetailsStatus.Loading) seriesDetailsStatus = SeriesDetailsStatus.Idle
            error.rethrowIfCancellation()
            seriesDetailsStatus = SeriesDetailsStatus.Error(error.toSafeFailureMessage())
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

    /**
     * Where playback had reached, for restarting the engine without losing the viewer's place.
     *
     * Changing the speaker layout rebuilds the VLC process, because the audio chain is constructed
     * with the rest of the pipeline. Restarting from zero would throw somebody back to the opening
     * titles for changing a setting, so the stored checkpoint — written every twelve seconds
     * already — is reused rather than a new mechanism invented for this.
     *
     * Zero when nothing is stored, which starts the title from the beginning: the honest answer
     * when there is no record of a position.
     */
    fun lastCheckpointMillis(request: DesktopPlaybackRequest): Long =
        (playbackProgressCoordinator.resumeDecision(request.progressIdentity) as? ResumeDecision.ResumeFrom)
            ?.positionMs
            ?: 0L

    /**
     * Speaker layout for playback, remembered per profile.
     *
     * Defaults to [AudioOutputMode.SYSTEM] — leave the sound card alone — because asking for more
     * speakers than Windows is configured for can silence playback rather than improve it.
     */
    var audioOutput by mutableStateOf(userStore.audioOutput(activeProfileId))
        private set

    fun selectAudioOutput(mode: AudioOutputMode) {
        audioOutput = mode
        activeProfileId?.let { profileId -> userStore.setAudioOutput(profileId, mode) }
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
     * Hands a share destination to the browser or mail client.
     *
     * The scheme is checked rather than trusted. Every caller today builds its own URL from a
     * constant prefix, but this is the one function that turns a string into something the OS
     * launches, and `file:` or a custom scheme reaching it would be a way to start an arbitrary
     * program. https and mailto are the only two the share sheet needs.
     */
    fun openPublicUrl(url: String): ExternalOpenResult {
        val scheme = url.substringBefore(':', "").lowercase(Locale.ROOT)
        if (scheme != "https" && scheme != "mailto") return ExternalOpenResult.Refused
        return runCatching { openUriExternally(URI(url)) }
            .getOrDefault(ExternalOpenResult.Failed)
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
                                    id = credit.id,
                                    isSeries = credit.isSeries,
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
        val kidsMode = activeProfile?.isKids == true
        val lockedByType =
            mapOf(
                XtreamContentType.MOVIE to lockedCategoryIdsForBrowsing(XtreamContentType.MOVIE),
                XtreamContentType.SERIES to lockedCategoryIdsForBrowsing(XtreamContentType.SERIES),
            )

        val found =
            withContext(Dispatchers.Default) {
                // Films first, then series: a credit does not say which it is, and a viewer looking
                // for "Narcos" wants the series rather than a documentary of the same name.
                sequenceOf(XtreamContentType.MOVIE, XtreamContentType.SERIES)
                    .mapNotNull { type ->
                        xtreamRepository
                            .page(
                                type,
                                null,
                                wanted,
                                0,
                                pageSize = 20,
                                kidsMode = kidsMode,
                                lockedCategoryIds = lockedByType[type].orEmpty(),
                            )
                            .items
                            .firstOrNull { item ->
                                item.name.editorialCatalogTitle().lowercase(Locale.ROOT).trim() == wanted
                            }
                    }.firstOrNull()
            } ?: return false

        closePerson()
        // The item itself, not just its id.
        //
        // `selectXtreamItem` clears `dailySelectedItem` and then relies on the title being present
        // in the current catalogue page — and `selectedXtreamItem` returns null outright while the
        // destination is Home. So from the Home screen the selection resolved to nothing, the
        // details branch was never taken, and the press dropped the user back to the start with the
        // log cheerfully reporting success.
        //
        // `selectDailyItem` sets both halves, which is what every working path already uses.
        selectDailyItem(found)
        return true
    }

    /**
     * Opens a credit from a filmography: in this playlist if it is here, otherwise in Assinaturas.
     *
     * Previously a credit the playlist did not carry did nothing at all. The click searched a
     * 41,000-item catalogue on a background thread with no indication on screen, then set a
     * "missing" value that nothing ever read — so the app appeared to freeze and then to ignore the
     * press. Both halves of that were wrong.
     *
     * A person's filmography is mostly films the user does not have; that is the ordinary case, not
     * the failure. Sending those to Assinaturas answers the question they were actually asking —
     * where can I watch this — and the library check inside that screen still puts "you already have
     * this" first when it applies.
     */
    suspend fun openCredit(credit: PersonCredit): CreditDestination {
        // Logged because this has now failed twice for reasons that looked identical from outside:
        // the screen simply changed to something the user did not ask for. Each line below names
        // which guard stopped it — the title only, never an address.
        if (openTitleFromCredit(credit.title)) {
            println("credit: opened from playlist")
            // The caller has to open its own details page. Selecting the item is not the same as
            // showing it: `detailsOpen` is a flag inside each screen, and setting the selection
            // without it left the user on whatever was underneath — the Home.
            return CreditDestination.PLAYLIST_ITEM
        }

        // Not in this playlist. Ask where it can be watched instead of stopping here.
        //
        // Both are needed: without an id there is nothing to look up, and without a configured
        // catalogue there is nowhere to ask. Either way the press does nothing rather than opening
        // an empty screen — which is the one case this function cannot improve on.
        val tmdbId = credit.id
        if (tmdbId == null) {
            println("credit: no catalogue id, nothing to look up")
            return CreditDestination.NOWHERE
        }
        if (streamingCatalogue == null) {
            println("credit: no streaming catalogue — metadata key missing or blank")
            return CreditDestination.NOWHERE
        }
        val external =
            ExternalTitle(
                id =
                    ExternalContentId(
                        namespace = if (credit.isSeries) TMDB_SERIES_NAMESPACE else TMDB_NAMESPACE,
                        value = tmdbId.toString(),
                    ),
                title = credit.title,
                kind = if (credit.isSeries) ExternalTitleKind.SERIES else ExternalTitleKind.MOVIE,
                year = credit.year,
                posterUrl = credit.posterUrl,
                isDemo = false,
            )
        closePerson()
        openStreamingTitle(external)
        return CreditDestination.SUBSCRIPTIONS
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
        // Including the disk copy: the user is deliberately signing out, and leaving their whole
        // catalogue cached would be a surprise. The ordinary `clear()` runs on every sign-in and
        // must not touch it.
        xtreamRepository.clearIncludingDiskCache()
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
        // Read here with the rest of the inputs, not inside the worker below. It reads
        // `unlockedCategories` — a Compose snapshot list — and the user's preferences, and neither
        // belongs on a background dispatcher: every other input to this call is captured on the
        // calling thread for exactly that reason.
        val lockedCategories = lockedCategoryIdsForBrowsing()
        val kidsMode = activeProfile?.isKids == true
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
                    kidsMode = kidsMode,
                    lockedCategoryIds = lockedCategories,
                )
            }
        if (requestGeneration == xtreamPageRequestGeneration) {
            xtreamPage = page
            selectedXtreamItemId = page.items.firstOrNull()?.providerId
        }
    }

    private fun clearXtreamUiState() {
        xtreamPageRequestGeneration += 1
        dailyHomeRequestGeneration += 1
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
        heroSynopsis = emptyMap()
        unlockedCategories.clear()
    }

    /**
     * The categories this profile may see, in order of how absolute each rule is.
     *
     * A Kids profile removes adult categories outright — the child never learns they exist. A
     * hidden category is the user's own tidying and is equally gone. A PIN-locked one stays
     * visible: the point is that a parent can reach it and a child cannot, and hiding it would make
     * the parent's own catalogue smaller.
     */
    private fun visibleXtreamCategories(contentType: XtreamContentType): List<XtreamCategory> {
        val hidden = userStore.hiddenCategories(activeProfileId)
        return xtreamRepository
            .categories(contentType)
            .let { categories ->
                if (activeProfile?.isKids == true) {
                    categories.filterNot { FamilyContentPolicy.isExplicitAdultLabel(it.name) }
                } else {
                    categories
                }
            }.filterNot { category ->
                CategoryPreferenceIdentity.matches(hidden, contentType, category.providerId)
            }
    }

    /**
     * Whether opening [category] needs the PIN.
     *
     * Consulted by the catalogue before it pages: a locked category shows its name and asks, rather
     * than listing what is inside.
     */
    fun categoryNeedsPin(category: XtreamCategory): Boolean =
        isCategoryLocked(category.providerId, category.name, category.contentType)

    /**
     * How subtitles are drawn on the next title played.
     *
     * Read from three stored strings rather than one object so a value written by a later build
     * falls back to the default instead of discarding the whole setting.
     */
    var subtitleStyle by mutableStateOf(
        userStore.subtitleStyle().let { (size, colour, background) ->
            SubtitleStyle(
                size = runCatching { SubtitleSize.valueOf(size) }.getOrDefault(SubtitleSize.MEDIUM),
                textColour = runCatching { SubtitleColour.valueOf(colour) }.getOrDefault(SubtitleColour.WHITE),
                background = background,
            )
        },
    )
        private set

    fun changeSubtitleStyle(style: SubtitleStyle) {
        subtitleStyle = style
        userStore.setSubtitleStyle(style.size.name, style.textColour.name, style.background)
    }

    /** Whether the clock reads 24-hour. */
    var uses24HourClock by mutableStateOf(userStore.uses24HourClock())
        private set

    /** Named `change…` because `set…` collides on the JVM with the property's own setter. */
    fun changeClockFormat(use24Hour: Boolean) {
        uses24HourClock = use24Hour
        userStore.setUses24HourClock(use24Hour)
    }

    /**
     * Bumped whenever a category is hidden or restored, so the settings list redraws.
     *
     * The preferences store is not observable state, so writing to it changes nothing Compose is
     * watching. Hiding a category appeared to work only because it also rewrote `xtreamCategories`
     * — which is observed — and that recomposition happened to refresh the row; restoring one wrote
     * the same unobserved store and redrew nothing, so the button did nothing at all.
     */
    var hiddenCategoriesRevision by mutableStateOf(0)
        private set

    /** Categories this profile has chosen to hide, for the settings list. */
    val hiddenCategoryIds: Set<String>
        get() {
            return hiddenCategoryIdsForSettings(xtreamContentType)
        }

    fun hiddenCategoryIdsForSettings(
        contentType: XtreamContentType,
    ): Set<String> {
        @Suppress("UNUSED_EXPRESSION")
        hiddenCategoriesRevision
        return explicitCategoryIds(contentType, userStore.hiddenCategories(activeProfileId))
    }

    fun lockedCategoryIdsForSettings(contentType: XtreamContentType): Set<String> {
        @Suppress("UNUSED_EXPRESSION")
        parentalRevision
        return explicitCategoryIds(contentType, userStore.parentalLock(activeProfileId).lockedCategoryIds)
    }

    /**
     * Every category of the section currently open, hidden ones included.
     *
     * The settings list must show what is hidden, or hiding one removes it from the only place it
     * could be restored from — which is exactly what happened: a hidden category vanished from its
     * own switch and could never be brought back.
     */
    val allCategoriesForSettings: List<XtreamCategory>
        get() = categoriesForSettings(xtreamContentType)

    /**
     * Every category of one section, hidden ones included.
     *
     * Settings shows all three sections rather than only whichever happens to be open. Reading the
     * current section meant a user in Filmes could not reach a series category at all — the switch
     * for it simply was not on the screen, and there was nothing to say why.
     */
    fun categoriesForSettings(contentType: XtreamContentType): List<XtreamCategory> =
        xtreamRepository.categories(contentType).let { categories ->
            // A Kids profile still never sees adult categories, even here: the point of that
            // profile is that the content is not present, and a settings list is not an
            // exception to it.
            if (activeProfile?.isKids == true) {
                categories.filterNot { FamilyContentPolicy.isExplicitAdultLabel(it.name) }
            } else {
                categories
            }
        }

    /** Hides or restores a category. Hidden ones vanish from the rail and from paging. */
    fun setCategoryHidden(
        categoryId: String,
        hidden: Boolean,
        contentType: XtreamContentType = xtreamContentType,
    ) {
        val profileId = activeProfileId ?: return
        val current = CategoryPreferenceIdentity.migrateLegacy(userStore.hiddenCategories(profileId))
        val identity = CategoryPreferenceIdentity.scoped(contentType, categoryId)
        userStore.setHiddenCategories(
            profileId,
            if (hidden) current + identity else current - identity,
        )
        // The rail is built from this, so it has to be rebuilt for the change to show.
        xtreamCategories = visibleXtreamCategories(xtreamContentType)
        // And the settings list reads the preferences store directly, which Compose does not watch.
        hiddenCategoriesRevision += 1
        if (contentType == xtreamContentType && selectedXtreamCategoryId == categoryId && hidden) {
            selectedXtreamCategoryId = null
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

    /**
     * Last, so every property above it is initialised.
     *
     * The per-profile TMDb key cannot be read where it is declared: `activeProfileId` is declared
     * further down the class and does not exist yet at that point — the compiler says so. Reading
     * it here, once construction is complete, is the only correct place.
     */
    init {
        profileMetadataApiKey = userStore.profileMetadataApiKey(activeProfileId).orEmpty()
        // And the clients, which were built from the shared key alone before the profile was known.
        if (profileMetadataApiKey.isNotBlank()) rebuildMetadataClients()
    }

    private fun Throwable.rethrowIfCancellation() {
        if (this is CancellationException) throw this
    }

    /**
     * The user-facing text for a failure.
     *
     * The mapping itself lives in [FailureMessages], where it is tested. It has misled twice on
     * real installations by blaming the customer's provider for faults that were not theirs, so it
     * is worth a test rather than a comment promising care.
     */
    private fun Throwable.toSafeFailureMessage(): String =
        FailureMessages.forFailure(this, DiagnosticLog.location().toString())

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
/**
 * Where a press on a credit ended up, so the screen can finish the job.
 *
 * The state alone cannot: showing a title from the playlist means setting a `detailsOpen` flag that
 * lives inside each screen, and the state has no access to it. Returning the outcome is what lets
 * the caller open its own page — without this, selecting the item silently left the user on
 * whatever was underneath, which was the Home.
 */
enum class CreditDestination {
    /** Found in the user's own playlist; the caller opens its details page. */
    PLAYLIST_ITEM,

    /** Not in the playlist; Assinaturas is now showing where it can be watched. */
    SUBSCRIPTIONS,

    /** Nothing could be done — no catalogue id, or no metadata key configured. */
    NOWHERE,
}

data class PersonCredit(
    /**
     * TMDb's own id, so the credit can be opened even when the playlist does not carry it.
     *
     * Null when the metadata response omitted it; the credit still lists and still matches against
     * the playlist by title.
     */
    val id: Int?,
    /** Films and series are numbered separately, so the kind is part of the identity. */
    val isSeries: Boolean,
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

enum class DesktopDestination { HOME, CATALOG, FAVORITES, DOWNLOADS, CONTINUE, MUSIC, SUBSCRIPTIONS, HISTORY }

/**
 * What became of a shared link that this app was asked to open.
 *
 * [NotInYourList] is a normal result, not an error. A share carries a title, and whether that title
 * exists is a fact about the recipient's own provider — so the message says the list does not have
 * it rather than suggesting something went wrong.
 */
sealed interface ShareLinkOutcome {
    data class Opened(val title: String) : ShareLinkOutcome

    data class NotInYourList(val title: String) : ShareLinkOutcome
}

/**
 * How far back the history goes.
 *
 * Bounded because every entry is a preferences read and a catalogue lookup; two hundred covers what
 * anyone scrolls to and keeps the section instant.
 */
private const val HISTORY_LIMIT = 200

/**
 * Channels the multiview grid holds at once.
 *
 * Four is the practical ceiling on a single screen: beyond that each tile is too small to follow,
 * and the machine is decoding streams nobody can read. It is also the layout the grid is built for.
 */
internal const val MAX_MULTIVIEW_TILES = 4

/**
 * Connections this Windows session can still open without exceeding the provider's account limit.
 *
 * At least one tile remains available when a panel reports an inconsistent or stale count. Xtream
 * panels commonly lag for a few seconds after a stream closes; turning the entire feature off on
 * `active_cons == max_connections` would trap the user even after that connection had gone away.
 */
internal fun availableMultiviewConnections(
    maximumConnections: Int?,
    activeConnections: Int?,
    appLimit: Int = MAX_MULTIVIEW_TILES,
): Int {
    val safeAppLimit = appLimit.coerceAtLeast(1)
    val providerLimit = maximumConnections ?: return safeAppLimit
    val normalizedLimit = providerLimit.coerceAtLeast(1)
    val alreadyInUse = activeConnections?.coerceIn(0, normalizedLimit) ?: 0
    return (normalizedLimit - alreadyInUse).coerceIn(1, safeAppLimit)
}

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
    /**
     * Films released this year, newest catalogue-wide rather than page-wide.
     *
     * Empty when the provider carries nothing from this year, in which case the rail is not drawn —
     * a heading over an empty row reads as a fault.
     */
    val releasesThisYear: List<XtreamCatalogItem> = emptyList(),
    val seriesThisYear: List<XtreamCatalogItem> = emptyList(),
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

// editorialCatalogKey lived here and is gone: every shelf now uses shelfDeduplicationKey, which
// does the same job and also handles accents, pipe-separated labels and trailing language tags —
// the shapes that were still reaching the screen as duplicate posters.

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
        /**
         * Everything the provider sent, in order, for the full schedule.
         *
         * The client has always fetched several hours of programmes and the screen used two of
         * them; the rest was parsed and thrown away. Showing the whole grid is what people expect
         * from a live channel, and it costs nothing extra — the request is already made.
         *
         * Defaulted to empty so a caller that only has now-and-next still compiles.
         */
        val schedule: List<XtreamEpgProgram> = emptyList(),
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
