package com.lucasserafin94.iptvburo.desktop.license

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The installation UUID, anchored to the machine instead of drawn at random.
 *
 * This closes the cheapest attack on the whole product. The id used to be `UUID.randomUUID()`, and
 * the device id derives from it, so deleting three files on disk made the app introduce itself as a
 * machine the server had never met — and it correctly granted a fresh seven-day trial. Repeatable
 * indefinitely, requiring no patching, no network knowledge, and no skill.
 *
 * Every server-side defence is keyed on the device id, so a new device id sidestepped all of them at
 * once. Anchoring the installation id is what makes the returning machine recognisable.
 */
class MachineAnchorTest {
    @Test
    fun `the same machine always yields the same installation id`() {
        val anchor = "9f4b1c2e-77a3-4c55-9c1e-2b8f0a4d6e11"

        assertEquals(
            MachineAnchor.installationUuid(anchor),
            MachineAnchor.installationUuid(anchor),
            "a machine that deletes its files must come back as itself",
        )
    }

    @Test
    fun `different machines yield different installation ids`() {
        assertNotEquals(
            MachineAnchor.installationUuid("machine-one"),
            MachineAnchor.installationUuid("machine-two"),
        )
    }

    /**
     * The result must satisfy the validation every read performs.
     *
     * `canonicalUuid` requires an RFC-4122 version 4 variant 2 UUID and is checked on every load, on
     * the wire, and by the server. A derived value that failed it would lock the app out of its own
     * identity — worse than the hole it was closing.
     */
    @Test
    fun `the derived value is a valid version 4 uuid`() {
        val derived = MachineAnchor.installationUuid("any-machine-value")!!

        val parsed = UUID.fromString(derived)
        assertEquals(4, parsed.version(), "the stored format requires a version 4 UUID")
        assertEquals(2, parsed.variant(), "the stored format requires variant 2")
        assertEquals(derived, canonicalUuid(derived), "it must survive the app's own validation")
    }

    /**
     * Whitespace and casing must not fork one machine into two identities.
     *
     * A registry value that comes back padded on one read and clean on the next would produce two
     * different machines from one — and the second would be granted a fresh trial.
     */
    @Test
    fun `surrounding whitespace does not change the identity`() {
        val clean = MachineAnchor.installationUuid("abc-123")

        assertEquals(clean, MachineAnchor.installationUuid("  abc-123  "))
        assertEquals(clean, MachineAnchor.installationUuid("\tabc-123\n"))
    }

    /**
     * No anchor means fall back, never fail.
     *
     * On a machine whose registry cannot be read the app must still run. Losing the anti-reset
     * property for that user is a far better outcome than refusing to start — and the caller
     * substitutes a random UUID, which is exactly the old behaviour.
     */
    @Test
    fun `an unreadable anchor yields nothing rather than throwing`() {
        assertNull(MachineAnchor.installationUuid(null))
        assertNull(MachineAnchor.installationUuid(""))
        assertNull(MachineAnchor.installationUuid("   "))
    }

    /**
     * The machine value cannot be recovered from what is sent.
     *
     * The GUID identifies the customer's computer and never leaves the function. What travels is a
     * salted SHA-256 of it, which is no more identifying than the random UUID it replaces — and this
     * test is what keeps a future change from quietly passing the raw value along.
     */
    @Test
    fun `the raw machine value never appears in the derived id`() {
        val secret = "4f8d2a17-9b3c-4e6f-a1d2-c3b4e5f60718"

        val derived = MachineAnchor.installationUuid(secret)!!

        assertTrue(secret !in derived, "the machine's own identifier must not be passed through")
        assertTrue(
            secret.substringBefore('-') !in derived,
            "not even a recognisable fragment of it",
        )
    }

    /**
     * A short or odd anchor still produces a usable id.
     *
     * The registry is not guaranteed to hold a well-formed GUID, and a value that produced an
     * invalid UUID would fail at the point of use rather than here.
     */
    @Test
    fun `an unusual anchor still produces a valid uuid`() {
        listOf("x", "0", "a".repeat(500), "ção-ünïcode-✓").forEach { odd ->
            val derived = MachineAnchor.installationUuid(odd)!!
            assertEquals(derived, canonicalUuid(derived), "failed for: $odd")
        }
    }
}
