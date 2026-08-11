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

/** How subtitles are drawn, kept across sessions. */
interface SubtitleSettings {
    val presentation: Flow<SubtitlePresentation>

    suspend fun save(presentation: SubtitlePresentation)
}
