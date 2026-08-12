package com.lucasserafin94.iptvburo.desktop.user

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.prefs.Preferences

/**
 * Names the user has corrected, per profile.
 *
 * Corrections are an overlay keyed by track id: the user's M3U remains untouched and can be
 * replaced at any time. Only the id, title and artist are stored. A stream URI is deliberately
 * never persisted because it commonly contains subscription credentials.
 *
 * Each correction has its own Preferences value. A previous single-value representation had an
 * 8 KiB ceiling and silently discarded the newest corrections in a large tidy. Per-record values
 * keep that platform limit local to one pathological title instead of imposing it on the library.
 */
class MusicCorrectionStore(
    private val preferences: Preferences =
        Preferences.userRoot().node("com/lucasserafin94/iptvburo/music-corrections-v2"),
) {
    /** Every readable correction for [profileId], keyed by the original track id. */
    fun correctionsFor(profileId: String?): Map<String, MusicCorrection> {
        val id = profileId ?: return emptyMap()
        val node = profileNode(id)
        return runCatching { node.keys().asSequence() }
            .getOrDefault(emptySequence())
            .mapNotNull { key -> node.get(key, null)?.let(::decode) }
            // One damaged value costs only itself. Hash collisions are practically impossible,
            // but associateBy also makes their behaviour deterministic if one ever occurs.
            .associateBy(MusicCorrection::trackId)
    }

    /** Replaces the whole set and returns how many rows were stored. */
    fun save(
        profileId: String?,
        corrections: Map<String, MusicCorrection>,
    ): Int {
        val id = profileId ?: return 0
        val node = profileNode(id)
        runCatching { node.keys().forEach(node::remove) }
        return corrections.values.count { correction -> putRecord(node, correction) }
    }

    /** Adds or replaces one correction. False means the row exceeded Preferences' value limit. */
    fun put(
        profileId: String?,
        correction: MusicCorrection,
    ): Boolean = profileId?.let { id -> putRecord(profileNode(id), correction) } ?: false

    /** Adds or replaces several corrections and returns the number successfully stored. */
    fun putAll(
        profileId: String?,
        corrections: List<MusicCorrection>,
    ): Int {
        val id = profileId ?: return 0
        val node = profileNode(id)
        return corrections.associateBy(MusicCorrection::trackId).values.count { correction ->
            putRecord(node, correction)
        }
    }

    /** Restores a track to whatever the playlist says. */
    fun remove(
        profileId: String?,
        trackId: String,
    ) {
        profileId?.let { id -> profileNode(id).remove(correctionKey(trackId)) }
    }

    /** Undoes every correction for this profile. */
    fun clear(profileId: String?) {
        val id = profileId ?: return
        val node = profileNode(id)
        runCatching { node.keys().forEach(node::remove) }
    }

    private fun putRecord(
        node: Preferences,
        correction: MusicCorrection,
    ): Boolean {
        val encoded = encode(correction)
        if (encoded.length > Preferences.MAX_VALUE_LENGTH) return false
        node.put(correctionKey(correction.trackId), encoded)
        return true
    }

    private fun profileNode(profileId: String): Preferences =
        preferences.node("profiles").node(digest(profileId))

    private fun correctionKey(trackId: String): String = "c.${digest(trackId)}"

    /** Base64 per field lets titles contain any punctuation without an escaping format. */
    private fun encode(correction: MusicCorrection): String =
        listOf(correction.trackId, correction.title, correction.artist.orEmpty())
            .joinToString(FIELD_SEPARATOR) { field ->
                Base64.getEncoder().encodeToString(field.toByteArray(StandardCharsets.UTF_8))
            }

    private fun decode(record: String): MusicCorrection? =
        runCatching {
            val fields = record.split(FIELD_SEPARATOR)
            if (fields.size != 3) return null
            val decoded = fields.map { field ->
                String(Base64.getDecoder().decode(field), StandardCharsets.UTF_8)
            }
            MusicCorrection(
                trackId = decoded[0].ifBlank { return null },
                title = decoded[1],
                artist = decoded[2].takeIf(String::isNotBlank),
            )
        }.getOrNull()

    private fun digest(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)),
        )

    private companion object {
        const val FIELD_SEPARATOR = ":"
    }
}

/** A corrected display name. Stream addresses never enter this type. */
data class MusicCorrection(
    val trackId: String,
    val title: String,
    val artist: String?,
)
