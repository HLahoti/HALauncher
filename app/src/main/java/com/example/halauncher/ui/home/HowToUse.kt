package com.example.halauncher.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.halauncher.R
import com.example.halauncher.config.SearchConfig
import com.example.halauncher.config.SettingsPanelConfig
import com.example.halauncher.config.ThemeConfig
import com.example.halauncher.ui.components.liquidGlass

/**
 * A plain, static guide to the launcher's gestures and buttons. It comes up by itself the first
 * time the app opens, and can be reopened from the settings panel. Same glass look as the settings
 * panel: a scrim over the (blurred) home screen, a titled header, the tips in one card, and a Done
 * button at the end that returns to the home screen.
 *
 * On the first run it follows setup as step 2 of 2.
 *
 * @param isOnboarding whether this is the first-run walkthrough, so the header shows the step.
 * @param onDone closes the card and goes back to the bare home screen.
 */
@Composable
fun HowToUse(isOnboarding: Boolean, onDone: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SearchConfig.SCRIM_ALPHA))
            // Being a pointer-input target stops taps reaching the home screen underneath.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = SearchConfig.HorizontalPadding)
                .padding(bottom = SearchConfig.BottomSpacing),
        ) {
            Spacer(Modifier.height(SearchConfig.TopSpacing))
            GlassPageHeader(
                title = stringResource(R.string.how_to_title),
                step = if (isOnboarding) stringResource(R.string.onboarding_step, 2, ONBOARDING_STEPS) else null,
            )
            Spacer(Modifier.height(SearchConfig.ResultsSpacing))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SettingsPanelConfig.CardSpacing),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(
                            RoundedCornerShape(SettingsPanelConfig.CardCornerRadius),
                            tint = SettingsPanelConfig.GlassTint,
                        )
                        .padding(SettingsPanelConfig.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(SettingsPanelConfig.CardPadding),
                ) {
                    TIPS.forEach { (titleRes, descRes) -> Tip(titleRes, descRes) }
                }
                // The Done button sits at the end of the page, as the last thing after the tips.
                GlassPageButton(label = stringResource(R.string.done), onClick = onDone)
            }
        }
    }
}

/** One tip: a bold heading and a sentence explaining the gesture or button. */
@Composable
private fun Tip(@StringRes titleRes: Int, @StringRes descRes: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(SettingsPanelConfig.TitleDescriptionGap)) {
        Text(
            text = stringResource(titleRes),
            color = ThemeConfig.Foreground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(descRes),
            color = ThemeConfig.Foreground.copy(alpha = SettingsPanelConfig.DESCRIPTION_ALPHA),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** The tips, in order, as (title, description) string-resource pairs. */
private val TIPS: List<Pair<Int, Int>> = listOf(
    R.string.how_to_search_title to R.string.how_to_search_desc,
    R.string.how_to_filter_title to R.string.how_to_filter_desc,
    R.string.how_to_sphere_title to R.string.how_to_sphere_desc,
    R.string.how_to_sphere_edit_title to R.string.how_to_sphere_edit_desc,
    R.string.how_to_quick_title to R.string.how_to_quick_desc,
    R.string.how_to_quick_customise_title to R.string.how_to_quick_customise_desc,
    R.string.how_to_clock_title to R.string.how_to_clock_desc,
    R.string.how_to_settings_title to R.string.how_to_settings_desc,
    R.string.how_to_lock_title to R.string.how_to_lock_desc,
)
