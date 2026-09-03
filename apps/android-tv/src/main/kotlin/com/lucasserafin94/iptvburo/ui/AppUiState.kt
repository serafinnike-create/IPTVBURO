package com.lucasserafin94.iptvburo.ui

import com.lucasserafin94.iptvburo.metadata.CriticScores
import com.lucasserafin94.iptvburo.domain.model.BrowsableItem
import com.lucasserafin94.iptvburo.domain.model.CacheBudget
import com.lucasserafin94.iptvburo.domain.model.CacheFillProgress
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.CatalogueFilter
import com.lucasserafin94.iptvburo.domain.model.CatalogueLayout
import com.lucasserafin94.iptvburo.domain.model.applyCatalogueFilter
import com.lucasserafin94.iptvburo.domain.model.availableGenres
import com.lucasserafin94.iptvburo.domain.model.availableYears
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.ContentKind
import com.lucasserafin94.iptvburo.domain.model.OfferReason
import com.lucasserafin94.iptvburo.domain.model.ParentalLock
import com.lucasserafin94.iptvburo.domain.model.NotificationCentre
import com.lucasserafin94.iptvburo.domain.model.Reminder
import com.lucasserafin94.iptvburo.domain.model.SessionTaste
import com.lucasserafin94.iptvburo.domain.model.ReminderPolicy
import com.lucasserafin94.iptvburo.domain.model.SourceType
import com.lucasserafin94.iptvburo.domain.model.StreamingDiscoveryCapability
import com.lucasserafin94.iptvburo.domain.model.SubtitlePresentation
import com.lucasserafin94.iptvburo.ui.cast.CastUiState
import java.time.LocalTime

data class ProfileUi(
    val id: String,
    val name: String,
    val avatarKey: String,
    val isKids: Boolean,
    /** A photo the user chose. Null draws the avatar, which is the default and stays valid. */
    val photoUri: String? = null,
    /** The playlist this profile signs in to, or null for no preference. */
    val sourceId: String? = null,
)

data class SourceUi(
    val id: String,
    val name: String,
    val channelCount: Int,
    val type: SourceType = SourceType.LOCAL_M3U,
)

/**
 * Why a PIN action was refused.
 *
 * An enum rather than a message, so the screen renders it in the user's language and the ViewModel
 * stays free of resources.
 */
enum class ParentalMessage {
    /** Not four digits. */
    BAD_FORMAT,

    /** The current PIN did not match — also what a failed unlock reports. */
    WRONG_PIN,
}

/** A locked category the viewer is trying to open, held until the PIN is answered or dismissed. */
data class PendingUnlockUi(
    val categoryId: String?,
    val categoryName: String,
)

data class CategoryUi(
    val id: String?,
    val name: String,
    val channelCount: Int,
    /** Representative artwork from an actual item in this category. */
    val artworkUrl: String? = null,
)

data class ChannelUi(
    val id: String,
    val sourceId: String = "",
    val name: String,
    val categoryName: String?,
    val streamUrl: String = "",
    val logoUrl: String?,
    val requestHeaders: Map<String, String> = emptyMap(),
    val contentType: CatalogContentType = CatalogContentType.UNKNOWN,
    val providerItemId: String? = null,
    val seriesId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val year: Int? = null,
    val rating: Double? = null,
) {
    override fun toString(): String =
        "ChannelUi(" +
            "id=$id, sourceId=$sourceId, name=$name, categoryName=$categoryName, " +
            "streamUrl=${if (streamUrl.isBlank()) "<unresolved>" else "<redacted>"}, " +
            "logoUrl=${if (logoUrl == null) "null" else "<redacted>"}, " +
            "requestHeaderNames=${requestHeaders.keys.sorted()}, " +
            "contentType=$contentType, providerItemId=$providerItemId)"
}

data class SeriesDetailsUi(
    val title: String,
    val plot: String?,
    val episodes: List<EpisodeUi>,
    val artworkUrl: String? = null,
    val backdropUrl: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val rating: Double? = null,
    val youtubeTrailerId: String? = null,
)

data class MovieDetailsUi(
    val title: String,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val duration: String?,
    val releaseDate: String?,
    val country: String?,
    val rating: Double?,
    /** How many people voted, which is what gives [rating] its weight. */
    val voteCount: Int? = null,
    val artworkUrl: String?,
    val backdropUrl: String?,
    val youtubeTrailerId: String?,
)

/**
 * A title as the share sheet needs to describe it.
 *
 * Carries what the *screen* knows, not what may be shared: [TitleShareLink] applies that rule, and
 * drops a provider-hosted poster rather than trusting the caller to have filtered one out. Keeping
 * the two apart means a new call site cannot leak an artwork URL by forgetting a check.
 */
data class ShareRequestUi(
    val kind: ContentKind,
    val title: String,
    val year: Int?,
    val artworkUrl: String?,
    val description: String?,
)

/**
 * A title as casting needs to name it, held while the cast sheet is open.
 *
 * Carries the identity rather than a URL, which is the whole point of the feature: the receiving
 * screen looks the title up in *its own* list and plays from the provider itself, so this device's
 * credentials never travel.
 */
