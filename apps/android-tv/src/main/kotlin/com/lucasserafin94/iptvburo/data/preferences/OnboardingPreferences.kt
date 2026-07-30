package com.lucasserafin94.iptvburo.data.preferences

import kotlinx.coroutines.flow.Flow

interface OnboardingPreferences {
    val accepted: Flow<Boolean>

    suspend fun acceptLegalNotice()
}
