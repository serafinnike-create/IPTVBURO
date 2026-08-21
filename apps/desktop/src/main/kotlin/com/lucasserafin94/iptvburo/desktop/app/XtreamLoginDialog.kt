package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.security.SecureTextBuffer
import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.xtream.XtreamSubscriptionParser

@Composable
fun XtreamLoginDialog(
    onDismiss: () -> Unit,
    onConnect: (XtreamLoginInput) -> Unit,
) {
    val form = remember { SecureXtreamLoginForm() }
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
                    onConnect(form.consume())
                    onDismiss()
                },
                enabled = form.canSubmit,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = BuroColors.Primary,
                        contentColor = BuroColors.OnPrimary,
                    ),
            ) {
                Text("Conectar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    form.clear()
                    onDismiss()
                },
            ) {
                Text("Cancelar")
            }
        },
        title = { Text(strings.shareStrings.screens.connectXtreamTitle) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Aceita o endereço do servidor ou uma URL completa get.php/player_api.php. Ao conectar, os dados são cifrados pelo Windows para este usuário.",
                    color = BuroColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = form.server.text,
                    onValueChange = form::acceptServerInput,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Servidor") },
                    placeholder = { Text("http://servidor:porta") },
                    singleLine = true,
                )
                if (form.usesPlainHttp) {
                    Text(
                        "Atenção: HTTP envia usuário e senha sem proteção TLS. Prefira HTTPS sempre que o provedor oferecer.",
                        color = BuroColors.Warning,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else if (form.hasValueWithoutScheme) {
                    Text(
                        "Sem esquema informado, o IPTV BURO tentará HTTPS por padrão.",
                        color = BuroColors.Success,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedTextField(
                    value = form.username.text,
                    onValueChange = form.username::replace,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Usuário") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = form.password.text,
                    onValueChange = form.password::replace,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Senha") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "●",
                        color = BuroColors.Success,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Sem histórico, preenchimento automático ou Preferences.",
                        color = BuroColors.TextSubtle,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
    )
}

@Stable
private class SecureXtreamLoginForm {
    val server = SecureTextBuffer()
    val username = SecureTextBuffer()
    val password = SecureTextBuffer()

    val canSubmit: Boolean
        get() = !server.isBlank && !username.isBlank && !password.isBlank

    val usesPlainHttp: Boolean
        get() = server.text.trimStart().startsWith("http://", ignoreCase = true)

    val hasValueWithoutScheme: Boolean
        get() {
            val value = server.text.trim()
            return value.isNotEmpty() && "://" !in value
        }

    /**
     * Takes what was typed or pasted into the server field, splitting a full link into three.
     *
     * Providers hand out the same subscription in several shapes — `get.php?username=…`,
     * `/playlist/USER/PASS/m3u_plus`, `player_api.php`, a stream URL copied from another player,
     * or credentials in the userinfo. The person pasting did not choose the format and should not
     * have to recognise it, so anything carrying a username and password fills all three fields
     * and leaves only "Conectar" to press.
     *
     * A plain address is left exactly as typed: [XtreamSubscriptionParser] answers null there, and
     * that is the ordinary case of someone typing a host and then the credentials by hand.
     *
     * Existing values are only overwritten when the link actually carries credentials — pasting a
     * bare host after typing a username must not wipe it.
     */
    fun acceptServerInput(value: String) {
        val link = XtreamSubscriptionParser.parse(value)
        if (link == null) {
            server.replace(value)
            return
        }
        server.replace(link.endpoint.baseUrl.toString())
        username.replace(link.username)
        password.replace(link.password)
    }

    fun consume(): XtreamLoginInput {
        val input =
            XtreamLoginInput(
                server = server.copyChars(),
                username = username.copyChars(),
                password = password.copyChars(),
            )
        clear()
        return input
    }

    fun clear() {
        server.clear()
        username.clear()
        password.clear()
    }

    override fun toString(): String = "SecureXtreamLoginForm(<redacted>)"
}
