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
    class CatalogItem(
        val providerId: String,
        val contentType: XtreamContentType,
        val containerExtension: String?,
    ) : XtreamPlaybackTarget {
        override fun toString(): String =
            "XtreamPlaybackTarget.CatalogItem(providerId=<redacted>, contentType=$contentType)"
    }

    class Episode(
        val seriesId: String,
        val episode: XtreamEpisode,
    ) : XtreamPlaybackTarget {
        override fun toString(): String = "XtreamPlaybackTarget.Episode(<redacted>)"
    }
}
