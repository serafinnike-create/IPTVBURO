package com.lucasserafin94.iptvburo.data.sync

import android.content.Context
import com.lucasserafin94.iptvburo.data.licensing.AndroidDeviceIdentityProvider
import com.lucasserafin94.iptvburo.data.licensing.AndroidDeviceProofAction
import com.lucasserafin94.iptvburo.data.licensing.AndroidLicenseEndpoints
import com.lucasserafin94.iptvburo.data.repository.CatalogRepository
import com.lucasserafin94.iptvburo.data.repository.UserLibraryRepository
import com.lucasserafin94.iptvburo.di.IoDispatcher
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.Channel
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.ContentKind
import com.lucasserafin94.iptvburo.sync.FavoriteSync
import com.lucasserafin94.iptvburo.sync.FavoriteSyncSession
import com.lucasserafin94.iptvburo.sync.SyncClient
import com.lucasserafin94.iptvburo.sync.SyncInvite
import com.lucasserafin94.iptvburo.sync.SyncProver
import com.lucasserafin94.iptvburo.sync.SyncResult
import com.lucasserafin94.iptvburo.sync.SyncStateStore
import com.lucasserafin94.iptvburo.sync.SyncStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Keeps this device's favourites in step with the household's other devices — the phone, the
 * television, the computer.
 *
 * The rules are the shared [FavoriteSyncSession]'s. What is Android's own is the key (Keystore,
 * under the `sync` proof action) and the translation between a favourite here, which is a row in
 * the imported catalogue, and a favourite in the household, which is the title's identity. A
 * favourite that arrives for a title this playlist does not carry is not lost: the ledger keeps it,
 * and it is simply not shown until a list that has it is imported.
 */
@Singleton
class AndroidFavoriteSync
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val library: UserLibraryRepository,
        private val catalog: CatalogRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        private val session by lazy {
            FavoriteSyncSession(
                client =
                    SyncClient(
                        http =
                            OkHttpClient.Builder()
                                .connectTimeout(10, TimeUnit.SECONDS)
                                .readTimeout(20, TimeUnit.SECONDS)
                                .build(),
                        baseUrl = "https://${AndroidLicenseEndpoints.DOMAIN}",
                        prover = KeystoreSyncProver(context),
                    ),
                store = SharedPreferencesSyncStore(context),
            )
        }

        /** One exchange at a time: the worker and a button press must not push the same edit twice. */
        private val lock = Mutex()

        val isMember: Boolean get() = session.isMember

        suspend fun status(): SyncResult<SyncStatus> = withContext(ioDispatcher) { session.status() }

        suspend fun invite(): SyncResult<SyncInvite> = withContext(ioDispatcher) { session.invite() }

        suspend fun join(code: String): SyncResult<Int> = withContext(ioDispatcher) { session.join(code) }

        suspend fun leave(): SyncResult<Unit> = withContext(ioDispatcher) { session.leave() }

        /**
         * Every profile's favourites up, the household's down, applied here.
         *
         * Returns how many favourites arrived from the other devices. Profiles are matched by name,
         * since a profile id means nothing on another device.
         */
        suspend fun exchange(): SyncResult<Int> =
            withContext(ioDispatcher) {
                if (!session.isMember) return@withContext SyncResult.Success(0)
                lock.withLock {
                    val profiles = library.observeProfiles().first()
                    val current =
                        profiles.flatMap { profile ->
                            library.loadFavorites(profile.id, limit = MAX_FAVORITES).mapNotNull { channel ->
                                syncFavoriteOf(profile.name, channel)
                            }
                        }
                    val outcome =
                        when (val result = session.exchange(current, profiles.map { it.name })) {
                            is SyncResult.Success -> result.value
                            is SyncResult.Failure -> return@withLock result
                        }
                    var applied = 0
                    profiles.forEach { profile ->
                        val name = com.lucasserafin94.iptvburo.sync.SyncEntry.normaliseProfile(profile.name)
                        val favourites = library.loadFavorites(profile.id, limit = MAX_FAVORITES)
                        outcome.remove
                            .filter { com.lucasserafin94.iptvburo.sync.SyncEntry.normaliseProfile(it.profile) == name }
                            .forEach { removed ->
                                favourites
                                    .filter { channel -> identityOf(channel)?.key == removed.identityKey }
                                    .forEach { channel -> library.toggleFavorite(profile.id, channel.id, currentlyFavorite = true) }
                            }
                        outcome.add
                            .filter { com.lucasserafin94.iptvburo.sync.SyncEntry.normaliseProfile(it.profile) == name }
                            .forEach { added ->
                                val already = favourites.any { channel -> identityOf(channel)?.key == added.identityKey }
                                if (already) return@forEach
                                findInCatalogue(added)?.let { channel ->
                                    library.toggleFavorite(profile.id, channel.id, currentlyFavorite = false)
                                    applied++
                                }
                            }
                    }
                    SyncResult.Success(applied)
                }
            }

        /**
         * The catalogue row for a favourite that arrived, found by searching its most telling word
         * and keeping only a row whose identity is exactly the same title, kind and year.
         */
        private suspend fun findInCatalogue(favorite: FavoriteSync.Local): Channel? {
            val word = searchWordFor(favorite.title) ?: return null
            return catalog.search(word, limit = SEARCH_LIMIT).firstOrNull { channel ->
                identityOf(channel)?.key == favorite.identityKey
            }
        }

        private companion object {
            /** More favourites than anyone keeps, and a bound on what one exchange reads. */
            const val MAX_FAVORITES = 5_000
            const val SEARCH_LIMIT = 100
        }
    }

