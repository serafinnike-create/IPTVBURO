package com.lucasserafin94.iptvburo.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.ui.ProfileUi
import com.lucasserafin94.iptvburo.ui.SourceUi
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroDanger
import com.lucasserafin94.iptvburo.ui.theme.BuroFieldColors
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

/**
 * Editing one profile: its name, its avatar, whether it is a children's profile, and deleting it.
 *
 * The Android counterpart of the desktop's profile editor. Until this existed a profile could only
 * be created — never renamed, never given a different avatar, never removed — so a typo in a name
 * was permanent and the five avatars were assigned round-robin with no way to choose.
 *
 * Deletion asks for confirmation in place rather than through a second dialog: it removes the
 * profile's favourites and viewing history with it, and that is not something to do on one tap.
 */
@Composable
internal fun ProfileEditorDialog(
    profile: ProfileUi,
    avatars: List<String>,
    /** False for the last remaining profile, which cannot be removed — the app needs one. */
    canDelete: Boolean,
    /** Playlists already configured, for this profile to sign in to. */
    sources: List<SourceUi> = emptyList(),
    onSelectSource: ((String?) -> Unit)? = null,
    /** Leaves for the sources screen, which owns credentials and the connection that can fail. */
    onAddSource: (() -> Unit)? = null,
    onSave: (name: String, avatarKey: String, isKids: Boolean, photoUri: String?, clearPhoto: Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var avatarKey by remember(profile.id) { mutableStateOf(profile.avatarKey) }
    var isKids by remember(profile.id) { mutableStateOf(profile.isKids) }
    var confirmingDelete by remember(profile.id) { mutableStateOf(false) }
    var photoUri by remember(profile.id) { mutableStateOf(profile.photoUri) }
    var photoCleared by remember(profile.id) { mutableStateOf(false) }
    val context = LocalContext.current

    // PickVisualMedia, not OpenDocument: it is the system photo picker, and it grants access to the
    // single item chosen rather than asking for the photo library as a whole.
    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                // Persisted, or the URI stops resolving the next time the app starts and the tile
                // silently falls back to the drawn avatar.
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                photoUri = uri.toString()
                photoCleared = false
            }
        }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 460.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(BuroCanvas)
                    .padding(22.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_edit_title),
                color = BuroTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                label = { Text(stringResource(R.string.profile_picker_name)) },
                singleLine = true,
                colors = BuroFieldColors,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(18.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(avatarBrush(avatarKey)),
                    contentAlignment = Alignment.Center,
                ) {
                    val shownPhoto = photoUri.takeUnless { photoCleared }
                    if (shownPhoto != null) {
                        AsyncImage(
                            model = shownPhoto,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            avatarIcon(avatarKey, isKids),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    FocusSurface(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.profile_edit_choose_photo),
                            color = BuroTextPrimary,
                            fontSize = 13.sp,
                        )
                    }
                    // Only where there is something to remove, so the dialog does not carry a
                    // control that would do nothing.
                    if (photoUri != null && !photoCleared) {
                        Spacer(Modifier.height(6.dp))
                        FocusSurface(
                            onClick = {
                                photoCleared = true
                                photoUri = null
                            },
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.profile_edit_remove_photo),
                                color = BuroTextSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.profile_edit_avatar),
                color = BuroTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(avatars, key = { it }) { key ->
                    AvatarChoice(
                        avatarKey = key,
                        isKids = isKids,
                        selected = key == avatarKey,
                        onClick = { avatarKey = key },
                    )
                }
            }

            // Which playlist this profile signs in to.
            //
            // Only where there is a choice to make: a household with one playlist has nothing to
            // decide, and a picker with a single entry is a control that cannot do anything.
            if (onSelectSource != null && sources.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.profile_edit_source),
                    color = BuroTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // "Any" is how a profile goes back to no preference, which is the state every
                    // profile starts in and the right answer for most households.
                    item(key = "source-any") {
                        SourceChoice(
                            label = stringResource(R.string.profile_edit_source_any),
                            selected = profile.sourceId == null,
                            onClick = { onSelectSource(null) },
                        )
                    }
                    items(sources, key = SourceUi::id) { source ->
                        SourceChoice(
                            label = source.name,
                            selected = source.id == profile.sourceId,
                            onClick = { onSelectSource(source.id) },
                        )
                    }
                    onAddSource?.let { addSource ->
                        item(key = "source-add") {
                            SourceChoice(
                                label = stringResource(R.string.profile_edit_source_new),
                                selected = false,
                                onClick = addSource,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            FocusSurface(
                onClick = { isKids = !isKids },
                selected = isKids,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.ChildCare,
                        contentDescription = null,
                        tint = if (isKids) BuroAccent else BuroTextSecondary,
                    )
                    Column {
                        Text(
                            text =
                                stringResource(
                                    if (isKids) R.string.profile_picker_child else R.string.profile_picker_adult,
                                ),
                            color = BuroTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.profile_edit_kids_hint),
                            color = BuroTextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FocusSurface(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(50.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.common_cancel), color = BuroTextSecondary, fontSize = 14.sp)
                }
                FocusSurface(
                    onClick = { onSave(name, avatarKey, isKids, photoUri, photoCleared) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1f).height(50.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.profile_edit_save),
                        color = BuroTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (canDelete) {
                Spacer(Modifier.height(14.dp))
                // Two taps, not one. Deleting takes the profile's favourites and history with it,
                // and the confirmation states that rather than leaving it to be discovered.
                FocusSurface(
                    onClick = { if (confirmingDelete) onDelete() else confirmingDelete = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = BuroDanger)
                        Text(
                            text =
                                stringResource(
                                    if (confirmingDelete) {
                                        R.string.profile_edit_delete_confirm
                                    } else {
                                        R.string.profile_edit_delete
                                    },
                                ),
                            color = BuroDanger,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/** One avatar in the picker, drawn exactly as the profile tile will draw it. */
@Composable
private fun AvatarChoice(
    avatarKey: String,
    isKids: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        selected = selected,
        shape = CircleShape,
        modifier = Modifier.size(62.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(if (selected) 56.dp else 48.dp)
                    .clip(CircleShape)
                    .background(avatarBrush(avatarKey)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                avatarIcon(avatarKey, isKids),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/** One playlist choice, drawn as a pill so a long provider name does not stretch the dialog. */
@Composable
private fun SourceChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        selected = selected,
        shape = RoundedCornerShape(50),
        contentAlignment = Alignment.Center,
        modifier = Modifier.height(40.dp),
    ) {
        Text(
            text = label,
            color = if (selected) BuroAccent else BuroTextPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
