package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A label squeezed until it breaks one letter per line.
 *
 * Reported twice from the settings panel, in two unrelated places, as the same shape of mistake: a
 * plain `Row` holding several labels. A Row measures its children in order and gives each as much
 * width as it asks for, so the last one is left with whatever remains — and a `Text` handed a few
 * pixels does not overflow or ellipsise, it wraps, one character at a time, into a vertical column
 * of letters down the edge of the panel.
 *
 * It happened to the guide link beside the TMDb hint ("como obter?" became "c/o/m/o…") and to the
 * last cache size ("16 GB" became "1/6/G/B"). Both are fixed the same way: wrap with `FlowRow` so a
 * label that does not fit moves to the next line whole, and set `softWrap = false` on labels that
 * are single values, so they can never be broken even when the window is narrower still.
 *
 * These tests pin the mechanism rather than either call site, because the next place somebody adds a
 * Row of labels is the next place this appears.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsLabelWrapUiTest {
    /**
     * The defect, reproduced: a Row too narrow for both labels.
     *
     * This is what the panel used to do, and it is here to prove the assertion below can actually
     * fail — a test that only ever sees the fixed layout cannot tell a working `softWrap` from a
     * broken assertion.
     */
    @Test
    fun `a plain row squeezes the trailing label into many lines`() =
        runComposeUiTest {
            setContent {
                Column(modifier = Modifier.width(PANEL_WIDTH)) {
                    Row {
                        Text(text = LONG_HINT)
                        Text(text = SHORT_LINK, modifier = Modifier.testTag("link"))
                    }
                }
            }

            val height = onNodeWithTag("link").getBoundsInRoot().let { it.bottom - it.top }
            assertTrue(
                height > SINGLE_LINE_CEILING,
                "Expected the squeezed label to wrap into a tall stack, measured $height.",
            )
        }

    /** The fix: wrapped, and refusing to break the short label. */
    @Test
    fun `a flow row keeps the trailing label on one line`() =
        runComposeUiTest {
            setContent {
                Column(modifier = Modifier.width(PANEL_WIDTH)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = LONG_HINT)
                        Text(
                            text = SHORT_LINK,
                            softWrap = false,
                            modifier = Modifier.testTag("link"),
                        )
                    }
                }
            }

            val height = onNodeWithTag("link").getBoundsInRoot().let { it.bottom - it.top }
            assertTrue(
                height <= SINGLE_LINE_CEILING,
                "Expected the label to stay on one line, measured $height.",
            )
        }

    /**
     * The cache sizes, which is the second place it happened.
     *
     * Four pills where the panel fits about three: the point is that the one pushed past the edge
     * keeps its own height instead of becoming a letter per line.
     */
    @Test
    fun `the last cache size keeps its label on one line`() =
        runComposeUiTest {
            setContent {
                Column(modifier = Modifier.width(PANEL_WIDTH)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("Não guardar", "2 GB", "8 GB", "16 GB").forEach { label ->
                            Text(
                                text = label,
                                softWrap = false,
                                maxLines = 1,
                                modifier = Modifier.testTag("size:$label"),
                            )
                        }
                    }
                }
            }

            val height = onNodeWithTag("size:16 GB").getBoundsInRoot().let { it.bottom - it.top }
            assertTrue(
                height <= SINGLE_LINE_CEILING,
                "Expected \"16 GB\" to stay on one line, measured $height.",
            )
        }

    private companion object {
        /**
         * About the width the settings panel gives its content, which is what makes the labels
         * below compete for it rather than all fitting comfortably.
         */
        val PANEL_WIDTH = 320.dp

        /**
         * Comfortably above one line of body text and far below a stack of letters.
         *
         * "como obter?" broken up is eleven lines; at any ordinary text size that is well past this,
         * while a single line stays under it whatever the platform's default font turns out to be.
         */
        val SINGLE_LINE_CEILING = 40.dp

        /** Long enough to take the whole width on its own, as the real TMDb hint does. */
        const val LONG_HINT = "Cole a sua chave de themoviedb.org/settings/api"

        /** The label that used to be left with nothing: short, and two words. */
        const val SHORT_LINK = "Não sabe como obter?"
    }
}
