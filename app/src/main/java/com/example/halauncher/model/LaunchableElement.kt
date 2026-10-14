package com.example.halauncher.model

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable

/**
 * A piece of home-screen UI that opens something when tapped.
 *
 * @property actionLabel describes what a tap does (e.g. "Open alarms"). Accessibility
 * services announce it as the click action, and it names the target in the error toast.
 * @property fallbackIntents tried in order when nothing can handle [launchIntent].
 * @property launchDirectly tried before [launchIntent]; returns whether it opened anything (see
 * [SearchEntry.launchDirectly]).
 */
data class LaunchableElement(
    val displayContent: @Composable () -> Unit,
    val launchIntent: Intent,
    val actionLabel: String,
    val fallbackIntents: List<Intent> = emptyList(),
    val launchDirectly: (Context) -> Boolean = { false },
)
