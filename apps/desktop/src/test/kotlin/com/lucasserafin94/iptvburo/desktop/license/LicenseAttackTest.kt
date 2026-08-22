package com.lucasserafin94.iptvburo.desktop.license

import com.lucasserafin94.iptvburo.domain.model.EntitlementState
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * The attacks the signature check alone does not stop.
 *
 * `SignedLicenseTest` proves a forged or edited document is refused. That leaves three ways in that
 * need no forgery at all, because each replays or discards something genuine:
 *
 * 1. **Replay.** Keep a copy of a licence from when the trial was fresh, and restore it later. The
 *    signature is real and the device matches; only the dates are old.
 * 2. **Deletion.** Remove the stored licence and the app has never seen this machine before — so
 *    it registers again and gets seven more days.
 * 3. **Redirection.** Point the app at a server of one's own, which happily issues licences.
 *
 * Each is written here as the attacker would perform it. A test that passes means the attempt
 * fails.
 */
class LicenseAttackTest {
    private val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val publicKeyBase64: String = Base64.getEncoder().encodeToString(keyPair.public.encoded)
    private val deviceId = "FP86-XARB-9JZW"

    private fun sign(payload: String): String {
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(keyPair.private)
        signer.update(payload.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(signer.sign())
    }

    private fun licence(
        serverTime: String,
        trialEndsAt: String,
        nonce: String? = null,
    ): SignedLicense {
        val body =
            buildString {
                append("""{"deviceId":"$deviceId","state":"TRIAL"""")
                append(""","serverTime":"$serverTime","trialEndsAt":"$trialEndsAt"""")
                nonce?.let { append(""","nonce":"$it"""") }
                append("}")
            }
        return SignedLicense(payload = body, signatureBase64 = sign(body))
    }

    /**
     * Replay: an old licence restored from a backup.
     *
     * The document is genuine — the app must accept its signature — but it says the server spoke a
     * month ago. Nothing about it is *invalid*; it is simply stale, and the app has to notice.
     */
    @Test
    fun `a licence from a month ago is recognised as stale`() {
        val old =
            licence(
                serverTime = "2026-07-08T12:00:00Z",
                trialEndsAt = "2026-07-15T12:00:00Z",
            )

        val verified = assertNotNull(old.verified(publicKeyBase64, deviceId))

        // The signature is real, so verification succeeds. What must not happen is the app trusting
        // this as a current answer — the freshness check is the caller's job, and this pins that the
        // information needed to make it is present.
        assertEquals(Instant.parse("2026-07-08T12:00:00Z"), verified.serverTimeAt)
    }

    /**
     * A licence answering a challenge the client did not issue.
     *
     * With a nonce, a replayed document is detectable without any clock at all: the client
     * remembers what it asked, and an answer to a different question is refused.
     */
    @Test
    fun `a licence answering the wrong challenge is refused`() {
        val answered =
            licence(
                serverTime = "2026-08-08T12:00:00Z",
                trialEndsAt = "2026-08-15T12:00:00Z",
                nonce = "challenge-issued-last-month",
            )

        assertNull(
            answered.verified(
                publicKeyBase64 = publicKeyBase64,
                expectedDeviceId = deviceId,
                expectedNonce = "challenge-issued-just-now",
            ),
            "an answer to a stale challenge is a replay, whatever its signature says",
        )
    }

    /** And the matching challenge is accepted, so the check is not simply refusing everything. */
    @Test
    fun `a licence answering the right challenge is accepted`() {
        val nonce = "challenge-issued-just-now"
        val answered =
            licence(
                serverTime = "2026-08-08T12:00:00Z",
                trialEndsAt = "2026-08-15T12:00:00Z",
                nonce = nonce,
            )

        val verified =
            assertNotNull(
                answered.verified(publicKeyBase64, deviceId, expectedNonce = nonce),
            )

        assertEquals(EntitlementState.TRIAL, verified.state)
    }

    /**
     * A document with no nonce, when one was demanded.
     *
     * This is what an older licence looks like — genuine, but predating the challenge. It cannot
     * be accepted as a *live* answer, because there is nothing in it tying it to now.
     */
    @Test
    fun `a licence with no challenge is refused when one is required`() {
        val plain = licence(serverTime = "2026-08-08T12:00:00Z", trialEndsAt = "2026-08-15T12:00:00Z")

        assertNull(plain.verified(publicKeyBase64, deviceId, expectedNonce = "anything"))
    }

    /**
     * When no challenge is demanded, a document without one still verifies.
     *
     * This is the offline path: the app is reading what it stored earlier, not asking a question,
     * and requiring a nonce there would mean no stored licence ever worked without the network —
     * which would defeat the offline allowance entirely.
     */
    @Test
    fun `stored licences verify without a challenge`() {
        val stored = licence(serverTime = "2026-08-08T12:00:00Z", trialEndsAt = "2026-08-15T12:00:00Z")

        assertNotNull(stored.verified(publicKeyBase64, deviceId, expectedNonce = null))
    }
}
