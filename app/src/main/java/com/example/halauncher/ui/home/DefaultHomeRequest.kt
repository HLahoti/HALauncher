package com.example.halauncher.ui.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.halauncher.R
import com.example.halauncher.service.DefaultHome

/**
 * Returns an action that asks the user to make HALauncher the default home app. On Android 10+ that's
 * the system's home-role dialog; before that (and as a fallback) it's the "Default home app" settings
 * screen. The result arrives as the launcher resuming, so [onResult] runs to re-check whether it's now
 * the default. If neither screen can be opened, it shows a short message rather than failing silently.
 */
@Composable
fun rememberDefaultHomeRequest(onResult: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onResult()
    }
    return {
        try {
            request.launch(DefaultHome.requestIntent(context))
        } catch (e: ActivityNotFoundException) {
            // The role intent wasn't handled; fall back to the general home-settings screen.
            try {
                request.launch(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(context, R.string.setup_home_settings_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
