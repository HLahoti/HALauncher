package com.example.halauncher.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.halauncher.R
import com.example.halauncher.config.PinPickerConfig
import com.example.halauncher.config.SearchConfig
import com.example.halauncher.config.SettingsPanelConfig
import com.example.halauncher.config.ThemeConfig
import com.example.halauncher.ui.components.liquidGlass

/**
 * The first-run setup: everything the launcher needs to work well, in one place. It comes up by
 * itself the very first time the app opens, and can be reopened from settings ("Set-up &
 * permissions") to grant anything that was skipped or later turned off elsewhere.
 *
 * Same glass look as the settings panel and the how-to guide: a scrim over the (blurred) home
 * screen, a titled header, one card per item, and a Done button at the end. Each card shows whether
 * that item is already set up (a check and "Enabled") or offers a button to set it up now; because
 * the status is re-read every time the launcher resumes, an item granted in a system screen flips to
 * "Enabled" as soon as the user comes back.
 *
 * @param isDefaultHome whether HALauncher is already the device's default home app.
 * @param hasContactsAccess whether the contacts permission is granted.
 * @param hasFilesAccess whether file access is granted.
 * @param onSetDefaultHome opens the system prompt to make HALauncher the default home app.
 * @param onGrantContacts asks for the contacts permission.
 * @param onGrantFiles asks for file access.
 * @param onDone closes setup and returns to the home screen.
 */
@Composable
fun SetupScreen(
    isDefaultHome: Boolean,
    hasContactsAccess: Boolean,
    hasFilesAccess: Boolean,
    onSetDefaultHome: () -> Unit,
    onGrantContacts: () -> Unit,
    onGrantFiles: () -> Unit,
    onDone: () -> Unit,
) {
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
                    .padding(start = PinPickerConfig.HeaderStartPadding, top = SettingsPanelConfig.CardPadding, bottom = SettingsPanelConfig.CardPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.setup_title),
                    color = ThemeConfig.Foreground,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            Spacer(Modifier.height(SearchConfig.ResultsSpacing))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SettingsPanelConfig.CardSpacing),
            ) {
                Text(
                    text = stringResource(R.string.setup_intro),
                    color = ThemeConfig.Foreground.copy(alpha = SettingsPanelConfig.DESCRIPTION_ALPHA),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = SettingsPanelConfig.CardPadding),
                )
                SetupItem(
                    title = stringResource(R.string.setup_default_home_title),
                    description = stringResource(R.string.setup_default_home_desc),
                    granted = isDefaultHome,
                    actionLabel = stringResource(R.string.setup_action_set_default),
                    onAction = onSetDefaultHome,
                )
                SetupItem(
                    title = stringResource(R.string.setup_contacts_title),
                    description = stringResource(R.string.setup_contacts_desc),
                    granted = hasContactsAccess,
                    actionLabel = stringResource(R.string.setup_action_allow),
                    onAction = onGrantContacts,
                )
                SetupItem(
                    title = stringResource(R.string.setup_files_title),
                    description = stringResource(R.string.setup_files_desc),
                    granted = hasFilesAccess,
                    actionLabel = stringResource(R.string.setup_action_allow),
                    onAction = onGrantFiles,
                )
                // The Done button sits at the end of the page; setup can be finished at any point,
                // whether or not everything was granted.
                DoneButton(onDone)
            }
        }
    }
}

/**
 * One setup item: a title, an explanation, and — depending on whether it's already set up — either a
 * check with "Enabled", or a glass button that starts the request. When granted the whole card is
 * inert; otherwise the whole card is the button, so it's an easy target.
 */
@Composable
private fun SetupItem(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val enabledLabel = stringResource(R.string.setup_enabled)
    val cardModifier = Modifier
        .fillMaxWidth()
        .liquidGlass(RoundedCornerShape(SettingsPanelConfig.CardCornerRadius), tint = SettingsPanelConfig.GlassTint)
        .then(if (granted) Modifier else Modifier.clickable(onClickLabel = actionLabel, onClick = onAction))
        .padding(SettingsPanelConfig.CardPadding)
    Row(
        modifier = cardModifier.semantics {
            // Read the whole card as one item: its title, and whether it's done or still tappable.
            contentDescription = title
            stateDescription = if (granted) enabledLabel else actionLabel
            if (!granted) role = Role.Button
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SettingsPanelConfig.CardPadding),
    ) {
        Column(
            modifier = Modifier.weight(1f).clearAndSetSemantics { },
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
        if (granted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SettingsPanelConfig.TitleDescriptionGap),
                modifier = Modifier.clearAndSetSemantics { },
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = ThemeConfig.Foreground,
                    modifier = Modifier.size(SetupCheckSize),
                )
                Text(
                    text = enabledLabel,
                    color = ThemeConfig.Foreground,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        } else {
            // A pill that reads as a button; the whole card carries the click, so this is decorative.
            Box(
                modifier = Modifier
                    .background(ThemeConfig.Foreground, RoundedCornerShape(percent = 50))
                    .padding(horizontal = SetupButtonHorizontalPadding, vertical = SetupButtonVerticalPadding)
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = actionLabel,
                    color = Color.Black,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** A full-width glass button ending setup; tapping it returns to the home screen. */
@Composable
private fun DoneButton(onDone: () -> Unit) {
    val label = stringResource(R.string.done)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(percent = 50), tint = SettingsPanelConfig.GlassTint)
            .clickable(onClickLabel = label, onClick = onDone)
            .padding(SettingsPanelConfig.CardPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = ThemeConfig.Foreground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Size of the "Enabled" check icon. */
private val SetupCheckSize = 20.dp

/** Inner padding of the action pill on an item that isn't set up yet. */
private val SetupButtonHorizontalPadding = 16.dp
private val SetupButtonVerticalPadding = 8.dp
