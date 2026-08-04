package com.lucasserafin94.iptvburo.desktop.user

import com.lucasserafin94.iptvburo.domain.model.ListeningHistoryEntry
import com.lucasserafin94.iptvburo.domain.model.ListeningHistoryRules
import com.lucasserafin94.iptvburo.domain.model.ListeningKind
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.prefs.Preferences

/**
 * Listening history, per profile — the record described in GDD 8 section 18.
 *
 * This supersedes [MusicPlayCountStore] rather than sitting beside it. A play count is one fact,
 * and two stores holding it would disagree the first time one was written and the other was not;
 * the count now lives in [ListeningHistoryEntry.playCount] and nowhere else. Existing counts are
 * migrated on first read, so a user who has been listening does not lose their "most played" shelf
 * — see [migrateLegacyCounts].
 *
 * Stored in ordinary preferences, like the counts before it. History is not a secret, but it is
 * personal: the identities it is keyed by are positions in the user's own playlist, and no resolved
 * URL, token or credential is ever written here. Section 23 forbids persisting those, and
 * [ListeningHistoryEntry] redacts them from its own `toString` for the same reason.
 */
class ListeningHistoryStore(
    private val preferences: Preferences =
        Preferences.userRoot().node("com/lucasserafin94/iptvburo/listening-v1"),
    /**
     * The legacy count store, read once per profile so existing rankings survive the change.
     *
     * Injectable so the migration itself can be tested against a known starting state.
     */
    private val legacyCounts: MusicPlayCountStore = MusicPlayCountStore(),
) {
    /**
     * Every history row for [profileId], keyed by media identity.
     *
     * An absent or unreadable entry reads as "no history" rather than throwing: corrupt history
     * must cost the user their ordering, never their access to the library.
     */
    fun historyFor(profileId: String?): Map<String, ListeningHistoryEntry> {
        val id = profileId ?: return emptyMap()
        migrateLegacyCounts(id)
        val raw = preferences.get(historyKey(id), "")
        if (raw.isBlank()) return emptyMap()
        return raw.split(RECORD_SEPARATOR)
            .mapNotNull { record -> decodeEntry(id, record) }
            .associateBy(ListeningHistoryEntry::mediaIdentity)
    }

    /** Play counts by identity, the shape the existing "most played" shelf consumes. */
    fun playCountsFor(profileId: String?): Map<String, Int> =
        historyFor(profileId)
            .filterValues(ListeningHistoryEntry::hasBeenPlayed)
            .mapValues { (_, entry) -> entry.playCount }

    /**
     * Folds one playback report into the profile's history and returns the updated rows.
     *
     * The threshold decision belongs to [ListeningHistoryRules], not here: this class owns storage,
     * and duplicating the per-kind rules in a store is how the two would drift apart.
     */
    fun record(
        profileId: String?,
        mediaIdentity: String,
        kind: ListeningKind,
        atEpochMillis: Long = System.currentTimeMillis(),
        listenedMillis: Long = 0L,
        positionMillis: Long? = null,
        durationMillis: Long? = null,
        sourceId: String? = null,
        completed: Boolean = false,
    ): Map<String, ListeningHistoryEntry> {
        if (profileId == null || mediaIdentity.isBlank()) return historyFor(profileId)
        val current = historyFor(profileId).toMutableMap()
        current[mediaIdentity] =
            ListeningHistoryRules.record(
                existing = current[mediaIdentity],
                profileId = profileId,
                mediaIdentity = mediaIdentity,
                kind = kind,
                atEpochMillis = atEpochMillis,
                listenedMillis = listenedMillis,
                positionMillis = positionMillis,
                durationMillis = durationMillis,
                sourceId = sourceId,
                completed = completed,
            )
        val trimmed = trim(current)
        write(profileId, trimmed)
        return trimmed
    }

    fun clear(profileId: String) {
        preferences.remove(historyKey(profileId))
        preferences.remove(migratedKey(profileId))
    }

    /**
     * Caps the stored history, dropping the least recently played first.
     *
     * [Preferences] holds one string per key with a hard length limit, so history cannot grow
     * without bound. Recency is the right thing to keep: a "most played" ranking survives trimming
     * because a heavily played track is also a recently played one, whereas dropping by count would
     * throw away the entire "recently played" shelf.
     */
    private fun trim(entries: Map<String, ListeningHistoryEntry>): Map<String, ListeningHistoryEntry> {
        if (entries.size <= MAX_TRACKED) return entries
        return entries.entries
            .sortedByDescending { it.value.lastPlayedAtEpochMillis }
            .take(MAX_TRACKED)
            .associate { it.key to it.value }
    }

    private fun write(
        profileId: String,
        entries: Map<String, ListeningHistoryEntry>,
    ) {
        preferences.put(
            historyKey(profileId),
            entries.values.joinToString(RECORD_SEPARATOR.toString(), transform = ::encodeEntry),
        )
    }

    /**
     * Copies play counts out of [MusicPlayCountStore] the first time a profile's history is read.
     *
     * Runs once, guarded by its own flag, and never overwrites history that already exists. The
     * legacy counts are left in place rather than deleted: if this version is rolled back, the old
     * shelf still works, and a stale count is harmless once the flag says migration has happened.
     *
     * Migrated rows carry a zero timestamp and a play count only. There is no way to recover when
     * those plays happened, so they rank in "most played" but do not fabricate a "recently played"
     * ordering out of times that were never recorded.
     */
    private fun migrateLegacyCounts(profileId: String) {
        if (preferences.getBoolean(migratedKey(profileId), false)) return
        // Set the flag first: a migration that throws half way must not run again and double every
        // count it had already written.
        preferences.putBoolean(migratedKey(profileId), true)

        val counts = runCatching { legacyCounts.countsFor(profileId) }.getOrNull().orEmpty()
        if (counts.isEmpty()) return
        if (preferences.get(historyKey(profileId), "").isNotBlank()) return

        val migrated =
            counts.entries.associate { (identity, count) ->
                identity to
                    ListeningHistoryEntry(
                        profileId = profileId,
                        mediaIdentity = identity,
                        // Everything the legacy store held was music; it was only ever written by
                        // the music shelf.
                        kind = ListeningKind.MUSIC,
                        startedAtEpochMillis = 0L,
                        lastPlayedAtEpochMillis = 0L,
                        playCount = count,
                    )
            }
        write(profileId, trim(migrated))
    }

    /**
     * One row, as base64 fields joined by [FIELD_SEPARATOR].
     *
     * The identity is base64 because it comes from the playlist's own tvg-id and may contain the
     * characters this format separates on — the same reason [MusicPlayCountStore] encodes it.
     */
    private fun encodeEntry(entry: ListeningHistoryEntry): String =
        listOf(
            encode(entry.mediaIdentity),
            entry.kind.name,
            entry.startedAtEpochMillis.toString(),
            entry.lastPlayedAtEpochMillis.toString(),
            entry.playCount.toString(),
            entry.completedCount.toString(),
            entry.lastPositionMillis?.toString().orEmpty(),
            entry.durationMillis?.toString().orEmpty(),
            entry.sourceId?.let(::encode).orEmpty(),
        ).joinToString(FIELD_SEPARATOR.toString())

    /**
     * Decodes one row, or null when it cannot be read.
     *
     * Lenient by row rather than by store: one corrupt entry costs the user that entry, not their
     * whole listening history. An unknown kind name — written by a later version — drops the row
     * instead of guessing a kind whose progress rules would then be wrong.
     */
    private fun decodeEntry(
        profileId: String,
        record: String,
    ): ListeningHistoryEntry? {
        val parts = record.split(FIELD_SEPARATOR)
        if (parts.size < 6) return null
        return runCatching {
            val identity = decode(parts[0]).takeIf(String::isNotBlank) ?: return null
            val kind = ListeningKind.entries.firstOrNull { it.name == parts[1] } ?: return null
            ListeningHistoryEntry(
                profileId = profileId,
                mediaIdentity = identity,
                kind = kind,
                startedAtEpochMillis = parts[2].toLongOrNull() ?: 0L,
                lastPlayedAtEpochMillis = parts[3].toLongOrNull() ?: 0L,
                playCount = parts[4].toIntOrNull()?.coerceAtLeast(0) ?: 0,
                completedCount = parts[5].toIntOrNull()?.coerceAtLeast(0) ?: 0,
                lastPositionMillis = parts.getOrNull(6)?.toLongOrNull(),
                durationMillis = parts.getOrNull(7)?.toLongOrNull(),
                sourceId =
                    parts.getOrNull(8)?.takeIf(String::isNotBlank)?.let { stored ->
                        runCatching { decode(stored) }.getOrNull()
                    },
            )
        }.getOrNull()
    }

    private fun historyKey(profileId: String): String = "history.$profileId"

    private fun migratedKey(profileId: String): String = "history-migrated.$profileId"

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String =
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)

    private companion object {
        /**
         * Enough to rank the shelves many times over without approaching the preference value
         * length limit. Matches the legacy store's cap so migration can never grow the data.
         */
        const val MAX_TRACKED = 300
        const val RECORD_SEPARATOR = ';'
        const val FIELD_SEPARATOR = ':'
    }
}
