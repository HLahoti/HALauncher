package com.example.halauncher.ui.home

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import android.os.Build
import android.content.Context
import android.content.res.Resources
import android.text.format.DateFormat
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.annotation.ArrayRes
import androidx.annotation.PluralsRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
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
import com.example.halauncher.data.ShortcutIconCache
import com.example.halauncher.model.AccessState
import com.example.halauncher.model.ContactEntry
import com.example.halauncher.model.EventEntry
import com.example.halauncher.model.FileEntry
import com.example.halauncher.model.LaunchableElement
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry
import com.example.halauncher.model.SearchFilter
import com.example.halauncher.model.ShortcutEntry
import com.example.halauncher.ui.components.ClickToLaunch
import com.example.halauncher.ui.components.GridLayoutIcon
import com.example.halauncher.ui.components.ListLayoutIcon
import com.example.halauncher.ui.components.icon
import com.example.halauncher.ui.components.ignoreLongPress
import com.example.halauncher.ui.components.isLandscape
import com.example.halauncher.ui.components.launchSafely
import com.example.halauncher.ui.components.liquidGlass
import com.example.halauncher.ui.components.overlayTopSpacing
import com.example.halauncher.ui.components.rememberDrawablePainter
import com.example.halauncher.util.Haptics
import com.example.halauncher.util.QuickAction
import com.example.halauncher.util.QuickActionTarget
import com.example.halauncher.util.QuickCommand
import com.example.halauncher.util.intent
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
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
    searchKind: SearchFilter? = null,
    onFilterChipPicked: (SearchFilter) -> Unit = {},
    onSearchKindCleared: () -> Unit = {},
    gridCategories: Set<SearchCategory?> = emptySet(),
    onGridChange: (SearchCategory?, Boolean) -> Unit = { _, _ -> },
    contactPhotos: ContactPhotoCache? = null,
    fileThumbnails: FileThumbnailCache? = null,
    shortcutIcons: ShortcutIconCache? = null,
    isFullyIn: Boolean = true,
    reveal: SearchRevealState? = null,
    pickTitle: String? = null,
    onEntryPicked: (SearchEntry) -> Unit = {},
    calcResult: String? = null,
    onCalcResultSelected: (String) -> Unit = {},
    quickActions: List<QuickActionTarget> = emptyList(),
    quickActionsQuery: String = "",
    onQuickActionRun: () -> Unit = {},
    commandHints: List<QuickCommand> = emptyList(),
    supportedCommands: List<QuickCommand> = emptyList(),
    permissions: SearchPermissions = SearchPermissions(),
    indexedCounts: Map<SearchCategory, Int> = emptyMap(),
    leftHanded: Boolean = false,
    entryActions: SearchEntryActions = SearchEntryActions(),
) {
    // When picking (e.g. an app for the money button), results are chosen, not opened.
    val isPicking = pickTitle != null
    // Calculator answers only make sense while finding, not while choosing an app for a button.
    val activeCalcResult = calcResult.takeIf { !isPicking }
    // Nor do quick actions ("timer 5m").
    val activeQuickActions = if (isPicking) emptyList() else quickActions
    val activeCommandHints = if (isPicking) emptyList() else commandHints
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
    val isLandscape = isLandscape()
    val searchFieldShape = RoundedCornerShape(percent = 50)
    val placeholderColor = ThemeConfig.Foreground.copy(alpha = SearchConfig.PLACEHOLDER_ALPHA)
    // While nothing's typed, the status line above the results carries a tip for the tab showing, so
    // what search can do gets found; a different one each time search comes in (picked while it's
    // hidden, so it never changes in view). The field itself stays empty. Picking an app for a button
    // keeps its plain hint and has no tips: only apps are listed then.
    val hints = tabHints(categoryFilter, supportedCommands)
    var hintSeed by remember { mutableIntStateOf(Random.nextInt(HINT_SEED_RANGE)) }
    val isHidden by remember(reveal) { derivedStateOf { reveal != null && reveal.value == 0f } }
    LaunchedEffect(isHidden) {
        if (isHidden) hintSeed = Random.nextInt(HINT_SEED_RANGE)
    }
    val placeholder = if (isPicking) stringResource(R.string.search_apps) else null
    val fieldLabel = placeholder ?: stringResource(R.string.search_field_label)
    val keyboard = LocalSoftwareKeyboardController.current
    // Every tab says how much it's searching (on "All", every readable source together), so it's
    // clear the index is there and working, even on a tab with no recents yet. A source that can't
    // be read has its permission notice instead.
    val readableCounts = SearchCategory.entries.mapNotNull { category ->
        val count = indexedCounts[category]
        val readable = when (category) {
            SearchCategory.APP -> true
            SearchCategory.CONTACT -> permissions.contacts.isGranted
            SearchCategory.EVENT -> permissions.calendar.isGranted
            SearchCategory.FILE, SearchCategory.FOLDER -> permissions.files.isGranted
            SearchCategory.SHORTCUT, SearchCategory.SETTING -> true
        }
        if (count != null && readable && (categoryFilter == null || categoryFilter == category.tab)) count else null
    }
    // Once something's typed, the line counts the matches listed instead: the recents at first,
    // then the disk's too once it answers. With no matches it's left out; "No results" says so.
    val isQueryBlank = query.isBlank()
    val statusCount = when {
        isPicking -> null
        isQueryBlank -> readableCounts.takeIf { it.isNotEmpty() }?.sum()
        // Quick actions ("Call Mahak") and the calculator's answer are things found too, so they count.
        else -> (results.matchCounts.values.sum() + activeQuickActions.size + (if (activeCalcResult != null) 1 else 0))
            .takeIf { it > 0 }
    }
    // Beside the count: while a command's word is being typed ("tim"), how to finish it, tapped to
    // type the word; otherwise, with nothing typed, the tab's tip.
    // Commands sharing the word ("alarm 730am", "alarm off") are shown together.
    val commandHint = activeCommandHints.firstOrNull()
        ?.let { first -> activeCommandHints.filter { it.word == first.word } }
    val statusHint: StatusHint? = when {
        isPicking -> null
        commandHint != null -> StatusHint.Command(commandHint)
        isQueryBlank && hints.isNotEmpty() -> StatusHint.Tip(hints[hintSeed % hints.size])
        else -> null
    }
    // Matches only need a label to set them apart from the recents above them.
    val showsMoreLabel = results.frequent.isNotEmpty() && results.more.isNotEmpty()

    // The result whose long-press menu is open, if any. Meanwhile every other result dims and
    // takes no taps, and the results don't scroll (which would carry the menu's row away from it).
    // Choosing an app for a button has no menus.
    // Removing a recent reorders the results under the finger, and the list would keep its place
    // by the removed entry; it goes back to the top once they change instead.
    var scrollToTopOnChange by remember { mutableStateOf(false) }
    val activeEntryActions = remember(entryActions, isPicking) {
        if (isPicking) {
            SearchEntryActions()
        } else {
            entryActions.copy(
                onForgetRecent = { entry ->
                    scrollToTopOnChange = true
                    entryActions.onForgetRecent(entry)
                },
            )
        }
    }
    LaunchedEffect(results) {
        if (scrollToTopOnChange) {
            scrollToTopOnChange = false
            listState.scrollToItem(0)
            gridState.scrollToItem(0)
        }
    }
    var menuEntryId by remember { mutableStateOf<String?>(null) }
    val dimAlpha by animateFloatAsState(
        targetValue = if (menuEntryId != null) SearchConfig.MENU_DIMMED_ALPHA else 1f,
        animationSpec = tween(SearchConfig.MENU_DIM_DURATION_MS),
        label = "menuDim",
    )
    // Gone with search, and with its result (e.g. after "Remove from recents" or a re-index).
    LaunchedEffect(isHidden, isPicking) {
        if (isHidden || isPicking) menuEntryId = null
    }
    LaunchedEffect(results, menuEntryId) {
        val open = menuEntryId ?: return@LaunchedEffect
        val shown = results.frequent.any { it.id == open } || results.more.any { it.id == open } ||
            results.suggestions.any { group -> group.entries.any { it.id == open } }
        if (!shown) menuEntryId = null
    }
    // The last result to have its menu open, which stays fully lit while the rest fade back.
    var litEntryId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(menuEntryId) { menuEntryId?.let { litEntryId = it } }

    val focusManager = LocalFocusManager.current
    // Only once search has fully come in; a swipe that's still bringing it in may yet let go.
    // The overlay stays composed while hidden, so it lets go of the keyboard itself on the way out.
    // In landscape the keyboard would cover all the results, so search opens with them in view and
    // the keyboard comes up when the field is tapped.
    LaunchedEffect(isFullyIn) {
        if (isFullyIn && !isLandscape) {
            focusRequester.requestFocus()
            keyboard?.show()
        } else if (!isFullyIn) {
            focusManager.clearFocus()
        }
    }

    // Swiping down closes search the way it came in, in step with the finger. On the results
    // list that starts only once the list is scrolled to the top: it gets the drag first.
    val dismissScroll = remember(reveal) { reveal?.let(::SearchDismissScroll) }

    // The category slider the filter pill opens (see FilterPill). It closes with search, while
    // picking an app (no filter then), and once the query changes.
    val sliderState = remember { CategorySliderState() }
    LaunchedEffect(isHidden, isPicking, query) { sliderState.close() }
    // In landscape the keyboard covers nearly all the results, so scrolling them puts it away.
    val hideKeyboardOnScroll = remember(keyboard) { HideKeyboardOnScroll { keyboard?.hide() } }

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
        // The keyboard's own edge sets the field apart from it, so the gap under the field shrinks
        // while it's up, leaving the results that much more room.
        val imeVisible = WindowInsets.isImeVisible
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = SearchConfig.HorizontalPadding),
        ) {
            Spacer(Modifier.height(overlayTopSpacing()))
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
            // What Go acts on: the calc answer, which sits above everything else; then a quick action
            // the query can only mean ("timer 5m"), or the best-matching contact for a contact command
            // ("call ma": a call was asked for, so not a result that merely has "call" in it); then the
            // top result; then a free-text quick action ("maps coffee"), which may just be a search, so
            // only when nothing else matched. Its row is marked while something's typed, so it's clear
            // what Go will do.
            // The actions are worked out in the background; until they're for what's typed now, Go
            // waits for them ([goPending]) rather than run the last query's ("alarm 7" for "alarm 7:30").
            val actionsAreCurrent = quickActionsQuery == query.trim()
            val goTarget: GoTarget? = run {
                if (activeCalcResult != null) return@run GoTarget.Calc(activeCalcResult)
                val entry = results.firstOrNull()
                val action = activeQuickActions.firstOrNull()
                    ?.takeIf { it.action.isExact || it.action is QuickAction.ForContact || entry == null }
                when {
                    action != null -> GoTarget.Action(action)
                    entry != null -> GoTarget.Entry(entry)
                    else -> null
                }
            }
            val goKey = goTarget?.key?.takeIf { !isQueryBlank && !isPicking }
            // Go does what tapping [goTarget] would.
            val runGo = {
                when (val target = goTarget) {
                    is GoTarget.Calc -> onCalcResultSelected(target.result)
                    is GoTarget.Action -> runQuickAction(context, target.target, onQuickActionRun)
                    is GoTarget.Entry -> when {
                        isPicking -> onEntryPicked(target.entry)
                        context.launchSafely(target.entry, resources.getString(R.string.open_app, target.entry.name)) ->
                            onEntryOpened(target.entry)
                    }
                    null -> Unit
                }
            }
            var goPending by remember { mutableStateOf(false) }
            LaunchedEffect(goPending, actionsAreCurrent) {
                if (goPending && actionsAreCurrent) {
                    goPending = false
                    runGo()
                }
            }
            // A plain TextField with its fill and underline cleared, so the glass is the surface.
            // It keeps its own cursor: text set from outside (a command hint typing "timer ")
            // puts the cursor at the end, ready for the rest, rather than where it was.
            var fieldValue by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
            val shownValue = if (fieldValue.text == query) fieldValue else TextFieldValue(query, TextRange(query.length))
            val searchField = @Composable { fieldModifier: Modifier ->
                TextField(
                    value = shownValue,
                    onValueChange = { value ->
                        fieldValue = value
                        if (value.text != query) onQueryChange(value.text)
                    },
                    modifier = fieldModifier
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
                    placeholder = placeholder?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                    // With no placeholder, the icon names the field for screen readers (the field reads
                    // out what's inside it).
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = fieldLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            // Picking ignores quick actions, so it never needs to wait for them.
                            if (actionsAreCurrent || isPicking || activeCalcResult != null) runGo() else goPending = true
                        },
                    ),
                )
            }
            val layoutToggle = @Composable { toggleModifier: Modifier ->
                LayoutToggle(
                    category = categoryFilter,
                    asGrid = showGrid,
                    onChange = { onGridChange(categoryFilter, it) },
                    modifier = toggleModifier,
                )
            }
            // The field and the filter bar, under the results: on top of the keyboard while it's up,
            // at the bottom of the screen otherwise, where the swipe that opened search ended.
            val searchControls = @Composable { controlsModifier: Modifier ->
                Column(
                    modifier = controlsModifier
                        .fillMaxWidth()
                        .padding(bottom = if (imeVisible) SearchConfig.KeyboardGap else SearchConfig.BottomSpacing),
                ) {
                // One row: the field, then the filter pill and the grid/list toggle, or the mirror
                // image in left-handed mode.
                val filterPill = @Composable {
                    FilterPill(
                        state = sliderState,
                        categories = categories,
                        selected = categoryFilter,
                        onSelected = onCategoryFilterChange,
                    )
                }
                val controlsRow = @Composable { searchPart: @Composable RowScope.() -> Unit ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = CategoryFilterConfig.VerticalSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SearchConfig.LandscapeHeaderGap),
                    ) {
                        if (leftHanded) {
                            layoutToggle(Modifier)
                            filterPill()
                            searchPart()
                        } else {
                            searchPart()
                            filterPill()
                            layoutToggle(Modifier)
                        }
                    }
                }
                // The suggested filter chips, while something's typed. Picking an app for a button
                // has none. In landscape the keyboard leaves room for about a row of results, which
                // the chips would take, so they wait for it to go down (as the status line does).
                AnimatedVisibility(
                    visible = !isPicking && !isQueryBlank && !(isLandscape && imeVisible) &&
                        (searchKind != null || results.filterChips.isNotEmpty()),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    FilterChipsRow(
                        chips = results.filterChips,
                        kind = searchKind,
                        onPicked = onFilterChipPicked,
                        onCleared = onSearchKindCleared,
                        alignEnd = !leftHanded,
                        modifier = Modifier.padding(top = CategoryFilterConfig.VerticalSpacing),
                    )
                }
                if (showsCategoryBar) {
                    controlsRow { searchField(Modifier.weight(1f)) }
                } else {
                    Spacer(Modifier.height(SearchConfig.ResultsSpacing))
                    searchField(Modifier.fillMaxWidth())
                }
                }
            }
            // In landscape the keyboard leaves room for about a row of results; the status line would take it.
            if ((statusCount != null || statusHint != null) && !(isLandscape && imeVisible)) {
                IndexStatus(
                    count = statusCount,
                    summary = if (isQueryBlank) R.plurals.items_indexed else R.plurals.results_found,
                    hint = statusHint,
                    onTypeCommand = onQueryChange,
                )
            }
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
            // Landscape spreads the results over more columns: a wider grid, and a list in two
            // columns (laid out by the grid, each cell a list row).
            val entryContent: @Composable (SearchEntry) -> Unit = { entry ->
                if (showGrid) {
                    EntryGridCellContent(entry, contactPhotos, fileThumbnails, shortcutIcons)
                } else {
                    EntryRowContent(entry, contactPhotos, fileThumbnails, shortcutIcons)
                }
            }
            // A source that can't be read (permission not granted) leads its tab's results with a
            // button to grant it, scrolling with them, rather than asking on its own.
            val showsPermissionNotice = !isPicking && hasPermissionNotice(permissions, categoryFilter)
            val permissionNotice = @Composable {
                SearchPermissionNotice(
                    permissions = permissions,
                    categoryFilter = categoryFilter,
                    modifier = Modifier
                        .padding(bottom = SearchConfig.ResultsSpacing)
                        .alpha(dimAlpha),
                )
            }
            val resultItem: @Composable (SearchEntry) -> Unit = { entry ->
                EntryItem(
                    modifier = Modifier.goHighlight(entry.id == goKey),
                    entry = entry,
                    isPicking = isPicking,
                    onOpened = onEntryOpened,
                    onPicked = onEntryPicked,
                    actions = activeEntryActions,
                    menuEntryId = menuEntryId,
                    alpha = if (entry.id == (menuEntryId ?: litEntryId)) 1f else dimAlpha,
                    onMenuChange = { menuEntryId = it },
                ) {
                    entryContent(entry)
                }
            }
            // The results run on under the field and filter bar, which float over a blurred copy of
            // them that fades into the sharp ones above (see resultsBackdrop). Padding at the end of
            // the list lets the last results scroll up clear of the bars.
            val resultsLayer = rememberGraphicsLayer()
            val blurLayer = rememberGraphicsLayer()
            var controlsHeight by remember { mutableIntStateOf(0) }
            // Read while the blurred band draws, so it redraws when the results area changes height
            // (the keyboard coming or going) and lines its copy up with them again.
            var resultsHeight by remember { mutableIntStateOf(0) }
            // Where the results start on screen, for lining their blurred copy up behind the slider.
            val resultsOrigin = remember { RootPosition() }
            val sliderBackdrop = Modifier.drawBehind {
                // The slider floats over sharp results; it's frosted with the same blurred copy the
                // bars sit on (recorded into blurLayer as they draw, just before it), clipped to its
                // pill shape. Without blur (before Android 12), just the dark base.
                val pill = Path().apply {
                    addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(size.height / 2)))
                }
                clipPath(pill) {
                    // The sharp results are still drawn underneath (unlike under the bars, where they
                    // fade out), so a dark base covers them first; the blurred copy goes over it.
                    drawRect(Color.Black.copy(alpha = SearchConfig.SLIDER_BASE_ALPHA))
                    val bounds = sliderState.bounds
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && bounds != null && blurLayer.size != IntSize.Zero) {
                        translate(left = resultsOrigin.x - bounds.left, top = resultsOrigin.y - bounds.top) {
                            drawLayer(blurLayer)
                        }
                        drawRect(Color.Black.copy(alpha = SearchConfig.CONTROLS_BACKDROP_DIM))
                    }
                }
            }
            val density = LocalDensity.current
            val fadePx = with(density) { SearchConfig.ControlsFadeHeight.toPx() }
            val endPadding = with(density) { controlsHeight.toDp() } + SearchConfig.ResultsSpacing
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { resultsHeight = it.height }
                    .onGloballyPositioned { resultsOrigin.set(it.positionInRoot()) }
                    // Left open by a tap, the slider closes when anything else is touched. Watched on
                    // the way down (Initial) and left unconsumed, so that touch still does what it
                    // would (scrolls the results, focuses the field) instead of being spent closing it.
                    // The pill opens and closes the slider itself.
                    .pointerInput(sliderState) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            if (!sliderState.sticky) return@awaitEachGesture
                            val atRoot = down.position + Offset(resultsOrigin.x, resultsOrigin.y)
                            if (!sliderState.isOnSlider(atRoot)) sliderState.close()
                        }
                    },
            ) {
                val listModifier = Modifier
                    .fillMaxSize()
                    .resultsBackdrop(resultsLayer, sharpUntil = { it - controlsHeight - fadePx }, fadePx = fadePx)
                if (showGrid || isLandscape) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(
                            when {
                                !showGrid -> SearchConfig.LIST_COLUMNS_LANDSCAPE
                                isLandscape -> SearchConfig.GRID_COLUMNS_LANDSCAPE
                                else -> SearchConfig.GRID_COLUMNS
                            },
                        ),
                        state = gridState,
                        userScrollEnabled = menuEntryId == null,
                        contentPadding = PaddingValues(bottom = endPadding),
                        modifier = listModifier
                            // In landscape the keyboard leaves room for little more than a row of
                            // results, so scrolling them puts it away to show the rest.
                            .then(if (isLandscape) Modifier.nestedScroll(hideKeyboardOnScroll) else Modifier),
                    ) {
                        if (showsPermissionNotice) {
                            item(key = PERMISSION_NOTICE_KEY, span = { GridItemSpan(maxLineSpan) }) { permissionNotice() }
                        }
                        if (activeCalcResult != null) {
                            item(key = CALC_RESULT_KEY, span = { GridItemSpan(maxLineSpan) }) {
                                CalcResultRow(
                                    query = query,
                                    result = activeCalcResult,
                                    onCopy = onCalcResultSelected,
                                    enabled = menuEntryId == null,
                                    modifier = Modifier.alpha(dimAlpha).goHighlight(goKey == CALC_RESULT_KEY),
                                )
                            }
                        }
                        activeQuickActions.forEachIndexed { index, target ->
                            item(key = quickActionKey(index), span = { GridItemSpan(maxLineSpan) }) {
                                QuickActionRow(
                                    target = target,
                                    onRun = { runQuickAction(context, it, onQuickActionRun) },
                                    enabled = menuEntryId == null,
                                    modifier = Modifier.alpha(dimAlpha).goHighlight(goKey == quickActionKey(index)),
                                )
                            }
                        }
                        items(results.frequent, key = { it.id }) { entry ->
                            resultItem(entry)
                        }
                        if (showsMoreLabel) {
                            item(key = MORE_RESULTS_KEY, span = { GridItemSpan(maxLineSpan) }) {
                                SectionLabel(stringResource(R.string.more_results), Modifier.alpha(dimAlpha))
                            }
                        }
                        items(results.more, key = { it.id }) { entry ->
                            resultItem(entry)
                        }
                        results.suggestions.forEach { group ->
                            item(key = suggestionsKey(group.kind), span = { GridItemSpan(maxLineSpan) }) {
                                SectionLabel(stringResource(group.kind.label), Modifier.alpha(dimAlpha))
                            }
                            items(group.entries, key = { it.id }) { entry ->
                                resultItem(entry)
                            }
                        }
                        if (results.isEmpty && activeCalcResult == null && activeQuickActions.isEmpty() && !results.isSearchingDisk && query.isNotBlank()) {
                            item(key = NO_RESULTS_KEY, span = { GridItemSpan(maxLineSpan) }) {
                                SectionLabel(stringResource(R.string.no_results), Modifier.alpha(dimAlpha))
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = listModifier,
                        contentPadding = PaddingValues(bottom = endPadding),
                        userScrollEnabled = menuEntryId == null,
                    ) {
                        if (showsPermissionNotice) {
                            item(key = PERMISSION_NOTICE_KEY) { permissionNotice() }
                        }
                        if (activeCalcResult != null) {
                            item(key = CALC_RESULT_KEY) {
                                CalcResultRow(
                                    query = query,
                                    result = activeCalcResult,
                                    onCopy = onCalcResultSelected,
                                    enabled = menuEntryId == null,
                                    modifier = Modifier.alpha(dimAlpha).goHighlight(goKey == CALC_RESULT_KEY),
                                )
                            }
                        }
                        activeQuickActions.forEachIndexed { index, target ->
                            item(key = quickActionKey(index)) {
                                QuickActionRow(
                                    target = target,
                                    onRun = { runQuickAction(context, it, onQuickActionRun) },
                                    enabled = menuEntryId == null,
                                    modifier = Modifier.alpha(dimAlpha).goHighlight(goKey == quickActionKey(index)),
                                )
                            }
                        }
                        items(results.frequent, key = { it.id }) { entry ->
                            resultItem(entry)
                        }
                        if (showsMoreLabel) {
                            item(key = MORE_RESULTS_KEY) { SectionLabel(stringResource(R.string.more_results), Modifier.alpha(dimAlpha)) }
                        }
                        items(results.more, key = { it.id }) { entry ->
                            resultItem(entry)
                        }
                        results.suggestions.forEach { group ->
                            item(key = suggestionsKey(group.kind)) { SectionLabel(stringResource(group.kind.label), Modifier.alpha(dimAlpha)) }
                            items(group.entries, key = { it.id }) { entry ->
                                resultItem(entry)
                            }
                        }
                        if (results.isEmpty && activeCalcResult == null && activeQuickActions.isEmpty() && !results.isSearchingDisk && query.isNotBlank()) {
                            item(key = NO_RESULTS_KEY) { SectionLabel(stringResource(R.string.no_results), Modifier.alpha(dimAlpha)) }
                        }
                    }
                }
                if (controlsHeight > 0) {
                    BlurredBackdrop(
                        source = resultsLayer,
                        blurLayer = blurLayer,
                        sourceHeight = { resultsHeight },
                        fadePx = fadePx,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(with(density) { (controlsHeight + fadePx).toDp() }),
                    )
                }
                searchControls(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { controlsHeight = it.height },
                )
                if (showsCategoryBar) {
                    CategorySlider(
                        state = sliderState,
                        categories = categories,
                        selected = categoryFilter,
                        onSelected = onCategoryFilterChange,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = with(density) { controlsHeight.toDp() } + SearchConfig.SliderGap),
                        barBackdrop = sliderBackdrop,
                    )
                }
            }
        }
    }
}

