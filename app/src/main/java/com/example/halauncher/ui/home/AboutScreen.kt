package com.example.halauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.example.halauncher.BuildConfig
import com.example.halauncher.R
import com.example.halauncher.config.SearchConfig
import com.example.halauncher.config.SettingsPanelConfig
import com.example.halauncher.config.ThemeConfig
import com.example.halauncher.ui.components.liquidGlass

/**
 * About HALauncher, opened from settings: the installed version, who made it, and what's new in this
 * release compared with the last. Laid out like setup: a scrim, a glass header, glass cards, and a
 * Done button at the end that goes back to settings.
 *
 * The release notes are R.array.release_notes, written for the version in R.string.release_notes_version.
 * If that isn't the version installed (the notes weren't updated for a release), they're left out
 * rather than show another release's notes as this one's.
 *
 * @param onDone closes About, back to settings.
 */
@Composable
fun AboutScreen(onDone: () -> Unit) {
    val version = BuildConfig.VERSION_NAME
    val notes = stringArrayResource(R.array.release_notes)
        .takeIf { stringResource(R.string.release_notes_version) == version }
        .orEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SearchConfig.SCRIM_ALPHA))
            // Being a pointer-input target stops taps reaching what's underneath.
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
            GlassPageHeader(title = stringResource(R.string.about_title))
            Spacer(Modifier.height(SearchConfig.ResultsSpacing))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SettingsPanelConfig.CardSpacing),
            ) {
                AboutCard {
                    Text(
                        text = stringResource(R.string.app_name),
                        color = ThemeConfig.Foreground,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.about_version, version),
                        color = ThemeConfig.Foreground,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.about_created_by),
                        color = ThemeConfig.Foreground.copy(alpha = SettingsPanelConfig.DESCRIPTION_ALPHA),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (notes.isNotEmpty()) {
                    AboutCard {
                        Text(
                            text = stringResource(R.string.about_whats_new, version),
                            color = ThemeConfig.Foreground,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { heading() },
                        )
                        for (note in notes) ReleaseNote(note)
                    }
                }
                GlassPageButton(label = stringResource(R.string.done), onClick = onDone)
            }
        }
    }
}

/** A glass card like the settings', holding [content] in a column. */
@Composable
private fun AboutCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(SettingsPanelConfig.CardCornerRadius), tint = SettingsPanelConfig.GlassTint)
            .padding(SettingsPanelConfig.CardPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsPanelConfig.TitleDescriptionGap),
    ) {
        content()
    }
}

/** One change in the release notes, as a bulleted line. */
@Composable
private fun ReleaseNote(text: String) {
    val color = ThemeConfig.Foreground.copy(alpha = SettingsPanelConfig.DESCRIPTION_ALPHA)
    Row(modifier = Modifier.padding(top = SettingsPanelConfig.TitleDescriptionGap)) {
        Text(
            text = "•",
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = SettingsPanelConfig.ChoiceChipSpacing),
        )
        Text(text = text, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}
