package com.lucasserafin94.iptvburo.data.licensing

import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The installation id has to be a v4-shaped UUID, because the Worker refuses anything else.
 *
 * This is the exact pattern from `validInstallationId` in `services/license-server/src/index.js`.
 * A value that does not match is answered with `bad_identity` (400) at registration, which the app
 * surfaces as "this device is not registered" — a message that tells the user nothing about the
 * real problem and that no amount of retrying can clear.
 *
 * That happened: the id was first derived as a raw Base64 digest, which is stable and unique and
 * still made every registration fail. No unit test caught it, because nothing checked the *shape*
 * against the server's rule. This is that check.
 */
class InstallationIdShapeTest {
    private val serverPattern =
        Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

    private fun digestOf(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))

    @Test
    fun `a derived id matches the pattern the worker accepts`() {
        listOf("iptvburo-installation-v1\n1cf4fab7a64ba15b", "another-device", "", "ç é 漢字")
            .forEach { source ->
                val id = digestOf(source).toVersion4Uuid()

                assertTrue("'$id' would be refused as bad_identity", serverPattern.matches(id))
            }
    }

    /**
     * Derived, not random: the same phone has to produce the same id after a reinstall.
     *
     * This is the property the whole licence-recovery fix rests on. A real `UUID.randomUUID()`
     * would also match the pattern above and would be useless here.
     */
    @Test
    fun `the same input always yields the same id`() {
        val first = digestOf("stable-input").toVersion4Uuid()
        val second = digestOf("stable-input").toVersion4Uuid()

        assertEquals(first, second)
        assertNotEquals(first, digestOf("different-input").toVersion4Uuid())
    }

    /** The version and variant nibbles are fixed, whatever the digest happened to contain. */
    @Test
    fun `version and variant are forced`() {
        val id = digestOf("whatever").toVersion4Uuid()

        assertEquals('4', id[14])
        assertTrue("variant nibble was '${id[19]}'", id[19] in "89ab")
    }
}
