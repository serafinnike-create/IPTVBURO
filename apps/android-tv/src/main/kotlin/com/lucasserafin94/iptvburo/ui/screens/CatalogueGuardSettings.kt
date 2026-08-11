package com.lucasserafin94.iptvburo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.domain.model.ParentalPin
import com.lucasserafin94.iptvburo.domain.model.SubtitlePresentation
import com.lucasserafin94.iptvburo.domain.model.SubtitleTextColour
import com.lucasserafin94.iptvburo.domain.model.SubtitleTextSize
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroDanger
import com.lucasserafin94.iptvburo.ui.theme.BuroFieldColors
import com.lucasserafin94.iptvburo.ui.theme.BuroGold
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroSurfaceRaised
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

/** One category as settings needs it: what to call it and what to key the switches by. */
data class GuardCategoryUi(
    val id: String,
    val name: String,
    val sectionLabel: String,
)

/**
 * Everything the three new settings sections need, bundled.
 *
 * One parameter rather than nine on a signature that already carries eleven. They are passed
 * together and always change together, so splitting them would only make the call site longer.
 */
data class CatalogueGuardUi(
    val hasPin: Boolean,
    val lockAdultCategories: Boolean,
    val pinMessage: String?,
    val subtitles: SubtitlePresentation,
    val categories: List<GuardCategoryUi>,
    val hiddenIds: Set<String>,
    val lockedIds: Set<String>,
    val onSetPin: (newPin: String, currentPin: String?) -> Unit,
    val onClearPin: (currentPin: String) -> Unit,
    val onLockAdultChange: (Boolean) -> Unit,
    val onSubtitlesChange: (SubtitlePresentation) -> Unit,
    val onHiddenChange: (String, Boolean) -> Unit,
    val onLockedChange: (String, Boolean) -> Unit,
)

/** The three sections, in the order Windows shows them: subtitles, then the lock, then categories. */
@Composable
internal fun CatalogueGuardSections(
    guard: CatalogueGuardUi,
    compact: Boolean,
) {
    SubtitleSettingsCard(
        presentation = guard.subtitles,
        compact = compact,
        onChange = guard.onSubtitlesChange,
    )
    Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
    ParentalPinCard(
        hasPin = guard.hasPin,
        lockAdultCategories = guard.lockAdultCategories,
        compact = compact,
        onSetPin = guard.onSetPin,
        onClearPin = guard.onClearPin,
        onLockAdultChange = guard.onLockAdultChange,
        errorMessage = guard.pinMessage,
    )
    Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
    CategoryVisibilityCard(
        categories = guard.categories,
        hiddenIds = guard.hiddenIds,
        lockedIds = guard.lockedIds,
        canLock = guard.hasPin,
        compact = compact,
        onHiddenChange = guard.onHiddenChange,
        onLockedChange = guard.onLockedChange,
    )
}

/**
 * Setting, changing and removing the PIN that opens locked categories.
 *
 * The same shape as the desktop's panel, and the same honesty about what it is: four digits keep a
 * child out, not a determined adult, and the wording does not pretend otherwise.
 *
 * Changing or removing asks for the current PIN. Without that the lock would be decorative — anyone
 * could clear it from the screen that offers it.
 */
@Composable
internal fun ParentalPinCard(
    hasPin: Boolean,
    lockAdultCategories: Boolean,
    compact: Boolean,
    onSetPin: (newPin: String, currentPin: String?) -> Unit,
    onClearPin: (currentPin: String) -> Unit,
    onLockAdultChange: (Boolean) -> Unit,
    errorMessage: String?,
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }

    SettingsCard(compact = compact) {
        Text(
            text = stringResource(R.string.parental_title),
            color = BuroTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.parental_hint),
            color = BuroTextSecondary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))

        // Only asked for when one exists: a first-time setup demanding the current PIN would be
        // unanswerable.
        if (hasPin) {
            PinField(
                value = currentPin,
                onValueChange = { entered ->
                    currentPin = entered.filter(Char::isDigit).take(ParentalPin.LENGTH)
                },
                label = stringResource(R.string.parental_current_pin),
            )
            Spacer(Modifier.height(8.dp))
        }
        PinField(
            value = newPin,
            onValueChange = { entered ->
                newPin = entered.filter(Char::isDigit).take(ParentalPin.LENGTH)
            },
            label = stringResource(R.string.parental_new_pin),
        )

        errorMessage?.let { shown ->
            Spacer(Modifier.height(6.dp))
            Text(text = shown, color = BuroDanger, fontSize = 12.sp)
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsTextButton(
                label =
                    stringResource(
                        if (hasPin) R.string.parental_change_pin else R.string.parental_set_pin,
                    ),
                onClick = {
                    onSetPin(newPin, currentPin.takeIf { hasPin })
                    newPin = ""
                    currentPin = ""
                },
            )
            if (hasPin) {
                SettingsTextButton(
                    label = stringResource(R.string.parental_remove_pin),
                    tint = BuroDanger,
                    onClick = {
                        onClearPin(currentPin)
                        newPin = ""
                        currentPin = ""
                    },
                )
            }
        }

        // Offered only once a PIN exists. A switch that locks categories with no way to open them
        // would shut the user out of their own catalogue.
        if (hasPin) {
            Spacer(Modifier.height(12.dp))
            SwitchRow(
                label = stringResource(R.string.parental_lock_adult),
                checked = lockAdultCategories,
                onCheckedChange = onLockAdultChange,
            )
        }
    }
}

