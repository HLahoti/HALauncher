package com.example.halauncher.ui.home

import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import com.example.halauncher.R
import com.example.halauncher.config.CategoryFilterConfig
import com.example.halauncher.config.SearchConfig
import com.example.halauncher.config.ThemeConfig
import com.example.halauncher.data.ContactPhotoCache
import com.example.halauncher.data.FileThumbnailCache
import com.example.halauncher.model.AccessState
import com.example.halauncher.model.ContactEntry
import com.example.halauncher.model.FileEntry
import com.example.halauncher.model.LaunchableElement
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry
import com.example.halauncher.ui.components.ClickToLaunch
import com.example.halauncher.ui.components.GridLayoutIcon
import com.example.halauncher.ui.components.ListLayoutIcon
import com.example.halauncher.ui.components.icon
import com.example.halauncher.ui.components.launchSafely
import com.example.halauncher.ui.components.liquidGlass
import com.example.halauncher.ui.components.rememberDrawablePainter
import com.example.halauncher.util.Haptics
import java.text.NumberFormat
import kotlin.math.abs
import kotlinx.coroutines.delay

@Composable
fun SearchOverlay(
    query: String,
    results: SearchResults,
    categories: List<SearchCategory>,
    categoryFilter: SearchCategory?,
    onQueryChange: (String) -> Unit,
    onCategoryFilterChange: (SearchCategory?) -> Unit,
    onEntryOpened: (SearchEntry) -> Unit,
    modifier: Modifier = Modifier,
    gridCategories: Set<SearchCategory?> = emptySet(),
    onGridChange: (SearchCategory?, Boolean) -> Unit = { _, _ -> },
    contactPhotos: ContactPhotoCache? = null,
    fileThumbnails: FileThumbnailCache? = null,
    isFullyIn: Boolean = true,
    reveal: SearchRevealState? = null,
    pickTitle: String? = null,
    onEntryPicked: (SearchEntry) -> Unit = {},
    calcResult: String? = null,
    onCalcResultSelected: (String) -> Unit = {},
    permissions: SearchPermissions = SearchPermissions(),
    indexedCounts: Map<SearchCategory, Int> = emptyMap(),
) {
    // When picking (e.g. an app for the money button), results are chosen, not opened.
    val isPicking = pickTitle != null
    // Calculator answers only make sense while finding, not while choosing an app for a button.
    val activeCalcResult = calcResult.takeIf { !isPicking }
    val context = LocalContext.current
    val resources = LocalResources.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    // Filtering only helps when there's more than one kind of result, and picking is already
    // limited to one kind.
    val showsCategoryBar = categories.size > 1 && !isPicking
    // Each tab ("All" included) can be a grid, switched by the toggle beside the bar; picking an
    // app, or search without the bar, stays a list.
    val showGrid = showsCategoryBar && categoryFilter in gridCategories
    val searchFieldShape = RoundedCornerShape(percent = 50)
    val placeholderColor = ThemeConfig.Foreground.copy(alpha = SearchConfig.PLACEHOLDER_ALPHA)
    val keyboard = LocalSoftwareKeyboardController.current
    // Every tab says how much it's searching (on "All", each readable source), so it's clear the
    // index is there and working, even on a tab with no recents yet. A source that can't be read
    // has its permission notice instead.
    val readyCounts = SearchCategory.entries.mapNotNull { category ->
        val count = indexedCounts[category]
        val readable = when (category) {
            SearchCategory.APP -> true
            SearchCategory.CONTACT -> permissions.contacts.isGranted
            SearchCategory.FILE -> permissions.files.isGranted
        }
        if (count != null && readable && (categoryFilter == null || categoryFilter == category)) category to count else null
    }.takeIf { !isPicking }.orEmpty()
    // Matches only need a label to set them apart from the recents above them.
    val showsMoreLabel = results.frequent.isNotEmpty() && results.more.isNotEmpty()

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
            if (showsCategoryBar) {
                // The bar stays centred; the grid/list toggle sits at the end of its line.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = CategoryFilterConfig.VerticalSpacing),
                ) {
                    CategoryFilterBar(
                        categories = categories,
                        selected = categoryFilter,
                        onSelected = onCategoryFilterChange,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    LayoutToggle(
                        category = categoryFilter,
                        asGrid = showGrid,
                        onChange = { onGridChange(categoryFilter, it) },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            } else {
                Spacer(Modifier.height(SearchConfig.ResultsSpacing))
            }
            if (!isPicking) {
                SearchPermissionNotice(
                    permissions = permissions,
                    categoryFilter = categoryFilter,
                    modifier = Modifier.padding(bottom = SearchConfig.ResultsSpacing),
                )
            }
            if (readyCounts.isNotEmpty()) IndexStatus(readyCounts)
            // A new query or category starts the list from the top, and so does coming back:
            // search stays composed while hidden, so the list would otherwise keep its place.
            LaunchedEffect(query, categoryFilter, showGrid) {
                listState.scrollToItem(0)
                gridState.scrollToItem(0)
            }
            LaunchedEffect(reveal) {
                if (reveal != null) {
                    snapshotFlow { reveal.value == 0f }.collect { hidden ->
                        if (hidden) {
                            listState.scrollToItem(0)
                            gridState.scrollToItem(0)
                        }
                    }
                }
            }
            if (showGrid) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(SearchConfig.GRID_COLUMNS),
                    state = gridState,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (activeCalcResult != null) {
                        item(key = CALC_RESULT_KEY, span = { GridItemSpan(maxLineSpan) }) {
                            CalcResultRow(query = query, result = activeCalcResult, onCopy = onCalcResultSelected)
                        }
                    }
                    items(results.frequent, key = { it.id }) { entry ->
                        EntryItem(entry = entry, isPicking = false, onOpened = onEntryOpened, onPicked = onEntryPicked) {
                            EntryGridCellContent(entry, contactPhotos, fileThumbnails)
                        }
                    }
                    if (showsMoreLabel) {
                        item(key = MORE_RESULTS_KEY, span = { GridItemSpan(maxLineSpan) }) {
                            SectionLabel(stringResource(R.string.more_results))
                        }
                    }
                    items(results.more, key = { it.id }) { entry ->
                        EntryItem(entry = entry, isPicking = false, onOpened = onEntryOpened, onPicked = onEntryPicked) {
                            EntryGridCellContent(entry, contactPhotos, fileThumbnails)
                        }
                    }
                    results.suggestions.forEach { group ->
                        item(key = suggestionsKey(group.kind), span = { GridItemSpan(maxLineSpan) }) {
                            SectionLabel(stringResource(group.kind.label))
                        }
                        items(group.entries, key = { it.id }) { entry ->
                            EntryItem(entry = entry, isPicking = false, onOpened = onEntryOpened, onPicked = onEntryPicked) {
                                EntryGridCellContent(entry, contactPhotos, fileThumbnails)
                            }
                        }
                    }
                    if (results.isEmpty && activeCalcResult == null && !results.isSearchingDisk && query.isNotBlank()) {
                        item(key = NO_RESULTS_KEY, span = { GridItemSpan(maxLineSpan) }) {
                            SectionLabel(stringResource(R.string.no_results))
                        }
                    }
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                    if (activeCalcResult != null) {
                        item(key = CALC_RESULT_KEY) {
                            CalcResultRow(query = query, result = activeCalcResult, onCopy = onCalcResultSelected)
                        }
                    }
                    items(results.frequent, key = { it.id }) { entry ->
                        EntryItem(entry = entry, isPicking = isPicking, onOpened = onEntryOpened, onPicked = onEntryPicked) {
                            EntryRowContent(entry, contactPhotos, fileThumbnails)
                        }
                    }
                    if (showsMoreLabel) {
                        item(key = MORE_RESULTS_KEY) { SectionLabel(stringResource(R.string.more_results)) }
                    }
                    items(results.more, key = { it.id }) { entry ->
                        EntryItem(entry = entry, isPicking = isPicking, onOpened = onEntryOpened, onPicked = onEntryPicked) {
                            EntryRowContent(entry, contactPhotos, fileThumbnails)
                        }
                    }
                    results.suggestions.forEach { group ->
                        item(key = suggestionsKey(group.kind)) { SectionLabel(stringResource(group.kind.label)) }
                        items(group.entries, key = { it.id }) { entry ->
                            EntryItem(entry = entry, isPicking = isPicking, onOpened = onEntryOpened, onPicked = onEntryPicked) {
                                EntryRowContent(entry, contactPhotos, fileThumbnails)
                            }
                        }
                    }
                    if (results.isEmpty && activeCalcResult == null && !results.isSearchingDisk && query.isNotBlank()) {
                        item(key = NO_RESULTS_KEY) { SectionLabel(stringResource(R.string.no_results)) }
                    }
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

private fun suggestionsKey(kind: SuggestionKind) = "section-suggested-${kind.name}"

private val SuggestionKind.label: Int
    get() = when (this) {
        SuggestionKind.FAVOURITES -> R.string.favourites
        SuggestionKind.RECENTLY_UPDATED -> R.string.recently_updated
        SuggestionKind.RECENTLY_ADDED -> R.string.recently_added
    }

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

/**
 * "142 apps · 1,248 contacts · 8,910 files ready": how many entries of each category in [counts]
 * the index holds, so it's clear search has everything it should.
 */
@Composable
private fun IndexStatus(counts: List<Pair<SearchCategory, Int>>) {
    val numbers = NumberFormat.getIntegerInstance()
    val parts = counts.map { (category, count) ->
        val plurals = when (category) {
            SearchCategory.APP -> R.plurals.app_count
            SearchCategory.CONTACT -> R.plurals.contact_count
            SearchCategory.FILE -> R.plurals.file_count
        }
        pluralStringResource(plurals, count, numbers.format(count))
    }
    Text(
        text = stringResource(R.string.index_ready, parts.joinToString(" · ")),
        color = ThemeConfig.Foreground.copy(alpha = SearchConfig.SECTION_LABEL_ALPHA),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(start = SearchConfig.SectionLabelPadding, bottom = SearchConfig.ResultsSpacing),
    )
}

/**
 * Where search's permissions stand, and what to do about them. Each tab whose source can't be read
 * says so in a [PermissionNotice] with a link to grant it; "All" sums up whatever's missing, with a
 * link to the setup page, until dismissed.
 */
data class SearchPermissions(
    val contacts: AccessState = AccessState.GRANTED,
    val files: AccessState = AccessState.GRANTED,
    /** Whether "All" still shows its summary (something's missing and it hasn't been dismissed). */
    val showSummary: Boolean = false,
    val onRequestContacts: () -> Unit = {},
    val onRequestFiles: () -> Unit = {},
    val onReview: () -> Unit = {},
    val onDismissSummary: () -> Unit = {},
)

/** The permission notice for the [categoryFilter] tab, if its source (or, on "All", any) is missing. */
@Composable
private fun SearchPermissionNotice(
    permissions: SearchPermissions,
    categoryFilter: SearchCategory?,
    modifier: Modifier = Modifier,
) {
    when (categoryFilter) {
        SearchCategory.CONTACT -> if (!permissions.contacts.isGranted) {
            val denied = permissions.contacts == AccessState.DENIED
            PermissionNotice(
                icon = SearchCategory.CONTACT.icon,
                message = stringResource(
                    if (denied) R.string.permission_contacts_denied else R.string.permission_contacts_not_requested,
                ),
                actionLabel = stringResource(if (denied) R.string.permission_action_turn_on else R.string.permission_action_allow),
                onAction = permissions.onRequestContacts,
                modifier = modifier,
            )
        }
        SearchCategory.FILE -> if (!permissions.files.isGranted) {
            val denied = permissions.files == AccessState.DENIED
            PermissionNotice(
                icon = SearchCategory.FILE.icon,
                message = stringResource(
                    if (denied) R.string.permission_files_denied else R.string.permission_files_not_requested,
                ),
                actionLabel = stringResource(if (denied) R.string.permission_action_turn_on else R.string.permission_action_allow),
                onAction = permissions.onRequestFiles,
                modifier = modifier,
            )
        }
        SearchCategory.APP -> Unit
        null -> {
            val contactsOff = !permissions.contacts.isGranted
            val filesOff = !permissions.files.isGranted
            if (permissions.showSummary && (contactsOff || filesOff)) {
                PermissionNotice(
                    icon = Icons.Default.Info,
                    message = stringResource(
                        when {
                            contactsOff && filesOff -> R.string.permissions_off_both
                            contactsOff -> R.string.permissions_off_contacts
                            else -> R.string.permissions_off_files
                        },
                    ),
                    actionLabel = stringResource(R.string.permissions_review),
                    onAction = permissions.onReview,
                    onDismiss = permissions.onDismissSummary,
                    modifier = modifier,
                )
            }
        }
    }
}

/**
 * A result, drawn by [content] (a list row or a grid cell): tapping opens the entry, or, when
 * [isPicking], chooses it instead.
 */
@Composable
private fun EntryItem(
    entry: SearchEntry,
    isPicking: Boolean,
    onOpened: (SearchEntry) -> Unit,
    onPicked: (SearchEntry) -> Unit,
    content: @Composable () -> Unit,
) {
    if (isPicking) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.choose_app, entry.name)) { onPicked(entry) },
        ) {
            content()
        }
        return
    }
    val label = stringResource(R.string.open_app, entry.name)
    val currentContent by rememberUpdatedState(content)
    val element = remember(entry, label) {
        LaunchableElement(
            displayContent = { currentContent() },
            launchIntent = entry.link,
            fallbackIntents = entry.fallbackLinks,
            actionLabel = label,
        )
    }
    ClickToLaunch(element, modifier = Modifier.fillMaxWidth(), onLaunched = { onOpened(entry) })
}

