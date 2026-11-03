package com.lucasserafin94.iptvburo.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.webServiceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "web_services",
)

/**
 * Which web services (GoDorama) each profile turned on, and whose sign-in the browser view holds.
 *
 * Nothing about the account itself is stored here — no username, no password, no token. The user
 * signs in on the service's own page and the browser view keeps that session in its cookies.
 *
 * DataStore rather than Room: these are a few switches per profile, and keeping them out of the
 * catalogue database means no schema change for a feature that stores no catalogue.
 */
@Singleton
class WebServicePreferences
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : WebServiceSettings {
        private val dataStore = context.webServiceDataStore

        private val stored: Flow<Preferences> =
            dataStore.data.catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }

        override fun enabledIds(profileId: String): Flow<Set<String>> =
            stored.map { preferences -> preferences[enabledKey(profileId)].orEmpty() }

        override suspend fun setEnabled(
            profileId: String,
            serviceId: String,
            enabled: Boolean,
        ) {
            dataStore.edit { preferences ->
                val key = enabledKey(profileId)
                val current = preferences[key].orEmpty()
                preferences[key] = if (enabled) current + serviceId else current - serviceId
            }
        }

        override suspend fun claimSession(profileId: String): Boolean {
            var changed = false
            dataStore.edit { preferences ->
                val owner = preferences[SESSION_OWNER]
                changed = owner != null && owner != profileId
                preferences[SESSION_OWNER] = profileId
            }
            return changed
        }

        override suspend fun releaseSession() {
            dataStore.edit { preferences -> preferences.remove(SESSION_OWNER) }
        }

        private fun enabledKey(profileId: String) = stringSetPreferencesKey("enabled.$profileId")

        private companion object {
            val SESSION_OWNER = stringPreferencesKey("session_owner")
        }
    }
