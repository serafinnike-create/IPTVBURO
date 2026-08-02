package com.lucasserafin94.iptvburo.desktop.user

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.prefs.Preferences

data class DesktopProfile(val id: String, val name: String, val isKids: Boolean)

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
            listOf(profile.id, encode(profile.name), if (profile.isKids) "1" else "0").joinToString(":")
        })
    }

    fun setActiveProfile(id: String?) {
        if (id == null) preferences.remove(KEY_ACTIVE_PROFILE) else preferences.put(KEY_ACTIVE_PROFILE, id)
    }

    fun setLanguage(language: DesktopLanguage) = preferences.put(KEY_LANGUAGE, language.tag)

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
            if (parts.size != 3) return@mapNotNull null
            runCatching { DesktopProfile(parts[0], decode(parts[1]), parts[2] == "1") }.getOrNull()
        }.take(5)

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String =
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)

    private companion object {
        const val KEY_PROFILES = "profiles"
        const val KEY_ACTIVE_PROFILE = "active_profile"
        const val KEY_LANGUAGE = "language"
        const val KEY_LEGACY_FAVORITES = "favorites"
    }
}
