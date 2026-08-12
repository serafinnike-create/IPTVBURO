package com.lucasserafin94.iptvburo.ui.cast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.domain.model.CastMessage
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButton
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButtonStyle
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

/**
 * Choosing a screen and entering its code.
 *
 * Three steps, one at a time: look for screens, pick one, type the four digits it is showing. They
 * are separate states rather than one form because the code belongs to a particular screen — asking
 * for it before anything is chosen would be asking for a number the user cannot see yet.
 *
 * The success wording says **sent**, never *playing*. The receiver answers a wrong code with
 * silence, so this device genuinely cannot tell a mistyped code from a screen that stopped
 * listening, and saying "playing" would state as fact something it does not know.
 */
@Composable
fun CastSheet(
    state: CastUiState,
    onSearchAgain: () -> Unit,
    onChoose: (CastTarget) -> Unit,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Cast, contentDescription = null)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.cast_title),
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            TextButton(onClick = onClose) { Text(stringResource(R.string.common_close)) }
        }
        Spacer(Modifier.height(12.dp))

        when (state) {
            CastUiState.Idle -> Unit

            CastUiState.Searching ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.cast_searching),
                        color = BuroTextSecondary,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }

            is CastUiState.Found -> FoundScreens(state.targets, onChoose, onSearchAgain)

            is CastUiState.NeedsCode -> CodeEntry(state, onSend, onBack)

            is CastUiState.Sending ->
                Text(
                    text = stringResource(R.string.cast_sending, state.target.displayName),
                    color = BuroTextSecondary,
                )

            is CastUiState.Sent ->
                Text(stringResource(R.string.cast_sent, state.target.displayName))

            is CastUiState.Failed ->
                Column {
                    Text(stringResource(R.string.cast_failed, state.target.displayName))
                    Spacer(Modifier.height(12.dp))
                    BuroButton(onClick = onBack, style = BuroButtonStyle.Secondary) {
                        Text(stringResource(R.string.cast_choose_another))
                    }
                }
        }
    }
}

@Composable
private fun FoundScreens(
    targets: List<CastTarget>,
    onChoose: (CastTarget) -> Unit,
    onSearchAgain: () -> Unit,
) {
    if (targets.isEmpty()) {
        // An empty result is ordinary rather than broken: plenty of home routers keep wifi and
        // ethernet apart and drop the broadcast between them. Saying so is more useful than an
        // error, because the fix is on the router and not in the app.
        Text(stringResource(R.string.cast_none_found), color = BuroTextSecondary)
        Spacer(Modifier.height(12.dp))
        BuroButton(onClick = onSearchAgain, style = BuroButtonStyle.Secondary) {
            Text(stringResource(R.string.cast_search_again))
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(targets, key = { target -> target.address }) { target ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onChoose(target) }
                        .padding(vertical = 12.dp),
            ) {
                Text(target.displayName)
                Text(target.address, color = BuroTextSecondary)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    TextButton(onClick = onSearchAgain) { Text(stringResource(R.string.cast_search_again)) }
}

@Composable
private fun CodeEntry(
    state: CastUiState.NeedsCode,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
) {
    var code by remember(state.target) { mutableStateOf("") }

    Text(stringResource(R.string.cast_code_prompt, state.target.displayName))
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.cast_code_hint), color = BuroTextSecondary)
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = code,
        // Filtered as it is typed rather than validated on submit: the code is four digits and
        // nothing else, so a letter is a keystroke to ignore rather than an error to report.
        onValueChange = { typed ->
            code = typed.filter(Char::isDigit).take(CastMessage.PAIRING_CODE_LENGTH)
        },
        singleLine = true,
        isError = state.badCode,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )

    if (state.badCode) {
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.cast_code_invalid), color = BuroTextSecondary)
    }

    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BuroButton(
            onClick = { onSend(code) },
            // Enabled only on a complete code, so the button cannot send something the receiver
            // will discard without saying anything.
            enabled = code.length == CastMessage.PAIRING_CODE_LENGTH,
        ) {
            Text(stringResource(R.string.cast_send))
        }
        BuroButton(onClick = onBack, style = BuroButtonStyle.Secondary) {
            Text(stringResource(R.string.common_back))
        }
    }
}