/** A position in root coordinates, kept outside composition state: read only while drawing. */
private class RootPosition {
    var x = 0f
        private set
    var y = 0f
        private set

    fun set(position: Offset) {
        x = position.x
        y = position.y
    }
}

/**
 * Records the results into [layer] (for [BlurredBackdrop] to blur), then draws them fading out from
 * [sharpUntil] (given the results' height, where the fade starts) over [fadePx]: below that they're
 * only seen blurred, behind the field and filter bar. Without blur (before Android 12) they simply
 * fade out there.
 */
private fun Modifier.resultsBackdrop(layer: GraphicsLayer, sharpUntil: (Float) -> Float, fadePx: Float): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
        val fadeStart = sharpUntil(size.height).coerceIn(0f, size.height)
        val fadeEnd = (fadeStart + fadePx).coerceAtMost(size.height)
        if (fadeStart >= size.height) return@drawWithContent
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black,
                fadeStart / size.height to Color.Black,
                fadeEnd / size.height to Color.Transparent,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * A blurred copy of the results (recorded in [source], [sourceHeight] tall, which end where this does),
 * fading in over its top [fadePx]: the frosted backing of the field and filter bar. It runs to the
 * screen's edges, past search's side margins and down behind the navigation bar, so the blur fades out
 * there rather than stopping in a straight line. Android 12+ only; before that, nothing (the results
 * fade out instead).
 */
