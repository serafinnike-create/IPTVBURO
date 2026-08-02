package com.lucasserafin94.iptvburo.data.preferences

import kotlinx.coroutines.flow.Flow

interface OnboardingPreferences {
    val accepted: Flow<Boolean>
    val activeProfileId: Flow<String?>

    suspend fun acceptLegalNotice()

    suspend fun selectProfile(profileId: String?)
}
