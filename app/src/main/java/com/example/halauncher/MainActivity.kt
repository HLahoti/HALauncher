package com.example.halauncher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.example.halauncher.ui.home.HomeScreen
import com.example.halauncher.ui.home.HomeViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent bars with light icons, drawn over the wallpaper.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                HomeScreen(viewModel)
            }
        }
        // Started from the settings app shortcut. Not again when recreated (e.g. on rotation).
        if (savedInstanceState == null && intent?.action == ACTION_OPEN_SETTINGS) viewModel.openSettings()
    }

    /** Index updates are only recorded while the launcher is out of sight, and caught up on here. */
    override fun onStart() {
        super.onStart()
        viewModel.onForegroundChanged(started = true)
    }

    override fun onStop() {
        viewModel.onForegroundChanged(started = false)
        super.onStop()
    }

    /**
     * Pressing Home while already home (singleTask) closes any open overlay; the settings app
     * shortcut opens settings in its place.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.resetHome()
        if (intent.action == ACTION_OPEN_SETTINGS) viewModel.openSettings()
    }

    companion object {
        /** The action of the settings app shortcut (res/xml/shortcuts.xml). */
        const val ACTION_OPEN_SETTINGS = "com.example.halauncher.action.OPEN_SETTINGS"
    }
}
