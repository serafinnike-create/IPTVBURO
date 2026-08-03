package com.lucasserafin94.iptvburo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.tv.material3.Text
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.stalker.StalkerMacAddress
import com.lucasserafin94.iptvburo.ui.StalkerFailureUi
import com.lucasserafin94.iptvburo.ui.XtreamImportStageUi
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButton
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButtonStyle
import com.lucasserafin94.iptvburo.ui.designsystem.BuroProgressBar
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroDanger
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

/**
 * Collects the portal address and MAC address that a Stalker/Ministra subscription is sold as.
 *
 * The MAC is the whole credential on such a portal, so it is held in local composable state, never
 * logged, never echoed into a failure message, and only ever shown back to the user in the
 * normalised confirmation line — never carried into any surface that outlives the dialog.
 */
@Composable
internal fun StalkerSourceDialog(
    isImporting: Boolean,
    hasImportError: Boolean,
    failure: StalkerFailureUi?,
    importStage: XtreamImportStageUi?,
    importSuccessVersion: Long,
    successVersionAtOpen: Long,
    onSubmit: (
        displayName: String,
        portalUrl: String,
        macAddress: String,
        username: String,
        password: String,
    ) -> Unit,
    onCancelImport: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var displayName by remember { mutableStateOf("") }
    var portalUrl by remember { mutableStateOf("") }
    var macAddress by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val nameFocusRequester = remember { FocusRequester() }

    fun clearAndDismiss() {
        if (isImporting) onCancelImport()
        displayName = ""
        portalUrl = ""
        macAddress = ""
        username = ""
        password = ""
        submitted = false
        onDismiss()
    }

    LaunchedEffect(Unit) {
        nameFocusRequester.requestFocus()
    }
    LaunchedEffect(importSuccessVersion) {
        if (importSuccessVersion > successVersionAtOpen) {
            clearAndDismiss()
        }
    }
    LaunchedEffect(hasImportError) {
        if (hasImportError) password = ""
    }

    // Recomputed on every keystroke so the user is told immediately whether their MAC was
    // understood, rather than discovering it only after a round trip to the portal.
    val normalisedMac = remember(macAddress) { StalkerMacAddress.normalise(macAddress) }
    val isFormValid =
        displayName.isNotBlank() &&
            portalUrl.isNotBlank() &&
            StalkerMacAddress.isValid(macAddress)
    val usesInsecureHttp = portalUrl.trim().startsWith("http://", ignoreCase = true)

    Dialog(
        onDismissRequest = ::clearAndDismiss,
        properties =
            DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                securePolicy = SecureFlagPolicy.SecureOn,
                usePlatformDefaultWidth = false,
            ),
    ) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .background(BuroCanvas.copy(alpha = 0.98f))
                .safeDrawingPadding(),
        ) {
            val stackActions = maxWidth < 600.dp
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding =
                    PaddingValues(
                        horizontal = if (stackActions) 16.dp else 32.dp,
                        vertical = if (maxHeight < 520.dp) 16.dp else 28.dp,
                    ),
            ) {
                item(key = "stalker:form") {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 680.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(BuroSurface)
                                .border(
                                    width = 1.dp,
                                    color = BuroTextPrimary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(24.dp),
                                )
                                .padding(if (stackActions) 20.dp else 30.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.sources_stalker_title),
                                color = BuroTextPrimary,
                                fontSize = if (stackActions) 26.sp else 31.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.sources_stalker_body),
                                color = BuroTextSecondary,
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                            )
                            Spacer(Modifier.height(22.dp))
                            StalkerTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                label = stringResource(R.string.sources_stalker_name),
                                placeholder = stringResource(R.string.sources_stalker_name_hint),
                                enabled = !isImporting,
                                focusRequester = nameFocusRequester,
                            )
                            Spacer(Modifier.height(14.dp))
                            StalkerTextField(
                                value = portalUrl,
                                onValueChange = { portalUrl = it },
                                label = stringResource(R.string.sources_stalker_portal),
                                placeholder = stringResource(R.string.sources_stalker_portal_hint),
                                keyboardType = KeyboardType.Uri,
                                enabled = !isImporting,
                            )
                            if (usesInsecureHttp) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.sources_stalker_http_warning),
                                    color = BuroDanger,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            StalkerTextField(
                                value = macAddress,
                                onValueChange = { macAddress = it },
                                label = stringResource(R.string.sources_stalker_mac),
                                placeholder = stringResource(R.string.sources_stalker_mac_hint),
                                keyboardType = KeyboardType.Ascii,
                                enabled = !isImporting,
                            )
                            if (normalisedMac != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text =
                                        stringResource(
                                            R.string.sources_stalker_mac_recognised,
                                            normalisedMac,
                                        ),
                                    color = BuroAccent,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                )
                            } else if (macAddress.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.sources_stalker_mac_invalid),
                                    color = BuroDanger,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                )
                            }
                            Spacer(Modifier.height(22.dp))
                            StalkerOptionalCredentials(
                                username = username,
                                password = password,
                                onUsernameChange = { username = it },
                                onPasswordChange = { password = it },
                                enabled = !isImporting,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.sources_stalker_privacy),
                                color = BuroAccent,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            )
                            if (submitted && hasImportError) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = stringResource(failure.messageResource()),
                                    color = BuroDanger,
                                    fontSize = 14.sp,
                                    lineHeight = 19.sp,
                                )
                            }
                            if (isImporting) {
                                Spacer(Modifier.height(16.dp))
                                StalkerImportProgress(importStage)
                            }
                            Spacer(Modifier.height(24.dp))
                            val submit: () -> Unit = {
                                submitted = true
                                onSubmit(
                                    displayName,
                                    portalUrl,
                                    macAddress,
                                    username,
                                    password,
                                )
                            }
                            if (stackActions) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    SubmitStalkerButton(
                                        isImporting = isImporting,
                                        isFormValid = isFormValid,
                                        onClick = submit,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    CancelStalkerButton(
                                        isImporting = isImporting,
                                        onClick = ::clearAndDismiss,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    SubmitStalkerButton(
                                        isImporting = isImporting,
                                        isFormValid = isFormValid,
                                        onClick = submit,
                                        modifier = Modifier.weight(1f),
                                    )
                                    CancelStalkerButton(
                                        isImporting = isImporting,
                                        onClick = ::clearAndDismiss,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The username/password pair, set apart so it does not read as required.
 *
 * Nearly every portal authenticates on the MAC alone; presenting these as equals to the MAC field
 * makes people believe they are missing credentials they were never given.
 */
@Composable
private fun StalkerOptionalCredentials(
    username: String,
    password: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BuroCanvas.copy(alpha = 0.45f))
            .border(
                width = 1.dp,
                color = BuroTextPrimary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.sources_stalker_optional_heading),
            color = BuroTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.sources_stalker_optional_body),
            color = BuroTextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(14.dp))
        StalkerTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = stringResource(R.string.sources_stalker_username),
            placeholder = stringResource(R.string.sources_stalker_username_hint),
            enabled = enabled,
        )
        Spacer(Modifier.height(14.dp))
        StalkerTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.sources_stalker_password),
            placeholder = stringResource(R.string.sources_stalker_password_hint),
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
            enabled = enabled,
        )
    }
}

