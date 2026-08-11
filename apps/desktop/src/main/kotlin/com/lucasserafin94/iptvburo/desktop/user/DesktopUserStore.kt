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



/**
 * A profile's stated streaming situation, as persisted.
 *
 * Deliberately all-optional and all-defaulted: a profile that has never opened Assinaturas has no
 * stored row, and this is what it gets. Nothing here is a claim about what the user can actually
 * watch — it only lets an offer they already pay for be ranked above one they would have to buy.
 */
data class StoredStreamingPreference(
    /** ISO 3166-1 alpha-2, or null when the user has not said. */
    val region: String? = null,
    /** ISO 4217, or null to show whatever currency the provider quoted. */
    val currency: String? = null,
    val subscribedProviderIds: Set<String> = emptySet(),
)

/**
 * A profile's parental lock, as persisted.
 *
 * [salt] and [hash] are absent until a PIN is set, which is what [hasPin] tests: a lock with
 * categories listed but no PIN cannot ask for anything, so the UI must not offer it.
 */
/**
 * Where the window was and how big, so the next launch reopens it that way.
 *
 * Sizes are in density-independent pixels, as Compose reports them: storing device pixels would
 * reopen at the wrong size on a machine whose scaling has changed.
 */
data class StoredWindowGeometry(
    val maximised: Boolean,
    val width: Float,
    val height: Float,
    val x: Float,
    val y: Float,
)

