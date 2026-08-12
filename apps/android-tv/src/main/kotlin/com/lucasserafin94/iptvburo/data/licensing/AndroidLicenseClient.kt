package com.lucasserafin94.iptvburo.data.licensing

import android.content.Context
import android.util.Base64
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lucasserafin94.iptvburo.domain.model.EntitlementState
import com.lucasserafin94.iptvburo.domain.model.LicenseBlockReason
import com.lucasserafin94.iptvburo.domain.model.LicenseDecision
import com.lucasserafin94.iptvburo.domain.model.LicensePolicy
import com.lucasserafin94.iptvburo.domain.model.LicenseSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bouncycastle.math.ec.rfc8032.Ed25519
import org.bouncycastle.util.encoders.Base64 as BouncyBase64

/**
 * Android implementation of the signed licence protocol already used by Windows.
 *
 * The public device code is not an authentication secret. Every request also carries a fresh
 * nonce signed by the installation's P-256 key in Android Keystore, and every answer is verified
 * with the server's pinned Ed25519 public key before it can grant access.
 */
@Singleton
class AndroidLicenseClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
    sharedHttpClient: OkHttpClient,
) : AndroidLicenseService {
    private val store = AndroidLicenseStore(context)
    private val http =
        sharedHttpClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()

    /** Checks the live entitlement, registering a genuinely new installation when necessary. */
    override fun check(now: Instant): AndroidLicenseStatus {
        val identity =
            runCatching { AndroidDeviceIdentityProvider.getOrCreate(context) }
                .getOrElse {
                    return AndroidLicenseStatus.blocked(
                        reason = LicenseBlockReason.NOT_ACTIVATED,
                        deviceId = "",
                    )
                }
        val firstSeen = store.rememberFirstSeen(now)

        return runCatching {
            val validationNonce = freshNonce()
            when (
                val validation =
                    ask(
                        url = AndroidLicenseEndpoints.VALIDATE,
                        action = AndroidDeviceProofAction.VALIDATE,
                        identity = identity,
                        nonce = validationNonce,
                    )
            ) {
                is ServerAnswer.Signed -> acceptLive(validation.licence, identity.deviceId, validationNonce, now)
                is ServerAnswer.Refused -> {
                    val mayRegister =
                        (validation.status == 404 && validation.code == "not_registered") ||
                            (validation.status == 409 && validation.code == "identity_upgrade_required")
                    if (!mayRegister) return fallback(identity.deviceId, LicenseBlockReason.UNREACHABLE, now)

                    val registrationNonce = freshNonce()
                    val registration =
                        ask(
                            url = AndroidLicenseEndpoints.REGISTER,
                            action = AndroidDeviceProofAction.REGISTER,
                            identity = identity,
                            nonce = registrationNonce,
                            includeRegistration = true,
                            extra = { body -> body.addProperty("firstSeen", firstSeen.toString()) },
                        )
                    val signed = registration as? ServerAnswer.Signed
                        ?: return fallback(identity.deviceId, LicenseBlockReason.NOT_ACTIVATED, now)
                    acceptLive(signed.licence, identity.deviceId, registrationNonce, now)
                }

                ServerAnswer.Unreachable -> fallback(identity.deviceId, LicenseBlockReason.UNREACHABLE, now)
            }
        }.getOrElse {
            fallback(identity.deviceId, LicenseBlockReason.UNREACHABLE, now)
        }
    }

    /** Redeems a single-use activation key through proof of possession of this installation. */
    override fun redeem(key: String, now: Instant): AndroidLicenseStatus? {
        val clean = key.trim().uppercase().takeIf { it.length in 4..128 } ?: return null
        val identity = runCatching { AndroidDeviceIdentityProvider.getOrCreate(context) }.getOrNull() ?: return null
        val nonce = freshNonce()
        val answer =
            runCatching {
                ask(
                    url = AndroidLicenseEndpoints.REDEEM,
                    action = AndroidDeviceProofAction.REDEEM,
                    identity = identity,
                    nonce = nonce,
                    extra = { body -> body.addProperty("key", clean) },
                )
            }.getOrNull() as? ServerAnswer.Signed ?: return null
        return acceptLive(answer.licence, identity.deviceId, nonce, now)
    }

    /**
     * Sends an opaque Play token to the Worker; only Google's server response may grant access.
     * The P-256 proof binds the token digest and stable obfuscated account id to this installation,
     * so changing either field after signing is rejected before the purchase is queried.
     */
    override fun submitGooglePlayPurchase(
        purchaseToken: String,
        accountId: String,
        now: Instant,
    ): GooglePlayPurchaseSubmission {
        val token =
            purchaseToken.takeIf {
                it.length in 16..4_096 && it.all { character -> character.code in 0x21..0x7E }
            } ?: return GooglePlayPurchaseSubmission.Rejected
        val account = accountId.takeIf { it.matches(Regex("^[a-f0-9]{64}$")) }
            ?: return GooglePlayPurchaseSubmission.Rejected
        val identity = runCatching { AndroidDeviceIdentityProvider.getOrCreate(context) }.getOrNull()
            ?: return GooglePlayPurchaseSubmission.Unreachable
        val nonce = freshNonce()
        val tokenHash = sha256Base64Url(token)
        val body =
            JsonObject().apply {
                addProperty("deviceId", identity.deviceId)
                addProperty("nonce", nonce)
                addProperty("purchaseToken", token)
                addProperty("accountId", account)
                addProperty(
                    "proof",
                    identity.googlePlayPurchaseProof(
                        nonce = nonce,
                        purchaseTokenHash = tokenHash,
                        accountId = account,
                    ),
                )
            }
        val request =
            Request.Builder()
                .url(AndroidLicenseEndpoints.GOOGLE_PLAY_PURCHASE)
                .post(body.toString().toRequestBody(JSON))
                .build()

        return runCatching {
            http.newCall(request).execute().use { response ->
                val responseText = response.body.string()
                if (response.code == 202) return@use GooglePlayPurchaseSubmission.Pending
                if (!response.isSuccessful) {
                    return@use if (response.code == 429 || response.code >= 500) {
                        GooglePlayPurchaseSubmission.Unreachable
                    } else {
                        GooglePlayPurchaseSubmission.Rejected
                    }
                }
                val json = JsonParser.parseString(responseText).asJsonObject
                val signed =
                    SignedAndroidLicense(
                        payload = json.get("payload")?.asString
                            ?: return@use GooglePlayPurchaseSubmission.Rejected,
                        signatureBase64 = json.get("signature")?.asString
                            ?: return@use GooglePlayPurchaseSubmission.Rejected,
                    )
                val status = acceptLive(signed, identity.deviceId, nonce, now)
                if (status.allowsUse) {
                    GooglePlayPurchaseSubmission.Verified(status)
                } else {
                    GooglePlayPurchaseSubmission.Rejected
                }
            }
        }.getOrDefault(GooglePlayPurchaseSubmission.Unreachable)
    }

    private fun acceptLive(
        licence: SignedAndroidLicense,
        deviceId: String,
        nonce: String,
        localNow: Instant,
    ): AndroidLicenseStatus {
        val verified = licence.verified(AndroidLicenseEndpoints.SERVER_PUBLIC_KEY, deviceId, nonce)
            ?: return fallback(deviceId, LicenseBlockReason.UNREACHABLE, localNow)
        store.write(licence)
        val snapshot = verified.toSnapshot(trustedNow = verified.serverTimeAt)
        return AndroidLicenseStatus(
            decision = LicensePolicy.decide(snapshot),
            deviceId = deviceId,
            offline = false,
            clockSuspect = LicensePolicy.isClockSuspect(snapshot, localNow),
        )
    }

    private fun fallback(
        deviceId: String,
        reason: LicenseBlockReason,
        localNow: Instant,
    ): AndroidLicenseStatus {
        val licence = store.read()
            ?: return AndroidLicenseStatus.blocked(reason, deviceId)
        val verified = licence.verified(AndroidLicenseEndpoints.SERVER_PUBLIC_KEY, deviceId)
            ?: return AndroidLicenseStatus.blocked(reason, deviceId)
        val snapshot = verified.toSnapshot(trustedNow = localNow)
        val clockSuspect = LicensePolicy.isClockSuspect(snapshot, localNow)
        return AndroidLicenseStatus(
            decision =
                if (clockSuspect) {
                    LicenseDecision.Blocked(LicenseBlockReason.NEEDS_VERIFICATION)
                } else {
                    LicensePolicy.decide(snapshot)
                },
            deviceId = deviceId,
            offline = true,
            clockSuspect = clockSuspect,
        )
    }

    private fun ask(
        url: String,
        action: AndroidDeviceProofAction,
        identity: AndroidDeviceInstallationIdentity,
        nonce: String,
        includeRegistration: Boolean = false,
        extra: (JsonObject) -> Unit = {},
    ): ServerAnswer {
        val body =
            JsonObject().apply {
                addProperty("deviceId", identity.deviceId)
                addProperty("nonce", nonce)
                addProperty("proof", identity.proof(action, nonce))
                if (includeRegistration) {
                    addProperty("installationId", identity.installationId)
                    addProperty("publicKey", identity.publicKeyDerBase64)
                }
                extra(this)
            }
        val request =
            Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody(JSON))
                .build()

        return runCatching<ServerAnswer> {
            http.newCall(request).execute().use { response ->
                val responseText = response.body.string()
                val json = runCatching { JsonParser.parseString(responseText).asJsonObject }.getOrNull()
                if (!response.isSuccessful) {
                    return@use ServerAnswer.Refused(
                        status = response.code,
                        code = json?.get("error")?.asString.orEmpty(),
                    )
                }
                ServerAnswer.Signed(
                    SignedAndroidLicense(
                        payload = json?.get("payload")?.asString
                            ?: return@use ServerAnswer.Refused(response.code, "bad_response"),
                        signatureBase64 = json.get("signature")?.asString
                            ?: return@use ServerAnswer.Refused(response.code, "bad_response"),
                    ),
                )
            }
        }.getOrDefault(ServerAnswer.Unreachable)
    }

    private fun freshNonce(): String =
        ByteArray(NONCE_BYTES).also(RANDOM::nextBytes).let { bytes ->
            Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }

    private fun sha256Base64Url(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .let { digest ->
                Base64.encodeToString(
                    digest,
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
                )
            }

    private companion object {
        const val NONCE_BYTES = 16
        val JSON = "application/json; charset=utf-8".toMediaType()
        val RANDOM = SecureRandom()
    }
}

