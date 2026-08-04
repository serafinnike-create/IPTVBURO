package com.lucasserafin94.iptvburo.desktop.user

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.prefs.Preferences

/**
 * [avatarIndex] selects one of the drawn avatars in BURO_AVATARS. Stored as an index rather than an
 * image so the choice survives a reinstall and costs nothing on disk. A photo, when the user picks
 * one, lives separately in ProfilePhotoStore and takes precedence over the index.
 */
data class DesktopProfile(
    val id: String,
    val name: String,
    val isKids: Boolean,
    val avatarIndex: Int = 0,
    /**
     * The playlist this profile signs in to, or null to use whichever is already connected.
     *
     * Two profiles may share one id — a household on one subscription, each with their own
     * favourites — or hold different ones, in which case switching profile switches the account.
     */
    val sourceId: String? = null,
    /**
     * An M3U of music, supplied by the user and entirely optional.
     *
     * Null is the ordinary case and means the app behaves exactly as it did before music existed:
     * no Músicas entry, no extra work at startup. Stored as a path rather than as parsed content
     * because the file is the user's own and may change between sessions.
     */
    val musicPlaylistPath: String? = null,
)



enum class DesktopLanguage(val tag: String) {
    PORTUGUESE_BRAZIL("pt-BR"), ENGLISH("en"), GERMAN("de"), ITALIAN("it");

    companion object {
        fun fromTag(tag: String?): DesktopLanguage = entries.firstOrNull { it.tag == tag } ?: PORTUGUESE_BRAZIL
    }
}

data class DesktopUserSnapshot(
    val profiles: List<DesktopProfile>,
    val activeProfileId: String?,
    val language: DesktopLanguage,
    val favoriteKeys: Set<String>,
)

