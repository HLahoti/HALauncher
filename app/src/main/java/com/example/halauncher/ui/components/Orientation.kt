package com.example.halauncher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import com.example.halauncher.config.SearchConfig

/**
 * Whether the launcher's window is wider than it is tall: the phone turned on its side (or a wide
 * split-screen pane). Layouts use it to spread out sideways where they'd otherwise run out of height.
 */
@Composable
@ReadOnlyComposable
fun isLandscape(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.screenWidthDp > configuration.screenHeightDp
}

/**
 * The gap above an overlay's header (search, settings, the pin picker, setup, About): smaller in
 * landscape, where height is short.
 */
@Composable
@ReadOnlyComposable
fun overlayTopSpacing(): Dp = if (isLandscape()) SearchConfig.TopSpacingLandscape else SearchConfig.TopSpacing
