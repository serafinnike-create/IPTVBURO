package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.lucasserafin94.iptvburo.desktop.license.LicenseClient
import com.lucasserafin94.iptvburo.desktop.license.LicenseStatus
import com.lucasserafin94.iptvburo.desktop.ui.BuroDesktopTheme
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.LocalDesktopStrings
import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import com.lucasserafin94.iptvburo.domain.model.LicenseBlockReason
import com.lucasserafin94.iptvburo.domain.model.LicenseDecision
import kotlin.test.Test

/** Guards the cinematic backdrop on the two full-screen waits where it is easiest to regress. */
@OptIn(ExperimentalTestApi::class)
class CinematicPosterWallUiTest {
    @Test
    fun `startup renders the bundled moving poster wall before a catalogue exists`() =
        runComposeUiTest {
            setContent {
                BuroDesktopTheme {
                    CompositionLocalProvider(
                        LocalDesktopStrings provides DesktopStrings.of(DesktopLanguage.ENGLISH),
                    ) {
                        SplashScreen(
                            message = "Loading films",
                            progress = 0.8f,
                            backdropPosters = emptyList(),
                        )
                    }
                }
            }

            onNodeWithTag(POSTER_WALL_TAG).assertIsDisplayed()
            onNodeWithTag(LOCAL_POSTER_WALL_TAG).assertIsDisplayed()
            onNodeWithText("Loading films").assertIsDisplayed()
        }

    @Test
    fun `payment lock keeps the poster wall behind the activation content`() =
        runComposeUiTest {
            setContent {
                BuroDesktopTheme {
                    CompositionLocalProvider(
                        LocalDesktopStrings provides DesktopStrings.of(DesktopLanguage.ENGLISH),
                    ) {
                        LicenseGate(
                            status =
                                LicenseStatus(
                                    decision =
                                        LicenseDecision.Blocked(LicenseBlockReason.TRIAL_ENDED),
                                    deviceId = "TEST-BURO-WALL",
                                ),
                            client = LicenseClient(),
                            onRechecked = {},
                            onQuit = {},
                            languageTag = "en",
                            backdropPosters = emptyList(),
                        )
                    }
                }
            }

            onNodeWithTag(POSTER_WALL_TAG).assertIsDisplayed()
            onNodeWithTag(LOCAL_POSTER_WALL_TAG).assertIsDisplayed()
            onNodeWithText("TEST-BURO-WALL").assertIsDisplayed()
        }
}
