package com.lucasserafin94.iptvburo.domain.model

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Stable universal identity. Authenticated URLs are reduced to a non-reversible digest. */
@JvmInline
value class MediaIdentity(val key: String) {
    init {
        require(key.isNotBlank())
    }

    companion object {
        fun video(kind: ContentKind, title: String, year: Int? = null): MediaIdentity {
            require(kind != ContentKind.UNKNOWN)
            return MediaIdentity(ContentIdentity.of(kind, title, year).key)
        }

        fun track(
            artist: String?,
            album: String?,
            discNumber: Int?,
            trackNumber: Int?,
            title: String,
            durationMillis: Long?,
        ): MediaIdentity =
            MediaIdentity(
                listOf(
                    "track",
                    IDENTITY_VERSION,
                    slugOrUnknown(artist),
                    slugOrUnknown(album),
                    (discNumber ?: 0).coerceAtLeast(0).toString(),
                    (trackNumber ?: 0).coerceAtLeast(0).toString(),
                    slugOrUnknown(title),
                    (durationMillis ?: 0L).coerceAtLeast(0L).toString(),
                ).joinToString(":"),
            )

        fun album(artist: String?, title: String, year: Int? = null): MediaIdentity =
            MediaIdentity("album:$IDENTITY_VERSION:${slugOrUnknown(artist)}:${slugOrUnknown(title)}:${year ?: 0}")

        fun artist(name: String): MediaIdentity =
            MediaIdentity("artist:$IDENTITY_VERSION:${slugOrUnknown(name)}")

        fun radio(streamUrl: String): MediaIdentity =
            MediaIdentity("radio:$IDENTITY_VERSION:${remoteLocatorDigest(streamUrl)}")

        fun podcast(feedUrl: String): MediaIdentity =
            MediaIdentity("podcast:$IDENTITY_VERSION:${remoteLocatorDigest(feedUrl)}")

        fun podcastEpisode(
            feedUrl: String,
            guid: String?,
            enclosureUrl: String?,
        ): MediaIdentity {
            val episodeSeed = guid?.takeIf(String::isNotBlank)?.let(::stableGuidDigest)
                ?: enclosureUrl?.takeIf(String::isNotBlank)?.let(::remoteLocatorDigest)
                ?: error("A podcast episode needs a GUID or enclosure URL.")
            return MediaIdentity(
                "podcast-episode:$IDENTITY_VERSION:${remoteLocatorDigest(feedUrl)}:$episodeSeed",
            )
        }

        fun audiobook(author: String?, title: String, year: Int? = null): MediaIdentity =
            MediaIdentity(
                "audiobook:$IDENTITY_VERSION:${slugOrUnknown(author)}:${slugOrUnknown(title)}:${year ?: 0}",
            )

        fun chapter(book: MediaIdentity, index: Int, title: String): MediaIdentity {
            require(book.key.startsWith("audiobook:$IDENTITY_VERSION:"))
            require(index >= 0)
            return MediaIdentity(
                "chapter:$IDENTITY_VERSION:${digest(book.key)}:$index:${slugOrUnknown(title)}",
            )
        }

        private fun remoteLocatorDigest(rawUrl: String): String {
            val uri = URI(rawUrl.trim())
            require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true))
            require(!uri.host.isNullOrBlank())
            // User-info and query are deliberately excluded. The path is retained only inside a
            // digest because several legal providers place account material in path segments.
            val normalized = buildString {
                append(uri.scheme.lowercase())
                append("://")
                append(uri.host.lowercase())
                if (uri.port >= 0 && !uri.usesDefaultPort()) append(":${uri.port}")
                append(uri.normalize().rawPath.orEmpty().ifBlank { "/" })
            }
            return digest(normalized)
        }

        /** RSS GUIDs are often URLs; normalize those so a rotating auth query is not identity. */
        private fun stableGuidDigest(rawGuid: String): String =
            runCatching {
                val uri = URI(rawGuid.trim())
                if (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
                    remoteLocatorDigest(rawGuid)
                } else {
                    digest(rawGuid.trim())
                }
            }.getOrElse { digest(rawGuid.trim()) }

        private fun URI.usesDefaultPort(): Boolean =
            (scheme.equals("http", true) && port == 80) ||
                (scheme.equals("https", true) && port == 443)

        private fun slugOrUnknown(value: String?): String =
            value?.let(ContentIdentity::slugify)?.takeIf(String::isNotBlank) ?: "unknown"

        private fun digest(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(StandardCharsets.UTF_8))
                .take(DIGEST_BYTES)
                .joinToString("") { byte -> "%02x".format(byte) }

        /** New namespaces are versioned before any value reaches persistence. Video keys stay v0. */
        private const val IDENTITY_VERSION = "v1"
        private const val DIGEST_BYTES = 12
    }
}
