package com.lucasserafin94.iptvburo.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-GCM under a dedicated Android Keystore key, distinct from
 * [AndroidKeystoreSourceConnectionStore]'s: that one guards one credential blob per source, this
 * one guards up to four fields on every catalogue row.
 *
 * Each value gets its own random IV — required for GCM, and enforced here the same way the
 * source connection store enforces it, via `setRandomizedEncryptionRequired(true)` rather than by
 * convention.
 */
@Singleton
class AndroidKeystoreChannelFieldCipher @Inject constructor() : ChannelFieldCipher {
    override fun encrypt(value: String?): String? {
        if (value == null) return null
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return buildString {
            append(PREFIX)
            append(Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            append(SEPARATOR)
            append(Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }
    }

    override fun decrypt(value: String?): String? {
        if (value == null || !value.startsWith(PREFIX)) return value
        return runCatching {
            val body = value.removePrefix(PREFIX)
            val separatorIndex = body.indexOf(SEPARATOR)
            require(separatorIndex >= 0)
            val iv = Base64.decode(body.substring(0, separatorIndex), Base64.NO_WRAP)
            val encrypted = Base64.decode(body.substring(separatorIndex + 1), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        }.getOrDefault(value)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "iptv_buro_channel_fields_v1"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"

        /**
         * Marks a value this cipher produced, so [decrypt] can tell it apart from a legacy
         * plaintext URL — which is never a valid Base64-and-dot pair starting with this exact
         * prefix — without needing a separate flag column.
         */
        const val PREFIX = "cf1:"
        const val SEPARATOR = "."
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_BITS = 128
    }
}
