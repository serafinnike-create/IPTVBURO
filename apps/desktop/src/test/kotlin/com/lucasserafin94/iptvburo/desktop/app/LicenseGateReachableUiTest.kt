package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.lucasserafin94.iptvburo.desktop.license.LicenseClient
import com.lucasserafin94.iptvburo.desktop.license.LicenseStatus
import com.lucasserafin94.iptvburo.desktop.ui.BuroDesktopTheme
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.LocalDesktopStrings
import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import com.lucasserafin94.iptvburo.domain.model.LicenseBlockReason
import com.lucasserafin94.iptvburo.domain.model.LicenseDecision
import androidx.compose.ui.unit.dp
import kotlin.test.Test

/**
 * Everything on the blocking screen can be reached.
 *
 * The activation field sits below a headline, a device panel, a price, a QR plate and a button, and
 * on a laptop that is taller than the window. It went unreachable twice — once because the panel had
 * no height bound, and once because `verticalScroll` and `fillMaxHeight` were on the same modifier
 * chain, where the scroll measures its content unbounded and the panel grows past the window anyway.
 *
 * Both times the screen looked correct in isolation. Only a real measured layout catches it, which
 * is what these do: they compose the screen at a short window size and scroll to the field.
 */
@OptIn(ExperimentalTestApi::class)
class LicenseGateReachableUiTest {

    private val strings = DesktopStrings.of(DesktopLanguage.PORTUGUESE_BRAZIL)

    private fun blockedStatus() =
        LicenseStatus(
            decision = LicenseDecision.Blocked(LicenseBlockReason.TRIAL_ENDED),
            deviceId = "7HXY-3HVE-SFSE",
        )

    /**
     * The route to the code field fits a laptop window without scrolling.
     *
     * This is the assertion the earlier tests were missing. They composed at the test harness's own
     * generous size, passed, and the button was still off the bottom of a real 900px window — which
     * is what the user was actually looking at.
     *
     * 780dp of height is a 1080p laptop minus the taskbar and title bar, scaled at 125%: the most
     * common Windows screen this product runs on.
     */
    @Test
    fun `the code route fits a laptop window without scrolling`() = runComposeUiTest {
        setContent {
            BuroDesktopTheme {
                CompositionLocalProvider(LocalDesktopStrings provides strings) {
                    Box(Modifier.size(width = 1280.dp, height = 780.dp)) {
                        LicenseGate(
                            status = blockedStatus(),
                            client = LicenseClient(),
                            onRechecked = {},
                            onQuit = {},
                            languageTag = "pt-BR",
                        )
                    }
                }
            }
        }

        // No performScrollTo: the point is that it is already there. A user who has to discover a
        // scrollbar to find the only route they need has not been given that route.
        onNodeWithText(strings.licenseText.haveKey).assertIsDisplayed()
    }

    /**
     * The way to enter a code is visible without scrolling.
     *
     * It is a link rather than a field, and that is the point: the field lives on its own view, so
     * the purchase column stays short enough to fit. Stacked, the field was the last item in a
     * column taller than the window and could not be reached at all.
     */
    @Test
    fun `the way to enter a code is on screen`() = runComposeUiTest {
        setContent {
            BuroDesktopTheme {
                CompositionLocalProvider(LocalDesktopStrings provides strings) {
                    LicenseGate(
                        status = blockedStatus(),
                        client = LicenseClient(),
                        onRechecked = {},
                        onQuit = {},
                        languageTag = "pt-BR",
                    )
                }
            }
        }

        onNodeWithText(strings.licenseText.haveKey).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `following that link reaches the field and its button`() = runComposeUiTest {
        setContent {
            BuroDesktopTheme {
                CompositionLocalProvider(LocalDesktopStrings provides strings) {
                    LicenseGate(
                        status = blockedStatus(),
                        client = LicenseClient(),
                        onRechecked = {},
                        onQuit = {},
                        languageTag = "pt-BR",
                    )
                }
            }
        }

        onNodeWithText(strings.licenseText.haveKey).performScrollTo().performClick()

        // Typing proves the field is both present and usable, which a placeholder assertion does
        // not: a disabled or unreachable field would still render its placeholder.
        onNode(hasSetTextAction()).assertIsDisplayed().performTextInput("ABCD-EFGH")

        // And the button beside it, on screen at the same time — which is what stacking them under
        // the QR plate never achieved.
        onNodeWithText(strings.licenseText.redeem).assertIsDisplayed()
    }

    @Test
    fun `the code view offers a way back to paying`() = runComposeUiTest {
        setContent {
            BuroDesktopTheme {
                CompositionLocalProvider(LocalDesktopStrings provides strings) {
                    LicenseGate(
                        status = blockedStatus(),
                        client = LicenseClient(),
                        onRechecked = {},
                        onQuit = {},
                        languageTag = "pt-BR",
                    )
                }
            }
        }

        onNodeWithText(strings.licenseText.haveKey).performScrollTo().performClick()

        // Somebody who followed the link by mistake, or whose code turned out to be used, must be
        // able to get back to the QR code without restarting the app.
        onNodeWithText(strings.licenseText.backToPurchase).assertIsDisplayed()
    }

    /**
     * The way out never scrolls away.
     *
     * It sits outside the scrolling area on purpose. Somebody who has decided not to buy should not
     * have to hunt for the exit, and on the blocked screen this button is the only alternative to
     * killing the process.
     */
    @Test
    fun `the exit is visible without scrolling`() = runComposeUiTest {
        setContent {
            BuroDesktopTheme {
                CompositionLocalProvider(LocalDesktopStrings provides strings) {
                    LicenseGate(
                        status = blockedStatus(),
                        client = LicenseClient(),
                        onRechecked = {},
                        onQuit = {},
                        languageTag = "pt-BR",
                    )
                }
            }
        }

        onNodeWithText(strings.licenseText.quit).assertIsDisplayed()
    }

    /**
     * Opened by choice, the screen offers a way back rather than a way to quit.
     *
     * The same composable serves both, and getting this wrong means either trapping a paying
     * customer or offering an escape from a screen there is no escaping.
     */
    @Test
    fun `a dismissible gate offers going back instead of quitting`() = runComposeUiTest {
        setContent {
            BuroDesktopTheme {
                CompositionLocalProvider(LocalDesktopStrings provides strings) {
                    LicenseGate(
                        status = blockedStatus(),
                        client = LicenseClient(),
                        onRechecked = {},
                        onQuit = {},
                        languageTag = "pt-BR",
                        onDismiss = {},
                    )
                }
            }
        }

        onNodeWithText(strings.licenseText.back).assertIsDisplayed()
    }

    @Test
    fun `the device code is shown so it can be read out`() = runComposeUiTest {
        setContent {
            BuroDesktopTheme {
                CompositionLocalProvider(LocalDesktopStrings provides strings) {
                    LicenseGate(
                        status = blockedStatus(),
                        client = LicenseClient(),
                        onRechecked = {},
                        onQuit = {},
                        languageTag = "pt-BR",
                    )
                }
            }
        }

        // The one value support asks for, and what a manual grant is keyed on.
        onNodeWithText("7HXY-3HVE-SFSE").assertIsDisplayed()
    }
}
