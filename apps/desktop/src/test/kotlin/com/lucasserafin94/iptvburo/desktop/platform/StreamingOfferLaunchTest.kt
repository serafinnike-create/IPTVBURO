package com.lucasserafin94.iptvburo.desktop.platform

import com.lucasserafin94.iptvburo.domain.model.ExternalLaunchTarget
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The platform side of GDD 9's launch rule.
 *
 * These cases never reach a browser: a refused target is rejected before `Desktop.browse` is
 * considered, which is exactly the property being pinned. Anything that would open is not asserted
 * here — it would launch a real browser on the machine running the tests.
 */
class StreamingOfferLaunchTest {
    @Test
    fun `a stream address is never handed to the system`() {
        val result =
            openStreamingOfferExternally(
                ExternalLaunchTarget(webUrl = "https://demo.example.invalid/video/master.m3u8"),
            )

        assertEquals(ExternalOpenResult.Refused, result)
    }

    @Test
    fun `an address carrying a token is never handed to the system`() {
        val result =
            openStreamingOfferExternally(
                ExternalLaunchTarget(webUrl = "https://demo.example.invalid/title/1?access_token=abc"),
            )

        assertEquals(ExternalOpenResult.Refused, result)
    }

    @Test
    fun `a local file is never handed to the system`() {
        val result = openStreamingOfferExternally(ExternalLaunchTarget(appDeepLink = "file:///C:/Users/someone/video.mkv"))

        assertEquals(ExternalOpenResult.Refused, result)
    }

    @Test
    fun `plain http is refused rather than opened in the clear`() {
        val result = openStreamingOfferExternally(ExternalLaunchTarget(webUrl = "http://demo.example.invalid/title/1"))

        assertEquals(ExternalOpenResult.Refused, result)
    }
}
