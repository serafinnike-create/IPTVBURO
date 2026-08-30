package com.lucasserafin94.iptvburo.desktop.user

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import com.lucasserafin94.iptvburo.domain.model.AudioOutputMode
import com.lucasserafin94.iptvburo.domain.model.CacheBudget
import com.lucasserafin94.iptvburo.domain.model.ReminderPolicy
import com.lucasserafin94.iptvburo.domain.model.SeriesWatermark
import com.lucasserafin94.iptvburo.domain.model.ParentalPin
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
    /**
     * Whether this is the PIN everybody ships with rather than one somebody chose.
     *
     * Adult categories are locked from the first launch, because a lock nobody has switched on is
     * a lock that protects nobody — and the household this exists for is exactly the one that will
     * never open Settings. But 0000 is public knowledge, so the screen says so and asks for a real
     * one. It protects by default without pretending to be a secret.
     */
    val usingDefaultPin: Boolean = false,
) {
    val hasPin: Boolean
        get() = !salt.isNullOrBlank() && !hash.isNullOrBlank()
}

/**
 * The PIN a profile has until somebody sets their own.
 *
 * Four zeroes because it has to be memorable enough to be told over the telephone by whoever sold
 * the subscription — the same support conversation the device code exists for.
 */
const val DEFAULT_PARENTAL_PIN = "0000"

/**
 * The salt the shipped PIN is hashed with.
 *
 * Fixed rather than random, because this hash is computed on every read instead of being stored,
 * and a fresh salt each time would produce a different hash each time. It is not protecting
 * anything: the PIN it hashes is printed in the app's own settings screen. A chosen PIN gets a
 * random salt, which is where the salt actually matters.
 */
private const val DEFAULT_PARENTAL_SALT = "iptvburo-default-parental-pin"

enum class DesktopLanguage(val tag: String) {
    PORTUGUESE_BRAZIL("pt-BR"),
    ENGLISH("en"),