/** How subtitles are drawn. Applies to the next title played, which the hint says outright. */
@Composable
internal fun SubtitleSettingsCard(
    presentation: SubtitlePresentation,
    compact: Boolean,
    onChange: (SubtitlePresentation) -> Unit,
) {
    SettingsCard(compact = compact) {
        Text(
            text = stringResource(R.string.subtitles_title),
            color = BuroTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.subtitles_hint),
            color = BuroTextSecondary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.subtitles_size),
            color = BuroTextSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        // A row that scrolls rather than a wrapping one: four sizes fit on a phone, but the
        // translated labels do not always, and a scroll never hides an option off-screen.
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(SubtitleTextSize.entries) { size ->
                SettingsPill(
                    label = stringResource(size.labelResource()),
                    selected = size == presentation.size,
                    onClick = { onChange(presentation.copy(size = size)) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.subtitles_colour),
            color = BuroTextSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(SubtitleTextColour.entries) { colour ->
                SettingsPill(
                    label = stringResource(colour.labelResource()),
                    selected = colour == presentation.colour,
                    onClick = { onChange(presentation.copy(colour = colour)) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SwitchRow(
            label = stringResource(R.string.subtitles_background),
            checked = presentation.background,
            onCheckedChange = { on -> onChange(presentation.copy(background = on)) },
        )
    }
}

/**
 * Every category, each with a switch to hide it and — once a PIN exists — one to lock it.
 *
 * Collapsed by default and capped in height. A provider carries several hundred categories, and
 * listing them inline buries every other setting; the header says how many there are so the user
 * can decide whether opening it is worth it.
 */
@Composable
internal fun CategoryVisibilityCard(
    categories: List<GuardCategoryUi>,
    hiddenIds: Set<String>,
    lockedIds: Set<String>,
    canLock: Boolean,
    compact: Boolean,
    onHiddenChange: (String, Boolean) -> Unit,
    onLockedChange: (String, Boolean) -> Unit,
) {
    if (categories.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }

    SettingsCard(compact = compact) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.categories_title),
                    color = BuroTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    // The count answers "is this worth opening?" before it is opened.
                    text = "${categories.size} · ${stringResource(R.string.categories_hint)}",
                    color = BuroTextSecondary,
                    fontSize = 13.sp,
                )
            }
            Text(
                text = if (expanded) "⌃" else "⌄",
                color = BuroTextSecondary,
                fontSize = 20.sp,
            )
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            // Its own scroll area rather than growing the settings page: several hundred rows
            // inside a page-level scroll makes everything below them unreachable in practice.
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(categories, key = GuardCategoryUi::id) { category ->
                    CategoryGuardRow(
                        category = category,
                        hidden = category.id in hiddenIds,
                        locked = category.id in lockedIds,
                        canLock = canLock,
                        onHiddenChange = { hide -> onHiddenChange(category.id, hide) },
                        onLockedChange = { lock -> onLockedChange(category.id, lock) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryGuardRow(
    category: GuardCategoryUi,
    hidden: Boolean,
    locked: Boolean,
    canLock: Boolean,
    onHiddenChange: (Boolean) -> Unit,
    onLockedChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(text = category.name, color = BuroTextPrimary, fontSize = 14.sp)
        // Only when there is something true to say. The item count is not fetched for this list —
        // it would mean three more queries over several hundred categories — and printing "0 itens"
        // under a category that is full is worse than printing nothing.
        if (category.sectionLabel.isNotBlank()) {
            Text(text = category.sectionLabel, color = BuroTextSecondary, fontSize = 11.sp)
        }
        Spacer(Modifier.height(4.dp))
        SwitchRow(
            label = stringResource(R.string.category_hide),
            checked = hidden,
            onCheckedChange = onHiddenChange,
            labelSize = 12.sp,
        )
        if (canLock) {
            SwitchRow(
                label = stringResource(R.string.category_lock),
                checked = locked,
                onCheckedChange = onLockedChange,
                labelSize = 12.sp,
            )
        }
    }
}

/**
 * Asks for the PIN before a locked category opens.
 *
 * A wrong PIN says only that it was wrong. Distinguishing it from "no PIN set" would tell a child
 * which of the two they are facing.
 */
@Composable
internal fun ParentalUnlockDialog(
    categoryName: String,
    wrong: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BuroSurfaceRaised, RoundedCornerShape(20.dp))
                .padding(22.dp),
        ) {
            Text(
                text = stringResource(R.string.parental_locked),
                color = BuroTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                // Named, so it is obvious which lock is being answered rather than just that one is.
                text = "$categoryName · ${stringResource(R.string.parental_unlock)}",
                color = BuroTextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(14.dp))
            PinField(
                value = pin,
                onValueChange = { entered ->
                    pin = entered.filter(Char::isDigit).take(ParentalPin.LENGTH)
                },
                label = stringResource(R.string.parental_current_pin),
            )
            if (wrong) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.parental_wrong_pin),
                    color = BuroDanger,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsTextButton(
                    label = stringResource(R.string.parental_unlock_confirm),
                    onClick = {
                        if (pin.length == ParentalPin.LENGTH) {
                            onSubmit(pin)
                            // Cleared because a rejected PIN is not a starting point for the next
                            // attempt; the prompt stays up so it can simply be retyped.
                            pin = ""
                        }
                    },
                )
                SettingsTextButton(
                    label = stringResource(R.string.common_cancel),
                    onClick = onDismiss,
                )
            }
        }
    }
}

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
        label = { Text(label) },
        // Masked as it is typed: the point is a child in the room, and that is exactly when it is
        // being entered.
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                capitalization = KeyboardCapitalization.None,
            ),
        colors = BuroFieldColors,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SettingsCard(
    compact: Boolean,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BuroSurface, RoundedCornerShape(18.dp))
            .padding(if (compact) 16.dp else 20.dp),
        content = content,
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    labelSize: androidx.compose.ui.unit.TextUnit = 14.sp,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = BuroTextPrimary,
            fontSize = labelSize,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            // Material's default switch is purple, which is not a colour this app uses anywhere.
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = BuroGold,
                    checkedTrackColor = BuroGold.copy(alpha = 0.35f),
                    uncheckedThumbColor = BuroTextSecondary,
                    uncheckedTrackColor = BuroSurfaceRaised,
                ),
        )
    }
}

