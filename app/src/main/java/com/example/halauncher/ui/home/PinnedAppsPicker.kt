package com.example.halauncher.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import com.example.halauncher.R
import com.example.halauncher.config.PinPickerConfig
import com.example.halauncher.config.SearchConfig
import com.example.halauncher.config.ThemeConfig
import com.example.halauncher.data.PinnedAppsStore
import com.example.halauncher.model.AppInfo
import com.example.halauncher.model.SearchEntry
import com.example.halauncher.model.ShortcutEntry
import com.example.halauncher.ui.components.isLandscape
import com.example.halauncher.ui.components.liquidGlass
import com.example.halauncher.ui.components.overlayTopSpacing
import com.example.halauncher.ui.components.rememberDrawablePainter
import com.example.halauncher.util.Haptics

/**
 * Every installed app with a pin toggle; the pinned ones appear on the home-screen sphere.
 * What's pinned is listed first, in sphere order, and can be dragged by its handle to reorder it:
 * apps, and shortcuts added through "Add to home screen" (which leave the list once unpinned, and
 * come back by adding them from their app again). The other apps follow A–Z. Changes save as
 * they're made. Once [PinPickerConfig.MAX_PINNED] are pinned, unpinned apps are disabled until one
 * is unpinned.
 *
 * With [mirrored] (left-handed mode), each row is flipped: drag handle and checkbox on the left,
 * name and icon on the right.
 *
 * @param pinned what's pinned and can be shown, in pin order (see HomeViewModel.pinnedItems).
 * @param onReorder the pins' keys (PinnedAppsStore.keyOf) in their new order.
 */
