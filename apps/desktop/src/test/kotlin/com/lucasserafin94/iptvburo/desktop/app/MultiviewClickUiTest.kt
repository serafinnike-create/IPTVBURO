package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.lucasserafin94.iptvburo.desktop.ui.BuroDesktopTheme
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.LocalDesktopStrings
import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The multiview button, pressed.
 *
 * Four separate faults were found and fixed in multiview, and none of them was the one stopping the
 * user: instrumentation showed `openMultiview()` was never reached at all, while the "remove from
 * multiview" control was plainly visible on their screen — so channels were being queued and the
 * thing that opens the grid was not responding.
 *
 * Reading the wiring proved nothing; every link looked correct. This composes the real toolbar and
 * clicks the real button, which is the only way to tell a broken chain from a button that is simply
 * not where anybody looks.
 */
@OptIn(ExperimentalTestApi::class)
class MultiviewClickUiTest {

    private val strings = DesktopStrings.of(DesktopLanguage.PORTUGUESE_BRAZIL)

    @Test
    fun `the toolbar button reaches its handler`() = runComposeUiTest {
        var opened = 0

        setContent {
            BuroDesktopTheme {
                CompositionLocalProvider(LocalDesktopStrings provides strings) {
                    XtreamToolbarForTesting(
                        selectedType = XtreamContentType.LIVE,
                        multiviewCount = 3,
                        onOpenMultiview = { opened += 1 },
                    )
                }
            }
        }

        onNodeWithText("▦  ${strings.settingsText.multiviewOpen} (3)").performClick()

        assertEquals(1, opened, "the press did not reach openMultiview")
    }

    /**
     * The button is there before anything is queued.
     *
     * Gated on a count, nothing announced that multiview existed: the only other way in was a button
     * inside a channel's detail page, which somebody had to already know to look for.
     */
    @Test
    fun `the button invites use when nothing is queued`() = runComposeUiTest {
        var opened = 0

        setContent {
            BuroDesktopTheme {
                CompositionLocalProvider(LocalDesktopStrings provides strings) {
                    XtreamToolbarForTesting(
                        selectedType = XtreamContentType.LIVE,
                        multiviewCount = 0,
                        onOpenMultiview = { opened += 1 },
                    )
                }
            }
        }

        onNodeWithText("▦  ${strings.settingsText.multiviewHint}").assertIsDisplayed().performClick()

        assertEquals(1, opened, "an empty multiview must still open, to explain itself")
    }

    /**
     * The button survives a narrow toolbar.
     *
     * This is the fault that hid multiview for four rounds of fixes. A `Row` does not shrink children
     * that carry no weight: once the filters, the pickers and this chip exceeded the toolbar's width,
     * whatever sat last was laid out past the edge of the window — present in the composition,
     * clickable by a test, and invisible on a real screen.
     *
     * Every earlier test passed because a test harness has no competition for space. This one
     * constrains the width to something a laptop actually has.
     */
    @Test
    fun `the button is reachable in a narrow toolbar`() = runComposeUiTest {
        var opened = 0

        setContent {
            BuroDesktopTheme {
                CompositionLocalProvider(LocalDesktopStrings provides strings) {
                    Box(Modifier.width(900.dp)) {
                        XtreamToolbarForTesting(
                            selectedType = XtreamContentType.LIVE,
                            multiviewCount = 2,
                            onOpenMultiview = { opened += 1 },
                        )
                    }
                }
            }
        }

        onNodeWithText("▦  ${strings.settingsText.multiviewOpen} (2)")
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, opened, "the button was laid out off the edge of the toolbar")
    }

    @Test
    fun `films and series do not offer it`() = runComposeUiTest {
        setContent {
            BuroDesktopTheme {
                CompositionLocalProvider(LocalDesktopStrings provides strings) {
                    XtreamToolbarForTesting(
                        selectedType = XtreamContentType.MOVIE,
                        multiviewCount = 0,
                        onOpenMultiview = {},
                    )
                }
            }
        }

        // Four films at once is not a thing anybody wants, and four decoders is the heaviest thing
        // this app can be asked to do.
        assertTrue(
            onAllNodesWithText("▦  ${strings.settingsText.multiviewHint}")
                .fetchSemanticsNodes()
                .isEmpty(),
            "multiview must not appear outside live",
        )
    }
}
