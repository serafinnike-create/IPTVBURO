package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.DesktopAppState
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.domain.model.ParentalPin

/**
 * Setting, changing and removing the parental PIN.
 *
 * What it protects against is a child on a shared machine, not a determined adult — four digits in
 * a desktop app cannot be more than that, and the wording avoids implying otherwise.
 *
 * Changing or removing the PIN requires the current one. Without that the lock would be a
 * formality: anyone could clear it from the same menu that offers it.
 */
@Composable
fun ParentalControlPanel(appState: DesktopAppState) {
    val text = strings
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }

    val hasPin = appState.hasParentalPin

    Column(
        // Fills its container rather than pinning a width: it now lives in a dialog that sizes
        // itself, and a fixed 320dp inside it left the hints wrapping across three lines.
        modifier = Modifier.fillMaxWidth().padding(vertical = BuroSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
    ) {
        Text(
            text = text.settingsText.parentalTitle,
            color = BuroColors.TextMuted,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = text.settingsText.parentalHint,
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.labelSmall,
        )

        // Only asked for when one already exists. A first-time setup that demanded the current PIN
        // would be unanswerable.
        if (hasPin) {
            PinField(
                value = currentPin,
                onValueChange = { entered -> currentPin = entered.filter(Char::isDigit).take(ParentalPin.LENGTH) },
                label = text.settingsText.parentalCurrentPin,
            )
        }
        PinField(
            value = newPin,
            onValueChange = { entered -> newPin = entered.filter(Char::isDigit).take(ParentalPin.LENGTH) },
            label = text.settingsText.parentalNewPin,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
            PanelButton(if (hasPin) text.settingsText.parentalChangePin else text.settingsText.parentalSetPin) {
                when {
                    !ParentalPin.isWellFormed(newPin) -> {
                        message = text.settingsText.parentalPinFormat
                        messageIsError = true
                    }
                    appState.setParentalPin(newPin, currentPin.takeIf { hasPin }) -> {
                        message = text.settingsText.parentalPinSaved
                        messageIsError = false
                        currentPin = ""
                        newPin = ""
                    }
                    else -> {
                        message = text.settingsText.parentalWrongPin
                        messageIsError = true
                    }
                }
            }

            if (hasPin) {
                PanelButton(text.settingsText.parentalRemovePin) {
                    if (appState.clearParentalPin(currentPin)) {
                        message = null
                        currentPin = ""
                        newPin = ""
                    } else {
                        message = text.settingsText.parentalWrongPin
                        messageIsError = true
                    }
                }
            }
        }

        message?.let { shown ->
            Text(
                text = shown,
                color = if (messageIsError) BuroColors.Error else BuroColors.Success,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        // Offered only once a PIN exists: a switch that locks categories with no way to open them
        // would shut the user out of their own catalogue.
        if (hasPin) {
            Spacer(Modifier.height(BuroSpacing.Xxs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = text.settingsText.parentalLockAdult,
                    color = BuroColors.Text,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = appState.parentalLock.lockAdultCategories,
                    onCheckedChange = appState::setLockAdultCategories,
                )
            }
        }
    }
}

/** A PIN entry: digits only, masked, four characters. */
@Composable
private fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label, color = BuroColors.TextSubtle) },
        // Masked as it is typed: the point is a child in the room, and that is exactly when it is
        // being entered.
        visualTransformation = PasswordVisualTransformation(),
        // No keyboardType. NumberPassword asks for a soft keyboard that does not exist on desktop,
        // and the field silently refused every keystroke — the digits are already enforced by the
        // caller filtering the input, which is where it belongs anyway.
        modifier = Modifier.fillMaxWidth(),
        shape = BuroRadius.Small,
        colors =
            TextFieldDefaults.colors(
                focusedTextColor = BuroColors.Text,
                unfocusedTextColor = BuroColors.Text,
                focusedContainerColor = BuroColors.Surface,
                unfocusedContainerColor = BuroColors.Surface,
                focusedIndicatorColor = BuroColors.Primary,
                unfocusedIndicatorColor = BuroColors.BorderSoft,
                cursorColor = BuroColors.Primary,
            ),
    )
}

@Composable
private fun PanelButton(
    label: String,
    onClick: () -> Unit,
) {
    BuroInteractiveRow(
        onClick = onClick,
        selected = false,
        shape = BuroRadius.Pill,
        contentDescription = label,
    ) { state ->
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = BuroSpacing.Sm, vertical = 6.dp),
            color = if (state.active) BuroColors.Primary else BuroColors.Text,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * Asks for the PIN before a locked category opens.
 *
 * A wrong PIN says only that it was wrong. Distinguishing "no PIN set" from "wrong PIN" would tell
 * a child which of the two they are facing.
 */
@Composable
fun ParentalUnlockDialog(
    /** The category being opened, shown so it is clear what the PIN is being asked for. */
    categoryName: String,
    /** Checks the PIN and, when it is right, opens the category. Returns false for a wrong one. */
    onSubmit: suspend (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    val text = strings
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    // Opening the category loads a page, so the answer is awaited rather than assumed; `checking`
    // keeps a second press from submitting the same PIN twice while the first is still in flight.
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier =
            Modifier
                .width(320.dp)
                .background(BuroColors.SurfaceRaised, BuroRadius.Medium)
                .padding(BuroSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
    ) {
        Text(
            text = text.settingsText.parentalLocked,
            color = BuroColors.Text,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            // The category by name, so it is obvious which lock is being answered rather than
            // just that one is.
            text = "$categoryName · ${text.settingsText.parentalUnlock}",
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.labelSmall,
        )

        PinField(
            value = pin,
            onValueChange = { entered ->
                pin = entered.filter(Char::isDigit).take(ParentalPin.LENGTH)
                wrong = false
            },
            label = text.settingsText.parentalCurrentPin,
        )

        if (wrong) {
            Text(text = text.settingsText.parentalWrongPin, color = BuroColors.Error, style = MaterialTheme.typography.labelSmall)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
            PanelButton(text.understood) {
                if (checking || pin.length != ParentalPin.LENGTH) return@PanelButton
                checking = true
                scope.launch {
                    // The prompt stays up on a wrong PIN, so it can simply be retyped; the field is
                    // cleared because a rejected PIN is not a starting point for the next attempt.
                    if (!onSubmit(pin)) {
                        wrong = true
                        pin = ""
                    }
                    checking = false
                }
            }
            PanelButton(text.cancel, onDismiss)
        }
    }
}