@Composable
private fun BlurredBackdrop(
    source: GraphicsLayer,
    blurLayer: GraphicsLayer,
    sourceHeight: () -> Int,
    fadePx: Float,
    modifier: Modifier = Modifier,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val radius = with(LocalDensity.current) { SearchConfig.ControlsBlurRadius.toPx() }
    var margin by remember { mutableIntStateOf(0) }
    // Search stops above the navigation bar (or the keyboard, which then covers this extra anyway).
    val below = WindowInsets.navigationBars.getBottom(LocalDensity.current)
    Box(
        modifier = modifier
            // Out to the screen's edges: across both side margins, centred on the results, and down
            // behind the navigation bar.
            .layout { measurable, constraints ->
                margin = SearchConfig.HorizontalPadding.roundToPx()
                val width = constraints.maxWidth + 2 * margin
                val height = constraints.maxHeight + below
                val placeable = measurable.measure(
                    constraints.copy(minWidth = width, maxWidth = width, minHeight = height, maxHeight = height),
                )
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-margin, 0) }
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                // The results above this band, whose own top is that much further up.
                // (The part behind the navigation bar hangs below the results' end, so it's left out.)
                val top = sourceHeight() - (size.height - below)
                if (blurLayer.renderEffect == null) {
                    // Decal, not clamp: past the results' edges the blur fades out rather than smearing them.
                    blurLayer.renderEffect = BlurEffect(radius, radius, TileMode.Decal)
                }
                blurLayer.record(source.size) { drawLayer(source) }
                translate(left = margin.toFloat(), top = -top) { drawLayer(blurLayer) }
                // Toned down a little, so bright icons blurred behind the field don't wash out its text.
                drawRect(Color.Black.copy(alpha = SearchConfig.CONTROLS_BACKDROP_DIM))
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        (fadePx / size.height).coerceIn(0f, 1f) to Color.Black,
                        1f to Color.Black,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    )
}