data class CastRequestUi(
    val identity: ContentIdentity,
    val title: String,
    val positionMillis: Long = 0L,
)

data class EpisodeUi(
    val id: String,
    val title: String,
    val seasonNumber: Int,
    val episodeNumber: Int?,
    val artworkUrl: String? = null,
)

data class LiveProgramUi(
    val title: String,
    val description: String? = null,
    /**
     * When the programme starts and ends, in epoch seconds, or null when the provider omitted it.
     *
     * Carried so the guide can print a time against each entry. An entry without a start is still
     * listed — the title is worth having — but it cannot be placed on a clock, and the guide says
     * so rather than inventing one.
     */
    val startEpochSeconds: Long? = null,
    val endEpochSeconds: Long? = null,
)

enum class SourceImportMethod {
    M3U_FILE,
    XTREAM,
    STALKER,
}

/**
 * Why a Stalker portal connection failed, kept separate from the generic import error flag.
 *
 * A portal rejecting the MAC and a portal being unreachable need very different advice, so the
 * reason survives as far as the screen instead of collapsing into "could not connect".
 */
enum class StalkerFailureUi {
    /** The portal answered but did not recognise the MAC: it is not registered there. */
    UNAUTHORISED,

    /** The subscription exists but the portal marked it blocked or expired. */
    BLOCKED,

    /** Network, DNS or TLS failure: the portal was never reached. */
    NETWORK,

    /** The portal answered with something the client cannot parse. */
    MALFORMED,

    /** The MAC or portal URL did not pass local validation, or the portal returned nothing. */
    INVALID_INPUT,
}

/** UI-facing state of an offline copy. Mirrors the desktop `DownloadState`. */
sealed interface DownloadStateUi {
    data object Idle : DownloadStateUi

    /**
     * Tapped, but no bytes yet.
     *
     * Resolving an authenticated URL takes a few seconds against a slow provider, and during that
     * window the button used to read "0%", which is indistinguishable from a download that started
     * and is going nowhere. Users read that as nothing having happened and tapped again.
     */
    data object Preparing : DownloadStateUi

    /**
     * [fraction] is negative when the server did not report a content length.
     *
     * [bytesPerSecond] is null until two progress reports have arrived — a rate needs an interval,
     * and inventing one from a single sample would show a figure that is not a measurement.
     */
    data class Running(
        val fraction: Float,
        val bytesPerSecond: Long? = null,
    ) : DownloadStateUi

    data object Completed : DownloadStateUi

    data object Failed : DownloadStateUi
}

/** A download as the UI needs it: identity, a human name, and current state. */
data class DownloadEntryUi(
    val contentKey: String,
    val title: String,
    val state: DownloadStateUi,
    /** The title's artwork, so the list is recognisable at a glance rather than a wall of names. */
    val artworkUrl: String? = null,
) {
    /**
     * Whether this is an episode rather than a film.
     *
     * Read from the key rather than stored: [episodeDownloadKey] appends `|s<season>e<episode>`,
     * and a film's key never does. Adding a field would mean every existing stored download had a
     * wrong one until it was downloaded again.
     */
    val isEpisode: Boolean
        get() = EPISODE_KEY_SUFFIX.containsMatchIn(contentKey)

    private companion object {
        val EPISODE_KEY_SUFFIX = Regex("""\|s\d+e""")
    }
}

data class ContinueWatchingUi(
    val channel: ChannelUi,
    val progress: Float,
)

data class WatchHistoryUi(
    val channel: ChannelUi,
    val progress: Float,
    val completed: Boolean,
    val lastWatchedAtEpochMillis: Long,
)

data class PersonDetailsUi(
    val name: String,
    val photoUrl: String? = null,
    val biography: String? = null,
    val birthday: String? = null,
    val placeOfBirth: String? = null,
    val credits: List<PersonCreditUi> = emptyList(),
    val isLoading: Boolean = false,
    val metadataConfigured: Boolean = false,
)

data class PersonCreditUi(
    val title: String,
    val year: Int?,
    val posterUrl: String?,
    val character: String?,
    /**
     * TMDb's own id, which is what makes the row openable.
     *
     * Null when the response omitted it; such a credit is still listed, but it cannot be looked up
     * and so is not offered as something to tap — a row that does nothing when pressed is worse
     * than one that plainly is not a button.
     */
    val externalId: String? = null,
    val isSeries: Boolean = false,
)

/**
 * Key an offline copy is filed under.
 *
 * Derived from what the content *is* rather than from the provider's numbering, so a stored copy is
 * still recognised after the user replaces their playlist. The view model and the screens both
 * derive it here so the button the user presses and the row the download lands in cannot drift.
 */
internal fun movieDownloadKey(title: String): String =
    ContentIdentity.of(
        kind = ContentKind.MOVIE,
        title = title,
        year = ContentIdentity.yearFromTitle(title),
    ).key

/**
 * Key for one episode, expressed relative to its series.
 *
 * Season and episode numbers are stable across providers in a way episode ids are not.
 */
