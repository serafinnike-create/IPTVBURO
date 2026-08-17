package com.lucasserafin94.iptvburo.data.repository

import com.lucasserafin94.iptvburo.data.local.dao.FavoriteDao
import com.lucasserafin94.iptvburo.data.local.dao.PlaybackProgressDao
import com.lucasserafin94.iptvburo.data.local.dao.ProfileDao
import com.lucasserafin94.iptvburo.data.local.dao.SeriesWatchDao
import com.lucasserafin94.iptvburo.data.local.entity.SeriesWatchEntity
import com.lucasserafin94.iptvburo.data.local.dao.ReminderDao
import com.lucasserafin94.iptvburo.data.local.entity.ChannelEntity
import com.lucasserafin94.iptvburo.data.local.entity.FavoriteEntity
import com.lucasserafin94.iptvburo.data.local.entity.PlaybackProgressEntity
import com.lucasserafin94.iptvburo.data.local.entity.ProfileEntity
import com.lucasserafin94.iptvburo.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tidying away the automatic first profile, and every case where it must be left alone.
 *
 * This deletes something the user can see, so the conditions matter more than the happy path: the
 * scaffolding profile exists so a fresh install has somewhere to file favourites before anyone has
 * been asked anything, and removing one that was actually used would destroy exactly the data the
 * profile list exists to keep apart.
 */
class DefaultProfileCleanupTest {
    private val defaultName = "Meu perfil"

    @Test
    fun `removes the automatic profile once the household names its own`() = runTest {
        val profiles = FakeProfileDao(listOf(scaffolding(), named("lucas")))
        val repository = repositoryWith(profiles)

        assertTrue(repository.removeUnusedDefaultProfile(defaultName))
        assertEquals(listOf("lucas"), profiles.stored.map(ProfileEntity::name))
    }

    @Test
    fun `leaves it alone when it is the only profile there is`() = runTest {
        // A fresh install: removing this would leave the app with nowhere to file anything, and
        // every write keyed by profile would silently do nothing.
        val profiles = FakeProfileDao(listOf(scaffolding()))
        val repository = repositoryWith(profiles)

        assertFalse(repository.removeUnusedDefaultProfile(defaultName))
        assertEquals(1, profiles.stored.size)
    }

    @Test
    fun `keeps it when somebody favourited something under it`() = runTest {
        val profiles = FakeProfileDao(listOf(scaffolding(), named("lucas")))
        val repository =
            repositoryWith(
                profiles,
                favourites = mapOf(SCAFFOLD_ID to listOf("channel-1")),
            )

        assertFalse(repository.removeUnusedDefaultProfile(defaultName))
        assertEquals(2, profiles.stored.size)
    }

    @Test
    fun `keeps it when a reminder was marked under it`() = runTest {
        val profiles = FakeProfileDao(listOf(scaffolding(), named("lucas")))
        val repository =
            repositoryWith(
                profiles,
                reminders = mapOf(SCAFFOLD_ID to listOf(reminder())),
            )

        assertFalse(repository.removeUnusedDefaultProfile(defaultName))
    }

    @Test
    fun `keeps it when something was watched under it`() = runTest {
        val profiles = FakeProfileDao(listOf(scaffolding(), named("lucas")))
        val repository =
            repositoryWith(
                profiles,
                progress = mapOf(SCAFFOLD_ID to listOf(progress())),
            )

        assertFalse(repository.removeUnusedDefaultProfile(defaultName))
    }

    @Test
    fun `keeps it once it has been renamed, which is how somebody adopts it`() = runTest {
        val profiles = FakeProfileDao(listOf(scaffolding(name = "Lucas"), named("Ana")))
        val repository = repositoryWith(profiles)

        assertFalse(repository.removeUnusedDefaultProfile(defaultName))
        assertEquals(2, profiles.stored.size)
    }

    @Test
    fun `keeps a profile the household deliberately named the same thing`() = runTest {
        // Created second and therefore not the scaffolding one: the app's own is always first.
        val theirs = named(defaultName).copy(sortOrder = 1, createdAtEpochMillis = 2_000L)
        val profiles = FakeProfileDao(listOf(named("lucas").copy(sortOrder = 0), theirs))
        val repository = repositoryWith(profiles)

        assertFalse(repository.removeUnusedDefaultProfile(defaultName))
        assertEquals(2, profiles.stored.size)
    }

    // -------------------------------------------------------------------------------------------

