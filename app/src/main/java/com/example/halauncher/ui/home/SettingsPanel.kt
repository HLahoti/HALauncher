package com.example.halauncher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.halauncher.R
import com.example.halauncher.config.PinPickerConfig
import com.example.halauncher.config.SearchConfig
import com.example.halauncher.config.SettingsPanelConfig
import com.example.halauncher.config.ThemeConfig
import com.example.halauncher.model.AccessState
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.service.DeviceLock
import com.example.halauncher.ui.components.labelRes
import com.example.halauncher.ui.components.liquidGlass
import com.example.halauncher.util.Haptics
import kotlin.math.roundToInt

/**
 * The launcher's settings, opened by long-pressing the empty home screen. Glass cards, each with a
 * short explanation of what it does and any permission it needs: two toggles and a vibration-intensity
 * slider. Laid out like the pinned-app picker: a full scrim over the (blurred) home screen, a glass
 * header with a Done button, then the cards.
 *
 * A "Lock settings" toggle sits at the very top, always usable. While it's on, every other setting is
 * greyed out and can't be changed, guarding against accidental taps.
 *
 * @param settingsLocked whether the other settings are frozen; the lock toggle itself stays live.
 * @param onSettingsLockedChange saves the lock preference.
 * @param onDoubleTapToLockChange saves the preference; the caller also gates the double-tap gesture on it.
 * @param onReduceIndexSizeChange saves the preference and re-indexes files so the change takes effect.
 * @param onHapticIntensityChange saves the tick strength (0f–1f); every haptic reads it on its next buzz.
 * @param onDefaultSearchCategoryChange saves the category search opens on (null for "All").
 * @param onHowToUse opens the "How to use this app" guide; stays available even while settings are locked.
 * @param onOpenSetup opens the "Set-up & permissions" screen; stays available even while settings are locked.
 * @param contactsAccess where the contacts permission stands; the set-up row warns while it's off.
 * @param filesAccess where file access stands; the set-up row warns while it's off.
 */