internal fun episodeDownloadKey(
    seriesTitle: String,
    episode: EpisodeUi,
): String =
    buildString {
        append(
            ContentIdentity.of(
                kind = ContentKind.SERIES,
                title = seriesTitle,
                year = ContentIdentity.yearFromTitle(seriesTitle),
            ).key,
        )
        append("|s")
        append(episode.seasonNumber)
        append("e")
        append(episode.episodeNumber ?: 0)
    }

/**
 * The episodes a bulk download would actually fetch, in playing order.
 *
 * Episodes already on disk are left out. `startDownload` refuses a download that is *running*, but
 * says nothing about one already stored — so without this rule, "Baixar temporada" on a season the
 * viewer already has would re-fetch every file, spending their data and the provider's bandwidth to
 * produce bytes that are already there.
 *
 * Deliberately not applied to a single "Baixar": tapping one episode's own button on a stored file
 * is a person asking for it again, most often because the copy is broken. A bulk button is not that
 * — nobody taps "download the whole season" meaning "fetch the forty I already have".
 *
 * A function of state rather than a method, so the rule can be tested for what it is — a decision
 * about a list — without standing up a ViewModel, a download manager and a filesystem.
 *
 * Sorted by season then episode so the transfer *queue* is in playing order: most of these wait
 * rather than run, so a viewer who starts watching before the last file lands gets the beginning
 * first.
 */
internal fun episodesWorthDownloading(
    state: AppUiState,
    where: (EpisodeUi) -> Boolean = { true },
): List<EpisodeUi> {
    val seriesTitle =
        state.seriesDetails?.title
            ?: (state.content as? AppContent.SeriesDetails)?.fallbackTitle
            ?: return emptyList()
    return state.seriesDetails
        ?.episodes
        ?.filter(where)
        ?.filterNot { episode ->
            state.downloads[episodeDownloadKey(seriesTitle, episode)] == DownloadStateUi.Completed
        }
        ?.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber ?: Int.MAX_VALUE }))
        .orEmpty()
}

/**
 * What the app is doing while it starts, for the boot screen to say out loud.
 *
 * A single "Preparing…" for the whole start-up left the user watching a spinner with no idea
 * whether anything was happening — on a catalogue of forty thousand items that wait is long enough
 * to look like a hang. Each step here is real work the app actually performs.
 */
enum class BootStageUi {
    /** Reading who is signed in and which profile is active. */
    PROFILES,

    /** Opening the local catalogue database. */
    CATALOGUE,

    /** Warming the artwork cache so the first screen is not a grid of grey boxes. */
    ARTWORK,

    /** Everything is ready; the first screen is about to replace this one. */
    READY,
}

enum class XtreamImportStageUi {
    AUTHENTICATING,
    CATEGORIES,
    LIVE,
    MOVIES,
    SERIES,
    SAVING,
}

enum class AppSection {
    HOME,
    /**
     * Channels on one side, what is on them on the other.
     *
     * The catalogue answers "what channels are there"; this answers "what is on". That second
     * question is what somebody reaches for when they sit down.
     */
    GUIDE,
    LIVE,
    MOVIES,
    SERIES,
    DISCOVER,
    MY_BURO,
    CONTINUE_WATCHING,
    HISTORY,
    REMINDERS,
    SUBSCRIPTIONS,
    DOWNLOADS,
    SEARCH,
    PROFILE,
    SOURCES,
    SETTINGS,
}

sealed interface AppContent {
    data object Home : AppContent
    data object Sources : AppContent

    /**
     * Channels on one side, what is on them on the other.
     *
     * Its own content rather than a mode of Channels: the guide lists every live channel at once
     * and carries a schedule beside it, where Channels lists one category and carries none.
     */
    data object Guide : AppContent

    data class SectionPlaceholder(
        val section: AppSection,
    ) : AppContent

    data class Story(
        val itemId: String,
    ) : AppContent

    data class Categories(
        val sourceId: String,
        val sourceName: String,
        val contentType: CatalogContentType? = null,
    ) : AppContent

    data class Channels(
        val sourceId: String,
        val sourceName: String,
        val categoryId: String?,
        val categoryName: String,
        val contentType: CatalogContentType? = null,
    ) : AppContent

    data class SeriesDetails(
        val sourceId: String,
        val providerSeriesId: String,
        val fallbackTitle: String,
        /**
         * The catalogue row's poster, standing in until the full record arrives.
         *
         * The counterpart of [fallbackTitle], and needed for the same reason: the reminder button
         * is enabled before the record loads, so a reminder marked quickly would otherwise be
         * stored with no artwork and stay blank on the reminders page for good.
         */
        val fallbackArtworkUrl: String? = null,
        /**
         * The catalogue row this was opened from, which is what favourites are keyed by.
         *
         * Empty only for a series reached without one. The favourite button is hidden in that
         * case rather than filing a favourite that points at nothing.
         */
        val channelId: String = "",
        /**
         * The category this series was filed under, which names its streaming service.
         *
         * Carried for the same reason a film's destination carries it, and it matters more here:
         * providers file their platform catalogues as "Series | Netflix", "Series | Max" and so on,
         * while films are filed by genre. Leaving this off meant the platform badge was missing
         * from precisely the titles that had a platform to name.
         */
        val categoryName: String? = null,
    ) : AppContent

