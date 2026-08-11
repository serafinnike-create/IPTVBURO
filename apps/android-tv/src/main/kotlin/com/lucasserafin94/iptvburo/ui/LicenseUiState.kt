package com.lucasserafin94.iptvburo.ui

import com.lucasserafin94.iptvburo.data.licensing.AndroidLicenseStatus
import com.lucasserafin94.iptvburo.domain.model.LicenseBlockReason

sealed interface LicenseUiState {
    data object NotChecked : LicenseUiState
    data class Checking(val deviceId: String? = null) : LicenseUiState

    data class Allowed(
        val deviceId: String,
        val isTrial: Boolean,
        val daysRemaining: Long?,
        val offline: Boolean,
    ) : LicenseUiState

    data class Blocked(
        val deviceId: String,
        val reason: LicenseBlockReason,
        val isWorking: Boolean = false,
        val activationFailed: Boolean = false,
    ) : LicenseUiState
}

internal fun AndroidLicenseStatus.toUiState(): LicenseUiState =
    if (allowsUse) {
        LicenseUiState.Allowed(
            deviceId = deviceId,
            isTrial = isTrial,
            daysRemaining = daysRemaining,
            offline = offline,
        )
    } else {
        LicenseUiState.Blocked(
            deviceId = deviceId,
            reason = requireNotNull(blockReason),
        )
    }
