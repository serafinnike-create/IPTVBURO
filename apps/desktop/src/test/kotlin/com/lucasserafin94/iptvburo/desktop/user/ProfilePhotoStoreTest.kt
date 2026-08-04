package com.lucasserafin94.iptvburo.desktop.user

import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfilePhotoStoreTest {
    private fun <T> withDirectory(block: (Path) -> T): T {
        val directory = Files.createTempDirectory("iptvburo-avatars")
        return try {
            block(directory)
        } finally {
            @OptIn(kotlin.io.path.ExperimentalPathApi::class)
            directory.deleteRecursively()
        }
    }

    private fun writeImage(directory: Path, name: String, width: Int, height: Int): Path {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.RED
        graphics.fillRect(0, 0, width, height)
        graphics.dispose()
        val file = directory.resolve(name)
        ImageIO.write(image, "png", file.toFile())
        return file
    }

    @Test
    fun `a stored photo is square and can be read back`() {
        withDirectory { directory ->
            val store = ProfilePhotoStore(directory.resolve("store"))
            val source = writeImage(directory, "wide.png", width = 900, height = 400)

            val stored = assertNotNull(store.store("profile-1", source))
            val decoded = assertNotNull(ImageIO.read(stored.toFile()))
            assertEquals(decoded.width, decoded.height, "the stored photo must be square")
            assertEquals(stored, store.photoFor("profile-1"))
        }
    }

    /**
     * Scaling a rectangle straight to a square would stretch a face, so the crop happens first and
     * takes the centre.
     */
    @Test
    fun `a wide photo is centre cropped rather than squashed`() {
        val source = BufferedImage(400, 100, BufferedImage.TYPE_INT_RGB)
        val graphics = source.createGraphics()
        graphics.color = Color.BLUE
        graphics.fillRect(0, 0, 400, 100)
        graphics.color = Color.GREEN
        // A marker only inside the centre square that a correct crop must keep.
        graphics.fillRect(150, 0, 100, 100)
        graphics.dispose()

        val cropped = cropToCircle(source, edge = 64)
        val centre = Color(cropped.getRGB(32, 32), true)
        assertEquals(Color.GREEN.rgb, Color(centre.red, centre.green, centre.blue).rgb)
    }

    @Test
    fun `corners are transparent so the avatar reads as a circle`() {
        val source = BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB)
        val graphics = source.createGraphics()
        graphics.color = Color.RED
        graphics.fillRect(0, 0, 100, 100)
        graphics.dispose()

        val cropped = cropToCircle(source, edge = 64)
        assertEquals(0, Color(cropped.getRGB(0, 0), true).alpha, "top-left corner must be clear")
        assertEquals(255, Color(cropped.getRGB(32, 32), true).alpha, "the centre must be opaque")
    }

    @Test
    fun `a file that is not an image is refused`() {
        withDirectory { directory ->
            val store = ProfilePhotoStore(directory.resolve("store"))
            val notAnImage = directory.resolve("notes.txt")
            Files.writeString(notAnImage, "this is not a picture")

            assertNull(store.store("profile-1", notAnImage))
            assertNull(store.photoFor("profile-1"))
        }
    }

    @Test
    fun `a missing file is refused`() {
        withDirectory { directory ->
            val store = ProfilePhotoStore(directory.resolve("store"))

            assertNull(store.store("profile-1", directory.resolve("absent.png")))
        }
    }

    @Test
    fun `replacing a photo overwrites the previous one`() {
        withDirectory { directory ->
            val store = ProfilePhotoStore(directory.resolve("store"))
            store.store("profile-1", writeImage(directory, "a.png", 200, 200))
            val second = store.store("profile-1", writeImage(directory, "b.png", 300, 300))

            assertNotNull(second)
            assertEquals(1, Files.list(directory.resolve("store")).use { it.count() }.toInt())
        }
    }

    @Test
    fun `removing deletes the stored photo`() {
        withDirectory { directory ->
            val store = ProfilePhotoStore(directory.resolve("store"))
            store.store("profile-1", writeImage(directory, "a.png", 200, 200))
            store.remove("profile-1")

            assertNull(store.photoFor("profile-1"))
        }
    }

    /** Each profile keeps its own photo; storing one must not disturb another. */
    @Test
    fun `photos are per profile`() {
        withDirectory { directory ->
            val store = ProfilePhotoStore(directory.resolve("store"))
            store.store("first", writeImage(directory, "a.png", 200, 200))
            store.store("second", writeImage(directory, "b.png", 200, 200))

            assertNotNull(store.photoFor("first"))
            assertNotNull(store.photoFor("second"))
            store.remove("first")
            assertNull(store.photoFor("first"))
            assertTrue(store.photoFor("second") != null, "the other profile keeps its photo")
        }
    }
}
