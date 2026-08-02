package com.lucasserafin94.iptvburo.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "onboarding",
)

@Singleton
class DataStoreOnboardingPreferences @Inject constructor(
    @ApplicationContext context: Context,
) : OnboardingPreferences {
    private val dataStore = context.onboardingDataStore

    override val accepted: Flow<Boolean> =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .map { preferences -> preferences[LEGAL_NOTICE_ACCEPTED] ?: false }

    override val activeProfileId: Flow<String?> =
        dataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }
            .map { preferences -> preferences[ACTIVE_PROFILE_ID] }

    override suspend fun acceptLegalNotice() {
        dataStore.edit { preferences ->
            preferences[LEGAL_NOTICE_ACCEPTED] = true
        }
    }

    override suspend fun selectProfile(profileId: String?) {
        dataStore.edit { preferences ->
            if (profileId == null) {
                preferences.remove(ACTIVE_PROFILE_ID)
            } else {
                preferences[ACTIVE_PROFILE_ID] = profileId
            }
        }
    }

    private companion object {
        val LEGAL_NOTICE_ACCEPTED = booleanPreferencesKey("legal_notice_accepted")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
    }
}