class DesktopUserStore(
    private val preferences: Preferences = Preferences.userRoot().node("com/lucasserafin94/iptvburo/user-v1"),
) {
    fun load(): DesktopUserSnapshot {
        val profiles = decodeProfiles(preferences.get(KEY_PROFILES, ""))
            .ifEmpty { listOf(DesktopProfile(UUID.randomUUID().toString(), "Meu perfil", false)) }
        if (preferences.get(KEY_PROFILES, "").isBlank()) saveProfiles(profiles)
        val active = preferences.get(KEY_ACTIVE_PROFILE, null)?.takeIf { id -> profiles.any { it.id == id } }
        migrateLegacyFavorites(profiles.first().id)
        val favorites = favoritesForProfile(active)
        return DesktopUserSnapshot(profiles, active, DesktopLanguage.fromTag(preferences.get(KEY_LANGUAGE, null)), favorites)
    }

    fun saveProfiles(profiles: List<DesktopProfile>) {
        require(profiles.size in 1..5)
        preferences.put(KEY_PROFILES, profiles.joinToString(";") { profile ->
            listOf(
                profile.id,
                encode(profile.name),
                if (profile.isKids) "1" else "0",
                profile.avatarIndex.toString(),
                // A UUID contains no ':' or ';', so it needs no escaping to survive this format.
                profile.sourceId.orEmpty(),
                // Base64 like the name, not raw: a Windows path carries the drive's own ':', which
                // is this format's field separator, so an unencoded "D:\music.m3u" would split into
                // two fields and take the row's field count out of range.
                profile.musicPlaylistPath?.let(::encode).orEmpty(),
            ).joinToString(":")
        })
    }

    fun setActiveProfile(id: String?) {
        if (id == null) preferences.remove(KEY_ACTIVE_PROFILE) else preferences.put(KEY_ACTIVE_PROFILE, id)
    }

    fun setLanguage(language: DesktopLanguage) = preferences.put(KEY_LANGUAGE, language.tag)

    /** Whether the user has ever picked a language, used to decide if first-run setup is needed. */
    fun hasChosenLanguage(): Boolean = preferences.get(KEY_LANGUAGE, null) != null

    /**
     * Whether the copyright notice has been acknowledged.
     *
     * Shown once. The app carries no catalogue of its own; it plays what the user's provider
     * serves, and the notice says so before anything is configured.
     */
    /**
     * How the catalogue grid is laid out, remembered per profile.
     *
     * Per profile rather than per install: one person browsing posters and another scanning a dense
     * list is exactly the kind of preference a shared machine has two of.
     */
    fun catalogLayout(profileId: String?): String? =
        profileId?.let { preferences.get(layoutKey(it), null) }

    fun setCatalogLayout(profileId: String, value: String) =
        preferences.put(layoutKey(profileId), value)

    private fun layoutKey(profileId: String): String = "catalog-layout.$profileId"

    fun hasAcceptedTerms(): Boolean = preferences.getBoolean(KEY_TERMS_ACCEPTED, false)

    fun setAcceptedTerms() = preferences.putBoolean(KEY_TERMS_ACCEPTED, true)

    /**
     * The user's own TMDb key, used for cast photos and filmographies.
     *
     * Kept in plain preferences rather than the credential vault: it is a personal API key for a
     * public catalogue, not an account credential, and treating it as a secret would mean it could
     * not be shown back to the user to check what they pasted. It is never sent anywhere except to
     * TMDb itself.
     */
    fun metadataApiKey(): String? = preferences.get(KEY_METADATA_KEY, null)?.takeIf(String::isNotBlank)

    fun setMetadataApiKey(value: String?) {
        val clean = value?.trim().orEmpty()
        if (clean.isBlank()) preferences.remove(KEY_METADATA_KEY) else preferences.put(KEY_METADATA_KEY, clean)
    }

    /**
     * Clears every stored preference for this user: profiles, favourites, language and the active
     * profile.
     *
     * Downloaded files are deliberately left alone. They are the user's own media, and removing
     * them from a settings reset would be a surprise.
     */
    fun resetAll() {
        preferences.removeNode()
        preferences.flush()
    }

    fun favoritesForProfile(profileId: String?): Set<String> =
        profileId
            ?.let { preferences.get(favoritesKey(it), "") }
            ?.split(',')
            ?.filter(String::isNotBlank)
            ?.toSet()
            .orEmpty()

    fun setFavorites(profileId: String, keys: Set<String>) =
        preferences.put(favoritesKey(profileId), keys.sorted().joinToString(","))

    private fun migrateLegacyFavorites(firstProfileId: String) {
        val legacy = preferences.get(KEY_LEGACY_FAVORITES, "")
        if (legacy.isBlank()) return
        val destination = favoritesKey(firstProfileId)
        if (preferences.get(destination, "").isBlank()) preferences.put(destination, legacy)
        preferences.remove(KEY_LEGACY_FAVORITES)
    }

    private fun favoritesKey(profileId: String): String = "favorites.$profileId"

    private fun decodeProfiles(raw: String): List<DesktopProfile> =
        raw.split(';').mapNotNull { encoded ->
            val parts = encoded.split(':')
            // Rows written before avatars existed have three fields, before per-profile sources
            // four, and before the optional music playlist five; every one of them decodes with the
            // later fields defaulted rather than being discarded.
            if (parts.size !in 3..6) return@mapNotNull null
            runCatching {
                DesktopProfile(
                    id = parts[0],
                    name = decode(parts[1]),
                    isKids = parts[2] == "1",
                    avatarIndex =
                        // Not clamped to the set size: the avatar list can grow, and clamping here
                        // would rewrite a stored choice into a different face. Out-of-range values
                        // are resolved when drawn.
                        parts.getOrNull(3)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                    sourceId = parts.getOrNull(4)?.takeIf(String::isNotBlank),
                    // Decoded leniently: an unreadable path costs the user their music playlist,
                    // which they can point at again, whereas failing the row would cost them the
                    // profile itself along with its favourites.
                    musicPlaylistPath =
                        parts.getOrNull(5)?.takeIf(String::isNotBlank)?.let { stored ->
                            runCatching { decode(stored) }.getOrNull()
                        },
                )
            }.getOrNull()
        }.take(5)

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String =
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)

    private companion object {
        const val KEY_PROFILES = "profiles"
        const val KEY_ACTIVE_PROFILE = "active_profile"
        const val KEY_LANGUAGE = "language"
        const val KEY_TERMS_ACCEPTED = "terms-accepted"
        const val KEY_METADATA_KEY = "metadata-api-key"
        const val KEY_LEGACY_FAVORITES = "favorites"
    }
}
