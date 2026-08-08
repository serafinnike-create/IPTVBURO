package com.lucasserafin94.iptvburo.desktop.license

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The deletion attack, and what stops it.
 *
 * A trial remembered in one file lasts seven days per deletion — right-click, delete, seven more
 * days. The record therefore lives in three unrelated places, and the earliest date any of them
 * reports is the one that counts.
 *
 * These tests perform the deletions rather than describe them.
 */
class LicenseStoreTest {
    private fun <T> withStore(block: (LicenseStore, Path, Path, Preferences) -> T): T {
        val appDirectory = Files.createTempDirectory("iptvburo-licence")
        val homeMarker = Files.createTempDirectory("iptvburo-home").resolve(".iptvburo-device")
        val preferences = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        return try {
            block(LicenseStore(appDirectory, homeMarker, preferences), appDirectory, homeMarker, preferences)
        } finally {
            runCatching { preferences.removeNode() }
            @OptIn(kotlin.io.path.ExperimentalPathApi::class)
            appDirectory.deleteRecursively()
            @OptIn(kotlin.io.path.ExperimentalPathApi::class)
            homeMarker.parent.deleteRecursively()
        }
    }

    private val firstRun = Instant.parse("2026-08-01T10:00:00Z")

    /** A genuinely new machine has no record, which is what starts a trial. */
    @Test
    fun `a fresh machine has never been seen`() {
        withStore { store, _, _, _ ->
            assertNull(store.firstSeen())
        }
    }

    @Test
    fun `the first-seen date is remembered`() {
        withStore { store, _, _, _ ->
            store.rememberFirstSeen(firstRun)

            assertEquals(firstRun, store.firstSeen())
        }
    }

    /**
     * The attack: delete the app's own folder.
     *
     * This is what a user who wants another seven days actually does, and the other two markers
     * have to answer.
     */
    @Test
    fun `deleting the application directory does not restart the trial`() {
        withStore { store, appDirectory, _, _ ->
            store.rememberFirstSeen(firstRun)

            @OptIn(kotlin.io.path.ExperimentalPathApi::class)
            appDirectory.deleteRecursively()

            assertEquals(firstRun, store.firstSeen(), "the machine has still been seen before")
        }
    }

    /** The same, from the other side: clear the registry entry and the files answer. */
    @Test
    fun `clearing the preferences entry does not restart the trial`() {
        withStore { store, _, _, preferences ->
            store.rememberFirstSeen(firstRun)

            preferences.removeNode()

            assertEquals(firstRun, store.firstSeen())
        }
    }

    @Test
    fun `deleting the home marker does not restart the trial`() {
        withStore { store, _, homeMarker, _ ->
            store.rememberFirstSeen(firstRun)

            Files.deleteIfExists(homeMarker)

            assertEquals(firstRun, store.firstSeen())
        }
    }

    /**
     * Two of three gone still answers.
     *
     * The bar is not "impossible" — it is "harder than paying", and clearing three unrelated
     * locations in three different tools is past that line.
     */
    @Test
    fun `only one surviving marker is enough`() {
        withStore { store, appDirectory, homeMarker, _ ->
            store.rememberFirstSeen(firstRun)

            @OptIn(kotlin.io.path.ExperimentalPathApi::class)
            appDirectory.deleteRecursively()
            Files.deleteIfExists(homeMarker)

            assertEquals(firstRun, store.firstSeen(), "the preferences entry still knows")
        }
    }

    /**
     * The subtle version: let the app re-register and write today's date.
     *
     * If remembering moved the date forward, deleting the licence and letting the app register
     * again would refresh the trial with the app's own cooperation. It must only ever move earlier.
     */
    @Test
    fun `remembering again cannot move the date forward`() {
        withStore { store, _, _, _ ->
            store.rememberFirstSeen(firstRun)

            store.rememberFirstSeen(firstRun.plus(Duration.ofDays(30)))

            assertEquals(firstRun, store.firstSeen(), "a later date must never overwrite an earlier one")
        }
    }

    /** An earlier date does win: restoring a backup should not shorten anyone's trial. */
    @Test
    fun `an earlier date replaces a later one`() {
        withStore { store, _, _, _ ->
            store.rememberFirstSeen(firstRun)

            val earlier = firstRun.minus(Duration.ofDays(3))
            store.rememberFirstSeen(earlier)

            assertEquals(earlier, store.firstSeen())
        }
    }

    /** After clearing one marker, remembering restores it rather than leaving a gap. */
    @Test
    fun `a cleared marker is restored on the next launch`() {
        withStore { store, appDirectory, homeMarker, _ ->
            store.rememberFirstSeen(firstRun)
            Files.deleteIfExists(homeMarker)

            // What the app does on every start: record that it has seen this machine.
            store.rememberFirstSeen(Instant.parse("2026-08-05T10:00:00Z"))

            assertEquals(firstRun, Files.readString(homeMarker).trim().let(Instant::parse))
        }
    }

    // ---------------------------------------------------------------------------------------
    // The licence document itself
    // ---------------------------------------------------------------------------------------

    @Test
    fun `a stored licence round-trips`() {
        withStore { store, _, _, _ ->
            val stored =
                StoredLicense(
                    license = SignedLicense(payload = """{"deviceId":"X"}""", signatureBase64 = "sig"),
                    lastVerifiedAt = firstRun,
                )

            store.write(stored)
            val read = assertNotNull(store.read())

            assertEquals(stored.license.payload, read.license.payload)
            assertEquals(stored.license.signatureBase64, read.license.signatureBase64)
            assertEquals(firstRun, read.lastVerifiedAt)
        }
    }

    /** A truncated file reads as no licence rather than as a half one. */
    @Test
    fun `a truncated licence file reads as absent`() {
        withStore { store, appDirectory, _, _ ->
            Files.createDirectories(appDirectory)
            Files.writeString(appDirectory.resolve("licence"), "only one line")

            assertNull(store.read())
        }
    }

    @Test
    fun `no licence file reads as absent`() {
        withStore { store, _, _, _ ->
            assertNull(store.read())
        }
    }
}