interface AndroidLicenseService {
    fun check(now: Instant = Instant.now()): AndroidLicenseStatus
    fun redeem(key: String, now: Instant = Instant.now()): AndroidLicenseStatus?
    fun submitGooglePlayPurchase(
        purchaseToken: String,
        accountId: String,
        now: Instant = Instant.now(),
    ): GooglePlayPurchaseSubmission = GooglePlayPurchaseSubmission.Unreachable
}

sealed interface GooglePlayPurchaseSubmission {
    data class Verified(val status: AndroidLicenseStatus) : GooglePlayPurchaseSubmission
    data object Pending : GooglePlayPurchaseSubmission
    data object Rejected : GooglePlayPurchaseSubmission
    data object Unreachable : GooglePlayPurchaseSubmission
}

/** A signed document kept verbatim so verification never depends on JSON re-serialization. */
internal data class SignedAndroidLicense(
    val payload: String,
    val signatureBase64: String,
) {
    fun verified(
        publicKeyBase64: String,
        expectedDeviceId: String,
        expectedNonce: String? = null,
    ): VerifiedAndroidLicense? =
        runCatching {
            val encodedKey = BouncyBase64.decode(publicKeyBase64)
            if (
                encodedKey.size != ED25519_SPKI_PREFIX.size + Ed25519.PUBLIC_KEY_SIZE ||
                !encodedKey.copyOfRange(0, ED25519_SPKI_PREFIX.size).contentEquals(ED25519_SPKI_PREFIX)
            ) {
                return null
            }
            val publicKey = encodedKey.copyOfRange(ED25519_SPKI_PREFIX.size, encodedKey.size)
            val signature = BouncyBase64.decode(signatureBase64)
            val message = payload.toByteArray(StandardCharsets.UTF_8)
            if (
                signature.size != Ed25519.SIGNATURE_SIZE ||
                !Ed25519.verify(signature, 0, publicKey, 0, message, 0, message.size)
            ) {
                return null
            }

            val document = JsonParser.parseString(payload).asJsonObject
            val deviceId = document.string("deviceId") ?: return null
            if (deviceId != expectedDeviceId) return null
            if (expectedNonce != null) {
                val answered = document.string("nonce") ?: return null
                if (!MessageDigest.isEqual(answered.toByteArray(), expectedNonce.toByteArray())) return null
            }

            VerifiedAndroidLicense(
                deviceId = deviceId,
                state = document.string("state")?.let(::stateOf) ?: return null,
                trialEndsAt = document.instant("trialEndsAt"),
                expiresAt = document.instant("expiresAt"),
                serverTimeAt = document.instant("serverTime") ?: return null,
            )
        }.getOrNull()

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString?.takeIf(String::isNotBlank)

    private fun JsonObject.instant(name: String): Instant? =
        string(name)?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun stateOf(value: String): EntitlementState =
        runCatching { EntitlementState.valueOf(value.uppercase()) }
            .getOrDefault(EntitlementState.UNAVAILABLE)

    private companion object {
        // RFC 8410 SubjectPublicKeyInfo prefix for a raw 32-byte Ed25519 public key.
        val ED25519_SPKI_PREFIX =
            byteArrayOf(0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70, 0x03, 0x21, 0x00)
    }
}

