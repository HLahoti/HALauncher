package com.example.halauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.example.halauncher.R
import com.example.halauncher.config.CategoryFilterConfig
import com.example.halauncher.config.SearchConfig
import com.example.halauncher.config.SettingsPanelConfig
import com.example.halauncher.config.ThemeConfig
import com.example.halauncher.ui.components.isLandscape
import com.example.halauncher.ui.components.liquidGlass
import com.example.halauncher.ui.components.overlayTopSpacing
import com.example.halauncher.util.QuickCommand

/**
 * Search tips, opened from settings: everything search can find and do, each with examples. Tapping
 * an example tries it: search opens with it typed ([onTry]), its result right there, nothing run
 * until the user taps it. Laid out like About: a scrim, a glass header, glass cards, and Done.
 *
 * @param commands the commands to list, in order: those some installed app can carry out.
 * @param onTry opens search with an example typed.
 * @param onDone closes the page, back to settings.
 */
@Composable
fun SearchTipsScreen(commands: List<QuickCommand>, onTry: (String) -> Unit, onDone: () -> Unit) {
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
            Spacer(Modifier.height(overlayTopSpacing()))
            // In landscape Done sits in the header, always in sight; in portrait it ends the page.
            val landscape = isLandscape()
            val doneLabel = stringResource(R.string.done)
            GlassPageHeader(
                title = stringResource(R.string.tips_title),
                buttonLabel = doneLabel.takeIf { landscape },
                onButtonClick = onDone,
            )
            Spacer(Modifier.height(SearchConfig.ResultsSpacing))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SettingsPanelConfig.CardSpacing),
            ) {
                Text(
                    text = stringResource(R.string.tips_intro),
                    color = ThemeConfig.Foreground.copy(alpha = SettingsPanelConfig.DESCRIPTION_ALPHA),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = SettingsPanelConfig.CardPadding),
                )
                CardRows(
                    columns = if (landscape) 2 else 1,
                    cards = listOf(
                        { modifier ->
                            TipCard(R.string.tips_do_title, R.string.tips_do_body, modifier) {
                                for (command in commands) CommandTip(command, onTry)
                                TipBody(stringResource(R.string.tips_bare_body))
                            }
                        },
                        { modifier ->
                            TipCard(R.string.tips_find_title, R.string.tips_find_body, modifier) {
                                Examples(FIND_EXAMPLES, onTry)
                            }
                        },
                        { modifier ->
                            TipCard(R.string.tips_filter_title, R.string.tips_filter_body, modifier) {
                                Examples(FILTER_EXAMPLES, onTry)
                            }
                        },
                        { modifier ->
                            TipCard(R.string.tips_maths_title, R.string.tips_maths_body, modifier) {
                                Examples(MATHS_EXAMPLES, onTry)
                            }
                        },
                        { modifier -> TipCard(R.string.tips_move_title, R.string.tips_move_body, modifier) },
                    ),
                )
                if (!landscape) GlassPageButton(label = doneLabel, onClick = onDone)
            }
        }
    }
}

/** A glass card like About's: a [title], a [body], then any [content] (examples). */
@Composable
private fun TipCard(
    title: Int,
    body: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(SettingsPanelConfig.CardCornerRadius), tint = SettingsPanelConfig.GlassTint)
            .padding(SettingsPanelConfig.CardPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsPanelConfig.TitleDescriptionGap),
    ) {
        Text(
            text = stringResource(title),
            color = ThemeConfig.Foreground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        TipBody(stringResource(body))
        content()
    }
}

@Composable
private fun TipBody(text: String) {
    Text(
        text = text,
        color = ThemeConfig.Foreground.copy(alpha = SettingsPanelConfig.DESCRIPTION_ALPHA),
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** One command: its icon, its example to try, and what it does. */
@Composable
private fun CommandTip(command: QuickCommand, onTry: (String) -> Unit) {
    Row(
        modifier = Modifier.padding(top = SettingsPanelConfig.TitleDescriptionGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SettingsPanelConfig.ChoiceChipSpacing),
    ) {
        Icon(
            imageVector = command.icon,
            contentDescription = null,
            tint = ThemeConfig.Foreground,
            modifier = Modifier.size(SearchConfig.QuickActionIconSize),
        )
        ExampleChip(command.example, onTry)
        Text(
            text = stringResource(command.descriptionRes),
            color = ThemeConfig.Foreground.copy(alpha = SettingsPanelConfig.DESCRIPTION_ALPHA),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

/** [examples] as chips, wrapping onto more lines as needed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Examples(examples: List<String>, onTry: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.padding(top = SettingsPanelConfig.TitleDescriptionGap),
        horizontalArrangement = Arrangement.spacedBy(SettingsPanelConfig.ChoiceChipSpacing),
        verticalArrangement = Arrangement.spacedBy(SettingsPanelConfig.ChoiceChipSpacing),
    ) {
        for (example in examples) ExampleChip(example, onTry)
    }
}

/** A glass pill with [example] in it; tapping it tries it in search. */
@Composable
private fun ExampleChip(example: String, onTry: (String) -> Unit) {
    val label = stringResource(R.string.tips_try, example)
    Text(
        text = example,
        color = ThemeConfig.Foreground,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .liquidGlass(RoundedCornerShape(percent = 50), tint = 0f)
            .background(
                ThemeConfig.Foreground.copy(alpha = CategoryFilterConfig.THUMB_SHEEN_ALPHA),
                RoundedCornerShape(percent = 50),
            )
            .clickable(onClickLabel = label) { onTry(example) }
            .padding(horizontal = SettingsPanelConfig.CardPadding, vertical = SettingsPanelConfig.TitleDescriptionGap),
    )
}

/** Things to find on any phone: Settings screens and a common folder. */
private val FIND_EXAMPLES = listOf("wifi", "brightness", "download")

private val FILTER_EXAMPLES = listOf("!pdf", "!images", "!settings")

private val MATHS_EXAMPLES = listOf("24*7", "sqrt(2)", "(120+45)/3")
