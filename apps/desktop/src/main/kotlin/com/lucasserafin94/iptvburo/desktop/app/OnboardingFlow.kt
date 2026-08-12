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
import androidx.compose.ui.text.style.TextOverflow
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
        // The scrollbar is drawn beside the panel, so it needs a Box sized to the panel to align
        // against. Scrolling worked before this — the wheel moved it — but with no bar there was
        // nothing on screen saying the form continued below the fold, so it read as cut off.
        Box(
            // Wraps the panel, not the window: the bar has to sit against the panel's own right
            // edge. Height caps here and the column inside is left to shrink to its content, so a
            // short step stays a short panel instead of being stretched to fill the cap.
            modifier =
                Modifier
                    .padding(BuroSpacing.Xl)
                    .widthIn(max = 620.dp)
                    .heightIn(max = maxPanelHeight * 0.92f),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        // As a panel it gets a surface and a rounded edge, so it reads as sitting
                        // over the app; during first-run it is the whole screen and needs neither.
                        .then(
                            if (onDismiss == null) {
                                Modifier
                            } else {
                                Modifier
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
                // Three dots, not a number: the count is small enough to read at a glance and does
                // not need translating.
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
                        // Inset by the panel's own radius so the bar sits inside the rounded
                        // surface rather than riding on its edge.
                        .padding(vertical = BuroSpacing.Sm, horizontal = 4.dp),
                style =
                    LocalScrollbarStyle.current.copy(
                        thickness = 8.dp,
                        unhoverColor = BuroColors.BorderSoft,
                        hoverColor = BuroColors.Primary,
                    ),
            )
        }
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

    /**
     * The optional music M3U, null unless the user picked one.
     *
     * Held with the rest of the draft so a failed connection does not silently discard a file the
     * user already chose, which would be invisible until they reached the sidebar and found no
     * Músicas entry.
     */
    val musicPlaylist = mutableStateOf<Path?>(null)

    /**
     * This profile's own TMDb key, blank when it should use the shared one.
     *
     * Held with the rest of the draft for the same reason as everything else here: a failed
     * connection returns to this screen, and a key the user pasted must still be there.
     */
    val metadataKey = mutableStateOf("")
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
    /** Opens the file dialog for the optional music playlist; null when the user cancels. */
    onPickMusicPlaylist: () -> Path?,
    onCreate: (
        profileName: String,
        avatarIndex: Int,
        listLabel: String,
        server: String,
        username: String,
        password: String,
        musicPlaylist: Path?,
        /** This profile's own TMDb key; blank means it uses the shared one. */
        metadataKey: String,
    ) -> Unit,
    onUseSaved: (
        profileName: String,
        avatarIndex: Int,
        sourceId: String,
        musicPlaylist: Path?,
        metadataKey: String,
    ) -> Unit,
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
    var musicPlaylist by draft.musicPlaylist
    var metadataKey by draft.metadataKey
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

        // The music playlist sits outside the credentials block: it is offered whether the user is
        // reusing a saved list or adding a new one, because music is per profile either way.
        Spacer(Modifier.height(BuroSpacing.Lg))
        MusicPlaylistField(
            chosen = musicPlaylist,
            onChoose = { onPickMusicPlaylist()?.let { picked -> musicPlaylist = picked } },
            onClear = { musicPlaylist = null },
            text = text,
        )

        // The TMDb key belongs to the profile, so it is asked for here rather than in the settings
        // menu — which is install-wide, and where a per-profile field read as another global one.
        //
        // Optional and blank by default: a household normally has one key, and leaving this empty
        // inherits it. It is filled in when someone wants their own TMDb account's quota used —
        // TMDb rate-limits per key, so several heavy users on one throttle each other.
        Spacer(Modifier.height(BuroSpacing.Lg))
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = text.settingsText.profileKeyLabel,
                    color = BuroColors.Text,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.width(BuroSpacing.Xs))
                Text(
                    // Reuses the music playlist's tag: it is the same word in all four languages,
                    // and DesktopStrings is close enough to the JVM's argument ceiling that a
                    // duplicate field would be a poor trade.
                    text = text.musicPlaylistOptional,
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.labelSmall,
                    modifier =
                        Modifier
                            .clip(BuroRadius.Pill)
                            .background(BuroColors.SurfaceRaised)
                            .padding(horizontal = BuroSpacing.Xs, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = text.settingsText.profileKeyHint,
                color = BuroColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(BuroSpacing.Xs))
            OnboardingField(
                value = metadataKey,
                onValueChange = { metadataKey = it.trim() },
                label = text.metadataKeyPlaceholder,
            )
        }

        Spacer(Modifier.height(BuroSpacing.Lg))
        Button(
            onClick = {
                val saved = reusedSourceId
                if (saved != null) {
                    onUseSaved(profileName, avatarIndex, saved, musicPlaylist, metadataKey)
                } else {
                    onCreate(
                        profileName,
                        avatarIndex,
                        listLabel,
                        server,
                        username,
                        password,
                        musicPlaylist,
                        metadataKey,
                    )
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

/**
 * The optional music playlist, marked as optional in the UI rather than only in the docs.
 *
 * A file picker rather than a text field: the value is a path on this machine, and asking someone
 * to type one is how you get a path with a typo in it that fails silently at the next launch.
 *
 * Leaving it untouched is a first-class outcome — nothing about the app changes — so the control is
 * quiet by default and never blocks the submit button.
 */
@Composable
private fun MusicPlaylistField(
    chosen: Path?,
    onChoose: () -> Unit,
    onClear: () -> Unit,
    text: DesktopStrings,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BuroRadius.Medium)
                .background(BuroColors.Surface)
                .padding(BuroSpacing.Md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text.musicPlaylistLabel,
                color = BuroColors.Text,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.width(BuroSpacing.Xs))
            // The badge, not a parenthesis in the label: it has to be readable at a glance so the
            // field is never mistaken for another thing setup is demanding.
            Text(
                text = text.musicPlaylistOptional,
                modifier =
                    Modifier
                        .clip(BuroRadius.Pill)
                        .background(BuroColors.SurfaceRaised)
                        .padding(horizontal = BuroSpacing.Xs, vertical = 2.dp),
                color = BuroColors.TextSubtle,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(BuroSpacing.Xxs))
        Text(
            text = text.musicPlaylistHint,
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(BuroSpacing.Sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BuroInteractiveSurface(
                onClick = onChoose,
                shape = BuroRadius.Small,
                background = BuroColors.SurfaceRaised,
                activeBackground = BuroColors.SurfaceHover,
                contentDescription = text.musicPlaylistChoose,
            ) { _ ->
                Text(
                    text = text.musicPlaylistChoose,
                    modifier =
                        Modifier.padding(horizontal = BuroSpacing.Md, vertical = BuroSpacing.Xs),
                    color = BuroColors.Text,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.width(BuroSpacing.Sm))
            if (chosen == null) return@Row
            Text(
                // The file name alone. The full path is often long enough to push the remove
                // control off the panel, and the name is what the user recognises anyway.
                text = chosen.fileName?.toString().orEmpty(),
                modifier = Modifier.weight(1f),
                color = BuroColors.Primary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onClear) {
                Text(
                    text = text.musicPlaylistRemove,
                    color = BuroColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
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
