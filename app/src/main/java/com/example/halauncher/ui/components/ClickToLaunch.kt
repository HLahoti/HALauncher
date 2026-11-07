package com.example.halauncher.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import com.example.halauncher.R
import com.example.halauncher.model.LaunchableElement
import com.example.halauncher.model.SearchEntry

/**
 * Renders [element] and launches its intent on tap. With [onLongClick], holding it calls that
 * instead (labelled [onLongClickLabel] for accessibility). While not [enabled], it takes no taps.
 */
@Composable
fun ClickToLaunch(
    element: LaunchableElement,
    modifier: Modifier = Modifier,
    onLaunched: () -> Unit = {},
    enabled: Boolean = true,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val launch = {
        val intents = listOf(element.launchIntent) + element.fallbackIntents
        if (element.launchDirectly(context) || context.launchSafely(intents, element.actionLabel)) onLaunched()
    }
    Box(
        modifier = if (onLongClick == null) {
            modifier.clickable(enabled = enabled, onClickLabel = element.actionLabel, role = Role.Button, onClick = launch)
        } else {
            modifier.combinedClickable(
                enabled = enabled,
                onClickLabel = element.actionLabel,
                role = Role.Button,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                onClick = launch,
                // The caller buzzes itself (through Haptics, at the user's chosen strength); the
                // system's own long-press buzz would cut it short.
                hapticFeedbackEnabled = false,
            )
        },
    ) {
        element.displayContent()
    }
}

/**
 * Makes holding a press do nothing, for something whose long-press has no meaning: `clickable`
 * would otherwise fire on release however long the press was held. Once a press outlasts the
 * long-press timeout, its release is consumed before the clickable after this sees it, which then
 * treats the press as cancelled. Quick taps, and drags (which scrolling takes), are left alone.
 */
fun Modifier.ignoreLongPress(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var ended = false
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            waitForUpOrCancellation(PointerEventPass.Initial)
            ended = true
        }
        if (ended) return@awaitEachGesture
        // Held past the timeout: swallow the release. Moves pass through, so holding still and
        // then dragging still scrolls.
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { if (it.changedToUp()) it.consume() }
        } while (event.changes.any { it.pressed })
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