@Composable
private fun EntryRowContent(entry: SearchEntry, contactPhotos: ContactPhotoCache?, fileThumbnails: FileThumbnailCache?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(SearchConfig.ResultRowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SearchConfig.ResultIconTextGap),
    ) {
        EntryIcon(entry, contactPhotos, fileThumbnails)
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
            // It runs the full width of the row, shown whole when it fits; otherwise it drops the
            // start and keeps as much of the end as fits (for a file path, the nearest folders).
            entry.detail?.let { detail ->
                Text(
                    text = detail,
                    color = ThemeConfig.Foreground.copy(alpha = SearchConfig.RESULT_DETAIL_ALPHA),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                )
            }
        }
    }
}

/**
 * A grid cell: the entry's icon with its name centred right below it, and under that its
 * secondary line (a phone number, a file's folder) when [SearchConfig.SHOW_DETAIL_IN_GRID] is on.
 */
@Composable
private fun EntryGridCellContent(entry: SearchEntry, contactPhotos: ContactPhotoCache?, fileThumbnails: FileThumbnailCache?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(SearchConfig.GridCellPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SearchConfig.GridIconTextGap),
    ) {
        EntryIcon(entry, contactPhotos, fileThumbnails, size = SearchConfig.GridIconSize)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SearchConfig.ResultDetailGap),
        ) {
            Text(
                text = entry.name,
                color = ThemeConfig.Foreground,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // One line, shortened the same way as a list row's: whole when it fits, else as much of
            // the end as fits.
            entry.detail?.takeIf { SearchConfig.SHOW_DETAIL_IN_GRID }?.let { detail ->
                Text(
                    text = detail,
                    color = ThemeConfig.Foreground.copy(alpha = SearchConfig.RESULT_DETAIL_ALPHA),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                )
            }
        }
    }
}

