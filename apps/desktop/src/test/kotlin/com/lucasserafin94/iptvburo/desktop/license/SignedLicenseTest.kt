package com.lucasserafin94.iptvburo.desktop.license

import com.lucasserafin94.iptvburo.domain.model.EntitlementState
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * The signature check, which is the only thing standing between a stored file and a free licence.
 *
 * Without it, extending a trial is a text edit. So each attack is written out as a test: change the
 * dates, drop the signature, replay someone else's licence, sign with a different key. A pass here
 * is not "the parser works" — it is "these specific attempts fail".
 *
 * The key pairs are generated per test. No real key material is in this repository.
 */
class SignedLicenseTest {
    private val keyPair: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val publicKeyBase64: String = Base64.getEncoder().encodeToString(keyPair.public.encoded)
    private val deviceId = "FP86-XARB-9JZW"

    private fun sign(payload: String): String {
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(keyPair.private)
        signer.update(payload.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(signer.sign())
    }

    private fun payload(
        device: String = deviceId,
        state: String = "TRIAL",
        trialEndsAt: String? = "2026-08-15T12:00:00Z",
        expiresAt: String? = null,
        serverTime: String = "2026-08-08T12:00:00Z",
    ): String =
        buildString {
            append("""{"deviceId":"$device","state":"$state","serverTime":"$serverTime"""")
            trialEndsAt?.let { append(""","trialEndsAt":"$it"""") }
            expiresAt?.let { append(""","expiresAt":"$it"""") }
            append("}")
        }

    /** The ordinary case, so the failures below mean something. */
    @Test
    fun `a genuine licence verifies`() {
        val body = payload()
        val licence = SignedLicense(payload = body, signatureBase64 = sign(body))

        val verified = assertNotNull(licence.verified(publicKeyBase64, deviceId))

        assertEquals(deviceId, verified.deviceId)
        assertEquals(EntitlementState.TRIAL, verified.state)
        assertEquals(Instant.parse("2026-08-15T12:00:00Z"), verified.trialEndsAt)
        assertEquals(Instant.parse("2026-08-08T12:00:00Z"), verified.serverTimeAt)
    }

    /**
     * The obvious attack: edit the expiry in the stored file.
     *
     * The signature covers the exact bytes, so any change at all invalidates it.
     */
    @Test
    fun `an edited expiry is rejected`() {
        val body = payload(trialEndsAt = "2026-08-15T12:00:00Z")
        val signature = sign(body)

        val tampered = body.replace("2026-08-15", "2099-01-01")
        val licence = SignedLicense(payload = tampered, signatureBase64 = signature)

        assertNull(licence.verified(publicKeyBase64, deviceId), "a rewritten date must not verify")
    }

    /** Promoting a trial to a paid licence is the same edit, and fails the same way. */
    @Test
    fun `an edited state is rejected`() {
        val body = payload(state = "TRIAL")
        val signature = sign(body)

        val tampered = body.replace("TRIAL", "ACTIVE")

        assertNull(SignedLicense(tampered, signature).verified(publicKeyBase64, deviceId))
    }

    /**
     * A genuine licence belonging to somebody else.
     *
     * This is what copying an install directory produces: the signature is real, the document is
     * real, and it is simply not for this machine.
     */
    @Test
    fun `another device's licence is rejected`() {
        val body = payload(device = "AAAA-BBBB-CCCC")
        val licence = SignedLicense(body, sign(body))

        assertNull(
            licence.verified(publicKeyBase64, deviceId),
            "a licence issued for another machine must not work here",
        )
    }

    /** A signature from a key of the attacker's own must not be accepted. */
    @Test
    fun `a licence signed by a different key is rejected`() {
        val otherKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val body = payload()
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(otherKey.private)
        signer.update(body.toByteArray(StandardCharsets.UTF_8))
        val foreignSignature = Base64.getEncoder().encodeToString(signer.sign())

        assertNull(SignedLicense(body, foreignSignature).verified(publicKeyBase64, deviceId))
    }

    @Test
    fun `an empty or malformed signature is rejected`() {
        val body = payload()

        assertNull(SignedLicense(body, "").verified(publicKeyBase64, deviceId))
        assertNull(SignedLicense(body, "not base64!!").verified(publicKeyBase64, deviceId))
        assertNull(SignedLicense(body, Base64.getEncoder().encodeToString(ByteArray(64))).verified(publicKeyBase64, deviceId))
    }

    @Test
    fun `a malformed payload is rejected`() {
        val body = "this is not json"

        assertNull(SignedLicense(body, sign(body)).verified(publicKeyBase64, deviceId))
    }

    /** A document without a server time cannot be trusted about time, so it is not trusted at all. */
    @Test
    fun `a licence with no server time is rejected`() {
        val body = """{"deviceId":"$deviceId","state":"ACTIVE"}"""

        assertNull(SignedLicense(body, sign(body)).verified(publicKeyBase64, deviceId))
    }

    /**
     * A state this build has never heard of degrades rather than crashes.
     *
     * A server that grows a new state must not brick every client already installed.
     */
    @Test
    fun `an unknown state becomes unavailable`() {
        val body = payload(state = "SOMETHING_NEW")

        val verified = assertNotNull(SignedLicense(body, sign(body)).verified(publicKeyBase64, deviceId))

        assertEquals(EntitlementState.UNAVAILABLE, verified.state)
    }

    /** A paid licence carries an expiry rather than a trial end. */
    @Test
    fun `a paid licence carries its expiry`() {
        val body = payload(state = "ACTIVE", trialEndsAt = null, expiresAt = "2028-08-07T12:00:00Z")

        val verified = assertNotNull(SignedLicense(body, sign(body)).verified(publicKeyBase64, deviceId))

        assertEquals(EntitlementState.ACTIVE, verified.state)
        assertEquals(Instant.parse("2028-08-07T12:00:00Z"), verified.expiresAt)
        assertNull(verified.trialEndsAt)
    }
}