/** Calls [hide] (to put the keyboard away) as soon as the finger scrolls the results. */
private class HideKeyboardOnScroll(private val hide: () -> Unit) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y != 0f) hide()
        return Offset.Zero
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
private const val PERMISSION_NOTICE_KEY = "section-permission"

private fun quickActionKey(index: Int) = "section-quick-$index"

/** What Go acts on (see `goTarget` in [SearchOverlay]), with the list key of its row. */
private sealed interface GoTarget {
    val key: String

    data class Calc(val result: String) : GoTarget {
        override val key: String get() = CALC_RESULT_KEY
    }

    /** Always the first quick action, as only that one is ever Go's. */
    data class Action(val target: QuickActionTarget) : GoTarget {
        override val key: String get() = quickActionKey(0)
    }

    data class Entry(val entry: SearchEntry) : GoTarget {
        override val key: String get() = entry.id
    }
}

/** A faint rounded backdrop marking the row Go would act on, when [on]. */
private fun Modifier.goHighlight(on: Boolean): Modifier =
    if (on) {
        background(
            ThemeConfig.Foreground.copy(alpha = SearchConfig.GO_TARGET_ALPHA),
            RoundedCornerShape(SearchConfig.GoTargetCornerRadius),
        )
    } else {
        this
    }

/**
 * Starts [target]'s action in its app; once it's started, [onRun] (search closes). Timers and alarms
 * are set without the clock opening, so a toast says it worked.
 */