    /**
     * Neutral Spanish rather than a regional variant.
     *
     * The app sells across Latin America and Spain, and picking one country's vocabulary makes it
     * read as foreign everywhere else. Where the two diverge — "ordenador" against "computadora",
     * "móvil" against "celular" — the wording avoids the choice rather than taking a side.
     */
    SPANISH("es"),
    GERMAN("de"),
    ITALIAN("it"),
    ;

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

/**
 * A title the viewer asked to be reminded about, as it is written to disk.
 *
 * Deliberately not the domain's `Reminder`: that one carries an artwork URL and a release date the
 * desktop has nowhere to get yet, and storing fields no writer fills would suggest they mean
 * something. [title] is display text, kept because [identityKey] is a slug that cannot be read back
 * into a name.
 */
data class StoredReminder(
    val identityKey: String,
    val title: String,
    val year: Int? = null,
    /**
     * The film's own poster, when one was known at the moment it was marked.
     *
     * Null is ordinary and must draw as a designed fallback rather than a broken image: an upcoming
     * title often has no artwork yet, and a provider poster may simply be missing.
     *
     * Safe to write to disk: a provider artwork URL has already been through
     * `sanitizeArtworkUrl`, which rejects any URL carrying credentials — userinfo, a query string,
     * or the username or password as a path segment. A TMDb poster is a public image address.
     */
    val artworkUrl: String? = null,
) {
    /**
     * Whether the stored title is really just the identity key.
     *
     * The first version of this stored no title, so those records fall back to showing the slug —
     * `movie:enola-holmes-3:2026` where a name belongs. The screen uses this to know it should look
     * for a better name rather than printing that at the user.
     */
    val titleIsPlaceholder: Boolean get() = title == identityKey
}

/**
 * One notice as it is written to disk.
 *
 * Deliberately not the domain's `AppNotification`: the kind is a string here rather than an enum,
 * so a record written by a newer build that knows a kind this one does not can be read back and
 * kept instead of throwing. The screen decides what to do with a kind it cannot draw.
 */
data class StoredNotification(
    val id: String,
    val kind: String,
    val read: Boolean,
    val createdAt: Long,
    val title: String,
    val body: String? = null,
)

/** Where this app's user preferences live under the user root. */
private const val USER_NODE_PATH = "com/lucasserafin94/iptvburo/user-v1"

class DesktopUserStore(
    initialPreferences: Preferences = Preferences.userRoot().node(USER_NODE_PATH),
) {
    /**
     * The preferences node, re-acquired after a reset.
     *
     * Not a `val`, and that is the whole point. `resetAll` removes this node, and
     * [Preferences.removeNode] invalidates the *object* permanently: every later call on it throws
     * `IllegalStateException("Node has been removed.")`. Holding one instance for the lifetime of
     * the store meant that after a reset, every read and write in the process threw — profiles,
     * language, favourites, window geometry. Adding a list straight after a reset therefore died at
     * the end of the splash, in a raw AWT error dialog, which is exactly what was reported.
     *
     * A removed node is replaced here rather than reused, so the store survives its own reset.
     */
    private var preferencesNode: Preferences = initialPreferences

    /**
     * Where the node is re-created from after a reset.
     *
     * Captured from whatever was injected, so a test using a temporary node re-creates *that* node
     * rather than reaching into the real user preferences of the machine running the suite.
     */
    private val nodePath: String = initialPreferences.absolutePath()

    private val preferences: Preferences
        get() {
            val current = preferencesNode
            // `nodeExists("")` asks the node about itself, and is documented to throw exactly when
            // the node has been removed — which is the condition being recovered from.
            val stillValid = runCatching { current.nodeExists("") }.getOrDefault(false)
            if (stillValid) return current
            val replacement = Preferences.userRoot().node(nodePath.removePrefix("/"))
            preferencesNode = replacement
            return replacement
        }

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
     * Whether this machine listens for a phone as soon as the app opens.
     *
     * On by default, at the owner's request: the pairing code is what actually guards the feature,
     * and a receiver that has to be switched on by hand every session is one that is never on when
     * somebody reaches for their phone.
     *
     * Per install rather than per profile, matching the thing it controls: one machine opens one
     * socket, whoever happens to be signed in.
     */
    /**
     * Whether every configured subscription is browsed as one catalogue.
     *
     * Off by default. Somebody with a single list gains nothing and would pay for a merge pass over
     * their whole catalogue, and somebody with two who has not asked for this should not find their
     * library silently rearranged.
     *
     * Per install rather than per profile: it describes how the machine loads its lists, and a
     * profile that saw a different library from the one next to it would be confusing rather than
     * useful.
     */
    /**
     * Whether the banner's trailer plays with sound.
     *
     * Off by default, and not merely because a trailer starts muted: an app that makes noise the
     * moment it opens is one somebody turns down at the wall. Remembered, so the choice survives
     * the next launch either way.
     */
    fun bannerTrailerSound(): Boolean = preferences.getBoolean(KEY_BANNER_TRAILER_SOUND, false)

    fun setBannerTrailerSound(enabled: Boolean) {
        preferences.putBoolean(KEY_BANNER_TRAILER_SOUND, enabled)
    }

    fun mergeAllSources(): Boolean = preferences.getBoolean(KEY_MERGE_SOURCES, false)

    fun setMergeAllSources(enabled: Boolean) {
        preferences.putBoolean(KEY_MERGE_SOURCES, enabled)
    }

    fun castReceiverAutoStart(): Boolean = preferences.getBoolean(KEY_CAST_AUTO_START, true)

    fun setCastReceiverAutoStart(enabled: Boolean) {
        preferences.putBoolean(KEY_CAST_AUTO_START, enabled)
    }

    /**
     * This machine's pairing code, kept between sessions.
     *
     * It used to be regenerated on every start, which meant the code on screen was new each time
     * the app opened and the phone had to be told it again — every session, for a feature whose
     * whole appeal is not having to think about it. Kept, so it is typed once and the sender can
     * remember it; [clearCastPairingCode] is what makes a new one when the user wants one.
     *
     * Still four digits and still checked on every message. What it stops is a stranger on a shared
     * network reaching this screen, and it goes on stopping that whether or not it changes daily —
     * a code that rotates is only stronger against someone who saw it once and came back later,
     * which is a far smaller risk than the user simply switching the feature off in irritation.
     */
    fun castPairingCode(): String? =
        preferences.get(KEY_CAST_PAIRING_CODE, "").takeIf(String::isNotBlank)

    fun setCastPairingCode(code: String) = preferences.put(KEY_CAST_PAIRING_CODE, code)

    fun clearCastPairingCode() = preferences.remove(KEY_CAST_PAIRING_CODE)

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
    /**
     * The lock a profile has before anybody configures one: adult categories closed behind 0000.
     *
     * Built rather than stored, so an install that predates this gets it too — the alternative is
     * writing a PIN into everybody's preferences on upgrade, which is a migration for something
     * that can simply be computed.
     */
    private fun defaultParentalLock(): StoredParentalLock {
        val pin = ParentalPin.of(DEFAULT_PARENTAL_PIN, DEFAULT_PARENTAL_SALT) ?: return StoredParentalLock()
        return StoredParentalLock(
            salt = pin.salt,
            hash = pin.hash,
            lockAdultCategories = true,
            usingDefaultPin = true,
        )
    }

    fun parentalLock(profileId: String?): StoredParentalLock {
        // Nothing stored means nobody has chosen a PIN, and the shipped one applies. Answered here
        // rather than at each caller so every screen and every filter sees the same lock: one that
        // is off until Settings is opened protects only the households that did not need it.
        val raw =
            profileId?.let { preferences.get(parentalKey(it), null) }
                ?: return defaultParentalLock()
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

    /**
     * Whether the catalogue shows one card per film instead of every copy a provider carries.
     *
     * On by default. A list routinely holds the same film three or four times over — one per
     * quality, per dubbing, per channel prefix — and the catalogue showed all of them, which was
     * reported as duplicate films. Those copies are a choice of quality in principle, but four cards
     * with nothing to tell them apart is not a choice anybody can make, so the tidy view is the
     * default and this setting is for whoever wants the raw list back.
     */
    fun collapsesDuplicateTitles(): Boolean = preferences.getBoolean(KEY_COLLAPSE_DUPLICATES, true)

    fun setCollapsesDuplicateTitles(value: Boolean) =
        preferences.putBoolean(KEY_COLLAPSE_DUPLICATES, value)

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
    /**
     * The activation key this installation redeemed, so it can be shown back to its owner.
     *
     * Kept because losing it costs money: the key is bound to this device and buying another is the
     * only alternative to asking support. It is stored, deliberately, in the same preferences as
     * everything else rather than encrypted — it unlocks nothing on its own, since redeeming it
     * requires a signature from the private key in the DPAPI blob, and a key nobody can read back
     * is a key the customer has already lost.
     */
    /**
     * Speaker layout for this profile, or the system default.
     *
     * Per profile rather than global: one household member may watch on headphones and another on a
     * 5.1 set, and a shared setting would make each of them change it back.
     *
     * An unknown stored value falls back to the system default rather than failing. A preference
     * written by a newer build must never stop an older one from playing anything.
     */
    fun audioOutput(profileId: String?): AudioOutputMode {
        val stored = profileId?.let { preferences.get(audioOutputKey(it), null) } ?: return AudioOutputMode.SYSTEM
        return runCatching { AudioOutputMode.valueOf(stored) }.getOrDefault(AudioOutputMode.SYSTEM)
    }

    fun setAudioOutput(profileId: String, mode: AudioOutputMode) {
        preferences.put(audioOutputKey(profileId), mode.name)
    }

    private fun audioOutputKey(profileId: String): String = "$KEY_AUDIO_OUTPUT.$profileId"

    fun activationKey(): String? = preferences.get(KEY_ACTIVATION_KEY, null)?.takeIf(String::isNotBlank)

    fun setActivationKey(value: String?) {
        val clean = value?.trim().orEmpty()
        if (clean.isBlank()) preferences.remove(KEY_ACTIVATION_KEY) else preferences.put(KEY_ACTIVATION_KEY, clean)
    }

    fun metadataApiKey(): String? = preferences.get(KEY_METADATA_KEY, null)?.takeIf(String::isNotBlank)

    fun setMetadataApiKey(value: String?) {
        val clean = value?.trim().orEmpty()
        if (clean.isBlank()) preferences.remove(KEY_METADATA_KEY) else preferences.put(KEY_METADATA_KEY, clean)
    }

    /**
     * The OMDb key, which is what the critics' scores are fetched with.
     *
     * A second key rather than a second use of the TMDb one: they are different services with
     * different accounts. Absent by default, and absent means the details screen shows TMDb's
     * audience score alone — which is what it showed before this existed.
     */
    fun criticScoresApiKey(): String? = preferences.get(KEY_CRITIC_KEY, null)?.takeIf(String::isNotBlank)

    /**
     * The ThePornDB key, which is what covers a catalogue TMDb deliberately excludes.
     *
     * TMDb answers nothing for these titles — the app asks it not to, and TMDb's own guidance is
     * that applications should not fetch them — so an adult category arrives with no artwork at
     * all. This is the only way to fill it, and it is the viewer's own account: there is no key
     * shipped with the app, because a key inside an installer is a published key, and the account
     * suspended when somebody abuses it would be the one that issued it.
     *
     * Absent by default, which leaves those rows drawn with the title fallback exactly as now.
     */
    fun adultMetadataApiKey(): String? = preferences.get(KEY_ADULT_KEY, null)?.takeIf(String::isNotBlank)

    fun setAdultMetadataApiKey(value: String?) {
        val clean = value?.trim().orEmpty()
        if (clean.isBlank()) preferences.remove(KEY_ADULT_KEY) else preferences.put(KEY_ADULT_KEY, clean)
    }

    fun setCriticScoresApiKey(value: String?) {
        val clean = value?.trim().orEmpty()
        if (clean.isBlank()) preferences.remove(KEY_CRITIC_KEY) else preferences.put(KEY_CRITIC_KEY, clean)
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
        val removed = preferences
        removed.removeNode()
        // Legal on a removed node — `flush` is one of the few methods the contract still allows —
        // and it is what commits the removal to disk.
        removed.flush()
        // The next read re-acquires. Without this the store would keep handing out an object that
        // throws on every call for the rest of the process.
        preferencesNode = Preferences.userRoot().node(nodePath.removePrefix("/"))
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

    /**
     * The titles this profile asked to be reminded about.
     *
     * Per profile like favourites, and for the same reason: a reminder is one person's interest in
     * a title, not a property of the machine. Identity keys rather than row ids so a replaced
     * playlist does not silently move a reminder onto an unrelated film — see [favoritesKey].
     *
     * The display title is stored alongside the key rather than being looked up. An identity key is
     * a slug — `movie:festival-astroworld:2025` — which cannot be turned back into the name anyone
     * would recognise, and the reminders that need naming most are for films that are *not in the
     * catalogue yet*, so there is nothing to look the title up in.
     */
    fun remindersForProfile(profileId: String?): List<StoredReminder> =
        profileId
            ?.let { preferences.get(remindersKey(it), "") }
            ?.split(RECORD_SEPARATOR)
            ?.filter(String::isNotBlank)
            ?.mapNotNull(::decodeReminder)
            .orEmpty()

    fun setReminders(profileId: String, reminders: List<StoredReminder>) =
        preferences.put(
            remindersKey(profileId),
            reminders
                .sortedBy { reminder -> reminder.identityKey }
                .joinToString(RECORD_SEPARATOR.toString(), transform = ::encodeReminder),
        )

    /**
     * One record: key, title and year, with the title encoded so its punctuation cannot break the
     * format. Written as three fields even when the year is unknown, so a reader never has to guess
     * whether a two-field record is missing the year or the title.
     */
    private fun encodeReminder(reminder: StoredReminder): String =
        listOf(
            reminder.identityKey,
            encode(reminder.title),
            reminder.year?.toString().orEmpty(),
            reminder.artworkUrl?.let(::encode).orEmpty(),
        ).joinToString(FIELD_SEPARATOR.toString())

    private fun decodeReminder(raw: String): StoredReminder? {
        val parts = raw.split(FIELD_SEPARATOR)
        val key = parts.firstOrNull()?.takeIf(String::isNotBlank) ?: return null
        // A bare key is what the first version of this wrote, before titles were stored. Those
        // records are kept and shown under the slug rather than discarded: a reminder the user set
        // is theirs, and dropping it silently to tidy up a format would be the worse failure.
        val title =
            parts
                .getOrNull(1)
                ?.takeIf(String::isNotBlank)
                ?.let { stored -> runCatching { decode(stored) }.getOrNull() }
                ?: key
        return StoredReminder(
            identityKey = key,
            title = title,
            year = parts.getOrNull(2)?.toIntOrNull(),
            artworkUrl =
                parts
                    .getOrNull(3)
                    ?.takeIf(String::isNotBlank)
                    ?.let { stored -> runCatching { decode(stored) }.getOrNull() },
        )
    }

    private fun remindersKey(profileId: String): String = "reminders.$profileId"

    /**
     * The bell's contents for a profile, as `id|kind|read|createdAt|title|body` records.
     *
     * Per profile, like everything the bell announces: a reminder belongs to whoever marked it, and
     * one person's news has no business appearing under another's name.
     *
     * Read state is stored rather than derived. Without it every launch would rebuild the digest
     * and mark it unread again, so the badge would come back however many times the viewer had
     * already looked — which is the fastest way to teach somebody to ignore a badge.
     */
    fun notificationsForProfile(profileId: String?): List<StoredNotification> =
        profileId
            ?.let { preferences.get(notificationsKey(it), "") }
            ?.split(RECORD_SEPARATOR)
            ?.filter(String::isNotBlank)
            ?.mapNotNull(::decodeNotification)
            .orEmpty()

    fun setNotifications(profileId: String, notifications: List<StoredNotification>) =
        preferences.put(
            notificationsKey(profileId),
            notifications.joinToString(RECORD_SEPARATOR.toString(), transform = ::encodeNotification),
        )

    private fun encodeNotification(notification: StoredNotification): String =
        listOf(
            encode(notification.id),
            notification.kind,
            if (notification.read) "1" else "0",
            notification.createdAt.toString(),
            encode(notification.title),
            notification.body?.let(::encode).orEmpty(),
        ).joinToString(FIELD_SEPARATOR.toString())

    private fun decodeNotification(raw: String): StoredNotification? {
        val parts = raw.split(FIELD_SEPARATOR)
        if (parts.size < 5) return null
        val id = runCatching { decode(parts[0]) }.getOrNull()?.takeIf(String::isNotBlank) ?: return null
        val title = runCatching { decode(parts[4]) }.getOrNull()?.takeIf(String::isNotBlank) ?: return null
        return StoredNotification(
            id = id,
            kind = parts[1].takeIf(String::isNotBlank) ?: return null,
            read = parts[2] == "1",
            createdAt = parts[3].toLongOrNull() ?: 0L,
            title = title,
            body = parts.getOrNull(5)?.takeIf(String::isNotBlank)?.let { stored ->
                runCatching { decode(stored) }.getOrNull()
            },
        )
    }

    private fun notificationsKey(profileId: String): String = "notifications.$profileId"

    /**
     * What each followed series looked like when it was last counted.
     *
     * Providers do not say what changed, so the only way to answer "is there a new episode" is to
     * remember what was there before. Stored as `identity|season|episode|count` records, per
     * profile, because following a series is one person's business.
     */
    fun seriesWatermarks(profileId: String?): Map<String, SeriesWatermark> =
        profileId
            ?.let { preferences.get(watermarksKey(it), "") }
            ?.split(RECORD_SEPARATOR)
            ?.filter(String::isNotBlank)
            ?.mapNotNull(::decodeWatermark)
            ?.associateBy { mark -> mark.identityKey }
            .orEmpty()

    fun setSeriesWatermarks(profileId: String, marks: Collection<SeriesWatermark>) =
        preferences.put(
            watermarksKey(profileId),
            marks.joinToString(RECORD_SEPARATOR.toString()) { mark ->
                listOf(
                    encode(mark.identityKey),
                    mark.latestSeason.toString(),
                    mark.latestEpisode.toString(),
                    mark.episodeCount.toString(),
                ).joinToString(FIELD_SEPARATOR.toString())
            },
        )

    private fun decodeWatermark(raw: String): SeriesWatermark? {
        val parts = raw.split(FIELD_SEPARATOR)
        if (parts.size < 4) return null
        val key =
            runCatching { decode(parts[0]) }.getOrNull()?.takeIf(String::isNotBlank) ?: return null
        return SeriesWatermark(
            identityKey = key,
            latestSeason = parts[1].toIntOrNull() ?: return null,
            latestEpisode = parts[2].toIntOrNull() ?: return null,
            episodeCount = parts[3].toIntOrNull() ?: return null,
        )
    }

    private fun watermarksKey(profileId: String): String = "series-watermarks.$profileId"

    /**
     * Titles this profile has already decided about in Descobrir.
     *
     * Per profile, because a swipe is one person saying what they think of a film. Kept so a card
     * somebody dismissed never comes back — the rule people notice immediately when it breaks,
     * since a returning card reads as the app ignoring them.
     */
    fun discoverySeen(profileId: String?): Set<String> =
        profileId
            ?.let { preferences.get(discoverySeenKey(it), "") }
            ?.split(RECORD_SEPARATOR)
            ?.filter(String::isNotBlank)
            ?.mapNotNull { stored -> runCatching { decode(stored) }.getOrNull() }
            ?.toSet()
            .orEmpty()

    fun setDiscoverySeen(profileId: String, ids: Collection<String>) =
        preferences.put(
            discoverySeenKey(profileId),
            // Bounded, and the newest kept: preferences cap a value at 8 KB, and a viewer who has
            // swiped through thousands would otherwise silently lose the whole list to a failed
            // write. The oldest decisions are the ones least likely to be offered again anyway.
            ids.toList()
                .takeLast(MAX_DISCOVERY_SEEN)
                .joinToString(RECORD_SEPARATOR.toString(), transform = ::encode),
        )

    private fun discoverySeenKey(profileId: String): String = "discovery-seen.$profileId"

    /**
     * How many gigabytes of artwork this machine may keep, 0–64.
     *
     * Per install rather than per profile: it is a claim on one disk, and two people sharing a
     * computer share the drive whether or not they share a profile.
     *
     * Absent means the viewer has not been asked yet, which the first-run screen uses to decide
     * whether to offer the choice — distinct from having been asked and answered zero.
     */
    fun cacheBudgetGigabytes(): Int? =
        preferences.getInt(KEY_CACHE_BUDGET_GB, -1).takeIf { value -> value >= 0 }

    fun setCacheBudgetGigabytes(gigabytes: Int) =
        preferences.putInt(KEY_CACHE_BUDGET_GB, gigabytes.coerceIn(0, CacheBudget.MAX_GIGABYTES))

    /**
     * The hour of day the viewer wants their reminder digest, 0–23.
     *
     * Not per profile: this is when *this machine* is allowed to interrupt whoever is using it, in
     * the way a notification setting belongs to the device rather than to an account on it.
     *
     * Defaults to [ReminderPolicy.DEFAULT_HOUR] rather than to a stored zero, which would otherwise
     * mean a fresh install announced everything at midnight.
     */
    fun reminderHour(): Int =
        preferences
            .getInt(KEY_REMINDER_HOUR, ReminderPolicy.DEFAULT_HOUR)
            .takeIf { hour -> hour in 0..23 }
            ?: ReminderPolicy.DEFAULT_HOUR

    fun setReminderHour(hour: Int) =
        preferences.putInt(KEY_REMINDER_HOUR, hour.coerceIn(0, 23))

    /** Whether the in-app reminder notice is wanted at all. On by default, and switchable off. */
    fun remindersAnnounced(): Boolean = preferences.getBoolean(KEY_REMINDERS_ANNOUNCED, true)

    fun setRemindersAnnounced(announced: Boolean) =
        preferences.putBoolean(KEY_REMINDERS_ANNOUNCED, announced)

    /**
     * The day the digest was last shown, as an ISO date, or null when it never has been.
     *
     * Stored so the notice appears once a day rather than on every navigation back to the home
     * screen — a banner that reappears all day is one the user learns to dismiss without reading.
     */
    fun reminderLastShownOn(): String? =
        preferences.get(KEY_REMINDER_LAST_SHOWN, "").takeIf(String::isNotBlank)

    fun setReminderLastShownOn(isoDate: String) =
        preferences.put(KEY_REMINDER_LAST_SHOWN, isoDate)

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
        const val KEY_CAST_AUTO_START = "cast-receiver-auto-start"
        const val KEY_MERGE_SOURCES = "merge-all-sources"
        const val KEY_BANNER_TRAILER_SOUND = "banner-trailer-sound"
        const val KEY_CAST_PAIRING_CODE = "cast-pairing-code"
        const val KEY_CACHE_BUDGET_GB = "cache-budget-gb"

        /** Room for a long history of swipes without risking the 8 KB ceiling on one value. */
        const val MAX_DISCOVERY_SEEN = 400
        const val KEY_WINDOW_GEOMETRY = "window-geometry"
        const val KEY_BACKDROP_POSTERS = "backdrop-posters"

        /** Enough for three drifting columns, far short of the 8 KB a preference value allows. */
        const val MAX_BACKDROP_POSTERS = 18
        const val KEY_TERMS_ACCEPTED = "terms-accepted"
        const val KEY_METADATA_KEY = "metadata-api-key"
        const val KEY_CRITIC_KEY = "critic-scores-api-key"
        const val KEY_ADULT_KEY = "adult-metadata-api-key"
        const val KEY_ACTIVATION_KEY = "activation-key"
        const val KEY_AUDIO_OUTPUT = "audio-output"
        const val KEY_LEGACY_FAVORITES = "favorites"

        /**
         * Separators for the reminder list.
         *
         * Neither can occur in the data: an identity key is a slug of `[a-z0-9-]` plus colons, and
         * the title is Base64. A comma would have been ambiguous the moment a title contained one.
         */
        const val RECORD_SEPARATOR = ';'
        const val FIELD_SEPARATOR = '|'
        const val KEY_REMINDER_HOUR = "reminder-hour"
        const val KEY_REMINDERS_ANNOUNCED = "reminders-announced"
        const val KEY_REMINDER_LAST_SHOWN = "reminder-last-shown"
        const val KEY_CLOCK_24H = "clock-24h"
        const val KEY_COLLAPSE_DUPLICATES = "collapse-duplicate-titles"
        const val KEY_SUBTITLE_SIZE = "subtitle-size"
        const val KEY_SUBTITLE_COLOUR = "subtitle-colour"
        const val KEY_SUBTITLE_BACKGROUND = "subtitle-background"
    }
}
