package com.example.halauncher.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.halauncher.R
import com.example.halauncher.config.SearchConfig
import com.example.halauncher.config.SettingsPanelConfig
import com.example.halauncher.config.ThemeConfig
import com.example.halauncher.ui.components.liquidGlass

/**
 * A small glass box saying a permission is missing, with a link that fixes it: e.g. on search's
 * Contacts tab while contacts can't be read. The whole box is the link, so it's an easy target;
 * with [onDismiss] it also gets a close button.
 *
 * @param icon what the permission unlocks (the contacts or files category icon).
 * @param message why this view is empty or incomplete.
 * @param actionLabel the link's text, also read out as the box's click action.
 * @param onAction asks for the permission (or opens the page that does).
 */
@Composable
fun PermissionNotice(
    icon: ImageVector,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(SettingsPanelConfig.CardCornerRadius), tint = SearchConfig.PROMPT_GLASS_TINT)
            .clickable(onClickLabel = actionLabel, onClick = onAction)
            .padding(start = SettingsPanelConfig.CardPadding)
            .padding(vertical = NoticeVerticalPadding)
            // Without a close button the text runs to the card's edge, so it needs the same inset there.
            .padding(end = if (onDismiss == null) SettingsPanelConfig.CardPadding else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SearchConfig.ResultIconTextGap),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ThemeConfig.Foreground,
            modifier = Modifier.size(NoticeIconSize),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                // Read as one sentence; the click label already announces the link.
                .clearAndSetSemantics { contentDescription = message },
            verticalArrangement = Arrangement.spacedBy(SettingsPanelConfig.TitleDescriptionGap),
        ) {
            Text(
                text = message,
                color = ThemeConfig.Foreground,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = actionLabel,
                color = ThemeConfig.Foreground,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
            )
        }
        if (onDismiss != null) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.dismiss),
                    tint = ThemeConfig.Foreground.copy(alpha = SearchConfig.CATEGORY_BADGE_ALPHA),
                    modifier = Modifier.size(SearchConfig.CategoryBadgeSize),
                )
            }
        }
    }
}

/**
 * A one-line warning inside a glass card, e.g. "Permission denied…" on a setup item or "Contacts
 * access is off" on the settings panel's set-up row: a warning glyph and the [text], on a slightly
 * darker inner glass pill so it reads as a box within the card.
 */
@Composable
fun InlineWarning(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .liquidGlass(RoundedCornerShape(WarningCornerRadius), tint = WARNING_GLASS_TINT)
            .padding(horizontal = WarningHorizontalPadding, vertical = WarningVerticalPadding)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WarningIconGap),
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = ThemeConfig.Foreground,
            modifier = Modifier.size(WarningIconSize),
        )
        Text(
            text = text,
            color = ThemeConfig.Foreground,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Size of the category glyph at the start of a [PermissionNotice]. */
private val NoticeIconSize = 22.dp

/** Vertical inner padding of a [PermissionNotice]; the close button adds its own touch area. */
private val NoticeVerticalPadding = 12.dp

/** Shape, spacing and darkness of an [InlineWarning]'s inner glass. */
private val WarningCornerRadius = 12.dp
private val WarningHorizontalPadding = 10.dp
private val WarningVerticalPadding = 6.dp
private val WarningIconGap = 8.dp
private val WarningIconSize = 16.dp
private const val WARNING_GLASS_TINT = 0.3f
