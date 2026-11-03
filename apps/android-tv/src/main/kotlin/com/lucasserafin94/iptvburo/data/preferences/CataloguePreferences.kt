package com.lucasserafin94.iptvburo.data.preferences

import com.lucasserafin94.iptvburo.domain.model.ParentalLock
import com.lucasserafin94.iptvburo.domain.model.SubtitlePresentation
import kotlinx.coroutines.flow.Flow

/**
 * What the catalogue hides and what it locks.
 *
 * An interface for the same reason [OnboardingPreferences] is one: the ViewModel's tests run on a
 * plain JVM with a fake Context, where DataStore cannot start. Depending on the implementation
 * directly meant every navigation test failed inside `getApplicationContext`.
 */
interface CatalogueGuard {
    val hiddenCategoryIds: Flow<Set<String>>
    val parentalLock: Flow<ParentalLock>
    val hasPin: Flow<Boolean>

    suspend fun setCategoryHidden(
        categoryId: String,
        hidden: Boolean,
    )

    suspend fun setCategoryLocked(
        categoryId: String,
        locked: Boolean,
    )

    suspend fun setLockAdultCategories(locked: Boolean)

    /** False when [currentPin] is wrong, or when [newPin] is not four digits. */
    suspend fun setPin(
        newPin: String,
        currentPin: String?,
    ): Boolean

    /** False when [currentPin] is wrong. */
    suspend fun clearPin(currentPin: String): Boolean

    /** False when no PIN is set, so nothing opens by accident. */
    suspend fun checkPin(candidate: String): Boolean
}

/** Which web services each profile turned on, and whose sign-in the browser view holds. */
interface WebServiceSettings {
    fun enabledIds(profileId: String): Flow<Set<String>>

    suspend fun setEnabled(
        profileId: String,
        serviceId: String,
        enabled: Boolean,
    )

    /**
     * Records [profileId] as the one signed in through the browser view.
     *
     * True when another profile held it, which means its cookies must be cleared before the page
     * loads: the view has one cookie jar per device, and one adult must not land in another's
     * account.
     */
    suspend fun claimSession(profileId: String): Boolean

    /** Forgets the owner, after the user signed out and the cookies were cleared. */
    suspend fun releaseSession()
}

/** How subtitles are drawn, kept across sessions. */
interface SubtitleSettings {
    val presentation: Flow<SubtitlePresentation>

    suspend fun save(presentation: SubtitlePresentation)
}
