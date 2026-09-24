package com.example.halauncher.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import com.example.halauncher.R
import com.example.halauncher.config.CategoryFilterConfig
import com.example.halauncher.config.ThemeConfig
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.ui.components.icon
import com.example.halauncher.ui.components.labelRes
import com.example.halauncher.ui.components.liquidGlass
import com.example.halauncher.util.Haptics
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A glass segmented control: "All", then one icon per category in [categories]. A brighter
 * glass thumb marks the selection. Tap a segment to glide the thumb there, or drag the thumb
 * (or anywhere on the bar): it follows the finger, the filter switches live as it enters
 * each segment with a haptic tick, and on release it springs to the nearest segment.
 *
 * @param selected the chosen category, or null for "All".
 */
@Composable
fun CategoryFilterBar(
    categories: List<SearchCategory>,
    selected: SearchCategory?,
    onSelected: (SearchCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // null stands for "All" and always comes first.
    val segments: List<SearchCategory?> = remember(categories) { listOf(null) + categories }
    val selectedIndex = segments.indexOf(selected).coerceAtLeast(0)

    val context = LocalContext.current
    val haptics = remember(context) { Haptics(context) }
    val scope = rememberCoroutineScope()
    // Thumb position in segments: 0f is centred on "All", 1.5f is halfway between the next two.
    val thumb = remember { Animatable(selectedIndex.toFloat()) }
    // While dragging, the finger positions the thumb; selection changes mustn't pull it away.
    var isDragging by remember { mutableStateOf(false) }
    val currentSegments by rememberUpdatedState(segments)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentOnSelected by rememberUpdatedState(onSelected)

    // Follow selection changes that didn't come from a drag (taps, or a reset on close).
    LaunchedEffect(selectedIndex) {
        if (!isDragging) thumb.animateTo(selectedIndex.toFloat(), CategoryFilterConfig.SettleSpring)
    }

    fun select(index: Int) {
        if (index == currentSelectedIndex) return
        haptics.tick()
        currentOnSelected(currentSegments[index])
    }

    val segmentWidth = CategoryFilterConfig.SegmentWidth
    Box(
        modifier = modifier
            .height(CategoryFilterConfig.Height)
            .width(segmentWidth * segments.size)
            .liquidGlass(RoundedCornerShape(percent = 50))
            .pointerInput(Unit) {
                val segmentPx = segmentWidth.toPx()
                fun positionAt(x: Float) =
                    (x / segmentPx - 0.5f).coerceIn(0f, currentSegments.lastIndex.toFloat())

                fun follow(x: Float) {
                    val position = positionAt(x)
                    scope.launch { thumb.snapTo(position) }
                    select(position.roundToInt())
                }

                fun settle() {
                    isDragging = false
                    scope.launch {
                        thumb.animateTo(thumb.value.roundToInt().toFloat(), CategoryFilterConfig.SettleSpring)
                    }
                }
                detectHorizontalDragGestures(
                    onDragStart = {
                        isDragging = true
                        follow(it.x)
                    },
                    onDragEnd = { settle() },
                    onDragCancel = { settle() },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        follow(change.position.x)
                    },
                )
            },
    ) {
        // The sliding thumb, drawn under the segment contents.
        Box(
            modifier = Modifier
                .offset { IntOffset((thumb.value * segmentWidth.toPx()).roundToInt(), 0) }
                .width(segmentWidth)
                .fillMaxHeight()
                .padding(CategoryFilterConfig.ThumbInset)
                .liquidGlass(RoundedCornerShape(percent = 50), tint = 0f)
                .background(
                    ThemeConfig.Foreground.copy(alpha = CategoryFilterConfig.THUMB_SHEEN_ALPHA),
                    RoundedCornerShape(percent = 50),
                ),
        )
        Row(modifier = Modifier.fillMaxHeight().selectableGroup()) {
            segments.forEachIndexed { index, category ->
                Segment(
                    category = category,
                    isSelected = index == selectedIndex,
                    onClick = { select(index) },
                    modifier = Modifier
                        .width(segmentWidth)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun Segment(
    category: SearchCategory?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(
        if (isSelected) {
            ThemeConfig.Foreground
        } else {
            ThemeConfig.Foreground.copy(alpha = CategoryFilterConfig.UNSELECTED_ALPHA)
        },
        label = "segmentTint",
    )
    Box(
        modifier = modifier.selectable(
            selected = isSelected,
            // The sliding thumb is the feedback; a ripple on top would fight it.
            interactionSource = null,
            indication = null,
            role = Role.Tab,
            onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (category == null) {
            Text(
                text = stringResource(R.string.category_all),
                color = tint,
                fontSize = CategoryFilterConfig.AllLabelFontSize,
                fontWeight = FontWeight.Medium,
            )
        } else {
            Icon(
                imageVector = category.icon,
                contentDescription = stringResource(category.labelRes),
                tint = tint,
                modifier = Modifier.size(CategoryFilterConfig.IconSize),
            )
        }
    }
}
