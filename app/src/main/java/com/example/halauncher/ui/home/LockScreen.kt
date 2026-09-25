package com.example.halauncher.ui.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import com.example.halauncher.service.LockScreenService

/**
 * Returns an action that locks the screen. It works through [LockScreenService], the accessibility
 * service that does the actual locking:
 *
 *  - Already switched on → lock straight away.
 *  - Not switched on yet → explain why it's needed and offer to open Accessibility settings (there
 *    is no runtime permission dialog for accessibility; the user turns the service on themselves).
 *  - Android older than 9, where this kind of locking doesn't exist → a short toast.
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
                !LockScreenService.isSupported ->
                    context.toast(R.string.lock_unsupported)
                !LockScreenService.isEnabled(context) ->
                    promptEnable()
                // Enabled, but not performed: usually the service hasn't finished connecting yet.
                !LockScreenService.lockScreen() ->
                    context.toast(R.string.lock_failed)
            }
        }
    }
}

/**
 * Returns an action that shows the "turn on HALauncher in Accessibility settings" dialog and, if
 * confirmed, opens those settings. Used both when a double-tap lock is attempted before the service
 * is on, and when the settings toggle for it is switched on. The dialog is rendered here.
 */
@Composable
fun rememberEnableLockPrompt(): () -> Unit {
    val context = LocalContext.current
    var showEnablePrompt by remember { mutableStateOf(false) }

    if (showEnablePrompt) {
        LockAccessibilityDialog(
            onConfirm = {
                showEnablePrompt = false
                context.openAccessibilitySettings()
            },
            onDismiss = { showEnablePrompt = false },
        )
    }

    return remember(context) { { showEnablePrompt = true } }
}

@Composable
private fun LockAccessibilityDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
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

/** Opens the system's Accessibility settings, where the user switches the lock service on. */
private fun Context.openAccessibilitySettings() {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        toast(R.string.lock_settings_unavailable)
    }
}

private fun Context.toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