    data class MovieDetails(
        val sourceId: String,
        val providerMovieId: String,
        val channelId: String,
        val fallbackTitle: String,
        val fallbackArtworkUrl: String?,
        val categoryName: String?,
    ) : AppContent

    data class Person(
        val name: String,
    ) : AppContent

    data object Favorites : AppContent

    /**
     * Part-watched titles, and everything watched before.
     *
     * Their own destinations rather than sections buried inside Favourites, which is where the
     * desktop puts them: someone looking for what they were half-way through should not have to
     * know it lives under a different heading.
     */
    data object ContinueWatching : AppContent

    data object History : AppContent

    /**
     * The Descobrir deck, one card at a time.
     *
     * Carries no payload: the hand lives on [AppUiState.discoverDeck] so keeping or skipping a card
     * updates in place, rather than the destination holding a snapshot that goes stale on the first
     * swipe.
     */
    data object Discover : AppContent

    /**
     * The titles the profile asked to be reminded about, and when the daily notice arrives.
     *
     * Carries no payload for the same reason [Downloads] does not: the entries live on
     * [AppUiState.reminders], which the repository observer keeps current, so the destination never
     * holds a snapshot that goes stale the moment a reminder is marked from a details page.
     */
    data object Reminders : AppContent

    /**
     * The list of offline copies.
     *
     * Carries no payload: the entries live on [AppUiState.downloadEntries], which the download
     * engine keeps current, so the destination never holds a snapshot that can go stale mid-download.
     */
    data object Downloads : AppContent

    /**
     * Where a title can be watched — GDD 9, the Android counterpart of the Windows Assinaturas
     * screen.
     *
     * Carries no payload for the same reason [Downloads] does not: shelves, the selected title and
     * its offers live on [AppUiState.subscriptions], so returning to the destination never restores
     * a stale snapshot of a catalogue that has since reloaded.
     */
    data object Subscriptions : AppContent

    data object Profiles : AppContent

    data class Player(
        val channel: ChannelUi,
    ) : AppContent

    data object Settings : AppContent
}

