package com.lucasserafin94.iptvburo.desktop.security

import com.sun.jna.Platform
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Local secrets survive a round trip, on whichever system the tests run.
 *
 * On Windows this is DPAPI; on macOS it is the Keychain-held key, which is the path every saved
 * account and the licence identity depend on there.
 */
class LocalSecretProtectorTest {
    @Test
    fun `a secret comes back as it went in`() {
        if (!Platform.isWindows() && !Platform.isMac()) return
        assertTrue(LocalSecretProtector.isAvailable)
        val secret = "user:pass@server".toByteArray()

        val sealed = LocalSecretProtector.protect(secret)

        assertFalse(sealed.contentEquals(secret))
        assertContentEquals(secret, LocalSecretProtector.unprotect(sealed))
    }

    @Test
    fun `the macOS seal refuses a tampered blob`() {
        val key = ByteArray(32).also(SecureRandom()::nextBytes)
        val sealed = LocalSecretProtector.seal(key, "secret".toByteArray())
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 1).toByte()

        assertFailsWith<Exception> { LocalSecretProtector.open(key, sealed) }
    }

    @Test
    fun `the macOS seal opens with its own key`() {
        val key = ByteArray(32).also(SecureRandom()::nextBytes)

        assertContentEquals("secret".toByteArray(), LocalSecretProtector.open(key, LocalSecretProtector.seal(key, "secret".toByteArray())))
    }
}
