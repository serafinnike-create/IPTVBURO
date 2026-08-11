package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.playback.DesktopPlaybackRequest
import com.lucasserafin94.iptvburo.desktop.playback.MultiviewBarForTesting
import com.lucasserafin94.iptvburo.desktop.playback.MultiviewTile
import com.lucasserafin94.iptvburo.desktop.ui.BuroDesktopTheme
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.LocalDesktopStrings
import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The two window controls in the multiview bar, at real widths.
 *
 * Both have gone missing on the user's screen, and neither time did reading the code show it: once
 * four intrinsic channel widths pushed full-screen and close off the right edge, and once a build
 * without the fix was installed. Closing the grid then meant closing the app.
 *
 * These compose the real bar at fixed widths, because a test harness left to choose its own size is
 * generous enough that controls fit when they would not on a laptop.
 */
@OptIn(ExperimentalTestApi::class)
class MultiviewBarControlsUiTest {

    private val strings = DesktopStrings.of(DesktopLanguage.PORTUGUESE_BRAZIL)

    private fun tiles(count: Int, titleLength: Int = 14): List<MultiviewTile> =
        (1..count).map { index ->
            MultiviewTile(
                providerId = "channel-$index",
                request =
                    DesktopPlaybackRequest(
                        uri = URI("http://127.0.0.1/placeholder"),
                        title = "Canal $index",
                    ),
                // Long names are the ordinary case in a real list — "AMC FHD [H265]", "AXN HD [LEG]"
                // — and they are what pushed the controls off the edge the first time.
                title = "Canal ${"$index".repeat(1)} ${"X".repeat(titleLength)}",
            )
        }

    @Test
    fun `close and full screen are reachable with two tiles`() = runComposeUiTest {
        var closed = 0
        var toggled = 0

        setContent {
            BuroDesktopTheme {
                CompositionLocalProvider(LocalDesktopStrings provides strings) {
                    Box(Modifier.width(900.dp)) {
                        MultiviewBarForTesting(
                            tiles = tiles(2),
                            audioProviderId = "channel-1",
                            onClose = { closed += 1 },
                            onToggleFullScreen = { toggled += 1 },
                        )
                    }
                }
            }
        }

        onNodeWithContentDescription(strings.close).assertIsDisplayed().performClick()
        onNodeWithContentDescription(strings.settingsText.multiviewFullScreen)
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, closed, "closing the grid must not mean closing the app")
        assertEquals(1, toggled, "the grid must offer full screen the way the single player does")
    }

    /**
     * Four long channel names is the case that broke it.
     *
     * The cap is four tiles, so this is the widest the bar ever gets, and a narrow window is where
     * the controls were lost — measured at 900dp rather than at whatever the harness would pick.
     */
    @Test
    fun `close and full screen survive four long channel names`() = runComposeUiTest {
        var closed = 0

        setContent {
            BuroDesktopTheme {
                CompositionLocalProvider(LocalDesktopStrings provides strings) {
                    Box(Modifier.width(900.dp)) {
                        MultiviewBarForTesting(
                            tiles = tiles(4, titleLength = 26),
                            audioProviderId = "channel-1",
                            onClose = { closed += 1 },
                        )
                    }
                }
            }
        }

        onNodeWithContentDescription(strings.close).assertIsDisplayed().performClick()

        assertEquals(1, closed)
    }

    /** The same on a narrow laptop, where there is least room to lose. */
    @Test
    fun `the controls survive a narrow window`() = runComposeUiTest {
        var closed = 0

        setContent {
            BuroDesktopTheme {
                CompositionLocalProvider(LocalDesktopStrings provides strings) {
                    Box(Modifier.width(640.dp)) {
                        MultiviewBarForTesting(
                            tiles = tiles(4, titleLength = 20),
                            audioProviderId = "channel-1",
                            onClose = { closed += 1 },
                        )
                    }
                }
            }
        }

        onNodeWithContentDescription(strings.close).assertIsDisplayed().performClick()

        assertEquals(1, closed)
    }

    /**
     * In full screen the button offers the way back, not the way in.
     *
     * A control that still said "full screen" while already full screen is how somebody ends up
     * with no visible exit at all.
     */
    @Test
    fun `full screen offers the way back once entered`() = runComposeUiTest {
        var toggled = 0

        setContent {
            BuroDesktopTheme {
                CompositionLocalProvider(LocalDesktopStrings provides strings) {
                    Box(Modifier.width(900.dp)) {
                        MultiviewBarForTesting(
                            tiles = tiles(2),
                            audioProviderId = "channel-1",
                            isFullScreen = true,
                            onToggleFullScreen = { toggled += 1 },
                        )
                    }
                }
            }
        }

        onNodeWithContentDescription(strings.settingsText.multiviewWindowed)
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, toggled)
    }
}
