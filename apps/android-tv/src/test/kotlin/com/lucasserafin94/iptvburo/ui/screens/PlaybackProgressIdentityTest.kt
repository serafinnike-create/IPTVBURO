package com.lucasserafin94.iptvburo.ui.screens

import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.PlaybackContentType
import com.lucasserafin94.iptvburo.ui.ChannelUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackProgressIdentityTest {
    @Test
    fun `live never creates playback progress`() {
        assertNull(playbackProgressIdentity("profile", channel(CatalogContentType.LIVE)))
    }

    @Test
    fun `movie and episode preserve stable provider identity`() {
        val movie = playbackProgressIdentity("profile", channel(CatalogContentType.MOVIE))
        val episode = playbackProgressIdentity(
            "profile",
            channel(CatalogContentType.EPISODE).copy(seriesId = "series-7", seasonNumber = 2, episodeNumber = 4),
        )
        assertEquals(PlaybackContentType.MOVIE, movie?.contentType)
        assertEquals(PlaybackContentType.EPISODE, episode?.contentType)
        assertEquals("series-7", episode?.seriesId)
        assertEquals(2, episode?.seasonNumber)
        assertEquals(4, episode?.episodeNumber)
    }

    private fun channel(type: CatalogContentType) = ChannelUi(
        id = "local-id",
        sourceId = "source",
        name = "Title",
        categoryName = null,
        contentType = type,
        providerItemId = "provider-id",
        logoUrl = null,
    )
}
