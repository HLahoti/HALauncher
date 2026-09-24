package com.example.halauncher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.example.halauncher.config.HomeScreenConfig
import com.example.halauncher.util.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * How far search has come in over the home screen: 0 is hidden, 1 fully open.
 *
 * Swipes drive it directly, 1:1 with the finger ([dragBy]): up from the home screen to open,
 * down inside search to close. When the finger lifts, [settle] springs it fully open or fully
 * closed. Arriving at the other end from where a swipe started (fully in, or fully out) ticks.
 * Every other way of opening or closing (Back, opening a result, the money button) goes through
 * [animateTo] instead, without a tick.
 */
@Stable
class SearchRevealState internal constructor(
    private val scope: CoroutineScope,
    private val haptics: Haptics,
    private val distancePx: Float,
    private val flingVelocityPx: Float,
    private val isOpen: () -> Boolean,
    private val open: () -> Unit,
    private val close: () -> Unit,
) {
    private val animatable = Animatable(0f).apply { updateBounds(0f, 1f) }
    private val spec = spring<Float>(stiffness = HomeScreenConfig.SEARCH_REVEAL_STIFFNESS)

    /** Read it in layout/draw blocks, not composition, so a swipe redraws without recomposing. */
    val value: Float get() = animatable.value

    /**
     * True from a swipe's first movement until it has settled, so [animateTo] leaves the finger
     * in charge meanwhile.
     */
    var isGesture by mutableStateOf(false)
        private set

    /** A finger is down and moving it (as opposed to settling after one). */
    var isDragging = false
        private set
    private var progress = 0f
    /** The end (0 or 1) the swipe last touched; arriving at the other one ticks. */
    private var lastEnd: Float? = null

    /**
     * Moves search with a finger that moved [dyPx] (positive is down) and returns how much of
     * that it used; the rest is left for whatever else is being dragged (the results list).
     */
    fun dragBy(dyPx: Float): Float {
        if (!isDragging) {
            isDragging = true
            isGesture = true
            // Catching search mid-settle carries on from where it is.
            progress = animatable.value
            lastEnd = endAt(progress) ?: lastEnd
        }
        val before = progress
        progress = (progress - dyPx / distancePx).coerceIn(0f, 1f)
        // Open (for Back, and the results) as soon as any of it shows.
        if (progress > 0f && !isOpen()) open()
        scope.launch { animatable.snapTo(progress) }
        endAt(progress)?.let(::arrive)
        return (before - progress) * distancePx
    }

    /**
     * The finger lifted, moving at [velocityPx] per second (positive is down): finishes opening
     * past [HomeScreenConfig.SEARCH_REVEAL_OPEN_FRACTION], or closing below it, unless the
     * finger was flicking, which wins either way.
     */
    fun settle(velocityPx: Float) {
        if (!isDragging) return
        isDragging = false
        val opening = if (abs(velocityPx) > flingVelocityPx) {
            velocityPx < 0f
        } else {
            progress >= HomeScreenConfig.SEARCH_REVEAL_OPEN_FRACTION
        }
        val target = if (opening) 1f else 0f
        scope.launch {
            animatable.animateTo(target, spec, initialVelocity = -velocityPx / distancePx)
            arrive(target)
            when {
                !opening && isOpen() -> close()
                // Closed some other way (Back) while settling open: follow it out.
                opening && !isOpen() -> animatable.animateTo(0f, spec)
            }
            isGesture = false
        }
    }

    /** Opens or closes without a finger, unless a swipe is in charge. */
    suspend fun animateTo(open: Boolean) {
        if (!isGesture) animatable.animateTo(if (open) 1f else 0f, spec)
    }

    private fun arrive(end: Float) {
        if (end != lastEnd) {
            haptics.tick()
            lastEnd = end
        }
    }

    private fun endAt(progress: Float): Float? = when {
        progress >= 1f -> 1f
        progress <= 0f -> 0f
        else -> null
    }
}

@Composable
fun rememberSearchRevealState(isOpen: () -> Boolean, open: () -> Unit, close: () -> Unit): SearchRevealState {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val currentIsOpen by rememberUpdatedState(isOpen)
    val currentOpen by rememberUpdatedState(open)
    val currentClose by rememberUpdatedState(close)
    return remember(context, density) {
        with(density) {
            SearchRevealState(
                scope = scope,
                haptics = Haptics(context),
                distancePx = HomeScreenConfig.SearchRevealDistance.toPx(),
                flingVelocityPx = HomeScreenConfig.SearchRevealFlingVelocity.toPx(),
                isOpen = { currentIsOpen() },
                open = { currentOpen() },
                close = { currentClose() },
            )
        }
    }
}