/**
 * The round glass button beside the category bar that switches the [category] tab (null for "All")
 * between a list and a grid. It shows the layout it's set to, brightened like the bar's thumb while it's a grid.
 */
@Composable
private fun LayoutToggle(
    category: SearchCategory?,
    asGrid: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = remember(context) { Haptics(context) }
    Box(
        modifier = modifier
            .size(SearchConfig.LayoutToggleSize)
            .liquidGlass(CircleShape)
            .then(
                if (asGrid) {
                    Modifier.background(
                        ThemeConfig.Foreground.copy(alpha = CategoryFilterConfig.THUMB_SHEEN_ALPHA),
                        CircleShape,
                    )
                } else {
                    Modifier
                },
            )
            .toggleable(value = asGrid, role = Role.Switch) {
                haptics.tick()
                onChange(it)
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (asGrid) GridLayoutIcon else ListLayoutIcon,
            contentDescription = stringResource(
                when (category) {
                    null -> R.string.show_all_as_grid
                    SearchCategory.APP -> R.string.show_apps_as_grid
                    SearchCategory.CONTACT -> R.string.show_contacts_as_grid
                    SearchCategory.FILE -> R.string.show_files_as_grid
                },
            ),
            tint = ThemeConfig.Foreground,
            modifier = Modifier.size(SearchConfig.LayoutToggleIconSize),
        )
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

/**
 * The entry's own image (app icon, contact photo, file thumbnail), or its category icon when it has
 * none. A contact's photo loads from [contactPhotos], and a file's thumbnail from [fileThumbnails],
 * as it's shown; until then (or if there's none) the category icon stands in.
 */
@Composable
private fun EntryIcon(
    entry: SearchEntry,
    contactPhotos: ContactPhotoCache?,
    fileThumbnails: FileThumbnailCache?,
    size: Dp = SearchConfig.ResultIconSize,
) {
    val icon = entry.icon ?: rememberContactPhoto(entry, contactPhotos) ?: rememberFileThumbnail(entry, fileThumbnails)
    // The name next to it already describes the row, so the image itself is decorative.
    if (icon != null) {
        Image(
            painter = rememberDrawablePainter(icon),
            contentDescription = null,
            modifier = Modifier.size(size),
        )
    } else {
        Icon(
            imageVector = entry.category.icon,
            contentDescription = null,
            tint = ThemeConfig.Foreground,
            modifier = Modifier
                .size(size)
                .padding(SearchConfig.PlaceholderIconPadding),
        )
    }
}

/**
 * [entry]'s contact photo: straight away when it's in memory, else once [contactPhotos] has loaded
 * it in the background. Null for anything that isn't a contact with a photo.
 */
@Composable
private fun rememberContactPhoto(entry: SearchEntry, contactPhotos: ContactPhotoCache?): Drawable? {
    val contact = (entry as? ContactEntry)?.takeIf { it.photoUri != null }
    var photo by remember(contact, contactPhotos) { mutableStateOf(contact?.let { contactPhotos?.cached(it) }) }
    LaunchedEffect(contact, contactPhotos) {
        if (photo == null && contact != null && contactPhotos != null) photo = contactPhotos.load(contact)
    }
    return photo
}

/**
 * [entry]'s file thumbnail: straight away when it's in memory, else once [fileThumbnails] has
 * loaded it in the background, if the row is still shown after a short pause. Null for anything
 * that isn't an image, video, audio or PDF file with a thumbnail.
 */
@Composable
private fun rememberFileThumbnail(entry: SearchEntry, fileThumbnails: FileThumbnailCache?): Drawable? {
    val file = (entry as? FileEntry)?.takeIf { fileThumbnails?.canHaveThumbnail(it) == true }
    var thumbnail by remember(file, fileThumbnails) { mutableStateOf(file?.let { fileThumbnails?.cached(it) }) }
    LaunchedEffect(file, fileThumbnails) {
        if (thumbnail == null && file != null && fileThumbnails != null) {
            // Cancelled with the row, so results only flashing past never load.
            delay(SearchConfig.FILE_THUMBNAIL_LOAD_DELAY_MS)
            thumbnail = fileThumbnails.load(file)
        }
    }
    return thumbnail
}
