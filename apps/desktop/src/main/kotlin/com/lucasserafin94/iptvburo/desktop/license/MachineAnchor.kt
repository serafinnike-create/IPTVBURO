package com.lucasserafin94.iptvburo.desktop.license

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/**
 * The installation UUID, derived from the machine rather than drawn at random.
 *
 * ## The hole this closes
 *
 * The installation id was `UUID.randomUUID()`, and the device id is derived from it. Deleting three
 * files — the DPAPI identity under `AppData\Local`, `~/.iptvburo`, and `~/.iptvburo-device` — made
 * the app generate a new identity, which the server correctly saw as a machine it had never met and
 * granted a fresh seven-day trial. Repeatable indefinitely, by anyone, for ever.
 *
 * The server's trial-reset defence was already sound: it clamps a claimed `firstSeen`, refuses one
 * in the future, and will not let a second registration lengthen a trial. But every one of those
 * rules is keyed on the device id, and a new device id sidesteps all of them at once.
 *
 * Anchoring the installation id to the machine means the deletion produces the *same* identity, the
 * server recognises it, and the trial resumes where it left off.
 *
 * ## Why a v4 UUID rather than a hash
 *
 * [canonicalUuid] requires an RFC-4122 version 4 variant 2 UUID, and it is validated on every read.
 * Rather than loosen that check — which guards the wire format the server also parses — the machine
 * value is hashed and the result is *shaped* into a valid v4 UUID. It is deterministic where a real
 * v4 is random, which is the whole point, and it is indistinguishable to everything downstream.
 *
 * ## What is and is not sent
 *
 * The MachineGuid never leaves this function. What travels is the derived device id, which is a
 * one-way hash of a hash — the machine value cannot be recovered from it, and it is no more
 * identifying than the random UUID it replaces. The GUID is salted with a constant of this app's
 * own, so the same machine yields a different value here than it would to any other software
 * reading the same registry key.
 *
 * ## The cost, which is real
 *
 * Reinstalling Windows changes the MachineGuid, and so changes the identity: a paying customer then
 * looks like a new machine and has to be re-granted through the admin panel. That is the accepted
 * trade — the alternative leaves the trial resettable by anyone who can delete three files.
 */
internal object MachineAnchor {
    /**
     * A stable installation UUID for this machine, or null when no anchor can be read.
     *
     * Null means "fall back to a random UUID": on a machine whose registry cannot be read, a random
     * identity still works exactly as before. Losing the anti-reset property for that user is far
     * better than refusing to run.
     */
    fun installationUuid(anchor: String? = machineAnchor()): String? {
        val value = anchor?.trim()?.takeIf(String::isNotBlank) ?: return null
        val digest =
            MessageDigest.getInstance("SHA-256")
                .digest("$SALT\n$value".toByteArray(StandardCharsets.UTF_8))
        return uuidFrom(digest)
    }

    /**
     * Shapes 16 bytes of hash into a valid RFC-4122 version 4 UUID.
     *
     * The version and variant bits are overwritten, exactly as `UUID.randomUUID` does with its own
     * random bytes. The remaining 122 bits come from SHA-256, so two different machines colliding
     * is not a practical concern.
     */
    internal fun uuidFrom(digest: ByteArray): String {
        val bytes = digest.copyOf(16)
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()

        var high = 0L
        var low = 0L
        for (index in 0 until 8) high = (high shl 8) or (bytes[index].toLong() and 0xFF)
        for (index in 8 until 16) low = (low shl 8) or (bytes[index].toLong() and 0xFF)
        return UUID(high, low).toString()
    }

    /**
     * Windows' own installation identifier, read from the registry.
     *
     * `MachineGuid` is written when Windows is installed and survives application installs and
     * uninstalls, disk cleanups and profile changes — which is precisely the property needed here.
     * It changes when Windows is reinstalled or the disk is imaged, which is the documented cost.
     *
     * Failures return null rather than throwing: a locked-down machine, a non-Windows host or a
     * missing key must cost the anti-reset property, never the ability to run the app.
     */
    private fun machineAnchor(): String? =
        runCatching {
            if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) {
                return null
            }
            com.sun.jna.platform.win32.Advapi32Util.registryGetStringValue(
                com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE,
                "SOFTWARE\\Microsoft\\Cryptography",
                "MachineGuid",
            )
        }.getOrNull()

    /**
     * Salt, so the value here is this app's own.
     *
     * Without it, the derived id would be a plain hash of a value other software can also read, and
     * two unrelated products could produce the same identifier for the same machine.
     */
    private const val SALT = "iptvburo-machine-anchor-v1"
}
