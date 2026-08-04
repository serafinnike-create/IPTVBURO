package com.lucasserafin94.iptvburo.domain.model

/**
 * Where a playlist came from, which is what decides how much of it the user may edit.
 *
 * GDD 8 section 17 lists five kinds. They are one enum rather than five types because every
 * consumer — the shelf, the export, the persistence format — treats them identically apart from
 * whether tracks can be added and removed by hand, and a sealed hierarchy would force each of those
 * to branch five ways to learn one boolean.
 */
enum class MusicPlaylistKind {
    /** Built by the user, track by track. Fully editable. */
    MANUAL,

    /**
     * Imported from an M3U.
     *
     * Editable after import: once the tracks are in the library they are no different from any
     * other, and refusing edits would mean an imported list could never have a mistake corrected.
     */
    IMPORTED,

    /**
     * Derived from a rule rather than stored as members. See [SmartPlaylistRule].
     *
     * Its contents are recomputed, so adding a track by hand is meaningless — the next evaluation
     * would drop it again.
     */
    SMART,

    /** A snapshot of the playback queue, taken by "save queue as playlist". */
    SAVED_QUEUE,

    /**
     * Provided by the app itself, such as favourites or most played.
     *
     * Cannot be renamed or deleted: the user did not create it and would have no way to get it
     * back.
     */
    SYSTEM,
    ;

    /**
     * Whether [MusicPlaylist.tracks] is authoritative for this kind.
     *
     * Smart and system playlists carry no stored members; their contents come from evaluating a
     * rule against the library, so the manual add/remove operations reject them rather than writing
     * a member list that would be ignored.
     */
    val holdsOwnTracks: Boolean
        get() = this == MANUAL || this == IMPORTED || this == SAVED_QUEUE

    /**
     * Whether the user may rename or delete it.
     *
     * Only [SYSTEM] is protected. A smart playlist is the user's own rule and may be renamed like
     * anything else, even though its membership is computed.
     */
    val isUserManaged: Boolean
        get() = this != SYSTEM
}

/**
 * A playlist of tracks, identified by track id rather than by value.
 *
 * Ids rather than [MusicTrack] copies for two reasons. A stored [MusicTrack] would embed
 * [MusicTrack.streamUri] — a signed, credential-bearing address — into the playlist file, which
 * GDD 8 section 23 forbids persisting; and a copy would go stale the moment the underlying playlist
 * was re-imported. The track is resolved against the library at read time instead.
 *
 * [id] is stable for the life of the playlist. Renaming changes [name] only, so a playlist
 * referenced from anywhere else survives being renamed — see [renamed].
 */
data class MusicPlaylist(
    val id: String,
    val name: String,
    val kind: MusicPlaylistKind,
    /**
     * Member track ids, in the user's chosen order.
     *
     * Empty and meaningless for [MusicPlaylistKind.SMART] and [MusicPlaylistKind.SYSTEM], whose
     * contents come from [rule].
     */
    val trackIds: List<String> = emptyList(),
    /** The rule to evaluate, for smart and system playlists only. */
    val rule: SmartPlaylistRule? = null,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
) {
    val trackCount: Int
        get() = trackIds.size

    val isEditable: Boolean
        get() = kind.holdsOwnTracks

    /**
     * Renames without touching identity or membership.
     *
     * Explicitly a copy of everything but [name] and [updatedAtEpochMillis]: an earlier class of bug
     * in this repository was "edit" operations that rebuilt an entity and silently reset a field
     * nobody was looking at.
     */
    fun renamed(
        newName: String,
        nowEpochMillis: Long = updatedAtEpochMillis,
    ): MusicPlaylist {
        val clean = newName.trim()
        // A blank name is rejected rather than stored: the row would render as an unclickable gap
        // and the user would have no way to tell which playlist it was.
        if (clean.isEmpty()) return this
        return copy(name = clean, updatedAtEpochMillis = nowEpochMillis)
    }

    /**
     * Appends [trackId], ignoring a duplicate.
     *
     * A playlist is a set in the user's mind — adding the same song twice from a menu is a slip,
     * not an instruction. Deliberate duplicates are still reachable through [reordered], which does
     * not deduplicate.
     */
    fun withTrackAdded(
        trackId: String,
        nowEpochMillis: Long = updatedAtEpochMillis,
    ): MusicPlaylist {
        if (!isEditable || trackId.isBlank() || trackId in trackIds) return this
        return copy(trackIds = trackIds + trackId, updatedAtEpochMillis = nowEpochMillis)
    }

    fun withTrackRemoved(
        trackId: String,
        nowEpochMillis: Long = updatedAtEpochMillis,
    ): MusicPlaylist {
        if (!isEditable || trackId !in trackIds) return this
        return copy(trackIds = trackIds - trackId, updatedAtEpochMillis = nowEpochMillis)
    }

    /**
     * Moves the track at [fromIndex] to [toIndex].
     *
     * Out-of-range indices return the playlist unchanged rather than throwing: reorder is driven by
     * a drag, and a drag that ends outside the list is a cancelled gesture, not a programming error.
     */
    fun reordered(
        fromIndex: Int,
        toIndex: Int,
        nowEpochMillis: Long = updatedAtEpochMillis,
    ): MusicPlaylist {
        if (!isEditable) return this
        if (fromIndex !in trackIds.indices || toIndex !in trackIds.indices) return this
        if (fromIndex == toIndex) return this
        val moved = trackIds.toMutableList()
        moved.add(toIndex, moved.removeAt(fromIndex))
        return copy(trackIds = moved, updatedAtEpochMillis = nowEpochMillis)
    }

    /**
     * A copy under a new identity.
     *
     * The duplicate is always [MusicPlaylistKind.MANUAL], whatever the original was. Duplicating a
     * smart playlist is how a user says "keep these tracks as they are now" — carrying the rule
     * across would produce a second list that changes underneath them, which is the opposite of
     * what duplicating one is for. The resolved [tracksNow] are therefore frozen into the copy.
     */
    fun duplicated(
        newId: String,
        newName: String,
        nowEpochMillis: Long,
        tracksNow: List<String> = trackIds,
    ): MusicPlaylist =
        MusicPlaylist(
            id = newId,
            name = newName.trim().ifEmpty { name },
            kind = MusicPlaylistKind.MANUAL,
            trackIds = tracksNow,
            rule = null,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
        )

    /**
     * Resolves member ids against [library], dropping ids the library no longer contains.
     *
     * Dropping rather than rendering a placeholder: the user's M3U is theirs and may lose entries
     * between sessions, and a playlist row that cannot be played is worse than a shorter list. The
     * stored ids are left alone so that re-adding the source restores the entries.
     */
    fun resolve(library: MusicLibrary): List<MusicTrack> {
        val byId = library.tracks.associateBy(MusicTrack::id)
        return trackIds.mapNotNull(byId::get)
    }
}
