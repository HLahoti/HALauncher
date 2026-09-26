package com.example.halauncher.ui.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.halauncher.R
import com.example.halauncher.service.DeviceLock

/**
 * Returns an action that locks the screen. It works through [DeviceLock], the device admin that does
 * the actual locking:
 *
 *  - Already activated → lock straight away.
 *  - Not activated yet → explain why it's needed and offer to open the system's device-admin dialog
 *    (there is no runtime permission dialog for this; the user activates it themselves).
 *
 * The rationale dialog is rendered by this composable when needed, so callers only invoke the
 * returned lambda.
 */
@Composable
fun rememberDoubleTapLock(): () -> Unit {
    val context = LocalContext.current
    val promptEnable = rememberEnableLockPrompt()

    return remember(context, promptEnable) {
        {
            when {
                !DeviceLock.isEnabled(context) ->
                    promptEnable()
                // Activated, but the platform refused the lock — rare; surface it rather than fail silently.
                !DeviceLock.lockScreen(context) ->
                    context.toast(R.string.lock_failed)
            }
        }
    }
}

/**
 * Returns an action that shows the "let HALauncher lock the screen" dialog and, if confirmed, opens
 * the system's device-admin activation dialog. Used both when a double-tap lock is attempted before
 * the admin is active, and when the settings toggle for it is switched on. The dialog is rendered here.
 */
@Composable
fun rememberEnableLockPrompt(): () -> Unit {
    val context = LocalContext.current
    var showEnablePrompt by remember { mutableStateOf(false) }

    if (showEnablePrompt) {
        LockAdminDialog(
            onConfirm = {
                showEnablePrompt = false
                context.openAddDeviceAdmin()
            },
            onDismiss = { showEnablePrompt = false },
        )
    }

    return remember(context) { { showEnablePrompt = true } }
}

@Composable
private fun LockAdminDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lock_permission_title)) },
        text = { Text(stringResource(R.string.lock_permission_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.lock_open_settings)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
        },
    )
}

/**
 * Opens the system's device-admin activation dialog, where the user turns lock-screen access on.
 *
 * It's launched from the host Activity, in that Activity's task, without FLAG_ACTIVITY_NEW_TASK:
 * Samsung's device-admin screen refuses to run as a new task (it finishes itself immediately) since
 * it returns its result to the caller's task. The NEW_TASK path is only a fallback for the unlikely
 * case that no Activity is found in the context chain.
 */
private fun Context.openAddDeviceAdmin() {
    val intent = DeviceLock.addAdminIntent(this)
    try {
        val activity = findActivity()
        if (activity != null) {
            activity.startActivity(intent)
        } else {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    } catch (e: ActivityNotFoundException) {
        toast(R.string.lock_settings_unavailable)
    }
}

/** Walks the context-wrapper chain to the hosting Activity, or null if there isn't one. */
private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private fun Context.toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