data class AppUiState(
    val isInitializing: Boolean = true,
    /** Which start-up step is running, for the boot screen. */
    val bootStage: BootStageUi = BootStageUi.PROFILES,
    /** Real catalogue covers already available while the rest of the home is still loading. */
    val bootBackdropUrls: List<String> = emptyList(),
    val hasAcceptedLegalNotice: Boolean = false,
    /** What the user typed on the search screen, and what came back for it. */
    val searchQuery: String = "",
    val searchResults: List<ChannelUi> = emptyList(),
    val isSearching: Boolean = false,
    val license: LicenseUiState = LicenseUiState.NotChecked,
    /**
     * Days until the active source's own subscription runs out, or null when unknown.
     *
     * The source's subscription, from the panel's `exp_date` — not [license], which is the app's
     * own licence. Null whenever the panel never reported a date, when no source is loaded yet, and
     * on any source imported before this was tracked.
     */
    val subscriptionDaysLeft: Int? = null,
    /**
     * The day that subscription ends, as the viewer would write it.
     *
     * Beside the countdown rather than instead of it: the days say whether to act now, and the date
     * is what somebody needs when they go to renew with whoever sold them the list.
     */
    val subscriptionExpiresOn: String? = null,
    /**
     * Outcome of the last activation-key attempt, from either the gate or the Settings card.
     *
     * Held outside [license] because a key can be redeemed while the licence is valid — extending a
     * subscription — and the Settings card cannot see the gate's Blocked state.
     */
    val redemption: RedemptionUi = RedemptionUi.Idle,
    val isProfilesLoading: Boolean = true,
    val profiles: List<ProfileUi> = emptyList(),
    val activeProfile: ProfileUi? = null,
    val deviceId: String? = null,
    val tmdbKeyConfigured: Boolean = false,
    /** Whether the household key exists. Every profile without its own falls back to it. */
    val sharedTmdbKeyConfigured: Boolean = false,
    val favoriteIds: Set<String> = emptySet(),
    /**
     * Content keys the active profile asked to be reminded about.
     *
     * Keys rather than rows, for the reason [Reminder] itself is keyed that way: an upcoming title
     * is not in the catalogue yet, so there is no row to point at when the reminder is made.
     */
    val reminderKeys: Set<String> = emptySet(),
    /** What is marked, in full, for the reminders page and the home shelf. */
    val reminders: List<Reminder> = emptyList(),
    /**
     * Whether the daily notice is on, and the hour it arrives.
     *
     * Household-wide rather than per profile, matching the store behind it: one device posts one
     * notification at one time. Defaulted to the same hour [ReminderPolicy] falls back to, so the
     * page shows the hour that is really in force before the stored value has been read.
     */
    /**
     * The four digits a computer has to be told before it can send anything here.
     *
     * Kept once minted, even after listening stops, so the number does not change between sessions
     * and have to be retyped into the computer every time.
     */
    val castReceiverCode: String? = null,
    /** Whether the sockets are open. False when the sheet is closed, or when the bind failed. */
    val isCastReceiverOn: Boolean = false,
    /**
     * The Descobrir hand, top card first. Empty once it has been swiped through.
     *
     * Rows rather than ids, so the screen can draw a card without another lookup per swipe.
     */
    val discoverDeck: List<ChannelUi> = emptyList(),
    /** How many the hand started with, so the card counter can say "3 of 15". */
    val discoverDealtCount: Int = 0,
    val isDiscoverLoading: Boolean = false,
    /**
     * Titles this profile has already judged in Descobrir, kept for the session.
     *
     * Skipping is not stored anywhere else — a favourite is a real record, but "not this one" is
     * only a fact about this deck. Held here so the next hand does not offer it back immediately,
     * and deliberately forgotten when the app closes: taste changes, and a title refused once
     * should not be hidden for good.
     */
    val discoverJudgedIds: Set<String> = emptySet(),
    /**
     * What this sitting's swipes have said, which the next hand is ranked against.
     *
     * Separate from the watch history on purpose: "what I just said about a poster" is a fresher
     * statement than "what I watched last month", and a skip teaches something a watch history
     * never can — a title that was passed over is never watched, so it would otherwise leave no
     * trace at all. Held for the session only, like [discoverJudgedIds].
     */
    val discoverSessionTaste: SessionTaste = SessionTaste(),
    /**
     * The official logo of the service the open title streams on, from TMDb.
     *
     * TMDb publishes the providers' own marks through `watch/providers` and licenses them for this
     * use with attribution, which is what makes showing a real Netflix or Prime logo legitimate
     * where drawing an imitation would not be. Null until the lookup runs or when it finds nothing,
     * and the badge falls back to the monogram on the service's colour.
     */
    val openTitleProviderLogoUrl: String? = null,
    /**
     * The streaming services' official logos, by service name.
     *
     * One fetch for the whole region rather than one per title: a grid draws dozens of cards at
     * once, and asking TMDb about each would be hundreds of requests to paint one screen. Empty
     * until the directory loads, and every badge falls back to its monogram until then.
     */
    val providerLogos: Map<String, String> = emptyMap(),
    /**
     * The services worth a shortcut on the movies/series shelf — the same directory as
     * [providerLogos], with each entry's TMDb id kept so tapping one can open its full catalogue
     * on Assinaturas. Empty until the directory loads, and the shortcut row simply does not draw.
     */
    val discoveredProviders: List<com.lucasserafin94.iptvburo.data.discovery.DiscoveredProvider> = emptyList(),
    /**
     * The critics' scores for the open title, from OMDb.
     *
     * Null until the lookup returns, when there is no OMDb key, or when the services hold nothing
     * for this title — all three mean the same thing to the screen: draw what is there and no more.
     */
    val openTitleCriticScores: CriticScores? = null,
    /** Whether an OMDb key is stored, which is what makes the critics' row possible. */
    val criticsKeyConfigured: Boolean = false,
    /**
     * The service TMDb says the open title streams on, in that service's own name.
     *
     * The category is the first source for this, but most playlists file films by genre, so on a
     * film page it is usually TMDb that knows. Null when neither does, and no badge is drawn.
     */
    val openTitleProviderName: String? = null,
    /**
     * Other titles TMDb considers close to the one open, franchise entries and lookalikes alike —
     * the strip a viewer sees under the cast on a film or series page.
     *
     * Reuses [PersonCreditUi] rather than a type of its own: both are "a title with a TMDb id,
     * openable on the where-to-watch page", and [openPersonCredit] already does exactly that.
     * Empty until the lookup returns, when there is no metadata key, or when TMDb has nothing to
     * suggest — all three draw no strip at all rather than an empty one with a heading.
     */
    val openTitleSimilarTitles: List<PersonCreditUi> = emptyList(),
    /** What the bell is holding for the active profile. */
    val notifications: NotificationCentre = NotificationCentre(),
    /**
     * How much artwork this device may keep.
     *
     * Not per profile, unlike the bell above: the cache is a property of the device's storage, and
     * two people sharing a tablet share the disk it fills.
     */
    val cacheBudget: CacheBudget = CacheBudget.DEFAULT,
    /** Whether the first-run offer is still owed — distinct from having answered zero. */
    val cacheChoicePending: Boolean = false,
    val cacheProgress: CacheFillProgress = CacheFillProgress(),
    /** Measured from the directory, so it matches what the device's storage screen reports. */
    val cacheBytesUsed: Long = 0L,
    val reminderNotify: Boolean = true,
    val reminderTime: LocalTime = LocalTime.of(ReminderPolicy.DEFAULT_HOUR, 0),
    val favoriteItems: List<ChannelUi> = emptyList(),
    val section: AppSection = AppSection.HOME,
    val content: AppContent = AppContent.Home,
    val lastFocusedHomeItemId: String? = null,
    val sources: List<SourceUi> = emptyList(),
    /**
     * Whether every configured list is browsed as one catalogue.
     *
     * Off by default: somebody with one list gains nothing, and somebody with two who has
     * not asked for this should not find their library silently rearranged.
     */
    val mergeEverySource: Boolean = false,
    val homeItems: List<ChannelUi> = emptyList(),
    val continueWatching: List<ContinueWatchingUi> = emptyList(),
    val watchHistory: List<WatchHistoryUi> = emptyList(),
    val categories: List<CategoryUi> = emptyList(),
    /**
     * Every category the source has, including hidden ones.
     *
     * Separate from [categories], which is what the catalogue draws and therefore has the hidden
     * ones removed. Settings needs the unfiltered list: reading the filtered one would mean hiding
     * a category also removed the switch that hid it, leaving no way to bring it back.
     */
    val allCategories: List<CategoryUi> = emptyList(),
    val channels: List<ChannelUi> = emptyList(),
    /** How the catalogue grid is arranged and narrowed. Applied to [channels] by the screen. */
    val catalogueLayout: CatalogueLayout = CatalogueLayout.POSTER,
    val catalogueFilter: CatalogueFilter = CatalogueFilter(),
    val isImporting: Boolean = false,
    val lastImportedChannelCount: Int? = null,
    val hasImportError: Boolean = false,
    val lastImportMethod: SourceImportMethod? = null,
    val stalkerFailure: StalkerFailureUi? = null,
    val xtreamImportStage: XtreamImportStageUi? = null,
    /**
     * How fast the catalogue is arriving, in bytes per second, or null when it cannot be said.
     *
     * Null rather than zero on purpose: between requests, and before enough has arrived to divide
     * by, the app does not know the rate, and a zero on screen would read as stopped. The screens
     * omit the line instead. See [formatDownloadRate].
     */
    val downloadBytesPerSecond: Long? = null,
    val importSuccessVersion: Long = 0L,
    val isCatalogLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreChannels: Boolean = false,
    val hasCatalogError: Boolean = false,
    val isResolvingPlayback: Boolean = false,
    val hasPlaybackError: Boolean = false,
    /** A shared link is being looked up in this device's catalogue. */
    val isResolvingSharedTitle: Boolean = false,
    /** A shared link was opened, but this device's list does not carry the title. */
    val sharedTitleMissing: Boolean = false,
    val liveNow: LiveProgramUi? = null,
    val liveNext: LiveProgramUi? = null,
    /** The whole schedule for the open live channel, for the guide. Empty when there is none. */
    val liveSchedule: List<LiveProgramUi> = emptyList(),
    /**
     * Trailer ids found for banner titles, by home item id.
     *
     * A blank value means "asked, and there is none": stored so the rotation does not look the same
     * title up again on every pass.
     */
    val heroTrailers: Map<String, String> = emptyMap(),
    /**
     * Trailers that would not play, and when.
     *
     * The banner is the first thing anybody sees, so a video that fails must not be retried on
     * every rotation — the viewer pays a wait each time for the same answer. See BannerTrailer.
     */
    val heroTrailerFailures: Map<String, Long> = emptyMap(),
    /**
     * The channel the guide is sitting on, and the schedules it has fetched.
     *
     * Its own selection, separate from what is playing: the guide is a screen somebody moves down
     * with the D-pad, and sharing the player's channel would mean browsing changed what was on.
     */
    val guideFocusedChannelId: String? = null,
    /** Schedules already fetched, by channel id. Absent means not fetched yet, not empty. */
    val guideSchedules: Map<String, List<LiveProgramUi>> = emptyMap(),
    /** Channels being fetched right now, so a row can say so rather than looking empty. */
    val guideLoadingChannelIds: Set<String> = emptySet(),
    val isLiveEpgLoading: Boolean = false,
    val movieDetails: MovieDetailsUi? = null,
    /**
     * How far into the open title the viewer already is, 0..1, or null when it is unwatched.
     *
     * Shown as a bar on the details page so somebody returning to a part-watched film can see at a
     * glance that they left it half-way, without opening the player to find out.
     */
    val openTitleProgress: Float? = null,
    /**
     * How far into each episode of the open series the viewer is, keyed by episode id, 0..1.
     *
     * A season can run to twenty episodes and a series to several hundred; without this the list
     * gives no sign of which ones have been seen, and picking up where you left off means
     * remembering a number.
     */
    val episodeProgress: Map<String, Float> = emptyMap(),
    val isMovieLoading: Boolean = false,
    val hasMovieError: Boolean = false,
    val seriesDetails: SeriesDetailsUi? = null,
    val isSeriesLoading: Boolean = false,
    val hasSeriesError: Boolean = false,
    val personMovies: List<ChannelUi> = emptyList(),
    val personDetails: PersonDetailsUi? = null,
    val downloads: Map<String, DownloadStateUi> = emptyMap(),
    val downloadTitles: Map<String, String> = emptyMap(),
    /** Artwork for each download, captured when it starts: the catalogue row may be gone later. */
    val downloadArtwork: Map<String, String> = emptyMap(),
    val subscriptions: SubscriptionsUi = SubscriptionsUi(),
    /**
     * Cast photos already looked up, keyed by lower-cased name.
     *
     * A null value is a name TMDb does not know, kept so it is not asked for again. Absent means
     * "not looked up yet", which is a different state and must stay distinguishable.
     */
    val castPhotos: Map<String, String?> = emptyMap(),
    /**
     * Real synopses for the banner, keyed by channel id.
     *
     * Fetched for the rotating hero only. The catalogue row carries no plot — it is on the details
     * page, behind a provider call — so the banner used to show a stock sentence telling the viewer
     * to open the title to find out what it is about, which is the opposite of what a banner is for.
     */
    val heroSynopses: Map<String, String> = emptyMap(),
    /** Categories the user chose not to see. Removed from the catalogue, kept in settings. */
    val hiddenCategoryIds: Set<String> = emptySet(),
    /** Which categories need the PIN, and whether adult-marked ones are covered without listing. */
    val parentalLock: ParentalLock = ParentalLock(),
    /** Whether a PIN exists. Locking a category is only offered once one does. */
    val hasParentalPin: Boolean = false,
    /** Set when a PIN action was refused, cleared on the next attempt. */
    val parentalMessage: ParentalMessage? = null,
    /** The category waiting on a PIN, or null when nothing is being unlocked. */
    val pendingUnlock: PendingUnlockUi? = null,
    /** How subtitles are drawn, applied to the next player that opens. */
    val subtitles: SubtitlePresentation = SubtitlePresentation(),
    /**
     * Whether the home banner's trailer carries sound.
     *
     * Off until somebody asks. Note it never changes how the trailer *starts*: no engine autoplays
     * audio, so it always begins muted and the sound is raised once it is genuinely playing.
     */
    val bannerTrailerSound: Boolean = false,
    /**
     * What the cast sheet is doing. [CastUiState.Idle] means it is closed.
     *
     * Held here rather than inside a composable so that leaving the details screen mid-search does
     * not strand a discovery, and so the sheet survives a rotation with its chosen screen intact.
     */
    val cast: CastUiState = CastUiState.Idle,
    /** The title the open cast sheet would send. Null whenever [cast] is Idle. */
    val castRequest: CastRequestUi? = null,
) {
    /**
     * [channels] narrowed and ordered by [catalogueFilter].
     *
     * Derived here rather than stored so the filter can never disagree with the list it describes,
     * and computed against the domain rules so Android and the desktop narrow a catalogue
     * identically. The ids are mapped back to the original items, keeping every field the grid
     * needs without the domain having to know about them.
     */
    /**
     * The key a reminder for the open film would have, or null when no film is open.
     *
     * Derived here so the button and the toggle cannot disagree about which title they mean: both
     * ask this, and the identity is built the same way sharing and casting build theirs.
     */
    val movieReminderKey: String?
        get() {
            val content = content as? AppContent.MovieDetails ?: return null
            val name = movieDetails?.title ?: content.fallbackTitle
            val year =
                movieDetails?.releaseDate?.let { date -> date.trim().take(4).toIntOrNull() }
                    ?: ContentIdentity.yearFromTitle(name)
            return ContentIdentity.of(ContentKind.MOVIE, name, year).key
        }

    /** The same, for an open series. */
    val seriesReminderKey: String?
        get() {
            val content = content as? AppContent.SeriesDetails ?: return null
            val name = seriesDetails?.title ?: content.fallbackTitle
            val year = seriesDetails?.releaseDate?.let { date -> date.trim().take(4).toIntOrNull() }
            return ContentIdentity.of(ContentKind.SERIES, name, year).key
        }

    val visibleChannels: List<ChannelUi>
        get() {
            if (!catalogueFilter.isActive) return channels
            val byId = channels.associateBy(ChannelUi::id)
            return applyCatalogueFilter(channels.map(ChannelUi::toBrowsable), catalogueFilter)
                .mapNotNull { item -> byId[item.id] }
        }

    /** The genres actually present in this catalogue, for the filter picker. */
    val availableCatalogueGenres: List<String>
        get() = availableGenres(channels.map(ChannelUi::toBrowsable))

    /** The years actually present in this catalogue, newest first. */
    val availableCatalogueYears: List<Int>
        get() = availableYears(channels.map(ChannelUi::toBrowsable))

    /** Ordered view for a download list: running first, so active work is never scrolled away. */
    val downloadEntries: List<DownloadEntryUi>
        get() =
            downloads
                .map { (key, state) ->
                    DownloadEntryUi(
                        contentKey = key,
                        title = downloadTitles[key] ?: key,
                        state = state,
                        artworkUrl = downloadArtwork[key],
                    )
                }
                .sortedBy { entry ->
                    when (entry.state) {
                        is DownloadStateUi.Running -> 0
                        DownloadStateUi.Preparing -> 0
                        DownloadStateUi.Failed -> 1
                        DownloadStateUi.Idle -> 2
                        DownloadStateUi.Completed -> 3
                    }
                }
}

