package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Renaming and forgetting a saved playlist.
 *
 * Reported after a reset: the profile screen listed two saved lists and offered no way to remove or
 * rename either. `XtreamSourceLibrary` had both operations already and nothing called them.
 *
 * The dangerous half is removal. It erases the password stored with the list, and a profile holding
 * that list's id would be left pointing at something that no longer exists — which reads as a
 * broken profile with no way to see why.
 */
class SavedListManagementTest {
    private fun read(relative: String): String = Path.of(relative).readText()

    private val state = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt")
    private val onboarding = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/OnboardingFlow.kt")
    private val app = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DesktopApp.kt")

    private fun removeBody(): String {
        val marker = "fun removeSavedSource(sourceId: String) {"
        // substringAfter returns the whole file when its marker is missing, which would leave every
        // check below searching all of DesktopAppState and passing on an unrelated line.
        assertTrue(state.contains(marker), "the remover was renamed; this test needs updating")
        return state.substringAfter(marker).substringBefore("\n    }")
    }

    @Test
    fun `the screen offers both actions`() {
        assertTrue(onboarding.contains("screens.setupRenameList"), "rename")
        assertTrue(onboarding.contains("screens.setupRemoveList"), "remove")
        assertTrue(app.contains("onRenameSaved = appState::renameSavedSource"), "wired")
        assertTrue(app.contains("onRemoveSaved = appState::removeSavedSource"), "wired")
    }

    @Test
    fun `a profile is never left pointing at a list that was removed`() {
        // The whole safety property. Without this the profile keeps an id for something gone, and
        // opening it shows an app with no list and no explanation.
        val body = removeBody()
        // The whole statement, not its fragments. Checking for "profile.sourceId == sourceId"
        // alone passed with the reassignment deleted, because that text also appears in the line
        // that merely counts the affected profiles — a test that could not fail.
        assertTrue(
            body.contains("profiles = profiles.map { profile ->") &&
                body.contains("if (profile.sourceId == sourceId) profile.copy(sourceId = null) else profile"),
            "profiles using the list have to actually be pointed away from it",
        )
        assertTrue(body.contains("userStore.saveProfiles(profiles)"), "and that has to be persisted")
    }

    @Test
    fun `removing the list the viewer is signed in to closes that session`() {
        // The catalogue in memory belongs to credentials that have just been erased; leaving it on
        // screen would offer titles that can no longer be played.
        val body = removeBody()
        assertTrue(body.contains("xtreamRepository.clear()"))
        assertTrue(body.contains("XtreamStatus.Disconnected"))
    }

    @Test
    fun `removal is confirmed before it happens`() {
        // It erases a password and can orphan a profile; neither is recoverable from this screen.
        assertTrue(onboarding.contains("removingSource"), "a pending removal is held")
        assertTrue(onboarding.contains("AlertDialog"), "and confirmed")
        assertTrue(
            onboarding.contains("screens.setupRemoveListConfirm.format"),
            "the dialog names the list, so the wrong one is not forgotten by accident",
        )
    }

    @Test
    fun `the selection does not survive the list it pointed at`() {
        assertTrue(
            onboarding.contains("if (reusedSourceId == doomed.id) reusedSourceId = null"),
            "otherwise the form would try to reuse a list that no longer exists on submit",
        )
    }

    @Test
    fun `a blank name is refused rather than stored`() {
        // An unlabelled row is one nobody can identify, and the label is all this screen shows.
        val marker = "fun renameSavedSource(sourceId: String, label: String) {"
        assertTrue(state.contains(marker), "the renamer was renamed; this test needs updating")
        assertTrue(state.substringAfter(marker).substringBefore("\n    }").contains("label.isBlank()"))
    }

    @Test
    fun `the screen redraws when a list changes`() {
        // savedSources reads the store directly and holds no state, so without reading the
        // revision the row would keep its old name until something else redrew the screen.
        assertTrue(state.contains("var savedSourcesRevision"), "a revision exists")
        assertTrue(removeBody().contains("savedSourcesRevision += 1"), "and removal bumps it")
        assertTrue(
            app.contains("appState.savedSourcesRevision.let { appState.savedSources() }"),
            "and the screen reads it, or Compose never learns the list changed",
        )
    }

    @Test
    fun `the labels are translated rather than written into the screen`() {
        val tables = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/ui/DesktopStrings.kt")
        // Five locales: es, pt-BR, en, de, it.
        listOf("setupRenameList", "setupRemoveList", "setupRemoveListConfirm").forEach { key ->
            assertTrue(
                tables.split("$key = ").size - 1 == 5,
                "every locale needs $key, or one language fails to compile",
            )
        }
        assertFalse(onboarding.contains("\"Remover\""), "the label belongs in the string tables")
    }
}