private fun runQuickAction(context: Context, target: QuickActionTarget, onRun: () -> Unit) {
    val is24Hour = DateFormat.is24HourFormat(context)
    val title = quickActionTitle(context.resources, target.action, is24Hour)
    if (!context.launchSafely(target.action.intent, title)) return
    val done = when (val action = target.action) {
        is QuickAction.Timer ->
            context.getString(R.string.quick_timer_set, formatDuration(context.resources, action.seconds))
        is QuickAction.Alarm ->
            context.getString(R.string.quick_alarm_set, formatClockTime(action.hour, action.minute, is24Hour))
        else -> null
    }
    if (done != null) Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
    onRun()
}

private fun suggestionsKey(kind: SuggestionKind) = "section-suggested-${kind.name}"

private val SuggestionKind.label: Int
    get() = when (this) {
        SuggestionKind.FAVOURITES -> R.string.favourites
        SuggestionKind.RECENTLY_UPDATED -> R.string.recently_updated
        SuggestionKind.RECENTLY_ADDED -> R.string.recently_added
        SuggestionKind.UPCOMING -> R.string.upcoming
    }

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = ThemeConfig.Foreground.copy(alpha = SearchConfig.SECTION_LABEL_ALPHA),
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .padding(SearchConfig.SectionLabelPadding)
            .semantics { heading() },
    )
}

