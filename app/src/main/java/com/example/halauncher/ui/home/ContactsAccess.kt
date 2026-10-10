package com.example.halauncher.ui.home

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.example.halauncher.R
import com.example.halauncher.ui.components.launchSafely

/**
 * Returns an action that asks for the contacts permission. Normally that's the system dialog, and
 * [onResult] runs when it closes. Once the user has turned it down for good (Android stops showing
 * the dialog after a second "Don't allow"), launching it would silently do nothing, so instead this
 * opens HALauncher's page in system Settings, where it can still be switched on; the result then
 * arrives as the launcher resuming.
 *
 * @param requestedBefore whether the dialog has been shown before; until it has, Android reports
 *   no rationale either, which mustn't be mistaken for a permanent denial.
 */
@Composable
fun rememberContactsAccessRequest(requestedBefore: () -> Boolean, onResult: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onResult()
    }
    return {
        if (isPermanentlyDenied(activity, Manifest.permission.READ_CONTACTS, requestedBefore())) {
            context.openAppSettings()
        } else {
            permission.launch(Manifest.permission.READ_CONTACTS)
        }
    }
}

/**
 * Whether Android would no longer show [permission]'s dialog: it was asked for before, and the
 * system no longer suggests explaining it (which it stops doing once the user declines for good).
 */
internal fun isPermanentlyDenied(activity: Activity?, permission: String, requestedBefore: Boolean): Boolean =
    requestedBefore && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)

/** Opens HALauncher's own page in system Settings, where its permissions can be switched on. */
internal fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    launchSafely(intent, getString(R.string.app_settings))
}
