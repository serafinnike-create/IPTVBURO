package com.lucasserafin94.iptvburo.desktop

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lucasserafin94.iptvburo.desktop.build.BUNDLED_TMDB_KEY
import com.lucasserafin94.iptvburo.desktop.data.ArtworkCache
import com.lucasserafin94.iptvburo.desktop.data.CatalogLoadProgress
import com.lucasserafin94.iptvburo.desktop.data.FtpPlaylistReader
import com.lucasserafin94.iptvburo.desktop.data.CatalogueRepository
import com.lucasserafin94.iptvburo.desktop.data.InMemoryCatalogRepository
import com.lucasserafin94.iptvburo.desktop.data.XmltvGuideSource
import com.lucasserafin94.iptvburo.desktop.license.DeviceFingerprint
import com.lucasserafin94.iptvburo.desktop.license.ProvisioningClient
import com.lucasserafin94.iptvburo.desktop.data.MusicLibraryLoader
import com.lucasserafin94.iptvburo.desktop.data.RemotePlaylistProtocol
import com.lucasserafin94.iptvburo.desktop.data.RemotePlaylistSource
import com.lucasserafin94.iptvburo.desktop.data.ServiceTitleIndex
import com.lucasserafin94.iptvburo.desktop.data.SessionXtreamRepository
import com.lucasserafin94.iptvburo.desktop.data.SwitchingCatalogueRepository
import com.lucasserafin94.iptvburo.desktop.data.StreamingShelfDiskCache
import com.lucasserafin94.iptvburo.desktop.data.WebDavPlaylistReader
import com.lucasserafin94.iptvburo.desktop.data.contentIdentity
import com.lucasserafin94.iptvburo.desktop.data.migrateFavoriteKeys
import com.lucasserafin94.iptvburo.desktop.download.DISPLAY_LOCALE
import com.lucasserafin94.iptvburo.desktop.download.DesktopDownloadManager
import com.lucasserafin94.iptvburo.desktop.download.DownloadRateTracker
import com.lucasserafin94.iptvburo.desktop.download.DownloadResult
import com.lucasserafin94.iptvburo.desktop.download.FailureReason
import com.lucasserafin94.iptvburo.desktop.download.StoredDownload
import com.lucasserafin94.iptvburo.desktop.download.toReadableTitle
import com.lucasserafin94.iptvburo.desktop.license.LicenseClient
import com.lucasserafin94.iptvburo.desktop.license.LicenseStatus
import com.lucasserafin94.iptvburo.desktop.model.DesktopSourceKind
import com.lucasserafin94.iptvburo.desktop.model.DesktopSourceSummary
import com.lucasserafin94.iptvburo.desktop.model.ImportedCatalog
import com.lucasserafin94.iptvburo.desktop.model.XtreamCatalogPage
import com.lucasserafin94.iptvburo.desktop.model.XtreamPlaybackTarget
import com.lucasserafin94.iptvburo.desktop.model.XtreamSessionSummary
import com.lucasserafin94.iptvburo.desktop.platform.CastReceiver
import com.lucasserafin94.iptvburo.desktop.platform.CastTarget
import com.lucasserafin94.iptvburo.desktop.platform.ExternalOpenResult
import com.lucasserafin94.iptvburo.desktop.platform.openUriExternally
import com.lucasserafin94.iptvburo.desktop.playback.DesktopPlaybackProgressCoordinator
import com.lucasserafin94.iptvburo.desktop.playback.DesktopPlaybackRequest
import com.lucasserafin94.iptvburo.desktop.playback.MultiviewTile
import com.lucasserafin94.iptvburo.desktop.playback.SubtitleColour
import com.lucasserafin94.iptvburo.desktop.playback.SubtitleSize
import com.lucasserafin94.iptvburo.desktop.playback.SubtitleStyle
import com.lucasserafin94.iptvburo.desktop.security.RememberedXtreamStore
import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import com.lucasserafin94.iptvburo.desktop.security.XtreamSource
import com.lucasserafin94.iptvburo.desktop.security.XtreamSourceLibrary
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.RememberedScroll
import com.lucasserafin94.iptvburo.desktop.ui.editorialTitle
import com.lucasserafin94.iptvburo.desktop.ui.providerIdentityFor
import com.lucasserafin94.iptvburo.desktop.user.CategoryPreferenceIdentity
import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import com.lucasserafin94.iptvburo.desktop.user.DesktopProfile
import com.lucasserafin94.iptvburo.desktop.user.DesktopUserStore
import com.lucasserafin94.iptvburo.desktop.user.ListeningHistoryStore
import com.lucasserafin94.iptvburo.desktop.user.MusicCorrection
import com.lucasserafin94.iptvburo.desktop.user.MusicCorrectionStore
import com.lucasserafin94.iptvburo.desktop.user.MusicPlayCountStore
import com.lucasserafin94.iptvburo.desktop.user.MusicPlaylistStore
import com.lucasserafin94.iptvburo.desktop.user.ProfilePhotoStore
import com.lucasserafin94.iptvburo.desktop.user.StoredNotification
import com.lucasserafin94.iptvburo.desktop.user.StoredParentalLock
import com.lucasserafin94.iptvburo.desktop.user.StoredReminder
import com.lucasserafin94.iptvburo.domain.model.AppNotification
import com.lucasserafin94.iptvburo.domain.model.AudioOutputMode
import com.lucasserafin94.iptvburo.domain.model.BestOfferPolicy
import com.lucasserafin94.iptvburo.domain.model.MergedSources
import com.lucasserafin94.iptvburo.domain.model.CacheBudget
import com.lucasserafin94.iptvburo.domain.model.CacheFillProgress
import com.lucasserafin94.iptvburo.domain.model.CacheFillState
import com.lucasserafin94.iptvburo.domain.model.CastMessage
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.Category
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.DiscoveryCandidate
import com.lucasserafin94.iptvburo.domain.model.DiscoveryDeck
import com.lucasserafin94.iptvburo.domain.model.DiscoveryVerdict
import com.lucasserafin94.iptvburo.domain.model.ExternalContentId
import com.lucasserafin94.iptvburo.domain.model.ExternalTitle
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleDetails
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleKind
import com.lucasserafin94.iptvburo.domain.model.FamilyContentPolicy
import com.lucasserafin94.iptvburo.domain.model.HeroCandidate
import com.lucasserafin94.iptvburo.domain.model.HeroSelection
import com.lucasserafin94.iptvburo.domain.model.LibraryCandidate
import com.lucasserafin94.iptvburo.domain.model.LibraryOfferPolicy
import com.lucasserafin94.iptvburo.domain.model.ListeningHistoryEntry
import com.lucasserafin94.iptvburo.domain.model.ListeningKind
import com.lucasserafin94.iptvburo.domain.model.MusicLibrary
import com.lucasserafin94.iptvburo.domain.model.MusicPlaylist
import com.lucasserafin94.iptvburo.domain.model.MusicPlaylistExportResult
import com.lucasserafin94.iptvburo.domain.model.MusicPlaylistExportWarning
import com.lucasserafin94.iptvburo.domain.model.MusicPlaylistExporter
import com.lucasserafin94.iptvburo.domain.model.MusicPlaylistKind
import com.lucasserafin94.iptvburo.domain.model.MusicTidyProposal
import com.lucasserafin94.iptvburo.domain.model.MusicTidying
import com.lucasserafin94.iptvburo.domain.model.MusicTrack
import com.lucasserafin94.iptvburo.domain.model.NotificationCentre
import com.lucasserafin94.iptvburo.domain.model.NotificationKind
import com.lucasserafin94.iptvburo.domain.model.OfferRanking
import com.lucasserafin94.iptvburo.domain.model.ParentalLock
import com.lucasserafin94.iptvburo.domain.model.ParentalPin
import com.lucasserafin94.iptvburo.domain.model.PlaybackContentType
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgress
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgressIdentity
import com.lucasserafin94.iptvburo.domain.model.PlaybackQueue
import com.lucasserafin94.iptvburo.domain.model.QueueEntry
import com.lucasserafin94.iptvburo.domain.model.QueueMediaKind
import com.lucasserafin94.iptvburo.domain.model.Reminder
import com.lucasserafin94.iptvburo.domain.model.ReminderDigest
import com.lucasserafin94.iptvburo.domain.model.ReminderPolicy
import com.lucasserafin94.iptvburo.domain.model.ResumeDecision
import com.lucasserafin94.iptvburo.domain.model.SeasonalCollection
import com.lucasserafin94.iptvburo.domain.model.SeasonalCollections
import com.lucasserafin94.iptvburo.domain.model.SeriesChange
import com.lucasserafin94.iptvburo.domain.model.SeriesWatchPolicy
import com.lucasserafin94.iptvburo.domain.model.SessionTaste
import com.lucasserafin94.iptvburo.domain.model.SmartPlaylistRule
import com.lucasserafin94.iptvburo.domain.model.SmartPlaylists
import com.lucasserafin94.iptvburo.domain.model.StreamingDiscoveryCapability
import com.lucasserafin94.iptvburo.domain.model.StreamingDiscoveryProvider
import com.lucasserafin94.iptvburo.domain.model.StreamingProvider
import com.lucasserafin94.iptvburo.domain.model.TasteProfile
import com.lucasserafin94.iptvburo.domain.model.TitleShareLink
import com.lucasserafin94.iptvburo.domain.model.UserStreamingPreference
import com.lucasserafin94.iptvburo.domain.model.ViewerAffinity
import com.lucasserafin94.iptvburo.domain.model.asExternalCandidate
import com.lucasserafin94.iptvburo.domain.model.asLibraryCandidate
import com.lucasserafin94.iptvburo.domain.model.normalisedForMatching
import com.lucasserafin94.iptvburo.domain.model.shelfDeduplicationKey
import com.lucasserafin94.iptvburo.metadata.CriticScores
import com.lucasserafin94.iptvburo.metadata.CriticScoresClient
import com.lucasserafin94.iptvburo.metadata.TMDB_NAMESPACE
import com.lucasserafin94.iptvburo.metadata.TMDB_SERIES_NAMESPACE
import com.lucasserafin94.iptvburo.desktop.data.AdultArtworkShelf
import com.lucasserafin94.iptvburo.desktop.diagnostics.DiagnosticsRunner
import com.lucasserafin94.iptvburo.metadata.AdultArtworkClient
import com.lucasserafin94.iptvburo.metadata.TmdbAudienceScore
import com.lucasserafin94.iptvburo.metadata.TmdbClient
import com.lucasserafin94.iptvburo.metadata.TmdbDiscoverKind
import com.lucasserafin94.iptvburo.metadata.TmdbServiceShelf
import com.lucasserafin94.iptvburo.metadata.TmdbShelfLoadResult
import com.lucasserafin94.iptvburo.metadata.TmdbStreamingCatalogue
import com.lucasserafin94.iptvburo.metadata.TmdbTitleDetails
import com.lucasserafin94.iptvburo.playlist.MusicPlaylistMapper
import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamCategory
import com.lucasserafin94.iptvburo.xtream.XtreamClientException
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import com.lucasserafin94.iptvburo.xtream.XtreamEpgProgram
import com.lucasserafin94.iptvburo.xtream.XtreamFailureReason
import com.lucasserafin94.iptvburo.xtream.XtreamMovieDetails
import com.lucasserafin94.iptvburo.xtream.XtreamSeriesDetails
import java.net.URI
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Arrays
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.toLocalDateTime

/** Provider ids are reused between film and series catalogues, so both parts form the cache key. */
internal fun heroSynopsisKey(
    contentType: XtreamContentType,
    providerId: String,
): String = "${contentType.name}:$providerId"

