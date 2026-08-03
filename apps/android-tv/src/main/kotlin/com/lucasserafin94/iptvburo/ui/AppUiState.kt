package com.lucasserafin94.iptvburo.ui

import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.ContentKind
import com.lucasserafin94.iptvburo.domain.model.SourceType

data class ProfileUi(
    val id: String,
    val name: String,
    val avatarKey: String,
    val isKids: Boolean,
)

data class SourceUi(
    val id: String,
    val name: String,
    val channelCount: Int,
    val type: SourceType = SourceType.LOCAL_M3U,
)

data class CategoryUi(
    val id: String?,
    val name: String,
    val channelCount: Int,
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
    val artworkUrl: String?,
    val backdropUrl: String?,
    val youtubeTrailerId: String?,
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

    /** [fraction] is negative when the server did not report a content length. */
    data class Running(val fraction: Float) : DownloadStateUi

    data object Completed : DownloadStateUi

    data object Failed : DownloadStateUi
}

/** A download as the UI needs it: identity, a human name, and current state. */
data class DownloadEntryUi(
    val contentKey: String,
    val title: String,
    val state: DownloadStateUi,
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
    LIVE,
    MOVIES,
    SERIES,
    DISCOVER,
    MY_BURO,
    SEARCH,
    PROFILE,
    SOURCES,
    SETTINGS,
}

sealed interface AppContent {
    data object Home : AppContent
    data object Sources : AppContent

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

    data object Profiles : AppContent

    data class Player(
        val channel: ChannelUi,
    ) : AppContent

    data object Settings : AppContent
}

data class AppUiState(
    val isInitializing: Boolean = true,
    val hasAcceptedLegalNotice: Boolean = false,
    val isProfilesLoading: Boolean = true,
    val profiles: List<ProfileUi> = emptyList(),
    val activeProfile: ProfileUi? = null,
    val deviceId: String? = null,
    val favoriteIds: Set<String> = emptySet(),
    val favoriteItems: List<ChannelUi> = emptyList(),
    val section: AppSection = AppSection.HOME,
    val content: AppContent = AppContent.Home,
    val lastFocusedHomeItemId: String? = null,
    val sources: List<SourceUi> = emptyList(),
    val homeItems: List<ChannelUi> = emptyList(),
    val categories: List<CategoryUi> = emptyList(),
    val channels: List<ChannelUi> = emptyList(),
    val isImporting: Boolean = false,
    val lastImportedChannelCount: Int? = null,
    val hasImportError: Boolean = false,
    val lastImportMethod: SourceImportMethod? = null,
    val stalkerFailure: StalkerFailureUi? = null,
    val xtreamImportStage: XtreamImportStageUi? = null,
    val importSuccessVersion: Long = 0L,
    val isCatalogLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreChannels: Boolean = false,
    val hasCatalogError: Boolean = false,
    val isResolvingPlayback: Boolean = false,
    val hasPlaybackError: Boolean = false,
    val liveNow: LiveProgramUi? = null,
    val liveNext: LiveProgramUi? = null,
    val isLiveEpgLoading: Boolean = false,
    val movieDetails: MovieDetailsUi? = null,
    val isMovieLoading: Boolean = false,
    val hasMovieError: Boolean = false,
    val seriesDetails: SeriesDetailsUi? = null,
    val isSeriesLoading: Boolean = false,
    val hasSeriesError: Boolean = false,
    val personMovies: List<ChannelUi> = emptyList(),
    val downloads: Map<String, DownloadStateUi> = emptyMap(),
    val downloadTitles: Map<String, String> = emptyMap(),
) {
    /** Ordered view for a download list: running first, so active work is never scrolled away. */
    val downloadEntries: List<DownloadEntryUi>
        get() =
            downloads
                .map { (key, state) -> DownloadEntryUi(key, downloadTitles[key] ?: key, state) }
                .sortedBy { entry ->
                    when (entry.state) {
                        is DownloadStateUi.Running -> 0
                        DownloadStateUi.Failed -> 1
                        DownloadStateUi.Idle -> 2
                        DownloadStateUi.Completed -> 3
                    }
                }
}
