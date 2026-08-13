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
import com.lucasserafin94.iptvburo.ui.XtreamImportStageUi
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButton
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButtonStyle
import com.lucasserafin94.iptvburo.ui.designsystem.BuroProgressBar
import com.lucasserafin94.iptvburo.xtream.XtreamEndpointParser
import com.lucasserafin94.iptvburo.xtream.XtreamSubscriptionParser
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroDanger
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

@Composable
internal fun XtreamSourceDialog(
    isImporting: Boolean,
    hasImportError: Boolean,
    importStage: XtreamImportStageUi?,
    importSuccessVersion: Long,
    successVersionAtOpen: Long,
    onSubmit: (
        displayName: String,
        serverUrl: String,
        username: String,
        password: String,
    ) -> Unit,
    onCancelImport: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var displayName by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val nameFocusRequester = remember { FocusRequester() }

    fun clearAndDismiss() {
        if (isImporting) onCancelImport()
        displayName = ""
        serverUrl = ""
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
    val isFormValid =
        displayName.isNotBlank() &&
            serverUrl.isNotBlank() &&
            username.isNotBlank() &&
            password.isNotBlank()
    val usesInsecureHttp = serverUrl.trim().startsWith("http://", ignoreCase = true)

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
                item(key = "xtream:form") {
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
                            text = stringResource(R.string.sources_xtream_title),
                            color = BuroTextPrimary,
                            fontSize = if (stackActions) 26.sp else 31.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.sources_xtream_body),
                            color = BuroTextSecondary,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                        )
                        Spacer(Modifier.height(22.dp))
                        XtreamTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = stringResource(R.string.sources_xtream_name),
                            placeholder = stringResource(R.string.sources_xtream_name_hint),
                            enabled = !isImporting,
                            focusRequester = nameFocusRequester,
                        )
                        Spacer(Modifier.height(14.dp))
                        XtreamTextField(
                            value = serverUrl,
                            onValueChange = { pasted ->
                                // A pasted subscription link fills all three fields instead of
                                // having its credentials thrown away. See acceptXtreamServerInput.
                                val link = XtreamSubscriptionParser.parse(pasted)
                                if (link == null) {
                                    serverUrl = sanitizeXtreamServerField(pasted)
                                } else {
                                    serverUrl = link.endpoint.baseUrl.toString()
                                    username = link.username
                                    password = link.password
                                }
                            },
                            label = stringResource(R.string.sources_xtream_server),
                            placeholder = stringResource(R.string.sources_xtream_server_hint),
                            keyboardType = KeyboardType.Uri,
                            enabled = !isImporting,
                        )
                        if (usesInsecureHttp) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.sources_xtream_http_warning),
                                color = BuroDanger,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        XtreamTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = stringResource(R.string.sources_xtream_username),
                            placeholder = stringResource(R.string.sources_xtream_username_hint),
                            enabled = !isImporting,
                        )
                        Spacer(Modifier.height(14.dp))
                        XtreamTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = stringResource(R.string.sources_xtream_password),
                            placeholder = stringResource(R.string.sources_xtream_password_hint),
                            keyboardType = KeyboardType.Password,
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !isImporting,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.sources_xtream_privacy),
                            color = BuroAccent,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                        if (submitted && hasImportError) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.sources_xtream_error),
                                color = BuroDanger,
                                fontSize = 14.sp,
                                lineHeight = 19.sp,
                            )
                        }
                        if (isImporting) {
                            Spacer(Modifier.height(16.dp))
                            XtreamImportProgress(importStage)
                        }
                        Spacer(Modifier.height(24.dp))
                        if (stackActions) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                SubmitXtreamButton(
                                    isImporting = isImporting,
                                    isFormValid = isFormValid,
                                    importStage = importStage,
                                    onClick = {
                                        submitted = true
                                        onSubmit(
                                            displayName,
                                            serverUrl,
                                            username,
                                            password,
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                CancelXtreamButton(
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
                                SubmitXtreamButton(
                                    isImporting = isImporting,
                                    isFormValid = isFormValid,
                                    importStage = importStage,
                                    onClick = {
                                        submitted = true
                                        onSubmit(
                                            displayName,
                                            serverUrl,
                                            username,
                                            password,
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                CancelXtreamButton(
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
 * Trims a pasted address down to the server.
 *
 * Only reached when the link carries no credentials — [XtreamSubscriptionParser] handles the rest,
 * because a link that *does* carry them should fill the username and password rather than have
 * them stripped and silently discarded, which is what this did on its own.
 */
internal fun sanitizeXtreamServerField(value: String): String {
    if ('?' !in value) return value
    return runCatching {
        XtreamEndpointParser.parse(value).baseUrl.toString()
    }.getOrDefault(value)
}

@Composable
private fun XtreamTextField(
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
private fun SubmitXtreamButton(
    isImporting: Boolean,
    isFormValid: Boolean,
    importStage: XtreamImportStageUi?,
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
                        importStage?.labelResource()
                            ?: R.string.sources_xtream_connecting
                    } else {
                        R.string.sources_xtream_connect
                    },
                ),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun XtreamImportProgress(stage: XtreamImportStageUi?) {
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
internal fun XtreamImportStageUi.localizedLabel(): String =
    stringResource(labelResource())

private fun XtreamImportStageUi.labelResource(): Int =
    when (this) {
        XtreamImportStageUi.AUTHENTICATING ->
            R.string.sources_xtream_stage_authenticating

        XtreamImportStageUi.CATEGORIES ->
            R.string.sources_xtream_stage_categories

        XtreamImportStageUi.LIVE ->
            R.string.sources_xtream_stage_live

        XtreamImportStageUi.MOVIES ->
            R.string.sources_xtream_stage_movies

        XtreamImportStageUi.SERIES ->
            R.string.sources_xtream_stage_series

        XtreamImportStageUi.SAVING ->
            R.string.sources_xtream_stage_saving
    }

internal fun XtreamImportStageUi.progressFraction(): Float =
    when (this) {
        XtreamImportStageUi.AUTHENTICATING -> 1f / 6f
        XtreamImportStageUi.CATEGORIES -> 2f / 6f
        XtreamImportStageUi.LIVE -> 3f / 6f
        XtreamImportStageUi.MOVIES -> 4f / 6f
        XtreamImportStageUi.SERIES -> 5f / 6f
        XtreamImportStageUi.SAVING -> 1f
    }

@Composable
private fun CancelXtreamButton(
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
