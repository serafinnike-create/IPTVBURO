package com.lucasserafin94.iptvburo.desktop.license

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lucasserafin94.iptvburo.domain.model.LicenseBlockReason
import com.lucasserafin94.iptvburo.domain.model.LicenseDecision
import com.lucasserafin94.iptvburo.domain.model.LicensePolicy
import java.security.SecureRandom
import kotlin.time.Duration
import kotlin.time.Clock
import kotlin.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Obtains a signed entitlement for the random installation identity and applies the offline policy.
 *
 * Every live request is signed by the installation's DPAPI-protected P-256 key. The public device
 * code is therefore useful for Checkout and support, but cannot be used by itself to impersonate
 * the installation at register, validate or redeem.
 */
class LicenseClient(
    private val store: LicenseStore = LicenseStore(),
    private val http: OkHttpClient = defaultHttpClient(),
    private val identityProvider: DeviceIdentityProvider = DeviceFingerprint,
    private val server: LicenseServerConfiguration = LicenseServerConfiguration.production(),
    private val clock: () -> Instant = Clock.System::now,
) {
    /** The launch check. It fails closed and never lets identity, network or parsing errors escape. */
    fun check(): LicenseStatus {
        val identity =
            runCatching { identityProvider.getOrCreate() }
                .getOrElse { error ->
                    println("licence identity failed: ${error.javaClass.simpleName}")
                    return LicenseStatus(
                        decision = LicenseDecision.Blocked(LicenseBlockReason.NOT_ACTIVATED),
                        deviceId = "",
                    )
                }

        return runCatching { performCheck(identity) }
            .getOrElse { error ->
                // Only the exception type: request URLs and headers must not enter customer logs.
                println("licence check failed: ${error.javaClass.simpleName}")
                fallbackToStored(identity, LicenseBlockReason.UNREACHABLE)
            }
    }

    private fun performCheck(identity: DeviceInstallationIdentity): LicenseStatus {
        val now = clock()
        store.rememberFirstSeen(now)

        // The earliest this machine was ever seen, across three locations the app writes to: its
        // own directory, the user's home, and the Windows registry.
        //
        // Sent to the server so a reinstall cannot buy a fresh trial. The installation identity is
        // a random id in one file; deleting it produces a device the server has never seen, and
        // seven more free days, repeatable for ever. The markers make that harder — somebody has to
        // find all three — and the server refuses to start a trial dated later than the earliest one
        // reported.
        //
        // Not a proof of anything: a determined user can clear all three. It is a speed bump on the
        // easy attack, which is deleting the app's folder, and it costs nothing to send.
        val firstSeen = store.firstSeen()

        if (!server.isConfigured) {
            println("licence check skipped: this build has no server key")
            return LicenseStatus(
                decision = LicenseDecision.Blocked(LicenseBlockReason.NOT_ACTIVATED),
                deviceId = identity.deviceId,
            )
        }

        val validationNonce = freshNonce()
        val liveAnswer =
            when (
                val validation =
                    ask(
                        url = server.validateUrl,
                        action = DeviceProofAction.VALIDATE,
                        identity = identity,
                        nonce = validationNonce,
                    )
            ) {
                is ServerAnswer.Signed -> LiveAnswer(validation.license, validationNonce)
                is ServerAnswer.Refused -> {
                    // Two refusals mean "register and try again", and both must be followed or the
                    // device is stuck for ever.
                    //
                    // `not_registered` is the ordinary first launch. `identity_upgrade_required` is
                    // a row written before device identities were cryptographic: validation cannot
                    // succeed because there is no key to check a proof against, and registration is
                    // exactly what supplies one. Treating it as a hard failure left such a device
                    // unable to register, because registering was what it was being refused.
                    val recoverable =
                        (validation.status == 404 && validation.code == "not_registered") ||
                            (validation.status == 409 && validation.code == "identity_upgrade_required")

                    if (!recoverable) {
                        return fallbackToStored(identity, LicenseBlockReason.UNREACHABLE)
                    }
                    val registrationNonce = freshNonce()
                    val registration =
                        ask(
                            url = server.registerUrl,
                            action = DeviceProofAction.REGISTER,
                            identity = identity,
                            nonce = registrationNonce,
                            includeRegistration = true,
                            // Only on registration, which is the one request where it changes an
                            // outcome. Sending it on every validate would be noise the server has
                            // no use for.
                            extra = { body ->
                                firstSeen?.let { seen -> body.addProperty("firstSeen", seen.toString()) }
                            },
                        )
                    val signed = registration as? ServerAnswer.Signed
                        ?: return fallbackToStored(
                            identity,
                            // A refusal is not a network problem, and saying so sends the customer
                            // to check a connection that is working. NOT_ACTIVATED puts the device
                            // code and the purchase route on screen, which is what they can act on.
                            if (registration is ServerAnswer.Refused) {
                                LicenseBlockReason.NOT_ACTIVATED
                            } else {
                                LicenseBlockReason.UNREACHABLE
                            },
                        )
                    LiveAnswer(signed.license, registrationNonce)
                }

                ServerAnswer.Unreachable ->
                    return fallbackToStored(identity, LicenseBlockReason.UNREACHABLE)
            }

        val verified =
            liveAnswer.license.verified(
                publicKeyBase64 = server.publicKeyBase64,
                expectedDeviceId = identity.deviceId,
                expectedNonce = liveAnswer.nonce,
            ) ?: run {
                println("licence answer failed verification")
                return fallbackToStored(identity, LicenseBlockReason.UNREACHABLE)
            }

        // Anchor the offline deadline to signed server time. Using the local clock here would let a
        // clock moved far forward during validation manufacture an equally far-future grace window.
        store.write(StoredLicense(license = liveAnswer.license, lastVerifiedAt = verified.serverTimeAt))
        val snapshot =
            verified.toSnapshot(
                lastVerifiedAt = verified.serverTimeAt,
                trustedNow = verified.serverTimeAt,
            )
        return LicenseStatus(
            decision = LicensePolicy.decide(snapshot),
            deviceId = identity.deviceId,
            clockSuspect = LicensePolicy.isClockSuspect(snapshot, now),
        )
    }

    /**
     * Asks the server what this machine would be charged.
     *
     * Null when the server cannot be reached, in which case the caller shows nothing rather than a
     * guess: a wrong price is worse than no price, because the customer only discovers it is wrong
     * after committing to buy.
     *
     * No identity and no proof — this is the same public price list printed on the buy page.
     */
    fun price(): PriceQuote? {
        val request = Request.Builder().url(LicenseEndpoints.PRICE).get().build()

        return runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                PriceQuote(
                    label = json.get("label")?.asString ?: return null,
                    currency = json.get("currency")?.asString.orEmpty(),
                    termDays = json.get("termDays")?.asInt ?: 0,
                )
            }
        }.getOrNull()
    }

    /** Redeems a single-use key only through a request signed by this installation. */
    fun redeem(key: String): LicenseStatus? {
        val identity = runCatching { identityProvider.getOrCreate() }.getOrNull() ?: return null
        val nonce = freshNonce()
        val result =
            runCatching {
                ask(
                    url = server.redeemUrl,
                    action = DeviceProofAction.REDEEM,
                    identity = identity,
                    nonce = nonce,
                    extra = { body -> body.addProperty("key", key.trim().uppercase()) },
                )
            }.getOrNull()
        val answer = (result as? ServerAnswer.Signed)?.license ?: return null
        val verified = answer.verified(server.publicKeyBase64, identity.deviceId, nonce) ?: return null
        val now = clock()
        store.write(StoredLicense(license = answer, lastVerifiedAt = verified.serverTimeAt))
        return LicenseStatus(
            decision =
                LicensePolicy.decide(
                    verified.toSnapshot(
                        lastVerifiedAt = verified.serverTimeAt,
                        trustedNow = verified.serverTimeAt,
                    ),
                ),
            deviceId = identity.deviceId,
        )
    }

    /**
     * What a key is, without spending it.
     *
     * The activation screen used to say nothing until a redemption succeeded or failed, so a
     * customer holding a code learned only whether it worked — not that it was already theirs, or
     * already used elsewhere, or worth thirty days.
     *
     * Null covers every failure the same way on purpose: no network, an unregistered device, a
     * server that refused. The screen falls back to letting them try the key, which is what it did
     * before this existed — a description that cannot be fetched must never block a redemption that
     * would have worked.
     */
    fun keyInfo(key: String): KeyInfo? {
        val identity = runCatching { identityProvider.getOrCreate() }.getOrNull() ?: return null
        val nonce = freshNonce()
        val body =
            JsonObject().apply {
                addProperty("deviceId", identity.deviceId)
                addProperty("nonce", nonce)
                // The same signed proof the redemption uses. Without it this endpoint would be an
                // oracle for guessing codes, so the server refuses an unsigned request.
                addProperty("proof", identity.proof(DeviceProofAction.VALIDATE, nonce))
                addProperty("key", key.trim().uppercase())
            }

        return runCatching {
            val request =
                Request.Builder()
                    .url(server.keyInfoUrl)
                    .post(body.toString().toRequestBody(JSON))
                    .build()
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject
                val state = json?.get("state")?.asString ?: return@use null
                KeyInfo(
                    state = KeyState.from(state) ?: return@use null,
                    grantDays = json.get("grantDays")?.takeIf { !it.isJsonNull }?.asInt,
                )
            }
        }.getOrNull()
    }

    /**
     * Re-verifies the stored server signature before granting offline use.
     *
     * A local clock more than [LicensePolicy.CLOCK_TOLERANCE] behind the last signed server time is
     * blocked. Merely setting `clockSuspect` while still returning an allowed decision would make
     * moving the clock backwards an effective trial-extension attack.
     */
    private fun fallbackToStored(
        identity: DeviceInstallationIdentity,
        reason: LicenseBlockReason,
    ): LicenseStatus {
        val stored =
            store.read()
                ?: return LicenseStatus(
                    decision = LicenseDecision.Blocked(reason),
                    deviceId = identity.deviceId,
                )
        val verified =
            stored.license.verified(server.publicKeyBase64, identity.deviceId)
                ?: return LicenseStatus(
                    decision = LicenseDecision.Blocked(reason),
                    deviceId = identity.deviceId,
                )

        val now = clock()
        // Old builds stored a local timestamp here. The signed server time is the only safe anchor,
        // so it also clamps legacy files whose local clock may have been moved far into the future.
        val snapshot =
            verified.toSnapshot(lastVerifiedAt = verified.serverTimeAt, trustedNow = now)
        val clockSuspect = LicensePolicy.isClockSuspect(snapshot, now)
        return LicenseStatus(
            decision =
                if (clockSuspect) {
                    LicenseDecision.Blocked(LicenseBlockReason.NEEDS_VERIFICATION)
                } else {
                    LicensePolicy.decide(snapshot)
                },
            deviceId = identity.deviceId,
            offline = true,
            clockSuspect = clockSuspect,
        )
    }

    /** One bounded request. Network/protocol failures are values, never leaked exceptions. */
    private fun ask(
        url: String,
        action: DeviceProofAction,
        identity: DeviceInstallationIdentity,
        nonce: String,
        includeRegistration: Boolean = false,
        extra: (JsonObject) -> Unit = {},
    ): ServerAnswer {
        val body =
            JsonObject().apply {
                addProperty("deviceId", identity.deviceId)
                addProperty("nonce", nonce)
                addProperty("proof", identity.proof(action, nonce))
                // Reported support information only; it never participates in licence decisions.
                // Sent on validation as well as registration so existing installs fill the admin
                // panel the next time they open the app.
                add("deviceProfile", WindowsDeviceProfile.report())
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
                    SignedLicense(
                        payload =
                            json?.get("payload")?.asString
                                ?: return@use ServerAnswer.Refused(response.code, "bad_response"),
                        signatureBase64 =
                            json.get("signature")?.asString
                                ?: return@use ServerAnswer.Refused(response.code, "bad_response"),
                    ),
                )
            }
        }.getOrDefault(ServerAnswer.Unreachable)
    }

    private fun freshNonce(): String {
        val bytes = ByteArray(NONCE_BYTES)
        RANDOM.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val NONCE_BYTES = 16
        val JSON = "application/json; charset=utf-8".toMediaType()
        val RANDOM = SecureRandom()

        fun defaultHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .callTimeout(8, TimeUnit.SECONDS)
                .build()
    }
}

