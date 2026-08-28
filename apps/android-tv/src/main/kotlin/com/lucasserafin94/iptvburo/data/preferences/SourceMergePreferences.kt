package com.lucasserafin94.iptvburo.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.sourceMergeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "source-merge",
)

/**
 * Whether every configured subscription is browsed as one catalogue.
 *
 * Somebody who buys a second list to fill the gaps in the first ends up switching between them to
 * find which has the film they want — work the app should be doing.
 *
 * Not per profile, matching what it describes: it is how the device loads its lists, and a profile
 * seeing a different library from the one next to it would confuse rather than help.
 */
interface SourceMergeSettings {
    val mergeEverySource: Flow<Boolean>

    suspend fun setMergeEverySource(enabled: Boolean)
}

@Singleton
class SourceMergePreferences
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : SourceMergeSettings {
        /**
         * Off by default.
         *
         * Somebody with a single list gains nothing and would pay for a merge over their whole
         * catalogue, and somebody with two who has not asked for this should not find their library
         * silently rearranged.
         */
        override val mergeEverySource: Flow<Boolean> =
            context.sourceMergeDataStore.data
                // A corrupt or unreadable store must not stop the app starting: it degrades to the
                // default, which is the behaviour every install had before this existed.
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { preferences -> preferences[MERGE_EVERY_SOURCE] ?: false }

        override suspend fun setMergeEverySource(enabled: Boolean) {
            context.sourceMergeDataStore.edit { preferences ->
                preferences[MERGE_EVERY_SOURCE] = enabled
            }
        }

        private companion object {
            val MERGE_EVERY_SOURCE = booleanPreferencesKey("merge-every-source")
        }
    }