/** What the status line says beside its count. */
private sealed interface StatusHint {
    /** A tip for the tab showing, while nothing's typed. */
    data class Tip(val text: String) : StatusHint

    /**
     * How to finish the command being typed ("tim" → "timer 10m"), for each of [commands] (all
     * with the same word); tapping it types the word.
     */
    data class Command(val commands: List<QuickCommand>) : StatusHint
}

/**
 * "41,136 items indexed": how many entries the index holds for the current tab, so it's clear
 * search has everything it should. With a query, [summary] is [R.plurals.results_found] and
 * [count] is the matches listed: "5 results found". Beside it, [hint], if any, wrapping to a second
 * line rather than being cut short; a command hint is tapped to type its word ([onTypeCommand]).
 * With no count (nothing found yet), just the hint.
 */
@Composable
private fun IndexStatus(
    count: Int?,
    @PluralsRes summary: Int,
    hint: StatusHint?,
    onTypeCommand: (String) -> Unit,
) {
    val color = ThemeConfig.Foreground.copy(alpha = SearchConfig.SECTION_LABEL_ALPHA)
    val style = MaterialTheme.typography.labelMedium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SearchConfig.SectionLabelPadding)
            .padding(bottom = SearchConfig.ResultsSpacing),
        horizontalArrangement = Arrangement.spacedBy(SearchConfig.StatusHintGap),
    ) {
        if (count != null) {
            Text(
                text = pluralStringResource(summary, count, NumberFormat.getIntegerInstance().format(count)),
                color = color,
                style = style,
                maxLines = 1,
            )
        }
        when (hint) {
            is StatusHint.Tip -> Text(
                text = if (count != null) stringResource(R.string.status_hint_beside, hint.text) else hint.text,
                color = color,
                style = style,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            is StatusHint.Command -> {
                val command = hint.commands.first()
                val text = hint.commands
                    .map { stringResource(R.string.status_hint_command, stringResource(it.descriptionRes), it.example) }
                    .joinToString(HINT_SEPARATOR)
                Text(
                    text = if (count != null) stringResource(R.string.status_hint_beside, text) else text,
                    color = ThemeConfig.Foreground,
                    style = style,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClickLabel = stringResource(R.string.hint_type_command, command.word)) {
                            onTypeCommand(command.word + " ")
                        },
                )
            }
            null -> Unit
        }
    }
}

