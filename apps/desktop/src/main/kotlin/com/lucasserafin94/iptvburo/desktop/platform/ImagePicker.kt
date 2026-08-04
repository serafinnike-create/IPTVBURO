package com.lucasserafin94.iptvburo.desktop.platform

import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path

/**
 * Asks the user for a picture using the platform's own file dialog.
 *
 * AWT's FileDialog is used rather than Swing's JFileChooser because on Windows it is the real
 * Explorer dialog: it matches what the rest of the system looks like and understands shortcuts,
 * pinned folders and OneDrive paths that the Swing chooser presents inconsistently.
 *
 * Returns null when the user cancels, which is a normal outcome and not an error.
 */
fun chooseImageFile(
    owner: Frame?,
    title: String,
): Path? {
    val dialog =
        FileDialog(owner, title, FileDialog.LOAD).apply {
            // Advisory on Windows, where the dialog has no format filter of its own. The real
            // defence is that the store refuses anything ImageIO cannot decode.
            setFilenameFilter { _, name -> name.lowercase().endsWithImageExtension() }
            isMultipleMode = false
        }
    dialog.isVisible = true

    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return runCatching { Path.of(directory, file) }.getOrNull()
}

private fun String.endsWithImageExtension(): Boolean =
    IMAGE_EXTENSIONS.any { extension -> endsWith(extension) }

private val IMAGE_EXTENSIONS = listOf(".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp")
