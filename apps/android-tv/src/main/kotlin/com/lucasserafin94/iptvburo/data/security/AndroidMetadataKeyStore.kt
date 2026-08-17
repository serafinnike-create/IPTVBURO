package com.lucasserafin94.iptvburo.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Stores each profile's optional TMDB v3 key encrypted under an Android Keystore AES key. */
@Singleton
class AndroidMetadataKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) : MetadataKeyStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun save(profileId: String, apiKey: String) {
        requireProfileId(profileId)
        val clean = apiKey.trim()
        if (clean.isEmpty()) {
            preferences.edit().remove(profileId).commit()
            return
        }
        require(clean.length <= MAX_KEY_LENGTH) { "Metadata key is too long." }
        val plaintext = clean.toByteArray(StandardCharsets.UTF_8)
        try {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(profileId.toByteArray(StandardCharsets.UTF_8))
            val encrypted = cipher.doFinal(plaintext)
            val stored =
                listOf(
                    FORMAT_VERSION,
                    Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
                    Base64.encodeToString(encrypted, Base64.NO_WRAP),
                ).joinToString(SEPARATOR)
            check(preferences.edit().putString(profileId, stored).commit()) {
                "Metadata key could not be stored."
            }
        } finally {
            plaintext.fill(0)
        }
    }

    @Synchronized
    override fun read(profileId: String): String? {
        requireProfileId(profileId)
        val stored = preferences.getString(profileId, null) ?: return null
        val parts = stored.split(SEPARATOR)
        if (parts.size != 3 || parts[0] != FORMAT_VERSION) return null
        val plaintext =
            runCatching {
                val cipher = Cipher.getInstance(CIPHER)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    GCMParameterSpec(GCM_TAG_BITS, Base64.decode(parts[1], Base64.NO_WRAP)),
                )
                cipher.updateAAD(profileId.toByteArray(StandardCharsets.UTF_8))
                cipher.doFinal(Base64.decode(parts[2], Base64.NO_WRAP))
            }.getOrNull() ?: return null
        return try {
            String(plaintext, StandardCharsets.UTF_8).takeIf(String::isNotBlank)
        } finally {
            plaintext.fill(0)
        }
    }

    override fun isConfigured(profileId: String): Boolean = read(profileId) != null

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun requireProfileId(profileId: String) {
        require(profileId.isNotBlank() && profileId.length <= 128) { "profileId is invalid" }
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "iptv_buro_metadata_keys_v1"
        const val PREFERENCES_NAME = "encrypted_metadata_keys"
        const val CIPHER = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = "v1"
        const val SEPARATOR = "."
        const val GCM_TAG_BITS = 128
        const val MAX_KEY_LENGTH = 512
    }
}

interface MetadataKeyStore {
    fun save(profileId: String, apiKey: String)
    fun read(profileId: String): String?
    fun isConfigured(profileId: String): Boolean

    /**
     * The key every profile falls back to.
     *
     * Stored under a reserved id rather than in a second file: it is the same secret with the same
     * protection, and one encrypted store is one thing to get right.
     *
     * The three levels exist because each answers a different need. The bundled key makes the app
     * work out of the box; this shared key is a household that pasted its own; a per-profile key is
     * for somebody who wants their own TMDb quota used, since TMDb rate-limits per key and several
     * heavy users on one key throttle each other.
     */
    fun saveShared(apiKey: String) = save(SHARED_PROFILE_ID, apiKey)

    fun readShared(): String? = read(SHARED_PROFILE_ID)

    /**
     * The OMDb key, which is what fills the critics' row.
     *
     * A different service with a different key, filed under its own reserved id rather than in a
     * second store: it is the same kind of secret with the same protection needs, and one encrypted
     * store is one thing to get right. Device-wide rather than per profile — OMDb rate-limits per
     * key by day, and there is nothing personal about a Tomatometer.
     */
    fun saveCritics(apiKey: String) = save(CRITICS_PROFILE_ID, apiKey)

    fun readCritics(): String? = read(CRITICS_PROFILE_ID)

    companion object {
        /**
         * Reserved id for the household key.
         *
         * Not a UUID, so it can never collide with a real profile: profile ids are generated by
         * `UUID.randomUUID()` and none of them looks like this.
         */
        const val SHARED_PROFILE_ID = "__shared__"

        /** Reserved id for the OMDb key. Reserved on the same grounds as [SHARED_PROFILE_ID]. */
        const val CRITICS_PROFILE_ID = "__critics__"
    }
}
