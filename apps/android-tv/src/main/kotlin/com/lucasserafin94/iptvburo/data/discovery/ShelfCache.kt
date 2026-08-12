package com.lucasserafin94.iptvburo.data.discovery

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lucasserafin94.iptvburo.metadata.TmdbDiscoverKind
import com.lucasserafin94.iptvburo.metadata.TmdbServiceShelf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/**
 * Storage for the day's shelves.
 *
 * An interface for the same reason the preference stores are: the ViewModel's tests run on a plain
 * JVM where DataStore cannot start, and [NoShelfCache] gives them a cache that is always a miss.
 */
interface ShelfCache {
    /** Today's shelves for this region and kind, or null when today's have not been stored. */
    suspend fun read(
        region: String,
        kind: TmdbDiscoverKind,
    ): List<TmdbServiceShelf>?

    suspend fun write(
        region: String,
        kind: TmdbDiscoverKind,
        shelves: List<TmdbServiceShelf>,
    )
}

/** Always a miss, never stores. For tests and for any caller with nowhere to write. */
object NoShelfCache : ShelfCache {
    override suspend fun read(
        region: String,
        kind: TmdbDiscoverKind,
    ): List<TmdbServiceShelf>? = null

    override suspend fun write(
        region: String,
        kind: TmdbDiscoverKind,
        shelves: List<TmdbServiceShelf>,
    ) = Unit
}

private val Context.shelfCacheDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "shelf_cache",
)

/**
 * Yesterday's service shelves, kept so today's are fetched once rather than on every visit.
 *
 * TMDb rebuilds these lists daily, so anything finer than a day is wasted: the same request made
 * twice on the same afternoon returns the same catalogue. What the user notices is the difference
 * between a screen that draws instantly from the device and one that waits on the network every
 * time they open it.
 *
 * Keyed by day, region and kind. A viewer switching from films to series, or from BR to another
 * region, is asking a different question and must not be given the previous answer.
 *
 * Cached data only — no key, no credential, nothing that identifies the user. If the stored JSON is
 * unreadable it is treated as absent, because a corrupt cache should cost a network request rather
 * than the screen.
 */
@Singleton
class DataStoreShelfCache
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : ShelfCache {
        private val dataStore = context.shelfCacheDataStore

        // Not a constructor parameter: Hilt injects this class and would need a Clock binding for
        // one, which nothing else in the app requires. The day boundary is the device's own.
        private val clock: Clock = Clock.systemDefaultZone()
        private val gson = Gson()

        /** Today's shelves for [region] and [kind], or null when today's have not been stored. */
        override suspend fun read(
            region: String,
            kind: TmdbDiscoverKind,
        ): List<TmdbServiceShelf>? {
            val stored =
                dataStore.data
                    .catch { error ->
                        if (error is IOException) emit(emptyPreferences()) else throw error
                    }.first()[keyFor(region, kind)] ?: return null
            // A parse failure means a version wrote a shape this one cannot read. Falling back to
            // the network is right; failing the screen is not.
            return runCatching {
                gson.fromJson<List<TmdbServiceShelf>>(
                    stored,
                    object : TypeToken<List<TmdbServiceShelf>>() {}.type,
                )
            }.getOrNull()?.takeIf(List<TmdbServiceShelf>::isNotEmpty)
        }

        /**
         * Stores [shelves] as today's answer.
         *
         * An empty list is not stored: "nothing came back" is usually a network failure, and
         * caching it would leave the screen empty for the rest of the day with no way to retry.
         */
        override suspend fun write(
            region: String,
            kind: TmdbDiscoverKind,
            shelves: List<TmdbServiceShelf>,
        ) {
            if (shelves.isEmpty()) return
            val encoded = runCatching { gson.toJson(shelves) }.getOrNull() ?: return
            dataStore.edit { stored ->
                // Yesterday's entries are dropped rather than accumulating a key per day forever.
                val today = keyFor(region, kind).name
                stored.asMap().keys
                    .map(Preferences.Key<*>::name)
                    .filter { name -> name.startsWith(KEY_PREFIX) && name != today }
                    .forEach { name -> stored.remove(stringPreferencesKey(name)) }
                stored[keyFor(region, kind)] = encoded
            }
        }

        private fun keyFor(
            region: String,
            kind: TmdbDiscoverKind,
        ): Preferences.Key<String> =
            stringPreferencesKey(
                "$KEY_PREFIX${LocalDate.now(clock)}:$region:${kind.name}",
            )

        private companion object {
            const val KEY_PREFIX = "shelves:"
        }
    }
