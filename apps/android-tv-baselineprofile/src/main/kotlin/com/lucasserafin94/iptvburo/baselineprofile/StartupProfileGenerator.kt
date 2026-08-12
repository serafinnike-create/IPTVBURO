package com.lucasserafin94.iptvburo.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Records which classes and methods start-up actually touches.
 *
 * The measured problem this exists for: `classloader create took 1152ms` on a real phone, before a
 * line of app code runs. That is the cost of loading and verifying classes from the APK, and it is
 * paid on every cold start. A baseline profile lets the device AOT-compile exactly that set at
 * install time instead, which is the only lever that reaches it — moving work between threads does
 * not, because the work happens before any of our threads exist.
 *
 * The journey below is deliberately the *boring* one: launch, wait for the catalogue, let the home
 * settle. It has to mirror what a user's first seconds look like, because anything it does not
 * touch is not in the profile. Adding a detour through a screen most people never open would spend
 * profile budget on classes that do not affect the number being fixed.
 */
class StartupProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            // Cold every time: a warm start reuses classes already loaded, which is exactly the
            // cost being profiled, so a warm run would record a smaller set than reality needs.
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()

            // The home is composed after the catalogue query returns, and on a real list that takes
            // seconds. Returning before it renders would cut the profile off at the splash screen —
            // the classes that actually cost the 1.1s are the ones loaded on the way to first
            // content, so the run has to stay alive until that content exists.
            //
            // Waited on rather than slept through, but with a generous timeout: this drives a real
            // provider catalogue whose size varies, and a fixed sleep would either truncate the
            // recording on a slow list or waste a minute on a fast one.
            device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), UI_TIMEOUT_MILLIS)
            device.waitForIdle(IDLE_TIMEOUT_MILLIS)

            // One scroll, because the home is a scrolling surface and the row recycler, image
            // decoder and painter classes only load when something actually moves. Guarded: a
            // profile run must not fail because a device rendered nothing scrollable.
            runCatching {
                device.swipe(
                    device.displayWidth / 2,
                    (device.displayHeight * 0.75).toInt(),
                    device.displayWidth / 2,
                    (device.displayHeight * 0.25).toInt(),
                    SCROLL_STEPS,
                )
                device.waitForIdle(IDLE_TIMEOUT_MILLIS)
            }
        }
    }

    private companion object {
        /**
         * The release application id, without the debug suffix.
         *
         * The profile is generated against the build the user installs; `.debug` would record a
         * different package and produce a profile the shipped APK cannot use.
         */
        val TARGET_PACKAGE: String =
            InstrumentationRegistry.getArguments().getString("targetAppId")
                ?: "com.lucasserafin94.iptvburo"

        const val UI_TIMEOUT_MILLIS = 30_000L
        const val IDLE_TIMEOUT_MILLIS = 15_000L
        const val SCROLL_STEPS = 10
    }
}