@Composable
fun PinnedAppsPicker(
    apps: List<AppInfo>,
    pinned: List<SearchEntry>,
    onToggle: (SearchEntry) -> Unit,
    onReorder: (List<String>) -> Unit,
    onDone: () -> Unit,
    mirrored: Boolean = false,
) {
    val pinnedApps = pinned
    val otherApps = remember(apps, pinned) {
        val pinnedKeys = pinned.mapTo(HashSet(), ::pinKey)
        apps.filter { it.key !in pinnedKeys }
    }
    val pinnedCount = pinnedApps.size
    val isFull = pinnedCount >= PinPickerConfig.MAX_PINNED

    val context = LocalContext.current
    val haptics = remember(context) { Haptics(context) }
    // Two columns in landscape, where one wide row per app would leave a gap between the name and its
    // checkbox; one in portrait.
    val columns = if (isLandscape()) 2 else 1
    val gridState = rememberLazyGridState()
    val reorder = remember(gridState) { PinReorderState(gridState, onMove = haptics::tick) }
    // Read from the long-lived drag gesture, so always the latest.
    val currentPinnedApps by rememberUpdatedState(pinnedApps)
    val currentOnReorder by rememberUpdatedState(onReorder)
    // The drag's arrangement stays on screen until the saved order catches up with it.
    LaunchedEffect(pinnedApps) {
        if (!reorder.isDragging) reorder.order = null
    }
    val shownPinned = reorder.order ?: pinnedApps

    val startDrag = { app: SearchEntry ->
        if (reorder.start(pinKey(app), currentPinnedApps)) haptics.longPress()
    }
    val endDrag = {
        val order = reorder.end()
        if (order != null && order != currentPinnedApps) {
            currentOnReorder(order.map(::pinKey))
        } else {
            reorder.order = null
        }
    }
    val moveBy = { index: Int, step: Int ->
        val target = index + step
        if (target in pinnedApps.indices) {
            val moved = pinnedApps.toMutableList().apply { add(target, removeAt(index)) }
            onReorder(moved.map(::pinKey))
        }
    }
    val moveUpLabel = stringResource(R.string.move_up)
    val moveDownLabel = stringResource(R.string.move_down)

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
            Spacer(Modifier.height(overlayTopSpacing()))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(RoundedCornerShape(percent = 50), tint = SearchConfig.FIELD_GLASS_TINT)
                    .padding(start = PinPickerConfig.HeaderStartPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.pinned_apps),
                        color = ThemeConfig.Foreground,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = pluralStringResource(
                            if (isFull) R.plurals.pinned_full else R.plurals.pinned_count,
                            pinnedCount,
                            pinnedCount,
                            PinPickerConfig.MAX_PINNED,
                        ),
                        color = ThemeConfig.Foreground.copy(alpha = SearchConfig.SECTION_LABEL_ALPHA),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                TextButton(onClick = onDone) {
                    Text(stringResource(R.string.done), color = ThemeConfig.Foreground)
                }
            }
            Spacer(Modifier.height(SearchConfig.ResultsSpacing))
            LazyVerticalGrid(columns = GridCells.Fixed(columns), state = gridState, modifier = Modifier.fillMaxWidth()) {
                // Pinned apps come first so their indices match their place in the order.
                itemsIndexed(shownPinned, key = { _, app -> pinKey(app) }) { index, app ->
                    val isDragged = reorder.isDragging && reorder.draggedIndex == index
                    val placement = if (isDragged) {
                        // Lifted above its neighbours and following the finger.
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer {
                                translationX = reorder.draggedOffset.x
                                translationY = reorder.draggedOffset.y
                                scaleX = PinPickerConfig.DRAGGED_SCALE
                                scaleY = PinPickerConfig.DRAGGED_SCALE
                            }
                            .background(
                                Color.White.copy(alpha = PinPickerConfig.DRAGGED_BACKGROUND_ALPHA),
                                RoundedCornerShape(PinPickerConfig.DraggedCornerRadius),
                            )
                    } else {
                        Modifier.animateItem()
                    }
                    PickerRow(
                        app = app,
                        mirrored = mirrored,
                        isPinned = true,
                        enabled = true,
                        onToggle = { onToggle(app) },
                        modifier = placement.semantics {
                            customActions = listOfNotNull(
                                CustomAccessibilityAction(moveUpLabel) { moveBy(index, -1); true }
                                    .takeIf { index > 0 },
                                CustomAccessibilityAction(moveDownLabel) { moveBy(index, 1); true }
                                    .takeIf { index < shownPinned.lastIndex },
                            )
                        },
                        // Up and down in one column; any direction across two.
                        dragHandle = Modifier.pointerInput(pinKey(app), columns) {
                            if (columns == 1) {
                                detectVerticalDragGestures(
                                    onDragStart = { startDrag(app) },
                                    onVerticalDrag = { change, amount ->
                                        change.consume()
                                        reorder.drag(Offset(0f, amount))
                                    },
                                    onDragEnd = { endDrag() },
                                    onDragCancel = { endDrag() },
                                )
                            } else {
                                detectDragGestures(
                                    onDragStart = { startDrag(app) },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        reorder.drag(amount)
                                    },
                                    onDragEnd = { endDrag() },
                                    onDragCancel = { endDrag() },
                                )
                            }
                        },
                    )
                }
                items(otherApps, key = { it.key }) { app ->
                    PickerRow(
                        app = app,
                        mirrored = mirrored,
                        isPinned = false,
                        // Only while there's room on the sphere.
                        enabled = !isFull,
                        onToggle = { onToggle(app) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/**
 * Drag-to-reorder for the pinned apps at the start of the picker's grid, which occupy indices
 * `0 until order.size`. The dragged cell follows the finger; whenever its middle passes over
 * another pinned cell, the two trade places in [order].
 */
@Stable
private class PinReorderState(
    private val gridState: LazyGridState,
    private val onMove: () -> Unit,
) {
    /** The pins as arranged by the current (or just finished) drag; null otherwise. */
    var order by mutableStateOf<List<SearchEntry>?>(null)

    /** The dragged app's current index in [order]. */
    var draggedIndex by mutableIntStateOf(-1)
        private set

    val isDragging: Boolean get() = draggedIndex >= 0

    /** Where the dragged cell was when the drag began, and how far the finger has moved since. */
    private var startOffset = Offset.Zero
    private var dragDelta by mutableStateOf(Offset.Zero)

    private val draggedItem: LazyGridItemInfo?
        get() = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggedIndex }

    /** How far the dragged cell is drawn from its slot in the grid, so it stays under the finger. */
    val draggedOffset: Offset
        get() = draggedItem?.let { startOffset + dragDelta - it.offset.toOffset() } ?: Offset.Zero

    /** Starts dragging the cell keyed [key]; false if it isn't on screen. */
    fun start(key: String, pinnedApps: List<SearchEntry>): Boolean {
        val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return false
        if (item.index !in pinnedApps.indices) return false
        order = pinnedApps
        draggedIndex = item.index
        startOffset = item.offset.toOffset()
        dragDelta = Offset.Zero
        return true
    }

    fun drag(amount: Offset) {
        val current = order ?: return
        dragDelta += amount
        val item = draggedItem ?: return
        val middle = item.offset.toOffset() + draggedOffset + Offset(item.size.width / 2f, item.size.height / 2f)
        val target = gridState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index != item.index && it.index in current.indices && Rect(it.offset.toOffset(), it.size.toSize()).contains(middle)
        } ?: return
        // Moving the first visible cell would otherwise drag the scroll position along with it.
        if (item.index == gridState.firstVisibleItemIndex || target.index == gridState.firstVisibleItemIndex) {
            gridState.requestScrollToItem(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
        }
        order = current.toMutableList().apply { add(target.index, removeAt(item.index)) }
        draggedIndex = target.index
        onMove()
    }

    /** Ends the drag, returning the resulting order (still shown until the caller clears it). */
    fun end(): List<SearchEntry>? {
        draggedIndex = -1
        dragDelta = Offset.Zero
        return order
    }
}

/** [entry]'s pin key, which also keys its row. Only apps and shortcuts are ever pinned. */
private fun pinKey(entry: SearchEntry): String = PinnedAppsStore.keyOf(entry) ?: entry.id

/**
 * One app or pinned shortcut: tapping the row toggles its pin. [dragHandle], if given, shows a handle wearing it.
 * [mirrored] lays the row out the other way round, right-aligned.
 */
@Composable
private fun PickerRow(
    app: SearchEntry,
    isPinned: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    mirrored: Boolean = false,
    dragHandle: Modifier? = null,
) {
    val ambientDirection = LocalLayoutDirection.current
    val rowDirection = when {
        !mirrored -> ambientDirection
        ambientDirection == LayoutDirection.Ltr -> LayoutDirection.Rtl
        else -> LayoutDirection.Ltr
    }
    CompositionLocalProvider(LocalLayoutDirection provides rowDirection) {
        Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .toggleable(value = isPinned, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
                    .alpha(if (enabled) 1f else PinPickerConfig.DISABLED_ALPHA)
                    .padding(SearchConfig.ResultRowPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SearchConfig.ResultIconTextGap),
            ) {
                // Pins always have an icon (a shortcut without one gets its app's); keeps the gap if not.
                val icon = app.icon
                if (icon != null) {
                    Image(
                        painter = rememberDrawablePainter(icon),
                        // The name next to it already describes the row.
                        contentDescription = null,
                        modifier = Modifier.size(SearchConfig.ResultIconSize),
                    )
                } else {
                    Spacer(Modifier.size(SearchConfig.ResultIconSize))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.name,
                        color = ThemeConfig.Foreground,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // A shortcut says which app it belongs to, as in search.
                    if (app is ShortcutEntry) {
                        Text(
                            text = app.appName,
                            color = ThemeConfig.Foreground.copy(alpha = SearchConfig.RESULT_DETAIL_ALPHA),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // The whole row toggles; the checkbox only shows the state.
                Checkbox(
                    checked = isPinned,
                    onCheckedChange = null,
                    enabled = enabled,
                    colors = CheckboxDefaults.colors(
                        checkedColor = ThemeConfig.Foreground,
                        checkmarkColor = Color.Black,
                        uncheckedColor = ThemeConfig.Foreground.copy(alpha = PinPickerConfig.UNCHECKED_ALPHA),
                        // The whole row is dimmed when disabled, so keep the box's own colours.
                        disabledUncheckedColor = ThemeConfig.Foreground.copy(alpha = PinPickerConfig.UNCHECKED_ALPHA),
                    ),
                )
            }
            // Outside the toggleable area, so grabbing the handle never unpins the app.
            Box(
                modifier = Modifier
                    .size(PinPickerConfig.DragHandleTouchSize)
                    .then(dragHandle ?: Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (dragHandle != null) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(R.string.reorder_pinned_app),
                        tint = ThemeConfig.Foreground.copy(alpha = PinPickerConfig.DRAG_HANDLE_ALPHA),
                        modifier = Modifier.size(PinPickerConfig.DragHandleIconSize),
                    )
                }
            }
        }
    }
}
