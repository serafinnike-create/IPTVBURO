package com.lucasserafin94.iptvburo.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.PlaybackBuffering
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
    fun create(channel: ChannelUi, autoPlay: Boolean = true): ExoPlayer {
        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(USER_AGENT)
            .setDefaultRequestProperties(channel.requestHeaders)
        // Wrapped rather than used directly. OkHttp speaks only HTTP, so a downloaded title —
        // which is a file:// URI — could never be opened and every offline playback failed with
        // "could not play this stream". DefaultDataSource keeps HTTP going through OkHttp and adds
        // file, content and asset handling on top.
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
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
            .setLoadControl(loadControlFor(channel))
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
                playWhenReady = autoPlay
                prepare()
            }
    }

    /**
     * How far ahead of the picture this stream reads.
     *
     * A film is a file, so the player can be two minutes ahead and simply not notice a connection
     * that drops and comes back — which is the most visible failure this app has. A live channel
     * has no ahead to read, so the same buffer there buys nothing and costs a later start and a
     * picture that sits behind.
     */
    private fun loadControlFor(channel: ChannelUi): DefaultLoadControl {
        val readAhead = PlaybackBuffering.millisFor(isLive = channel.isLiveStream())
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                // The floor before playback may begin. Kept at Media3's own value: raising it
                // would delay the first frame by the whole read-ahead, which is the opposite of
                // what this is for — the buffer should fill behind a picture that is already up.
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                readAhead,
                // How much must arrive before the picture starts, and before it resumes after a
                // stall. Untouched for the same reason: these decide how quickly playback begins,
                // not how much protection it has once it is running.
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            ).build()
    }

    /**
     * Whether this stream is live.
     *
     * UNKNOWN counts as live, which is the safe way round: a film treated as live merely keeps the
     * smaller buffer it has today, while a channel treated as a film would start two minutes late.
     */
    private fun ChannelUi.isLiveStream(): Boolean =
        contentType == CatalogContentType.LIVE || contentType == CatalogContentType.UNKNOWN

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
