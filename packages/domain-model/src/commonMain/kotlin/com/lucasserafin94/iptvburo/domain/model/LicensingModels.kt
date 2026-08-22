package com.lucasserafin94.iptvburo.domain.model

import kotlin.time.Instant

enum class EntitlementState {
    UNREGISTERED,
    TRIAL,
    ACTIVE,
    GRACE,
    REVOKED,
    REFUNDED,
    EXPIRED,
    UNAVAILABLE,
}

enum class PurchaseProvider {
    STRIPE,
    GOOGLE_PLAY,
    APPLE_STOREKIT,
    SAMSUNG_CHECKOUT,
    MICROSOFT_STORE,
}

data class DeviceIdentity(
    val deviceId: String,
    val publicKeyDerBase64: String,
) {
    init {
        require(DEVICE_ID.matches(deviceId)) { "Device ID has an invalid format." }
        require(publicKeyDerBase64.isNotBlank()) { "A public key is required." }
    }

    override fun toString(): String = "DeviceIdentity(deviceId=$deviceId, publicKey=<redacted>)"

    private companion object {
        val DEVICE_ID = Regex("[A-Z2-9]{4}(?:-[A-Z2-9]{4}){2}")
    }
}

data class Entitlement(
    val state: EntitlementState,
    val deviceId: String,
    val trialEndsAt: Instant? = null,
    val purchasedAt: Instant? = null,
    val lastVerifiedAt: Instant? = null,
    val offlineValidUntil: Instant? = null,
    val provider: PurchaseProvider? = null,
) {
    val grantsPlayback: Boolean
        get() = state in setOf(EntitlementState.TRIAL, EntitlementState.ACTIVE, EntitlementState.GRACE)
}

interface LicenseClient {
    suspend fun register(identity: DeviceIdentity, platform: String): Entitlement
    suspend fun entitlement(deviceId: String, signedChallenge: ByteArray): Entitlement
    suspend fun restore(provider: PurchaseProvider, receipt: String): Entitlement
}
