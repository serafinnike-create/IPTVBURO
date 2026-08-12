package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.desktop.user.DesktopProfile
import java.nio.file.Path

/**
 * Editing a profile: its name, picture, Kids setting, playlist and music file.
 *
 * ## Why the playlist is a button rather than a field
 *
 * Changing which playlist a profile signs in to is not the same kind of change as renaming it. It
 * swaps the account, which means new credentials, a fresh catalogue, and a connection that can fail.
 * That belongs in the account screen, which already knows how to do all three — so this offers a
 * button that goes there rather than a text field pretending the change is cosmetic.
 *
 * Everything else saves in place, because nothing else here can fail.
 *
 * ## The music file
 *
 * Cleared as easily as it is set. It is optional by design: a profile with no music file behaves
 * exactly as the app did before music existed, and somebody who added one by mistake should not have
 * to delete the profile to undo it.
 */
@Composable
fun ProfileEditorDialog(
    profile: DesktopProfile,
    musicPath: Path?,
    photo: Path?,
    sourceLabel: String?,
    onSave: (name: String, isKids: Boolean, avatarIndex: Int) -> Unit,
    onChangeSource: () -> Unit,
    onChooseMusic: () -> Unit,
    onClearMusic: () -> Unit,
    onPickPhoto: () -> Unit,
    onClearPhoto: () -> Unit,
    onDismiss: () -> Unit,
) {
    val allText = strings
    val text = allText.settingsText

    // Seeded from the profile and edited locally, so dismissing discards rather than half-applies.
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var isKids by remember(profile.id) { mutableStateOf(profile.isKids) }
    var avatarIndex by remember(profile.id) { mutableStateOf(profile.avatarIndex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BuroColors.Surface,
        title = { Text(text.profileEditTitle, color = BuroColors.Text) },
        text = {
            Column(
                // Scrollable: with the avatar grid, the playlist row and the music row, this is
                // taller than a small laptop's dialog area, and a section a user cannot reach is a
                // section that does not exist.
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text(text.profileNameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(BuroSpacing.Md))

                Text(text.profileAvatarLabel, color = BuroColors.TextMuted)
                Spacer(Modifier.height(BuroSpacing.Xs))
                AvatarPicker(
                    selectedIndex = avatarIndex,
                    onSelect = { avatarIndex = it },
                    photo = photo,
                    onPickPhoto = onPickPhoto,
                    onClearPhoto = onClearPhoto,
                    text = allText,
                )

                Spacer(Modifier.height(BuroSpacing.Md))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isKids,
                        onCheckedChange = { isKids = it },
                        colors = CheckboxDefaults.colors(checkedColor = BuroColors.Primary),
                    )
                    Column {
                        Text(text.profileKidsLabel, color = BuroColors.Text)
                        Text(
                            text = text.profileKidsHint,
                            color = BuroColors.TextSubtle,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                Spacer(Modifier.height(BuroSpacing.Md))

                // The playlist. A label and a button, because changing it leaves this dialog.
                Text(text.profileSourceLabel, color = BuroColors.TextMuted)
                Spacer(Modifier.height(BuroSpacing.Xxs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = sourceLabel ?: text.profileSourceNone,
                        color = BuroColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(220.dp),
                    )
                    TextButton(onClick = onChangeSource) {
                        Text(text.profileSourceChange, color = BuroColors.Primary)
                    }
                }

                Spacer(Modifier.height(BuroSpacing.Sm))

                Text(text.profileMusicLabel, color = BuroColors.TextMuted)
                Spacer(Modifier.height(BuroSpacing.Xxs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        // The file name alone. A full path is long enough to push the buttons off
                        // the dialog, and the name is what the user recognises.
                        text = musicPath?.fileName?.toString() ?: text.profileMusicNone,
                        color = BuroColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(180.dp),
                    )
                    Row {
                        if (musicPath != null) {
                            TextButton(onClick = onClearMusic) {
                                Text(text.profileMusicClear, color = BuroColors.TextSubtle)
                            }
                        }
                        TextButton(onClick = onChooseMusic) {
                            Text(text.profileMusicChoose, color = BuroColors.Primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, isKids, avatarIndex) },
                // A blank name would save as nothing and leave an unlabelled circle on the gate.
                enabled = name.isNotBlank(),
            ) {
                Text(text.profileSave, color = BuroColors.Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel, color = BuroColors.TextMuted)
            }
        },
    )
}
