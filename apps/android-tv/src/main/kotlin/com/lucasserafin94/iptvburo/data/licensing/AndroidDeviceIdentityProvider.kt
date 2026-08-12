package com.lucasserafin94.iptvburo.data.licensing

import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.util.UUID

enum class AndroidDeviceProofAction(val wireValue: String) {
    REGISTER("register"),
    VALIDATE("validate"),
    REDEEM("redeem"),
}

/**
 * The stable Android installation identity and the one operation allowed with its private key.
 *
 * The private key never leaves Android Keystore. The Worker accepts fixed-width IEEE P1363 ECDSA
 * signatures, while Android's platform provider returns ASN.1 DER, so [proof] converts only that
 * public signature representation before sending it.
 */
class AndroidDeviceInstallationIdentity internal constructor(
    val installationId: String,
    val deviceId: String,
    val publicKeyDerBase64: String,
    private val privateKey: PrivateKey,
) {
    fun proof(action: AndroidDeviceProofAction, nonce: String): String {
        val message = canonicalDeviceProof(action, deviceId, nonce).toByteArray(StandardCharsets.UTF_8)
        return signCanonical(message)
    }

    fun googlePlayPurchaseProof(
        nonce: String,
        purchaseTokenHash: String,
        accountId: String,
    ): String {
        val message =
            canonicalGooglePlayPurchaseProof(
                deviceId = deviceId,
                nonce = nonce,
                purchaseTokenHash = purchaseTokenHash,
                accountId = accountId,
            ).toByteArray(StandardCharsets.UTF_8)
        return signCanonical(message)
    }

    private fun signCanonical(message: ByteArray): String {
        val derSignature =
            Signature.getInstance(ECDSA_SIGNATURE).run {
                initSign(privateKey)
                update(message)
                sign()
            }
        val p1363 = derEcdsaToP1363(derSignature)
        return Base64.encodeToString(p1363, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    override fun toString(): String =
        "AndroidDeviceInstallationIdentity(deviceId=$deviceId, installationId=<redacted>, publicKey=<redacted>)"
}

object AndroidDeviceIdentityProvider {
    fun getOrCreate(context: Context): AndroidDeviceInstallationIdentity {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val installationId =
            preferences.getString(KEY_INSTALLATION_ID, null)
                ?: stableInstallationId(context).also { generated ->
                    preferences.edit().putString(KEY_INSTALLATION_ID, generated).apply()
                }
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).apply {
                initialize(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                    ).setDigests(KeyProperties.DIGEST_SHA256).build(),
                )
            }.generateKeyPair()
        }
        val publicKey = requireNotNull(keyStore.getCertificate(KEY_ALIAS)?.publicKey?.encoded)
        val privateKey = requireNotNull(keyStore.getKey(KEY_ALIAS, null) as? PrivateKey)
        return AndroidDeviceInstallationIdentity(
            installationId = installationId,
            deviceId = deriveDeviceId(publicKey, installationId),
            publicKeyDerBase64 = Base64.encodeToString(publicKey, Base64.NO_WRAP),
            privateKey = privateKey,
        )
    }

    /**
     * An installation id that survives the app being uninstalled.
     *
     * This is the fix for a paid licence being lost on reinstall. The id used to be a random UUID
     * in SharedPreferences, and the device id was derived from it *and* the Keystore public key —
     * both of which Android deletes with the app. Reinstalling therefore produced a device the
     * server had never seen, and a user who had paid for thirty days was dropped back onto the
     * seven-day trial. `allowBackup="false"`, which is right for a credential store, guarantees
     * neither comes back.
     *
     * `ANDROID_ID` is the one identifier available without a permission that outlives an
     * uninstall: it is scoped to the app's signing key, the user and the device, so it is stable
     * across reinstalls of *this* app while telling us nothing about any other app and giving no
     * cross-app tracking handle. A factory reset changes it, which matches the user's own
     * expectation that a wiped device is a new device.
     *
     * It is hashed with a fixed salt rather than sent as-is, so the value that leaves the phone
     * cannot be correlated with `ANDROID_ID` observed anywhere else.
     *
     * **The result must be a v4-shaped UUID.** The Worker's `validInstallationId` matches exactly
     * that pattern and answers `bad_identity` for anything else, so a plain hash — which is what
     * this returned at first — makes every registration fail with no hint as to why. The digest is
     * therefore folded into UUID form rather than sent raw. It is a hash wearing a UUID's clothes:
     * derived, not random, which is the whole point.
     *
     * The random UUID stays as the fallback for the case where the platform returns nothing: a
     * device that cannot be identified stably is still usable, it simply loses the reinstall
     * grace, which is strictly better than refusing to run.
     */
    private fun stableInstallationId(context: Context): String {
        val androidId =
            runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            }.getOrNull()

        // Some devices historically returned this well-known broken value for every install.
        if (androidId.isNullOrBlank() || androidId == BROKEN_ANDROID_ID) {
            return UUID.randomUUID().toString()
        }

        val digest =
            MessageDigest.getInstance("SHA-256").digest(
                (INSTALLATION_ID_SALT + androidId).toByteArray(StandardCharsets.UTF_8),
            )
        return digest.toVersion4Uuid()
    }

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "iptv_buro_device_signing_v1"
    private const val PREFERENCES_NAME = "device_identity"
    private const val KEY_INSTALLATION_ID = "installation_id"

    /** Domain separation, so the value sent is not `ANDROID_ID` under another name. */
    private const val INSTALLATION_ID_SALT = "iptvburo-installation-v1\n"

    /** A value several buggy devices returned for every app; treated as no answer at all. */
    private const val BROKEN_ANDROID_ID = "9774d56d682e549c"
}

