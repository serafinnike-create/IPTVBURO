package com.lucasserafin94.iptvburo.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lucasserafin94.iptvburo.domain.model.SubtitlePresentation
import com.lucasserafin94.iptvburo.domain.model.SubtitleTextColour
import com.lucasserafin94.iptvburo.domain.model.SubtitleTextSize
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.subtitleDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "subtitles",
)

/**
 * The viewer's subtitle appearance, kept across sessions.
 *
 * Stored by the stable ids on [SubtitleTextSize] and [SubtitleTextColour] rather than by ordinal:
 * reordering the enum would otherwise turn somebody's yellow subtitles grey.
 *
 * Household-wide rather than per profile, matching Windows. Subtitle size is mostly about the
 * screen and how far away it is, which does not change when a different person picks up the remote.
 */
@Singleton
class SubtitlePreferences
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : SubtitleSettings {
        private val dataStore = context.subtitleDataStore

        override val presentation: Flow<SubtitlePresentation> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) emit(emptyPreferences()) else throw error
                }.map { stored ->
                    SubtitlePresentation(
                        size = SubtitleTextSize.fromId(stored[SIZE]),
                        colour = SubtitleTextColour.fromId(stored[COLOUR]),
                        background = stored[BACKGROUND] ?: true,
                    )
                }

        override suspend fun save(presentation: SubtitlePresentation) {
            dataStore.edit { stored ->
                stored[SIZE] = presentation.size.id
                stored[COLOUR] = presentation.colour.id
                stored[BACKGROUND] = presentation.background
            }
        }

        private companion object {
            val SIZE = stringPreferencesKey("subtitle_size")
            val COLOUR = stringPreferencesKey("subtitle_colour")
            val BACKGROUND = booleanPreferencesKey("subtitle_background")
        }
    }