/**
 * The Assinaturas screen's state — GDD 9.
 *
 * Two views in one type, distinguished by [selected]: the shelves, and one title's offers. They are
 * not separate destinations because Back must return to the shelves rather than leaving the screen,
 * and a nested destination would have made that a navigation special case.
 *
 * [capability] is derived from whether a metadata key is configured, never set directly, so there is
 * no way to reveal the screen without something real behind it.
 */
/**
 * One service's whole catalogue, behind a shelf's "Ver mais".
 *
 * A shelf holds twenty titles because that is what fits on a rail, and reaching its end is exactly
 * where "what else is on Netflix?" gets asked. [isLoading] is its own flag rather than the screen's:
 * the shelves stay drawn underneath while this fills.
 */
data class ExpandedServiceUi(
    val providerId: String,
    val providerName: String,
    val titles: List<SubscriptionTitleUi> = emptyList(),
    val isLoading: Boolean = false,
)

data class SubscriptionsUi(
    val capability: StreamingDiscoveryCapability = StreamingDiscoveryCapability.UNAVAILABLE,
    val shelves: List<ProviderShelfUi> = emptyList(),
    val kind: SubscriptionsKindUi = SubscriptionsKindUi.MOVIES,
    val region: String = "BR",
    val isLoading: Boolean = false,
    /** The title whose offers are showing. Null means the shelves are. */
    /**
     * The service whose full catalogue is open, from a shelf's "Ver mais".
     *
     * Null while the shelves are showing. Its own field rather than a destination, because it is a
     * step *inside* Assinaturas: back returns to the shelves, and making it a destination would
     * have turned that into a navigation special case.
     */
    val expandedService: ExpandedServiceUi? = null,
    val selected: SubscriptionTitleUi? = null,
    val offers: List<SubscriptionOfferUi> = emptyList(),
    val isSelectionLoading: Boolean = false,
    /**
     * True when the lookup finished and TMDb had nothing to say about the selected title.
     *
     * Distinct from an empty [offers] before the lookup returns: "we cannot say" and "not looked up
     * yet" read identically in the data but must not read identically on screen.
     */
    val selectionUnknown: Boolean = false,
)

