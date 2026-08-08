package com.lucasserafin94.iptvburo.desktop.license

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.prefs.Preferences

/**
 * Where the licence lives on this machine, and why it lives in more than one place.
 *
 * ## The deletion attack
 *
 * A trial that is remembered in exactly one file lasts seven days *per deletion*. Remove the file
 * and the app has never seen this machine: it registers again, and the seven days start over. That
 * is not a sophisticated attack — it is a right-click.
 *
 * So the record is written to three unrelated places, and the earliest first-seen date any of them
 * reports is the one that counts:
 *
 * - the application data directory, where the app keeps everything else;
 * - the Java Preferences store, which on Windows is the registry;
 * - a marker in the user's home directory, outside the app's own folder.
 *
 * Clearing all three is possible and takes deliberate effort in three different tools. That is the
 * intended bar: not "impossible", but "harder than paying €9,90", which is the only bar a desktop
 * client can actually hold.
 *
 * ## What is stored
 *
 * The signed document, verbatim, plus when it was last verified. The dates inside the document are
 * the server's and are covered by its signature; the first-seen marker is the only value this class
 * decides for itself, and it can only ever move *earlier* — see [rememberFirstSeen].
 */
class LicenseStore(
    private val appDirectory: Path = defaultAppDirectory(),
    private val homeMarker: Path = defaultHomeMarker(),
    private val preferences: Preferences = Preferences.userRoot().node(PREFERENCES_NODE),
) {
    /**
     * The stored licence, or null when this machine has none.
     *
     * Null is the ordinary first-run answer, not an error.
     */
    fun read(): StoredLicense? =
        runCatching {
            val file = appDirectory.resolve(LICENCE_FILE)
            if (!Files.isRegularFile(file)) return null
            val lines = Files.readAllLines(file)
            if (lines.size < 3) return null
            StoredLicense(
                license = SignedLicense(payload = lines[0], signatureBase64 = lines[1]),
                lastVerifiedAt = Instant.parse(lines[2]),
            )
        }.getOrNull()

    fun write(stored: StoredLicense) {
        runCatching {
            Files.createDirectories(appDirectory)
            val file = appDirectory.resolve(LICENCE_FILE)
            val temporary = file.resolveSibling("$LICENCE_FILE.tmp")
            Files.write(
                temporary,
                listOf(stored.license.payload, stored.license.signatureBase64, stored.lastVerifiedAt.toString()),
            )
            // Atomic move, so a crash mid-write leaves the previous licence rather than a truncated
            // file that reads as "no licence" — which would look exactly like the deletion attack
            // and hand the user a fresh trial by accident.
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * The earliest moment this machine was ever seen, across every marker.
     *
     * Null only when none of the three has ever been written — a genuinely new machine.
     *
     * The *earliest* rather than the latest: an attacker can add a marker but cannot remove the
     * evidence from a place they have not found, so taking the minimum means clearing one location
     * achieves nothing unless all of them are cleared.
     */
    fun firstSeen(): Instant? =
        listOfNotNull(
            readMarker(appDirectory.resolve(FIRST_SEEN_FILE)),
            readMarker(homeMarker),
            runCatching { preferences.get(FIRST_SEEN_KEY, null)?.let(Instant::parse) }.getOrNull(),
        ).minOrNull()

    /**
     * Records that this machine has been seen, if it has not been already.
     *
     * Never moves the date forward. Rewriting it to *now* on every launch would let an attacker
     * refresh their trial simply by deleting the licence and letting the app re-register — the
     * markers would agree with them. Writing only when a location is missing, and only with the
     * earliest date already known, means the record can be restored but never reset.
     */
    fun rememberFirstSeen(at: Instant) {
        val earliest = firstSeen()?.let { known -> minOf(known, at) } ?: at
        val text = earliest.toString()

        runCatching {
            Files.createDirectories(appDirectory)
            Files.writeString(appDirectory.resolve(FIRST_SEEN_FILE), text)
        }
        runCatching {
            homeMarker.parent?.let(Files::createDirectories)
            Files.writeString(homeMarker, text)
        }
        runCatching { preferences.put(FIRST_SEEN_KEY, text) }
    }

    /**
     * Forgets everything, for tests and for a genuine reset.
     *
     * Deliberately not reachable from the interface: a "reset licence" button would be the very
     * attack this class exists to prevent, offered as a feature.
     */
    fun clearForTesting() {
        runCatching { Files.deleteIfExists(appDirectory.resolve(LICENCE_FILE)) }
        runCatching { Files.deleteIfExists(appDirectory.resolve(FIRST_SEEN_FILE)) }
        runCatching { Files.deleteIfExists(homeMarker) }
        runCatching { preferences.remove(FIRST_SEEN_KEY) }
    }

    private fun readMarker(path: Path): Instant? =
        runCatching {
            if (!Files.isRegularFile(path)) return null
            Instant.parse(Files.readString(path).trim())
        }.getOrNull()

    companion object {
        private const val LICENCE_FILE = "licence"
        private const val FIRST_SEEN_FILE = ".seen"
        private const val FIRST_SEEN_KEY = "device-first-seen"
        private const val PREFERENCES_NODE = "com/lucasserafin94/iptvburo/licence"

        fun defaultAppDirectory(): Path =
            Path.of(System.getProperty("user.home"), ".iptvburo", "licence")

        /**
         * Outside the app's own directory, so deleting the install folder does not take it.
         *
         * A dotted name in the home directory: unobtrusive, survives an uninstall, and is not
         * where anyone looking for "the licence file" would think to look first.
         */
        fun defaultHomeMarker(): Path =
            Path.of(System.getProperty("user.home"), ".iptvburo-device")
    }
}

/** A signed licence together with when this machine last heard it from the server. */
data class StoredLicense(
    val license: SignedLicense,
    val lastVerifiedAt: Instant,
)
