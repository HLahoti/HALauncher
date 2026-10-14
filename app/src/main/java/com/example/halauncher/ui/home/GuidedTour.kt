package com.example.halauncher.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toSize
import com.example.halauncher.R
import com.example.halauncher.config.SearchConfig
import com.example.halauncher.config.SettingsPanelConfig
import com.example.halauncher.config.SphereConfig
import com.example.halauncher.config.ThemeConfig
import com.example.halauncher.config.TourConfig
import com.example.halauncher.ui.components.liquidGlass
import kotlin.math.max
import kotlin.math.min

/**
 * The steps of the hands-on tour, in order. Each is finished by doing it for real. [inSearch] steps
 * play out inside search; the rest on the bare home screen.
 */
enum class TourStep(val inSearch: Boolean) {
    /** Swipe up to open search. */
    SEARCH(inSearch = false),

    /** Swipe sideways to change the category filter. */
    FILTER(inSearch = true),

    /** Swipe down (or go back) to close search. */
    CLOSE_SEARCH(inSearch = true),

    /** Turn the sphere, or with fewer than two pins, hold it to choose some. */
    SPHERE(inSearch = false),

    /** Hold an empty spot to open settings. */
    SETTINGS(inSearch = false),
}

/**
 * The hands-on tour, drawn over the real home screen (or search) rather than described in a page of
 * text. For each step a ghost fingertip acts out the gesture where it's done, a spotlight picks that
 * spot out of a dimmed home screen, and a short caption says what to do. The user does it for real
 * to move on; the view model hears about that and advances [step].
 *
 * Only the caption's Skip button takes touches, so every gesture reaches the screen underneath.
 *
 * @param step the step under way, or null when there's no tour (it fades out).
 * @param steps all the steps this run of the tour expects, for the progress dots.
 * @param visible whether [step] can be done right now: false while something covers it (settings,
 *   the pin picker), or while a home-screen step waits behind search, and vice versa.
 * @param reveal how far search has come in (0–1); read only while drawing. Home-screen steps fade as
 *   it comes in, steps inside search with it.
 * @param sphereBounds where the sphere is, in root coordinates, once it's been laid out.
 * @param sphereCanTurn whether the sphere has enough pins to turn; otherwise its step is to hold it
 *   and pick some.
 */
@Composable
fun GuidedTour(
    step: TourStep?,
    steps: List<TourStep>,
    visible: Boolean,
    reveal: () -> Float,
    sphereBounds: Rect?,
    sphereCanTurn: Boolean,
    onSkip: () -> Unit,
) {
    // The last step stays on screen while the tour fades out after it.
    val last = remember { LastTourStep() }
    if (step != null) last.step = step
    val shown by animateFloatAsState(if (step != null && visible) 1f else 0f, label = "tourShown")
    val current = last.step ?: return
    // Gone entirely once faded out, so Skip can't catch taps meant for whatever is in front.
    if (shown == 0f) return

    val density = LocalDensity.current
    var size by remember { mutableStateOf(Size.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var captionTop by remember { mutableFloatStateOf(0f) }
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    val sphere = sphereBounds?.translate(-origin)
    val gesture = if (size == Size.Zero || captionTop == 0f) {
        null
    } else {
        tourGesture(current, sphereCanTurn, size, captionTop, statusTop, sphere, density)
    }

    // Search dims the home screen itself, so the spotlight is only for home-screen steps.
    val spotlightTarget = gesture?.takeUnless { current.inSearch }?.let { spotlightFor(current, it, sphere, density) }
    val spotlight = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    LaunchedEffect(spotlightTarget) {
        val target = spotlightTarget ?: return@LaunchedEffect
        // The first spotlight appears where it belongs; later ones glide over from the last.
        if (spotlight.value == Rect.Zero) spotlight.snapTo(target) else spotlight.animateTo(target)
    }
    val scrimAlpha by animateFloatAsState(if (current.inSearch) 0f else TourConfig.SCRIM_ALPHA, label = "tourScrim")
    val loop = rememberInfiniteTransition(label = "tourGesture")
    val time by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(TourConfig.GESTURE_LOOP_MS, easing = LinearEasing)),
        label = "tourGestureTime",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                size = it.size.toSize()
                origin = it.positionInRoot()
            }
            // Read in the draw phase, so a swipe fades it without recomposing.
            .graphicsLayer {
                val r = reveal()
                alpha = shown * if (current.inSearch) r else (1f - 2f * r).coerceAtLeast(0f)
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Offscreen, so clearing the spotlight out of the scrim leaves a hole, not black.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            if (scrimAlpha > 0f) {
                drawRect(Color.Black.copy(alpha = scrimAlpha))
                val hole = spotlight.value
                if (hole != Rect.Zero) {
                    drawRoundRect(
                        color = Color.Black,
                        topLeft = hole.topLeft,
                        size = hole.size,
                        cornerRadius = CornerRadius(hole.minDimension / 2),
                        blendMode = BlendMode.Clear,
                    )
                }
            }
            gesture?.let { drawGesture(it, time) }
        }
        TourCaption(
            step = current,
            sphereCanTurn = sphereCanTurn,
            steps = steps,
            onSkip = onSkip,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                // Above the keyboard while search is open.
                .imePadding()
                .padding(horizontal = SearchConfig.HorizontalPadding)
                .padding(bottom = SearchConfig.BottomSpacing)
                .onGloballyPositioned { captionTop = it.positionInParent().y },
        )
    }
}

