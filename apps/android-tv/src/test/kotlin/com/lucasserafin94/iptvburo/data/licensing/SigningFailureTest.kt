package com.lucasserafin94.iptvburo.data.licensing

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Signing a request can fail, and the licence client must survive it.
 *
 * Every request to the licence server carries a P-256 signature made in Android Keystore. That is
 * two operations that can throw: the Keystore call itself, and parsing the DER signature it returns
 * into the fixed-width form the Worker expects. A key invalidated by a biometric or lock-screen
 * change is the ordinary way it happens, and it happens to a user who did nothing wrong.
 *
 * Found by audit rather than by a report: three call sites built their JSON body — including the
 * signature — outside the `runCatching` that guarded the network call. The worst was the Google
 * Play submission, which runs *immediately after payment*: an exception there would crash the app
 * with the purchase made at Google and not yet reported, and the user looking at a charge and no
 * licence.
 *
 * These tests pin the parser's behaviour, which is the part that can be exercised without a device.
 * The guards themselves are asserted by reading the source, below.
 */
class SigningFailureTest {
    /**
     * The DER parser rejects malformed input by throwing, which is why callers must guard it.
     *
     * This is correct behaviour for the parser — a signature that cannot be converted is not
     * something to paper over — and it is exactly why the conversion cannot sit outside a guard.
     */
    @Test
    fun `a malformed signature is rejected rather than silently accepted`() {
        val notDer = "this is not a DER signature".toByteArray(StandardCharsets.UTF_8)

        assertThrows(IllegalArgumentException::class.java) {
            derEcdsaToP1363(notDer)
        }
    }

    @Test
    fun `a truncated signature is rejected`() {
        // A DER sequence header promising more bytes than are present.
        val truncated = byteArrayOf(0x30, 0x44, 0x02, 0x20)

        assertThrows(IllegalArgumentException::class.java) {
            derEcdsaToP1363(truncated)
        }
    }

    /**
     * The two call sites that build a body of their own must sign inside a guard.
     *
     * `keyState` and `submitGooglePlayPurchase` construct their own JSON and were doing it outside
     * the `runCatching` that guards the network call, so a Keystore failure threw straight out of
     * functions whose contracts are "returns null" and "returns Unreachable". `ask` is excluded
     * deliberately: its body is built inside the function, and all three of its callers wrap the
     * call in a guard of their own.
     *
     * Read from the source because the alternative needs a Keystore key that can be invalidated on
     * demand, which a unit test cannot arrange. Crude, and it catches exactly the mistake made.
     */
    @Test
    fun `the self-built bodies sign inside a guard`() {
        val source =
            java.nio.file.Files.readAllBytes(
                java.nio.file.Path.of(
                    "src/main/kotlin/com/lucasserafin94/iptvburo/data/licensing/AndroidLicenseClient.kt",
                ),
            ).toString(Charsets.UTF_8)

        listOf("override fun keyState(", "override fun submitGooglePlayPurchase(").forEach { entry ->
            val start = source.indexOf(entry)
            assertTrue("$entry not found; this test needs updating", start >= 0)

            val bodyAt = source.indexOf("val body =", start)
            assertTrue("no body built in $entry", bodyAt > 0)

            // The guard has to open on the body itself, not somewhere later.
            val opening = source.substring(bodyAt, bodyAt + 60)
            assertTrue(
                "$entry builds its signed body outside runCatching",
                "runCatching" in opening,
            )
        }
    }
}
