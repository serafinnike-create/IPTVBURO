package com.lucasserafin94.iptvburo.desktop.user

import com.lucasserafin94.iptvburo.domain.model.MusicPlaylist
import com.lucasserafin94.iptvburo.domain.model.MusicPlaylistKind
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.prefs.Preferences

/**
 * The user's own playlists, per profile — GDD 8 section 17.
 *
 * Only member track ids are stored, never a [com.lucasserafin94.iptvburo.domain.model.MusicTrack].
 * A stored track would embed its stream URI, which routinely carries the supplier's subscription
 * credentials; section 23 forbids persisting one, and an id resolves against the library at read
 * time anyway. That is also why a playlist survives re-importing the source M3U.
 *
 * Smart and system playlists are not stored at all. Their contents come from evaluating a rule, so
 * writing a member list would create a second, immediately stale answer to the same question.
 */
class MusicPlaylistStore(
    private val preferences: Preferences =
        Preferences.userRoot().node("com/lucasserafin94/iptvburo/music-playlists-v1"),
) {
    /**
     * Every stored playlist for [profileId], in creation order.
     *
     * An unreadable row is skipped rather than failing the load: one corrupt playlist must not cost
     * the user the rest of them.
     */
    fun playlistsFor(profileId: String?): List<MusicPlaylist> {
        val raw = profileId?.let { preferences.get(playlistsKey(it), "") }.orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(RECORD_SEPARATOR).mapNotNull(::decodePlaylist)
    }

    /** Replaces the whole set for [profileId]. */
    fun save(
        profileId: String?,
        playlists: List<MusicPlaylist>,
    ) {
        if (profileId == null) return
        val storable = playlists.filter { it.kind.holdsOwnTracks }.take(MAX_PLAYLISTS)
        preferences.put(
            playlistsKey(profileId),
            storable.joinToString(RECORD_SEPARATOR.toString(), transform = ::encodePlaylist),
        )
    }

    /**
     * Creates an empty playlist and returns it alongside the updated set.
     *
     * The id is a UUID rather than the name: names are not unique, and a rename must not break the
     * reference — see [MusicPlaylist.renamed].
     */
    fun create(
        profileId: String?,
        name: String,
        kind: MusicPlaylistKind = MusicPlaylistKind.MANUAL,
        trackIds: List<String> = emptyList(),
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): List<MusicPlaylist> {
        if (profileId == null) return emptyList()
        val clean = name.trim().ifEmpty { return playlistsFor(profileId) }
        val existing = playlistsFor(profileId)
        if (existing.size >= MAX_PLAYLISTS) return existing
        val created =
            MusicPlaylist(
                id = UUID.randomUUID().toString(),
                name = clean,
                kind = kind,
                trackIds = trackIds,
                createdAtEpochMillis = nowEpochMillis,
                updatedAtEpochMillis = nowEpochMillis,
            )
        val updated = existing + created
        save(profileId, updated)
        return updated
    }

    fun delete(
        profileId: String?,
        playlistId: String,
    ): List<MusicPlaylist> {
        if (profileId == null) return emptyList()
        // System playlists are not stored, so nothing here can delete one; the filter is on kind
        // rather than on id for that reason.
        val updated = playlistsFor(profileId).filterNot { it.id == playlistId }
        save(profileId, updated)
        return updated
    }

    /**
     * Applies [transform] to one playlist and persists the result.
     *
     * A single mutation point so that every operation — rename, reorder, add, remove — goes through
     * the same load, replace and save, instead of each caller reimplementing it.
     */
    fun update(
        profileId: String?,
        playlistId: String,
        transform: (MusicPlaylist) -> MusicPlaylist,
    ): List<MusicPlaylist> {
        if (profileId == null) return emptyList()
        val updated =
            playlistsFor(profileId).map { playlist ->
                if (playlist.id == playlistId) transform(playlist) else playlist
            }
        save(profileId, updated)
        return updated
    }

    fun clear(profileId: String) = preferences.remove(playlistsKey(profileId))

    /**
     * One playlist as base64 fields.
     *
     * The name and every track id are encoded because both come from the user's own playlist and
     * may contain the separators this format relies on — a track id derived from a tvg-id
     * frequently contains a colon.
     */
    private fun encodePlaylist(playlist: MusicPlaylist): String =
        listOf(
            playlist.id,
            encode(playlist.name),
            playlist.kind.name,
            playlist.createdAtEpochMillis.toString(),
            playlist.updatedAtEpochMillis.toString(),
            playlist.trackIds.joinToString(TRACK_SEPARATOR.toString(), transform = ::encode),
        ).joinToString(FIELD_SEPARATOR.toString())

    private fun decodePlaylist(record: String): MusicPlaylist? {
        val parts = record.split(FIELD_SEPARATOR)
        if (parts.size < 5) return null
        return runCatching {
            val kind = MusicPlaylistKind.entries.firstOrNull { it.name == parts[2] } ?: return null
            // A stored smart or system row would be a second source of truth for a computed list,
            // so it is dropped on read as well as refused on write.
            if (!kind.holdsOwnTracks) return null
            MusicPlaylist(
                id = parts[0].takeIf(String::isNotBlank) ?: return null,
                name = decode(parts[1]).takeIf(String::isNotBlank) ?: return null,
                kind = kind,
                trackIds =
                    parts.getOrNull(5)
                        .orEmpty()
                        .split(TRACK_SEPARATOR)
                        .filter(String::isNotBlank)
                        .mapNotNull { encoded -> runCatching { decode(encoded) }.getOrNull() },
                createdAtEpochMillis = parts[3].toLongOrNull() ?: 0L,
                updatedAtEpochMillis = parts[4].toLongOrNull() ?: 0L,
            )
        }.getOrNull()
    }

    private fun playlistsKey(profileId: String): String = "playlists.$profileId"

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String =
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)

    private companion object {
        /** Bounded so the preference value cannot outgrow the backing store's length limit. */
        const val MAX_PLAYLISTS = 100
        const val RECORD_SEPARATOR = ';'
        const val FIELD_SEPARATOR = ':'
        const val TRACK_SEPARATOR = ','
    }
}