private data class LiveAnswer(val license: SignedLicense, val nonce: String)

private sealed interface ServerAnswer {
    data class Signed(val license: SignedLicense) : ServerAnswer
    data class Refused(val status: Int, val code: String) : ServerAnswer
    data object Unreachable : ServerAnswer
}

/** Injectable only so the protocol and offline paths can be tested without a production request. */
data class LicenseServerConfiguration(
    val registerUrl: String,
    val validateUrl: String,
    val redeemUrl: String,
    val publicKeyBase64: String,
    /**
     * Last and defaulted, deliberately.
     *
     * Describing a key is a fixed endpoint on the same server, so requiring every caller to repeat
     * it would only be a way to get it wrong. Placing it after the required parameters keeps the
     * positional calls that already exist compiling — inserting it in the middle silently shifted
     * publicKeyBase64 in a test that passes four arguments by position.
     */
    val keyInfoUrl: String = LicenseEndpoints.KEY_INFO,
) {
    val isConfigured: Boolean
        get() =
            publicKeyBase64.isNotBlank() &&
                listOf(registerUrl, validateUrl, redeemUrl, keyInfoUrl).all(String::isNotBlank)

    companion object {
        fun production(): LicenseServerConfiguration =
            LicenseServerConfiguration(
                registerUrl = LicenseEndpoints.REGISTER,
                validateUrl = LicenseEndpoints.VALIDATE,
                redeemUrl = LicenseEndpoints.REDEEM,
                keyInfoUrl = LicenseEndpoints.KEY_INFO,
                publicKeyBase64 = LicenseEndpoints.SERVER_PUBLIC_KEY,
            )
    }
}

