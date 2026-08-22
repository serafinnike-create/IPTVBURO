package com.lucasserafin94.iptvburo.desktop.license

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lucasserafin94.iptvburo.domain.model.EntitlementState
import com.lucasserafin94.iptvburo.domain.model.LicenseSnapshot
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.time.Instant
import java.util.Base64

/**
 * A licence answer from the server, and the proof that the server sent it.
 *
 * ## Why this is signed
 *
 * The stored licence lives in a file on the customer's machine. Without a signature, extending a
 * trial is a text edit: open the file, change the date, save. Signing means the app will only
 * believe a document the server's private key produced, and that key is not on the machine.
 *
 * Ed25519 rather than RSA: the signatures are 64 bytes, the public key is 32, and the JDK has had
 * it built in since 15. Nothing to bundle, nothing to configure.
 *
 * ## What signing does not do
 *
 * It does not stop someone patching the binary to skip the check. Nothing in a desktop client can.
 * What it stops is the easy attack — editing a file, replaying an old answer, or pointing the app
 * at a server of one's own — and those are what ordinary sharing actually looks like.
 */
data class SignedLicense(
    /** The exact bytes that were signed. Kept verbatim: re-serialising would change them. */
    val payload: String,
    val signatureBase64: String,
) {
    /**
     * Checks the signature, then reads the document.
     *
     * Returns null for anything that does not verify — a wrong signature, a malformed payload, a
     * document for another device. The caller treats null as "no licence", which fails closed.
     */
    fun verified(
        publicKeyBase64: String,
        expectedDeviceId: String,
        /**
         * The challenge this answer must contain, or null when reading a stored licence.
         *
         * A signature proves the server wrote the document; it says nothing about *when*. Someone
         * who keeps a copy of their licence from the first day of the trial can restore it a month
         * later and it verifies perfectly — the dates are old, but nothing is forged.
         *
         * The client generates a random challenge per live request and refuses an answer that does
         * not carry it back. That makes a replayed document detectable without trusting any clock.
         *
         * Null for the offline path: there the app is reading what it stored earlier rather than
         * asking a question, and demanding a nonce would mean no stored licence ever worked without
         * the network — which would defeat the offline allowance entirely.
         */
        expectedNonce: String? = null,
    ): VerifiedLicense? =
        runCatching {
            val keyBytes = Base64.getDecoder().decode(publicKeyBase64)
            val key = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(keyBytes))
            val verifier = Signature.getInstance("Ed25519")
            verifier.initVerify(key)
            verifier.update(payload.toByteArray(StandardCharsets.UTF_8))
            if (!verifier.verify(Base64.getDecoder().decode(signatureBase64))) return null

            val document = JsonParser.parseString(payload).asJsonObject

            // The document names the device it is for. Without this check, a valid licence copied
            // from a paying customer would work on any machine — the signature would be genuine,
            // it would simply be a genuine licence for somebody else.
            if (document.string("deviceId") != expectedDeviceId) return null

            // The challenge, when one was demanded. Compared in constant time: a comparison that
            // returns early on the first differing character leaks how much of a guess was right,
            // and that is enough to recover a value one character at a time.
            if (expectedNonce != null) {
                val answered = document.string("nonce") ?: return null
                if (!constantTimeEquals(answered, expectedNonce)) return null
            }

            VerifiedLicense(
                deviceId = document.string("deviceId") ?: return null,
                state = document.string("state")?.let(::stateOf) ?: return null,
                trialEndsAt = document.instant("trialEndsAt"),
                expiresAt = document.instant("expiresAt"),
                // The server's own clock at the moment it answered. This is the value that makes
                // the trial resistant to the local clock being moved.
                serverTimeAt = document.instant("serverTime") ?: return null,
            )
        }.getOrNull()

    /**
     * Compares two strings without returning early on the first difference.
     *
     * `==` on a String stops at the first mismatched character, so the time it takes reveals how
     * much of a guess was correct — enough to recover a value one character at a time. The
     * difference is measurable across a network, and this comparison guards a challenge.
     */
    private fun constantTimeEquals(left: String, right: String): Boolean {
        val a = left.toByteArray(StandardCharsets.UTF_8)
        val b = right.toByteArray(StandardCharsets.UTF_8)
        // Length is not secret — the challenge is fixed-width — so an early return here leaks
        // nothing, and MessageDigest.isEqual requires equal lengths anyway.
        if (a.size != b.size) return false
        return java.security.MessageDigest.isEqual(a, b)
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString?.takeIf(String::isNotBlank)

    private fun JsonObject.instant(name: String): Instant? =
        string(name)?.let { value -> runCatching { Instant.parse(value) }.getOrNull() }

    /**
     * Maps the server's wording onto the local states.
     *
     * An unrecognised value becomes UNAVAILABLE rather than an exception: a server that grows a new
     * state must not brick every older client, and "I do not understand this answer" is honestly a
     * kind of unavailability.
     */
    private fun stateOf(value: String): EntitlementState =
        runCatching { EntitlementState.valueOf(value.uppercase()) }
            .getOrDefault(EntitlementState.UNAVAILABLE)
}

/** A licence document whose signature has been checked and whose device matches this machine. */
data class VerifiedLicense(
    val deviceId: String,
    val state: EntitlementState,
    val trialEndsAt: Instant?,
    val expiresAt: Instant?,
    val serverTimeAt: Instant,
) {
    /**
     * Turns the document into the value the policy decides on.
     *
     * [offlineValidUntil] is computed here rather than taken from the server: it is a client-side
     * allowance — "how long may I run without hearing from you again" — and the client is the only
     * party that knows when it last did.
     */
    fun toSnapshot(
        lastVerifiedAt: Instant,
        trustedNow: Instant,
    ): LicenseSnapshot =
        LicenseSnapshot(
            state = state,
            trustedNow = trustedNow,
            trialEndsAt = trialEndsAt,
            expiresAt = expiresAt,
            offlineValidUntil =
                com.lucasserafin94.iptvburo.domain.model.LicensePolicy.offlineDeadlineFor(
                    verifiedAt = lastVerifiedAt,
                    // The state the *server* signed, not one the client chose: a trial gets two
                    // days offline and a paid licence fourteen, and the difference has to rest on
                    // something the customer cannot edit.
                    state = state,
                ),
            serverTimeAt = serverTimeAt,
        )
}
