package com.lucasserafin94.iptvburo.data.security

/**
 * Encrypts the per-row fields that can carry a provider's stream URL, credentials in a query
 * string, or playback headers: `streamUrl`, `userAgent`, `referer`, `origin` on `ChannelEntity`.
 *
 * Unlike [SourceConnectionStore], which holds one credential per source in a keyed store, this
 * operates on values already in hand — a playlist import produces tens of thousands of these
 * rows, so this is called per row rather than per source.
 *
 * A value this cipher did not produce — every row written before it existed, and Xtream/Stalker
 * rows that only fill these fields at playback time — is returned by [decrypt] unchanged rather
 * than rejected: those are plaintext read normally today, and refusing them would turn every
 * legacy row into a stream nothing can open, in exchange for protecting data that was never
 * encrypted in the first place. [encrypt] always encrypts going forward.
 */
interface ChannelFieldCipher {
    /** Encrypts [value], or returns null unchanged for a field that was never set. */
    fun encrypt(value: String?): String?

    /**
     * Decrypts a value produced by [encrypt].
     *
     * Falls back to returning [value] unchanged when it does not carry this cipher's own prefix
     * (a legacy plaintext row) or fails to decrypt (a value written by a build with a different
     * key, e.g. after a factory reset that dropped the Keystore key but kept an app-level backup
     * of the database) — in both cases the field is still usable exactly as it was before this
     * cipher existed, rather than turning an existing row unplayable.
     */
    fun decrypt(value: String?): String?
}