@Stable
class DesktopAppState(
    private val localRepository: InMemoryCatalogRepository,
    /**
     * The subscription, whichever protocol it speaks.
     *
     * Typed as the contract rather than the Xtream class so a Stalker repository can take its
     * place without this file knowing. Everything below asks the same 19 questions either way.
     */
    private val xtreamRepository: CatalogueRepository,
    private val rememberedXtreamStore: RememberedXtreamStore,
    private val userStore: DesktopUserStore = DesktopUserStore(),
    private val playbackProgressCoordinator: DesktopPlaybackProgressCoordinator = DesktopPlaybackProgressCoordinator(),
    private val downloadManager: DesktopDownloadManager = DesktopDownloadManager(),
    private val sourceLibrary: XtreamSourceLibrary = XtreamSourceLibrary(),
    private val photoStore: ProfilePhotoStore = ProfilePhotoStore(),
    /** The guide for playlists that name one in `url-tvg`. Xtream brings its own. */
    private val xmltvGuideSource: XmltvGuideSource = XmltvGuideSource(),
    /** Collects a connection a reseller set up for this machine. Usually finds nothing. */
    private val provisioningClient: ProvisioningClient = ProvisioningClient(DeviceFingerprint),
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
     * The critics' scores, keyed by the user's own OMDb key.
     *
     * No bundled fallback, unlike TMDb: OMDb's free tier is a thousand requests a day against one
     * account, and shipping a shared key would exhaust one person's allowance on everyone else's
     * browsing. Without a key the details screen shows the audience score alone.
     */
    private var criticScoresClient: CriticScoresClient? =
        userStore.criticScoresApiKey()?.let { key -> CriticScoresClient(key) }

    var criticScoresApiKey by mutableStateOf(userStore.criticScoresApiKey().orEmpty())
        private set

    /**
     * The viewer's ThePornDB key, or blank.
     *
     * Blank is the shipped state and stays a working app: those rows keep the title card they
     * already draw. No key travels with the installer — one inside a file anybody unpacks is a
     * published key, and the account suspended for its abuse would be the one that issued it.
     */
    var adultMetadataApiKey by mutableStateOf(userStore.adultMetadataApiKey().orEmpty())
        private set

    /** The client, present only while a key is. */
    var adultArtworkClient by mutableStateOf(
        userStore.adultMetadataApiKey()?.takeIf(String::isNotBlank)?.let(::AdultArtworkClient),
    )
        private set

    /**
     * Covers fetched for the grid, one lookup per title however often a card is drawn.
     *
     * Separate from [adultArtworkUrl], which the details screen uses for the one title on it. A
     * grid rebuilds its cards on every scroll, so without a cache the same title would be asked
     * about again each time it came back into view.
     *
     * Present only while a key is, and rebuilt with the client so a pasted key takes effect at
     * once — and so a key that is removed cannot go on serving covers it fetched.
     *
     * Built on first use rather than in the constructor. It needs `streamingScope`, which is
     * declared far below this and is therefore still null while these properties initialise —
     * constructing it here threw on startup, and no test caught it because a test builds the shelf
     * directly with a scope already in hand.
     */
    private var adultArtworkShelfOrNull: AdultArtworkShelf? = null

    private var adultArtworkShelfClient: AdultArtworkClient? = null

    val adultArtworkShelf: AdultArtworkShelf?
        get() {
            val client = adultArtworkClient ?: return null
            // Rebuilt when the key changed, so a pasted one takes effect without a restart and a
            // replaced one cannot go on serving covers the previous key fetched.
            if (adultArtworkShelfClient !== client) {
                adultArtworkShelfClient = client
                adultArtworkShelfOrNull =
                    AdultArtworkShelf(
                        client = client,
                        scope = streamingScope,
                        // Each answer nudges the revision so cards holding a placeholder redraw.
                        // One signal per answer, not per card.
                        onFound = { adultArtworkRevision += 1 },
                    )
            }
            return adultArtworkShelfOrNull
        }

    /** Bumped as covers arrive, so a grid drawn before the answer redraws once it has one. */
    var adultArtworkRevision by mutableStateOf(0)
        private set

    /** Stores the key and rebuilds the client, so pasting one takes effect without a restart. */
    fun updateAdultMetadataApiKey(value: String) {
        val clean = value.trim()
        userStore.setAdultMetadataApiKey(clean)
        adultMetadataApiKey = clean
        adultArtworkClient = clean.takeIf(String::isNotBlank)?.let(::AdultArtworkClient)
        // The shelf follows the client on next use; drop the old one so a replaced key cannot go
        // on serving covers the previous one fetched.
        adultArtworkShelfOrNull = null
        adultArtworkShelfClient = null
    }

    /**
     * Stores the OMDb key and rebuilds the client, so pasting one takes effect without a restart.
     */
    fun updateCriticScoresApiKey(value: String) {
        val clean = value.trim()
        userStore.setCriticScoresApiKey(clean)
        criticScoresApiKey = clean
        criticScoresClient = clean.takeIf(String::isNotBlank)?.let { key -> CriticScoresClient(key) }
        // Anything already on screen came from the old key, or from none at all.
        criticScores = null
    }

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

    /**
     * Titles this profile asked to be reminded about, newest concerns first as stored.
     *
     * Read from the store rather than the boot snapshot, which predates reminders and would have to
     * grow a field — and every caller that builds one would have to be found and changed — to carry
     * something only this screen reads.
     */
    var reminders by mutableStateOf(userStore.remindersForProfile(initialUserSnapshot.activeProfileId))
        private set

    /** The identity keys alone, which is what "is this one marked" asks. */
    private val reminderKeys: Set<String>
        get() = reminders.mapTo(LinkedHashSet(), StoredReminder::identityKey)

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
        // Alongside favourites: a reminder belongs to whoever asked for it, so the new profile must
        // not inherit the previous one's marked titles.
        reminders = userStore.remindersForProfile(activeProfileId)
        // The bell belongs to whoever it is announcing to: one person's news must not appear under
        // another's name, and a Kids profile must not inherit an adult's.
        notifications = loadNotifications(activeProfileId)
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
        userStore.setReminders(profileId, emptyList())
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
        reminders = emptyList()
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

    /**
     * This machine's code, as the panel and the seller know it.
     *
     * Read straight from the identity rather than from the licence answer: it has to be shown
     * before any licence check has come back, and on a machine with no network at all — which is
     * one of the situations where somebody most needs to read it out for help.
     *
     * Empty only if the identity cannot be created, in which case the button that shows it is
     * hidden rather than offering a blank code to read aloud.
     */
    val deviceCode: String by lazy {
        runCatching { DeviceFingerprint.deviceId() }.getOrDefault("")
    }

    /**
     * Points the next connection at a Stalker portal rather than an Xtream server.
     *
     * Called by the source form before it connects, because the two protocols want different
     * things: a portal takes an address and a MAC where Xtream takes a username and a password.
     * Silently ignored when the repository cannot switch, which is the case in tests that inject a
     * single-protocol double — a form that refused to connect there would fail tests about
     * something else entirely.
     */
    fun useStalkerForNextConnection(stalker: Boolean) {
        val switching = xtreamRepository as? SwitchingCatalogueRepository ?: return
        if (stalker) switching.useStalker() else switching.useXtream()
    }

    /**
     * Covers this provider reuses across thousands of titles, so a card can ignore them.
     *
     * Read through [xtreamCatalogRevision] so the value refreshes when a catalogue is loaded; the
     * repository computes it once per catalogue, not once per card.
     */
    val placeholderArtworkUrls: Set<String>
        get() = xtreamRepository.placeholderArtworkUrls()

    /**
     * The cover to draw for [item], or null when the provider effectively gave none.
     *
     * A provider that files ten thousand adult titles under one "XXX" card leaves every row
     * technically covered, so nothing ever reached the fallback and the grid drew that one picture
     * hundreds of times. Treating it as absent lets the readable title card do its job.
     */
    fun artworkFor(item: XtreamCatalogItem): String? =
        item.artworkUrl?.takeIf { url -> url.trim() !in placeholderArtworkUrls }

    /**
     * A fetched cover for a title the provider left uncovered, or null.
     *
     * Only for adult titles, and only when a key is configured. Two reasons to gate it rather than
     * asking for everything uncovered: the source only knows adult titles, so an ordinary film
     * spends a request to learn nothing; and the request budget belongs to the viewer's own key.
     *
     * Reading [adultArtworkRevision] here is what makes a card redraw when its answer arrives —
     * the value is unused, but touching it subscribes this composable to the change.
     */
    fun fetchedArtworkFor(item: XtreamCatalogItem): String? {
        val shelf = adultArtworkShelf ?: return null
        // The category, not just the title. A provider files these under "ADULTOS" and names the
        // titles themselves anything at all, so matching only the name would miss most of them.
        val adultCategoryIds =
            xtreamCategories
                .filter { category -> FamilyContentPolicy.isExplicitAdultLabel(category.name) }
                .map(XtreamCategory::providerId)
                .toSet()
        if (!FamilyContentPolicy.isExplicitAdultLabel(item.name) &&
            item.categoryIds.none { id -> id in adultCategoryIds }
        ) {
            return null
        }
        @Suppress("UNUSED_EXPRESSION")
        adultArtworkRevision
        return shelf.posterFor(item.name.editorialTitle())
    }

    /**
     * A connection test against the provider this session is signed in to.
     *
     * Built here rather than in the screen so the repository stays private: it holds the account's
     * credentials, and a screen that could reach it could also put a credentialed URL into UI
     * state, where a recomposition snapshot or a crash dump would keep it.
     *
     * A new one per call, because it keeps nothing between runs and a stored one would pin the
     * repository it was built against after a sign-out.
     */
    fun newDiagnosticsRunner(): DiagnosticsRunner = DiagnosticsRunner(repository = xtreamRepository)

    /**
     * Whether every configured list is browsed as one catalogue.
     *
     * Off by default: somebody with one list gains nothing and would pay for a merge pass over
     * their whole catalogue, and somebody with two who has not asked for this should not find
     * their library silently rearranged.
     */
    var mergeAllSources by mutableStateOf(userStore.mergeAllSources())
        private set

    /**
     * Stores the choice.
     *
     * Takes effect on the next load rather than immediately: rebuilding the catalogue underneath
     * somebody who is browsing would empty the screen they are looking at, and the setting is
     * changed from the profile form — which is on the way to a load anyway.
     */
    fun updateMergeAllSources(enabled: Boolean) {
        userStore.setMergeAllSources(enabled)
        mergeAllSources = enabled
    }

    /**
     * The same title from another subscription, for a stream that failed.
     *
     * Null when only one list carries it, or when every list has been tried — which is the point at
     * which the viewer genuinely has to be told rather than kept waiting.
     *
     * Half the value of owning a second subscription is that a dead stream is not the end of the
     * evening, and the viewer should never have to know a swap happened.
     */
    fun alternativePlayback(
        request: DesktopPlaybackRequest,
        attempt: Int,
    ): DesktopPlaybackRequest? {
        val target = lastPlaybackTarget ?: return null
        val uri =
            runCatching { xtreamRepository.buildAlternativePlaybackUri(target, exclude = attempt) }
                .getOrNull() ?: return null
        return request.copy(uri = uri)
    }

    /** What is playing, so a failed stream can be asked for from a different list. */
    private var lastPlaybackTarget: XtreamPlaybackTarget? = null

    /** Playlists already configured, offered so a new profile can reuse one. */
    fun savedSources(): List<XtreamSource> = sourceLibrary.sources()

    /**
     * Renames a saved playlist.
     *
     * The label is the only thing distinguishing one saved list from another on the profile screen,
     * and the name a list is created with is often whatever was typed in a hurry. A blank name is
     * refused rather than stored: it would leave an unlabelled row nobody could identify.
     */
    fun renameSavedSource(sourceId: String, label: String) {
        if (label.isBlank()) return
        sourceLibrary.rename(sourceId, label)
        savedSourcesRevision += 1
    }

    /**
     * Forgets a saved playlist and its credentials.
     *
     * Profiles that were using it keep working until they are next opened, and then have no list —
     * so they are pointed away from it here rather than left holding an id for something that no
     * longer exists. That reads as a broken profile, and the viewer would have no way to see why.
     *
     * The credentials go with it: [XtreamSourceLibrary.remove] clears the protected file, so
     * forgetting a list actually forgets the password rather than orphaning it on disk.
     */
    fun removeSavedSource(sourceId: String) {
        sourceLibrary.remove(sourceId)
        val orphaned = profiles.filter { it.sourceId == sourceId }
        if (orphaned.isNotEmpty()) {
            profiles = profiles.map { profile ->
                if (profile.sourceId == sourceId) profile.copy(sourceId = null) else profile
            }
            userStore.saveProfiles(profiles)
        }
        // The signed-in session belongs to the list that was just forgotten.
        if (orphaned.any { it.id == activeProfileId }) {
            xtreamRepository.clear()
            xtreamStatus = XtreamStatus.Disconnected
        }
        savedSourcesRevision += 1
    }

    /**
     * Bumped whenever the saved list changes.
     *
     * [savedSources] reads the store directly rather than holding state, so a screen showing it
     * would not recompose when a list is renamed or removed — the row would sit there with its old
     * name until something else redrew the screen. Reading this makes the change visible.
     */
    var savedSourcesRevision by mutableStateOf(0)
        private set

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

    /** Whether this profile asked to be reminded about [item]. */
    fun hasReminder(item: XtreamCatalogItem): Boolean = hasReminder(item.contentIdentity())

    /**
     * The same question for a title that is not in the library.
     *
     * An upcoming film on the Assinaturas shelf has no catalogue row — that is the whole point of
     * it — so it can only be named by identity. [ContentIdentity] is derived from the title and
     * year, so the mark made here is the one found later when the film does arrive in a playlist.
     */
    fun hasReminder(identity: ContentIdentity): Boolean = identity.key in reminderKeys

    /** Marks or unmarks [item] as something to be reminded about. */
    fun toggleReminder(item: XtreamCatalogItem) =
        toggleReminder(
            identity = item.contentIdentity(),
            // The editorial title, not the provider's decorated one: the reminders list would
            // otherwise read "Filme 4K [DV] [HDR]" where the film's name should be.
            title = item.name.editorialTitle(),
            year = ContentIdentity.yearFromTitle(item.name),
            artworkUrl = item.artworkUrl,
        )

    /**
     * Marks or unmarks a title, named by identity so an upcoming release can be marked too.
     *
     * Storage only. Windows has no scheduler behind this yet — the phone's daily notification is
     * Android's, and nothing here posts one — so what this earns the viewer today is a durable,
     * per-profile list rather than an alert. Marking a title, seeing it in Lembretes and finding it
     * still there next launch is the honest half of the feature; announcing it is the half to come.
     */
    fun toggleReminder(
        identity: ContentIdentity,
        title: String,
        year: Int? = null,
        artworkUrl: String? = null,
    ) {
        val profileId = activeProfileId ?: return
        val key = identity.key
        reminders =
            if (key in reminderKeys) {
                reminders.filterNot { reminder -> reminder.identityKey == key }
            } else {
                reminders +
                    StoredReminder(
                        identityKey = key,
                        title = title,
                        year = year,
                        artworkUrl = artworkUrl,
                    )
            }
        userStore.setReminders(profileId, reminders)
    }

    /**
     * Fills in the name and poster of entries stored before those were kept.
     *
     * The first version wrote only the identity key, so those rows show `movie:enola-holmes-3:2026`
     * where a title belongs. The catalogue can answer what that film is actually called, so the
     * entry is repaired the moment the library is able to — once, and written back, so the ugly
     * name is not merely hidden on screen while remaining on disk.
     *
     * Silent when the catalogue has no row: an upcoming film legitimately has none, and its record
     * is left exactly as it is rather than being blamed for a lookup that could never succeed.
     */
    fun healStoredReminders() {
        val profileId = activeProfileId ?: return
        if (reminders.none { reminder -> reminder.titleIsPlaceholder || reminder.artworkUrl == null }) {
            return
        }

        val healed =
            reminders.map { reminder ->
                if (!reminder.titleIsPlaceholder && reminder.artworkUrl != null) return@map reminder
                val item = catalogItemForReminder(reminder) ?: return@map reminder
                reminder.copy(
                    title = if (reminder.titleIsPlaceholder) item.name.editorialTitle() else reminder.title,
                    year = reminder.year ?: item.year,
                    artworkUrl = reminder.artworkUrl ?: item.artworkUrl,
                )
            }
        if (healed == reminders) return
        reminders = healed
        userStore.setReminders(profileId, healed)
    }

    /** The hour of day the viewer chose for the reminder notice, 0–23. */
    var reminderHour by mutableStateOf(userStore.reminderHour())
        private set

    /** Whether the notice is wanted at all. */
    var remindersAnnounced by mutableStateOf(userStore.remindersAnnounced())
        private set

    /** Named `choose`/`announce` rather than `set`, which would clash with the property's setter. */
    fun chooseReminderHour(hour: Int) {
        reminderHour = hour.coerceIn(0, 23)
        userStore.setReminderHour(reminderHour)
        // The slot may now be in the past for today, which makes the notice due. Re-checked rather
        // than left until the next navigation, so choosing an earlier hour shows it immediately.
        refreshReminderNotice()
    }

    fun announceReminders(announced: Boolean) {
        remindersAnnounced = announced
        userStore.setRemindersAnnounced(announced)
        if (!announced) reminderNotice = null else refreshReminderNotice()
    }

    /**
     * The digest waiting to be shown in the app, or null when there is nothing to say.
     *
     * In-app rather than a system notification: this process only runs while the app is open, so a
     * desktop toast would fire when the user is already looking at the app and never when they are
     * not. Announcing it where they are is the honest version of the feature.
     */
    var reminderNotice by mutableStateOf<ReminderDigest.Daily?>(null)
        private set

    /**
     * Works out whether today's notice is due, and what it says.
     *
     * Three things must hold: the notice is wanted, the chosen hour has passed, and it has not
     * already been shown today. The last is what stops a banner reappearing on every navigation.
     */
    fun refreshReminderNotice() {
        if (!remindersAnnounced || reminders.isEmpty()) {
            reminderNotice = null
            return
        }

        // kotlinx types throughout this block, because everything here feeds ReminderPolicy,
        // which is multiplatform. The rest of the file keeps java.time for its own clock work.
        val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
        val now = kotlin.time.Clock.System.now()
        val today = now.toLocalDateTime(zone).date
        if (userStore.reminderLastShownOn() == today.toString()) {
            reminderNotice = null
            return
        }
        // Before the chosen hour there is nothing to announce yet — that is what choosing an hour
        // means. nextNotificationAt returns tomorrow's slot once today's has passed, so today's
        // being in the future is exactly the test for "not yet".
        if (ReminderPolicy
                .nextNotificationAt(kotlinx.datetime.LocalTime(reminderHour, 0), now, zone)
                .toLocalDateTime(zone)
                .date == today
        ) {
            reminderNotice = null
            return
        }

        val digest =
            ReminderPolicy.digestFor(
                reminders = reminders.map { held ->
                    Reminder(
                        identity = ContentIdentity(held.identityKey),
                        title = held.title,
                        artworkUrl = held.artworkUrl,
                        // No release date is stored yet, so every entry is "waiting" rather than a
                        // countdown. The policy handles that case; inventing a date would not.
                        releaseDate = null,
                    )
                },
                now = now,
                zone = zone,
            )
        reminderNotice = digest as? ReminderDigest.Daily
        // Also posted to the bell, so it survives being dismissed on the Lembretes screen and can
        // be found again later. Keyed on the day, so a rebuild adds nothing and the badge does not
        // come back for news the viewer has already read.
        (digest as? ReminderDigest.Daily)?.let { daily ->
            // Worded in the viewer's language at the moment it is posted, and kept as text. The
            // alternative — storing the count and formatting it when drawn — would re-word old
            // news in a language the viewer has since switched to, which is worse: the notice is a
            // record of what was said, not a template to re-render.
            val text = DesktopStrings.of(language).savedForLater
            postNotification(
                AppNotification(
                    id = NotificationCentre.reminderDigestId(today.toString()),
                    kind = NotificationKind.REMINDER,
                    title = text.remindersTitle,
                    body = text.reminderNoticeBody.format(daily.total),
                    createdAt = now.toEpochMilliseconds(),
                ),
            )
        }
    }

    /**
     * The bell beside the profile: what is waiting, and how much of it is unread.
     *
     * Loaded per profile and written back on every change. The alternative — holding notices only
     * in memory — would mean closing the app was the same as dismissing everything, and a reminder
     * that vanishes when you close the window is not a reminder.
     */
    var notifications by mutableStateOf(loadNotifications(initialUserSnapshot.activeProfileId))
        private set

    private fun loadNotifications(profileId: String?): NotificationCentre =
        NotificationCentre(
            userStore.notificationsForProfile(profileId).map { stored ->
                AppNotification(
                    id = stored.id,
                    // An unrecognised kind becomes REMINDER rather than throwing: a record written
                    // by a newer build must not stop this one from reading the rest of the list.
                    kind =
                        runCatching { NotificationKind.valueOf(stored.kind) }
                            .getOrDefault(NotificationKind.REMINDER),
                    title = stored.title,
                    body = stored.body,
                    createdAt = stored.createdAt,
                    read = stored.read,
                )
            },
        )

    private fun persistNotifications() {
        val profileId = activeProfileId ?: return
        userStore.setNotifications(
            profileId,
            notifications.notifications.map { notification ->
                StoredNotification(
                    id = notification.id,
                    kind = notification.kind.name,
                    read = notification.read,
                    createdAt = notification.createdAt,
                    title = notification.title,
                    body = notification.body,
                )
            },
        )
    }

    /** Adds a notice, unless the bell already holds one with the same id. */
    fun postNotification(notification: AppNotification) {
        val before = notifications
        notifications = notifications.add(notification).trimmed()
        // Only written when something actually changed: a rebuilt digest that adds nothing should
        // not rewrite the file on every launch.
        if (notifications != before) persistNotifications()
    }

    fun markNotificationsRead() {
        if (notifications.unreadCount == 0) return
        notifications = notifications.markAllRead()
        persistNotifications()
    }

    fun removeNotification(id: String) {
        notifications = notifications.remove(id)
        persistNotifications()
    }

    fun clearNotifications() {
        if (notifications.notifications.isEmpty()) return
        notifications = notifications.clear()
        persistNotifications()
    }

    /**
     * Looks for new episodes and seasons of the series this profile has favourited.
     *
     * Only favourites, and that is the permission the whole feature rests on: announcing every
     * series a provider adds an episode to would be a stream of noise about programmes nobody is
     * watching. Marking a series as a favourite is the viewer saying "I am following this".
     *
     * Each series costs one request, because the episode list is not part of the catalogue — so
     * this runs off the main thread, at most [FOLLOWED_SERIES_LIMIT] series at a time, and only
     * when the screen asks. A household with forty favourite series would otherwise spend forty
     * round trips before the app had drawn anything.
     */
    fun checkFollowedSeries() {
        val profileId = activeProfileId ?: return
        if (xtreamSummary == null) return
        if (followedSeriesChecking) return
        followedSeriesChecking = true

        streamingScope.launch {
            runCatching { withContext(Dispatchers.IO) { checkFollowedSeriesBlocking(profileId) } }
            followedSeriesChecking = false
        }
    }

    private var followedSeriesChecking = false

    private fun checkFollowedSeriesBlocking(profileId: String) {
        val marks = userStore.seriesWatermarks(profileId).toMutableMap()
        var changed = false

        // Never-checked series first, so a newly favourited one gets its mark on the next pass
        // rather than waiting behind twenty that already have theirs.
        //
        // This is priority, not rotation: somebody following more than the ceiling still has the
        // overflow checked only once the earlier ones stop being new, which for a stable list means
        // the same set each time. Worth fixing when anyone actually follows more than twenty
        // series; not worth a scheduler before then.
        val ordered =
            favoriteKeys
                .sortedBy { key -> if (marks.containsKey(key)) 1 else 0 }
                .take(FOLLOWED_SERIES_LIMIT)

        ordered.forEach { key ->
            val series = xtreamRepository.itemByContentKey(XtreamContentType.SERIES, key) ?: return@forEach
            // One request per series, and a failure is skipped rather than fatal: a provider that
            // refuses one series must not stop the rest from being checked.
            val details = runCatching { xtreamRepository.seriesDetails(series.providerId) }.getOrNull()
                ?: return@forEach
            if (details.episodes.isEmpty()) return@forEach

            val seasons = details.episodes.map { episode -> episode.seasonNumber }
            val latestSeason = seasons.maxOrNull() ?: return@forEach
            val episodesInLatest =
                details.episodes
                    .filter { episode -> episode.seasonNumber == latestSeason }
                    .mapNotNull { episode -> episode.episodeNumber }

            val change =
                SeriesWatchPolicy.changeSince(
                    previous = marks[key],
                    seasons = seasons,
                    episodesInLatestSeason = episodesInLatest,
                    totalEpisodes = details.episodes.size,
                )

            val text = DesktopStrings.of(language).savedForLater
            val title = series.name.editorialTitle()
            when (change) {
                is SeriesChange.NewSeason ->
                    postNotification(
                        AppNotification(
                            id = NotificationCentre.seasonId(key, change.season),
                            kind = NotificationKind.NEW_SEASON,
                            title = title,
                            body = text.newSeasonBody.format(change.season),
                            createdAt = System.currentTimeMillis(),
                        ),
                    )

                is SeriesChange.NewEpisode ->
                    postNotification(
                        AppNotification(
                            id = NotificationCentre.episodeId(key, change.season, change.episode),
                            kind = NotificationKind.NEW_EPISODE,
                            title = title,
                            body = text.newEpisodeBody.format(change.season, change.episode),
                            createdAt = System.currentTimeMillis(),
                        ),
                    )

                SeriesChange.None -> Unit
            }

            // The mark moves whatever the answer was, including "nothing new": storing it only on a
            // change would re-announce the same episode on every launch after a shrink.
            val next =
                SeriesWatchPolicy.watermarkFor(key, seasons, episodesInLatest, details.episodes.size)
            if (marks[key] != next) {
                marks[key] = next
                changed = true
            }
        }

        if (changed) userStore.setSeriesWatermarks(profileId, marks.values)
    }

    /**
     * How much artwork this machine may keep, and whether the viewer has been asked yet.
     *
     * Null in [storedCacheBudget] means never asked, which is what the first-run panel tests — a
     * viewer who answered zero has made a choice and must not be asked again.
     */
    private val storedCacheBudget: Int? = userStore.cacheBudgetGigabytes()

    var cacheBudget by mutableStateOf(
        CacheBudget.ofGigabytes(storedCacheBudget ?: CacheBudget.DEFAULT_GIGABYTES),
    )
        private set

    /** Whether the first-run panel should offer the choice. */
    var cacheChoicePending by mutableStateOf(storedCacheBudget == null)
        private set

    var cacheProgress by mutableStateOf(CacheFillProgress())
        private set

    /**
     * How much the cache currently holds, in bytes.
     *
     * Measured from the directory rather than counted as it fills: Coil evicts on its own once the
     * budget is reached, so a running total kept here would drift away from what is actually on
     * disk and report a number the viewer could disprove with Explorer.
     */
    var cacheBytesUsed by mutableStateOf(0L)
        private set

    fun chooseCacheBudget(gigabytes: Int) {
        cacheBudget = CacheBudget.ofGigabytes(gigabytes)
        userStore.setCacheBudgetGigabytes(cacheBudget.gigabytes)
        cacheChoicePending = false
        // Lowering it has to free space now rather than at some later eviction: somebody who
        // reduces the setting to reclaim a disk and finds nothing reclaimed has been ignored.
        if (!cacheBudget.isEnabled) clearArtworkCache() else refreshCacheUsage()
    }

    /** Records that the viewer declined the offer, so it is not made again. */
    fun declineCacheChoice() {
        chooseCacheBudget(0)
    }

    /**
     * How many items the library holds, for the cache estimate.
     *
     * Everything loaded, including live channels — which overstates the size a little, because a
     * channel logo is far smaller than a poster. The estimate is presented as approximate and it is
     * better for it to err high: somebody who reserves more than they need loses nothing, while
     * somebody who reserves too little watches the cache fill and stop short of the library.
     */
    val libraryTitleCount: Int
        get() = xtreamSummary?.loadedItemCount ?: 0

    fun refreshCacheUsage() {
        cacheBytesUsed = runCatching { ArtworkCache.bytesUsed() }.getOrDefault(0L)
    }

    fun clearArtworkCache() {
        runCatching { ArtworkCache.clear() }
        cacheBytesUsed = 0
        cacheProgress = CacheFillProgress()
    }

    /**
     * Fetches the artwork of everything in the library, so the list is ready before it is opened.
     *
     * Runs in the background and never blocks the app: the whole point of a cache is that the
     * product is usable while it fills, and a progress bar that holds the door shut for twenty
     * minutes would be worse than no cache at all.
     *
     * What it warms is the same Coil cache the screens read, so a poster fetched here is one the
     * grid does not fetch later. Nothing else is stored: this is the ordinary image pipeline run
     * ahead of time.
     */
    fun startCacheFill() {
        if (!cacheBudget.isEnabled) return
        if (cacheProgress.isRunning) return

        val urls = artworkUrlsForCaching()
        if (urls.isEmpty()) {
            cacheProgress = CacheFillProgress(state = CacheFillState.COMPLETE)
            return
        }

        cacheProgress = CacheFillProgress(done = 0, total = urls.size, state = CacheFillState.RUNNING)
        cacheFillJob =
            streamingScope.launch {
                var done = 0
                for (url in urls) {
                    // Checked every item rather than only between batches, so Pausar and Cancelar
                    // stop within a poster rather than within a hundred.
                    if (cacheProgress.state != CacheFillState.RUNNING) break
                    runCatching { withContext(Dispatchers.IO) { ArtworkCache.warm(url) } }
                    done += 1
                    // Reported every few items: setting Compose state forty thousand times would
                    // spend more on recomposition than on the download it is describing.
                    if (done % CACHE_PROGRESS_STEP == 0 || done == urls.size) {
                        cacheProgress = cacheProgress.copy(done = done)
                    }
                }
                val finished = cacheProgress.state != CacheFillState.RUNNING
                cacheProgress =
                    cacheProgress.copy(
                        done = done,
                        state = if (finished) cacheProgress.state else CacheFillState.COMPLETE,
                    )
                refreshCacheUsage()
            }
    }

    private var cacheFillJob: Job? = null

    fun pauseCacheFill() {
        if (cacheProgress.isRunning) cacheProgress = cacheProgress.copy(state = CacheFillState.PAUSED)
    }

    fun resumeCacheFill() {
        if (cacheProgress.state == CacheFillState.PAUSED) startCacheFill()
    }

    /**
     * Fetches artwork the library has gained since the last fill.
     *
     * Distinct from starting over: what is already on disk stays, and Coil skips anything it
     * already holds, so this costs a request only for what is genuinely new. The reason it exists
     * is that a finished fill is only finished until the provider adds titles — without it, "Tudo
     * guardado" would slowly stop being true and nothing would say so.
     */
    fun refreshCacheFill() {
        if (!cacheBudget.isEnabled) return
        cacheProgress = CacheFillProgress(state = CacheFillState.IDLE)
        startCacheFill()
    }

    fun cancelCacheFill() {
        cacheFillJob?.cancel()
        cacheFillJob = null
        cacheProgress = CacheFillProgress(state = CacheFillState.IDLE)
    }

    /**
     * Every artwork URL worth warming, newest first.
     *
     * Ordered so the first thing cached is the first thing seen: somebody who starts the fill and
     * opens the catalogue a minute later finds the top of the list already drawn, rather than
     * waiting for an alphabetical walk to reach it.
     */
    private fun artworkUrlsForCaching(): List<String> {
        val kidsMode = activeProfile?.isKids == true
        return buildList {
            listOf(XtreamContentType.MOVIE, XtreamContentType.SERIES, XtreamContentType.LIVE)
                .forEach { contentType ->
                    // Walked through `page` rather than a flat list, which does two things: it
                    // avoids materialising forty thousand items at once, and it applies the same
                    // parental filter the catalogue screen does — a Kids profile must not cause the
                    // app to go and fetch the artwork of everything it is not allowed to see.
                    val locked = lockedCategoryIdsForBrowsing(contentType)
                    var pageIndex = 0
                    while (pageIndex < MAX_CACHE_PAGES) {
                        val page =
                            runCatching {
                                xtreamRepository.page(
                                    contentType = contentType,
                                    categoryId = null,
                                    query = "",
                                    requestedPage = pageIndex,
                                    kidsMode = kidsMode,
                                    lockedCategoryIds = locked,
                                )
                            }.getOrNull() ?: break
                        if (page.items.isEmpty()) break
                        page.items.forEach { item ->
                            item.artworkUrl?.takeIf(String::isNotBlank)?.let(::add)
                        }
                        pageIndex += 1
                    }
                }
        }.distinct()
    }

    /**
     * The audience score for the title whose page is open, once TMDb has answered.
     *
     * From TMDb rather than from the provider, and the reason is the vote count: a provider sends a
     * rating with nothing to say how many people it represents, so a 10.0 from two viewers is
     * indistinguishable from one earned by thousands. TMDb sends both, and the screen refuses to
     * print a score that too few people gave.
     */
    var audienceScore by mutableStateOf<TmdbAudienceScore?>(null)
        private set

    /**
     * Looks the score up for [title], unless it is already the one on screen.
     *
     * Cleared first, so a page never shows the previous film's score while this one is in flight —
     * which is the kind of error nobody notices and everybody is misled by.
     */
    /**
     * A cover fetched for a title the playlist gave none for, or null.
     *
     * Held per title rather than written into the catalogue: the catalogue is the provider's own
     * data and a fetched cover is not, so mixing them would leave a disk cache full of artwork
     * that a changed key can no longer explain.
     */
    var adultArtworkUrl by mutableStateOf<String?>(null)
        private set

    private var adultArtworkFor: String? = null

    /**
     * Looks up a cover for [title] when the provider sent none.
     *
     * Only when there is no artwork already: a title the playlist covered is not worth a request,
     * and a fetched image replacing a provider's own would be the app overruling the list.
     *
     * Silent on failure, like the rest of the metadata here — the details screen already draws a
     * readable card for a title with no cover, and an error about a service the viewer may not
     * have heard of would be worse than the card.
     */
    fun loadAdultArtwork(title: String, existingArtwork: String?) {
        if (!existingArtwork.isNullOrBlank()) return
        val client = adultArtworkClient ?: return
        val wanted = title.trim()
        if (wanted.isEmpty() || adultArtworkFor == wanted) return

        adultArtworkFor = wanted
        adultArtworkUrl = null
        streamingScope.launch {
            val found =
                runCatching { withContext(Dispatchers.IO) { client.posterFor(wanted) } }
                    .getOrNull()
            // Only if the viewer is still on the title this was asked for: opening a second one
            // while the first is in flight would otherwise put the wrong cover on the screen.
            if (adultArtworkFor == wanted) adultArtworkUrl = found
        }
    }

    fun loadAudienceScore(
        title: String,
        year: Int?,
        /**
         * Which TMDb catalogue to search.
         *
         * Not a detail: films and series are separate endpoints there, matched on different date
         * fields, and asking the film catalogue for a series name finds nothing at all. This was
         * defaulted to false and only ever called from the film loader, so a series page showed no
         * audience score, no critics' row and no source mark — reported as the ratings simply being
         * absent from séries.
         */
        isSeries: Boolean = false,
    ) {
        if (!metadataClient.isConfigured) return
        val requested = title.trim()
        if (requested.isBlank()) return
        // The same title asked for twice keeps the answer it already has rather than paying for
        // it again — but only when that answer is actually still on hand. Reopening a film clears
        // the shelf, and returning here on the strength of the remembered *name* left it empty for
        // the rest of the session with nothing in flight to fill it.
        if (audienceScoreFor == requested && similarTitles.isNotEmpty()) return

        audienceScoreFor = requested
        audienceScore = null
        streamingScope.launch {
            val found =
                runCatching {
                    withContext(Dispatchers.IO) {
                        metadataClient.findAudienceScore(requested, year, isSeries)
                    }
                }.getOrNull()
            // Checked against what is on screen now: the viewer may have opened another title while
            // this was in flight, and showing this score under that film's name would be worse than
            // showing none.
            if (audienceScoreFor == requested) {
                audienceScore = found
                loadCriticScores(requested, found?.tmdbId)
                loadSimilarTitles(requested, found?.tmdbId, isSeries)
            }
        }
    }

    private var audienceScoreFor: String? = null

    /**
     * What the critics said about the title on screen, when OMDb has an answer.
     *
     * Kept apart from [audienceScore] because they are different claims by different people: TMDb's
     * is the audience voting, and these are the Tomatometer, the Metascore and IMDb. The details
     * screen labels each with its own source for exactly that reason.
     */
    var criticScores by mutableStateOf<CriticScores?>(null)
        private set

    /**
     * Fetches the critics' scores for the title whose audience score has just arrived.
     *
     * Chained onto that lookup rather than started alongside it, because the join key is an IMDb id
     * and only TMDb can supply it: the search resolves a name to a TMDb id, the details call turns
     * that into an IMDb id, and OMDb is keyed by the latter. Matching OMDb on title and year
     * instead would eventually put another film's Tomatometer on this page.
     */
    /**
     * Other titles worth opening from the one on screen: the rest of a franchise, then near misses.
     *
     * TMDb's `recommendations` first and `similar` only as a fallback — that is what the client
     * already does. Recommendations are drawn from what people actually watch together, so for
     * Superman they lead with the other Superman films; `similar` is genre-and-keyword matching,
     * which is a weaker answer but better than an empty shelf.
     */
    var similarTitles by mutableStateOf<List<PersonCredit>>(emptyList())
        private set

    private var similarTitlesFor: String? = null

    /**
     * Chained onto the audience-score lookup, like the critics' scores and for the same reason.
     *
     * The join key is a TMDb id, and resolving a name to one costs a search request. That request
     * has already been made by the time this runs, so asking again would be paying twice for an
     * answer the caller is holding.
     */
    private fun loadSimilarTitles(requested: String, tmdbId: Int?, isSeries: Boolean) {
        similarTitles = emptyList()
        similarTitlesFor = requested
        if (!metadataClient.isConfigured || tmdbId == null) return
        streamingScope.launch {
            val found =
                runCatching {
                    withContext(Dispatchers.IO) {
                        metadataClient.similarTitles(tmdbId, isSeries)
                    }
                }.getOrElse { emptyList() }
            // The viewer may have opened something else while this was in flight. Attaching one
            // film's recommendations to another's page is worse than showing no shelf at all.
            if (similarTitlesFor != requested) return@launch
            similarTitles =
                found
                    // The title itself comes back from `similar` often enough to matter, and a
                    // shelf that offers the film you are already looking at reads as a bug.
                    .filterNot { it.title.equals(requested, ignoreCase = true) }
                    .map { discovered ->
                        PersonCredit(
                            id = discovered.id,
                            isSeries = discovered.isSeries,
                            title = discovered.title,
                            year = discovered.year,
                            posterUrl = discovered.posterUrl,
                            // A recommendation is not a role. The card shows the year instead.
                            character = null,
                        )
                    }
        }
    }

    private fun loadCriticScores(requested: String, tmdbId: Int?) {
        criticScores = null
        val client = criticScoresClient ?: return
        if (!client.isConfigured || tmdbId == null) return

        streamingScope.launch {
            val found =
                runCatching {
                    withContext(Dispatchers.IO) {
                        metadataClient.titleDetails(tmdbId)
                            ?.imdbId
                            ?.let { imdbId -> client.scoresFor(imdbId) }
                    }
                }.getOrNull()
            // The same guard the audience score uses: the viewer may have moved on, and a score
            // drawn under another film's name is worse than no score at all.
            if (audienceScoreFor == requested) criticScores = found
        }
    }

    // --- Descobrir -----------------------------------------------------------------------------

    /**
     * The cards on offer, and the catalogue rows behind them.
     *
     * The deck is decided in the domain, which knows about taste and nothing about posters; the
     * items are kept alongside so the screen can draw artwork and a synopsis without the domain
     * having to carry them.
     */
    var discoveryDeck by mutableStateOf<List<XtreamCatalogItem>>(emptyList())
        private set

    var discoveryLoading by mutableStateOf(false)
        private set

    private var discoverySession = SessionTaste()
    private var discoverySeen: Set<String> = emptySet()

    /** The card on top, or null when the deck is spent. */
    val discoveryTop: XtreamCatalogItem?
        get() = discoveryDeck.firstOrNull()

    /**
     * Builds a deck for this profile.
     *
     * The taste comes from what the viewer has actually done — the genres of their favourites and
     * of what they have watched — rather than from a questionnaire, because what people say they
     * like and what they watch are different lists.
     */
    fun loadDiscoveryDeck() {
        val profileId = activeProfileId ?: return
        if (discoveryLoading) return
        if (xtreamSummary == null) return
        discoveryLoading = true

        streamingScope.launch {
            val built =
                runCatching {
                    withContext(Dispatchers.IO) { buildDiscoveryDeck(profileId) }
                }.getOrDefault(emptyList())
            discoveryDeck = built
            discoveryLoading = false
        }
    }

    private fun buildDiscoveryDeck(profileId: String): List<XtreamCatalogItem> {
        discoverySeen = userStore.discoverySeen(profileId)

        // Everything the catalogue holds, through the same paged walk the cache fill uses — which
        // applies the parental filter, so a Kids profile is never offered a card it may not open.
        val kidsMode = activeProfile?.isKids == true
        val items = mutableListOf<XtreamCatalogItem>()
        listOf(XtreamContentType.MOVIE, XtreamContentType.SERIES).forEach { contentType ->
            val locked = lockedCategoryIdsForBrowsing(contentType)
            var pageIndex = 0
            while (pageIndex < MAX_DISCOVERY_PAGES) {
                val page =
                    runCatching {
                        xtreamRepository.page(
                            contentType = contentType,
                            categoryId = null,
                            query = "",
                            requestedPage = pageIndex,
                            kidsMode = kidsMode,
                            lockedCategoryIds = locked,
                        )
                    }.getOrNull() ?: break
                if (page.items.isEmpty()) break
                items += page.items
                pageIndex += 1
            }
        }
        if (items.isEmpty()) return emptyList()

        val byId = items.associateBy { item -> item.contentIdentity().key }
        val taste =
            TasteProfile(
                favouriteGenres = genresOf(favoriteKeys.mapNotNull { key -> byId[key] }),
                // Repeats on purpose: a genre watched six times counts six times, which is what
                // makes it outweigh one watched once.
                watchedGenres = genresOf(historyEntries.map { entry -> entry.item }),
                seenIds = discoverySeen + favoriteKeys,
            )

        val candidates =
            items.map { item ->
                DiscoveryCandidate(
                    id = item.contentIdentity().key,
                    title = item.name.editorialTitle(),
                    genres = item.categoryIds,
                    year = item.year,
                    rating = item.rating,
                    isSeries = item.contentType == XtreamContentType.SERIES,
                )
            }

        return DiscoveryDeck
            .build(candidates = candidates, taste = taste, session = discoverySession)
            .mapNotNull { candidate -> byId[candidate.id] }
    }

    /** The categories of a set of titles, as the taste profile consumes them. */
    private fun genresOf(items: List<XtreamCatalogItem>): List<String> =
        items.flatMap { item -> item.categoryIds }

    /**
     * Records a decision and moves to the next card.
     *
     * Accepting adds the title to favourites, which is what the product asked for and also what
     * makes the choice worth something: a swipe that only advanced a deck would teach the app about
     * the viewer and give the viewer nothing.
     */
    fun decideDiscovery(
        item: XtreamCatalogItem,
        verdict: DiscoveryVerdict,
    ) {
        val profileId = activeProfileId ?: return
        val key = item.contentIdentity().key

        if (verdict == DiscoveryVerdict.KEPT && key !in favoriteKeys) toggleFavorite(item)

        discoverySession = discoverySession.after(item.categoryIds, verdict)
        discoverySeen = discoverySeen + key
        userStore.setDiscoverySeen(profileId, discoverySeen)
        discoveryDeck = discoveryDeck.filterNot { held -> held.contentIdentity().key == key }
    }

    /**
     * What the card says the film is about.
     *
     * From the details already fetched for this title, when there are any — the card is worth
     * reading without it, so nothing is requested on its behalf. A network call per card would turn
     * a game somebody plays in bursts into a screen that waits.
     */
    fun discoverySynopsis(item: XtreamCatalogItem): String? =
        (movieDetailsStatus as? MovieDetailsStatus.Loaded)
            ?.details
            ?.takeIf { details -> details.title == item.name }
            ?.plot

    /**
     * The genres to print under the title.
     *
     * Category names rather than ids: the ids are the provider's numbering and mean nothing to
     * anybody reading a card.
     */
    fun discoveryGenres(item: XtreamCatalogItem): List<String> {
        val names = xtreamRepository.categories(item.contentType).associateBy(XtreamCategory::providerId)
        return item.categoryIds.mapNotNull { id -> names[id]?.name }
    }

    /** Opens Descobrir, building a deck if there is not one already. */
    fun openDiscovery() {
        favoritesOnly = false
        destination = DesktopDestination.DISCOVER
        if (discoveryDeck.isEmpty()) loadDiscoveryDeck()
    }

    /** Marks today's notice as seen, so it does not return until tomorrow's slot. */
    fun dismissReminderNotice() {
        userStore.setReminderLastShownOn(LocalDate.now().toString())
        reminderNotice = null
    }

    /** Forgets one marked title. */
    fun removeReminder(reminder: StoredReminder) {
        val profileId = activeProfileId ?: return
        reminders = reminders.filterNot { held -> held.identityKey == reminder.identityKey }
        userStore.setReminders(profileId, reminders)
    }

    /** Opens the reminders section. */
    fun openReminders() {
        favoritesOnly = false
        destination = DesktopDestination.REMINDERS
        // Here rather than at startup: the catalogue is what supplies the missing names, and on a
        // cold launch it has not loaded yet. By the time someone opens this screen it has.
        healStoredReminders()
        refreshReminderNotice()
        // Here rather than at startup: it costs one request per followed series, and spending that
        // before the app has drawn anything would make every launch slower for news that can just
        // as well arrive a moment later.
        checkFollowedSeries()
    }

    /**
     * The catalogue row for a marked title, when the library happens to hold one.
     *
     * Null is the ordinary case for an upcoming film and is not a failure: the row is what makes
     * the entry openable, so its absence means the entry is shown but not clickable.
     */
    fun catalogItemForReminder(reminder: StoredReminder): XtreamCatalogItem? =
        xtreamRepository.itemByContentKey(XtreamContentType.MOVIE, reminder.identityKey)
            ?: xtreamRepository.itemByContentKey(XtreamContentType.SERIES, reminder.identityKey)

    /**
     * Opens a marked title's page in the catalogue.
     *
     * Deliberately the same route [openInLibrary] takes, including [selectDailyItem] and
     * [pendingDetailsRequest]: selecting an id alone resolves against the visible page of eighty,
     * so a title from anywhere else in a large catalogue opened whatever happened to be first.
     */
    fun openReminder(item: XtreamCatalogItem) = openTitle(item)

    /**
     * Opens a title's page from anywhere in the app.
     *
     * Four things have to happen together, and every caller that did them by hand got it wrong:
     *
     * 1. [selectDailyItem], not `selectedXtreamItemId` alone — the id resolves against the visible
     *    page of eighty, so a title from elsewhere in a 40,000-item catalogue fell through to
     *    `firstOrNull()` and opened whatever happened to be first.
     * 2. The content type, or the page loads the wrong catalogue.
     * 3. `destination = CATALOG`. The loaders that fetch details live in `XtreamWorkspace`, which
     *    composes for CATALOG and nothing else — leaving the destination on SEARCH or HOME meant the
     *    page opened on "Carregando ficha do filme…" with no request in flight and no way to
     *    recover. That was reported from the search results, and it is the failure this function
     *    exists to make unrepeatable.
     * 4. [pendingDetailsRequest], which is what actually asks for the details once the workspace is
     *    on screen.
     *
     * Search, Lembretes and "já está na sua lista" all route through here now.
     */
    fun openTitle(item: XtreamCatalogItem) {
        // Where the user was, so closing the title returns there instead of somewhere plausible.
        //
        // Opening a title always lands on CATALOG, because that is the only screen whose composable
        // owns the details page and its loaders. That is fine on the way in and wrong on the way
        // out: somebody who pressed Detalhes on a Descobrir card was returned to the catalogue,
        // which they had not been looking at. Remembering the origin costs one field and makes the
        // back button mean what it says.
        // Only a genuine elsewhere. Opening a title while already in the catalogue — from the grid,
        // from search, from a reminder — has nothing to return to, and recording CATALOG here would
        // make the back button a no-op that looks broken.
        titleOpenedFrom = destination.takeIf { it != DesktopDestination.CATALOG }
        selectDailyItem(item)
        xtreamContentType = item.contentType
        destination = DesktopDestination.CATALOG
        pendingDetailsRequest = item.providerId
    }

    /**
     * The screen the current title was opened from, when it was not the catalogue itself.
     *
     * Null once consumed, so a second visit to the catalogue's own details page does not bounce the
     * user back to a screen they left long ago.
     */
    private var titleOpenedFrom: DesktopDestination? = null

    /**
     * Returns to whatever screen the open title was reached from.
     *
     * True when it moved somewhere, so the caller knows whether its own dismissal is still needed:
     * closing a details page opened from the catalogue is a local matter, and closing one opened
     * from Descobrir is a navigation.
     */
    fun closeOpenedTitle(): Boolean {
        val origin = titleOpenedFrom ?: return false
        titleOpenedFrom = null
        destination = origin
        return true
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
                    // Every subscription in the merge, not just the one connected last.
                    //
                    // This used to add a single row for the open session, so somebody browsing two
                    // merged lists saw one — and the switch that turns merging on hides itself
                    // below two sources, which made it unreachable exactly when it was wanted.
                    // Reported as a second list that closed the first, with no switch anywhere.
                    val merged =
                        (xtreamRepository as? SwitchingCatalogueRepository)?.merging?.heldSources.orEmpty()
                    if (merged.size > 1) {
                        val saved = sourceLibrary.sources().associateBy { it.id }
                        merged.forEach { held ->
                            add(
                                DesktopSourceSummary(
                                    id = held.sourceId,
                                    // The name the viewer gave it, which is the only thing telling
                                    // one subscription from another. The stored label is the
                                    // fallback, and the id is never shown: it means nothing to
                                    // anybody reading the sidebar.
                                    name = saved[held.sourceId]?.label?.takeIf { it.isNotBlank() }
                                        ?: held.label,
                                    // Per-source counts are not held once the lists are merged, and
                                    // repeating the merged total on each row would claim every list
                                    // has all of it. Zero reads as "not counted" to the row, which
                                    // shows nothing rather than a wrong number.
                                    itemCount = 0,
                                    kind = DesktopSourceKind.XTREAM_SESSION,
                                    isWorking = held.isWorking,
                                ),
                            )
                        }
                    } else {
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
            }

    val isXtreamSelected: Boolean
        get() {
            val xtreamSourceId = xtreamSummary?.sourceId ?: return false
            if (xtreamSourceId == selectedSourceId) return true
            // Any subscription in the merge counts, not only the one connected last.
            //
            // The sidebar now lists each merged list on its own row, and every one of them is the
            // Xtream catalogue. Matching just the open session would leave a click on the second
            // row reading as "no Xtream selected" and blank the screen.
            return mergedSourceIds().contains(selectedSourceId)
        }

    /** The subscriptions currently merged, empty when the lists are browsed separately. */
    private fun mergedSourceIds(): List<String> =
        (xtreamRepository as? SwitchingCatalogueRepository)
            ?.merging
            ?.heldSources
            .orEmpty()
            .map { it.sourceId }

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

    /**
     * What is on now and next on the selected playlist channel, when its list brought a guide.
     *
     * Read straight from the loaded guide rather than kept in a status field: the lookup is a hash
     * lookup on an id, so there is nothing to load and no in-flight state for the screen to show.
     */
    val selectedChannelNowAndNext: Pair<XtreamEpgProgram?, XtreamEpgProgram?>
        get() {
            if (!xmltvGuideSource.isLoaded) return null to null
            val channel = selectedChannel ?: return null to null
            return xmltvGuideSource
                .shortEpg(channel.tvgId)
                .nowAndNext(System.currentTimeMillis() / 1_000L)
        }

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
     * The streaming services' own logos, by the label this app recognises them as.
     *
     * TMDb publishes the whole directory for a region in one request — every service with its mark —
     * and licenses those marks for exactly this use, with attribution. That is what makes a genuine
     * Netflix or Prime badge legitimate where an imitation would not be.
     *
     * The Serviço selector shipped drawing two-letter monograms, and the reply was that "AP" is not
     * the Prime Video logo — which is fair, since recognising a mark faster than reading a name is
     * the entire point of that selector.
     *
     * Empty until loaded, and empty forever without a key. A missing logo falls back to the monogram
     * on the service's colour: a worse badge, never a broken screen.
     */
    var providerLogos by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** The key the logo directory was built with, so a changed key refetches it. */
    private var providerLogosKey: String? = null

    /**
     * Fills [providerLogos] unless it already holds the answer for this key and region.
     *
     * Both directories are read: films and series list different services — TMDb's film directory
     * for Brazil leads with transactional shops that carry no series at all — and a household
     * browsing either should see the right marks.
     *
     * Failure is silence, by design. This decorates a selector that works without it.
     */
    suspend fun ensureProviderLogos() {
        val key = effectiveMetadataKey()?.takeIf(String::isNotBlank) ?: return
        val region = streamingRegion
        val cacheKey = "$key@$region"
        if (providerLogosKey == cacheKey && providerLogos.isNotEmpty()) return

        val loaded =
            withContext(Dispatchers.IO) {
                runCatching {
                    val client = TmdbClient(key)
                    // Series first, then films: where the two disagree about a service's mark the
                    // film directory wins, since it is the larger catalogue. Neither actually
                    // differs in practice — the logo belongs to the service, not to the medium.
                    val directory =
                        client.watchProviderDirectory(region, forSeries = true) +
                            client.watchProviderDirectory(region, forSeries = false)
                    directory.mapNotNull { entry ->
                        val logo = entry.logoUrl ?: return@mapNotNull null
                        // Matched through the same name rules the rest of the app uses. TMDb says
                        // "Amazon Prime Video" where a playlist says "Prime Video", and "HBO Max"
                        // where it says "Max", so a literal key would miss most of them.
                        val identity = providerIdentityFor(entry.name) ?: return@mapNotNull null
                        identity.label to logo
                    }.toMap()
                }.getOrDefault(emptyMap())
            }

        // Reported once, so whether the selector is showing real marks or falling back to monograms
        // can be read from the log rather than guessed at from a screenshot. Names only — the key is
        // never printed.
        println("[providers] logos loaded: ${loaded.size} (${loaded.keys.sorted().joinToString(", ")})")

        if (loaded.isNotEmpty()) {
            providerLogos = loaded
            providerLogosKey = cacheKey
        }
    }

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
     * The service whose full catalogue is open, or null while the shelves are showing.
     *
     * A shelf holds twenty titles because that is what fits on a rail, and reaching its end is
     * exactly where somebody wonders what else the service carries. This is the answer to that: one
     * service, a gridful of titles, reached from the end of its own shelf.
     */
    var expandedService by mutableStateOf<ExpandedService?>(null)
        private set

    var expandedServiceLoading by mutableStateOf(false)
        private set

    /**
     * Opens the full catalogue for one service.
     *
     * The shelf's own titles are shown at once and replaced when the wider list arrives, so the
     * grid is never empty while the request runs — the viewer has just been looking at those very
     * posters, and blanking them to fetch more of the same reads as the button having lost them.
     */
    fun openServiceCatalogue(shelf: TmdbServiceShelf) {
        val providerId = shelf.tmdbProviderId
        val catalogue = streamingCatalogue
        expandedService =
            ExpandedService(
                provider = shelf.provider,
                titles = shelf.titles,
                // Only a real service can be expanded. "Em breve" is a set of films no provider
                // carries yet, so it has no id and no wider list to ask for — the caller does not
                // offer the button there, and this is the second line of that defence.
                complete = providerId == null || catalogue == null,
            )
        if (providerId == null || catalogue == null) return

        expandedServiceLoading = true
        streamingScope.launch {
            val wider =
                runCatching {
                    withContext(Dispatchers.IO) {
                        catalogue.allTitlesOnService(providerId, streamingKind)
                    }
                }.getOrDefault(emptyList())
            // Checked against what is on screen now: the viewer may have gone back to the shelves
            // or opened another service while this was in flight, and replacing a different
            // service's grid with these titles would be worse than dropping them.
            if (expandedService?.provider?.id == shelf.provider.id) {
                expandedService =
                    ExpandedService(
                        provider = shelf.provider,
                        // An empty answer keeps the shelf's own titles rather than emptying the
                        // grid: a failed request is not the same as a service with nothing on it.
                        titles = wider.ifEmpty { shelf.titles },
                        complete = true,
                    )
            }
            expandedServiceLoading = false
        }
    }

    /** Returns from the expanded catalogue to the shelves. */
    fun closeServiceCatalogue() {
        expandedService = null
        expandedServiceLoading = false
    }

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

    /**
     * Which of the user's own films each streaming service carries.
     *
     * Empty until built, and empty for anyone without a metadata key. The Serviço selector offers the
     * services this knows about *in addition to* any the playlist names in its categories — a list
     * that files films by genre has none of the latter, which is why the selector had nothing to
     * offer and said so.
     */
    var serviceTitleIndex by mutableStateOf(ServiceTitleIndex.EMPTY)
        private set

    /** True while the index is being built, so the selector can say it is working rather than empty. */
    var serviceIndexLoading by mutableStateOf(false)
        private set

    private var serviceIndexBuiltFor: String? = null

    /**
     * The service the catalogue is currently filtered to, by label, or null for all of them.
     *
     * Separate from [selectedXtreamCategoryId] because it is a different kind of filter: a category
     * is one of the provider's own folders, while this is a set of library ids assembled from TMDb.
     * They are mutually exclusive in the UI for the reason set out on [splitCategories] — a title
     * belongs to one category, so combining the two returns an empty grid.
     */
    var selectedServiceLabel by mutableStateOf<String?>(null)
        private set

    /** Filters the catalogue to one service, or clears the filter when [label] is null. */
    suspend fun selectService(label: String?) {
        if (selectedServiceLabel == label) return
        selectedServiceLabel = label
        // A service filter and a category filter answer different questions and cannot both apply:
        // see the note on selectedServiceLabel.
        if (label != null) selectedXtreamCategoryId = null
        refreshXtreamPage(pageIndex = 0)
    }

    /**
     * Builds the service index, unless it is already built for this key and region.
     *
     * ## Why this is cheap
     *
     * The obvious direction — ask TMDb which services carry each of the user's films — costs two
     * requests per title, and on a 42,095-item catalogue that is tens of thousands of requests TMDb
     * would rate-limit long before finishing. So the question is inverted: each service is asked what
     * it carries, several pages at a time, which is a handful of requests per service. The answers are
     * then matched against the library by the same normalised name-and-year rule the details page uses.
     *
     * Failure is silence. This adds a filter to a screen that works without it.
     */
    suspend fun ensureServiceTitleIndex() {
        val key = effectiveMetadataKey()?.takeIf(String::isNotBlank) ?: return
        val region = streamingRegion
        val cacheKey = "$key@$region"
        if (serviceIndexBuiltFor == cacheKey && !serviceTitleIndex.isEmpty) return
        if (serviceIndexLoading) return

        val candidates = libraryMatchCandidates()
        if (candidates.isEmpty()) return

        serviceIndexLoading = true
        val built =
            withContext(Dispatchers.IO) {
                runCatching {
                    val client = TmdbClient(key)
                    // Both catalogues, and both directories.
                    //
                    // This asked only for films, so a service's series were never fetched and the
                    // Séries selector was filtering television against a list of films. Netflix
                    // dropped out of it entirely — reported exactly that way — because none of its
                    // series had ever been looked up to match against.
                    //
                    // The two directories overlap but are not the same: TMDb lists 85 providers for
                    // films in BR and 68 for television. Distinct by provider id so a service in
                    // both is still visited once.
                    val services =
                        (
                            client.watchProviderDirectory(region, forSeries = false) +
                                client.watchProviderDirectory(region, forSeries = true)
                        )
                            // One request per *service*, not per provider id. TMDb lists a service
                            // several times over — "Netflix" and "Netflix Standard with Ads",
                            // "Amazon Prime Video" and "Amazon Video" — and all of them fold to the
                            // same label here, so fetching each id meant fetching the same
                            // catalogue five times. Forty ids collapse to eight services, which is
                            // the difference between three minutes and thirty seconds.
                            .distinctBy { providerIdentityFor(it.name)?.label ?: it.name }
                    val byService = LinkedHashMap<String, MutableList<Pair<String, Int?>>>()
                    services.forEach { service ->
                        // Matched to the label this app recognises the service as, so the selector's
                        // rows and the index agree on what "Netflix" means.
                        val label = providerIdentityFor(service.name)?.label ?: return@forEach
                        val titles = byService.getOrPut(label) { mutableListOf() }
                        // Several pages, because one page is twenty titles and a filter built from
                        // twenty would miss almost everything the user owns. Stops early when a page
                        // comes back short, which means the service has no more to give.
                        // Films and series both, because the one selector filters both tabs.
                        listOf(TmdbDiscoverKind.MOVIES, TmdbDiscoverKind.SERIES).forEach { kind ->
                            for (page in 1..SERVICE_INDEX_PAGES) {
                                val batch =
                                    client.titlesOnProvider(
                                        providerId = service.providerId,
                                        region = region,
                                        limit = SERVICE_INDEX_PAGE_SIZE,
                                        kind = kind,
                                        page = page,
                                    )
                                titles += batch.map { it.title to it.year }
                                if (batch.size < SERVICE_INDEX_PAGE_SIZE) break
                            }
                        }
                    }
                    ServiceTitleIndex.build(
                        serviceTitles = byService,
                        library =
                            candidates.map { candidate ->
                                Triple(candidate.title, candidate.year, candidate.localContentId)
                            },
                    )
                }.getOrDefault(ServiceTitleIndex.EMPTY)
            }

        serviceIndexLoading = false
        if (!built.isEmpty) {
            serviceTitleIndex = built
            serviceIndexBuiltFor = cacheKey
            println(
                "[servicos] indice: " +
                    built.services.joinToString(", ") { name -> "$name=${built.countFor(name)}" },
            )
        } else {
            println("[servicos] indice vazio - nenhum titulo da lista casou com o que a TMDb oferece")
        }
    }

    /**
     * How many pages of each service to read.
     *
     * Twenty titles a page, so ten pages is two hundred per service — enough that a filter finds a
     * useful share of an ordinary library, and few enough that eight services cost eighty requests
     * rather than the tens of thousands a per-title lookup would.
     */
    private val SERVICE_INDEX_PAGES = 10

    /** TMDb's discover page size. Asking for less would waste a request; more is not offered. */
    private val SERVICE_INDEX_PAGE_SIZE = 20

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

    /**
     * Whether this profile is still on the PIN the app ships with.
     *
     * Read by the settings screen, which has to say so: the lock works from the first launch, and
     * 0000 is public knowledge.
     */
    val usingDefaultParentalPin: Boolean
        get() {
            @Suppress("UNUSED_EXPRESSION")
            parentalRevision
            return userStore.parentalLock(activeProfileId).usingDefaultPin
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
    /**
     * The answer from the last call, keyed by everything that can change it.
     *
     * This function reads the preferences store and then walks every category the provider
     * publishes — several hundred on a real list — and it is called from the hot paths rather than
     * the cold ones: twice per read of `historyEntries`, which itself resolves up to two hundred
     * titles and is read on every keystroke in the history search box, and again for each of the
     * three content types when the home screen is built.
     *
     * The key is a plain data class rather than a timestamp: it holds the profile, the content
     * type, the two revisions that already signal a parental or category change, and the unlocked
     * set itself. Every one of those is bumped or mutated by the code that can alter the answer, so
     * a stale entry cannot outlive what it was computed from — and a cache that can go stale on a
     * *parental* control would be far worse than the work it saves.
     *
     * The unlocked set is copied in full rather than counted. Every mutation of it today happens to
     * change its size, so a count would work — but it would work by accident, and the first future
     * edit that swaps one unlocked category for another would silently keep showing the locked one.
     * A handful of category ids is nothing to copy next to the several hundred this function walks.
     */
    private data class LockedCategoriesKey(
        val profileId: String?,
        val contentType: XtreamContentType,
        val parentalRevision: Int,
        val hiddenRevision: Int,
        val unlocked: Set<String>,
    )

    private var lockedCategoriesKey: LockedCategoriesKey? = null
    private var lockedCategoriesValue: Set<String> = emptySet()

    fun lockedCategoryIdsForBrowsing(
        contentType: XtreamContentType = xtreamContentType,
    ): Set<String> {
        val key =
            LockedCategoriesKey(
                profileId = activeProfileId,
                contentType = contentType,
                parentalRevision = parentalRevision,
                hiddenRevision = hiddenCategoriesRevision,
                unlocked = unlockedCategories.toSet(),
            )
        if (key == lockedCategoriesKey) return lockedCategoriesValue

        // Read once, not once per category. `hasParentalPin` and `parentalLock` each hit the Java
        // Preferences store and re-parse a packed string, and this runs on every page of the
        // catalogue — with a provider's several hundred categories that was several hundred
        // preference reads per keystroke in the search box.
        val stored = userStore.parentalLock(activeProfileId)
        val computed =
            if (!stored.hasPin) {
                emptySet()
            } else {
                val lock = ParentalLock(lockAdultCategories = stored.lockAdultCategories)
                xtreamRepository
                    .categories(contentType)
                    .filter { category ->
                        val identity = CategoryPreferenceIdentity.scoped(contentType, category.providerId)
                        identity !in unlockedCategories &&
                            (
                                CategoryPreferenceIdentity.matches(
                                    stored.lockedCategoryIds,
                                    contentType,
                                    category.providerId,
                                ) || lock.requiresPin(null, category.name)
                            )
                    }.map(XtreamCategory::providerId)
                    .toSet()
            }

        // Stored after the work, including the empty answer: "this profile has no PIN" is as
        // expensive to establish as any other result and just as worth keeping.
        lockedCategoriesKey = key
        lockedCategoriesValue = computed
        return computed
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

        // Replacing a PIN somebody chose needs that PIN. Replacing the shipped one does not:
        // 0000 is printed on the settings screen, so demanding it would be theatre — and it would
        // turn "choose a PIN" into "type the one everybody knows, then choose a PIN" for every
        // first-time user.
        if (stored.hasPin && !stored.usingDefaultPin) {
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
    // Sending a title to another screen on the same network
    // -----------------------------------------------------------------------------------------

    /** What the send sheet is doing. Idle means it is closed. */
    var castSendState by mutableStateOf<CastSendState>(CastSendState.Idle)
        private set

    /** The title the open sheet would send. Null whenever [castSendState] is Idle. */
    private var castSendRequest: TitleShareLink? = null

    private var castSendJob: Job? = null

    /**
     * Opens the send sheet for [link] and starts looking for screens.
     *
     * Takes the same [TitleShareLink] the share button builds, for the same reason casting exists:
     * both name a title rather than a location. The receiving screen finds it in its own catalogue
     * and plays from the provider directly, so this machine's credentials never travel.
     */
    fun startCastTo(link: TitleShareLink) {
        castSendRequest = link
        searchForCastTargets()
    }

    /** Looks for screens again. Used when the sheet opens and by "procurar de novo". */
    fun searchForCastTargets() {
        castSendJob?.cancel()
        castSendState = CastSendState.Searching
        castSendJob =
            downloadScope.launch {
                val targets = withContext(Dispatchers.IO) { CastReceiver.discover() }
                lastCastTargets = targets
                castSendState = CastSendState.Found(targets)
            }
    }

    fun chooseCastTarget(target: CastTarget) {
        castSendState = CastSendState.NeedsCode(target)
    }

    /**
     * Reaches a screen by its address, for a network where the search comes back empty.
     *
     * Many home routers drop broadcast between clients, so discovery finds nothing while both
     * devices sit on the same wifi listening — confirmed on a real network here. Without this the
     * feature is simply unavailable to that household, with the dialog offering only a search that
     * will keep failing.
     *
     * Goes straight to the pairing step on success, exactly as choosing a found row does. A failure
     * leaves the dialog where it was, with the typed address still on screen to correct.
     */
    fun connectToCastAddress(address: String) {
        castSendJob?.cancel()
        castSendJob =
            downloadScope.launch {
                val target = withContext(Dispatchers.IO) { CastReceiver.probeAddress(address) }
                if (target == null) {
                    castManualAddressFailed = true
                    return@launch
                }
                castManualAddressFailed = false
                // Remembered like a found one, so "escolher outra tela" comes back to a list holding
                // it rather than to the empty result the search produced.
                lastCastTargets = (lastCastTargets + target).distinctBy(CastTarget::address)
                castSendState = CastSendState.NeedsCode(target)
            }
    }

    /** Whether the last typed address reached nothing, so the field can say so. */
    var castManualAddressFailed by mutableStateOf(false)
        private set

    fun clearCastManualAddressFailure() {
        castManualAddressFailed = false
    }

    /**
     * Back to the list, so a wrong choice does not need the sheet closed and reopened.
     *
     * Shows the screens the last search found rather than searching again: discovery takes over a
     * second, and repeating it because somebody tapped the wrong row would make correcting a
     * mistake slower than making it.
     */
    fun backToCastTargets() {
        castSendState = CastSendState.Found(lastCastTargets)
    }

    /** The screens the last search found, kept so going back does not need another search. */
    private var lastCastTargets: List<CastTarget> = emptyList()

    fun closeCastSend() {
        castSendJob?.cancel()
        castSendJob = null
        castSendRequest = null
        castSendState = CastSendState.Idle
    }

    /**
     * Sends the open title with the code shown on the chosen screen.
     *
     * A malformed code is refused here rather than sent: four digits is the whole contract, and a
     * message the receiver will silently drop is worse than one never sent, because the sender is
     * told nothing either way.
     */
    fun sendToCastTarget(code: String) {
        val target = (castSendState as? CastSendState.NeedsCode)?.target ?: return
        val link = castSendRequest ?: return
        if (!CastMessage.isWellFormedPairingCode(code)) {
            castSendState = CastSendState.NeedsCode(target, badCode = true)
            return
        }

        castSendState = CastSendState.Sending(target)
        castSendJob?.cancel()
        castSendJob =
            downloadScope.launch {
                val message =
                    CastMessage(
                        identity = link.identity,
                        title = link.title,
                        // Zero: this machine sends a title, not a position. Resuming where the
                        // viewer left off would need the receiving end to apply it, and that path
                        // opens the title's page rather than starting playback.
                        positionMillis = 0L,
                        pairingCode = code,
                    )
                val delivered = withContext(Dispatchers.IO) { CastReceiver.send(target, message) }
                castSendState =
                    if (delivered) CastSendState.Sent(target) else CastSendState.Failed(target)
            }
    }

    // -----------------------------------------------------------------------------------------
    // Receiving a title from a phone on the same network
    // -----------------------------------------------------------------------------------------

    private val castReceiver by lazy { CastReceiver(displayName = machineDisplayName()) }

    /** The code to show on screen while receiving is on, or null when it is off. */
    var castPairingCode by mutableStateOf<String?>(null)
        private set

    /**
     * Whether the receiver comes up with the app.
     *
     * On by default. This is a change from the original behaviour, which never started the receiver
     * implicitly on the grounds that a socket should be opened only on request — the owner asked
     * for the opposite, because a receiver that has to be switched on by hand every session is
     * never on at the moment somebody reaches for their phone.
     *
     * What still guards it is the pairing code: a sender has to type four digits shown on this
     * screen, and they change every time the receiver starts. The setting below turns the whole
     * thing off for anyone who would rather open the socket deliberately.
     */
    var castReceiverAutoStart by mutableStateOf(userStore.castReceiverAutoStart())
        private set

    fun changeCastReceiverAutoStart(enabled: Boolean) {
        castReceiverAutoStart = enabled
        userStore.setCastReceiverAutoStart(enabled)
        // Applied at once rather than at the next launch: a switch that says "off" while the socket
        // is still open would be describing something that is not true.
        if (enabled && castPairingCode == null) {
            startCastReceiver()
        } else if (!enabled && castPairingCode != null) {
            castReceiver.stop()
            castPairingCode = null
        }
    }

    /**
     * Starts or stops listening for a phone, for the button that does it by hand.
     *
     * Kept separate from [changeCastReceiverAutoStart]: turning the receiver off for this session is
     * not the same as saying it should never come up again, and collapsing the two would make
     * closing the socket once silently change the setting for every future launch.
     */
    fun toggleCastReceiver() {
        if (castPairingCode != null) {
            castReceiver.stop()
            castPairingCode = null
            return
        }
        startCastReceiver()
    }

    /**
     * Starts listening, reusing this machine's kept code.
     *
     * The code that comes back is written down so the next session offers the same one. Typing it
     * into the phone is therefore a one-time job rather than something to redo on every launch.
     */
    private fun startCastReceiver() {
        castPairingCode =
            castReceiver.start(existingCode = userStore.castPairingCode()) { message ->
                // Logged on arrival, because everything after this point can decline silently and
                // the user is left with "I pressed send and nothing happened". A message that gets
                // here has already passed the pairing code, so the line separates "the phone never
                // reached us" from "it did, and the title was not in this list".
                println("[cast] recebido: ${message.identity.key}")
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
        // Written down whether it was reused or freshly minted, so the next launch offers the same
        // one. Without this the code would still change every session and nothing would improve.
        castPairingCode?.let(userStore::setCastPairingCode)
    }

    /**
     * Throws this machine's code away and starts again with a new one.
     *
     * The way back when a code has been seen by someone it should not have been. Every phone that
     * knew the old one has to be told the new one, which is the point: that is what revoking is.
     */
    fun regenerateCastPairingCode() {
        userStore.clearCastPairingCode()
        if (castPairingCode != null) {
            castReceiver.stop()
            castPairingCode = null
        }
        startCastReceiver()
    }

    /** How this machine introduces itself to a phone looking for screens. */
    private fun machineDisplayName(): String =
        (System.getenv("COMPUTERNAME") ?: System.getProperty("user.name"))
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: "IPTV BURO"

    /**
     * Records a shared or cast title, to be resolved against this machine's catalogue.
     *
     * Only records it. Resolving is left to the effect in Main that watches [pendingShareLink],
     * which used to mean the work happened twice: this function resolved on the caller's thread —
     * for a cast, the socket thread — and the effect then woke on the state change and resolved the
     * same link again, so one send produced two "not in your list" dialogs. One writer, one reader.
     */
    fun submitShareLink(link: TitleShareLink) {
        pendingShareLink = link
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
        //
        // Logged, because this is a silent hold rather than an answer: a title that arrives from a
        // phone before the catalogue is ready simply waits here, and from the sofa that is
        // indistinguishable from the send having failed.
        if (xtreamSummary == null) {
            println("[cast] catálogo ainda não carregado; ${link.identity.key} fica pendente")
            return
        }

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
            println("[cast] '${link.title}' não está nesta lista (${link.identity.key})")
            shareLinkOutcome = ShareLinkOutcome.NotInYourList(link.title)
            return
        }
        println("[cast] abrindo '${resolved.name}'")
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
        /**
         * Reports items as a catalogue streams in, so the bar moves during the longest wait.
         *
         * Separate from [onCatalogueStage]: that one marks a step boundary and moves the bar, this
         * one fires many times inside a single step and changes only the detail line.
         */
        onCatalogueItems: (message: String, progress: CatalogLoadProgress) -> Unit = { _, _ -> },
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
                // Reported while they run, not just before them.
                //
                // These two used to announce themselves once — 0.75 for films, 0.88 for series — and
                // then say nothing for the whole download, which on a real list is tens of seconds.
                // The bar sat at 80% throughout, and a bar that does not move reads as a hang. The
                // repository now calls back as items are parsed, so the count and the rate move.
                // The active language's wording. Read here rather than passed in: this runs from
                // several callers, only one of which has a splash to write to.
                val stageText = DesktopStrings.of(language).shareStrings.startup
                if (XtreamContentType.MOVIE !in latestSummary?.loadedContentTypes.orEmpty()) {
                    onCatalogueStage(0.75f, stageText.downloadingMovies)
                    latestSummary =
                        xtreamRepository.loadCatalog(XtreamContentType.MOVIE) { progress ->
                            onCatalogueItems(stageText.downloadingMovies, progress)
                        }
                }
                if (XtreamContentType.SERIES !in latestSummary?.loadedContentTypes.orEmpty()) {
                    onCatalogueStage(0.88f, stageText.downloadingSeries)
                    latestSummary =
                        xtreamRepository.loadCatalog(XtreamContentType.SERIES) { progress ->
                            onCatalogueItems(stageText.downloadingSeries, progress)
                        }
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
                            // Rotated per day, like every other shelf on this screen. Without it the
                            // shelf showed the year's best-rated eighteen and nothing else, ever —
                            // reported as the releases never changing, with every card reading ★5.0.
                            //
                            // Multipliers differ from the ones the daily pages use so films and
                            // series do not advance in lockstep with each other or with the picks
                            // below them.
                            rotation = date.dayOfYear * 11 + date.year,
                        ),
                    seriesThisYear =
                        xtreamRepository.releasesForYear(
                            XtreamContentType.SERIES,
                            date.year,
                            18,
                            kidsMode,
                            lockedCategoriesByType.getValue(XtreamContentType.SERIES),
                            rotation = date.dayOfYear * 19 + date.year,
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
        // Converted here rather than upstream: this screen's own date handling is JVM
        // LocalDate — reminders, the clock, the day rollover — while the domain model is
        // multiplatform and speaks kotlinx. The boundary is one line wide, so it lives here.
        val collection =
            SeasonalCollections.primaryCollectionFor(
                kotlinx.datetime.LocalDate(date.year, date.monthValue, date.dayOfMonth),
            ) ?: return null
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
            loadGuideFor(catalog)
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

    /**
     * Imports a playlist kept on the user's own server.
     *
     * The address decides the protocol, so the user pastes what their NAS showed them rather than
     * choosing from a menu first. An address this app cannot read fails before any request is made,
     * which is the difference between "that is not an address I understand" and a connection error
     * blamed on their server.
     *
     * The credentials are used for the fetch and then dropped: the imported catalogue lives in
     * memory under the host's name, and nothing about the password reaches disk. Re-importing after
     * a restart means entering it again, which is the deliberate trade — see the source library's
     * note on why remembered credentials are a separate, explicit decision.
     */
    suspend fun importRemotePlaylist(
        url: String,
        username: String?,
        password: String?,
    ) {
        if (importStatus is ImportStatus.Loading) return
        val address = url.trim()
        val protocol = RemotePlaylistProtocol.of(address)
        if (address.isBlank() || protocol == null) {
            importStatus = ImportStatus.Error(DesktopStrings.of(language).shareStrings.remoteSource.unsupportedAddress)
            return
        }

        importStatus = ImportStatus.Loading
        runCatching {
            val source = remotePlaylistSource(protocol, address, username, password)
            withContext(Dispatchers.IO) {
                localRepository.importRemote(source = source, sourceLabel = source.displayName)
            }
        }.onSuccess { catalog ->
            catalogs = catalogs + catalog
            selectedSourceId = catalog.source.id
            selectedCategoryId = null
            selectedChannelId = catalog.channels.firstOrNull()?.id
            loadGuideFor(catalog)
            searchQuery = ""
            importStatus =
                ImportStatus.Success(
                    channelCount = catalog.source.channelCount,
                    warningCount = catalog.warnings.size,
                )
        }.onFailure { error ->
            // The same reset the local import does: a cancelled import otherwise leaves the guard
            // above refusing every later attempt.
            if (importStatus is ImportStatus.Loading) importStatus = ImportStatus.Idle
            error.rethrowIfCancellation()
            importStatus = ImportStatus.Error(error.toSafeImportMessage())
        }
    }

    /**
     * Builds the reader for [address].
     *
     * FTP is split into host, port and path here rather than inside the reader, because the reader
     * is what re-assembles them with the credentials embedded — and keeping that assembly in one
     * place is what stops a password-bearing URL from being built anywhere else.
     */
    private fun remotePlaylistSource(
        protocol: RemotePlaylistProtocol,
        address: String,
        username: String?,
        password: String?,
    ): RemotePlaylistSource =
        when (protocol) {
            RemotePlaylistProtocol.WEBDAV ->
                WebDavPlaylistReader(url = address, username = username, password = password)
            RemotePlaylistProtocol.FTP -> {
                val uri = URI(address)
                FtpPlaylistReader(
                    host = uri.host ?: throw IllegalArgumentException("The address names no server."),
                    path = uri.path.orEmpty().ifBlank { "/" },
                    username = username,
                    password = password,
                    port = uri.port.takeIf { value -> value > 0 },
                )
            }
        }

    suspend fun connectXtream(
        input: XtreamLoginInput,
        /**
         * What to call this list, or blank to name it after its host.
         *
         * The connect form never asked, so every subscription added that way was labelled
         * "cb.visualplay.online" — what the server calls itself, not what the person recognises.
         * Blank keeps that old behaviour for anyone who leaves the field empty.
         */
        listLabel: String = "",
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
                // Recorded in the library so it appears by name under "usar uma lista ja
                // configurada", the same as one added during onboarding. Before this, a
                // subscription connected from the form was usable but nameless — it never showed
                // up in that list at all.
                runCatching {
                    val entry =
                        sourceLibrary.create(
                            listLabel.ifBlank { String(rememberedServer).hostLabel() },
                        )
                    withContext(Dispatchers.IO) {
                        sourceLibrary
                            .store(entry.id)
                            .save(rememberedServer, rememberedUsername, rememberedPassword)
                    }
                }
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
                // Forgotten with the query it described, so the next apply counts as a change and
                // actually runs instead of being mistaken for a repeat.
                appliedXtreamSearch = null
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

    /**
     * The shortest gap worth deriving a rate from.
     *
     * Below this the divisor is small enough that ordinary scheduling jitter produces a wild figure,
     * and a rate that leaps between 400/s and 9,000/s is noise rather than information.
     */
    private val RATE_SAMPLE_MINIMUM_MILLIS = 200L

    /**
     * Thousands as "12,4 mil".
     *
     * An exact five-digit count changing several times a second is unreadable — the eye cannot track
     * the digits — and the figure is there to convey scale and movement, not an audit.
     */
    private fun formatItemCount(count: Int): String =
        if (count >= 1_000) "%.1f mil".format(DISPLAY_LOCALE, count / 1_000.0) else count.toString()

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
        startupDetail = ""
        startupBeatAt = System.currentTimeMillis()
    }

    /**
     * The second line on the splash: how much of the current step has been done.
     *
     * Separate from [startupMessage] because they answer different questions and change at different
     * rates. The message names the step and changes rarely; this carries the count and the rate, and
     * changes several times a second while a catalogue streams in. Empty whenever there is nothing
     * countable to say, which is most steps.
     */
    var startupDetail by mutableStateOf("")
        private set

    /**
     * When the bar last actually moved.
     *
     * The reported defect was a bar stuck at 80% — which was true, and worse, indistinguishable from
     * a hang. A determinate bar is only honest while it is moving; once it stops, the screen has to
     * say "still working" some other way. The splash reads this and switches to an indeterminate bar
     * when nothing has changed for a moment, so the app never looks frozen while it is busy.
     */
    var startupBeatAt by mutableStateOf(System.currentTimeMillis())
        private set

    /**
     * Records progress inside a catalogue download.
     *
     * The rate is computed over the gap between two readings rather than since the download began: a
     * running average flattens out and stops reflecting a network that has just slowed down, which is
     * exactly the moment somebody watching the screen wants to know.
     */
    private fun startupCatalogueProgress(
        stepMessage: String,
        items: Int,
        atMillis: Long,
    ) {
        val elapsed = atMillis - lastCatalogueSampleAt
        val gained = items - lastCatalogueSampleItems
        // Under a fifth of a second the divisor is small enough that ordinary jitter turns into a
        // wild figure, so the previous rate is kept rather than printed as a spike.
        if (elapsed >= RATE_SAMPLE_MINIMUM_MILLIS && gained > 0) {
            catalogueItemsPerSecond = (gained * 1000.0 / elapsed).toInt()
            lastCatalogueSampleAt = atMillis
            lastCatalogueSampleItems = items
        }

        startupMessage = stepMessage
        startupDetail =
            if (catalogueItemsPerSecond > 0) {
                "${formatItemCount(items)} · ${formatItemCount(catalogueItemsPerSecond)}/s"
            } else {
                formatItemCount(items)
            }
        startupBeatAt = atMillis
    }

    /** Reset between catalogues, so the series rate is not dragged down by the films before it. */
    private fun resetCatalogueRate() {
        lastCatalogueSampleAt = System.currentTimeMillis()
        lastCatalogueSampleItems = 0
        catalogueItemsPerSecond = 0
    }

    private var lastCatalogueSampleAt = 0L
    private var lastCatalogueSampleItems = 0
    private var catalogueItemsPerSecond = 0

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

    /**
     * The names of the lists that did not answer, for the screen to report.
     *
     * Empty in the ordinary case, and empty when merging is off — a viewer with one list has
     * nothing to be told about.
     */
    var mergeFailures by mutableStateOf<List<String>>(emptyList())
        private set

    /**
     * Adds every other saved subscription to the merge.
     *
     * Only when the viewer asked for it, and only for the lists beyond the one already open: the
     * first was restored the ordinary way, and re-adding it would show every one of its titles
     * twice.
     *
     * A list that fails is recorded and skipped. One dead subscription blanking a working library
     * would be far worse than the problem merging solves.
     */
    private suspend fun addRemainingSourcesToMerge() {
        val merged = (xtreamRepository as? SwitchingCatalogueRepository)?.merging ?: return
        val alreadyOpen = xtreamRepository.summary()?.sourceId
        val others =
            sourceLibrary
                .sources()
                .filter { source -> source.id != alreadyOpen }
                .take(MergedSources.MAXIMUM_SOURCES - 1)
        if (others.isEmpty()) return

        withContext(Dispatchers.IO) {
            others.forEach { source ->
                // Credentials are read one at a time and handed straight over: the merge holds the
                // vault, and nothing here keeps a second copy.
                val input = runCatching { sourceLibrary.store(source.id).load() }.getOrNull()
                if (input != null) {
                    merged.addSource(sourceId = source.id, label = source.label, input = input)
                }
            }
        }
        mergeFailures = merged.failedSources
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
            // The active language's wording, read once for the whole sequence. The splash is the
            // first screen a user ever sees, and it was in Portuguese whatever they had chosen.
            val stageText = DesktopStrings.of(language).shareStrings.startup
            startupStep(1, stageText.openingSession)
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
                            is SessionXtreamRepository.XtreamLoadStage.Authenticating -> stageText.openingSession
                            is SessionXtreamRepository.XtreamLoadStage.Categories ->
                                when (stage.contentType) {
                                    XtreamContentType.LIVE -> stageText.loadingLiveCategories
                                    XtreamContentType.MOVIE -> stageText.loadingMovieCategories
                                    XtreamContentType.SERIES -> stageText.loadingSeriesCategories
                                }
                            is SessionXtreamRepository.XtreamLoadStage.Channels -> stageText.loadingLiveCategories
                        },
                )
            }
            // The other subscriptions, when the viewer asked for one catalogue.
            //
            // After the first one is open, so the app is usable at the earliest moment: a second
            // list that is slow to answer delays only its own titles rather than the whole start.
            addRemainingSourcesToMerge()

            // The home is built from the catalogue, so loading it here means the first screen is
            // complete when the splash clears rather than filling in afterwards.
            if (xtreamStatus !is XtreamStatus.Error) {
                startupStep(4, stageText.organising)
                loadDailyHome(
                    LocalDate.now(),
                    onCatalogueStage = { progress, message ->
                        // A new step: the rate belongs to the catalogue that is starting, not to the
                        // one that just finished.
                        resetCatalogueRate()
                        startupProgressWithin(from = progress, to = progress, fraction = 1f, message = message)
                    },
                    onCatalogueItems = { message, progress ->
                        startupCatalogueProgress(message, progress.items, progress.atMillis)
                    },
                )
                startupStep(5, stageText.ready)
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
            appliedXtreamSearch = null
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

    /**
     * The query that produced the page currently on screen.
     *
     * Null means "no page has been built for any query yet", which is not the same as the empty
     * string: the first apply with an empty box is still a real transition from nothing loaded to
     * everything. Cleared by [clearXtreamUiState] and wherever the session's query is reset.
     */
    private var appliedXtreamSearch: String? = null

    /**
     * Runs the search, and returns to the first page only when the query actually changed.
     *
     * The unconditional `pageIndex = 0` that used to be here sent the catalogue home every time this
     * ran — and it runs from a `LaunchedEffect` in `XtreamWorkspace`, which re-enters whenever that
     * composable is composed again. Returning from a title's page does exactly that, so paging to
     * the second page, opening a film and pressing back put the user back on page one. On a
     * catalogue of forty thousand titles everything past the first eighty is reachable only by
     * paging, so that walk had to be repeated after every single film.
     *
     * Resetting on a genuinely new query stays: results for "duna" have no page seven, and holding
     * the old index there would show an empty grid.
     */
    suspend fun applyXtreamSearch() {
        val query = xtreamSearchQuery
        if (appliedXtreamSearch == query) return
        appliedXtreamSearch = query
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

    /**
     * Set when a playlist named a guide that could not be read, for the diagnostics panel.
     *
     * Not surfaced as an error: the list plays regardless, and the viewer has no action to take
     * against a third-party address that is down.
     */
    var guideUnavailable by mutableStateOf(false)
        private set

    /**
     * Set when an operator's connection was collected and saved this launch.
     *
     * Only so the onboarding screen can say the list is already there rather than asking the
     * viewer to type one in.
     */
    var provisionedSourceLabel by mutableStateOf<String?>(null)
        private set

    /**
     * Collects a connection a reseller set up for this machine, and saves it as a new source.
     *
     * Runs once at startup. Almost every launch finds nothing — that is every machine no operator
     * has ever configured — so it must be silent and must not delay the window.
     *
     * **Added, never substituted.** Whatever the viewer already had stays exactly where it was:
     * this arrives from outside the machine, and a remote action that deletes someone's own
     * playlists is not one this app performs. The new source becomes the selected one, which is
     * what the customer who just asked for help is expecting to see.
     */
    suspend fun collectProvisionedSource() {
        val source =
            try {
                withContext(Dispatchers.IO) { provisioningClient.claim() }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                // Nothing was asked for and nothing is broken; the app opens as usual.
                return
            } ?: return

        try {
            // A delivery need not carry a connection at all. A seller whose customer already has a
            // working list may send only a TMDb key, or only a new name for it, and demanding the
            // address and password again just to hand over a key is what was reported.
            val server = source.server
            val username = source.username
            val password = source.password
            val entry =
                if (server != null && username != null && password != null) {
                    // The name the seller chose, or the host when they chose none.
                    sourceLibrary
                        .create(
                            source.listLabel?.takeIf(String::isNotBlank)
                                ?: String(server).hostLabel(),
                        ).also { created ->
                            withContext(Dispatchers.IO) {
                                sourceLibrary.store(created.id).save(server, username, password)
                            }
                        }
                } else {
                    null
                }
            // The metadata keys, when the seller set those up too. Applied only when sent: an
            // absent key means "leave alone", so replacing a provider address that went down
            // cannot wipe a key the viewer configured themselves.
            source.metadataKey?.let { key -> userStore.setMetadataApiKey(String(key)) }
            source.criticsKey?.let { key -> userStore.setCriticScoresApiKey(String(key)) }
            // Only when a list actually arrived. The onboarding screen says "a sua lista ja esta
            // aqui", which would be a lie for a delivery that carried only a key.
            entry?.let { created -> provisionedSourceLabel = created.label }
            // Only once it is actually saved. Confirming at the moment of delivery would leave a
            // customer whose app closed in between with no list and no way to ask again.
            withContext(Dispatchers.IO) { provisioningClient.confirmApplied() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            // Reported so the seller sees it in their panel: their customer can only say that it
            // did not work, and the wrong password they typed is visible only on their side.
            withContext(Dispatchers.IO) {
                runCatching { provisioningClient.reportFailure("apply_failed") }
            }
        } finally {
            // The credentials are in the protected store now, or the attempt failed; either way
            // they have no reason to stay in this process's memory.
            source.clear()
        }
    }

    /**
     * A name for the list, taken from the address.
     *
     * The seller names nothing — their form has three fields, none of them a label — so the host
     * is what is left. Never the whole address: it is shown on screen and can carry a query.
     */
    private fun String.hostLabel(): String =
        runCatching { java.net.URI(this).host }.getOrNull()?.takeIf(String::isNotBlank)
            ?: "Minha lista"

    /**
     * Loads the guide a freshly imported playlist points at, if it points at one.
     *
     * Deliberately quiet. The guide is an enhancement over a list that already works, and it is
     * fetched from a third address that may simply be down — so a failure leaves the channels
     * playing and the schedule absent, with nothing for the viewer to dismiss.
     */
    private suspend fun loadGuideFor(catalog: ImportedCatalog) {
        if (catalog.epgUrls.isEmpty()) return
        // Only the fetch is swallowed. Cancellation has to keep travelling — the alternative is a
        // closing window that waits on a download of tens of megabytes — and there is no status to
        // put back on the way out: this loader has no in-flight flag and no spinner, so nothing
        // can be left stranded by leaving early.
        try {
            withContext(Dispatchers.IO) { xmltvGuideSource.load(catalog.epgUrls) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            // A guide that will not load leaves a list that still plays.
            guideUnavailable = true
        }
    }

    /** Seconds in a day, for turning the provider's catch-up window into an epoch cut-off. */
    private val SECONDS_PER_DAY = 86_400L

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
                        // Only where there is something to play. A programme is offered when it has
                        // finished, has both of its times, and started inside the window the
                        // provider says it keeps — offering one older than that produces a button
                        // the server refuses.
                        past =
                            if (selected.catchUpDays == null) {
                                emptyList()
                            } else {
                                val earliest =
                                    nowSeconds - selected.catchUpDays!!.toLong() * SECONDS_PER_DAY
                                epg.programs
                                    .filter { program ->
                                        val start = program.startEpochSeconds
                                        val end = program.endEpochSeconds
                                        start != null && end != null && end <= nowSeconds && start >= earliest
                                    }
                                    // Most recent first: "what did I miss" is nearly always about
                                    // the last few hours rather than about three days ago.
                                    .sortedByDescending { program -> program.startEpochSeconds ?: 0L }
                            },
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
        // Every early return here is silent, and each one leaves the screen on its initial
        // "Carregando ficha do filme…" with nothing in flight and no way to recover. Reported from
        // Descobrir, and diagnosing it needs the reason on the record rather than inferred.
        val selected = selectedXtreamItem
        if (selected == null) {
            println("[details] sem item selecionado (destino=$destination)")
            return
        }
        if (selected.contentType != XtreamContentType.MOVIE) return
        if (movieDetailsStatus is MovieDetailsStatus.Loading) return
        movieDetailsStatus = MovieDetailsStatus.Loading

        runCatching {
            withContext(Dispatchers.IO) {
                xtreamRepository.movieDetails(selected.providerId)
            }
        }.onSuccess { details ->
            // The answer arrived for a title that is no longer the one on screen.
            //
            // Dropping it is right — it belongs to a different film — but the status must come back
            // to Idle with it. Leaving it on Loading meant the guard at the top of this function
            // refused every later attempt, so the page kept "Carregando ficha do filme…" for ever
            // with nothing in flight and no way to recover short of restarting the app. The same
            // reasoning as the onFailure branch below, which had already been fixed for it.
            if (selectedXtreamItemId != selected.providerId) {
                if (movieDetailsStatus is MovieDetailsStatus.Loading) movieDetailsStatus = MovieDetailsStatus.Idle
                return@onSuccess
            }
            run {
                movieDetailsStatus = MovieDetailsStatus.Loaded(details)
                // Asked for alongside the details rather than with them: the score is a separate
                // TMDb lookup, and a page that waited for it would be slower for something that is
                // an ornament next to the synopsis and the cast.
                loadAudienceScore(selected.name.editorialTitle(), selected.year)
                // A cover for the catalogue TMDb answers nothing for. Only when the provider sent
                // none, and only with a key: without one this returns immediately.
                loadAdultArtwork(selected.name.editorialTitle(), selected.artworkUrl)
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
                // The same lookup the film loader performs, against TMDb's series catalogue.
                //
                // Series had no audience score, no critics' row and no source mark, because this
                // call simply was not here — the page showed the provider's own star and nothing
                // else. Reported as the ratings being missing from séries.
                loadAudienceScore(
                    title = selected.name.editorialTitle(),
                    year = selected.year,
                    isSeries = true,
                )
                loadAdultArtwork(selected.name.editorialTitle(), selected.artworkUrl)
            } else {
                // Same reason as the film loader: the answer belongs to a title that is no longer
                // showing, so it is dropped — but the status has to come back with it, or the guard
                // above refuses every later attempt and the spinner never ends.
                if (seriesDetailsStatus is SeriesDetailsStatus.Loading) seriesDetailsStatus = SeriesDetailsStatus.Idle
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
            // Remembered so a stream that fails can be asked for from another subscription.
            lastPlaybackTarget = target
            DesktopPlaybackRequest(
                title = title.take(180),
                uri = xtreamRepository.buildConfirmedPlaybackUri(target),
                progressIdentity = playbackIdentity(target),
                startPositionMillis = startPositionMillis.coerceAtLeast(0L),
                // A channel cannot be read ahead of the broadcast, so it keeps the small buffer.
                // Everything else is a file, and reads two minutes ahead of the picture.
                isLive =
                    target is XtreamPlaybackTarget.CatalogItem &&
                        target.contentType == XtreamContentType.LIVE,

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

        // A film recovered before its sidecar was written, now superseded by its real key.
        //
        // The copy is moved into place before the sidecar beside it is written, so a refresh
        // landing in that window recovers the film under the sanitised file name —
        // `movie_dupla-perigosa_2026` — which matches nothing else the app holds. Once the sidecar
        // exists the same file comes back under `movie:dupla-perigosa:2026`, and keeping both left
        // the film listed twice: once with its poster and once without. Reported from a real
        // machine, with a screenshot of the two rows.
        val supersededByRealKey =
            stored.keys
                .map(downloadManager::safeName)
                .filter { sanitised -> !stored.containsKey(sanitised) }
                .toSet()

        // Disk wins for keys we know nothing about; in-memory wins for the rest, since a running
        // download already has the live title and the sidecar is only written on completion.
        downloadMetadata = (stored + downloadMetadata) - supersededByRealKey
        // Anything on disk is complete, including a key still marked Running in memory: the file
        // being there is proof the transfer finished. Skipping those left the finished download
        // stuck at 100% with a Cancel button while its own copy appeared as a second row.
        downloads =
            (downloads + stored.keys.associateWith { DownloadState.Completed }) - supersededByRealKey
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
                // The timeshift endpoint serves a transport stream, the same as the live channel it
                // was recorded from. Naming it anything else would write a .ts under an extension
                // the player then refuses to open from the library.
                is XtreamPlaybackTarget.CatchUp -> "ts"
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
            // A recorded programme is filed like a film: it has a beginning and an end, so stopping
            // halfway and coming back to it is the same act. Its contentKey already carries the
            // start time, so two showings of the same programme are two entries rather than one.
            is XtreamPlaybackTarget.CatchUp ->
                PlaybackProgressIdentity(
                    profileId = profileId,
                    sourceId = LIBRARY_SCOPE,
                    contentId = target.contentKey,
                    contentType = PlaybackContentType.MOVIE,
                )
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
            // A channel from an M3U playlist is live, whatever the file extension suggests.
            DesktopPlaybackRequest(channel.name.take(180), uri, isLive = true)
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

        // Matched against the credits rather than swept for.
        //
        // The old sweep asked the provider for each film's cast in turn, which is one network
        // request per film. Against a catalogue of forty thousand that is impossible, so it stopped
        // after four hundred — always the same four hundred, from the top of the list — and showed
        // whatever happened to be in them. Reported as an actor's page listing random films, and it
        // was: under one percent of the library, chosen by position rather than by the person.
        //
        // TMDb has already named everything they are in. Turning that into "which of these do I
        // own" is a lookup against the library, which is held in memory and costs nothing.
        val discovered =
            withContext(Dispatchers.Default) {
                val creditNames =
                    selectedPerson
                        ?.credits
                        .orEmpty()
                        .mapNotNull { credit -> credit.title.normalisedForMatching().takeIf(String::isNotBlank) }
                        .toHashSet()
                if (creditNames.isEmpty()) {
                    emptyList()
                } else {
                    xtreamRepository.findByTitles(creditNames, MAX_FILMOGRAPHY_ITEMS)
                }
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
        // A merged subscription is not somewhere to switch to.
        //
        // Every merged row is the same one catalogue, so clicking one changes nothing about what is
        // shown — and falling through would clear the open category and channel as if the viewer
        // had moved to a different list.
        if (mergedSourceIds().contains(sourceId)) {
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

    // -----------------------------------------------------------------------------------------
    // The search tab
    // -----------------------------------------------------------------------------------------

    /**
     * What is typed into the search tab.
     *
     * Separate from [searchQuery], which narrows whatever catalogue is currently open. These answer
     * different questions and sharing one field would make each screen change the other: typing in
     * the search tab would silently filter the catalogue behind it.
     */
    var globalSearchQuery by mutableStateOf("")
        private set

    /** What the search found. Empty until two characters are typed, and while nothing matches. */
    var globalSearchResults by mutableStateOf<List<XtreamCatalogItem>>(emptyList())
        private set

    private var globalSearchJob: Job? = null

    /**
     * Opens the search tab, leaving whatever was typed before.
     *
     * Coming back to a search that is still on screen is the ordinary case — someone opens a
     * result, decides it is the wrong film, and returns to the list they already built.
     */
    fun openSearch() {
        favoritesOnly = false
        destination = DesktopDestination.SEARCH
    }

    /**
     * Runs a search over everything loaded, off the UI thread.
     *
     * Debounced by cancelling the previous job rather than by a timer: a catalogue walk of forty
     * thousand items is fast but not free, and a fast typist would otherwise start one per
     * keystroke and leave them all running.
     */
    fun updateGlobalSearch(query: String) {
        globalSearchQuery = query.take(MAX_SEARCH_LENGTH)
        val needle = globalSearchQuery.trim()
        globalSearchJob?.cancel()

        if (needle.length < MIN_GLOBAL_SEARCH_QUERY) {
            globalSearchResults = emptyList()
            return
        }

        globalSearchJob =
            downloadScope.launch {
                val found = withContext(Dispatchers.IO) { xtreamRepository.search(needle) }
                // Ignore an answer for a query the user has already moved on from.
                if (globalSearchQuery.trim() == needle) globalSearchResults = found
            }
    }

    fun clearGlobalSearch() {
        globalSearchJob?.cancel()
        globalSearchQuery = ""
        globalSearchResults = emptyList()
    }

    fun forgetSelectedSource() {
        if (isXtreamSelected) {
            disconnectXtream()
            return
        }
        val sourceId = selectedCatalog?.source?.id ?: return
        localRepository.forget(sourceId)
        catalogs = catalogs.filterNot { it.source.id == sourceId }
        // The guide belongs to the list that named it, so it goes when the list does.
        if (catalogs.isEmpty()) xmltvGuideSource.clear()
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
        xmltvGuideSource.clear()
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
        // Captured on the calling thread with every other input, for the reason above.
        val collapse = collapsesDuplicateTitles
        // The service filter, as the set of library ids that service carries. Null when no service is
        // selected, which is the ordinary case and costs nothing.
        val serviceIds =
            selectedServiceLabel?.let { label -> serviceTitleIndex.idsFor(label).takeIf { it.isNotEmpty() } }
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
                    // One card per film unless the user asked for every copy. A provider carries the
                    // same title several times over and the grid listed all of them.
                    collapseDuplicates = collapse,
                    // Restricted to one service's titles when the user picked one. The ids come from
                    // the TMDb-built index, since this playlist's categories do not name services.
                    allowedLocalIds = serviceIds,
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
        appliedXtreamSearch = null
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
     * Whether the catalogue collapses a provider's repeated copies of one film.
     *
     * On by default — see the note on the store's `collapsesDuplicateTitles`. Reported as duplicate
     * films filling the Filmes grid.
     */
    var collapsesDuplicateTitles by mutableStateOf(userStore.collapsesDuplicateTitles())
        private set

    suspend fun changeCollapsesDuplicateTitles(value: Boolean) {
        collapsesDuplicateTitles = value
        userStore.setCollapsesDuplicateTitles(value)
        // The grid is already drawn from a page that was built under the old setting, so it has to be
        // fetched again for the change to be visible at all.
        if (isXtreamSelected) refreshXtreamPage(pageIndex = 0)
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
        DesktopStrings.of(language).shareStrings.screens.let { text ->
            when (this) {
                is NoSuchFileException -> text.importFileMissing
                is AccessDeniedException -> text.importAccessDenied
                is SecurityException -> text.importBlocked
                // The generic case keeps its own entry, which names the accepted formats.
                else -> text.importFailed
            }
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
        // Listening from the moment the app opens, unless this machine has been told not to. The
        // failure it fixes is mundane: the phone searches, finds nothing, and reports an empty
        // network — when the only thing missing was a switch on this side nobody had flipped.
        if (castReceiverAutoStart) startCastReceiver()
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
        FailureMessages.forFailure(
            error = this,
            logLocation = DiagnosticLog.location().toString(),
            // The active language's wording. Read here rather than held as a field: the user can
            // change language while the app is open, and a cached copy would answer the next
            // failure in the language they just left.
            text = DesktopStrings.of(language).shareStrings.failures,
        )

    private companion object {
        const val MAX_SEARCH_LENGTH = 120

        /** Shortest query worth walking a catalogue for; one letter matches most of it. */
        const val MIN_GLOBAL_SEARCH_QUERY = 2

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

/**
 * One service's full catalogue, as the expanded view behind a shelf shows it.
 *
 * [complete] says whether this is the wider list or still the shelf's own twenty titles waiting to
 * be replaced. The screen uses it to decide whether "these are all of them" is a claim it can make.
 */
data class ExpandedService(
    val provider: StreamingProvider,
    val titles: List<ExternalTitle>,
    val complete: Boolean,
)

/**
 * How many followed series are checked for new episodes in one pass.
 *
 * Each costs a request, so this is a ceiling on what opening the app is allowed to spend. Twenty is
 * more series than most people follow at once, and the ones past it are checked on a later pass
 * rather than never — the list is stable, so the same twenty are not re-checked for ever.
 */
private const val FOLLOWED_SERIES_LIMIT = 20

/** How often the fill reports progress. Setting Compose state per poster costs more than the download. */
private const val CACHE_PROGRESS_STEP = 25

/** A bound on the walk, so a repository that never returns an empty page cannot loop for ever. */
private const val MAX_CACHE_PAGES = 600

/** Bounds the walk that gathers candidates, for the same reason the cache fill is bounded. */
private const val MAX_DISCOVERY_PAGES = 400

enum class DesktopDestination {
    HOME,
    /** The search tab: one box that reaches films, series and live channels at once. */
    SEARCH,
    CATALOG,
    FAVORITES,
    DOWNLOADS,
    CONTINUE,
    MUSIC,
    SUBSCRIPTIONS,
    HISTORY,
    /** Cards to keep or pass over, one at a time. */
    DISCOVER,
    /** Titles the viewer marked to come back to, including ones not in the catalogue yet. */
    REMINDERS,
}

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
        /**
         * What has already finished, most recent first, when the channel keeps a recording of it.
         *
         * Kept apart from [schedule] rather than merged into it: the schedule answers "what is on",
         * and opening it on this morning's programmes would bury that. This answers a different
         * question — "what did I miss" — and is empty for a channel with no recorder, so the
         * catch-up section simply does not appear where it cannot work.
         */
        val past: List<XtreamEpgProgram> = emptyList(),
    ) : LiveEpgStatus

    /** EPG is optional and must never block channel playback. */
    data object Unavailable : LiveEpgStatus
}

/** UI-facing state of an offline copy. */
/** What the "send to another screen" sheet is doing, and what it should show. */
sealed interface CastSendState {
    /** Closed. */
    data object Idle : CastSendState

    data object Searching : CastSendState

    /**
     * Screens found. Empty is a real answer and needs its own wording — plenty of home routers keep
     * wifi and ethernet apart and drop the broadcast, so "none found" is not "none exist".
     */
    data class Found(val targets: List<CastTarget>) : CastSendState

    /** A screen was chosen and is waiting for the code shown on it. */
    data class NeedsCode(val target: CastTarget, val badCode: Boolean = false) : CastSendState

    data class Sending(val target: CastTarget) : CastSendState

    /**
     * Delivered.
     *
     * Says "sent", never "playing". A receiver answers a wrong code with silence, so this machine
     * genuinely cannot tell a mistyped code from a screen that stopped listening, and claiming
     * playback started would be a guess presented as fact.
     */
    data class Sent(val target: CastTarget) : CastSendState

    /** The bytes did not arrive: the screen went away, or the network refused the connection. */
    data class Failed(val target: CastTarget) : CastSendState
}

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
