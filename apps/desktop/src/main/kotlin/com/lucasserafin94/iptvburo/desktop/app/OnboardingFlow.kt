package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.security.XtreamSource
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveSurface
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.update.DESKTOP_VERSION
import java.nio.file.Path

/**
 * Shared shell so every setup step sits on the same canvas at the same width.
 *
 * [onDismiss] is null during first-run setup, where there is nothing behind to go back to. Once the
 * app is running the same screens are used to add a profile, and then they read as a panel over the
 * app - dismissable, and visibly on top of something rather than replacing it.
 */
@Composable
private fun OnboardingScaffold(
    step: Int,
    onDismiss: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // The panel is capped at the window height so its scroll has somewhere to go. Without the cap
    // the column simply grew past the bottom edge and the fields below - the password, the submit
    // button - were laid out where nothing could reach them.
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(if (onDismiss == null) BuroColors.Canvas else BuroColors.Scrim),
        contentAlignment = Alignment.Center,
    ) {
        val maxPanelHeight = maxHeight
        val panelScroll = rememberScrollState()
        Column(
            modifier =
                Modifier
                    .widthIn(max = 620.dp)
                    .heightIn(max = maxPanelHeight)
                    .fillMaxWidth()
                    // As a panel it gets a surface and a rounded edge, so it reads as sitting over
                    // the app; during first-run it is the whole screen and needs neither.
                    .then(
                        if (onDismiss == null) {
                            Modifier
                        } else {
                            Modifier
                                .padding(BuroSpacing.Xl)
                                .clip(BuroRadius.Large)
                                .background(BuroColors.Surface)
                        },
                    ).verticalScroll(panelScroll)
                    .padding(BuroSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (onDismiss != null) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "✕",
                            color = BuroColors.TextMuted,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
            Text(
                text = "IPTV BURO",
                color = BuroColors.Primary,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(BuroSpacing.Lg))
            content()
            Spacer(Modifier.height(BuroSpacing.Xl))
            // Three dots, not a number: the count is small enough to read at a glance and does not
            // need translating.
            Row(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
                repeat(TOTAL_STEPS) { index ->
                    Box(
                        modifier =
                            Modifier
                                .size(if (index == step) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == step) BuroColors.Primary else BuroColors.BorderSoft,
                                ),
                    )
                }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(panelScroll),
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = BuroSpacing.Xl),
            style =
                LocalScrollbarStyle.current.copy(
                    thickness = 8.dp,
                    unhoverColor = BuroColors.BorderSoft,
                    hoverColor = BuroColors.Primary,
                ),
        )
    }
}

private const val TOTAL_STEPS = 3

/**
 * Copyright notice, shown once before anything is configured.
 *
 * The app ships no catalogue: it plays what the user's own provider serves. Saying so up front is
 * both honest and the thing a first-time user most needs to understand about what they installed.
 */
