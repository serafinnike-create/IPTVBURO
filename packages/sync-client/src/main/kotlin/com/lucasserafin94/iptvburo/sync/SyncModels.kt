package com.lucasserafin94.iptvburo.sync

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

/** One record as the server stores it: an opaque key, when it was last edited, and its content. */
data class SyncRecord(
    val key: String,
    val updatedAtMillis: Long,
    val deleted: Boolean,
    val data: String,
)

/**
 * What a device shares with the others in its household.
 *
 * Titles are joined across devices by [identityKey] — the normalised title, kind and year the app
 * already uses for shared links — never by a catalogue row id, which is minted on import and means
 * nothing on another device or another provider. Profiles are joined by name, normalised, for the
 * same reason: a profile id is local to the device that created it.
 */
sealed interface SyncEntry {
    val profile: String
    val identityKey: String
    val title: String
    val year: Int?

    /** A title marked as a favourite by [profile]. */
    data class Favorite(
        override val profile: String,
        override val identityKey: String,
        override val title: String,
        override val year: Int?,
        val kind: String,
    ) : SyncEntry

    /** Where [profile] left a film or an episode. */
    data class Position(
        override val profile: String,
        override val identityKey: String,
        override val title: String,
        override val year: Int?,
        val kind: String,
        val positionMillis: Long,
        val durationMillis: Long,
        val completed: Boolean,
    ) : SyncEntry

    /** The record key: a hash of what this is, for whom and for which title — so it names no title. */
    val recordKey: String
        get() = recordKeyFor(if (this is Favorite) "fav" else "pos", profile, identityKey)

    companion object {
        /** Profile names compared without case or surrounding space: "Ana" here is "ana " there. */
        fun normaliseProfile(name: String): String = name.trim().lowercase(Locale.ROOT)

        internal fun recordKeyFor(type: String, profile: String, identityKey: String): String {
            val digest =
                MessageDigest.getInstance("SHA-256")
                    .digest("iptvburo-sync-v1|$type|${normaliseProfile(profile)}|$identityKey".toByteArray())
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }
    }
}

/** Entries to and from the JSON a record carries. Unknown or malformed content decodes to null. */
object SyncEntryCodec {
    fun encode(entry: SyncEntry): String =
        JsonObject().apply {
            addProperty("v", 1)
            addProperty("p", SyncEntry.normaliseProfile(entry.profile))
            addProperty("id", entry.identityKey)
            addProperty("title", entry.title)
            entry.year?.let { addProperty("year", it) }
            when (entry) {
                is SyncEntry.Favorite -> {
                    addProperty("t", "fav")
                    addProperty("kind", entry.kind)
                }
                is SyncEntry.Position -> {
                    addProperty("t", "pos")
                    addProperty("kind", entry.kind)
                    addProperty("pos", entry.positionMillis)
                    addProperty("dur", entry.durationMillis)
                    addProperty("done", entry.completed)
                }
            }
        }.toString()

    fun decode(data: String): SyncEntry? =
        runCatching {
            val json = JsonParser.parseString(data).asJsonObject
            val profile = json.get("p").asString
            val identity = json.get("id").asString
            val title = json.get("title").asString
            val year = json.get("year")?.takeUnless { it.isJsonNull }?.asInt
            val kind = json.get("kind").asString
            when (json.get("t").asString) {
                "fav" -> SyncEntry.Favorite(profile, identity, title, year, kind)
                "pos" ->
                    SyncEntry.Position(
                        profile = profile,
                        identityKey = identity,
                        title = title,
                        year = year,
                        kind = kind,
                        positionMillis = json.get("pos").asLong.coerceAtLeast(0L),
                        durationMillis = json.get("dur").asLong.coerceAtLeast(0L),
                        completed = json.get("done").asBoolean,
                    )
                else -> null
            }
        }.getOrNull()
}
