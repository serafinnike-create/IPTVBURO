package com.lucasserafin94.iptvburo.data.download

import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the one refusal that is a technical limit rather than a policy choice.
 *
 * ADR-008 removed the GDD 6 authorization conditions but kept the live refusal, because a live
 * stream never ends and a download of one would grow until storage fills. A future change that
 * widens the feature further must not silently take this with it.
 */
class AndroidDownloadManagerTest {
    private val manager =
        AndroidDownloadManager(
            contextProvider = { error("Storage is not resolved by these assertions") },
            client = OkHttpClient(),
            ioDispatcher = Dispatchers.Unconfined,
        )

    @Test
    fun `live content is refused`() {
        assertFalse(manager.isDownloadable(CatalogContentType.LIVE))
    }

    @Test
    fun `an unclassified item is refused because it may be a live stream`() {
        assertFalse(manager.isDownloadable(CatalogContentType.UNKNOWN))
    }

    @Test
    fun `vod is allowed without an offline entitlement from the source`() {
        assertTrue(manager.isDownloadable(CatalogContentType.MOVIE))
        assertTrue(manager.isDownloadable(CatalogContentType.SERIES))
        assertTrue(manager.isDownloadable(CatalogContentType.EPISODE))
    }
}