/** The tour's step as last shown, kept so the tour can fade out on it once it's over. */
private class LastTourStep {
    var step: TourStep? = null
}

/** A gesture the ghost fingertip acts out. */
private sealed interface TourGesture {
    /** Touch down at [from], slide to [to], lift. */
    data class Swipe(val from: Offset, val to: Offset) : TourGesture

    /** Touch down at [at] and hold there. */
    data class Hold(val at: Offset) : TourGesture
}

/**
 * Where [step]'s gesture is acted out, in the tour's own coordinates: on the home screen, kept clear
 * of the caption (whose top is [captionTop]) and the [sphere]; in search, between the filter bar
 * and the caption, which sits above the keyboard. Null while the sphere hasn't been placed yet.
 */
private fun tourGesture(
    step: TourStep,
    sphereCanTurn: Boolean,
    size: Size,
    captionTop: Float,
    statusTop: Float,
    sphere: Rect?,
    density: Density,
): TourGesture? = with(density) {
    // Search's results area, below its field and filter bar.
    val searchTop = statusTop + TourConfig.SearchContentTop.toPx()
    val searchBottom = max(captionTop - TourConfig.CaptionGap.toPx(), searchTop)
    when (step) {
        TourStep.SEARCH -> {
            val x = size.width * TourConfig.SWIPE_X_FRACTION
            val clearOfSphere = sphere?.let { min(x, it.left - TourConfig.SphereClearance.toPx()) } ?: x
            val from = Offset(clearOfSphere, captionTop - TourConfig.SwipeStartAboveCaption.toPx())
            TourGesture.Swipe(from, from - Offset(0f, TourConfig.SwipeLength.toPx()))
        }
        // Leftwards, the way to the next category.
        TourStep.FILTER -> {
            val y = (searchTop + searchBottom) / 2
            TourGesture.Swipe(Offset(size.width * 0.72f, y), Offset(size.width * 0.28f, y))
        }
        TourStep.CLOSE_SEARCH -> {
            val height = searchBottom - searchTop
            TourGesture.Swipe(
                Offset(size.width / 2, searchTop + height * 0.15f),
                Offset(size.width / 2, searchTop + height * 0.85f),
            )
        }
        // On the app at the front of the sphere.
        TourStep.SPHERE -> sphere?.let {
            val front = Offset(it.left + SphereConfig.IconRingInset.toPx(), it.center.y)
            if (sphereCanTurn) {
                val half = Offset(0f, TourConfig.SphereDragLength.toPx() / 2)
                TourGesture.Swipe(front + half, front - half)
            } else {
                TourGesture.Hold(front)
            }
        }
        TourStep.SETTINGS -> TourGesture.Hold(
            Offset(size.width * TourConfig.HOLD_X_FRACTION, captionTop - TourConfig.HoldAboveCaption.toPx()),
        )
    }
}

/** The spotlight around [gesture]: the whole sphere for its step, else a pill along the gesture. */
private fun spotlightFor(step: TourStep, gesture: TourGesture, sphere: Rect?, density: Density): Rect = with(density) {
    if (step == TourStep.SPHERE && sphere != null) return sphere.inflate(TourConfig.SpotlightPadding.toPx())
    val half = TourConfig.SpotlightWidth.toPx() / 2
    when (gesture) {
        is TourGesture.Swipe -> Rect(
            left = min(gesture.from.x, gesture.to.x) - half,
            top = min(gesture.from.y, gesture.to.y) - half,
            right = max(gesture.from.x, gesture.to.x) + half,
            bottom = max(gesture.from.y, gesture.to.y) + half,
        )
        is TourGesture.Hold -> Rect(center = gesture.at, radius = half)
    }
}

/**
 * Draws [gesture] at [time] through its loop (0–1). A swipe lands, glides with a fading trail and
 * lifts; a hold lands, fills a ring around itself, and lets it ripple out. Both then rest a moment
 * before the loop repeats.
 */
