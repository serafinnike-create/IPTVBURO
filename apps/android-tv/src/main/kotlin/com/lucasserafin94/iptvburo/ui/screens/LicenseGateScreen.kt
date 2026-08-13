package com.lucasserafin94.iptvburo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.domain.model.LicenseBlockReason
import com.lucasserafin94.iptvburo.data.licensing.KeyState
import com.lucasserafin94.iptvburo.data.licensing.RedeemFailure
import kotlinx.coroutines.delay
import com.lucasserafin94.iptvburo.ui.LicenseUiState
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroDanger
import com.lucasserafin94.iptvburo.ui.theme.BuroFieldColors
import com.lucasserafin94.iptvburo.ui.theme.BuroGold
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

@Composable
fun LicenseGateScreen(
    state: LicenseUiState.Blocked,
    onPurchase: (String) -> Unit,
    onRetry: () -> Unit,
    onRedeem: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Asks what the typed key is without spending it. Defaults to doing nothing, for previews. */
    onInspectKey: (String) -> Unit = {},
    backdropPosters: List<String> = emptyList(),
) {
    var key by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val copy = copyFor(state.reason)

    Box(modifier = modifier.fillMaxSize().background(BuroCanvas)) {
        BuroCinematicBackdrop(posterUrls = backdropPosters)
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(BuroCanvas.copy(alpha = 0.94f))
                        .padding(horizontal = 22.dp, vertical = 26.dp),
            ) {
                Text(
                    text = "IPTV  BURO",
                    color = BuroGold,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(copy.title),
                    color = BuroTextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 33.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(copy.body),
                    color = BuroTextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )

                if (state.deviceId.isNotBlank()) {
                    Spacer(Modifier.height(22.dp))
                    Text(
                        text = stringResource(R.string.license_gate_code_label),
                        color = BuroTextSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(5.dp))
                    FocusSurface(
                        onClick = { clipboard.setText(AnnotatedString(state.deviceId)) },
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        backgroundColor = BuroSurface.copy(alpha = 0.92f),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = state.deviceId,
                                color = BuroGold,
                                fontSize = 22.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FocusSurface(
                        onClick = { onPurchase(state.deviceId) },
                        enabled = state.deviceId.isNotBlank() && !state.isWorking,
                        modifier = Modifier.weight(1f).height(54.dp),
                        backgroundColor = BuroGold,
                        focusedBackgroundColor = BuroAccent,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.license_gate_purchase),
                                color = BuroCanvas,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    FocusSurface(
                        onClick = onRetry,
                        enabled = !state.isWorking,
                        modifier = Modifier.weight(1f).height(54.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.license_gate_refresh),
                                color = BuroTextPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.uppercase().take(128) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { androidx.compose.material3.Text(stringResource(R.string.license_gate_key_label)) },
                    placeholder = { androidx.compose.material3.Text(stringResource(R.string.license_gate_key_hint)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    singleLine = true,
                    colors = BuroFieldColors,
                )

                // Asked once the typing settles, not on every keystroke: a key is checked in a
                // single request and a half-typed one is not a question worth asking.
                LaunchedEffect(key) {
                    delay(KEY_INSPECTION_DELAY_MILLIS)
                    onInspectKey(key)
                }

                // What the server says the key is, before anything is spent. The reassuring case is
                // "yours": after a reinstall the buyer's own key looks used, and saying so plainly
                // is the difference between reactivating and believing it must be bought again.
                state.typedKeyState?.let { keyState ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text =
                            stringResource(
                                when (keyState) {
                                    KeyState.AVAILABLE -> R.string.license_gate_key_state_available
                                    KeyState.YOURS -> R.string.license_gate_key_state_yours
                                    KeyState.IN_USE -> R.string.license_gate_key_state_in_use
                                    KeyState.EXPIRED -> R.string.license_gate_key_state_expired
                                    KeyState.UNKNOWN -> R.string.license_gate_key_state_unknown
                                },
                            ),
                        color =
                            when (keyState) {
                                KeyState.AVAILABLE, KeyState.YOURS -> BuroGold
                                else -> BuroDanger
                            },
                        fontSize = 13.sp,
                    )
                }

                Spacer(Modifier.height(10.dp))
                FocusSurface(
                    onClick = { onRedeem(key) },
                    enabled = key.isNotBlank() && !state.isWorking,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text =
                                stringResource(
                                    if (state.isWorking) {
                                        R.string.license_gate_working
                                    } else {
                                        R.string.license_gate_redeem
                                    },
                                ),
                            color = BuroTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (state.activationFailed) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        // Names the actual problem. "Could not activate" was shown for a mistyped
                        // key, a key already bound to another device and a dead connection alike,
                        // which left the user with no idea what to do next — and the server had
                        // been distinguishing all three the whole time.
                        text =
                            stringResource(
                                when (state.activationFailure) {
                                    RedeemFailure.UNKNOWN_KEY -> R.string.license_gate_key_unknown
                                    RedeemFailure.DEVICE_CODE_NOT_KEY ->
                                        R.string.license_gate_key_is_device_code
                                    RedeemFailure.ALREADY_USED -> R.string.license_gate_key_in_use
                                    RedeemFailure.EXPIRED -> R.string.license_gate_key_expired
                                    RedeemFailure.UNREACHABLE -> R.string.license_gate_key_offline
                                    RedeemFailure.NOT_REGISTERED ->
                                        R.string.license_gate_key_not_registered
                                    RedeemFailure.REFUSED, null -> R.string.license_gate_redeem_failed
                                },
                            ),
                        color = BuroDanger,
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.license_gate_privacy),
                    color = BuroTextSecondary.copy(alpha = 0.82f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

private data class GateCopy(val title: Int, val body: Int)

private fun copyFor(reason: LicenseBlockReason): GateCopy =
    when (reason) {
        LicenseBlockReason.TRIAL_ENDED ->
            GateCopy(R.string.license_gate_trial_ended_title, R.string.license_gate_trial_ended_body)
        LicenseBlockReason.EXPIRED ->
            GateCopy(R.string.license_gate_expired_title, R.string.license_gate_expired_body)
        LicenseBlockReason.REVOKED ->
            GateCopy(R.string.license_gate_revoked_title, R.string.license_gate_revoked_body)
        LicenseBlockReason.NEEDS_VERIFICATION ->
            GateCopy(R.string.license_gate_verify_title, R.string.license_gate_verify_body)
        LicenseBlockReason.UNREACHABLE ->
            GateCopy(R.string.license_gate_unreachable_title, R.string.license_gate_unreachable_body)
        LicenseBlockReason.NOT_ACTIVATED ->
            GateCopy(R.string.license_gate_not_activated_title, R.string.license_gate_not_activated_body)
    }

/**
 * How long the key field settles before the server is asked about it.
 *
 * Long enough that typing a full key produces one request rather than one per character; short
 * enough that the answer arrives while the user is still looking at the field.
 */
private const val KEY_INSPECTION_DELAY_MILLIS = 600L