    private fun repositoryWith(
        profiles: FakeProfileDao,
        favourites: Map<String, List<String>> = emptyMap(),
        reminders: Map<String, List<ReminderEntity>> = emptyMap(),
        progress: Map<String, List<PlaybackProgressEntity>> = emptyMap(),
    ) = UserLibraryRepository(
        profileDao = profiles,
        favoriteDao = FakeFavoriteDao(favourites),
        reminderDao = FakeReminderDao(reminders),
        playbackProgressDao = FakeProgressDao(progress),
        seriesWatchDao = NoSeriesWatchDao,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    private fun scaffolding(name: String = defaultName) =
        ProfileEntity(
            id = SCAFFOLD_ID,
            name = name,
            avatarKey = "aurora",
            profileType = "ADULT",
            languageTag = "pt-BR",
            audioLanguageTag = "pt-BR",
            subtitleLanguageTag = "pt-BR",
            sortOrder = 0,
            createdAtEpochMillis = 1_000L,
        )

    private fun named(name: String) =
        scaffolding(name).copy(
            id = "profile-$name",
            sortOrder = 1,
            createdAtEpochMillis = 2_000L,
        )

    private fun reminder() =
        ReminderEntity(
            profileId = SCAFFOLD_ID,
            contentKey = "movie:duna:2021",
            title = "Duna",
            artworkUrl = null,
            releaseDate = null,
            createdAtEpochMillis = 1_500L,
        )

    private fun progress() =
        PlaybackProgressEntity(
            profileId = SCAFFOLD_ID,
            sourceId = "source",
            contentId = "content",
            contentType = "MOVIE",
            seriesId = null,
            seasonNumber = null,
            episodeNumber = null,
            positionMs = 60_000L,
            durationMs = 600_000L,
            progressPercent = 10.0,
            lastWatchedAtEpochMillis = 1_500L,
            completedAtEpochMillis = null,
            updatedAtEpochMillis = 1_500L,
            revision = 1L,
        )

    private companion object {
        const val SCAFFOLD_ID = "profile-scaffolding"
    }
}

private class FakeProfileDao(initial: List<ProfileEntity>) : ProfileDao {
    var stored: List<ProfileEntity> = initial
        private set

    override fun observeAll(): Flow<List<ProfileEntity>> = flowOf(stored)

    override suspend fun getById(id: String): ProfileEntity? = stored.firstOrNull { it.id == id }

    override suspend fun count(): Int = stored.size

    override suspend fun maxSortOrder(): Int = stored.maxOfOrNull(ProfileEntity::sortOrder) ?: -1

    override suspend fun upsert(profile: ProfileEntity) {
        stored = stored.filterNot { it.id == profile.id } + profile
    }

    override suspend fun delete(id: String) {
        stored = stored.filterNot { it.id == id }
    }
}

private class FakeFavoriteDao(private val byProfile: Map<String, List<String>>) : FavoriteDao {
    override fun observeIds(profileId: String): Flow<List<String>> =
        flowOf(byProfile[profileId].orEmpty())

    override suspend fun loadChannels(profileId: String, limit: Int): List<ChannelEntity> = emptyList()

    override suspend fun add(favorite: FavoriteEntity) = Unit

    override suspend fun remove(profileId: String, channelId: String) = Unit

    override suspend fun removeAllForProfile(profileId: String) = Unit
}

private class FakeReminderDao(private val byProfile: Map<String, List<ReminderEntity>>) : ReminderDao {
    override fun observeForProfile(profileId: String): Flow<List<ReminderEntity>> =
        flowOf(byProfile[profileId].orEmpty())

    override suspend fun forProfile(profileId: String): List<ReminderEntity> =
        byProfile[profileId].orEmpty()

    override suspend fun upsert(reminder: ReminderEntity) = Unit

    override suspend fun remove(profileId: String, contentKey: String) = Unit

    override suspend fun isMarked(profileId: String, contentKey: String): Boolean = false

    override suspend fun all(): List<ReminderEntity> = byProfile.values.flatten()
}

private class FakeProgressDao(
    private val byProfile: Map<String, List<PlaybackProgressEntity>>,
) : PlaybackProgressDao {
    override fun find(
        profileId: String,
        sourceId: String,
        contentId: String,
        contentType: String,
    ): PlaybackProgressEntity? = null

    override fun upsert(entity: PlaybackProgressEntity) = Unit

    override fun remove(profileId: String, sourceId: String, contentId: String, contentType: String) = Unit

    override fun continueWatching(profileId: String, limit: Int): List<PlaybackProgressEntity> =
        byProfile[profileId].orEmpty().take(limit)

    override fun history(profileId: String, limit: Int): List<PlaybackProgressEntity> =
        byProfile[profileId].orEmpty().take(limit)
}

/** Follows nothing: these assertions never exercise the new-episode notice. */
private data object NoSeriesWatchDao : SeriesWatchDao {
    override suspend fun find(profileId: String, channelId: String): SeriesWatchEntity? = null

    override suspend fun upsert(state: SeriesWatchEntity) = Unit

    override suspend fun all(): List<SeriesWatchEntity> = emptyList()

    override suspend fun remove(profileId: String, channelId: String) = Unit
}
