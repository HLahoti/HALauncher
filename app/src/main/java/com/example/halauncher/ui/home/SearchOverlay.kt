package com.example.halauncher.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import com.example.halauncher.R
import com.example.halauncher.config.CategoryFilterConfig
import com.example.halauncher.config.SearchConfig
import com.example.halauncher.config.ThemeConfig
import com.example.halauncher.model.LaunchableElement
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry
import com.example.halauncher.ui.components.ClickToLaunch
import com.example.halauncher.ui.components.icon
import com.example.halauncher.ui.components.launchSafely
import com.example.halauncher.ui.components.liquidGlass
import com.example.halauncher.ui.components.rememberDrawablePainter
import com.example.halauncher.util.Haptics
import kotlin.math.abs

@Composable
fun SearchOverlay(
    query: String,
    results: SearchResults,
    categories: List<SearchCategory>,
    categoryFilter: SearchCategory?,
    showFilesPrompt: Boolean,
    onQueryChange: (String) -> Unit,
    onCategoryFilterChange: (SearchCategory?) -> Unit,
    onEntryOpened: (SearchEntry) -> Unit,
    onRequestFilesAccess: () -> Unit,
    onDismissFilesPrompt: () -> Unit,
    modifier: Modifier = Modifier,
    isFullyIn: Boolean = true,
    reveal: SearchRevealState? = null,
    pickTitle: String? = null,
    onEntryPicked: (SearchEntry) -> Unit = {},
    calcResult: String? = null,
    onCalcResultSelected: (String) -> Unit = {},
) {
    // When picking (e.g. an app for the money button), results are chosen, not opened.
    val isPicking = pickTitle != null
    // Calculator answers only make sense while finding, not while choosing an app for a button.
    val activeCalcResult = calcResult.takeIf { !isPicking }
    val context = LocalContext.current
    val resources = LocalResources.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val searchFieldShape = RoundedCornerShape(percent = 50)
    val placeholderColor = ThemeConfig.Foreground.copy(alpha = SearchConfig.PLACEHOLDER_ALPHA)
    val keyboard = LocalSoftwareKeyboardController.current

    val focusManager = LocalFocusManager.current
    // Only once search has fully come in; a swipe that's still bringing it in may yet let go.
    // The overlay stays composed while hidden, so it lets go of the keyboard itself on the way out.
    LaunchedEffect(isFullyIn) {
        if (isFullyIn) {
            focusRequester.requestFocus()
            keyboard?.show()
        } else {
            focusManager.clearFocus()
        }
    }

    // Swiping down closes search the way it came in, in step with the finger. On the results
    // list that starts only once the list is scrolled to the top: it gets the drag first.
    val dismissScroll = remember(reveal) { reveal?.let(::SearchDismissScroll) }

    // Swiping sideways anywhere in search steps the category filter, like dragging the bar's
    // thumb one segment: left for the next category, right for the previous one.
    val canSwitchCategory = categories.size > 1 && !isPicking
    val haptics = remember(context) { Haptics(context) }
    val segments: List<SearchCategory?> = remember(categories) { listOf(null) + categories }
    val currentSegments by rememberUpdatedState(segments)
    val currentCategoryFilter by rememberUpdatedState(categoryFilter)
    val currentOnCategoryFilterChange by rememberUpdatedState(onCategoryFilterChange)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SearchConfig.SCRIM_ALPHA))
            // Being a pointer-input target stops taps reaching the home screen underneath.
            .pointerInput(Unit) { detectTapGestures { } }
            // Drags anywhere else in search (around the list, below it) close it straight away.
            .pointerInput(reveal) {
                if (reveal == null) return@pointerInput
                val tracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        reveal.dragBy(dy)
                    },
                    onDragEnd = { reveal.settle(tracker.calculateVelocity().y) },
                    onDragCancel = { reveal.settle(0f) },
                )
            }
            .then(if (dismissScroll != null) Modifier.nestedScroll(dismissScroll) else Modifier)
            // The bar, the list and the search field get the drag first; this takes what's left.
            .pointerInput(canSwitchCategory) {
                if (!canSwitchCategory) return@pointerInput
                val stepPx = CategoryFilterConfig.SwipeStepDistance.toPx()
                var dragged = 0f
                var stepped = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragged = 0f
                        stepped = false
                    },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        dragged += dx
                        if (stepped || abs(dragged) < stepPx) return@detectHorizontalDragGestures
                        stepped = true
                        val current = currentSegments.indexOf(currentCategoryFilter).coerceAtLeast(0)
                        val next = (current + if (dragged < 0f) 1 else -1).coerceIn(0, currentSegments.lastIndex)
                        if (next != current) {
                            haptics.tick()
                            currentOnCategoryFilterChange(currentSegments[next])
                        }
                    },
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = SearchConfig.HorizontalPadding)
                .padding(bottom = SearchConfig.BottomSpacing),
        ) {
            Spacer(Modifier.height(SearchConfig.TopSpacing))
            if (pickTitle != null) {
                Text(
                    text = pickTitle,
                    color = ThemeConfig.Foreground,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(start = SearchConfig.SectionLabelPadding, bottom = SearchConfig.ResultsSpacing)
                        .semantics { heading() },
                )
            }
            // A plain TextField with its fill and underline cleared, so the glass is the surface.
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(searchFieldShape, tint = SearchConfig.FIELD_GLASS_TINT)
                    .focusRequester(focusRequester),
                shape = searchFieldShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = ThemeConfig.Foreground,
                    unfocusedTextColor = ThemeConfig.Foreground,
                    cursorColor = ThemeConfig.Foreground,
                    focusedLeadingIconColor = ThemeConfig.Foreground,
                    unfocusedLeadingIconColor = ThemeConfig.Foreground,
                    focusedPlaceholderColor = placeholderColor,
                    unfocusedPlaceholderColor = placeholderColor,
                ),
                placeholder = { Text(stringResource(R.string.search_apps)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    // Go acts on the top result, the same as tapping it. The calc answer, when
                    // it's there, sits above everything else, so Go copies it to the clipboard.
                    onGo = {
                        activeCalcResult?.let {
                            onCalcResultSelected(it)
                            return@KeyboardActions
                        }
                        val entry = results.firstOrNull() ?: return@KeyboardActions
                        when {
                            isPicking -> onEntryPicked(entry)
                            context.launchSafely(entry, resources.getString(R.string.open_app, entry.name)) ->
                                onEntryOpened(entry)
                        }
                    },
                ),
            )
            // Filtering only helps when there's more than one kind of result, and picking is
            // already limited to one kind.
            if (categories.size > 1 && !isPicking) {
                CategoryFilterBar(
                    categories = categories,
                    selected = categoryFilter,
                    onSelected = onCategoryFilterChange,
                    modifier = Modifier
                        .padding(vertical = CategoryFilterConfig.VerticalSpacing)
                        .align(Alignment.CenterHorizontally),
                )
            } else {
                Spacer(Modifier.height(SearchConfig.ResultsSpacing))
            }
            if (showFilesPrompt && !isPicking) {
                FilesAccessPrompt(onAllow = onRequestFilesAccess, onDismiss = onDismissFilesPrompt)
            }
            // A new query or category starts the list from the top, and so does coming back:
            // search stays composed while hidden, so the list would otherwise keep its place.
            LaunchedEffect(query, categoryFilter) { listState.scrollToItem(0) }
            LaunchedEffect(reveal) {
                if (reveal != null) {
                    snapshotFlow { reveal.value == 0f }.collect { hidden -> if (hidden) listState.scrollToItem(0) }
                }
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                if (activeCalcResult != null) {
                    item(key = CALC_RESULT_KEY) {
                        CalcResultRow(query = query, result = activeCalcResult, onCopy = onCalcResultSelected)
                    }
                }
                items(results.frequent, key = { it.id }) { entry ->
                    EntryRow(entry = entry, isPicking = isPicking, onOpened = onEntryOpened, onPicked = onEntryPicked)
                }
                if (results.frequent.isNotEmpty() && results.more.isNotEmpty()) {
                    item(key = MORE_RESULTS_KEY) { SectionLabel(stringResource(R.string.more_results)) }
                }
                items(results.more, key = { it.id }) { entry ->
                    EntryRow(entry = entry, isPicking = isPicking, onOpened = onEntryOpened, onPicked = onEntryPicked)
                }
                if (results.isEmpty && activeCalcResult == null && !results.isSearchingDisk && query.isNotBlank()) {
                    item(key = NO_RESULTS_KEY) { SectionLabel(stringResource(R.string.no_results)) }
                }
            }
        }
    }
}

