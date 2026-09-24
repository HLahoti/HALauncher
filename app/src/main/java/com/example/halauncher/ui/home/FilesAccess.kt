package com.example.halauncher.ui.home

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Returns an action that asks for file access. On Android 11+ that's "All files access", a
 * Settings screen the user toggles (the result arrives as the launcher resuming); before that
 * it's the ordinary storage permission dialog, and [onResult] runs when it closes.
 */
@Composable
fun rememberFilesAccessRequest(onResult: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onResult()
    }
    return {
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
        } else {
            storagePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}
