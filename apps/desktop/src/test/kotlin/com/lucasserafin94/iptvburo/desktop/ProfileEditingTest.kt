package com.lucasserafin94.iptvburo.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Editing a profile from the gate.
 *
 * The dangerous operation here is not renaming — it is changing which playlist a profile signs in
 * to. That reuses the account screen, which was written to *create* profiles, and the failure mode
 * is silent: a user who came to change their playlist gets a sixth profile, or worse, finds their
 * existing one renamed and no longer a Kids profile because the form supplied its own defaults.
 *
 * Read from source, because these are structural properties of a Compose state holder whose
 * behavioural surface would need the whole app assembled to reach.
 */
class ProfileEditingTest {
    private val stateSource: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt").readText()

    private val appSource: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DesktopApp.kt").readText()

    private val dialogSource: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/ProfileEditorDialog.kt")
            .readText()

    @Test
    fun `the gate offers an edit action`() {
        assertTrue(appSource.contains("onEditProfile"), "the gate must offer editing")
        assertTrue(
            appSource.contains("ProfileEditorDialog("),
            "the edit action must open the editor",
        )
    }

    /**
     * Editing is available even with a single profile.
     *
     * Deletion needs two, because removing the last would leave the gate with nothing to choose.
     * Editing has no such constraint, and a household with one profile is precisely who needs to
     * change its playlist without being made to create a second profile first.
     */
    @Test
    fun `editing is not gated on having more than one profile`() {
        val editIndex = appSource.indexOf("onEditProfile(profile.id)")
        assertTrue(editIndex > 0, "the edit call was not found")

        // The size guard must come after the edit button, meaning it applies only to deletion.
        val guardIndex = appSource.indexOf("if (profiles.size > 1)", startIndex = editIndex)
        assertTrue(guardIndex > editIndex, "editing appears to be inside the delete-only guard")
    }

    /**
     * Changing a playlist must not overwrite the rest of the profile.
     *
     * The account screen carries a name field and an avatar picker because it was built for
     * creation. Reusing those values when editing would rename the profile to whatever that form
     * happened to hold and clear its Kids flag — a change the user never asked for, applied while
     * they were doing something else.
     */
    @Test
    fun `editing a source preserves everything except the source`() {
        // Both completion paths — a new playlist, and reusing a saved one — must copy the existing
        // profile rather than constructing a fresh one. The value assigned differs between them
        // (`source.id` when one was just created, `sourceId` when reusing), so only the shape of
        // the copy is matched.
        val copies = Regex("""existing\?\.copy\(sourceId = [\w.]+\)""").findAll(stateSource).count()

        assertTrue(
            copies >= 2,
            "expected both setup paths to copy the existing profile, found $copies",
        )

        // And that the copy changes nothing else. `copy(sourceId = ...)` alone leaves name, avatar,
        // Kids and the music file untouched; adding another field here would silently start
        // overwriting something the user set elsewhere.
        Regex("""existing\?\.copy\(([^)]*)\)""").findAll(stateSource).forEach { match ->
            val arguments = match.groupValues[1]
            assertFalse(arguments.contains("name"), "editing a playlist must not rename the profile")
            assertFalse(arguments.contains("isKids"), "editing a playlist must not change Kids mode")
            assertFalse(arguments.contains("avatar"), "editing a playlist must not change the avatar")
            assertFalse(
                arguments.contains("musicPlaylistPath"),
                "editing a playlist must not clear the music file",
            )
        }
    }

