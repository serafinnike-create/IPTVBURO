package com.lucasserafin94.iptvburo.domain.model

/**
 * What one BURO installation sends another to say "play this here".
 *
 * ## What travels, and what deliberately does not
 *
 * The *title*, never the video. A [TitleShareLink] identity plus a position — about two hundred
 * bytes — and the receiving device resolves it against **its own** playlist and streams directly
 * from the provider.
 *
 * The alternative, mirroring the picture, is worse in every way that matters here:
 *
 * | | mirroring | sending the title |
 * | --- | --- | --- |
 * | on the wire | the whole stream | ~200 bytes |
 * | quality | capped by the phone's wifi | the TV's own connection |
 * | phone battery | drains | untouched |
 * | credentials | the phone must relay them | **never leave the device** |
 *
 * That last row is the decisive one. This project's rule is that an authenticated URL is resolved
 * in memory and never leaves the machine that holds the credentials, and mirroring would require
 * breaking it.
 *
 * ## Why a pairing code is not optional
 *
 * A receiver listens on the local network. Without pairing, anyone sharing that network — a
 * building with shared wifi, a hotel, a café — could push video onto a stranger's television. The
 * code is entered once per pair of devices and proves the sender is in the same room.
 */
data class CastMessage(
    /** Which title to open, in the provider-independent form both devices can resolve. */
    val identity: ContentIdentity,
    /** Shown while the receiver looks the title up, so the screen is never blank and nameless. */
    val title: String,
    /** Where to resume from. Zero starts at the beginning. */
    val positionMillis: Long = 0L,
    /** Proves the sender was told the receiver's code. */
    val pairingCode: String,
) {
    /**
     * The wire form: one line, so a receiver can read it with a line-based reader and a length cap.
     *
     * Deliberately not JSON. A parser is a place for a malformed message to become an exception on
     * a socket exposed to the local network, and there are four fields.
     */
    fun encode(): String =
        listOf(
            PROTOCOL_VERSION,
            pairingCode,
            identity.key,
            positionMillis.toString(),
            // Last, because it is the only field that may contain anything: a title with a
            // separator in it would otherwise shift every field after it.
            title.replace('\u001F', ' '),
        ).joinToString(SEPARATOR.toString())

    companion object {
        /** Bumped when the fields change, so an older receiver refuses rather than misreads. */
        const val PROTOCOL_VERSION = "buro-cast-1"

        /** Unit separator: it cannot appear in a title that came from a catalogue. */
        private const val SEPARATOR = '\u001F'

        /** Four digits, shown by the receiver and typed once into the sender. */
        const val PAIRING_CODE_LENGTH = 4

        /**
         * A message is small. The cap is what stops a socket on the local network being a way to
         * make the app allocate whatever an attacker chooses.
         */
        const val MAX_ENCODED_LENGTH = 2_048

        /**
         * Reads a message, or null when it is not one.
         *
         * Everything arriving here came off a network socket, so every field is checked rather than
         * trusted: the version must match exactly, the code must be four digits, the position must
         * be a plausible number, and the identity must be non-blank. A null return is the only
         * failure mode — nothing here throws, because an exception on the listener thread of a
         * socket anyone can reach is a way to stop the app.
         */
        fun decode(raw: String, expectedPairingCode: String): CastMessage? {
            if (raw.length > MAX_ENCODED_LENGTH) return null
            val parts = raw.split(SEPARATOR)
            if (parts.size != 5) return null
            if (parts[0] != PROTOCOL_VERSION) return null

            val code = parts[1]
            // Compared in full rather than aborting at the first wrong digit. The timing signal
            // from a four-digit code is small, but the comparison costs nothing either way.
            if (!isWellFormedPairingCode(code)) return null
            if (!constantTimeEquals(code, expectedPairingCode)) return null

            val identityKey = parts[2].takeIf { it.isNotBlank() && it.length <= MAX_IDENTITY_LENGTH } ?: return null
            val position = parts[3].toLongOrNull()?.takeIf { it in 0..MAX_POSITION_MILLIS } ?: return null
            val title = parts[4].takeIf { it.isNotBlank() && it.length <= MAX_TITLE_LENGTH } ?: return null

            return CastMessage(
                identity = ContentIdentity(identityKey),
                title = title,
                positionMillis = position,
                pairingCode = code,
            )
        }

        fun isWellFormedPairingCode(value: String): Boolean =
            value.length == PAIRING_CODE_LENGTH && value.all(Char::isDigit)

        private fun constantTimeEquals(left: String, right: String): Boolean {
            if (left.length != right.length) return false
            var difference = 0
            for (index in left.indices) {
                difference = difference or (left[index].code xor right[index].code)
            }
            return difference == 0
        }

        /** A content identity is a slug; anything longer is not one. */
        private const val MAX_IDENTITY_LENGTH = 300

        private const val MAX_TITLE_LENGTH = 300

        /** Forty hours, comfortably past any film and short of an implausible number. */
        private const val MAX_POSITION_MILLIS = 40L * 60 * 60 * 1000
    }
}
