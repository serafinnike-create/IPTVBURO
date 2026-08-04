package com.lucasserafin94.iptvburo.desktop.platform

import com.lucasserafin94.iptvburo.domain.model.Channel
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.net.URI
import java.nio.file.Path

fun chooseLocalPlaylist(owner: Frame?): Path? {
    val dialog =
        FileDialog(owner, "Importar lista M3U/M3U8", FileDialog.LOAD).apply {
            filenameFilter =
                java.io.FilenameFilter { _, name ->
                    name.endsWith(".m3u", ignoreCase = true) ||
                        name.endsWith(".m3u8", ignoreCase = true)
                }
            isMultipleMode = false
            isVisible = true
        }
    return dialog.files.firstOrNull()?.toPath()
}

/**
 * Asks for an M3U under a caller-supplied title.
 *
 * Separate from [chooseLocalPlaylist] only because that one names itself "Importar lista" in the
 * dialog chrome, which is the wrong prompt when the file being picked is a music playlist and the
 * app is running in one of the other three languages.
 */
fun chooseM3uFile(
    owner: Frame?,
    title: String,
): Path? {
    val dialog =
        FileDialog(owner, title, FileDialog.LOAD).apply {
            filenameFilter =
                java.io.FilenameFilter { _, name ->
                    name.endsWith(".m3u", ignoreCase = true) ||
                        name.endsWith(".m3u8", ignoreCase = true)
                }
            isMultipleMode = false
            isVisible = true
        }
    return dialog.files.firstOrNull()?.toPath()
}

fun openChannelExternally(channel: Channel): ExternalOpenResult {
    val uri =
        runCatching { URI(channel.streamUri) }
            .getOrElse { return ExternalOpenResult.Failed }
    return openUriExternally(uri)
}

/**
 * Uses the registered desktop URI handler without constructing a command line.
 *
 * Credential-bearing Xtream URIs must be created immediately before this call and discarded by
 * the caller immediately afterwards.
 */
fun openUriExternally(uri: URI): ExternalOpenResult {
    if (!Desktop.isDesktopSupported()) return ExternalOpenResult.NotSupported
    val desktop = Desktop.getDesktop()
    if (!desktop.isSupported(Desktop.Action.BROWSE)) return ExternalOpenResult.NotSupported

    return runCatching {
        desktop.browse(uri)
    }.fold(
        onSuccess = { ExternalOpenResult.Opened },
        onFailure = { ExternalOpenResult.Failed },
    )
}

enum class ExternalOpenResult {
    Opened,
    NotSupported,
    Failed,
}
