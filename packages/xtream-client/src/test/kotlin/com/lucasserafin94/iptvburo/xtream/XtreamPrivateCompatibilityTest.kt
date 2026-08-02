package com.lucasserafin94.iptvburo.xtream

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in smoke test for a user-authorized Xtream account.
 *
 * Credentials stay in process environment variables and are never committed, printed, or copied
 * into test reports. CI skips this test because the variables are intentionally absent.
 */
class XtreamPrivateCompatibilityTest {
    @Test
    fun `authorized private source supports the production client contract`() {
        val server = System.getenv(ENV_SERVER)
        val username = System.getenv(ENV_USERNAME)
        val password = System.getenv(ENV_PASSWORD)
        assumeTrue(
            "Private Xtream environment is not configured.",
            !server.isNullOrBlank() && !username.isNullOrBlank() && !password.isNullOrBlank(),
        )

        val credentials =
            XtreamCredentials(
                serverUrl = requireNotNull(server),
                username = requireNotNull(username),
                password = requireNotNull(password),
            )
        val client = XtreamClient()

        assertTrue(client.authenticate(credentials).authenticated)
        var firstLiveId: String? = null
        XtreamContentType.entries.forEach { contentType ->
            assertTrue(client.categories(credentials, contentType).items.isNotEmpty())
            val catalog = client.catalog(credentials, contentType)
            assertTrue(catalog.items.isNotEmpty())
            if (contentType == XtreamContentType.LIVE) firstLiveId = catalog.items.first().providerId
        }
        firstLiveId?.let { streamId -> client.shortEpg(credentials, streamId) }
    }

    private companion object {
        const val ENV_SERVER = "IPTV_BURO_PRIVATE_XTREAM_SERVER"
        const val ENV_USERNAME = "IPTV_BURO_PRIVATE_XTREAM_USERNAME"
        const val ENV_PASSWORD = "IPTV_BURO_PRIVATE_XTREAM_PASSWORD"
    }
}
