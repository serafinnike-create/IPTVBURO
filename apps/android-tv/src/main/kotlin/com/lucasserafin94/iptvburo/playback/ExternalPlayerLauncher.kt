package com.lucasserafin94.iptvburo.playback

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lucasserafin94.iptvburo.ui.ChannelUi

/**
 * Hands what is playing to another video player the user has installed — VLC, MX Player, Just
 * Player — the way the other IPTV players do.
 *
 * Android's own chooser picks the app, so BURO names none and favours none. The address goes out
 * whole, including the account it carries; the screen asks before this runs and says so, because
 * from here on the other app holds that address and BURO cannot take it back.
 *
 * The provider's request headers travel too, in the `headers` extra MX Player and Just Player read
 * (name, value, name, value…): a list that needs a User-Agent or Referer would otherwise fail in
 * the other player and look like BURO's fault.
 */
object ExternalPlayerLauncher {
    sealed interface Result {
        data object Opened : Result

        /** No installed app can play a video address. Common on a television. */
        data object NoPlayer : Result

        /** Nothing to hand over: the title has no address yet. */
        data object Unavailable : Result
    }

    fun open(
        context: Context,
        channel: ChannelUi,
        chooserTitle: String,
    ): Result {
        val address = channel.streamUrl.takeIf(::isPlayableAddress) ?: return Result.Unavailable
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(address), mimeTypeFor(address))
                putExtra(EXTRA_TITLE, channel.name)
                headerExtra(channel.requestHeaders)?.let { headers -> putExtra(EXTRA_HEADERS, headers) }
            }
        val chooser = Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            Result.Opened
        } catch (_: ActivityNotFoundException) {
            Result.NoPlayer
        }
    }

    /** Only a network address. A local file or any other scheme is not something to hand out. */
    internal fun isPlayableAddress(address: String): Boolean {
        val scheme = address.trim().substringBefore("://", "").lowercase()
        return scheme == "http" || scheme == "https"
    }

    /**
     * The type players register for, from the address alone.
     *
     * HLS and DASH are named, because several players register for those types and not for the
     * generic one; everything else — TS, MP4, MKV, an extensionless live address — goes as video.
     */
    internal fun mimeTypeFor(address: String): String {
        val path = address.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".m3u8") || path.endsWith(".m3u") -> "application/x-mpegURL"
            path.endsWith(".mpd") -> "application/dash+xml"
            else -> "video/*"
        }
    }

    /** Headers as the flat name/value array the players read, or null when there are none. */
    internal fun headerExtra(headers: Map<String, String>): Array<String>? =
        headers
            .filter { (name, value) -> name.isNotBlank() && value.isNotBlank() }
            .flatMap { (name, value) -> listOf(name, value) }
            .takeIf(List<String>::isNotEmpty)
            ?.toTypedArray()

    private const val EXTRA_TITLE = "title"
    private const val EXTRA_HEADERS = "headers"
}
