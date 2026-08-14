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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.domain.model.CastMessage
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButton
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButtonStyle
import com.lucasserafin94.iptvburo.ui.theme.BuroFieldColors
import com.lucasserafin94.iptvburo.ui.theme.BuroGold
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
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
    /**
     * Reaches a screen by typed address, offered only when the search found nothing.
     *
     * Returns whether it worked, so the field can say so without the sheet needing an error state
     * of its own for something that is corrected by editing the text already on screen.
     */
    onConnectToAddress: suspend (String) -> Boolean = { false },
) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Cast, contentDescription = null)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.cast_title),
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            // Explicit colours on every Material control in this sheet: the defaults paint text
            // buttons and the spinner in Material's own purple, which belongs to no BURO screen.
            TextButton(
                onClick = onClose,
                colors = ButtonDefaults.textButtonColors(contentColor = BuroTextPrimary),
            ) { Text(stringResource(R.string.common_close)) }
        }
        Spacer(Modifier.height(12.dp))

        when (state) {
            CastUiState.Idle -> Unit

            CastUiState.Searching ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        color = BuroGold,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(R.string.cast_searching),
                        color = BuroTextSecondary,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }

            is CastUiState.Found ->
                FoundScreens(state.targets, onChoose, onSearchAgain, onConnectToAddress)

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
    onConnectToAddress: suspend (String) -> Boolean,
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
        // The way out of a network that blocks the search.
        //
        // Only here, and deliberately: a router dropping broadcast makes discovery return nothing
        // while both devices sit on the same wifi listening, and without this the feature is simply
        // unavailable to that household with no way forward. Offered *after* the search rather than
        // instead of it — typing an address is a fallback, not the way this is meant to be used, and
        // putting a field in front of everyone would make the common case worse to serve the rare
        // one.
        Spacer(Modifier.height(20.dp))
        ManualAddressEntry(onConnect = onConnectToAddress)
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
    TextButton(
        onClick = onSearchAgain,
        colors = ButtonDefaults.textButtonColors(contentColor = BuroGold),
    ) { Text(stringResource(R.string.cast_search_again)) }
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
        colors = BuroFieldColors,
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

/**
 * Typing a screen's address, for a network where the search finds nothing.
 *
 * Refused before any packet leaves when the text is not a private IPv4 address — see
 * `CastSender.isPlausibleHost`. Doing that check here as well as there means a typo is answered
 * instantly instead of after a probe times out, and the message says what is wrong rather than
 * "nothing answered", which would send somebody looking at their router for a mistyped digit.
 */
@Composable
private fun ManualAddressEntry(onConnect: suspend (String) -> Boolean) {
    var address by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val plausible = remember(address) { CastSender.isPlausibleHost(address.trim()) }

    Text(
        text = stringResource(R.string.cast_manual_title),
        color = BuroTextPrimary,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.cast_manual_hint),
        color = BuroTextSecondary,
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = address,
        onValueChange = { typed ->
            // Trimmed as it is typed: an address pasted from another screen commonly arrives with a
            // trailing space, and refusing it for that would look like the address itself is wrong.
            address = typed.trim().take(MAX_ADDRESS_LENGTH)
            failed = false
        },
        label = { Text(stringResource(R.string.cast_manual_label)) },
        singleLine = true,
        isError = failed,
        colors = BuroFieldColors,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    if (failed) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.cast_manual_invalid),
            color = BuroTextSecondary,
            fontSize = 12.sp,
        )
    }
    Spacer(Modifier.height(10.dp))
    BuroButton(
        onClick = {
            if (connecting) return@BuroButton
            connecting = true
            scope.launch {
                // False covers both "not a valid address" and "nothing answered there". The two are
                // one thing to the person typing: whatever they entered did not reach a screen.
                failed = !onConnect(address.trim())
                connecting = false
            }
        },
        enabled = plausible && !connecting,
        style = BuroButtonStyle.Secondary,
    ) {
        Text(stringResource(R.string.cast_manual_connect))
    }
}

/** Longer than any IPv4 address; a value past this is not one being typed. */
private const val MAX_ADDRESS_LENGTH = 15
