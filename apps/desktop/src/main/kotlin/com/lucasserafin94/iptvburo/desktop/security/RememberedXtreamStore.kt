package com.lucasserafin94.iptvburo.desktop.security

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Arrays

/**
 * Remembers one Xtream connection between Windows launches.
 *
 * The payload is protected with Windows DPAPI for the current OS user before it reaches disk.
 * Nothing is persisted on unsupported platforms, and [clear] is called by the explicit
 * "Encerrar sessao" action.
 */
class RememberedXtreamStore(
    private val file: Path = defaultFile(),
) {
    fun save(
        server: CharArray,
        username: CharArray,
        password: CharArray,
    ) {
        if (!Platform.isWindows()) return
        require(server.size in 1..MAX_SERVER_CHARS)
        require(username.size in 1..MAX_CREDENTIAL_CHARS)
        require(password.size in 1..MAX_CREDENTIAL_CHARS)

        val plaintext = encode(server, username, password)
        var protected = ByteArray(0)
        try {
            protected = Crypt32Util.cryptProtectData(plaintext)
            require(protected.size <= MAX_PROTECTED_BYTES)
            Files.createDirectories(file.parent)
            val temporary = file.resolveSibling("${file.fileName}.tmp")
            Files.write(
                temporary,
                protected,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
            try {
                Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Arrays.fill(plaintext, ZERO_BYTE)
            Arrays.fill(protected, ZERO_BYTE)
        }
    }

    fun load(): XtreamLoginInput? {
        if (!Platform.isWindows() || !Files.isRegularFile(file)) return null
        val protected = runCatching { Files.readAllBytes(file) }.getOrNull() ?: return null
        if (protected.isEmpty() || protected.size > MAX_PROTECTED_BYTES) {
            Arrays.fill(protected, ZERO_BYTE)
            clear()
            return null
        }

        var plaintext = ByteArray(0)
        return try {
            plaintext = Crypt32Util.cryptUnprotectData(protected)
            decode(plaintext)
        } catch (_: RuntimeException) {
            clear()
            null
        } finally {
            Arrays.fill(protected, ZERO_BYTE)
            Arrays.fill(plaintext, ZERO_BYTE)
        }
    }

    fun clear() {
        runCatching { Files.deleteIfExists(file) }
    }

    private fun encode(
        server: CharArray,
        username: CharArray,
        password: CharArray,
    ): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(FORMAT_VERSION)
                output.writeUTF(server.concatToString())
                output.writeUTF(username.concatToString())
                output.writeUTF(password.concatToString())
            }
            bytes.toByteArray()
        }

    private fun decode(plaintext: ByteArray): XtreamLoginInput =
        DataInputStream(ByteArrayInputStream(plaintext)).use { input ->
            require(input.readInt() == FORMAT_VERSION)
            val server = input.readUTF().toCharArray()
            val username = input.readUTF().toCharArray()
            val password = input.readUTF().toCharArray()
            require(server.size in 1..MAX_SERVER_CHARS)
            require(username.size in 1..MAX_CREDENTIAL_CHARS)
            require(password.size in 1..MAX_CREDENTIAL_CHARS)
            require(input.available() == 0)
            XtreamLoginInput(server, username, password)
        }

    override fun toString(): String = "RememberedXtreamStore(<redacted>)"

    private companion object {
        const val FORMAT_VERSION = 1
        const val MAX_SERVER_CHARS = 2_048
        const val MAX_CREDENTIAL_CHARS = 512
        const val MAX_PROTECTED_BYTES = 32 * 1_024
        const val ZERO_BYTE: Byte = 0

        fun defaultFile(): Path {
            val localAppData = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)
            val root =
                localAppData?.let(Path::of)
                    ?: Path.of(System.getProperty("user.home"), "AppData", "Local")
            return root.resolve("IPTVBURO").resolve("remembered-source.dpapi")
        }
    }
}
