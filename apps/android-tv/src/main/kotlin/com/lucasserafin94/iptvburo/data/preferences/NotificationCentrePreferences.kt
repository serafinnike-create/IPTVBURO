package com.lucasserafin94.iptvburo.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lucasserafin94.iptvburo.domain.model.AppNotification
import com.lucasserafin94.iptvburo.domain.model.NotificationCentre
import com.lucasserafin94.iptvburo.domain.model.NotificationKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "notification-centre",
)

/**
 * The part of the bell's storage the view model depends on.
 *
 * An interface for the same reason `PlaybackSessionStore` is one: the implementation opens a
 * DataStore, which needs a working Android context, so a plain JVM test asserting what navigation
 * does would otherwise have to stand one up to reach it.
 */
interface NotificationCentreStore {
    fun observe(profileId: String): Flow<NotificationCentre>

    suspend fun add(profileId: String, notification: AppNotification)

    suspend fun markAllRead(profileId: String)

    suspend fun remove(profileId: String, id: String)

    suspend fun clear(profileId: String)
}

/**
 * What the bell is holding, kept across launches.
 *
 * Stored per profile: two people in a household follow different series, and one person's new
 * episode must never appear under the other's bell — the same rule reminders and favourites follow.
 *
 * Written as JSON in a single preference rather than as a Room table. The centre is read and
 * rewritten whole on every change, is capped at [NotificationCentre.MAX_HELD] entries, and has no
 * queries to answer — a table would buy indexing nothing needs and cost a migration.
 */
@Singleton
class NotificationCentrePreferences
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : NotificationCentreStore {
        private val dataStore = context.notificationDataStore

        override fun observe(profileId: String): Flow<NotificationCentre> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) emit(emptyPreferences()) else throw error
                }.map { stored -> stored[keyFor(profileId)].orEmpty().toCentre() }

        suspend fun current(profileId: String): NotificationCentre =
            runCatching { observe(profileId).first() }.getOrDefault(NotificationCentre())

        /**
         * Adds a notice unless the bell already holds its id.
         *
         * The de-duplication is [NotificationCentre.add]'s, not this class's: the same rule has to
         * hold on every platform, and a second copy of it here would be a second place to get it
         * wrong. Trimmed on the way in, so a bell nobody empties cannot grow without end.
         */
        override suspend fun add(profileId: String, notification: AppNotification) {
            update(profileId) { centre -> centre.add(notification).trimmed() }
        }

        override suspend fun markAllRead(profileId: String) {
            update(profileId) { centre -> centre.markAllRead() }
        }

        override suspend fun remove(profileId: String, id: String) {
            update(profileId) { centre -> centre.remove(id) }
        }

        override suspend fun clear(profileId: String) {
            update(profileId) { NotificationCentre() }
        }

        private suspend fun update(
            profileId: String,
            transform: (NotificationCentre) -> NotificationCentre,
        ) {
            // Failing quietly, as the notifier does: the bell is a courtesy, and a store that
            // cannot be written is not worth an error the viewer has to dismiss.
            runCatching {
                dataStore.edit { stored ->
                    val key = keyFor(profileId)
                    stored[key] = transform(stored[key].orEmpty().toCentre()).toJson()
                }
            }
        }

        private fun keyFor(profileId: String) = stringPreferencesKey("centre:$profileId")

        /**
         * Reads a stored centre back, discarding anything it cannot parse.
         *
         * A record written by a newer build may carry a kind this one does not know. Such an entry
         * is dropped rather than thrown on: losing one row of news is a small cost, and an
         * exception here would empty the bell on every read for as long as the row remained.
         */
        private fun String.toCentre(): NotificationCentre {
            if (isBlank()) return NotificationCentre()
            return runCatching {
                val array = JSONArray(this)
                val held =
                    (0 until array.length()).mapNotNull { index ->
                        runCatching {
                            val row = array.getJSONObject(index)
                            AppNotification(
                                id = row.getString("id"),
                                kind = NotificationKind.valueOf(row.getString("kind")),
                                title = row.getString("title"),
                                body = row.optString("body").takeIf(String::isNotBlank),
                                createdAt = row.optLong("createdAt"),
                                read = row.optBoolean("read"),
                            )
                        }.getOrNull()
                    }
                NotificationCentre(held)
            }.getOrDefault(NotificationCentre())
        }

        private fun NotificationCentre.toJson(): String =
            JSONArray().apply {
                notifications.forEach { notification ->
                    put(
                        JSONObject().apply {
                            put("id", notification.id)
                            put("kind", notification.kind.name)
                            put("title", notification.title)
                            notification.body?.let { body -> put("body", body) }
                            put("createdAt", notification.createdAt)
                            put("read", notification.read)
                        },
                    )
                }
            }.toString()
    }
