package com.example.halauncher.ui.home

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import com.example.halauncher.R
import com.example.halauncher.config.CategoryFilterConfig
import com.example.halauncher.config.SearchConfig
import com.example.halauncher.config.ThemeConfig
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.ui.components.FilterIcon
import com.example.halauncher.ui.components.liquidGlass
import com.example.halauncher.util.Haptics
import kotlinx.coroutines.delay

/**
 * Whether the category slider is showing, and how: [held] while a finger is down on the filter pill
 * (it closes as the finger lifts), or [sticky] after a plain tap (it stays until a category is picked
 * or something else is tapped). Shared by [FilterPill], which opens it and picks from it while held,
 * and [CategorySlider], which shows it.
 */
@Stable
class CategorySliderState {
    var held by mutableStateOf(false)
        private set
    var sticky by mutableStateOf(false)
        private set
    val isOpen: Boolean get() = held || sticky

    /** Bumped on each pick while [sticky], so the slider can close once picking stops. */
    var stickyPicks by mutableIntStateOf(0)
        private set

    /** Where the slider is, in root coordinates, and how wide each of its segments is. */
    internal var bounds: Rect? = null
    internal var segmentCount: Int = 0

    internal fun press() {
        held = true
    }

    /** The finger lifted: after a slide the slider closes; after a tap it stays ([sticky]), or closes if it already was. */
    internal fun release(slid: Boolean, wasSticky: Boolean) {
        held = false
        sticky = !slid && !wasSticky
    }

    internal fun pickedWhileSticky() {
        stickyPicks++
    }

    fun close() {
        held = false
        sticky = false
    }

    /** The segment under [rootX], a position in root coordinates, or null while the slider isn't laid out. */
    internal fun segmentAt(rootX: Float): Int? {
        val rect = bounds ?: return null
        if (segmentCount == 0 || rect.width <= 0f) return null
        val index = ((rootX - rect.left) / (rect.width / segmentCount)).toInt()
        return index.coerceIn(0, segmentCount - 1)
    }
}

/**
 * The filter button beside the search field: a filter icon and the current tab's name, with a dot per
 * tab under it (the current one lit), hinting that swiping sideways anywhere in search changes tabs.
 * It's as wide as the longest tab name, so it doesn't change size as the tab changes.
 *
 * Pressing it shows the category slider ([CategorySlider]) above; sliding the finger along picks
 * categories as it passes them, with a tick each, and lifting closes the slider. A plain tap opens
 * the slider and leaves it open to tap a category in; tapping the pill again closes it.
 */
@Composable
fun FilterPill(
    state: CategorySliderState,
    categories: List<SearchCategory>,
    selected: SearchCategory?,
    onSelected: (SearchCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val segments: List<SearchCategory?> = remember(categories) { listOf(null) + categories }
    val currentSegments by rememberUpdatedState(segments)
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelected by rememberUpdatedState(onSelected)
    val context = LocalContext.current
    val haptics = remember(context) { Haptics(context) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    // As wide as the widest tab name (in this language) needs, so the pill keeps its size.
    val labelStyle = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val names = segments.map { stringResource(it.tabLabelRes) }
    val density = LocalDensity.current
    val labelWidth = remember(names, labelStyle, density) {
        with(density) { names.maxOf { measurer.measure(it, labelStyle.copy(fontWeight = FontWeight.Medium)).size.width }.toDp() }
    }

    val label = stringResource(R.string.filter_pill_description, stringResource(selected.tabLabelRes))
    val openLabel = stringResource(R.string.filter_pill_open)
    val showLabels = segments.map { stringResource(R.string.filter_show, stringResource(it.tabLabelRes)) }
    Row(
        modifier = modifier
            .height(SearchConfig.FilterPillHeight)
            .liquidGlass(RoundedCornerShape(percent = 50), tint = SearchConfig.FIELD_GLASS_TINT)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val wasSticky = state.sticky
                    state.press()
                    var slid = false
                    var last = currentSegments.indexOf(currentSelected)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        if (!slid && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) slid = true
                        if (slid) {
                            val index = state.segmentAt(origin.x + change.position.x)
                            if (index != null && index != last) {
                                last = index
                                haptics.tick()
                                currentOnSelected(currentSegments[index])
                            }
                        }
                        change.consume()
                    }
                    state.release(slid, wasSticky)
                }
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
                onClick(openLabel) {
                    if (state.sticky) state.close() else state.release(slid = false, wasSticky = false)
                    true
                }
                // Screen readers can't press and slide: each tab is an action of its own.
                customActions = segments.mapIndexed { i, category ->
                    CustomAccessibilityAction(showLabels[i]) {
                        onSelected(category)
                        true
                    }
                }
            }
            .padding(horizontal = SearchConfig.FilterPillPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SearchConfig.FilterPillGap),
    ) {
        Icon(
            imageVector = FilterIcon,
            contentDescription = null,
            tint = ThemeConfig.Foreground,
            modifier = Modifier.size(SearchConfig.FilterPillIconSize),
        )
        Column(
            modifier = Modifier.width(labelWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SearchConfig.FilterPillDotsGap),
        ) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = { fadeIn(tween(LABEL_FADE_MS)) togetherWith fadeOut(tween(LABEL_FADE_MS)) },
                label = "filterPillLabel",
            ) { category ->
                Text(
                    text = stringResource(category.tabLabelRes),
                    color = ThemeConfig.Foreground,
                    style = labelStyle,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
            TabDots(count = segments.size, current = segments.indexOf(selected).coerceAtLeast(0))
        }
    }
}

