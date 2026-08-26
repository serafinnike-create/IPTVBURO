package com.lucasserafin94.iptvburo.ui

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A second list from the seller joins the first instead of being refused.
 *
 * The television refused any delivery once a source existed, which meant a seller could send
 * neither the second subscription their customer had bought nor the replacement for an address
 * that had died. Android already avoided that trap by asking a better question — "is *this* list
 * already here?" rather than "is *any* list here?" — and this pins the difference, because the
 * two read alike and only one of them lets a customer be helped twice.
 */
class AssignedListAccumulationTest {
    private fun read(relative: String): String =
        String(Files.readAllBytes(Path.of(relative)), Charsets.UTF_8)

    private val viewModel =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/ui/MainViewModel.kt")
    private val repository =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/data/repository/RoomCatalogRepository.kt")

    private fun applyBody(): String {
        val marker = "private fun applyAssignedPlaylistIfAny() {"
        // substringAfter returns the whole file when its marker is missing, which would leave the
        // checks below searching all of MainViewModel and passing on an unrelated line.
        assertTrue("the method was renamed; this test needs updating", viewModel.contains(marker))
        return viewModel.substringAfter(marker).substringBefore("\n    }")
    }

    @Test
    fun `a delivery is skipped only when that same list is already here`() {
        val body = applyBody()
        assertTrue(
            "the question has to be about this list, not about having any list",
            body.contains("hasXtreamSource("),
        )
        // The guard that broke the television: "any source exists" refuses a legitimate second
        // list forever, because the customer will always have one after the first delivery.
        assertFalse(
            "refusing on any existing source would block every later delivery",
            body.contains("sources.isEmpty()") || body.contains("sources.isNotEmpty()"),
        )
    }

    @Test
    fun `sameness is decided on the credentials, not the label`() {
        // Two lists from one seller share a display name. Comparing labels would treat a genuinely
        // different subscription as a duplicate and silently drop it.
        val marker = "override suspend fun hasXtreamSource("
        assertTrue("hasXtreamSource was renamed", repository.contains(marker))
        val body = repository.substringAfter(marker).substringBefore("\n    }")
        assertTrue(
            "all three fields decide it",
            body.contains("it.serverUrl == serverUrl") &&
                body.contains("it.username == username") &&
                body.contains("it.password == password"),
        )
    }

    @Test
    fun `a failed delivery never takes the boot down with it`() {
        // It runs on every launch of every device, nearly always finding nothing.
        assertTrue("contained", applyBody().contains("runCatching"))
    }
}
