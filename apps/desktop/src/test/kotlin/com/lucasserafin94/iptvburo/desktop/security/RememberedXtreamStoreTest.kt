package com.lucasserafin94.iptvburo.desktop.security

import com.sun.jna.Platform
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Arrays
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RememberedXtreamStoreTest {
    @Test
    fun `DPAPI round trip is user scoped and clear removes the blob`() {
        if (!Platform.isWindows()) return
        val directory = createTempDirectory("iptvburo-dpapi-test")
        val file = directory.resolve("source.dpapi")
        val store = RememberedXtreamStore(file)
        val server = "https://provider.invalid:8443".toCharArray()
        val username = "authorized-user".toCharArray()
        val password = "temporary-password".toCharArray()
        var restored: XtreamLoginInput? = null

        try {
            store.save(server, username, password)
            assertTrue(Files.isRegularFile(file))
            val protectedText = Files.readAllBytes(file).toString(StandardCharsets.UTF_8)
            assertFalse(protectedText.contains("provider.invalid"))
            assertFalse(protectedText.contains("authorized-user"))
            assertFalse(protectedText.contains("temporary-password"))

            restored = assertNotNull(store.load())
            assertContentEquals(server, restored.copyServer())
            assertContentEquals(username, restored.copyUsername())
            assertContentEquals(password, restored.copyPassword())

            store.clear()
            assertFalse(Files.exists(file))
        } finally {
            restored?.clear()
            store.clear()
            Files.deleteIfExists(directory)
            Arrays.fill(server, ZERO_CHAR)
            Arrays.fill(username, ZERO_CHAR)
            Arrays.fill(password, ZERO_CHAR)
        }
    }

    private companion object {
        const val ZERO_CHAR = '\u0000'
    }
}