/**
 * The tips the status line picks from on [tab] (null for "All"). On "All", those about commands are
 * only offered when some app here can carry them out ([supported]): no calling tips without a dialer.
 */
@Composable
private fun tabHints(tab: SearchCategory?, supported: List<QuickCommand>): List<String> {
    val tips = stringArrayResource(tab.hintsRes).toList()
    if (tab != null) return tips
    val commandTips = listOfNotNull(
        stringResource(R.string.search_hint_timer).takeIf { QuickCommand.TIMER in supported },
        stringResource(R.string.search_hint_call_contact).takeIf { QuickCommand.CALL in supported },
        stringResource(R.string.search_hint_call_number).takeIf { QuickCommand.CALL in supported },
    )
    return tips + commandTips
}

/** The tips the status line picks from on this tab (null for "All"). */
@get:ArrayRes
private val SearchCategory?.hintsRes: Int
    get() = when (this?.tab) {
        null -> R.array.search_hints_all
        SearchCategory.CONTACT -> R.array.search_hints_contacts
        SearchCategory.EVENT -> R.array.search_hints_events
        SearchCategory.FILE, SearchCategory.FOLDER -> R.array.search_hints_files
        SearchCategory.APP, SearchCategory.SHORTCUT, SearchCategory.SETTING -> R.array.search_hints_apps
    }

private const val HINT_SEPARATOR = " · "

/** Tips are picked as this seed modulo the tab's count, so each tab gets its own, stable while in view. */
private const val HINT_SEED_RANGE = 1_000

/**
 * Where search's permissions stand, and what to do about them. Each tab whose source can't be read
 * says so in a [PermissionNotice] with a link to grant it. "All" says nothing about them: it searches
 * whatever can be read.
 */
data class SearchPermissions(
    val contacts: AccessState = AccessState.GRANTED,
    val calendar: AccessState = AccessState.GRANTED,
    val files: AccessState = AccessState.GRANTED,
    val onRequestContacts: () -> Unit = {},
    val onRequestCalendar: () -> Unit = {},
    val onRequestFiles: () -> Unit = {},
)

/** Whether [SearchPermissionNotice] has anything to show on the [categoryFilter] tab. */
private fun hasPermissionNotice(permissions: SearchPermissions, categoryFilter: SearchCategory?): Boolean =
    when (categoryFilter) {
        SearchCategory.CONTACT -> !permissions.contacts.isGranted
        SearchCategory.EVENT -> !permissions.calendar.isGranted
        SearchCategory.FILE -> !permissions.files.isGranted
        // "All" and the Apps tab need no permission of their own (folders go with files).
        null, SearchCategory.APP, SearchCategory.SHORTCUT, SearchCategory.FOLDER, SearchCategory.SETTING -> false
    }

/** The permission notice for the [categoryFilter] tab, if its source can't be read. */
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
        SearchCategory.EVENT -> if (!permissions.calendar.isGranted) {
            val denied = permissions.calendar == AccessState.DENIED
            PermissionNotice(
                icon = SearchCategory.EVENT.icon,
                message = stringResource(
                    if (denied) R.string.permission_calendar_denied else R.string.permission_calendar_not_requested,
                ),
                actionLabel = stringResource(if (denied) R.string.permission_action_turn_on else R.string.permission_action_allow),
                onAction = permissions.onRequestCalendar,
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
        // "All" has none; the Apps tab needs no permission, and folders go with the Files tab's notice.
        null, SearchCategory.APP, SearchCategory.SHORTCUT, SearchCategory.FOLDER, SearchCategory.SETTING -> Unit
    }
}

/**
 * A result, drawn by [content] (a list row or a grid cell): tapping opens the entry, or, when
 * [isPicking], chooses it instead. Holding it opens its [SearchEntryMenu] (when it has one) with
 * a buzz, through [onMenuChange]; [menuEntryId] is the result whose menu is open, if any. While
 * another result's menu is open this one takes no taps. It's drawn at [alpha], which the overlay
 * lowers for every other result while a menu is open.
 */
@Composable
private fun EntryItem(
    entry: SearchEntry,
    isPicking: Boolean,
    onOpened: (SearchEntry) -> Unit,
    onPicked: (SearchEntry) -> Unit,
    actions: SearchEntryActions,
    menuEntryId: String?,
    alpha: Float,
    onMenuChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (isPicking) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .ignoreLongPress()
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
            launchDirectly = entry::launchDirectly,
            actionLabel = label,
        )
    }
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptics = remember(context) { Haptics(context) }
    val menuOpen = menuEntryId == entry.id
    val dimmed = menuEntryId != null && !menuOpen
    val optionsLabel = stringResource(R.string.entry_options, entry.name)
    val hasMenu = hasEntryMenu(entry, actions)
    Box(modifier = modifier.fillMaxWidth().alpha(alpha)) {
        ClickToLaunch(
            element,
            // Without a menu, holding a result does nothing, rather than opening it on release.
            modifier = Modifier.fillMaxWidth().then(if (hasMenu) Modifier else Modifier.ignoreLongPress()),
            enabled = !dimmed,
            onLaunched = {
                onOpened(entry)
                // An APK opens the file manager rather than the installer; say what to tap there.
                if (entry is FileEntry && entry.isApk) {
                    Toast.makeText(context, apkInstallNote(resources, entry), Toast.LENGTH_LONG).show()
                }
            },
            onLongClickLabel = optionsLabel,
            onLongClick = if (hasMenu) {
                {
                    haptics.longPress()
                    onMenuChange(entry.id)
                }
            } else {
                null
            },
        )
        SearchEntryMenu(
            entry = entry,
            expanded = menuOpen,
            actions = actions,
            onDismiss = { onMenuChange(null) },
            onOpened = onOpened,
        )
    }
}