/** Which shelf filter is in effect. Mirrors `TmdbDiscoverKind` without leaking it into the UI. */
enum class SubscriptionsKindUi {
    MOVIES,
    SERIES,
    THIS_WEEK,
    UPCOMING,
}

/** One service's rail of titles. Never empty — an empty shelf is dropped rather than rendered. */
data class ProviderShelfUi(
    val providerId: String,
    val providerName: String,
    /**
     * TMDb's own numeric id for this service, which is what asking for more of it needs.
     *
     * Null for a shelf that is not one service — "Em breve" is the set of films no service carries
     * yet, so there is no catalogue to expand. The screen uses that to decide whether "Ver mais"
     * belongs on the shelf at all.
     */
    val tmdbProviderId: Int? = null,
    val titles: List<SubscriptionTitleUi>,
)

/**
 * A title on a shelf.
 *
 * [externalNamespace] and [externalId] are carried so the offers lookup can rebuild the domain
 * `ExternalTitle` without the UI holding a domain object it would otherwise have no use for.
 */
data class SubscriptionTitleUi(
    val externalNamespace: String,
    val externalId: String,
    val title: String,
    val year: Int?,
    /** The full release date, for the upcoming shelf that has to say the day and not just the year. */
    val releaseDate: String? = null,
    val posterUrl: String?,
    val isSeries: Boolean,
    val isDemo: Boolean,
    val overview: String? = null,
    val rating: Double? = null,
    val runtimeMinutes: Int? = null,
    val genres: List<String> = emptyList(),
    val backdropUrl: String? = null,
    val youtubeTrailerId: String? = null,
    val cast: List<SubscriptionCastUi> = emptyList(),
)

