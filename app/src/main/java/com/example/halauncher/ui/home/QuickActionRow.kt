package com.example.halauncher.ui.home

import android.content.res.Resources
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import com.example.halauncher.R
import com.example.halauncher.config.SearchConfig
import com.example.halauncher.config.ThemeConfig
import com.example.halauncher.ui.components.AlarmIcon
import com.example.halauncher.ui.components.EventIcon
import com.example.halauncher.ui.components.LinkIcon
import com.example.halauncher.ui.components.MessageIcon
import com.example.halauncher.ui.components.TimerIcon
import com.example.halauncher.util.QuickAction
import com.example.halauncher.util.QuickActionTarget
import com.example.halauncher.util.QuickCommand
import java.util.Calendar

/**
 * A row for a quick action ("Start a 5 min timer"), above the search results like the calculator's
 * answer. Under its title it names the app that will do it, so it's clear where a tap goes; with
 * no default app chosen, it says Android will ask. Tapping it runs it through [onRun].
 */
@Composable
fun QuickActionRow(
    target: QuickActionTarget,
    onRun: (QuickActionTarget) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val title = quickActionTitle(LocalResources.current, target.action, DateFormat.is24HourFormat(LocalContext.current))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClickLabel = title) { onRun(target) }
            .padding(SearchConfig.ResultRowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SearchConfig.ResultIconTextGap),
    ) {
        Box(modifier = Modifier.size(SearchConfig.ResultIconSize), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = target.action.icon,
                contentDescription = null,
                tint = ThemeConfig.Foreground,
                modifier = Modifier.size(SearchConfig.QuickActionIconSize),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SearchConfig.ResultDetailGap),
        ) {
            Text(
                text = title,
                color = ThemeConfig.Foreground,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = target.appName ?: stringResource(R.string.quick_action_choose_app),
                color = ThemeConfig.Foreground.copy(alpha = SearchConfig.RESULT_DETAIL_ALPHA),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A dimmed example of [command] ("timer 10m · Start a timer"), shown while its word is being typed so
 * the command can be learned without reading about it. Tapping it types the word for the user,
 * through [onType], ready for the rest.
 */
@Composable
fun CommandHintRow(
    command: QuickCommand,
    onType: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val label = stringResource(R.string.hint_type_command, command.word)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClickLabel = label) { onType(command.word + " ") }
            .padding(SearchConfig.ResultRowPadding)
            .alpha(SearchConfig.COMMAND_HINT_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SearchConfig.ResultIconTextGap),
    ) {
        Box(modifier = Modifier.size(SearchConfig.ResultIconSize), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = command.icon,
                contentDescription = null,
                tint = ThemeConfig.Foreground,
                modifier = Modifier.size(SearchConfig.QuickActionIconSize),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SearchConfig.ResultDetailGap),
        ) {
            Text(
                text = command.example,
                color = ThemeConfig.Foreground,
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(command.descriptionRes),
                color = ThemeConfig.Foreground.copy(alpha = SearchConfig.RESULT_DETAIL_ALPHA),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** What [this] does, in a few words ("Start a timer"). */
@get:StringRes
internal val QuickCommand.descriptionRes: Int
    get() = when (this) {
        QuickCommand.TIMER -> R.string.hint_timer
        QuickCommand.ALARM -> R.string.hint_alarm
        QuickCommand.CALL -> R.string.hint_call
        QuickCommand.TEXT -> R.string.hint_text
        QuickCommand.EMAIL -> R.string.hint_email
        QuickCommand.MAPS -> R.string.hint_maps
        QuickCommand.EVENT -> R.string.hint_event
    }

/** The icon of the action [this] command carries out. */
internal val QuickCommand.icon: ImageVector
    get() = when (this) {
        QuickCommand.TIMER -> TimerIcon
        QuickCommand.ALARM -> AlarmIcon
        QuickCommand.CALL -> Icons.Default.Call
        QuickCommand.TEXT -> MessageIcon
        QuickCommand.EMAIL -> Icons.Default.Email
        QuickCommand.MAPS -> Icons.Default.Place
        QuickCommand.EVENT -> EventIcon
    }

private val QuickAction.icon: ImageVector
    get() = when (this) {
        is QuickAction.Timer -> TimerIcon
        is QuickAction.Alarm -> AlarmIcon
        is QuickAction.Call -> Icons.Default.Call
        is QuickAction.Message -> MessageIcon
        is QuickAction.Email -> Icons.Default.Email
        is QuickAction.Navigate -> Icons.Default.Place
        is QuickAction.OpenLink -> LinkIcon
        is QuickAction.NewEvent -> EventIcon
    }

/** What [action] does, in a few words: "Start a 1 h 30 min timer", "Call 98765 43210". */
internal fun quickActionTitle(resources: Resources, action: QuickAction, is24Hour: Boolean): String = when (action) {
    is QuickAction.Timer -> resources.getString(R.string.quick_timer, formatDuration(resources, action.seconds))
    is QuickAction.Alarm -> resources.getString(R.string.quick_alarm, formatClockTime(action.hour, action.minute, is24Hour))
    is QuickAction.Call -> resources.getString(R.string.quick_call, action.number)
    is QuickAction.Message -> resources.getString(R.string.quick_message, action.number)
    is QuickAction.Email -> resources.getString(R.string.quick_email, action.address)
    is QuickAction.Navigate -> resources.getString(R.string.quick_navigate, action.place)
    is QuickAction.OpenLink -> resources.getString(R.string.quick_open_link, action.url.substringAfter("://"))
    is QuickAction.NewEvent -> resources.getString(R.string.quick_new_event, action.title)
}

/** "1 h 30 min", "45 s": each non-zero part, largest first. */
private fun formatDuration(resources: Resources, totalSeconds: Int): String = listOfNotNull(
    (totalSeconds / 3600).takeIf { it > 0 }?.let { resources.getString(R.string.duration_hours, it) },
    (totalSeconds % 3600 / 60).takeIf { it > 0 }?.let { resources.getString(R.string.duration_minutes, it) },
    (totalSeconds % 60).takeIf { it > 0 }?.let { resources.getString(R.string.duration_seconds, it) },
).joinToString(" ")

/** [hour]:[minute] in the device's 12- or 24-hour style and language ("7:30 AM", "07:30"). */
private fun formatClockTime(hour: Int, minute: Int, is24Hour: Boolean): String {
    val time = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    val skeleton = if (is24Hour) "Hm" else "hm"
    val pattern = DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), skeleton)
    return DateFormat.format(pattern, time).toString()
}
