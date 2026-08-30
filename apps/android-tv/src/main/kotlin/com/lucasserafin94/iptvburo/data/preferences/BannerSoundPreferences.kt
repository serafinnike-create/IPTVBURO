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

private val Context.bannerSoundDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "banner-sound",
)

/**
 * Whether the home banner's trailer carries sound.
 *
 * Off until somebody asks for it. A television that starts talking on its own the moment it is
 * switched on is worse than a silent one, and the viewer who wants the sound only has to say so
 * once — this is what remembers it.
 *
 * Note what it does *not* control: the trailer always begins muted regardless. No engine autoplays
 * audio, so asking for sound up front means the trailer never starts at all, leaving a play button
 * over a still frame. The sound is raised the moment the player reports itself playing, which is
 * why this reads as a preference rather than an instruction.
 *
 * Household-wide rather than per profile, matching Windows and the subtitle settings beside it:
 * whether the room wants noise from the opening screen is a property of the room.
 */
interface BannerSoundSettings {
    /** Whether the trailer should be unmuted once it is playing. */
    val soundOn: Flow<Boolean>

    /** Flips it, and remembers. */
    suspend fun setSoundOn(enabled: Boolean)
}

@Singleton
class BannerSoundPreferences
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : BannerSoundSettings {
        private val dataStore = context.bannerSoundDataStore

        override val soundOn: Flow<Boolean> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) emit(emptyPreferences()) else throw error
                }.map { stored -> stored[SOUND_ON] ?: false }

        override suspend fun setSoundOn(enabled: Boolean) {
            dataStore.edit { stored -> stored[SOUND_ON] = enabled }
        }

        private companion object {
            val SOUND_ON = booleanPreferencesKey("banner_trailer_sound")
        }
    }