    /**
     * The editing marker is cleared on every exit.
     *
     * A left-over value would make the *next* profile creation overwrite the profile that was being
     * edited instead of adding one — the user would create a profile and watch a different one
     * change.
     */
    @Test
    fun `the editing marker is cleared when the account step is left`() {
        assertTrue(
            stateSource.contains("fun cancelAddingProfile()"),
            "cancelAddingProfile not found; this test needs updating",
        )

        val cancelBody = stateSource.substringAfter("fun cancelAddingProfile()").take(300)
        assertTrue(
            cancelBody.contains("editingProfileId = null"),
            "cancelling must clear the editing marker",
        )

        val addBody = stateSource.substringAfter("fun startAddingProfile()").take(300)
        assertTrue(
            addBody.contains("editingProfileId = null"),
            "adding a profile must clear any stale editing marker",
        )
    }

    @Test
    fun `a blank name cannot be saved`() {
        // Saving a blank name would leave an unlabelled circle on the gate that nobody can identify.
        assertTrue(
            dialogSource.contains("enabled = name.isNotBlank()"),
            "the save button must refuse a blank name",
        )

        val updateBody = stateSource.substringAfter("fun updateProfile(").take(600)
        assertTrue(
            updateBody.contains("if (clean.isBlank()) return"),
            "the state holder must refuse a blank name as well as the dialog",
        )
    }

    @Test
    fun `the name is bounded`() {
        // A long name overflows the fixed-width tile on the gate. Bounded in both places, because
        // the dialog is not the only caller of updateProfile.
        assertTrue(dialogSource.contains("it.take(24)"), "the field must bound what can be typed")

        val updateBody = stateSource.substringAfter("fun updateProfile(").take(600)
        assertTrue(updateBody.contains("take(24)"), "the state holder must bound the name too")
    }

    /**
     * The dialog scrolls.
     *
     * With the avatar grid, the playlist row and the music row it is taller than the dialog area on
     * a small laptop, and a section that cannot be reached is a section that does not exist. This
     * has been fixed twice elsewhere in the app after being reported.
     */
    @Test
    fun `the editor scrolls`() {
        assertTrue(
            dialogSource.contains("verticalScroll"),
            "the editor must scroll: it is taller than a small laptop's dialog area",
        )
    }

    @Test
    fun `changing the playlist leaves the dialog rather than pretending to be cosmetic`() {
        // Swapping the account means new credentials and a connection that can fail. The dialog
        // cannot show that, so it must hand over to the screen that can.
        assertTrue(
            appSource.contains("appState.startEditingProfileSource(profile.id)"),
            "changing the playlist must go through the account screen",
        )
    }

    @Test
    fun `every editor string is translated into all four languages`() {
        val strings =
            Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/ui/DesktopStrings.kt")
                .readText()

        for (key in listOf("profileEdit", "profileEditTitle", "profileSourceChange", "profileSave")) {
            val uses = Regex("""\b$key = "[^"]+"""").findAll(strings).count()
            assertTrue(uses >= 4, "$key is translated $uses times, expected 4")
        }
    }

    @Test
    fun `the editor holds an id rather than a copy of the profile`() {
        // Held as a copy, a rename would leave the dialog editing a stale value, and deleting the
        // profile would leave it editing something that no longer exists.
        assertTrue(
            appSource.contains("var editingProfile by remember { mutableStateOf<String?>(null) }"),
            "the editor should track a profile id",
        )
        assertTrue(
            appSource.contains("appState.profiles.firstOrNull { it.id == editingProfile }"),
            "the profile should be looked up fresh on each composition",
        )
    }

    @Test
    fun `no credential ever reaches the editor`() {
        // The dialog shows a playlist's label, never its address or sign-in details. Those live in
        // the credential store and must not travel into a screen that only names things.
        for (forbidden in listOf("password", "username", "server")) {
            assertFalse(
                dialogSource.contains(forbidden, ignoreCase = true),
                "the editor must not handle $forbidden",
            )
        }
    }

    private companion object {
        init {
            // Fails loudly rather than silently passing on a missing file.
            listOf(
                "src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/ProfileEditorDialog.kt",
                "src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt",
            ).forEach { path ->
                require(Files.isRegularFile(Path.of(path))) { "missing source: $path" }
            }
        }
    }
}