private fun DrawScope.drawGesture(gesture: TourGesture, time: Float) {
    val radius = TourConfig.FingerRadius.toPx()
    when (gesture) {
        is TourGesture.Swipe -> {
            val landed = phase(time, 0f, 0.12f)
            val moved = FastOutSlowInEasing.transform(phase(time, 0.12f, 0.62f))
            val alpha = landed * (1f - phase(time, 0.62f, 0.78f))
            if (alpha <= 0f) return
            val tip = lerp(gesture.from, gesture.to, moved)
            val tail = lerp(gesture.from, gesture.to, (moved - 0.45f).coerceAtLeast(0f))
            if ((tip - tail).getDistance() > 1f) {
                drawLine(
                    brush = Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = TourConfig.TRAIL_ALPHA * alpha)),
                        start = tail,
                        end = tip,
                    ),
                    start = tail,
                    end = tip,
                    strokeWidth = radius * 1.4f,
                    cap = StrokeCap.Round,
                )
            }
            // Lands from a touch larger, like a finger pressing down.
            drawFingertip(tip, radius * (1.3f - 0.3f * landed), alpha)
        }
        is TourGesture.Hold -> {
            val landed = phase(time, 0f, 0.1f)
            val held = phase(time, 0.1f, 0.6f)
            val released = phase(time, 0.6f, 0.8f)
            val alpha = landed * (1f - released)
            val ringRadius = radius * 1.7f
            val ring = Stroke(width = TourConfig.HoldRingWidth.toPx(), cap = StrokeCap.Round)
            if (released > 0f && released < 1f) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.5f * (1f - released)),
                    radius = ringRadius * (1f + released),
                    center = gesture.at,
                    style = ring,
                )
            }
            if (alpha <= 0f) return
            drawArc(
                color = Color.White.copy(alpha = TourConfig.FINGER_ALPHA * alpha),
                startAngle = -90f,
                sweepAngle = 360f * held,
                useCenter = false,
                topLeft = gesture.at - Offset(ringRadius, ringRadius),
                size = Size(ringRadius * 2, ringRadius * 2),
                style = ring,
            )
            drawFingertip(gesture.at, radius * (1.3f - 0.3f * landed), alpha)
        }
    }
}

/** A ghost fingertip: a bright dot in a soft halo. */
private fun DrawScope.drawFingertip(center: Offset, radius: Float, alpha: Float) {
    drawCircle(Color.White.copy(alpha = TourConfig.HALO_ALPHA * alpha), radius * 1.6f, center)
    drawCircle(Color.White.copy(alpha = TourConfig.FINGER_ALPHA * alpha), radius, center)
}

/** How far [time] is through the span from [start] to [end], clamped to 0–1. */
private fun phase(time: Float, start: Float, end: Float): Float = ((time - start) / (end - start)).coerceIn(0f, 1f)

/**
 * The glass card under the gesture: what to do in a few words, a line of help, progress dots and
 * Skip. Screen readers announce each new step as it arrives.
 */
@Composable
private fun TourCaption(
    step: TourStep,
    sphereCanTurn: Boolean,
    steps: List<TourStep>,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(SettingsPanelConfig.CardCornerRadius), tint = TourConfig.CAPTION_GLASS_TINT)
            .padding(SettingsPanelConfig.CardPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsPanelConfig.CardPadding),
    ) {
        AnimatedContent(
            targetState = step to sphereCanTurn,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tourCaption",
        ) { (shownStep, canTurn) ->
            val (titleRes, hintRes) = captionFor(shownStep, canTurn)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(SettingsPanelConfig.TitleDescriptionGap),
            ) {
                Text(
                    text = stringResource(titleRes),
                    color = ThemeConfig.Foreground,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(hintRes),
                    color = ThemeConfig.Foreground.copy(alpha = SettingsPanelConfig.DESCRIPTION_ALPHA),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressDots(steps, step, Modifier.weight(1f))
            val skip = stringResource(R.string.tour_skip)
            Text(
                text = skip,
                color = ThemeConfig.Foreground,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClickLabel = skip, onClick = onSkip)
                    .padding(horizontal = TourConfig.SkipHorizontalPadding, vertical = TourConfig.SkipVerticalPadding),
            )
        }
    }
}

/** The caption's title and help line for [step]. */
private fun captionFor(step: TourStep, sphereCanTurn: Boolean): Pair<Int, Int> = when (step) {
    TourStep.SEARCH -> R.string.tour_search_title to R.string.tour_search_hint
    TourStep.FILTER -> R.string.tour_filter_title to R.string.tour_filter_hint
    TourStep.CLOSE_SEARCH -> R.string.tour_close_title to R.string.tour_close_hint
    TourStep.SPHERE -> if (sphereCanTurn) {
        R.string.tour_spin_title to R.string.tour_spin_hint
    } else {
        R.string.tour_pin_title to R.string.tour_pin_hint
    }
    TourStep.SETTINGS -> R.string.tour_settings_title to R.string.tour_settings_hint
}

/** A dot per step, lit up to [current]; read out as "Step n of m". */
@Composable
private fun ProgressDots(steps: List<TourStep>, current: TourStep, modifier: Modifier = Modifier) {
    val index = steps.indexOf(current)
    val description = stringResource(R.string.onboarding_step, index + 1, steps.size)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(TourConfig.DotGap),
    ) {
        steps.forEachIndexed { i, _ ->
            Box(
                modifier = Modifier
                    .size(TourConfig.DotSize)
                    .background(
                        ThemeConfig.Foreground.copy(alpha = if (i <= index) 1f else TourConfig.DOT_DIM_ALPHA),
                        CircleShape,
                    ),
            )
        }
    }
}