/** A dot per tab, the [current] one lit: the hint that swiping sideways moves between them. */
@Composable
private fun TabDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(SearchConfig.FilterDotGap)) {
        repeat(count) { i ->
            Box(
                modifier = Modifier
                    .size(SearchConfig.FilterDotSize)
                    .background(
                        ThemeConfig.Foreground.copy(alpha = if (i == current) 1f else SearchConfig.FILTER_DOT_DIM_ALPHA),
                        CircleShape,
                    ),
            )
        }
    }
}

/**
 * The category slider: the glass segmented bar, fading (and growing slightly) in while [state] is
 * open, out when it closes. [barBackdrop] draws behind the bar itself (search frosts it with the
 * blurred results, like its other bars). Held open, the filter pill drives it; open after a tap, its own segments
 * take taps and drags, and it closes a moment after the last pick. It's centred and narrows its
 * segments if the screen is too narrow for them.
 */
@Composable
fun CategorySlider(
    state: CategorySliderState,
    categories: List<SearchCategory>,
    selected: SearchCategory?,
    onSelected: (SearchCategory?) -> Unit,
    modifier: Modifier = Modifier,
    barBackdrop: Modifier = Modifier,
) {
    LaunchedEffect(state.stickyPicks) {
        if (state.stickyPicks == 0) return@LaunchedEffect
        delay(SearchConfig.SLIDER_CLOSE_AFTER_PICK_MS)
        state.close()
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val segments = categories.size + 1
        state.segmentCount = segments
        AnimatedVisibility(
            visible = state.isOpen,
            enter = fadeIn(tween(SearchConfig.SLIDER_FADE_MS)) + scaleIn(tween(SearchConfig.SLIDER_FADE_MS), initialScale = SLIDER_INITIAL_SCALE),
            exit = fadeOut(tween(SearchConfig.SLIDER_FADE_MS)) + scaleOut(tween(SearchConfig.SLIDER_FADE_MS), targetScale = SLIDER_INITIAL_SCALE),
        ) {
            CategoryFilterBar(
                categories = categories,
                selected = selected,
                onSelected = { category ->
                    onSelected(category)
                    // Picked in the slider itself (it was left open by a tap): close once picking stops.
                    if (state.sticky) state.pickedWhileSticky()
                },
                segmentWidth = minOf(CategoryFilterConfig.SegmentWidth, maxWidth / segments),
                modifier = barBackdrop.onGloballyPositioned { state.bounds = it.boundsInRoot() },
            )
        }
    }
}

/** The name of the tab a category is listed under (null for "All"), as the filter pill shows it. */
@get:StringRes
private val SearchCategory?.tabLabelRes: Int
    get() = when (this?.tab) {
        null -> R.string.category_all
        SearchCategory.CONTACT -> R.string.tab_contacts
        SearchCategory.EVENT -> R.string.tab_events
        SearchCategory.FILE, SearchCategory.FOLDER -> R.string.tab_files
        SearchCategory.APP, SearchCategory.SHORTCUT, SearchCategory.SETTING -> R.string.tab_apps
    }

private const val LABEL_FADE_MS = 150

/** How small the slider starts as it fades in (and ends as it fades out). */
private const val SLIDER_INITIAL_SCALE = 0.92f