@Composable
fun SettingsPanel(
    settingsLocked: Boolean,
    doubleTapToLock: Boolean,
    reduceIndexSize: Boolean,
    hapticIntensity: Float,
    defaultSearchCategory: SearchCategory?,
    onSettingsLockedChange: (Boolean) -> Unit,
    onDoubleTapToLockChange: (Boolean) -> Unit,
    onReduceIndexSizeChange: (Boolean) -> Unit,
    onHapticIntensityChange: (Float) -> Unit,
    onDefaultSearchCategoryChange: (SearchCategory?) -> Unit,
    onHowToUse: () -> Unit,
    onOpenSetup: () -> Unit,
    contactsAccess: AccessState,
    filesAccess: AccessState,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    // Turning the lock toggle on is useless until HALauncher is an active device admin, so offer to.
    val promptEnableLock = rememberEnableLockPrompt()
    // Plays the tick at the live slider value as it's dragged, so the strength can be felt directly.
    var previewIntensity by remember { mutableFloatStateOf(hapticIntensity) }
    val previewHaptics = remember(context) { Haptics(context) { previewIntensity } }
    // Follow the saved value (initial load, or a change from elsewhere) when not mid-drag.
    LaunchedEffect(hapticIntensity) { previewIntensity = hapticIntensity }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SearchConfig.SCRIM_ALPHA))
            // Being a pointer-input target stops taps reaching the home screen underneath.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = SearchConfig.HorizontalPadding)
                .padding(bottom = SearchConfig.BottomSpacing),
        ) {
            Spacer(Modifier.height(SearchConfig.TopSpacing))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(RoundedCornerShape(percent = 50), tint = SearchConfig.FIELD_GLASS_TINT)
                    .padding(start = PinPickerConfig.HeaderStartPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_title),
                    color = ThemeConfig.Foreground,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TextButton(onClick = onDone) {
                    Text(stringResource(R.string.done), color = ThemeConfig.Foreground)
                }
            }
            Spacer(Modifier.height(SearchConfig.ResultsSpacing))
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SettingsPanelConfig.CardSpacing),
            ) {
                // At the very top, always usable so it can be turned back off: the master lock.
                SettingToggle(
                    title = stringResource(R.string.setting_lock_title),
                    description = stringResource(R.string.setting_lock_description),
                    checked = settingsLocked,
                    onCheckedChange = onSettingsLockedChange,
                )
                // Every other setting fades and stops responding while the lock is on.
                val contentAlpha by animateFloatAsState(
                    if (settingsLocked) SettingsPanelConfig.LOCKED_CONTENT_ALPHA else 1f,
                    label = "lockedSettingsAlpha",
                )
                val enabled = !settingsLocked
                Column(
                    modifier = Modifier.graphicsLayer { alpha = contentAlpha },
                    verticalArrangement = Arrangement.spacedBy(SettingsPanelConfig.CardSpacing),
                ) {
                    SettingToggle(
                        title = stringResource(R.string.setting_double_tap_lock_title),
                        description = stringResource(R.string.setting_double_tap_lock_description),
                        checked = doubleTapToLock,
                        enabled = enabled,
                        onCheckedChange = { enabledValue ->
                            onDoubleTapToLockChange(enabledValue)
                            if (enabledValue) {
                                // Offer to activate the device admin if it isn't already on.
                                if (!DeviceLock.isEnabled(context)) promptEnableLock()
                            } else {
                                // Turning the feature off also releases device admin, so the app can be
                                // uninstalled without first deactivating it in Settings.
                                DeviceLock.disable(context)
                            }
                        },
                    )
                    SettingChoice(
                        title = stringResource(R.string.setting_default_search_category_title),
                        description = stringResource(R.string.setting_default_search_category_description),
                        // null ("All") first, then the categories in their declared order.
                        options = remember { listOf<SearchCategory?>(null) + SearchCategory.entries },
                        selected = defaultSearchCategory,
                        enabled = enabled,
                        optionLabel = { category ->
                            if (category == null) stringResource(R.string.category_all)
                            else stringResource(category.labelRes)
                        },
                        onSelected = onDefaultSearchCategoryChange,
                    )
                    SettingToggle(
                        title = stringResource(R.string.setting_reduce_index_title),
                        description = stringResource(R.string.setting_reduce_index_description),
                        checked = reduceIndexSize,
                        enabled = enabled,
                        onCheckedChange = onReduceIndexSizeChange,
                    )
                    SettingSlider(
                        title = stringResource(R.string.setting_haptic_intensity_title),
                        description = stringResource(R.string.setting_haptic_intensity_description),
                        valueLabel = if (previewIntensity <= 0f) {
                            stringResource(R.string.setting_haptic_intensity_off)
                        } else {
                            stringResource(R.string.setting_percent, (previewIntensity * 100).roundToInt())
                        },
                        value = previewIntensity,
                        enabled = enabled,
                        onValueChange = { value ->
                            if (value != previewIntensity) {
                                previewIntensity = value
                                onHapticIntensityChange(value)
                                // A tick at the new strength, so the slider is felt as well as seen.
                                if (value > 0f) previewHaptics.tick()
                            }
                        },
                    )
                }
                // Always available, even while locked: re-granting permissions and opening the guide
                // don't change any of the launcher's own settings.
                SettingAction(
                    title = stringResource(R.string.setting_setup_title),
                    description = stringResource(R.string.setting_setup_description),
                    onClick = onOpenSetup,
                    warning = when {
                        !contactsAccess.isGranted && !filesAccess.isGranted -> stringResource(R.string.setup_warning_both)
                        !contactsAccess.isGranted -> stringResource(R.string.setup_warning_contacts)
                        !filesAccess.isGranted -> stringResource(R.string.setup_warning_files)
                        else -> null
                    },
                )
                SettingAction(
                    title = stringResource(R.string.setting_how_to_title),
                    description = stringResource(R.string.setting_how_to_description),
                    onClick = onHowToUse,
                )
            }
        }
    }
}

/**
 * A glass card that just does something when tapped (no state), e.g. opening the how-to guide. A
 * [warning] (e.g. a permission that's off) shows in a small box under the description.
 */
