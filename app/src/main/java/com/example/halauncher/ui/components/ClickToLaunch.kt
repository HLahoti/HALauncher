package com.example.halauncher.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import com.example.halauncher.R
import com.example.halauncher.model.LaunchableElement
import com.example.halauncher.model.SearchEntry

/** Renders [element] and launches its intent on tap. */
@Composable
fun ClickToLaunch(
    element: LaunchableElement,
    modifier: Modifier = Modifier,
    onLaunched: () -> Unit = {},
) {
    val context = LocalContext.current
    Box(
        modifier = modifier.clickable(onClickLabel = element.actionLabel, role = Role.Button) {
            val intents = listOf(element.launchIntent) + element.fallbackIntents
            if (element.launchDirectly(context) || context.launchSafely(intents, element.actionLabel)) onLaunched()
        },
    ) {
        element.displayContent()
    }
}

/** Opens a search entry: directly if it can be, else its link, else the first fallback that works. */
fun Context.launchSafely(entry: SearchEntry, label: String): Boolean =
    entry.launchDirectly(this) || launchSafely(listOf(entry.link) + entry.fallbackLinks, label)

/**
 * Starts the first of [intents] that some activity can handle; if none can, shows a toast.
 * Never throws: a missing or broken app must not crash the launcher.
 *
 * @return whether an activity was started.
 */
fun Context.launchSafely(intents: List<Intent>, label: String): Boolean {
    val started = intents.any { canHandle(it) && tryStart(it) }
    if (!started) {
        Toast.makeText(this, getString(R.string.launch_failed, label), Toast.LENGTH_SHORT).show()
    }
    return started
}

fun Context.launchSafely(intent: Intent, label: String): Boolean = launchSafely(listOf(intent), label)

/** The intent [launchSafely] would open: the first of [intents] some installed app handles. */
fun Context.firstHandledIntent(intents: List<Intent>): Intent? = intents.firstOrNull { canHandle(it) }

private fun Context.canHandle(intent: Intent): Boolean = intent.resolveActivity(packageManager) != null

private fun Context.tryStart(intent: Intent): Boolean {
    return try {
        startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