data class StoredParentalLock(
    val salt: String? = null,
    val hash: String? = null,
    val lockAdultCategories: Boolean = true,
    val lockedCategoryIds: Set<String> = emptySet(),
) {
    val hasPin: Boolean
        get() = !salt.isNullOrBlank() && !hash.isNullOrBlank()
}

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
        // No profile is invented for a new installation.
        //
        // This used to manufacture one called "Meu perfil" and save it immediately, so a customer
        // opening the app for the first time was met by a profile somebody else had apparently
        // made, offering only "Editar". The screen that asks for a name and a list — the one they
        // are supposed to see — was never reached, and creating their own left two profiles behind.
        //
        // Empty is a real state and the setup flow already knows how to handle it: no profile means
        // the app has never been set up, which is exactly what is true.
        val profiles = decodeProfiles(preferences.get(KEY_PROFILES, ""))
        val active = preferences.get(KEY_ACTIVE_PROFILE, null)?.takeIf { id -> profiles.any { it.id == id } }
        // Only when there is something to migrate onto. Reading `first()` on an empty list is what
        // made the fabricated profile necessary in the first place.
        profiles.firstOrNull()?.let { first -> migrateLegacyFavorites(first.id) }
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
     * Whether a catalogue has ever finished loading on this machine.
     *
     * Distinguishes the first, long start — where the whole list is read for the first time and the
     * wait needs explaining — from every later one. Per install rather than per profile: the wait
     * belongs to the machine, and a second profile on a set-up machine starts fast.
     */
    fun hasCompletedFirstStartup(): Boolean = preferences.getBoolean(KEY_FIRST_STARTUP_DONE, false)

    /**
     * How the window was left, or null on a machine that has never resized it.
     *
     * Null is deliberately the first-run answer, and the caller opens maximised for it: a catalogue
     * of posters is a poor fit for a small default window, and the user who wants one will make one.
     * After that, whatever they left is what they get.
     *
     * Stored as `maximised|width|height|x|y` in one string rather than five keys, because the five
     * are only ever meaningful together — a half-written set would place a window nowhere.
     */
    fun windowGeometry(): StoredWindowGeometry? {
        val raw = preferences.get(KEY_WINDOW_GEOMETRY, null) ?: return null
        val parts = raw.split('|')
        if (parts.size != 5) return null
        return runCatching {
            StoredWindowGeometry(
                maximised = parts[0] == "1",
                width = parts[1].toFloat(),
                height = parts[2].toFloat(),
                x = parts[3].toFloat(),
                y = parts[4].toFloat(),
            )
        }.getOrNull()
    }

    /**
     * Artwork URLs to drift behind the next launch's loading screen.
     *
     * Written when a home is built and read before anything is loaded, because the backdrop has to
     * be on screen during the wait rather than after it. Fetching them at startup would add a
     * network round trip to the very wait this is meant to soften.
     *
     * Poster addresses only: they are public, unauthenticated, and carry no credential. A stream
     * URL would never be stored here.
     */
    fun backdropPosters(): List<String> =
        preferences
            .get(KEY_BACKDROP_POSTERS, null)
            ?.split('\n')
            ?.filter(String::isNotBlank)
            .orEmpty()

    fun setBackdropPosters(urls: List<String>) {
        // Preferences caps a value at 8 KB, and a URL is roughly 60 bytes; the cap keeps a long
        // list from being rejected wholesale, which would silently store nothing.
        val capped = urls.filter(String::isNotBlank).take(MAX_BACKDROP_POSTERS)
        if (capped.isEmpty()) {
            preferences.remove(KEY_BACKDROP_POSTERS)
        } else {
            preferences.put(KEY_BACKDROP_POSTERS, capped.joinToString("\n"))
        }
    }

    fun setWindowGeometry(geometry: StoredWindowGeometry) {
        preferences.put(
            KEY_WINDOW_GEOMETRY,
            listOf(
                if (geometry.maximised) "1" else "0",
                geometry.width,
                geometry.height,
                geometry.x,
                geometry.y,
            ).joinToString("|"),
        )
    }

    fun markFirstStartupComplete() {
        preferences.putBoolean(KEY_FIRST_STARTUP_DONE, true)
    }

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
    /**
     * The layout of one section, per profile.
     *
     * Keyed by section as well as profile: films, series and live channels are browsed differently,
     * and a single shared value meant changing one changed all three.
     *
     * Falls back to the old profile-wide key so an existing choice is not lost on upgrade.
     */
    fun catalogLayout(
        profileId: String?,
        section: String,
    ): String? =
        profileId?.let { id ->
            preferences.get(layoutKey(id, section), null) ?: preferences.get(legacyLayoutKey(id), null)
        }

    fun setCatalogLayout(
        profileId: String,
        section: String,
        value: String,
    ) = preferences.put(layoutKey(profileId, section), value)

    private fun layoutKey(
        profileId: String,
        section: String,
    ): String = "catalog-layout.$profileId.$section"

    /** Written by builds before the layout was per-section. Read once, never written again. */
    private fun legacyLayoutKey(profileId: String): String = "catalog-layout.$profileId"

    /**
     * Which streaming services this profile says it pays for, and where it is watching from.
     *
     * Kept in its own key rather than as more fields on the profile row: the profile format is
     * positional, so every field appended there widens a row that already carries six, and this is
     * a set that will keep growing as services are added. A per-profile key also means a household
     * where one person has Netflix and another does not gets the right answer each.
     *
     * This is what the user *says*, never a verified entitlement — the app has no way to check and
     * does not try. Region defaults to null, meaning "not stated", which the discovery layer reads
     * as "show everything" rather than guessing a country from the machine's locale.
     */
    fun streamingPreference(profileId: String?): StoredStreamingPreference {
        val raw = profileId?.let { preferences.get(streamingKey(it), null) } ?: return StoredStreamingPreference()
        val parts = raw.split('|')
        // Rows written by an earlier build carry fewer fields; the missing ones default rather than
        // discarding a preference the user took the trouble to set.
        return StoredStreamingPreference(
            region = parts.getOrNull(0)?.takeIf(String::isNotBlank),
            currency = parts.getOrNull(1)?.takeIf(String::isNotBlank),
            subscribedProviderIds =
                parts.getOrNull(2)
                    ?.split(',')
                    ?.filter(String::isNotBlank)
                    ?.toSet()
                    .orEmpty(),
        )
    }

    fun setStreamingPreference(
        profileId: String,
        preference: StoredStreamingPreference,
    ) {
        // Provider ids are our own slugs — lower-case letters, digits and dashes — so they never
        // contain the separators this format splits on and need no encoding.
        preferences.put(
            streamingKey(profileId),
            listOf(
                preference.region.orEmpty(),
                preference.currency.orEmpty(),
                preference.subscribedProviderIds.sorted().joinToString(","),
            ).joinToString("|"),
        )
    }

    private fun streamingKey(profileId: String): String = "streaming-preference.$profileId"

    /**
     * The parental lock, per profile.
     *
     * Per profile rather than per install: a household has one machine and several people, and the
     * parent's own profile is exactly where the lock is wanted while the child's Kids profile
     * removes the content outright.
     *
     * The PIN is stored as salt and hash, never in the clear. It is a weak secret — four digits —
     * but it is often a number the user reuses, and a preferences file is not the place for it.
     */
    fun parentalLock(profileId: String?): StoredParentalLock {
        val raw = profileId?.let { preferences.get(parentalKey(it), null) } ?: return StoredParentalLock()
        val parts = raw.split('|')
        return StoredParentalLock(
            salt = parts.getOrNull(0)?.takeIf(String::isNotBlank),
            hash = parts.getOrNull(1)?.takeIf(String::isNotBlank),
            // Absent in rows written by an earlier build; defaulted on, which is the safer answer
            // for a lock.
            lockAdultCategories = parts.getOrNull(2) != "0",
            lockedCategoryIds =
                parts.getOrNull(3)
                    ?.split(',')
                    ?.filter(String::isNotBlank)
                    // Decoded leniently: an unreadable id costs one locked category, whereas
                    // failing the row would silently unlock every one of them.
                    ?.mapNotNull { stored -> runCatching { decode(stored) }.getOrNull() }
                    ?.toSet()
                    .orEmpty(),
        )
    }

    fun setParentalLock(
        profileId: String,
        lock: StoredParentalLock,
    ) {
        // Category ids come from the provider and can contain anything, so they are encoded — an
        // id with a comma in it would otherwise split into two and lock the wrong thing.
        preferences.put(
            parentalKey(profileId),
            listOf(
                lock.salt.orEmpty(),
                lock.hash.orEmpty(),
                if (lock.lockAdultCategories) "1" else "0",
                lock.lockedCategoryIds.sorted().joinToString(",", transform = ::encode),
            ).joinToString("|"),
        )
    }

    private fun parentalKey(profileId: String): String = "parental-lock.$profileId"

    /**
     * Categories this profile has chosen to hide.
     *
     * Separate from the parental lock, and deliberately so: hiding is tidying — a rail of sports
     * channels somebody never watches — while locking is protection. Conflating them would mean
     * tidying up required a PIN.
     */
    fun hiddenCategories(profileId: String?): Set<String> =
        profileId
            ?.let { preferences.get(hiddenKey(it), "") }
            ?.split(',')
            ?.filter(String::isNotBlank)
            // Encoded on write because a provider's category id can contain anything, including
            // this format's own comma.
            ?.mapNotNull { stored -> runCatching { decode(stored) }.getOrNull() }
            ?.toSet()
            .orEmpty()

    fun setHiddenCategories(
        profileId: String,
        ids: Set<String>,
    ) = preferences.put(hiddenKey(profileId), ids.sorted().joinToString(",", transform = ::encode))

    private fun hiddenKey(profileId: String): String = "hidden-categories.$profileId"

    /**
     * Whether the clock shows a 24-hour time.
     *
     * Per install rather than per profile: it is about the person reading the screen, and everyone
     * in a household reads the same clock. Defaults to 24-hour, which is what the app's primary
     * locale uses.
     */
    /**
     * How subtitles are drawn. Per install: it is about eyesight and screen, not about the profile.
     *
     * Stored as three fields rather than an object so an unknown value written by a later build
     * degrades to the default instead of discarding the whole setting.
     */
    fun subtitleStyle(): Triple<String, String, Boolean> =
        Triple(
            preferences.get(KEY_SUBTITLE_SIZE, "MEDIUM"),
            preferences.get(KEY_SUBTITLE_COLOUR, "WHITE"),
            preferences.getBoolean(KEY_SUBTITLE_BACKGROUND, true),
        )

    fun setSubtitleStyle(
        size: String,
        colour: String,
        background: Boolean,
    ) {
        preferences.put(KEY_SUBTITLE_SIZE, size)
        preferences.put(KEY_SUBTITLE_COLOUR, colour)
        preferences.putBoolean(KEY_SUBTITLE_BACKGROUND, background)
    }

    fun uses24HourClock(): Boolean = preferences.getBoolean(KEY_CLOCK_24H, true)

    fun setUses24HourClock(value: Boolean) = preferences.putBoolean(KEY_CLOCK_24H, value)

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
     * A profile's own TMDb key, when it has one.
     *
     * Null means "use the shared one", which is the default and the common case: a household
     * normally has one key and no reason for more. A profile sets its own when the shared key has
     * hit TMDb's rate limit, or when someone simply wants their own account's key used.
     *
     * Stored per profile rather than replacing the shared key so that clearing it falls back
     * rather than leaving the profile with no metadata at all.
     */
    fun profileMetadataApiKey(profileId: String?): String? =
        profileId
            ?.let { preferences.get(metadataKeyFor(it), null) }
            ?.takeIf(String::isNotBlank)

    fun setProfileMetadataApiKey(profileId: String, value: String?) {
        val clean = value?.trim().orEmpty()
        if (clean.isBlank()) {
            preferences.remove(metadataKeyFor(profileId))
        } else {
            preferences.put(metadataKeyFor(profileId), clean)
        }
    }

    /**
     * The key this profile should actually use: its own, or the shared one.
     *
     * A single place to ask, so no caller has to remember the precedence — getting that wrong
     * would silently use the wrong account's quota.
     */
    fun effectiveMetadataApiKey(profileId: String?): String? =
        profileMetadataApiKey(profileId) ?: metadataApiKey()

    private fun metadataKeyFor(profileId: String): String = "$KEY_METADATA_KEY.$profileId"

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
        const val KEY_FIRST_STARTUP_DONE = "first-startup-done"
        const val KEY_WINDOW_GEOMETRY = "window-geometry"
        const val KEY_BACKDROP_POSTERS = "backdrop-posters"

        /** Enough for three drifting columns, far short of the 8 KB a preference value allows. */
        const val MAX_BACKDROP_POSTERS = 18
        const val KEY_TERMS_ACCEPTED = "terms-accepted"
        const val KEY_METADATA_KEY = "metadata-api-key"
        const val KEY_LEGACY_FAVORITES = "favorites"
        const val KEY_CLOCK_24H = "clock-24h"
        const val KEY_SUBTITLE_SIZE = "subtitle-size"
        const val KEY_SUBTITLE_COLOUR = "subtitle-colour"
        const val KEY_SUBTITLE_BACKGROUND = "subtitle-background"
    }
}
