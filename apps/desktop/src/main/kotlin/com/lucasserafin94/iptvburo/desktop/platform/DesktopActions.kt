package com.lucasserafin94.iptvburo.desktop.platform

import com.lucasserafin94.iptvburo.domain.model.ExternalContentLauncher
import com.lucasserafin94.iptvburo.domain.model.ExternalLaunchTarget
import com.lucasserafin94.iptvburo.domain.model.LaunchDecision
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

/**
 * Asks where to write an M3U.
 *
 * The chosen path is only a destination; nothing is written by this function. The sensitive-URL
 * warning required by GDD 8 section 17 is raised before the file is produced, so picking a
 * destination must not itself commit the user to exporting.
 */
fun chooseM3uDestination(
    owner: Frame?,
    title: String,
    suggestedName: String,
): Path? {
    val dialog =
        FileDialog(owner, title, FileDialog.SAVE).apply {
            // The extension is appended below rather than trusted from the dialog: the native save
            // dialog does not add one, and a playlist saved without .m3u will not reopen.
            file = suggestedName
            isVisible = true
        }
    val directory = dialog.directory ?: return null
    val name = dialog.file ?: return null
    val withExtension =
        if (name.endsWith(".m3u", ignoreCase = true) || name.endsWith(".m3u8", ignoreCase = true)) {
            name
        } else {
            "$name.m3u"
        }
    return Path.of(directory, withExtension)
}

// openChannelExternally used to sit here: it took a Channel and handed channel.streamUri straight
// to Desktop.browse() with no check on the scheme, unlike openStreamingOfferExternally beside it,
// which routes every address through ExternalContentLauncher.decide(). A stream URI comes from an
// M3U — a file the app did not write — and on Windows browse() with a file: URI opens the target in
// whatever application is registered for it. Nothing called the function, so nothing was exploitable
// through it; it was removed rather than left as a loaded trap for the next caller, who would have
// had no reason to suspect the asymmetry with the function next to it.

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

/**
 * Opens a streaming service's own app or website for a title — GDD 9.
 *
 * The safety decision is not made here. [ExternalContentLauncher] owns it, in the domain, where it
 * is tested without a desktop; this function only carries out a decision already made. Keeping the
 * split means there is no path from a discovery result to `browse()` that skips the checks: a
 * refused target returns [ExternalOpenResult.Refused] and nothing is opened.
 *
 * The app never plays these titles itself. Handing the user to the official service is the whole
 * feature, and a stream URL reaching this function would mean something upstream got that wrong —
 * which is why the launcher refuses one rather than opening it.
 */
fun openStreamingOfferExternally(target: ExternalLaunchTarget): ExternalOpenResult =
    when (val decision = ExternalContentLauncher.decide(target)) {
        is LaunchDecision.OpenApp ->
            runCatching { URI(decision.uri) }
                .fold(onSuccess = ::openUriExternally, onFailure = { ExternalOpenResult.Failed })

        is LaunchDecision.OpenWeb ->
            runCatching { URI(decision.url) }
                .fold(onSuccess = ::openUriExternally, onFailure = { ExternalOpenResult.Failed })

        is LaunchDecision.Unavailable -> ExternalOpenResult.Refused
    }

enum class ExternalOpenResult {
    Opened,
    NotSupported,
    Failed,

    /**
     * The destination was rejected before anything was opened.
     *
     * Distinct from [Failed] on purpose: Failed means the system could not open a legitimate
     * address, Refused means the address should never have been opened at all — a stream, a
     * credential-bearing URL, a local file. The two want different handling and, if they are ever
     * surfaced, very different wording.
     */
    Refused,
}
