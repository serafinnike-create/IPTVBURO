package com.lucasserafin94.iptvburo.desktop.user

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.prefs.Preferences

/**
 * How often each track has been played, per profile.
 *
 * "Most played" is the one music shelf that cannot be derived from the playlist: an M3U carries no
 * play history, so unless the app counts plays itself the shelf has nothing to show. Counts are
 * kept per profile for the same reason favourites are — a household shares one subscription but not
 * one taste.
 *
 * Stored in ordinary preferences rather than the credential vault. A play count is not a secret,
 * and the track ids it is keyed by are positions in the user's own playlist, not stream URLs.
 */
class MusicPlayCountStore(
    private val preferences: Preferences =
        Preferences.userRoot().node("com/lucasserafin94/iptvburo/music-v1"),
) {
    /**
     * Counts for [profileId], keyed by track id.
     *
     * An absent or unreadable entry reads as "nothing played yet" rather than throwing: a corrupt
     * count must cost the user their ordering, never their access to the library.
     */
    fun countsFor(profileId: String?): Map<String, Int> {
        val raw = profileId?.let { preferences.get(countsKey(it), "") }.orEmpty()
        if (raw.isBlank()) return emptyMap()
        return raw.split(';')
            .mapNotNull { entry ->
                val parts = entry.split(':')
                if (parts.size != 2) return@mapNotNull null
                val id = runCatching { decode(parts[0]) }.getOrNull() ?: return@mapNotNull null
                val count = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                id to count
            }.toMap()
    }

    /**
     * Records one play of [trackId] and returns the updated counts.
     *
     * The whole map is rewritten rather than appended to, because [Preferences] stores one string
     * per key and there is no partial write. The map stays small: it is capped at [MAX_TRACKED]
     * entries, dropping the least played, so a user who listens for years does not accumulate an
     * unbounded preference value.
     */
    fun recordPlay(
        profileId: String?,
        trackId: String,
    ): Map<String, Int> {
        if (profileId == null || trackId.isBlank()) return countsFor(profileId)
        val updated = countsFor(profileId).toMutableMap()
        updated[trackId] = (updated[trackId] ?: 0) + 1

        val trimmed =
            if (updated.size <= MAX_TRACKED) {
                updated
            } else {
                updated.entries
                    .sortedByDescending(Map.Entry<String, Int>::value)
                    .take(MAX_TRACKED)
                    .associate { it.key to it.value }
            }
        preferences.put(
            countsKey(profileId),
            trimmed.entries.joinToString(";") { (id, count) -> "${encode(id)}:$count" },
        )
        return trimmed
    }

    fun clear(profileId: String) = preferences.remove(countsKey(profileId))

    private fun countsKey(profileId: String): String = "plays.$profileId"

    // Base64 because a track id is derived from the playlist's own tvg-id, which may contain the
    // ':' and ';' this format separates on.
    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String =
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)

    private companion object {
        /** Enough to rank a "most played" shelf many times over without growing without bound. */
        const val MAX_TRACKED = 300
    }
}