data class SubscriptionCastUi(
    val name: String,
    val character: String?,
    val photoUrl: String?,
)

/**
 * One row on the "where to watch" list.
 *
 * [requiresAttribution] is not styling. JustWatch's terms require the source credited on **every
 * item** that shows their data — an About screen is explicitly not enough — so the flag travels with
 * the row rather than being decided by the composable that happens to draw it.
 */
data class SubscriptionOfferUi(
    /** The local item to open, set only on the user's own copy. */
    val localContentId: String? = null,
    val providerId: String,
    val providerName: String,
    /**
     * The service's own mark, when TMDb has one.
     *
     * Null for the user's own library — that row draws the app's own [BuroMark] instead of a
     * service logo, matching the Windows row it sits beside conceptually.
     */
    val logoUrl: String? = null,
    val reason: OfferReason,
    val isUserLibrary: Boolean,
    val requiresAttribution: Boolean,
    val webUrl: String?,
    val appDeepLink: String?,
)

/**
 * A catalogue entry as the browsing rules see it.
 *
 * The category name stands in for the genre: an M3U playlist has no genre field, and the category
 * a provider filed something under is the closest thing to one the user can actually recognise.
 */
internal fun ChannelUi.toBrowsable(): BrowsableItem =
    BrowsableItem(
        id = id,
        title = name,
        genre = categoryName,
        year = year,
        rating = rating,
    )
