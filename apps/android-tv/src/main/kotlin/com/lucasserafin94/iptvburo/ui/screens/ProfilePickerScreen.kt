package com.lucasserafin94.iptvburo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.ui.ProfileUi
import com.lucasserafin94.iptvburo.ui.SourceUi
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroFieldColors
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

@Composable
fun ProfilePickerScreen(
    profiles: List<ProfileUi>,
    onSelect: (String) -> Unit,
    onCreate: (name: String, isKids: Boolean, sourceId: String?) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Opens Settings, where the per-profile TMDB key lives.
     *
     * Null on the sign-in picker, which runs before any profile is active and therefore before
     * there is anywhere to store a key. Non-null on the Perfis destination inside the app, because
     * users look for per-profile settings on the profile screen — this screen having nothing to say
     * about the key was reported as the key being impossible to set.
     */
    onOpenSettings: (() -> Unit)? = null,
    /**
     * Editing, when this screen is the Perfis destination rather than the sign-in gate.
     *
     * Null on the gate: someone choosing who is watching should not be able to rename or delete a
     * profile by mistake, and there is no active profile to authorise it.
     */
    avatars: List<String> = emptyList(),
    onUpdateProfile: (
        (id: String, name: String, avatarKey: String, isKids: Boolean, photoUri: String?, clearPhoto: Boolean) -> Unit
    )? = null,
    onDeleteProfile: ((String) -> Unit)? = null,
    sources: List<SourceUi> = emptyList(),
    onSelectProfileSource: ((profileId: String, sourceId: String?) -> Unit)? = null,
    onAddSource: (() -> Unit)? = null,
) {
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var isKids by remember { mutableStateOf(false) }
    /** Null means "no preference", which is what a household with one playlist should get. */
    var newSourceId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }
    val editable = onUpdateProfile != null && avatars.isNotEmpty()
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(BuroCanvas, BuroSurface, BuroCanvas)))
                .safeDrawingPadding()
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("IPTV  BURO", color = BuroTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.profile_picker_title), color = BuroTextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.profile_picker_subtitle), color = BuroTextSecondary, fontSize = 15.sp)
            Spacer(Modifier.height(30.dp))
            // FlowRow, not LazyRow. Two profiles already overflow 360dp, and a centred lazy row
            // drew "Adicionar" past the right edge with nothing on screen to suggest it scrolled —
            // so creating a profile looked impossible once a second one existed. Wrapping keeps
            // every card reachable at any width and still forms one line where there is room.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                profiles.forEach { profile ->
                    ProfileCard(
                        profile = profile,
                        onClick = { onSelect(profile.id) },
                        onEdit = if (editable) { { editing = profile.id } } else null,
                    )
                }
                if (profiles.size < 5) {
                    FocusSurface(onClick = { adding = true }, modifier = Modifier.size(128.dp)) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = BuroAccent, modifier = Modifier.size(42.dp))
                            Text(stringResource(R.string.profile_picker_add), color = BuroTextPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }
            if (adding) {
                Spacer(Modifier.height(28.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(24) },
                        label = { Text(stringResource(R.string.profile_picker_name)) },
                        singleLine = true,
                        colors = BuroFieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Which playlist the new profile signs in to, offered at creation as the
                    // desktop does. Reusing one already configured is the common case in a
                    // household; adding another leaves for the sources screen, which owns
                    // credentials and a connection that can fail.
                    if (sources.isNotEmpty() && onSelectProfileSource != null) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.profile_edit_source),
                            color = BuroTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item(key = "new-source-any") {
                                NewProfileSourceChoice(
                                    label = stringResource(R.string.profile_edit_source_any),
                                    selected = newSourceId == null,
                                    onClick = { newSourceId = null },
                                )
                            }
                            items(sources, key = SourceUi::id) { source ->
                                NewProfileSourceChoice(
                                    label = source.name,
                                    selected = source.id == newSourceId,
                                    onClick = { newSourceId = source.id },
                                )
                            }
                            onAddSource?.let { addSource ->
                                item(key = "new-source-add") {
                                    NewProfileSourceChoice(
                                        label = stringResource(R.string.profile_edit_source_new),
                                        selected = false,
                                        onClick = addSource,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FocusSurface(onClick = { isKids = !isKids }, modifier = Modifier.height(52.dp).weight(1f)) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ChildCare, null, tint = if (isKids) BuroAccent else BuroTextSecondary)
                                Text(
                                    if (isKids) stringResource(R.string.profile_picker_child) else stringResource(R.string.profile_picker_adult),
                                    color = BuroTextPrimary,
                                )
                            }
                        }
                        FocusSurface(
                            onClick = {
                                if (newName.isNotBlank()) {
                                    onCreate(newName, isKids, newSourceId)
                                    newName = ""
                                    newSourceId = null
                                    adding = false
                                }
                            },
                            modifier = Modifier.height(52.dp).weight(1f),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.profile_picker_create), color = BuroTextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // The way to the TMDB key, on the screen users go to looking for it.
            onOpenSettings?.let { openSettings ->
                Spacer(Modifier.height(26.dp))
                FocusSurface(
                    onClick = openSettings,
                    modifier = Modifier.widthIn(max = 420.dp).height(52.dp),
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = BuroAccent)
                        Column {
                            Text(
                                text = stringResource(R.string.settings_metadata_section),
                                color = BuroTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.profile_picker_open_settings),
                                color = BuroTextSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { profileId ->
        profiles.firstOrNull { it.id == profileId }?.let { profile ->
            ProfileEditorDialog(
                profile = profile,
                avatars = avatars,
                // The last profile stays: the app has no meaningful state without one, and a user
                // who removed it would face an empty picker with no way forward.
                canDelete = profiles.size > 1 && onDeleteProfile != null,
                sources = sources,
                onSelectSource =
                    onSelectProfileSource?.let { select ->
                        { sourceId -> select(profile.id, sourceId) }
                    },
                onAddSource = onAddSource,
                onSave = { name, avatarKey, kids, photoUri, clearPhoto ->
                    onUpdateProfile?.invoke(profile.id, name, avatarKey, kids, photoUri, clearPhoto)
                    editing = null
                },
                onDelete = {
                    onDeleteProfile?.invoke(profile.id)
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }
    }
}

@Composable
private fun ProfileCard(
    profile: ProfileUi,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
    FocusSurface(onClick = onClick, modifier = Modifier.size(128.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier.size(70.dp).clip(CircleShape).background(avatarBrush(profile.avatarKey)),
                contentAlignment = Alignment.Center,
            ) {
                // The photo wins where there is one: somebody who chose their own face expects to
                // see it, not the fallback that was only ever a stand-in.
                if (profile.photoUri != null) {
                    AsyncImage(
                        model = profile.photoUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        avatarIcon(profile.avatarKey, profile.isKids),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(profile.name, color = BuroTextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (profile.isKids) Text(stringResource(R.string.profile_picker_kids_badge), color = BuroAccent, fontSize = 11.sp)
        }
    }
        // Below the tile rather than on it: tapping the tile means "watch as this person", which
        // is the common action and must not be crowded by a control that changes it.
        onEdit?.let { edit ->
            Spacer(Modifier.height(6.dp))
            FocusSurface(
                onClick = edit,
                modifier = Modifier.height(34.dp).widthIn(min = 92.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.profile_edit_open),
                    color = BuroTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

internal fun avatarBrush(key: String): Brush =
    when (key) {
        "ember" -> Brush.linearGradient(listOf(Color(0xFFB84A3A), Color(0xFFF0A35A)))
        "forest" -> Brush.linearGradient(listOf(Color(0xFF184C3C), Color(0xFF68B78A)))
        "ocean" -> Brush.linearGradient(listOf(Color(0xFF173B63), Color(0xFF4A8CB8)))
        "moon" -> Brush.linearGradient(listOf(Color(0xFF3C365B), Color(0xFF8D82B7)))
        else -> Brush.linearGradient(listOf(Color(0xFF6B5A39), Color(0xFFD4B36A)))
    }

/**
 * A distinct mark per avatar.
 *
 * Every tile drew the same person glyph, so five different gradients still read as five identical
 * figures — the reason the avatars looked like interchangeable dummies. The shape is what tells
 * them apart at a glance; the colour only reinforces it.
 *
 * A kids profile keeps its own glyph regardless of avatar, because "this is the children's profile"
 * is more important to see than which colour it was given.
 */
internal fun avatarIcon(
    key: String,
    isKids: Boolean,
): ImageVector =
    when {
        isKids -> Icons.Default.ChildCare
        key == "ember" -> Icons.Default.LocalFireDepartment
        key == "forest" -> Icons.Default.Park
        key == "ocean" -> Icons.Default.Waves
        key == "moon" -> Icons.Default.NightlightRound
        else -> Icons.Default.AutoAwesome
    }

/** One playlist choice on the creation form, drawn as the editor's pills are. */
@Composable
private fun NewProfileSourceChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        selected = selected,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
        contentAlignment = Alignment.Center,
        modifier = Modifier.height(40.dp),
    ) {
        Text(
            text = label,
            color = if (selected) BuroAccent else BuroTextPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
