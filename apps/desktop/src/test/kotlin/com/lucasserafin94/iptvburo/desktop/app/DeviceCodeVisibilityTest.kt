package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The machine's code, reachable before anything is configured.
 *
 * This is what makes remote provisioning usable. A seller finds an install by its code, so a
 * customer who cannot set up their own playlist has to be able to read that code out — and until
 * now it appeared only in the licence screen, which opens when the trial has expired or from the
 * countdown chip. During the free days, the person who most needed help had no way to find it.
 */
class DeviceCodeVisibilityTest {
    private fun read(relative: String): String = Path.of(relative).readText()

    private val app = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DesktopApp.kt")
    private val state = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt")

    @Test
    fun `the profile screen offers the code`() {
        // The screen a customer with no playlist is actually looking at.
        assertTrue(
            app.contains("strings.shareStrings.screens.deviceCodeAction"),
            "the action has to be on the profile screen, not only in the licence gate",
        )
        assertTrue(app.contains("showingDeviceCode = true"), "and it opens something")
        assertTrue(app.contains("deviceCode = appState.deviceCode"), "with this machine's code")
    }

    @Test
    fun `the playlist form offers it too, not only the screen behind it`() {
        // Where somebody who cannot fill in three fields actually gives up. Offering the code only
        // on the profile gate means they have to go back to a screen they already left.
        val onboarding = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/OnboardingFlow.kt")
        assertTrue(
            onboarding.contains("text.shareStrings.screens.deviceCodeAction"),
            "the form itself has to offer it",
        )
        assertTrue(app.contains("deviceCode = appState.deviceCode,"), "and be given the code")
        assertTrue(
            app.contains("onShowDeviceCode = { setupShowingDeviceCode = true }"),
            "and a way to ask for it",
        )
        assertTrue(
            app.contains("if (setupShowingDeviceCode) {"),
            "and the dialog has to actually be drawn, or the button does nothing",
        )
    }

    @Test
    fun `a machine with no code yet offers nothing rather than something blank`() {
        // It is read aloud. A blank code finds nobody in the panel and wastes the customer's time
        // on a support message that cannot be acted on.
        val onboarding = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/OnboardingFlow.kt")
        assertTrue(onboarding.contains("if (deviceCode.isNotBlank()) {"))
    }

    @Test
    fun `both screens show the same dialog rather than a copy each`() {
        // Two copies drift, and the likeliest drift is one of them losing the line that says what
        // the code is for — the half that makes it useful to the person reading it.
        assertTrue(app.contains("private fun DeviceCodeDialog("), "one composable")
        assertTrue(
            app.split("DeviceCodeDialog(").size - 1 >= 3,
            "declared once and used from both screens",
        )
    }

    @Test
    fun `the code does not depend on a licence answer`() {
        // It has to be readable before the first check comes back, and on a machine with no
        // network — which is one of the situations where somebody most needs to read it out.
        val marker = "val deviceCode: String by lazy {"
        assertTrue(state.contains(marker), "the accessor was renamed; this test needs updating")
        val body = state.substringAfter(marker).substringBefore("\n    }")
        assertTrue(
            body.contains("DeviceFingerprint.deviceId()"),
            "read from the identity, not from licenseStatus",
        )
        assertFalse(body.contains("licenseStatus"), "a licence answer must not gate it")
    }

    @Test
    fun `a failure to read it does not crash the screen`() {
        val marker = "val deviceCode: String by lazy {"
        assertTrue(state.contains(marker), "the accessor was renamed; this test needs updating")
        assertTrue(
            state.substringAfter(marker).substringBefore("\n    }").contains("runCatching"),
            "the profile screen must open even if the identity cannot be created",
        )
    }

    @Test
    fun `the code can be copied rather than transcribed`() {
        // It is read off a screen and sent by message; transcribing it by hand is where the
        // digits get wrong, and a wrong code finds nobody in the panel.
        assertTrue(app.contains("copyToClipboard(deviceCode)"))
    }

    @Test
    fun `the screen says what the code is for`() {
        // "7HXY-3HVE-SFSE" alone means nothing to the person reading it.
        assertTrue(app.contains("strings.shareStrings.screens.deviceCodeHelp"))
    }

    @Test
    fun `the labels are translated rather than written into the screen`() {
        val tables = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/ui/DesktopStrings.kt")
        // Five locales: es, pt-BR, en, de, it.
        listOf("deviceCodeAction", "deviceCodeHelp").forEach { key ->
            assertTrue(
                tables.split("$key = ").size - 1 == 5,
                "every locale needs $key, or one language fails to compile",
            )
        }
        assertFalse(app.contains("\"Código do aparelho\""), "the label belongs in the string tables")
    }
}
