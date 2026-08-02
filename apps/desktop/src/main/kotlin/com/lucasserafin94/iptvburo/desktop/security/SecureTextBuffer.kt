package com.lucasserafin94.iptvburo.desktop.security

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.util.Arrays

/**
 * A Compose-aware text buffer that never retains the TextField's immutable String.
 *
 * Compose still creates short-lived String snapshots to render native text fields, but the
 * application state owns only a wipeable [CharArray]. This object must never be rememberSaveable.
 */
@Stable
class SecureTextBuffer {
    private var content = CharArray(0)
    private var revision by mutableIntStateOf(0)

    val text: String
        get() {
            @Suppress("UNUSED_EXPRESSION")
            revision
            return content.concatToString()
        }

    val isBlank: Boolean
        get() {
            // `canSubmit` is composed outside the text-field subtree. Reading the revision here
            // makes button enablement observe edits without retaining another String snapshot.
            @Suppress("UNUSED_EXPRESSION")
            revision
            return content.all(Char::isWhitespace)
        }

    fun replace(value: String) {
        Arrays.fill(content, ZERO_CHAR)
        content = value.toCharArray()
        revision += 1
    }

    fun copyChars(): CharArray = content.copyOf()

    fun clear() {
        Arrays.fill(content, ZERO_CHAR)
        content = CharArray(0)
        revision += 1
    }

    private companion object {
        const val ZERO_CHAR = '\u0000'
    }
}

/**
 * One-time credential transfer object.
 *
 * The repository copies the buffers into its session vault and immediately wipes this object.
 */
class XtreamLoginInput(
    server: CharArray,
    username: CharArray,
    password: CharArray,
) {
    private var serverChars = server
    private var usernameChars = username
    private var passwordChars = password

    internal fun copyServer(): CharArray = serverChars.copyOf()

    internal fun copyUsername(): CharArray = usernameChars.copyOf()

    internal fun copyPassword(): CharArray = passwordChars.copyOf()

    fun clear() {
        Arrays.fill(serverChars, ZERO_CHAR)
        Arrays.fill(usernameChars, ZERO_CHAR)
        Arrays.fill(passwordChars, ZERO_CHAR)
        serverChars = CharArray(0)
        usernameChars = CharArray(0)
        passwordChars = CharArray(0)
    }

    override fun toString(): String = "XtreamLoginInput(<redacted>)"

    private companion object {
        const val ZERO_CHAR = '\u0000'
    }
}
