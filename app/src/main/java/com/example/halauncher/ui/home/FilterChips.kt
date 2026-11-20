package com.example.halauncher.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.example.halauncher.R
import com.example.halauncher.config.CategoryFilterConfig
import com.example.halauncher.config.FilterSuggestionConfig
import com.example.halauncher.config.SearchConfig
import com.example.halauncher.config.ThemeConfig
import com.example.halauncher.model.SearchFilter
import com.example.halauncher.ui.components.icon
import com.example.halauncher.ui.components.labelRes
import com.example.halauncher.ui.components.liquidGlass

/**
 * The suggested filter chips over the search field: the kinds of result that would narrow what's
 * listed (see FilterSuggester), each a glass pill with its icon and name. While a chip's [kind]
 * narrows search it's the only one, lit, with a cross: tapping it takes the filter off.
 *
 * The chips sit at the side of the filter pill ([alignEnd] for right-handed, the start for left).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilterChipsRow(
    chips: List<SearchFilter>,
    kind: SearchFilter?,
    onPicked: (SearchFilter) -> Unit,
    onCleared: () -> Unit,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = true,
) {
    val shown = if (kind != null) listOf(kind) else chips
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(FilterSuggestionConfig.ChipHeight),
        horizontalArrangement = Arrangement.spacedBy(
            FilterSuggestionConfig.ChipGap,
            if (alignEnd) Alignment.End else Alignment.Start,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(shown, key = { it.name }) { chip ->
            val isOn = chip == kind
            FilterChip(
                chip = chip,
                isOn = isOn,
                onClick = { if (isOn) onCleared() else onPicked(chip) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun FilterChip(chip: SearchFilter, isOn: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(percent = 50)
    val name = stringResource(chip.labelRes)
    val description = if (isOn) stringResource(R.string.filter_chip_clear, name) else stringResource(R.string.filter_show, name)
    Row(
        modifier = modifier
            .height(FilterSuggestionConfig.ChipHeight)
            .liquidGlass(shape, tint = SearchConfig.FIELD_GLASS_TINT)
            .then(
                if (isOn) {
                    Modifier.background(ThemeConfig.Foreground.copy(alpha = CategoryFilterConfig.THUMB_SHEEN_ALPHA), shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
            }
            .padding(horizontal = FilterSuggestionConfig.ChipPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FilterSuggestionConfig.ChipGap),
    ) {
        Icon(
            imageVector = chip.icon,
            contentDescription = null,
            tint = ThemeConfig.Foreground,
            modifier = Modifier.size(FilterSuggestionConfig.ChipIconSize),
        )
        Text(
            text = name,
            color = ThemeConfig.Foreground,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        if (isOn) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = ThemeConfig.Foreground,
                modifier = Modifier.size(FilterSuggestionConfig.ChipIconSize),
            )
        }
    }
}
