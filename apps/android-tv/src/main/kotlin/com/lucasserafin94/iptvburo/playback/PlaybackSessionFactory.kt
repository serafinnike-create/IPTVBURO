package com.lucasserafin94.iptvburo.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.lucasserafin94.iptvburo.ui.ChannelUi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

@OptIn(markerClass = [UnstableApi::class])
@Singleton
class PlaybackSessionFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    fun create(channel: ChannelUi): ExoPlayer {
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(USER_AGENT)
            .setDefaultRequestProperties(channel.requestHeaders)
        val mediaSourceFactory =
            DefaultMediaSourceFactory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(
                    DefaultLoadErrorHandlingPolicy(MINIMUM_RETRY_COUNT),
                )
        val renderersFactory =
            DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true)

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setSeekBackIncrementMs(SEEK_BACK_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
            .build()
            .apply {
                setMediaItem(
                    MediaItem.Builder()
                        .setUri(channel.streamUrl)
                        .setMimeType(channel.streamUrl.inferredMimeType())
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(channel.name)
                                .build(),
                        )
                        .build(),
                )
                playWhenReady = true
                prepare()
            }
    }

    private fun String.inferredMimeType(): String? =
        substringBefore('?')
            .substringAfterLast('.', "")
            .lowercase()
            .let { extension ->
                when (extension) {
                    "m3u8" -> MimeTypes.APPLICATION_M3U8
                    "ts", "m2ts" -> MimeTypes.VIDEO_MP2T
                    "mp4", "m4v" -> MimeTypes.VIDEO_MP4
                    "mkv" -> MimeTypes.VIDEO_MATROSKA
                    "avi" -> MimeTypes.VIDEO_AVI
                    else -> null
                }
            }

    private companion object {
        const val USER_AGENT = "IPTV BURO/0.2 Android"
        const val SEEK_BACK_MS = 15_000L
        const val SEEK_FORWARD_MS = 30_000L
        const val MINIMUM_RETRY_COUNT = 3
    }
}