/**
 * What the server says this machine would pay.
 *
 * [label] arrives formatted, so the client never has to know how a currency is punctuated: "R$ 99,90"
 * and "€9,90" differ in symbol, separator and placement, and reproducing that in four languages is
 * work the server has already done once.
 */
data class PriceQuote(
    val label: String,
    val currency: String,
    val termDays: Int,
)

/** The result consumed by the future blocking screen. No hardware or OS account data is exposed. */
data class LicenseStatus(
    val decision: LicenseDecision,
    val deviceId: String,
    /** True when this answer came from disk rather than the server. */
    val offline: Boolean = false,
    /** True when the local clock moved backwards beyond the accepted tolerance. */
    val clockSuspect: Boolean = false,
) {
    val allowsUse: Boolean get() = decision.allowsPlayback

    val trialRemaining: Duration?
        get() = (decision as? LicenseDecision.Allowed)?.takeIf { it.isTrial }?.remaining

    /** How long is left, whether this is a trial or a paid licence. Null when there is no end date. */
    val remaining: Duration?
        get() = (decision as? LicenseDecision.Allowed)?.remaining

    val isTrial: Boolean
        get() = (decision as? LicenseDecision.Allowed)?.isTrial == true

    /**
     * Whole days left, rounded up.
     *
     * Up rather than down because a licence with eleven hours to run has a day left, not zero — and
     * "0 days" beside a working app reads as a fault.
     */
    val daysRemaining: Long?
        get() = remaining?.let { left ->
            when {
                left.isNegative() || left == Duration.ZERO -> 0L
                // Rounded up from minutes, not hours: five minutes left is still a day remaining,
                // and `toHours()` truncates it to zero — which would print "0 dias" beside an app
                // that plainly still works.
                else -> (left.inWholeMinutes + 1439) / 1440
            }
        }

    val blockReason: LicenseBlockReason?
        get() = (decision as? LicenseDecision.Blocked)?.reason
}

/**
 * What the server says a key is, without spending it.
 *
 * Deliberately says nothing about *which* device holds a key that is in use — that is another
 * customer's business, and returning it would turn a mistyped code into a disclosure.
 */
data class KeyInfo(
    val state: KeyState,
    /** How many days it grants, or null when the server did not say. */
    val grantDays: Int?,
)

enum class KeyState {
    /** Never redeemed: it will work here. */
    AVAILABLE,

    /** Already redeemed by *this* device, which may redeem it again. */
    YOURS,

    /** Held by another device. It will not work here. */
    IN_USE,

    EXPIRED,
    ;

    companion object {
        fun from(wire: String): KeyState? =
            when (wire) {
                "available" -> AVAILABLE
                "yours" -> YOURS
                "in_use" -> IN_USE
                "expired" -> EXPIRED
                // An unknown state from a newer server is treated as "cannot say" rather than
                // guessed at, so the screen falls back to simply letting the key be tried.
                else -> null
            }
    }
}