/**
 * Hands the results list's leftover drag to [reveal]: pulling down past the top of the list
 * brings search down with the finger, and while it's part-way down, pushing back up raises it
 * again before the list scrolls. Letting go settles it, like the swipe that opened it.
 */
private class SearchDismissScroll(private val reveal: SearchRevealState) : NestedScrollConnection {
    private val isPartWay get() = reveal.value < 1f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y >= 0f || !isPartWay) return Offset.Zero
        return Offset(0f, reveal.dragBy(available.y))
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        return Offset(0f, reveal.dragBy(available.y))
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!reveal.isDragging) return Velocity.Zero
        val partWay = isPartWay
        reveal.settle(available.y)
        // Search is moving now, not the list (unless it was pushed back fully in).
        return if (partWay) available else Velocity.Zero
    }
}

/** List keys that can't collide with entry ids (which all contain a ':'). */
private const val MORE_RESULTS_KEY = "section-more"
private const val NO_RESULTS_KEY = "section-none"
private const val CALC_RESULT_KEY = "section-calc"

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = ThemeConfig.Foreground.copy(alpha = SearchConfig.SECTION_LABEL_ALPHA),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .padding(SearchConfig.SectionLabelPadding)
            .semantics { heading() },
    )
}

/** A glass row offering to turn on file search, which needs "All files access". */
@Composable
private fun FilesAccessPrompt(onAllow: () -> Unit, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = SearchConfig.ResultsSpacing)
            .liquidGlass(shape, tint = SearchConfig.PROMPT_GLASS_TINT)
            .clickable(onClickLabel = stringResource(R.string.allow_file_search_action), onClick = onAllow)
            .padding(start = SearchConfig.ResultRowPadding * 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SearchConfig.ResultIconTextGap),
    ) {
        Icon(
            imageVector = SearchCategory.FILE.icon,
            contentDescription = null,
            tint = ThemeConfig.Foreground,
            modifier = Modifier.size(SearchConfig.CategoryBadgeSize),
        )
        Text(
            text = stringResource(R.string.allow_file_search),
            color = ThemeConfig.Foreground,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.allow_file_search_action),
            color = ThemeConfig.Foreground,
            style = MaterialTheme.typography.labelLarge,
        )
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.dismiss),
                tint = ThemeConfig.Foreground.copy(alpha = SearchConfig.CATEGORY_BADGE_ALPHA),
                modifier = Modifier.size(SearchConfig.CategoryBadgeSize),
            )
        }
    }
}

