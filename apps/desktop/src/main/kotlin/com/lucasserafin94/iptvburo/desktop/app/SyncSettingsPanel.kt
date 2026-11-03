package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.DesktopAppState
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.SyncStrings
import java.text.DateFormat
import java.util.Date

/**
 * "Sincronizar aparelhos": pair this computer with the household's other devices and keep the
 * favourites in step.
 *
 * Two states. Not yet in a household: show a code, or type one. In one: how many devices, when it
 * last synced, sync now, stop. The work itself is DesktopAppState's; this only draws it.
 */
@Composable
fun SyncSettingsSection(appState: DesktopAppState) {
    val text = SyncStrings.of(appState.language)
    val ui = appState.syncUi
    var typedCode by remember { mutableStateOf("") }

    // Where this device stands, read once when the section opens rather than on every frame.
    LaunchedEffect(ui.member) { if (ui.member) appState.refreshSyncStatus() }

    Column(verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
        Text(text = text.title, color = BuroColors.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(text = text.hint, color = BuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)

        ui.code?.let { code ->
            Text(
                text = text.codeLabel.replace("%s", code),
                color = BuroColors.Primary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(text = text.codeHint, color = BuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }

        if (ui.member) {
            ui.members?.let { members ->
                Text(text.membersFormat.replace("%d", members.toString()), color = BuroColors.Text)
            }
            ui.lastSyncedAtMillis?.let { at ->
                val stamp = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at))
                Text(text.lastSyncFormat.replace("%s", stamp), color = BuroColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (ui.lastReceived > 0) {
                Text(
                    text.receivedFormat.replace("%d", ui.lastReceived.toString()),
                    color = BuroColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
                Button(
                    onClick = appState::syncNow,
                    enabled = !ui.busy,
                    shape = BuroRadius.Small,
                    colors = ButtonDefaults.buttonColors(containerColor = BuroColors.Primary, contentColor = BuroColors.OnPrimary),
                ) { Text(if (ui.busy) text.syncing else text.syncNow) }
                OutlinedButton(onClick = appState::createSyncCode, enabled = !ui.busy, shape = BuroRadius.Small) {
                    Text(text.createCode, color = BuroColors.Text)
                }
                OutlinedButton(onClick = appState::leaveSync, enabled = !ui.busy, shape = BuroRadius.Small) {
                    Text(text.leave, color = BuroColors.TextMuted)
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = appState::createSyncCode,
                    enabled = !ui.busy,
                    shape = BuroRadius.Small,
                    colors = ButtonDefaults.buttonColors(containerColor = BuroColors.Primary, contentColor = BuroColors.OnPrimary),
                ) { Text(text.createCode) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = typedCode,
                    onValueChange = { typedCode = it.take(MAX_CODE_INPUT) },
                    label = { Text(text.joinField) },
                    singleLine = true,
                    modifier = Modifier.width(240.dp),
                )
                OutlinedButton(
                    onClick = { appState.joinSync(typedCode) },
                    enabled = !ui.busy && typedCode.count(Char::isLetterOrDigit) == CODE_LENGTH,
                    shape = BuroRadius.Small,
                ) { Text(text.join, color = BuroColors.Text) }
            }
        }

        ui.problem?.let { problem ->
            Text(text = text.problem(problem), color = BuroColors.Warning, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Eight letters and digits, shown as ABCD-EFGH. */
private const val CODE_LENGTH = 8

/** Room for the dash and a stray space, nothing more. */
private const val MAX_CODE_INPUT = 12
