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
    /** A photo the user chose, or null for the drawn avatar. */
    val photoUri: String? = null,
    /** The playlist this profile signs in to, or null to use whatever is available. */
    val sourceId: String? = null,
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

    /**
     * Creates the first profile when there is none, so a fresh install has somewhere to store
     * favourites and progress before the user has been asked anything.
     *
     * [defaultName] is supplied by the caller rather than written here: this class has no
     * resources, and the name was hard-coded in Portuguese, so an English, German or Italian
     * install opened onto a profile called "Meu perfil".
     */
    suspend fun ensureDefaultProfile(
        languageTag: String,
        defaultName: String = "Buro",
    ) = withContext(ioDispatcher) {
        if (profileDao.count() == 0) {
            profileDao.upsert(
                ProfileEntity(
                    id = UUID.randomUUID().toString(),
                    name = defaultName,
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

    /**
     * Renames a profile and changes its avatar and kind.
     *
     * An update rather than a delete-and-recreate: the id is what favourites, playback progress and
     * the encrypted metadata key are all filed under, so replacing it would silently orphan
     * everything the profile owns.
     *
     * [avatarKey] is validated against the known set. An unrecognised key would render as a blank
     * tile with no way for the user to tell why.
     */
    suspend fun updateProfile(
        id: String,
        name: String,
        avatarKey: String,
        type: ProfileType,
        /**
         * The chosen photo, or null to go back to the drawn avatar.
         *
         * [clearPhoto] distinguishes "leave it as it was" from "remove it": a null alone cannot
         * say which, and silently dropping somebody's photo on an unrelated rename would be worse
         * than refusing to remove it.
         */
        photoUri: String? = null,
        clearPhoto: Boolean = false,
    ): BuroProfile? =
        withContext(ioDispatcher) {
            val existing = profileDao.getById(id) ?: return@withContext null
            val cleanName = name.trim()
            require(cleanName.length in 1..MAX_NAME_LENGTH) { "Nome de perfil inválido." }
            require(avatarKey in AVATARS) { "Avatar desconhecido." }
            val updated =
                existing.copy(
                    name = cleanName,
                    avatarKey = avatarKey,
                    profileType = type.name,
                    photoUri =
                        when {
                            clearPhoto -> null
                            photoUri != null -> photoUri
                            else -> existing.photoUri
                        },
                )
            profileDao.upsert(updated)
            updated.toProfile()
        }

    /**
     * Removes a profile and everything filed under it.
     *
     * The last profile cannot be deleted: the app has no meaningful state without one, and a user
     * who removed it would be left staring at an empty picker with no way forward.
     *
     * Favourites go with it explicitly rather than relying on a cascade, so the rule survives a
     * schema change that drops the foreign key.
     */
    suspend fun deleteProfile(id: String): Boolean =
        withContext(ioDispatcher) {
            if (profileDao.count() <= 1) return@withContext false
            if (profileDao.getById(id) == null) return@withContext false
            favoriteDao.removeAllForProfile(id)
            profileDao.delete(id)
            true
        }

    /**
     * Points a profile at a playlist, or clears the choice.
     *
     * Its own method rather than a parameter on [updateProfile]: changing which playlist a profile
     * signs in to swaps the whole catalogue underneath it, which is a different kind of change from
     * renaming it, and the two should not be possible to confuse in one call.
     */
    suspend fun setProfileSource(id: String, sourceId: String?): BuroProfile? =
        withContext(ioDispatcher) {
            val existing = profileDao.getById(id) ?: return@withContext null
            val updated = existing.copy(sourceId = sourceId)
            profileDao.upsert(updated)
            updated.toProfile()
        }

    /** The avatars a profile may use, in a stable order for the picker. */
    fun availableAvatars(): List<String> = AVATARS

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
        photoUri = photoUri,
        sourceId = sourceId,
    )