@Composable
private fun StalkerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val focusModifier =
        if (focusRequester == null) {
            Modifier
        } else {
            Modifier.focusRequester(focusRequester)
        }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = BuroTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(7.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = focusModifier
                .fillMaxWidth()
                .height(54.dp)
                .onFocusChanged { focused = it.isFocused }
                .clip(RoundedCornerShape(14.dp))
                .background(BuroSurface)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) BuroAccent else BuroTextPrimary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 16.dp, vertical = 15.dp),
            enabled = enabled,
            singleLine = true,
            textStyle =
                TextStyle(
                    color = if (enabled) BuroTextPrimary else BuroTextSecondary,
                    fontSize = 16.sp,
                ),
            cursorBrush = SolidColor(BuroAccent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = BuroTextSecondary.copy(alpha = 0.72f),
                            fontSize = 15.sp,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun SubmitStalkerButton(
    isImporting: Boolean,
    isFormValid: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BuroButton(
        onClick = onClick,
        modifier = modifier,
        enabled = isFormValid && !isImporting,
    ) {
        Text(
            text =
                stringResource(
                    if (isImporting) {
                        R.string.sources_stalker_connecting
                    } else {
                        R.string.sources_stalker_connect
                    },
                ),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StalkerImportProgress(stage: XtreamImportStageUi?) {
    val resolvedStage = stage ?: XtreamImportStageUi.AUTHENTICATING
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BuroSurface)
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.sources_xtream_progress_title),
            color = BuroTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = resolvedStage.localizedLabel(),
            color = BuroAccent,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(12.dp))
        BuroProgressBar(
            progress = resolvedStage.progressFraction(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CancelStalkerButton(
    isImporting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BuroButton(
        onClick = onClick,
        modifier = modifier,
        enabled = true,
        style = BuroButtonStyle.Ghost,
    ) {
        Text(
            text =
                stringResource(
                    if (isImporting) {
                        R.string.common_cancel
                    } else {
                        R.string.common_close
                    },
                ),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Maps a failure reason onto the advice that actually resolves it.
 *
 * A portal that has never heard of the MAC needs the provider to register it; an unreachable portal
 * needs the address checked. Collapsing both into "could not connect" sends people down the wrong
 * path. The MAC itself never appears in any of these messages.
 */
internal fun StalkerFailureUi?.messageResource(): Int =
    when (this) {
        StalkerFailureUi.UNAUTHORISED -> R.string.sources_stalker_error_unauthorised
        StalkerFailureUi.BLOCKED -> R.string.sources_stalker_error_blocked
        StalkerFailureUi.NETWORK -> R.string.sources_stalker_error_network
        StalkerFailureUi.MALFORMED -> R.string.sources_stalker_error_malformed
        StalkerFailureUi.INVALID_INPUT -> R.string.sources_stalker_error_invalid
        null -> R.string.sources_stalker_error
    }
