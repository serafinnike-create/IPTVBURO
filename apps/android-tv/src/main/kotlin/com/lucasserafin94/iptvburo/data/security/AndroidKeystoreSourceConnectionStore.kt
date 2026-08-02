package com.lucasserafin94.iptvburo.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.lucasserafin94.iptvburo.xtream.XtreamCredentials
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidKeystoreSourceConnectionStore @Inject constructor(
    @ApplicationContext context: Context,
) : SourceConnectionStore {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun saveXtream(
        sourceId: String,
        credentials: XtreamCredentials,
    ) {
        requireValidSourceId(sourceId)
        val plaintext = encode(credentials)
        try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(sourceId.toByteArray(StandardCharsets.UTF_8))
            val encrypted = cipher.doFinal(plaintext)
            val value =
                buildString {
                    append(FORMAT_VERSION)
                    append(SEPARATOR)
                    append(Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                    append(SEPARATOR)
                    append(Base64.encodeToString(encrypted, Base64.NO_WRAP))
                }
            if (!preferences.edit().putString(sourceId, value).commit()) {
                throw SourceConnectionStoreException(
                    "The encrypted source connection could not be saved.",
                )
            }
        } catch (error: SourceConnectionStoreException) {
            throw error
        } catch (error: Exception) {
            throw SourceConnectionStoreException(
                "The encrypted source connection could not be saved.",
                error,
            )
        } finally {
            plaintext.fill(0)
        }
    }

    @Synchronized
    override fun readXtream(sourceId: String): XtreamCredentials? {
        requireValidSourceId(sourceId)
        val stored = preferences.getString(sourceId, null) ?: return null
        return try {
            val parts = stored.split(SEPARATOR)
            if (parts.size != 3 || parts[0] != FORMAT_VERSION) {
                throw SourceConnectionStoreException(
                    "The encrypted source connection has an unsupported format.",
                )
            }
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[2], Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(sourceId.toByteArray(StandardCharsets.UTF_8))
            val plaintext = cipher.doFinal(encrypted)
            try {
                decode(plaintext)
            } finally {
                plaintext.fill(0)
            }
        } catch (error: SourceConnectionStoreException) {
            throw error
        } catch (error: Exception) {
            throw SourceConnectionStoreException(
                "The encrypted source connection could not be read.",
                error,
            )
        }
    }

    @Synchronized
    override fun remove(sourceId: String) {
        requireValidSourceId(sourceId)
        if (!preferences.edit().remove(sourceId).commit()) {
            throw SourceConnectionStoreException(
                "The encrypted source connection could not be removed.",
            )
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore =
            KeyStore.getInstance(ANDROID_KEY_STORE).apply {
                load(null)
            }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
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

    private fun encode(credentials: XtreamCredentials): ByteArray =
        ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.writeInt(PAYLOAD_VERSION)
                data.writeUtf8(credentials.serverUrl)
                data.writeUtf8(credentials.username)
                data.writeUtf8(credentials.password)
            }
            output.toByteArray()
        }

    private fun decode(bytes: ByteArray): XtreamCredentials =
        DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            val version = data.readInt()
            if (version != PAYLOAD_VERSION) {
                throw SourceConnectionStoreException(
                    "The encrypted source payload has an unsupported version.",
                )
            }
            XtreamCredentials(
                serverUrl = data.readUtf8(),
                username = data.readUtf8(),
                password = data.readUtf8(),
            )
        }

    private fun DataOutputStream.writeUtf8(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > MAX_FIELD_BYTES) {
            bytes.fill(0)
            throw SourceConnectionStoreException(
                "A source connection field exceeds the safety limit.",
            )
        }
        writeInt(bytes.size)
        write(bytes)
        bytes.fill(0)
    }

    private fun DataInputStream.readUtf8(): String {
        val size = readInt()
        if (size !in 0..MAX_FIELD_BYTES) {
            throw SourceConnectionStoreException(
                "An encrypted source connection field is invalid.",
            )
        }
        val bytes = ByteArray(size)
        readFully(bytes)
        return try {
            String(bytes, StandardCharsets.UTF_8)
        } finally {
            bytes.fill(0)
        }
    }

    private fun requireValidSourceId(sourceId: String) {
        require(sourceId.isNotBlank() && sourceId.length <= MAX_SOURCE_ID_LENGTH) {
            "sourceId is invalid"
        }
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "iptv_buro_source_connections_v1"
        const val PREFERENCES_NAME = "encrypted_source_connections"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = "v1"
        const val SEPARATOR = "."
        const val PAYLOAD_VERSION = 1
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_BITS = 128
        const val MAX_FIELD_BYTES = 16 * 1024
        const val MAX_SOURCE_ID_LENGTH = 128
    }
}
