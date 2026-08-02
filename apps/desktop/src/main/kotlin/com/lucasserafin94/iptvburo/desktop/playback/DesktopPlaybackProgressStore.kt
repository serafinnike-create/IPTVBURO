package com.lucasserafin94.iptvburo.desktop.playback

import com.lucasserafin94.iptvburo.domain.model.PlaybackContentType
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgress
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgressIdentity
import com.lucasserafin94.iptvburo.domain.model.PlaybackProgressRepository
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.prefs.Preferences

/** Local, non-secret playback progress. Resolved URLs and credentials are never accepted here. */
class DesktopPlaybackProgressStore(
    private val preferences: Preferences = Preferences.userRoot().node("com/lucasserafin94/iptvburo/playback-progress-v1"),
) : PlaybackProgressRepository {
    override fun find(identity: PlaybackProgressIdentity): PlaybackProgress? =
        synchronized(preferences) { preferences.get(storageKey(identity), null)?.let(::decode) }

    override fun save(progress: PlaybackProgress) {
        synchronized(preferences) {
            val key = storageKey(progress.identity)
            val current = preferences.get(key, null)?.let(::decode)
            if (current != null && current.revision > progress.revision) return
            if (current?.completedAtEpochMillis != null && progress.completedAtEpochMillis == null) return
            preferences.put(key, encode(progress))
            preferences.flush()
        }
    }

    override fun remove(identity: PlaybackProgressIdentity) {
        synchronized(preferences) {
            preferences.remove(storageKey(identity))
            preferences.flush()
        }
    }

    override fun continueWatching(profileId: String, limit: Int): List<PlaybackProgress> =
        synchronized(preferences) {
            preferences.keys().asSequence()
                .mapNotNull { preferences.get(it, null)?.let(::decode) }
                .filter { it.identity.profileId == profileId && it.completedAtEpochMillis == null }
                .sortedByDescending(PlaybackProgress::lastWatchedAtEpochMillis)
                .take(limit.coerceAtLeast(0))
                .toList()
        }

    private fun storageKey(identity: PlaybackProgressIdentity): String {
        val canonical = listOf(identity.profileId, identity.sourceId, identity.contentType.name, identity.contentId).joinToString("\u001F")
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun encode(progress: PlaybackProgress): String {
        val id = progress.identity
        return listOf(
            VERSION,
            text(id.profileId),
            text(id.sourceId),
            text(id.contentId),
            id.contentType.name,
            text(id.seriesId.orEmpty()),
            id.seasonNumber?.toString().orEmpty(),
            id.episodeNumber?.toString().orEmpty(),
            progress.positionMs.toString(),
            progress.durationMs.toString(),
            progress.progressPercent.toString(),
            progress.lastWatchedAtEpochMillis.toString(),
            progress.completedAtEpochMillis?.toString().orEmpty(),
            progress.updatedAtEpochMillis.toString(),
            progress.revision.toString(),
        ).joinToString("|")
    }

    private fun decode(value: String): PlaybackProgress? = runCatching {
        val fields = value.split('|')
        require(fields.size == 15 && fields[0] == VERSION)
        val identity = PlaybackProgressIdentity(
            profileId = plain(fields[1]),
            sourceId = plain(fields[2]),
            contentId = plain(fields[3]),
            contentType = PlaybackContentType.valueOf(fields[4]),
            seriesId = plain(fields[5]).ifBlank { null },
            seasonNumber = fields[6].toIntOrNull(),
            episodeNumber = fields[7].toIntOrNull(),
        )
        PlaybackProgress(
            identity = identity,
            positionMs = fields[8].toLong(),
            durationMs = fields[9].toLong(),
            progressPercent = fields[10].toDouble(),
            lastWatchedAtEpochMillis = fields[11].toLong(),
            completedAtEpochMillis = fields[12].toLongOrNull(),
            updatedAtEpochMillis = fields[13].toLong(),
            revision = fields[14].toLong(),
        )
    }.getOrNull()

    private fun text(value: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    private fun plain(value: String): String = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)

    private companion object { const val VERSION = "1" }
}