@Composable
fun TermsGate(
    text: DesktopStrings,
    onAccept: () -> Unit,
) {
    OnboardingScaffold(step = 0) {
        Text(
            text = text.termsTitle,
            color = BuroColors.Text,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(BuroSpacing.Lg))
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(BuroRadius.Medium)
                    .background(BuroColors.Surface)
                    .padding(BuroSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
        ) {
            listOf(text.termsNoContent, text.termsYourSource, text.termsResponsibility).forEach { line ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("•", color = BuroColors.Primary)
                    Spacer(Modifier.width(BuroSpacing.Sm))
                    Text(
                        text = line,
                        color = BuroColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(BuroSpacing.Lg))
        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = BuroRadius.Small,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = BuroColors.Primary,
                    contentColor = BuroColors.OnPrimary,
                ),
        ) {
            Text(text.termsAccept, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * What the user has typed into setup, kept outside the screen that renders it.
 *
 * The connecting and failure states replace this composable, so anything held in `remember` here
 * would be discarded: a wrong password wiped the whole form, including the fields that were right.
 * Surviving the round trip is the point of this type.
 */
class AccountSetupDraft {
    val profileName = mutableStateOf("")
    val avatarIndex = mutableStateOf(0)
    val listLabel = mutableStateOf("")
    val server = mutableStateOf("")
    val username = mutableStateOf("")
    val password = mutableStateOf("")
    val reusedSourceId = mutableStateOf<String?>(null)
}

/**
 * Profile and playlist, entered together.
 *
 * A profile without a playlist cannot show anything, and a playlist with no profile has nowhere to
 * keep favourites, so asking for them on one screen matches how they are actually used. When a
 * playlist already exists it can be reused, which is the household case: same subscription,
 * separate favourites.
 */
@Composable
fun AccountSetupGate(
    text: DesktopStrings,
    savedSources: List<XtreamSource>,
    draft: AccountSetupDraft,
    photo: Path?,
    onPickPhoto: () -> Unit,
    onClearPhoto: () -> Unit,
    // Null on first run, where there is no app behind this to return to.
    onDismiss: (() -> Unit)? = null,
    onCreate: (profileName: String, avatarIndex: Int, listLabel: String, server: String, username: String, password: String) -> Unit,
    onUseSaved: (profileName: String, avatarIndex: Int, sourceId: String) -> Unit,
) {
    // Hoisted out of this composable so a failed connection can return here with everything the
    // user typed still in place. Held in remember alone, the state died with the screen and the
    // form came back empty — leaving no way to see which field was wrong.
    var profileName by draft.profileName
    var avatarIndex by draft.avatarIndex
    var listLabel by draft.listLabel
    var server by draft.server
    var username by draft.username
    var password by draft.password
    var reusedSourceId by draft.reusedSourceId
    var revealPassword by remember { mutableStateOf(false) }

    val canSubmit =
        profileName.isNotBlank() &&
            if (reusedSourceId != null) {
                true
            } else {
                server.isNotBlank() && username.isNotBlank() && password.isNotBlank()
            }

    OnboardingScaffold(step = 1, onDismiss = onDismiss) {
        Text(
            text = text.setupTitle,
            color = BuroColors.Text,
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(BuroSpacing.Xs))
        Text(
            text = text.setupSubtitle,
            color = BuroColors.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(BuroSpacing.Lg))

        OnboardingField(
            value = profileName,
            onValueChange = { profileName = it.take(24) },
            label = text.setupProfileName,
        )
        Spacer(Modifier.height(BuroSpacing.Md))
        AvatarPicker(
            selectedIndex = avatarIndex,
            onSelect = { index ->
                avatarIndex = index
                // Choosing a drawn avatar replaces a photo picked earlier, otherwise the photo would
                // keep winning and the selection would appear to do nothing.
                onClearPhoto()
            },
            photo = photo,
            onPickPhoto = onPickPhoto,
            onClearPhoto = onClearPhoto,
            text = text,
        )

        if (savedSources.isNotEmpty()) {
            Spacer(Modifier.height(BuroSpacing.Lg))
            Text(
                text = text.setupUseExisting,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(BuroSpacing.Xs))
            savedSources.forEach { source ->
                val selected = source.id == reusedSourceId
                BuroInteractiveSurface(
                    onClick = { reusedSourceId = if (selected) null else source.id },
                    modifier = Modifier.fillMaxWidth(),
                    shape = BuroRadius.Medium,
                    background = if (selected) BuroColors.SurfaceRaised else BuroColors.Surface,
                    contentDescription = source.label,
                ) { _ ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(BuroSpacing.Md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = source.label,
                            color = if (selected) BuroColors.Primary else BuroColors.Text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) Text("✓", color = BuroColors.Primary)
                    }
                }
                Spacer(Modifier.height(BuroSpacing.Xs))
            }
        }

        if (reusedSourceId == null) {
            Spacer(Modifier.height(BuroSpacing.Lg))
            Text(
                text = if (savedSources.isEmpty()) text.setupYourList else text.setupNewList,
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(BuroSpacing.Xs))
            OnboardingField(
                value = listLabel,
                onValueChange = { listLabel = it.take(40) },
                label = text.setupListName,
            )
            Spacer(Modifier.height(BuroSpacing.Sm))
            OnboardingField(
                value = server,
                onValueChange = { server = it.trim() },
                label = text.serverLabel,
            )
            Spacer(Modifier.height(BuroSpacing.Sm))
            OnboardingField(
                value = username,
                onValueChange = { username = it.trim() },
                label = text.usernameLabel,
            )
            Spacer(Modifier.height(BuroSpacing.Sm))
            OnboardingField(
                value = password,
                onValueChange = { password = it },
                label = text.passwordLabel,
                // Revealed on demand: a mistyped password is the most common reason setup fails,
                // and dots give the user no way to spot it.
                secret = !revealPassword,
                trailing = {
                    TextButton(onClick = { revealPassword = !revealPassword }) {
                        Text(
                            text = if (revealPassword) text.hidePassword else text.showPassword,
                            color = BuroColors.TextMuted,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
            )
        }

        Spacer(Modifier.height(BuroSpacing.Lg))
        Button(
            onClick = {
                val saved = reusedSourceId
                if (saved != null) {
                    onUseSaved(profileName, avatarIndex, saved)
                } else {
                    onCreate(profileName, avatarIndex, listLabel, server, username, password)
                }
            },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = BuroRadius.Small,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = BuroColors.Primary,
                    contentColor = BuroColors.OnPrimary,
                    disabledContainerColor = BuroColors.SurfaceRaised,
                    disabledContentColor = BuroColors.TextSubtle,
                ),
        ) {
            Text(text.setupContinue, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OnboardingField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    secret: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = BuroColors.TextSubtle) },
        trailingIcon = trailing,
        singleLine = true,
        visualTransformation =
            if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions.Default,
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

/**
 * Shown while the provider is contacted.
 *
 * The catalogue can take a while on a large playlist, and a still screen reads as a hang, so the
 * shimmer exists to show the app is alive rather than to represent measured progress.
 */
@Composable
fun ConnectingGate(text: DesktopStrings) {
    val transition = rememberInfiniteTransition(label = "connecting")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1_400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "sweep",
    )

    OnboardingScaffold(step = 2) {
        Text(
            text = text.connectingTitle,
            color = BuroColors.Text,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(BuroSpacing.Sm))
        Text(
            text = text.connectingBody,
            color = BuroColors.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(BuroSpacing.Xl))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(BuroColors.Surface),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.35f)
                        .height(6.dp)
                        .padding(start = 0.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    BuroColors.Primary,
                                    Color.Transparent,
                                ),
                            ),
                        )
                        .offsetFraction(offset),
            )
        }
    }
}

/** Slides the sweep across the track without laying out a new element each frame. */
private fun Modifier.offsetFraction(fraction: Float): Modifier =
    this.then(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val travel = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
            layout(constraints.maxWidth, placeable.height) {
                placeable.placeRelative((travel * fraction).toInt(), 0)
            }
        },
    )

/**
 * The playlist did not load.
 *
 * [message] is the provider's own reason, already stripped of the host and credentials by
 * toSafeXtreamMessage. Retry returns to the form rather than restarting setup, because the address
 * or password is usually one character wrong.
 */
@Composable
fun SetupFailedGate(
    text: DesktopStrings,
    message: String?,
    onRetry: () -> Unit,
) {
    OnboardingScaffold(step = 2) {
        Text(
            text = text.setupFailedTitle,
            color = BuroColors.Error,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(BuroSpacing.Sm))
        Text(
            text = message ?: text.setupFailedBody,
            color = BuroColors.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(BuroSpacing.Lg))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = BuroRadius.Small,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = BuroColors.Primary,
                    contentColor = BuroColors.OnPrimary,
                ),
        ) {
            Text(text.setupRetry, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(BuroSpacing.Xs))
        TextButton(onClick = onRetry) {
            Text(
                text = "IPTV BURO v$DESKTOP_VERSION",
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
