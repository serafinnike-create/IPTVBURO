package com.lucasserafin94.iptvburo.desktop.model

import com.lucasserafin94.iptvburo.domain.model.Category
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.Source
import com.lucasserafin94.iptvburo.playlist.M3uWarningCode
import com.lucasserafin94.iptvburo.xtream.XtreamAccount
import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import com.lucasserafin94.iptvburo.xtream.XtreamEpisode

data class ImportedCatalog(
    val source: Source,
    val categories: List<Category>,
    val channels: List<Channel>,
    val warnings: List<SafeImportWarning>,
)

data class SafeImportWarning(
    val code: M3uWarningCode,
    val lineNumber: Long?,
)

enum class PlaybackReadiness {
    EXTERNAL_READY,
    EXTERNAL_MAY_MISS_HEADERS,
}

fun Channel.playbackReadiness(): PlaybackReadiness =
    if (requestHeaders.isEmpty()) {
        PlaybackReadiness.EXTERNAL_READY
    } else {
        PlaybackReadiness.EXTERNAL_MAY_MISS_HEADERS
    }

enum class DesktopSourceKind {
    LOCAL_PLAYLIST,
    XTREAM_SESSION,
}

data class DesktopSourceSummary(
    val id: String,
    val name: String,
    val itemCount: Int,
    val kind: DesktopSourceKind,
)

data class XtreamSessionSummary(
    val sourceId: String,
    val account: XtreamAccount,
    val loadedItemCount: Int,
    val loadedContentTypes: Set<XtreamContentType>,
)

data class XtreamCatalogPage(
    val items: List<XtreamCatalogItem>,
    val pageIndex: Int,
    val pageSize: Int,
    val totalMatches: Int,
) {
    val pageCount: Int
        get() =
            if (totalMatches == 0) {
                1
            } else {
                ((totalMatches - 1) / pageSize) + 1
            }

    val hasPrevious: Boolean
        get() = pageIndex > 0

    val hasNext: Boolean
        get() = pageIndex + 1 < pageCount

    companion object {
        fun empty(pageSize: Int = 80): XtreamCatalogPage =
            XtreamCatalogPage(
                items = emptyList(),
                pageIndex = 0,
                pageSize = pageSize,
                totalMatches = 0,
            )
    }
}

/**
 * Playback target without a URL or credentials.
 *
 * The repository turns this opaque provider identifier into a URL only after the user confirms
 * the external handoff.
 */
sealed interface XtreamPlaybackTarget {
    /**
     * Provider-independent key for the content behind this target.
     *
     * Watch progress is stored against this rather than the provider id, so replacing the playlist
     * keeps "continue watching" pointing at the same title instead of orphaning every entry.
     */
    val contentKey: String

    class CatalogItem(
        val providerId: String,
        val contentType: XtreamContentType,
        val containerExtension: String?,
        override val contentKey: String,
    ) : XtreamPlaybackTarget {
        override fun toString(): String =
            "XtreamPlaybackTarget.CatalogItem(providerId=<redacted>, contentType=$contentType)"
    }

    class Episode(
        val seriesId: String,
        val episode: XtreamEpisode,
        override val contentKey: String,
    ) : XtreamPlaybackTarget {
        override fun toString(): String = "XtreamPlaybackTarget.Episode(<redacted>)"
    }

    /**
     * A programme replayed from a channel's catch-up recorder.
     *
     * Its own target rather than a CatalogItem with extra fields, because it is a different URL
     * shape entirely — `/timeshift/…` rather than `/live/…` — and because the watch progress has to
     * be filed separately: last night's film on a channel is not the channel.
     *
     * [startLocal] is already formatted as the provider expects, `YYYY-MM-DD:HH-MM` in the
     * provider's own local time. Formatted by the caller because only it knows which zone the
     * guide was reported in.
     */
    class CatchUp(
        val providerId: String,
        val startLocal: String,
        val durationMinutes: Int,
        override val contentKey: String,
    ) : XtreamPlaybackTarget {
        override fun toString(): String =
            "XtreamPlaybackTarget.CatchUp(providerId=<redacted>, minutes=$durationMinutes)"
    }
}
