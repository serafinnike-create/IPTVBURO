package com.lucasserafin94.iptvburo.data.repository

import com.lucasserafin94.iptvburo.data.local.dao.ReminderDao
import com.lucasserafin94.iptvburo.data.local.entity.ReminderEntity
import com.lucasserafin94.iptvburo.data.cache.isStorableArtwork
import com.lucasserafin94.iptvburo.di.IoDispatcher
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.Reminder
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Reminders, as the screens and the notification worker need them.
 *
 * The one place that knows a reminder is a row: everything above works in [Reminder], so nothing
 * else has to care that a release date is stored as text or that the identity is a string key.
 */
@Singleton
class ReminderRepository @Inject constructor(
    private val reminderDao: ReminderDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    fun observe(profileId: String): Flow<List<Reminder>> =
        reminderDao.observeForProfile(profileId).map { rows -> rows.map(ReminderEntity::toDomain) }

    suspend fun forProfile(profileId: String): List<Reminder> =
        withContext(ioDispatcher) { reminderDao.forProfile(profileId).map(ReminderEntity::toDomain) }

    /**
     * Marks a title, or unmarks it when it is already marked.
     *
     * One entry point for the button, because the button is one control: a separate add and remove
     * would let a double tap leave the row and the icon disagreeing about what is stored.
     *
     * Returns what the title is *now*, so the caller can say "lembrete adicionado" or "removido"
     * without reading the database again.
     */
    suspend fun toggle(
        profileId: String,
        identity: ContentIdentity,
        title: String,
        artworkUrl: String? = null,
        releaseDate: LocalDate? = null,
        now: Instant = Clock.System.now(),
    ): Boolean =
        withContext(ioDispatcher) {
            if (reminderDao.isMarked(profileId, identity.key)) {
                reminderDao.remove(profileId, identity.key)
                false
            } else {
                reminderDao.upsert(
                    ReminderEntity(
                        profileId = profileId,
                        contentKey = identity.key,
                        title = title.trim(),
                        // Dropped when it carries a credential. See [isStorableArtwork].
                        artworkUrl = artworkUrl?.takeIf(::isStorableArtwork),
                        releaseDate = releaseDate?.toString(),
                        createdAtEpochMillis = now.toEpochMilliseconds(),
                    ),
                )
                true
            }
        }

    suspend fun remove(profileId: String, identity: ContentIdentity) {
        withContext(ioDispatcher) { reminderDao.remove(profileId, identity.key) }
    }

    /**
     * Every profile's reminders, keyed by profile.
     *
     * For the notification worker, which runs with nobody signed in: it cannot ask "what is
     * outstanding for the person watching", so it asks for everything and keeps each profile's
     * reminders apart — one household member's list must not be announced under another's name.
     */
    suspend fun allByProfile(): Map<String, List<Reminder>> =
        withContext(ioDispatcher) {
            reminderDao.all()
                .groupBy(ReminderEntity::profileId)
                .mapValues { (_, rows) -> rows.map(ReminderEntity::toDomain) }
        }
}

private fun ReminderEntity.toDomain(): Reminder =
    Reminder(
        identity = ContentIdentity(contentKey),
        title = title,
        artworkUrl = artworkUrl,
        // A date that cannot be parsed is treated as absent rather than as a crash: the row still
        // names a title worth reminding about, and the worst case is that it stops counting down.
        releaseDate = releaseDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        createdAt = Instant.fromEpochMilliseconds(createdAtEpochMillis),
    )
