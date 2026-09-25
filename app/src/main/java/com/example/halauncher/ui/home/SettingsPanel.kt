package com.example.halauncher.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.example.halauncher.R
import com.example.halauncher.config.PinPickerConfig
import com.example.halauncher.config.SearchConfig
import com.example.halauncher.config.SettingsPanelConfig
import com.example.halauncher.config.ThemeConfig
import com.example.halauncher.service.LockScreenService
import com.example.halauncher.ui.components.liquidGlass

/**
 * The launcher's settings, opened by long-pressing the empty home screen. Two glass toggles, each
 * with a short explanation of what it does and any permission it needs. Laid out like the pinned-app
 * picker: a full scrim over the (blurred) home screen, a glass header with a Done button, then the
 * toggles.
 *
 * @param onDoubleTapToLockChange saves the preference; the caller also gates the double-tap gesture on it.
 * @param onReduceIndexSizeChange saves the preference and re-indexes files so the change takes effect.
 */
@Composable
fun SettingsPanel(
    doubleTapToLock: Boolean,
    reduceIndexSize: Boolean,
    onDoubleTapToLockChange: (Boolean) -> Unit,
    onReduceIndexSizeChange: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    // Turning the lock toggle on is useless until the accessibility service is enabled, so offer to.
    val promptEnableLock = rememberEnableLockPrompt()

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
                SettingToggle(
                    title = stringResource(R.string.setting_double_tap_lock_title),
                    description = stringResource(R.string.setting_double_tap_lock_description),
                    checked = doubleTapToLock,
                    onCheckedChange = { enabled ->
                        onDoubleTapToLockChange(enabled)
                        if (enabled && LockScreenService.isSupported && !LockScreenService.isEnabled(context)) {
                            promptEnableLock()
                        }
                    },
                )
                SettingToggle(
                    title = stringResource(R.string.setting_reduce_index_title),
                    description = stringResource(R.string.setting_reduce_index_description),
                    checked = reduceIndexSize,
                    onCheckedChange = onReduceIndexSizeChange,
                )
            }
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(SettingsPanelConfig.CardCornerRadius), tint = SettingsPanelConfig.GlassTint)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
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
