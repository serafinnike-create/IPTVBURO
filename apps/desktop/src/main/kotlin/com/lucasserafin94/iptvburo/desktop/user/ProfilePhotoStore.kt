package com.lucasserafin94.iptvburo.desktop.user

import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * Profile photos, stored as small square PNGs beside the app's other per-user data.
 *
 * The user's original file is never referenced at playback time: it is decoded once, cropped to a
 * circle and written at a fixed size. A stored path would break as soon as the picture was moved or
 * the drive was unplugged, and would leave the app reading from arbitrary locations on disk.
 */
class ProfilePhotoStore(
    private val directory: Path = defaultDirectory(),
) {
    fun photoFor(profileId: String): Path? = fileFor(profileId).takeIf(Files::isRegularFile)

    fun remove(profileId: String) {
        runCatching { Files.deleteIfExists(fileFor(profileId)) }
    }

    /**
     * Decodes [source], crops it to a centred circle and stores it for [profileId].
     *
     * Returns null when the file is not an image this platform can read, which is the expected
     * outcome for a user who picks the wrong file rather than an error worth surfacing loudly.
     * Oversized images are refused before decoding: a decoded pixel buffer is width * height * 4
     * bytes, so a deliberately huge file would exhaust the heap.
     */
    fun store(profileId: String, source: Path): Path? {
        if (!Files.isRegularFile(source)) return null
        if (Files.size(source) > MAX_SOURCE_BYTES) return null

        val decoded = runCatching { ImageIO.read(source.toFile()) }.getOrNull() ?: return null
        if (decoded.width <= 0 || decoded.height <= 0) return null
        if (decoded.width.toLong() * decoded.height.toLong() > MAX_SOURCE_PIXELS) return null

        val circular = cropToCircle(decoded, EDGE_PIXELS)
        val target = fileFor(profileId)
        return runCatching {
            Files.createDirectories(directory)
            // Written to a temporary file and moved into place, so an interrupted write cannot leave
            // a truncated PNG that later fails to decode.
            val temporary = target.resolveSibling("${target.fileName}.tmp")
            Files.newOutputStream(temporary).use { output -> ImageIO.write(circular, "png", output) }
            Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            target
        }.getOrNull()
    }

    private fun fileFor(profileId: String): Path =
        directory.resolve("${profileId.replace(UNSAFE, "_").take(80)}.png")

    private companion object {
        /** Large enough for the biggest tile on a high-DPI screen, small enough to stay cheap. */
        const val EDGE_PIXELS = 256
        const val MAX_SOURCE_BYTES = 24L * 1024 * 1024
        const val MAX_SOURCE_PIXELS = 60_000_000L
        val UNSAFE = Regex("""[^A-Za-z0-9._-]""")

        fun defaultDirectory(): Path {
            val localAppData = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)
            val root =
                localAppData?.let(Path::of)
                    ?: Path.of(System.getProperty("user.home"), "AppData", "Local")
            return root.resolve("lucasserafin94").resolve("IPTVBURO").resolve("avatars")
        }
    }
}

/**
 * Centre-crops [source] to a square, scales it to [edge] and masks it to a circle.
 *
 * Cropping before scaling keeps the subject's proportions: scaling a rectangle straight to a square
 * would stretch a face. The circle is applied here rather than at draw time so every consumer gets
 * the same shape without repeating the clip.
 */
internal fun cropToCircle(source: BufferedImage, edge: Int): BufferedImage {
    val side = minOf(source.width, source.height)
    val left = (source.width - side) / 2
    val top = (source.height - side) / 2
    val square = source.getSubimage(left, top, side, side)

    val output = BufferedImage(edge, edge, BufferedImage.TYPE_INT_ARGB)
    val graphics = output.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR,
        )
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        // Clipping to the ellipse before drawing leaves the corners fully transparent, which is what
        // lets the avatar sit on any background without a visible square edge.
        graphics.clip = Ellipse2D.Float(0f, 0f, edge.toFloat(), edge.toFloat())
        graphics.drawImage(square, 0, 0, edge, edge, null)
    } finally {
        graphics.dispose()
    }
    return output
}