/**
 * Formats the first 16 bytes of a digest as a v4-shaped UUID.
 *
 * Deliberately not a real random UUID: the value has to be *derived*, so that the same phone
 * produces the same installation id after a reinstall. It only has to *look* like a v4 UUID,
 * because that is the shape the Worker's `validInstallationId` accepts, and a value it rejects
 * fails registration with `bad_identity` and no explanation the user can act on.
 *
 * The version and variant nibbles are forced exactly as RFC 4122 lays them out. Doing so discards
 * six bits of the digest, leaving 122 — far more than enough to keep two phones apart.
 */
internal fun ByteArray.toVersion4Uuid(): String {
    val bytes = copyOf(16)
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte() // version 4
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte() // variant 10xx
    val hex = bytes.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    return buildString {
        append(hex, 0, 8).append('-')
        append(hex, 8, 12).append('-')
        append(hex, 12, 16).append('-')
        append(hex, 16, 20).append('-')
        append(hex, 20, 32)
    }
}

internal fun canonicalDeviceProof(
    action: AndroidDeviceProofAction,
    deviceId: String,
    nonce: String,
): String = "iptvburo-device-proof-v1\n${action.wireValue}\n$deviceId\n$nonce"

internal fun canonicalGooglePlayPurchaseProof(
    deviceId: String,
    nonce: String,
    purchaseTokenHash: String,
    accountId: String,
): String = "iptvburo-google-play-purchase-v1\n$deviceId\n$nonce\n$purchaseTokenHash\n$accountId"

/**
 * The device id, derived from the pinned public key and the installation id.
 *
 * **Must match `deriveDeviceId` in the Worker exactly.** Registration recomputes this server-side
 * and refuses `bad_identity` when the two disagree, so a change here that is not mirrored there
 * stops every new device from registering at all. That is not hypothetical: dropping the public key
 * from this derivation — an attempt to make the id survive a reinstall — did precisely that, and it
 * was only caught by watching a real phone fail to register.
 *
 * The id is *not* meant to survive an uninstall. The Keystore key is destroyed with the app, so a
 * reinstalled device legitimately gets a new id; what reunites it with its history is the
 * `machine_anchor`, which is the installation id and is stable across reinstalls. Keeping identity
 * and recovery in separate fields is the server's design, and it is the right one.
 */
internal fun deriveDeviceId(publicKey: ByteArray, installationId: String): String {
    val digest =
        MessageDigest.getInstance("SHA-256").digest(
            publicKey + installationId.toByteArray(StandardCharsets.UTF_8),
        )
    var bitBuffer = 0
    var bitCount = 0
    val output = StringBuilder(12)
    for (byte in digest) {
        bitBuffer = (bitBuffer shl 8) or (byte.toInt() and 0xFF)
        bitCount += 8
        while (bitCount >= 5 && output.length < 12) {
            bitCount -= 5
            output.append(DEVICE_ID_ALPHABET[(bitBuffer shr bitCount) and 31])
        }
        if (output.length == 12) break
    }
    return output.toString().chunked(4).joinToString("-")
}

/** Converts a strict DER ECDSA signature into the 64-byte P-256 format Web Crypto verifies. */
internal fun derEcdsaToP1363(der: ByteArray, componentSize: Int = 32): ByteArray {
    require(componentSize > 0)
    var cursor = 0

    fun readByte(): Int {
        require(cursor < der.size) { "Truncated ECDSA signature." }
        return der[cursor++].toInt() and 0xFF
    }

    fun readLength(): Int {
        val first = readByte()
        if (first and 0x80 == 0) return first
        val octets = first and 0x7F
        require(octets in 1..2) { "Unsupported DER length." }
        var length = 0
        repeat(octets) { length = (length shl 8) or readByte() }
        return length
    }

    fun readInteger(): ByteArray {
        require(readByte() == 0x02) { "Expected a DER integer." }
        val length = readLength()
        require(length > 0 && cursor + length <= der.size) { "Invalid DER integer length." }
        var start = cursor
        val end = cursor + length
        cursor = end
        while (start < end - 1 && der[start] == 0.toByte()) start += 1
        val valueLength = end - start
        require(valueLength <= componentSize) { "ECDSA component is too large." }
        return ByteArray(componentSize).also { output ->
            der.copyInto(output, destinationOffset = componentSize - valueLength, startIndex = start, endIndex = end)
        }
    }

    require(readByte() == 0x30) { "Expected a DER sequence." }
    val sequenceLength = readLength()
    require(sequenceLength == der.size - cursor) { "Invalid DER sequence length." }
    val r = readInteger()
    val s = readInteger()
    require(cursor == der.size) { "Unexpected data after ECDSA signature." }
    return r + s
}

private const val ECDSA_SIGNATURE = "SHA256withECDSA"
private const val DEVICE_ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
