package com.example.halauncher.ui.home

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Returns an action that asks for file access. On Android 11+ that's "All files access", a
 * Settings screen the user toggles (the result arrives as the launcher resuming); before that
 * it's the ordinary storage permission dialog, and [onResult] runs when it closes — unless that
 * dialog was turned down for good, in which case HALauncher's page in system Settings opens instead.
 *
 * @param requestedBefore whether file access has been asked for before (see [isPermanentlyDenied]).
 * @param onRequested runs each time access is asked for, so it can be remembered.
 */
@Composable
fun rememberFilesAccessRequest(
    requestedBefore: () -> Boolean,
    onRequested: () -> Unit,
    onResult: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onResult()
    }
    return {
        val blocked = isPermanentlyDenied(activity, Manifest.permission.READ_EXTERNAL_STORAGE, requestedBefore())
        onRequested()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val forThisApp = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.fromParts("package", context.packageName, null),
            )
            try {
                context.startActivity(forThisApp)
            } catch (e: ActivityNotFoundException) {
                // Some builds only have the list of all apps.
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else if (blocked) {
            context.openAppSettings()
        } else {
            storagePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}