@Composable
private fun SettingAction(
    title: String,
    description: String,
    onClick: () -> Unit,
    warning: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(SettingsPanelConfig.CardCornerRadius), tint = SettingsPanelConfig.GlassTint)
            .clickable(onClickLabel = title, onClick = onClick)
            .padding(SettingsPanelConfig.CardPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsPanelConfig.TitleDescriptionGap),
    ) {
        Text(
            text = title,
            color = ThemeConfig.Foreground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = description,
            color = ThemeConfig.Foreground.copy(alpha = SettingsPanelConfig.DESCRIPTION_ALPHA),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (warning != null) {
            InlineWarning(text = warning, modifier = Modifier.padding(top = SettingsPanelConfig.TitleDescriptionGap))
        }
    }
}

/** One glass setting: a title, an explanation, and a switch. The whole card toggles it. */
@Composable
private fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(SettingsPanelConfig.CardCornerRadius), tint = SettingsPanelConfig.GlassTint)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(SettingsPanelConfig.CardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SettingsPanelConfig.CardPadding),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SettingsPanelConfig.TitleDescriptionGap),
        ) {
            Text(
                text = title,
                color = ThemeConfig.Foreground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                color = ThemeConfig.Foreground.copy(alpha = SettingsPanelConfig.DESCRIPTION_ALPHA),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        // The whole row toggles; the switch only shows the state.
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = ThemeConfig.Foreground,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = ThemeConfig.Foreground.copy(alpha = SettingsPanelConfig.SWITCH_OFF_ALPHA),
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = ThemeConfig.Foreground.copy(alpha = SettingsPanelConfig.SWITCH_OFF_ALPHA),
            ),
        )
    }
}

/**
 * One glass setting offering a single choice among [options]: a title, an explanation, and a row of
 * pill chips (one per option) with the selected one filled. [optionLabel] names each option.
 */
@Composable
private fun <T> SettingChoice(
    title: String,
    description: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    enabled: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(SettingsPanelConfig.CardCornerRadius), tint = SettingsPanelConfig.GlassTint)
            .padding(SettingsPanelConfig.CardPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsPanelConfig.TitleDescriptionGap),
    ) {
        Text(
            text = title,
            color = ThemeConfig.Foreground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = description,
            color = ThemeConfig.Foreground.copy(alpha = SettingsPanelConfig.DESCRIPTION_ALPHA),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(SettingsPanelConfig.ChoiceChipSpacing))
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(SettingsPanelConfig.ChoiceChipSpacing),
        ) {
            options.forEach { option ->
                ChoiceChip(
                    label = optionLabel(option),
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A pill in a [SettingChoice] row: filled when selected, outlined when not. */
@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(percent = 50)
    val outline = ThemeConfig.Foreground.copy(alpha = SettingsPanelConfig.CHOICE_UNSELECTED_ALPHA)
    Box(
        modifier = modifier
            .background(if (selected) ThemeConfig.Foreground else Color.Transparent, shape)
            .then(if (selected) Modifier else Modifier.border(1.dp, outline, shape))
            .selectable(
                selected = selected,
                enabled = enabled,
                // The fill/outline is the feedback; a ripple on top would fight the glass look.
                interactionSource = null,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = SettingsPanelConfig.ChoiceChipVerticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else outline,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * One glass setting with a slider: a title (with the current value beside it), an explanation, and a
 * slider that snaps to [SettingsPanelConfig.HAPTIC_INTENSITY_STEPS] stops between 0f and 1f.
 */
@Composable
private fun SettingSlider(
    title: String,
    description: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(SettingsPanelConfig.CardCornerRadius), tint = SettingsPanelConfig.GlassTint)
            .padding(SettingsPanelConfig.CardPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsPanelConfig.TitleDescriptionGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SettingsPanelConfig.CardPadding),
        ) {
            Text(
                text = title,
                color = ThemeConfig.Foreground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                color = ThemeConfig.Foreground,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            text = description,
            color = ThemeConfig.Foreground.copy(alpha = SettingsPanelConfig.DESCRIPTION_ALPHA),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            valueRange = 0f..1f,
            steps = SettingsPanelConfig.HAPTIC_INTENSITY_STEPS,
            colors = SliderDefaults.colors(
                thumbColor = ThemeConfig.Foreground,
                activeTrackColor = ThemeConfig.Foreground,
                inactiveTrackColor = ThemeConfig.Foreground.copy(alpha = SettingsPanelConfig.SLIDER_INACTIVE_TRACK_ALPHA),
                // The value already reads out; hide the step ticks to keep the track clean.
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            // The value label beside the title carries the number; name the control for screen readers.
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}
