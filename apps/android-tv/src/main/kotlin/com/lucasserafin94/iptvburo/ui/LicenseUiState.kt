package com.lucasserafin94.iptvburo.ui

import com.lucasserafin94.iptvburo.data.licensing.AndroidLicenseStatus
import com.lucasserafin94.iptvburo.data.licensing.KeyState
import com.lucasserafin94.iptvburo.data.licensing.RedeemFailure
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
        /**
         * Why the last activation attempt failed, when one has been made.
         *
         * Null before the first attempt, and alongside [activationFailed] rather than replacing it
         * so the flag keeps meaning "an attempt failed" even for a reason a future version does not
         * have wording for.
         */
        val activationFailure: RedeemFailure? = null,
        /**
         * What the key currently typed is, when the server has been asked.
         *
         * Advisory only: nothing is granted from this, and pressing Use key is still what decides.
         * It exists so the user is not left typing into a box that says nothing back.
         */
        val typedKeyState: KeyState? = null,
    ) : LicenseUiState
}

/**
 * The result of the last activation-key attempt, wherever it was made from.
 *
 * Separate from [LicenseUiState] because the Settings card can be used while the licence is
 * perfectly valid — someone extending a subscription they are currently using — and that card has
 * no access to the gate's Blocked state. Keeping the outcome here is what lets both screens report
 * the same thing.
 */
sealed interface RedemptionUi {
    /** Nothing attempted, or the notice has been read. */
    data object Idle : RedemptionUi

    data object Working : RedemptionUi

    /** [daysRemaining] is null when the server granted access without stating a duration. */
    data class Activated(val daysRemaining: Long?) : RedemptionUi

    data class Failed(val reason: RedeemFailure) : RedemptionUi
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
