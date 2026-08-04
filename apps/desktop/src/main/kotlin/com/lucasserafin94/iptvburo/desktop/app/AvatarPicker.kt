package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.ui.BURO_AVATARS
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveSurface
import com.lucasserafin94.iptvburo.desktop.ui.BuroProfileAvatar
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import java.nio.file.Path

/**
 * The face of a profile, wherever it appears.
 *
 * A photo always wins over the drawn avatar: the user chose it explicitly. Every screen goes through
 * here so the header chip, the picker and the profile gate can never disagree about what a profile
 * looks like.
 */
@Composable
fun ProfileFace(
    avatarIndex: Int,
    photo: Path?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    if (photo != null) {
        LocalImage(
            path = photo,
            contentDescription = null,
            modifier = modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
        ) {
            // The file was readable when it was stored; if it has since been deleted or corrupted,
            // falling back to the drawn avatar keeps the profile recognisable.
            BuroProfileAvatar(index = avatarIndex, size = size, modifier = modifier)
        }
    } else {
        BuroProfileAvatar(index = avatarIndex, size = size, modifier = modifier)
    }
}

/**
 * Avatar selection: the drawn set, plus the option to use a photo.
 *
 * Wraps rather than scrolls. With sixteen avatars a single row would have run off the edge of the
 * narrower setup panel, and a horizontally scrolling strip hides choices the user does not know are
 * there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvatarPicker(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    photo: Path?,
    onPickPhoto: () -> Unit,
    onClearPhoto: () -> Unit,
    text: DesktopStrings,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
                verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
            ) {
                BURO_AVATARS.forEachIndexed { index, avatar ->
                    // A photo overrides the avatar, so nothing in the set reads as selected while
                    // one is set — otherwise two things would claim to be the current face.
                    val selected = photo == null && index == selectedIndex
                    BuroInteractiveSurface(
                        onClick = { onSelect(index) },
                        shape = CircleShape,
                        background = Color.Transparent,
                        contentDescription = avatar.id,
                    ) { _ ->
                        Box(
                            modifier =
                                Modifier
                                    .size(TILE)
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color =
                                            if (selected) {
                                                BuroColors.Primary
                                            } else {
                                                BuroColors.BorderSoft
                                            },
                                        shape = CircleShape,
                                    ).padding(3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BuroProfileAvatar(index = index, size = TILE - 8.dp)
                        }
                    }
                }

                // The photo tile sits with the avatars: it is one more way to answer the same
                // question, not a separate setting.
                BuroInteractiveSurface(
                    onClick = onPickPhoto,
                    shape = CircleShape,
                    background = Color.Transparent,
                    contentDescription = text.avatarUsePhoto,
                ) { _ ->
                    Box(
                        modifier =
                            Modifier
                                .size(TILE)
                                .border(
                                    width = if (photo != null) 2.dp else 1.dp,
                                    color =
                                        if (photo != null) BuroColors.Primary else BuroColors.BorderSoft,
                                    shape = CircleShape,
                                ).padding(3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (photo != null) {
                            LocalImage(
                                path = photo,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            ) {
                                PhotoPlaceholder()
                            }
                        } else {
                            PhotoPlaceholder()
                        }
                    }
                }
            }

            if (photo != null) {
                Spacer(Modifier.height(BuroSpacing.Xs))
                TextButton(onClick = onClearPhoto) {
                    Text(
                        text = text.avatarRemovePhoto,
                        color = BuroColors.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize().clip(CircleShape).background(BuroColors.SurfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+",
            color = BuroColors.TextMuted,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Light,
        )
    }
}

private val TILE = 46.dp
