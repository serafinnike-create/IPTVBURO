package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class MediaKindTest {
    @Test
    fun `every legacy video kind maps both ways`() {
        val catalog = mapOf(
            CatalogContentType.LIVE to MediaKind.LIVE_TV,
            CatalogContentType.MOVIE to MediaKind.MOVIE,
            CatalogContentType.SERIES to MediaKind.SERIES,
            CatalogContentType.EPISODE to MediaKind.VIDEO_EPISODE,
            CatalogContentType.UNKNOWN to MediaKind.UNKNOWN,
        )
        catalog.forEach { (legacy, universal) ->
            assertEquals(universal, legacy.toMediaKind())
            assertEquals(legacy, universal.toLegacyCatalogContentType())
        }

        val identities = mapOf(
            ContentKind.LIVE to MediaKind.LIVE_TV,
            ContentKind.MOVIE to MediaKind.MOVIE,
            ContentKind.SERIES to MediaKind.SERIES,
            ContentKind.EPISODE to MediaKind.VIDEO_EPISODE,
            ContentKind.UNKNOWN to MediaKind.UNKNOWN,
        )
        identities.forEach { (legacy, universal) ->
            assertEquals(universal, legacy.toMediaKind())
            assertEquals(legacy, universal.toLegacyContentKind())
        }
    }

    @Test
    fun `audio and future kinds never masquerade as legacy video`() {
        MediaKind.entries
            .filterNot {
                it in setOf(
                    MediaKind.LIVE_TV,
                    MediaKind.MOVIE,
                    MediaKind.SERIES,
                    MediaKind.VIDEO_EPISODE,
                )
            }
            .forEach { kind ->
                assertEquals(CatalogContentType.UNKNOWN, kind.toLegacyCatalogContentType(), kind.name)
                assertEquals(ContentKind.UNKNOWN, kind.toLegacyContentKind(), kind.name)
            }
    }
}