internal data class VerifiedAndroidLicense(
    val deviceId: String,
    val state: EntitlementState,
    val trialEndsAt: Instant?,
    val expiresAt: Instant?,
    val serverTimeAt: Instant,
) {
    fun toSnapshot(trustedNow: Instant): LicenseSnapshot =
        LicenseSnapshot(
            state = state,
            trustedNow = trustedNow,
            trialEndsAt = trialEndsAt,
            expiresAt = expiresAt,
            offlineValidUntil = LicensePolicy.offlineDeadlineFor(serverTimeAt, state),
            serverTimeAt = serverTimeAt,
        )
}

data class AndroidLicenseStatus(
    val decision: LicenseDecision,
    val deviceId: String,
    val offline: Boolean,
    val clockSuspect: Boolean,
) {
    val allowsUse: Boolean get() = decision.allowsPlayback
    val isTrial: Boolean get() = (decision as? LicenseDecision.Allowed)?.isTrial == true
    val daysRemaining: Long?
        get() = (decision as? LicenseDecision.Allowed)?.remaining?.roundedUpDays()
    val blockReason: LicenseBlockReason?
        get() = (decision as? LicenseDecision.Blocked)?.reason

    companion object {
        fun blocked(reason: LicenseBlockReason, deviceId: String): AndroidLicenseStatus =
            AndroidLicenseStatus(
                decision = LicenseDecision.Blocked(reason),
                deviceId = deviceId,
                offline = false,
                clockSuspect = false,
            )
    }
}

