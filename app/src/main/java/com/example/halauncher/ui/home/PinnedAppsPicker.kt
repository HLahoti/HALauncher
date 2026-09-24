package com.example.halauncher.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import com.example.halauncher.R
import com.example.halauncher.config.PinPickerConfig
import com.example.halauncher.config.SearchConfig
import com.example.halauncher.config.ThemeConfig
import com.example.halauncher.model.AppInfo
import com.example.halauncher.ui.components.liquidGlass
import com.example.halauncher.ui.components.rememberDrawablePainter
import com.example.halauncher.util.Haptics

/**
 * Every installed app with a pin toggle; the pinned ones appear on the home-screen sphere.
 * Pinned apps are listed first, in sphere order, and can be dragged by their handle to reorder
 * them; the rest follow A–Z. Changes save as they're
 * made. Once [PinPickerConfig.MAX_PINNED] are pinned, unpinned apps are disabled until one is
 * unpinned.
 */
@Composable
fun PinnedAppsPicker(
    apps: List<AppInfo>,
    pinnedPackages: List<String>,
    onToggle: (AppInfo) -> Unit,
    onReorder: (List<String>) -> Unit,
    onDone: () -> Unit,
) {
    val pinnedApps = remember(apps, pinnedPackages) {
        val byPackage = apps.associateBy { it.packageName }
        pinnedPackages.mapNotNull(byPackage::get)
    }
    val otherApps = remember(apps, pinnedPackages) {
        val pinned = pinnedPackages.toSet()
        apps.filter { it.packageName !in pinned }
    }
    val pinnedCount = pinnedApps.size
    val isFull = pinnedCount >= PinPickerConfig.MAX_PINNED

    val context = LocalContext.current
    val haptics = remember(context) { Haptics(context) }
    val listState = rememberLazyListState()
    val reorder = remember(listState) { PinReorderState(listState, onMove = haptics::tick) }
    // Read from the long-lived drag gesture, so always the latest.
    val currentPinnedApps by rememberUpdatedState(pinnedApps)
    val currentOnReorder by rememberUpdatedState(onReorder)
    // The drag's arrangement stays on screen until the saved order catches up with it.
    LaunchedEffect(pinnedApps) {
        if (!reorder.isDragging) reorder.order = null
    }
    val shownPinned = reorder.order ?: pinnedApps

    val startDrag = { app: AppInfo ->
        if (reorder.start(app.packageName, currentPinnedApps)) haptics.longPress()
    }
    val endDrag = {
        val order = reorder.end()
        if (order != null && order != currentPinnedApps) {
            currentOnReorder(order.map { it.packageName })
        } else {
            reorder.order = null
        }
    }
    val moveBy = { index: Int, step: Int ->
        val target = index + step
        if (target in pinnedApps.indices) {
            val moved = pinnedApps.toMutableList().apply { add(target, removeAt(index)) }
            onReorder(moved.map { it.packageName })
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
            Spacer(Modifier.height(SearchConfig.TopSpacing))
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
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                // Pinned apps come first so their list indices match their place in the order.
                itemsIndexed(shownPinned, key = { _, app -> app.packageName }) { index, app ->
                    val isDragged = reorder.isDragging && reorder.draggedIndex == index
                    val placement = if (isDragged) {
                        // Lifted above its neighbours and following the finger.
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer {
                                translationY = reorder.draggedOffset
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
                        dragHandle = Modifier.pointerInput(app.packageName) {
                            detectVerticalDragGestures(
                                onDragStart = { startDrag(app) },
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    reorder.drag(amount)
                                },
                                onDragEnd = { endDrag() },
                                onDragCancel = { endDrag() },
                            )
                        },
                    )
                }
                items(otherApps, key = { it.packageName }) { app ->
                    PickerRow(
                        app = app,
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
 * Drag-to-reorder for the pinned apps at the top of the picker's list, which occupy list
 * indices `0 until order.size`. The dragged row follows the finger; whenever its middle passes
 * over another pinned row, the two trade places in [order].
 */
@Stable
private class PinReorderState(
    private val listState: LazyListState,
    private val onMove: () -> Unit,
) {
    /** The pinned apps as arranged by the current (or just finished) drag; null otherwise. */
    var order by mutableStateOf<List<AppInfo>?>(null)

    /** The dragged app's current index in [order]. */
    var draggedIndex by mutableIntStateOf(-1)
        private set

    val isDragging: Boolean get() = draggedIndex >= 0

    /** Where the dragged row was when the drag began, and how far the finger has moved since. */
    private var startOffset = 0
    private var dragDelta by mutableFloatStateOf(0f)

    private val draggedItem: LazyListItemInfo?
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggedIndex }

    /** How far the dragged row is drawn from its slot in the list, so it stays under the finger. */
    val draggedOffset: Float
        get() = draggedItem?.let { startOffset + dragDelta - it.offset } ?: 0f

    /** Starts dragging the row keyed [packageName]; false if it isn't on screen. */
    fun start(packageName: String, pinnedApps: List<AppInfo>): Boolean {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == packageName } ?: return false
        if (item.index !in pinnedApps.indices) return false
        order = pinnedApps
        draggedIndex = item.index
        startOffset = item.offset
        dragDelta = 0f
        return true
    }

    fun drag(amount: Float) {
        val current = order ?: return
        dragDelta += amount
        val item = draggedItem ?: return
        val middle = (item.offset + draggedOffset + item.size / 2f).toInt()
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index != item.index && it.index in current.indices && middle in it.offset..(it.offset + it.size)
        } ?: return
        // Moving the first visible row would otherwise drag the scroll position along with it.
        if (item.index == listState.firstVisibleItemIndex || target.index == listState.firstVisibleItemIndex) {
            listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
        order = current.toMutableList().apply { add(target.index, removeAt(item.index)) }
        draggedIndex = target.index
        onMove()
    }

    /** Ends the drag, returning the resulting order (still shown until the caller clears it). */
    fun end(): List<AppInfo>? {
        draggedIndex = -1
        dragDelta = 0f
        return order
    }
}

/** One app: tapping the row toggles its pin. [dragHandle], if given, shows a handle wearing it. */
@Composable
private fun PickerRow(
    app: AppInfo,
    isPinned: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: Modifier? = null,
) {
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
            Image(
                painter = rememberDrawablePainter(app.icon),
                // The name next to it already describes the row.
                contentDescription = null,
                modifier = Modifier.size(SearchConfig.ResultIconSize),
            )
            Text(
                text = app.name,
                color = ThemeConfig.Foreground,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
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
