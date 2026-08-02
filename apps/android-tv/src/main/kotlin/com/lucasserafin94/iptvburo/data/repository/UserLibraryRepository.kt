package com.lucasserafin94.iptvburo.data.repository

import com.lucasserafin94.iptvburo.data.local.dao.FavoriteDao
import com.lucasserafin94.iptvburo.data.local.dao.ProfileDao
import com.lucasserafin94.iptvburo.data.local.entity.FavoriteEntity
import com.lucasserafin94.iptvburo.data.local.entity.ProfileEntity
import com.lucasserafin94.iptvburo.data.mapper.toDomain
import com.lucasserafin94.iptvburo.di.IoDispatcher
import com.lucasserafin94.iptvburo.domain.model.Channel
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class BuroProfile(
    val id: String,
    val name: String,
    val avatarKey: String,
    val type: ProfileType,
    val languageTag: String,
    val audioLanguageTag: String,
    val subtitleLanguageTag: String,
)

enum class ProfileType { ADULT, KIDS, GUEST }

@Singleton
class UserLibraryRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val favoriteDao: FavoriteDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    fun observeProfiles(): Flow<List<ProfileEntity>> = profileDao.observeAll()

    fun observeFavoriteIds(profileId: String): Flow<List<String>> =
        favoriteDao.observeIds(profileId)

    suspend fun ensureDefaultProfile(languageTag: String) = withContext(ioDispatcher) {
        if (profileDao.count() == 0) {
            profileDao.upsert(
                ProfileEntity(
                    id = UUID.randomUUID().toString(),
                    name = "Meu perfil",
                    avatarKey = "aurora",
                    profileType = ProfileType.ADULT.name,
                    languageTag = languageTag,
                    audioLanguageTag = languageTag,
                    subtitleLanguageTag = languageTag,
                    sortOrder = 0,
                    createdAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun createProfile(name: String, type: ProfileType, languageTag: String): BuroProfile =
        withContext(ioDispatcher) {
            require(profileDao.count() < MAX_PROFILES) { "A família já possui cinco perfis." }
            val cleanName = name.trim()
            require(cleanName.length in 1..MAX_NAME_LENGTH) { "Nome de perfil inválido." }
            val entity =
                ProfileEntity(
                    id = UUID.randomUUID().toString(),
                    name = cleanName,
                    avatarKey = AVATARS[(profileDao.maxSortOrder() + 1) % AVATARS.size],
                    profileType = type.name,
                    languageTag = languageTag,
                    audioLanguageTag = languageTag,
                    subtitleLanguageTag = languageTag,
                    sortOrder = profileDao.maxSortOrder() + 1,
                    createdAtEpochMillis = System.currentTimeMillis(),
                )
            profileDao.upsert(entity)
            entity.toProfile()
        }

    suspend fun getProfile(id: String): BuroProfile? =
        withContext(ioDispatcher) { profileDao.getById(id)?.toProfile() }

    suspend fun toggleFavorite(profileId: String, channelId: String, currentlyFavorite: Boolean) =
        withContext(ioDispatcher) {
            if (currentlyFavorite) {
                favoriteDao.remove(profileId, channelId)
            } else {
                favoriteDao.add(FavoriteEntity(profileId, channelId, System.currentTimeMillis()))
            }
        }

    suspend fun loadFavorites(profileId: String, limit: Int = 200): List<Channel> =
        withContext(ioDispatcher) { favoriteDao.loadChannels(profileId, limit).map { it.toDomain() } }

    private companion object {
        const val MAX_PROFILES = 5
        const val MAX_NAME_LENGTH = 24
        val AVATARS = listOf("aurora", "ember", "forest", "ocean", "moon")
    }
}

fun ProfileEntity.toProfile(): BuroProfile =
    BuroProfile(
        id = id,
        name = name,
        avatarKey = avatarKey,
        type = runCatching { ProfileType.valueOf(profileType) }.getOrDefault(ProfileType.ADULT),
        languageTag = languageTag,
        audioLanguageTag = audioLanguageTag,
        subtitleLanguageTag = subtitleLanguageTag,
    )