/** A result row: tapping opens the entry, or, when [isPicking], chooses it instead. */
@Composable
private fun EntryRow(
    entry: SearchEntry,
    isPicking: Boolean,
    onOpened: (SearchEntry) -> Unit,
    onPicked: (SearchEntry) -> Unit,
) {
    if (isPicking) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.choose_app, entry.name)) { onPicked(entry) },
        ) {
            EntryRowContent(entry)
        }
        return
    }
    val label = stringResource(R.string.open_app, entry.name)
    val element = remember(entry, label) {
        LaunchableElement(
            displayContent = { EntryRowContent(entry) },
            launchIntent = entry.link,
            fallbackIntents = entry.fallbackLinks,
            actionLabel = label,
        )
    }
    ClickToLaunch(element, modifier = Modifier.fillMaxWidth(), onLaunched = { onOpened(entry) })
}

@Composable
private fun EntryRowContent(entry: SearchEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(SearchConfig.ResultRowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SearchConfig.ResultIconTextGap),
    ) {
        EntryIcon(entry)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SearchConfig.ResultDetailGap),
        ) {
            Text(
                text = entry.name,
                color = ThemeConfig.Foreground,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // A category-specific secondary line, rendered the same way for every kind of result.
            // It runs the full width of the row and elides its middle only when it doesn't fit, so
            // both ends of a long file path stay readable.
            entry.detail?.let { detail ->
                Text(
                    text = detail,
                    color = ThemeConfig.Foreground.copy(alpha = SearchConfig.RESULT_DETAIL_ALPHA),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
        }
    }
}

/**
 * The row for a calculator answer. Tapping it (or pressing Go while it's at the top) copies the
 * [result] to the clipboard. The input [query] is shown on the secondary line so there's no doubt
 * which expression produced the number.
 */
@Composable
private fun CalcResultRow(query: String, result: String, onCopy: (String) -> Unit) {
    val label = stringResource(R.string.copy_calc_result, result)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = label) { onCopy(result) }
            .padding(SearchConfig.ResultRowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SearchConfig.ResultIconTextGap),
    ) {
        Box(
            modifier = Modifier.size(SearchConfig.ResultIconSize),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "=",
                color = ThemeConfig.Foreground,
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SearchConfig.ResultDetailGap),
        ) {
            Text(
                text = result,
                color = ThemeConfig.Foreground,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = query,
                color = ThemeConfig.Foreground.copy(alpha = SearchConfig.RESULT_DETAIL_ALPHA),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
    }
}

/** The entry's own image (app icon, contact photo), or its category icon when it has none. */
@Composable
private fun EntryIcon(entry: SearchEntry) {
    val icon = entry.icon
    // The name next to it already describes the row, so the image itself is decorative.
    if (icon != null) {
        Image(
            painter = rememberDrawablePainter(icon),
            contentDescription = null,
            modifier = Modifier.size(SearchConfig.ResultIconSize),
        )
    } else {
        Icon(
            imageVector = entry.category.icon,
            contentDescription = null,
            tint = ThemeConfig.Foreground,
            modifier = Modifier
                .size(SearchConfig.ResultIconSize)
                .padding(SearchConfig.PlaceholderIconPadding),
        )
    }
}
