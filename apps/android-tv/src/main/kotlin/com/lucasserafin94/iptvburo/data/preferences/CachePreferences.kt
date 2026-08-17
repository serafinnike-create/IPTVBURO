package com.lucasserafin94.iptvburo.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lucasserafin94.iptvburo.domain.model.CacheBudget
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.cacheDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "artwork-cache",
)

/**
 * How much artwork this device may keep, and whether the viewer has been asked yet.
 *
 * Not per profile, unlike reminders or the bell: the cache is a property of the device's storage,
 * and two people sharing a tablet share the disk it fills. Asking each of them for their own budget
 * would let one person's answer silently overwrite the other's on the same finite drive.
 */
interface CacheSettingsStore {
    /** The chosen budget, or the default for somebody who has not been asked yet. */
    fun observeBudget(): Flow<CacheBudget>

    /**
     * Whether the first-run offer is still owed.
     *
     * Distinct from "the budget is zero": a viewer who answered zero has made a choice and must not
     * be asked again, while one who has never seen the screen has not.
     */
    fun observeChoicePending(): Flow<Boolean>

    /** Records the viewer's answer, which also settles [observeChoicePending] for good. */
    suspend fun chooseBudget(gigabytes: Int)

    /**
     * Where the last fill got to, so a paused download can still say how far it went.
     *
     * Kept here rather than read back from WorkManager because cancelling a worker discards its
     * published progress: without this the bar would empty the instant somebody pressed Pausar,
     * which reads as the download having been thrown away rather than held.
     */
    fun observeMark(): Flow<CacheFillMark>

    suspend fun rememberMark(done: Int, total: Int)
}

/** The last position a fill reported, kept across pauses and launches. */
data class CacheFillMark(val done: Int = 0, val total: Int = 0)

@Singleton
class CachePreferences
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : CacheSettingsStore {
        private val dataStore = context.cacheDataStore

        override fun observeBudget(): Flow<CacheBudget> =
            stored().map { gigabytes ->
                CacheBudget.ofGigabytes(gigabytes ?: CacheBudget.DEFAULT_GIGABYTES)
            }

        override fun observeChoicePending(): Flow<Boolean> = stored().map { it == null }

        override suspend fun chooseBudget(gigabytes: Int) {
            // Failing quietly, as the other preference stores do: a setting that cannot be written
            // is not worth an error dialogue over an optimisation the app works fine without.
            runCatching {
                dataStore.edit { preferences ->
                    preferences[BUDGET_KEY] = gigabytes.coerceIn(0, CacheBudget.MAX_GIGABYTES)
                }
            }
        }

        override fun observeMark(): Flow<CacheFillMark> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) emit(emptyPreferences()) else throw error
                }.map { preferences ->
                    CacheFillMark(
                        done = preferences[MARK_DONE_KEY] ?: 0,
                        total = preferences[MARK_TOTAL_KEY] ?: 0,
                    )
                }

        override suspend fun rememberMark(done: Int, total: Int) {
            runCatching {
                dataStore.edit { preferences ->
                    preferences[MARK_DONE_KEY] = done
                    preferences[MARK_TOTAL_KEY] = total
                }
            }
        }

        /** Null means never asked — the distinction the first-run offer depends on. */
        private fun stored(): Flow<Int?> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) emit(emptyPreferences()) else throw error
                }.map { preferences -> preferences[BUDGET_KEY] }

        private companion object {
            val BUDGET_KEY = intPreferencesKey("cache-budget-gigabytes")
            val MARK_DONE_KEY = intPreferencesKey("cache-fill-done")
            val MARK_TOTAL_KEY = intPreferencesKey("cache-fill-total")
        }
    }
