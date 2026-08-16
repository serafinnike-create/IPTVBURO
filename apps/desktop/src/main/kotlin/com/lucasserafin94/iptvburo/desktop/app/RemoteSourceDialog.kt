package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.data.RemotePlaylistProtocol
import com.lucasserafin94.iptvburo.desktop.security.SecureTextBuffer
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.strings

/**
 * Adding a playlist that lives on the user's own server.
 *
 * The address decides the protocol, so there is no menu to choose WebDAV or FTP from first. People
 * paste what their NAS's own interface showed them, and asking them to classify that address before
 * the app will look at it is a question they should not have to answer — the scheme already says it.
 *
 * The credentials go through [SecureTextBuffer], like the Xtream login: the characters are cleared
 * from memory when the dialog closes rather than left in a String for the garbage collector to
 * release whenever it gets round to it.
 */
@Composable
fun RemoteSourceDialog(
    onDismiss: () -> Unit,
    onConnect: (url: String, username: String?, password: String?) -> Unit,
) {
    val text = strings.shareStrings.remoteSource
    val form = remember { SecureRemoteSourceForm() }
    DisposableEffect(form) {
        onDispose(form::clear)
    }

    AlertDialog(
        onDismissRequest = {
            form.clear()
            onDismiss()
        },
        confirmButton = {
            Button(
                onClick = {
                    onConnect(form.address.text.trim(), form.usernameOrNull(), form.passwordOrNull())
                    form.clear()
                    onDismiss()
                },
                enabled = form.canSubmit,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = BuroColors.Primary,
                        contentColor = BuroColors.OnPrimary,
                    ),
            ) {
                Text(text.connect)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    form.clear()
                    onDismiss()
                },
            ) {
                Text(text.cancel)
            }
        },
        title = { Text(text.title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text.hint,
                    color = BuroColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = form.address.text,
                    onValueChange = form.address::replace,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text.addressLabel) },
                    placeholder = { Text(text.addressPlaceholder) },
                    singleLine = true,
                )
                // Said while the address is being typed rather than after Connect fails, so the
                // scheme can be corrected before a request is attempted against nothing.
                if (form.hasUnsupportedAddress) {
                    Text(
                        text.unsupportedAddress,
                        color = BuroColors.Warning,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedTextField(
                    value = form.username.text,
                    onValueChange = form.username::replace,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text.userLabel) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = form.password.text,
                    onValueChange = form.password::replace,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text.passwordLabel) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Text(
                    // Said before they type it, not after: whether a household password is written
                    // to disk is something to know in advance.
                    text.credentialsNotice,
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
    )
}

@Stable
private class SecureRemoteSourceForm {
    val address = SecureTextBuffer()
    val username = SecureTextBuffer()
    val password = SecureTextBuffer()

    /**
     * An address alone is enough.
     *
     * Credentials are optional because a NAS on the household network is often readable without
     * one, and demanding a password the user does not have would block a case that works.
     */
    val canSubmit: Boolean
        get() = !address.isBlank && RemotePlaylistProtocol.of(address.text.trim()) != null

    /** Only once something has been typed: an empty field is not yet a wrong one. */
    val hasUnsupportedAddress: Boolean
        get() = !address.isBlank && RemotePlaylistProtocol.of(address.text.trim()) == null

    fun usernameOrNull(): String? = username.text.trim().takeIf(String::isNotEmpty)

    fun passwordOrNull(): String? = password.text.takeIf(String::isNotEmpty)

    fun clear() {
        address.clear()
        username.clear()
        password.clear()
    }
}
