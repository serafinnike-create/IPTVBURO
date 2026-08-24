package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Replaying a programme that has already aired.
 *
 * The value has to survive four hops from the provider to the button — parser, compact catalogue,
 * disk cache, guide — and two of those rebuild items from their own storage, so a field they do not
 * carry is a field the app silently never sees. Those two are what this pins.
 */
class CatchUpWiringTest {
    private fun read(relative: String): String = Path.of(relative).readText()

    private val workspace = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamWorkspace.kt")
    private val state = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt")
    private val compact = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/data/CompactXtreamCatalog.kt")
    private val cache = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/data/CatalogDiskCache.kt")

    @Test
    fun `the compact catalogue keeps the window`() {
        // It rebuilds every item from parallel columns, so a field without a column is a field the
        // app never sees again — and every channel would look as though it kept no recording.
        assertTrue(compact.contains("catchUpDays[index] = item.catchUpDays ?: 0"), "written")
        assertTrue(compact.contains("catchUpDays = catchUpDays[index].takeIf"), "read back")
        assertTrue(
            compact.contains("catchUpDays = catchUpDays.copyOf(nextCapacity)"),
            "The column has to grow with the others, or it overflows past the initial capacity.",
        )
    }

    @Test
    fun `the disk cache persists it, behind a new format version`() {
        assertTrue(cache.contains("output.writeInt(item.catchUpDays ?: 0)"), "written")
        assertTrue(cache.contains("catchUpDays = input.readInt().takeIf"), "read back")
        assertFalse(
            cache.contains("FORMAT_VERSION = 1"),
            "A version-1 row ends where this one expects another int; reading past it would take a " +
                "neighbouring field for a recording window.",
        )
    }

    @Test
    fun `only programmes inside the provider's own window are offered`() {
        val loader = state.substringAfter("past =").substringBefore("                    )")
        assertTrue(
            loader.contains("if (selected.catchUpDays == null)"),
            "A channel with no recorder offers nothing, so the section cannot appear.",
        )
        assertTrue(
            loader.contains("start >= earliest"),
            "Offering a programme older than the window produces a button the server refuses.",
        )
        assertTrue(loader.contains("end <= nowSeconds"), "Only what has finished can be replayed.")
    }

    @Test
    fun `the start is formatted in local time, not UTC`() {
        // Xtream's timeshift path takes wall-clock time, and the guide is already in the provider's
        // local time — the clock the viewer is reading. Converting would ask for a programme hours
        // from the one they pressed.
        val helper = workspace.substringAfter("private fun catchUpTargetFor(").substringBefore("\n}")
        assertTrue(helper.contains("ZoneId.systemDefault()"), "local zone")
        assertFalse(helper.contains("ZoneOffset.UTC"), "not UTC")
        assertTrue(workspace.contains("""ofPattern("yyyy-MM-dd:HH-mm")"""), "the shape Xtream accepts")
    }

    @Test
    fun `a programme without both times is not offered`() {
        val helper = workspace.substringAfter("private fun catchUpTargetFor(").substringBefore("\n}")
        assertTrue(helper.contains("startEpochSeconds ?: return null"), "no start, no request")
        assertTrue(helper.contains("endEpochSeconds ?: return null"), "no end, no length")
    }

    @Test
    fun `two showings of one programme are two entries`() {
        // The start is part of the key, so last night's film and tonight's repeat do not overwrite
        // each other in continue-watching.
        val helper = workspace.substringAfter("private fun catchUpTargetFor(").substringBefore("\n}")
        assertTrue(helper.contains("\"catchup:\${item.contentIdentity().key}:\$startLocal\""))
    }

    @Test
    fun `a recording is downloadable where a live channel is not`() {
        val manager =
            read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/download/DesktopDownloadManager.kt")
        assertTrue(
            manager.contains("is XtreamPlaybackTarget.CatchUp -> true"),
            "A live channel has no end; a recording does, which is the whole difference.",
        )
    }

    @Test
    fun `the section's wording is translated`() {
        assertFalse(workspace.contains("\"Rever (\""), "the label belongs in the string tables")
        assertTrue(workspace.contains("text.shareStrings.screens.catchUpShow"))
    }
}