@Composable
private fun SettingsPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        selected = selected,
        shape = RoundedCornerShape(50),
        contentAlignment = Alignment.Center,
        modifier = Modifier.height(38.dp),
    ) {
        Text(
            text = label,
            color = if (selected) BuroAccent else BuroTextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun SettingsTextButton(
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = BuroTextPrimary,
) {
    FocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        contentAlignment = Alignment.Center,
        modifier = Modifier.height(44.dp),
    ) {
        Text(
            text = label,
            color = tint,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
    }
}

private fun SubtitleTextSize.labelResource(): Int =
    when (this) {
        SubtitleTextSize.SMALL -> R.string.subtitles_size_small
        SubtitleTextSize.MEDIUM -> R.string.subtitles_size_medium
        SubtitleTextSize.LARGE -> R.string.subtitles_size_large
        SubtitleTextSize.HUGE -> R.string.subtitles_size_huge
    }

private fun SubtitleTextColour.labelResource(): Int =
    when (this) {
        SubtitleTextColour.WHITE -> R.string.subtitles_colour_white
        SubtitleTextColour.YELLOW -> R.string.subtitles_colour_yellow
        SubtitleTextColour.GREY -> R.string.subtitles_colour_grey
        SubtitleTextColour.GREEN -> R.string.subtitles_colour_green
        SubtitleTextColour.CYAN -> R.string.subtitles_colour_cyan
    }