/**
 * A catalogue row's identity, derived exactly as the desktop derives it for the same title, so a
 * favourite made on one is recognised on the other. Live channels and unknown rows have none worth
 * sharing.
 */
internal fun identityOf(channel: Channel): ContentIdentity? {
    val kind =
        when (channel.contentType) {
            CatalogContentType.MOVIE -> ContentKind.MOVIE
            CatalogContentType.SERIES -> ContentKind.SERIES
            else -> return null
        }
    return runCatching {
        ContentIdentity.of(kind, channel.name, channel.year ?: ContentIdentity.yearFromTitle(channel.name))
    }.getOrNull()
}

internal fun syncFavoriteOf(
    profileName: String,
    channel: Channel,
): FavoriteSync.Local? {
    val identity = identityOf(channel) ?: return null
    return FavoriteSync.Local(
        profile = profileName,
        identityKey = identity.key,
        title = channel.name,
        year = channel.year ?: ContentIdentity.yearFromTitle(channel.name),
        kind = identity.key.substringBefore(':'),
    )
}

/**
 * The word to search the catalogue by: the longest one, which is the least likely to match half
 * the list. Null when the title has nothing longer than two letters to go on.
 */
internal fun searchWordFor(title: String): String? =
    title.split(' ', '-', '.', ':', '_')
        .map(String::trim)
        .filter { it.length >= 3 && it.any(Char::isLetter) }
        .maxByOrNull(String::length)

private class KeystoreSyncProver(private val context: Context) : SyncProver {
    override val deviceId: String
        get() = AndroidDeviceIdentityProvider.getOrCreate(context).deviceId

    override fun proof(nonce: String): String =
        AndroidDeviceIdentityProvider.getOrCreate(context).proof(AndroidDeviceProofAction.SYNC, nonce)
}

/** Whether this device joined a household, read without building the service. */
fun isFavoriteSyncMember(context: Context): Boolean =
    context.getSharedPreferences(SYNC_PREFERENCES, Context.MODE_PRIVATE).getBoolean(KEY_SYNC_MEMBER, false)

private const val SYNC_PREFERENCES = "buro_sync"
private const val KEY_SYNC_MEMBER = "member"

private class SharedPreferencesSyncStore(context: Context) : SyncStateStore {
    private val preferences = context.getSharedPreferences(SYNC_PREFERENCES, Context.MODE_PRIVATE)

    override var isMember: Boolean
        get() = preferences.getBoolean(KEY_SYNC_MEMBER, false)
        set(value) {
            preferences.edit().putBoolean(KEY_SYNC_MEMBER, value).apply()
        }

    override fun readLedger(): String? = preferences.getString(KEY_LEDGER, null)

    override fun writeLedger(encoded: String) {
        preferences.edit().putString(KEY_LEDGER, encoded).apply()
    }

    private companion object {
        const val KEY_LEDGER = "favorites_ledger"
    }
}
