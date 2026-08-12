package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamEpisode

/**
 * Identity of a catalogue item, independent of which provider supplied it.
 *
 * Falls back to a year parsed out of the title when the provider leaves the `year` field empty,
 * which is common for films whose title already carries `(1998)`.
 */
fun XtreamCatalogItem.contentIdentity(): ContentIdentity =
    ContentIdentity.of(
        kind = contentType.toContentKind(),
        title = name,
        year = year ?: ContentIdentity.yearFromTitle(name),
    )

/**
 * Identity of one episode, expressed relative to its series.
 *
 * Season and episode numbers are stable across providers in a way episode ids are not, so this
 * survives a change of playlist for the same series.
 */
fun XtreamCatalogItem.episodeContentKey(episode: XtreamEpisode): String =
    buildString {
        append(contentIdentity().key)
        append("|s")
        // No fallback: the season number is non-null. The episode number below keeps its `?: 0`
        // because that one genuinely can be absent, and a key must still be produced for it.
        append(episode.seasonNumber)
        append("e")
        append(episode.episodeNumber ?: 0)
    }

/**
 * Rewrites favourite keys written by earlier versions.
 *
 * Old keys were `CONTENTTYPE:providerId`, which pointed at a slot in one particular playlist. There
 * is no way to recover which title such a key meant once that playlist is gone, so a key is only
 * migrated when the item is still present in the loaded catalogue. Keys that cannot be resolved are
 * dropped rather than kept, because keeping them is what caused unrelated titles to appear as
 * favourites after switching lists.
 */
fun migrateFavoriteKeys(
    storedKeys: Set<String>,
    resolveLegacy: (String) -> XtreamCatalogItem?,
): Set<String> {
    val migrated = LinkedHashSet<String>(storedKeys.size)
    storedKeys.forEach { key ->
        if (isLegacyFavoriteKey(key)) {
            resolveLegacy(key)?.let { item -> migrated += item.contentIdentity().key }
        } else {
            migrated += key
        }
    }
    return migrated
}

/**
 * A legacy key is `LIVE|MOVIE|SERIES` followed by the provider id.
 *
 * New keys start with a lowercase kind (`movie:`, `series:`…), so the uppercase prefix is what
 * distinguishes the two formats.
 */
internal fun isLegacyFavoriteKey(key: String): Boolean =
    LEGACY_PREFIXES.any { prefix -> key.startsWith(prefix) }

private val LEGACY_PREFIXES = listOf("LIVE:", "MOVIE:", "SERIES:")