private fun apkInstallNote(resources: Resources, file: FileEntry): String =
    file.detail?.let { resources.getString(R.string.apk_install_from_folder, file.name, it) }
        ?: resources.getString(R.string.apk_install_from_file_manager, file.name)

@Composable
private fun EntryRowContent(
    entry: SearchEntry,
    contactPhotos: ContactPhotoCache?,
    fileThumbnails: FileThumbnailCache?,
    shortcutIcons: ShortcutIconCache?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(SearchConfig.ResultRowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SearchConfig.ResultIconTextGap),
    ) {
        EntryIcon(entry, contactPhotos, fileThumbnails, shortcutIcons)
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
                    overflow = entry.detailOverflow,
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
private fun EntryGridCellContent(
    entry: SearchEntry,
    contactPhotos: ContactPhotoCache?,
    fileThumbnails: FileThumbnailCache?,
    shortcutIcons: ShortcutIconCache?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(SearchConfig.GridCellPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SearchConfig.GridIconTextGap),
    ) {
        EntryIcon(entry, contactPhotos, fileThumbnails, shortcutIcons, size = SearchConfig.GridIconSize)
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
            // An event's place and end would crowd out its start in a cell this narrow, so just the
            // start shows, with a pin standing in for the place (the list shows both in full).
            if (entry is EventEntry && SearchConfig.SHOW_DETAIL_IN_GRID) {
                EventGridDetail(entry)
                return@Column
            }
            // One line, shortened the same way as a list row's: whole when it fits, else as much of
            // the end as fits.
            entry.detail?.takeIf { SearchConfig.SHOW_DETAIL_IN_GRID }?.let { detail ->
                Text(
                    text = detail,
                    color = ThemeConfig.Foreground.copy(alpha = SearchConfig.RESULT_DETAIL_ALPHA),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = entry.detailOverflow,
                )
            }
        }
    }
}

/**
 * Where a result's secondary line is cut when it doesn't fit: from the start for a file's folder (the
 * nearest folders matter most), from the end for an event (its day and time come first).
 */
private val SearchEntry.detailOverflow: TextOverflow
    get() = if (this is EventEntry) TextOverflow.Ellipsis else TextOverflow.StartEllipsis

/** A grid cell's line for [event]: when it starts, then a small pin if it has a location. */
@Composable
private fun EventGridDetail(event: EventEntry) {
    val color = ThemeConfig.Foreground.copy(alpha = SearchConfig.RESULT_DETAIL_ALPHA)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SearchConfig.EventPinGap),
    ) {
        Text(
            text = event.startTime,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (event.location != null) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = stringResource(R.string.event_has_location, event.location),
                tint = color,
                modifier = Modifier.size(SearchConfig.EventPinSize),
            )
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
                    // Shortcuts have no tab of their own; they're in the apps'.
                    SearchCategory.APP, SearchCategory.SHORTCUT, SearchCategory.SETTING -> R.string.show_apps_as_grid
                    SearchCategory.CONTACT -> R.string.show_contacts_as_grid
                    SearchCategory.EVENT -> R.string.show_events_as_grid
                    SearchCategory.FILE, SearchCategory.FOLDER -> R.string.show_files_as_grid
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
private fun CalcResultRow(
    query: String,
    result: String,
    onCopy: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.copy_calc_result, result)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClickLabel = label) { onCopy(result) }
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
 * The entry's own image (app icon, contact photo, file thumbnail, shortcut icon), or its category
 * icon when it has none. A contact's photo loads from [contactPhotos], a file's thumbnail from
 * [fileThumbnails], and a shortcut's icon from [shortcutIcons], as it's shown; until then (or if
 * there's none) the category icon stands in.
 */
@Composable
private fun EntryIcon(
    entry: SearchEntry,
    contactPhotos: ContactPhotoCache?,
    fileThumbnails: FileThumbnailCache?,
    shortcutIcons: ShortcutIconCache?,
    size: Dp = SearchConfig.ResultIconSize,
) {
    val icon = entry.icon
        ?: rememberContactPhoto(entry, contactPhotos)
        ?: rememberFileThumbnail(entry, fileThumbnails)
        ?: rememberShortcutIcon(entry, shortcutIcons)
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
 * [entry]'s app shortcut icon: straight away when it's in memory, else once [shortcutIcons] has
 * loaded it in the background. Null for anything that isn't a shortcut with an icon.
 */
@Composable
private fun rememberShortcutIcon(entry: SearchEntry, shortcutIcons: ShortcutIconCache?): Drawable? {
    val shortcut = entry as? ShortcutEntry
    var icon by remember(shortcut, shortcutIcons) { mutableStateOf(shortcut?.let { shortcutIcons?.cached(it) }) }
    LaunchedEffect(shortcut, shortcutIcons) {
        if (icon == null && shortcut != null && shortcutIcons != null) icon = shortcutIcons.load(shortcut)
    }
    return icon
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
