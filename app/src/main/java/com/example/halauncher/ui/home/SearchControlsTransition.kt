package com.example.halauncher.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.halauncher.config.SearchConfig

/**
 * How search's field and filter bar change between their usual layout and the single row they fold
 * into while the results are scrolled (see `collapsed` in SearchOverlay). Each way of animating it
 * is one implementation; [ActiveSearchControlsTransition] picks the one in use, so another can be
 * tried by changing that line alone.
 */
interface SearchControlsTransition {
    /**
     * Shows [expanded] or, when [collapsed], [folded], getting from one to the other its own way.
     * Both are drawn full width; the height is whichever is showing (or in between, while animating).
     */
    @Composable
    fun Content(
        collapsed: Boolean,
        modifier: Modifier,
        expanded: @Composable () -> Unit,
        folded: @Composable () -> Unit,
    )

    /** How long the change takes, so what depends on the controls' final size can wait it out. */
    val durationMillis: Int

    /** Home of the built-in transitions: [Instant], [CrossFade]. */
    companion object
}

/** The transition search uses. */
val ActiveSearchControlsTransition: SearchControlsTransition = SearchControlsTransition.CrossFade

/** Swaps straight from one layout to the other. */
val SearchControlsTransition.Companion.Instant: SearchControlsTransition get() = InstantTransition

/** Fades one layout out as the other fades in, the height easing between them. */
val SearchControlsTransition.Companion.CrossFade: SearchControlsTransition get() = CrossFadeTransition

private object InstantTransition : SearchControlsTransition {
    override val durationMillis: Int = 0

    @Composable
    override fun Content(
        collapsed: Boolean,
        modifier: Modifier,
        expanded: @Composable () -> Unit,
        folded: @Composable () -> Unit,
    ) {
        Box(modifier) { if (collapsed) folded() else expanded() }
    }
}

private object CrossFadeTransition : SearchControlsTransition {
    override val durationMillis: Int = SearchConfig.CONTROLS_TRANSITION_MS

    @Composable
    override fun Content(
        collapsed: Boolean,
        modifier: Modifier,
        expanded: @Composable () -> Unit,
        folded: @Composable () -> Unit,
    ) {
        AnimatedContent(
            targetState = collapsed,
            modifier = modifier,
            // Pinned to the bottom, where the bars sit: the height eases from the top edge down.
            contentAlignment = Alignment.BottomCenter,
            transitionSpec = {
                fadeIn(tween(durationMillis)) togetherWith fadeOut(tween(durationMillis)) using
                    SizeTransform(clip = false) { _, _ -> tween(durationMillis) }
            },
            label = "searchControls",
        ) { isFolded ->
            if (isFolded) folded() else expanded()
        }
    }
}