private fun Duration.roundedUpDays(): Long =
    when {
        isNegative || isZero -> 0L
        else -> (toMinutes() + 1_439L) / 1_440L
    }

private class AndroidLicenseStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(): SignedAndroidLicense? {
        val payload = preferences.getString(KEY_PAYLOAD, null)?.takeIf(String::isNotBlank) ?: return null
        val signature = preferences.getString(KEY_SIGNATURE, null)?.takeIf(String::isNotBlank) ?: return null
        return SignedAndroidLicense(payload, signature)
    }

    fun write(licence: SignedAndroidLicense) {
        preferences.edit()
            .putString(KEY_PAYLOAD, licence.payload)
            .putString(KEY_SIGNATURE, licence.signatureBase64)
            .commit()
    }

    /**
     * The earliest this device was ever seen, across every marker the app writes.
     *
     * Sent to the server so that reinstalling cannot buy a fresh trial. It is only ever allowed to
     * *shorten* a trial, never to extend one, so a forged marker gains nothing.
     *
     * Two locations, because the preferences alone were not enough: they live inside the app's own
     * data and Android deletes all of it on uninstall, so removing the app and installing it again
     * produced a device the server had never met and seven more free days, repeatable for ever.
     * The second marker is in shared external storage, which survives an uninstall.
     *
     * Not a proof of anything — someone determined can clear both, and on Android 11 and later the
     * external marker may not be writable at all. It is a speed bump on the easy attack, and it
     * costs nothing when it fails.
     */
    fun rememberFirstSeen(now: Instant): Instant {
        val fromPreferences = preferences.getString(KEY_FIRST_SEEN, null)
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val fromExternal = readExternalMarker()

        val earliest = listOfNotNull(fromPreferences, fromExternal, now).min()

        if (fromPreferences == null || earliest != fromPreferences) {
            preferences.edit().putString(KEY_FIRST_SEEN, earliest.toString()).commit()
        }
        if (fromExternal == null || earliest != fromExternal) {
            writeExternalMarker(earliest)
        }
        return earliest
    }

    /** Null when the file is absent, unreadable or holds something that is not an instant. */
    private fun readExternalMarker(): Instant? =
        runCatching {
            externalMarkerFile()
                ?.takeIf(java.io.File::exists)
                ?.readText()
                ?.trim()
                ?.let(Instant::parse)
        }.getOrNull()

    private fun writeExternalMarker(value: Instant) {
        // Failure is expected and ignored: scoped storage, a missing volume, or no permission. The
        // preferences marker still applies, and the server still refuses a trial that starts later
        // than the earliest marker it has been shown.
        runCatching {
            externalMarkerFile()?.apply {
                parentFile?.mkdirs()
                writeText(value.toString())
            }
        }
    }

    /**
     * A dotted file in the app's external files directory.
     *
     * Deliberately not in the private data directory: that is what uninstall removes. The name is
     * unremarkable so it does not advertise what it is for.
     */
    private fun externalMarkerFile(): java.io.File? =
        applicationContext.getExternalFilesDir(null)
            ?.parentFile
            ?.parentFile
            ?.let { root -> java.io.File(root, ".iptvburo/.device") }

    private companion object {
        const val PREFERENCES_NAME = "signed_device_licence"
        const val KEY_PAYLOAD = "payload"
        const val KEY_SIGNATURE = "signature"
        const val KEY_FIRST_SEEN = "first_seen"
    }
}

object AndroidLicenseEndpoints {
    const val DOMAIN = "iptvburo.iptvburo.workers.dev"
    const val VALIDATE = "https://$DOMAIN/v1/validate"
    const val REGISTER = "https://$DOMAIN/v1/register"
    const val REDEEM = "https://$DOMAIN/v1/redeem"
    const val GOOGLE_PLAY_PURCHASE = "https://$DOMAIN/v1/google-play/purchase"
    const val SERVER_PUBLIC_KEY =
        "MCowBQYDK2VwAyEAXm01dKxc4kXNYaSYnVL0isza1EnYn+nYjyfNhnWoILw="

    fun purchaseUrl(deviceId: String, language: String): String =
        "https://$DOMAIN/comprar?device=$deviceId&lang=${language.substringBefore('-')}"
}

private sealed interface ServerAnswer {
    data class Signed(val licence: SignedAndroidLicense) : ServerAnswer
    data class Refused(val status: Int, val code: String) : ServerAnswer
    data object Unreachable : ServerAnswer
}
