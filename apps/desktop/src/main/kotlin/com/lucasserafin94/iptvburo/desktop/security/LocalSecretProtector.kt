package com.lucasserafin94.iptvburo.desktop.security

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.platform.win32.Crypt32Util
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts small local secrets — saved IPTV accounts, the licence identity — for the current OS user.
 *
 * Windows uses DPAPI, as it always has. macOS has no DPAPI, and every store that relied on it simply
 * did nothing there: an account was never remembered, and the licence could not make an identity at
 * all, so the app would have opened onto a locked licence screen. On macOS a random 256-bit key is
 * kept in the user's login Keychain and the data is sealed with AES-GCM; the key never touches disk
 * outside the Keychain, and another user on the same Mac cannot read it.
 *
 * Anything else reports [isAvailable] false, and the callers fall back exactly as they did before.
 */
object LocalSecretProtector {
    val isAvailable: Boolean
        get() =
            when {
                Platform.isWindows() -> true
                Platform.isMac() -> macKey() != null
                else -> false
            }

    fun protect(plaintext: ByteArray): ByteArray =
        when {
            Platform.isWindows() -> Crypt32Util.cryptProtectData(plaintext)
            Platform.isMac() -> seal(requireNotNull(macKey()) { "Keychain key unavailable" }, plaintext)
            else -> error("No local secret protection on this platform")
        }

    fun unprotect(protected: ByteArray): ByteArray =
        when {
            Platform.isWindows() -> Crypt32Util.cryptUnprotectData(protected)
            Platform.isMac() -> open(requireNotNull(macKey()) { "Keychain key unavailable" }, protected)
            else -> error("No local secret protection on this platform")
        }

    /** Read once per process: a Keychain query costs a round trip to securityd. */
    @Volatile
    private var cachedKey: ByteArray? = null

    private fun macKey(): ByteArray? {
        cachedKey?.let { return it }
        synchronized(this) {
            cachedKey?.let { return it }
            val key =
                runCatching {
                    MacKeychain.find(KEYCHAIN_SERVICE, KEYCHAIN_ACCOUNT)?.takeIf { it.size == KEY_BYTES }
                        ?: ByteArray(KEY_BYTES).also { fresh ->
                            SecureRandom().nextBytes(fresh)
                            check(MacKeychain.add(KEYCHAIN_SERVICE, KEYCHAIN_ACCOUNT, fresh)) { "Keychain refused the key" }
                        }
                }.getOrNull()
            cachedKey = key
            return key
        }
    }

    internal fun seal(
        key: ByteArray,
        plaintext: ByteArray,
    ): ByteArray {
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        val sealed = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(MAGIC.size + iv.size + sealed.size).put(MAGIC).put(iv).put(sealed).array()
    }

    internal fun open(
        key: ByteArray,
        protected: ByteArray,
    ): ByteArray {
        require(protected.size > MAGIC.size + IV_BYTES && protected.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "Not a sealed secret"
        }
        val iv = protected.copyOfRange(MAGIC.size, MAGIC.size + IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(protected, MAGIC.size + IV_BYTES, protected.size - MAGIC.size - IV_BYTES)
    }

    private const val KEYCHAIN_SERVICE = "IPTV BURO"
    private const val KEYCHAIN_ACCOUNT = "local-secret-key"
    private const val KEY_BYTES = 32
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    /** Marks a sealed blob, so a file written by another format is refused rather than misread. */
    private val MAGIC = byteArrayOf('B'.code.toByte(), 'S'.code.toByte(), 'K'.code.toByte(), 1)
}

/**
 * The two Keychain calls this app needs, through Security.framework.
 *
 * The generic-password functions are the long-standing C API every macOS version still ships; the
 * newer SecItem one needs CoreFoundation dictionaries built by hand, which is a great deal more JNA
 * for the same item in the same login Keychain.
 */
internal object MacKeychain {
    private interface Security : Library {
        fun SecKeychainFindGenericPassword(
            keychainOrArray: Pointer?,
            serviceNameLength: Int,
            serviceName: ByteArray,
            accountNameLength: Int,
            accountName: ByteArray,
            passwordLength: IntByReference,
            passwordData: PointerByReference,
            itemRef: Pointer?,
        ): Int

        fun SecKeychainAddGenericPassword(
            keychain: Pointer?,
            serviceNameLength: Int,
            serviceName: ByteArray,
            accountNameLength: Int,
            accountName: ByteArray,
            passwordLength: Int,
            passwordData: ByteArray,
            itemRef: Pointer?,
        ): Int

        fun SecKeychainItemFreeContent(
            attrList: Pointer?,
            data: Pointer?,
        ): Int
    }

    private val security: Security by lazy { Native.load("Security", Security::class.java) }

    fun find(
        service: String,
        account: String,
    ): ByteArray? {
        val serviceBytes = service.toByteArray(Charsets.UTF_8)
        val accountBytes = account.toByteArray(Charsets.UTF_8)
        val length = IntByReference()
        val data = PointerByReference()
        val status =
            security.SecKeychainFindGenericPassword(
                null,
                serviceBytes.size,
                serviceBytes,
                accountBytes.size,
                accountBytes,
                length,
                data,
                null,
            )
        if (status != 0) return null
        val pointer = data.value ?: return null
        return try {
            pointer.getByteArray(0, length.value)
        } finally {
            security.SecKeychainItemFreeContent(null, pointer)
        }
    }

    fun add(
        service: String,
        account: String,
        secret: ByteArray,
    ): Boolean {
        val serviceBytes = service.toByteArray(Charsets.UTF_8)
        val accountBytes = account.toByteArray(Charsets.UTF_8)
        return security.SecKeychainAddGenericPassword(
            null,
            serviceBytes.size,
            serviceBytes,
            accountBytes.size,
            accountBytes,
            secret.size,
            secret,
            null,
        ) == 0
    }
}
