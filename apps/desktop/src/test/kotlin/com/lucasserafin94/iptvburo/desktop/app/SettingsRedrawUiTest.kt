package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * The failure this suite exists for: a setting that changes on disk and not on screen.
 *
 * Java Preferences is not Compose snapshot state, so a screen reading through it will not redraw
 * when it is written. That shipped twice in one panel — hiding a category appeared to work only
 * because that path also rewrote an observed list, while restoring one did nothing visible at all,
 * and the parental switches never moved either way.
 *
 * `PreferenceRecompositionTest` guards the shape of the fix by reading the source. This guards the
 * behaviour: a store that is not observable, a reader that observes a revision counter, and a
 * writer that bumps it — with the actual Compose runtime deciding whether the pixel changes.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsRedrawUiTest {
    /**
     * Stands in for the preferences store: plain state that Compose knows nothing about.
     *
     * Deliberately not a `mutableStateOf`. The whole failure is that the real store is invisible to
     * the runtime, and a test whose store is observable would pass whatever the screen did.
     */
    private class UnobservableStore {
        private val hidden = mutableSetOf<String>()

        fun isHidden(id: String): Boolean = id in hidden

        fun setHidden(id: String, value: Boolean) {
            if (value) hidden += id else hidden -= id
        }
    }

    /** A row written the way the settings panel is: reading a counter, so it recomposes. */
    @Composable
    private fun CategoryRowUnderTest(store: UnobservableStore) {
        var revision by mutableStateOf(0)
        Column {
            // Read so Compose re-runs this when the writer bumps it.
            @Suppress("UNUSED_EXPRESSION")
            revision
            val hidden = store.isHidden("cat-1")
            Text(text = if (hidden) "Oculta" else "Visível")
            Text(
                text = "Alternar",
                modifier =
                    Modifier.clickable {
                        store.setHidden("cat-1", !store.isHidden("cat-1"))
                        revision += 1
                    },
            )
        }
    }

    /** A row written the way the broken one was: no counter, so nothing tells Compose to redraw. */
    @Composable
    private fun BrokenCategoryRow(store: UnobservableStore) {
        Column {
            Text(text = if (store.isHidden("cat-2")) "Oculta" else "Visível")
            Text(
                text = "Alternar",
                modifier = Modifier.clickable { store.setHidden("cat-2", !store.isHidden("cat-2")) },
            )
        }
    }

    /**
     * Hiding and restoring both have to show on screen.
     *
     * The second half is the part that was broken: hiding happened to redraw for an unrelated
     * reason, restoring did not, and the button appeared dead.
     */
    @Test
    fun `a preference change redraws when a revision is bumped`() =
        runComposeUiTest {
            val store = UnobservableStore()
            setContent { CategoryRowUnderTest(store) }

            onNodeWithText("Visível").assertIsDisplayed()

            onNodeWithText("Alternar").performClick()
            onNodeWithText("Oculta").assertIsDisplayed()

            // Back again. This direction is the one that did nothing in the shipped build.
            onNodeWithText("Alternar").performClick()
            onNodeWithText("Visível").assertIsDisplayed()
        }

    /**
     * And the counterexample, so the test above is not passing for some incidental reason.
     *
     * Without a revision the value on disk changes and the screen does not — which is exactly what
     * the user saw. If this ever starts redrawing, the test above has stopped proving anything.
     */
    @Test
    fun `a preference change without a revision does not redraw`() =
        runComposeUiTest {
            val store = UnobservableStore()
            setContent { BrokenCategoryRow(store) }

            onNodeWithText("Visível").assertIsDisplayed()
            onNodeWithText("Alternar").performClick()

            // Still the old text: the store changed, the composition did not.
            onNodeWithText("Visível").assertIsDisplayed()
        }
}
