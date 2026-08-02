package com.lucasserafin94.iptvburo.data.licensing

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.lucasserafin94.iptvburo.domain.model.DeviceIdentity
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID

object AndroidDeviceIdentityProvider {
    fun getOrCreate(context: Context): DeviceIdentity {
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
        val digest = MessageDigest.getInstance("SHA-256").digest(
            publicKey + installationId.toByteArray(StandardCharsets.UTF_8),
        )
        return DeviceIdentity(
            deviceId = digest.toBuroDeviceId(),
            publicKeyDerBase64 = Base64.encodeToString(publicKey, Base64.NO_WRAP),
        )
    }

    private fun ByteArray.toBuroDeviceId(): String {
        var bitBuffer = 0
        var bitCount = 0
        val output = StringBuilder(12)
        for (byte in this) {
            bitBuffer = (bitBuffer shl 8) or (byte.toInt() and 0xFF)
            bitCount += 8
            while (bitCount >= 5 && output.length < 12) {
                bitCount -= 5
                output.append(ALPHABET[(bitBuffer shr bitCount) and 31])
            }
            if (output.length == 12) break
        }
        return output.toString().chunked(4).joinToString("-")
    }

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "iptv_buro_device_signing_v1"
    private const val PREFERENCES_NAME = "device_identity"
    private const val KEY_INSTALLATION_ID = "installation_id"
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
}
