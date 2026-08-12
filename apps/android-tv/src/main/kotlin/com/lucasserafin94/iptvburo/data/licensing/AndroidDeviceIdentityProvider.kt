package com.lucasserafin94.iptvburo.data.licensing

import android.content.Context
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
                ?: UUID.randomUUID().toString().also { generated ->
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

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "iptv_buro_device_signing_v1"
    private const val PREFERENCES_NAME = "device_identity"
    private const val KEY_INSTALLATION_ID = "installation_id"
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
