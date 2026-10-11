package com.example.halauncher.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.halauncher.R
import com.example.halauncher.config.SphereConfig
import com.example.halauncher.model.AppInfo
import com.example.halauncher.ui.components.launchSafely
import com.example.halauncher.ui.components.LocalHomePalette
import com.example.halauncher.ui.components.liquidGlass
import com.example.halauncher.ui.components.rememberDrawablePainter
import com.example.halauncher.util.Haptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The pinned apps on the rim of a glass half-sphere docked on the right edge.
 *
 * Picture a globe seen side-on, turning about a horizontal axis. App `i` sits at angle
 * `(i - rotation) × step` on the rim: 0° is the front (middle, facing left) where the app is
 * full size and selected; towards ±90° apps roll over the top and bottom, shrinking and fading,
 * and past that they're behind the sphere. Dragging up or down turns it 1:1 with the finger; a
 * flick carries on and springs onto an app. There's a haptic tick each time a new app reaches
 * the front. Tap an app to open it; long-press to edit the pins.
 *
 * The ring is circular: after the last pin comes the first again, in either direction. Each
 * app's offset from the front is wrapped into ±N/2 slots, and [loopStepDegrees] spaces the apps
 * so that seam falls at the sphere's edge (±90°), where apps are fully faded, never on the
 * visible face.
 *
 * [rotation] is read only in layout/draw blocks, so turning the sphere redraws without
 * recomposing; composition only changes when the front app does.
 *
 * @param modifier should fill the screen; only the sphere itself takes touches.
 */
@Composable
fun PinnedAppsSphere(
    apps: List<AppInfo>,
    onAppOpened: (AppInfo) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptics = remember(context) { Haptics(context) }
    val scope = rememberCoroutineScope()
    // The sphere's fixed shading (light, rim, latitude stroke) doesn't depend on the rotation, so
    // it's built once and reused, not re-allocated on every frame of a turn.
    val shading = remember { SphereShading() }
    // Smoky glass with light lines on a dark wallpaper; frosted with dark lines on a light one.
    val palette = LocalHomePalette.current
    val currentApps by rememberUpdatedState(apps)
    val currentOnAppOpened by rememberUpdatedState(onAppOpened)
    val currentOnEdit by rememberUpdatedState(onEdit)
    // Read live inside the gesture handlers, which are keyed on the pin count and so don't restart
    // when the lock flips.
    val currentLocked by rememberUpdatedState(locked)

    val count = apps.size
    // Unbounded: the ring loops, so any value is valid. 0 puts the first pin at the front.
    val rotation = remember { Animatable(0f) }
    // True while the finger or the settle animation turns the sphere; only then do ticks fire
    // and the name label show (not when the pin list itself changes).
    var isTurning by remember { mutableStateOf(false) }
    var lastTurnEndedAt by remember { mutableLongStateOf(0L) }

    // After a turn: stop ticking, let the label linger, and fold the rotation back into one
    // lap (same positions, since the ring repeats every `count` slots) so it stays small.
    suspend fun settled() {
        isTurning = false
        lastTurnEndedAt = System.currentTimeMillis()
        if (count > 0) rotation.snapTo(rotation.value.mod(count.toFloat()))
    }

    val frontIndex by remember(count) {
        derivedStateOf { if (count == 0) 0 else rotation.value.roundToInt().mod(count) }
    }
    // Ticks on every slot that passes the front, including wrapping from the last pin to the first.
    LaunchedEffect(Unit) {
        snapshotFlow { rotation.value.roundToInt() }.distinctUntilChanged().collect { if (isTurning) haptics.tick() }
    }

    // The front app's name shows while turning and for a moment after.
    var showLabel by remember { mutableStateOf(false) }
    LaunchedEffect(isTurning, lastTurnEndedAt) {
        if (isTurning) {
            showLabel = true
        } else if (showLabel) {
            delay(SphereConfig.LABEL_LINGER_MS)
            showLabel = false
        }
    }

    val stepDegrees = loopStepDegrees(count)
    val step = (stepDegrees * PI / 180).toFloat()
    val halfVisibleSlots = floor(90f / stepDegrees).toInt()
    val editLabel = stringResource(R.string.edit_pinned_apps)
    val sphereLabel = stringResource(R.string.pinned_apps)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val gestureInset = with(density) { WindowInsets.systemGestures.getRight(density, layoutDirection).toDp() }
        val radius = SphereConfig.Radius
        val ringRadius = radius - SphereConfig.IconRingInset
        val iconSize = SphereConfig.IconSize
        // The centre sits on the edge of the back-gesture zone, so the whole visible half is
        // outside it and drags on it can't trigger Back.
        val centerX = maxWidth - gestureInset
        val centerY = maxHeight / 2 + SphereConfig.VerticalOffset

        fun positionOf(index: Int, rotationValue: Float, radiusPx: Float, ringPx: Float): SlotPosition {
            val angle = loopOffset(index, rotationValue, count) * step
            val depth = cos(angle)
            return SlotPosition(
                x = radiusPx - ringPx * depth,
                y = radiusPx + ringPx * sin(angle),
                depth = if (abs(angle) < PI / 2) depth else 0f,
            )
        }

        Box(
            modifier = Modifier
                .offset(x = centerX - radius, y = centerY - radius)
                .size(radius * 2)
                .systemGestureExclusion()
                .liquidGlass(
                    CircleShape,
                    tint = if (palette.isLight) SphereConfig.GLASS_TINT_ON_LIGHT else SphereConfig.GLASS_TINT,
                    base = palette.glassBase,
                    edge = palette.glassLine,
                )
                .drawBehind {
                    val r = size.minDimension / 2
                    // Rebuilds only when the sphere's pixel size changes, not on every turn frame.
                    shading.update(size, SphereConfig.LatitudeWidth.toPx())
                    // Light from the upper left, and a darker rim, for volume.
                    drawCircle(brush = shading.highlight)
                    drawCircle(brush = shading.rim)
                    // Latitude lines between the app slots roll with the sphere and squash
                    // towards the poles, which is what makes it read as turning.
                    val base = floor(rotation.value)
                    val within = rotation.value - base
                    for (slot in -halfVisibleSlots - 1..halfVisibleSlots + 1) {
                        val angle = (slot + 0.5f - within) * step
                        if (abs(angle) >= PI / 2) continue
                        val depth = cos(angle)
                        val halfWidth = r * depth
                        val height = halfWidth * SphereConfig.LATITUDE_TILT
                        drawOval(
                            color = palette.glassLine.copy(alpha = SphereConfig.LATITUDE_ALPHA * depth),
                            topLeft = Offset(r - halfWidth, r + r * sin(angle) - height / 2),
                            size = Size(halfWidth * 2, height),
                            style = shading.latitudeStroke,
                        )
                    }
                }
                .semantics {
                    contentDescription = sphereLabel
                    // Editing the pins is unavailable while settings are locked.
                    customActions = if (currentLocked) {
                        emptyList()
                    } else {
                        listOf(CustomAccessibilityAction(editLabel) { currentOnEdit(); true })
                    }
                }
                .pointerInput(count) {
                    val arcPerApp = ringRadius.toPx() * step
                    val tracker = VelocityTracker()
                    // With one pin (or none) there's nothing to turn to.
                    if (count < 2) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            isTurning = true
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val turned = rotation.value - dragAmount / arcPerApp
                            scope.launch { rotation.snapTo(turned) }
                        },
                        onDragEnd = {
                            val velocity = -tracker.calculateVelocity().y / arcPerApp
                            val target = (rotation.value + velocity * SphereConfig.FLING_SECONDS).roundToInt()
                            scope.launch {
                                rotation.animateTo(target.toFloat(), SphereConfig.SettleSpring, velocity)
                                settled()
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                rotation.animateTo(rotation.value.roundToInt().toFloat(), SphereConfig.SettleSpring)
                                settled()
                            }
                        },
                    )
                }
                .pointerInput(count) {
                    detectTapGestures(
                        onLongPress = {
                            // Locked settings mean the pin editor stays shut on a long-press.
                            if (!currentLocked) {
                                haptics.tick()
                                currentOnEdit()
                            }
                        },
                        onTap = { tap ->
                            val all = currentApps
                            if (all.isEmpty()) {
                                // With no pins, tapping normally opens the editor; not while locked.
                                if (!currentLocked) currentOnEdit()
                                return@detectTapGestures
                            }
                            val radiusPx = radius.toPx()
                            val ringPx = ringRadius.toPx()
                            val iconHalfPx = iconSize.toPx() / 2
                            // The nearest visible app under the finger; front-most wins overlaps.
                            val hit = all.indices
                                .map { it to positionOf(it, rotation.value, radiusPx, ringPx) }
                                .filter { (_, slot) -> slot.depth > 0f }
                                .filter { (_, slot) -> hypot(tap.x - slot.x, tap.y - slot.y) <= iconHalfPx * slot.scale() }
                                .maxByOrNull { (_, slot) -> slot.depth }
                                ?.first ?: return@detectTapGestures
                            val app = all[hit]
                            if (context.launchSafely(app.link, resources.getString(R.string.open_app, app.name))) {
                                currentOnAppOpened(app)
                            }
                        },
                    )
                },
        ) {
            // A soft halo marks the front (selected) slot.
            Box(
                modifier = Modifier
                    .offset(
                        x = radius - ringRadius - iconSize / 2 - SphereConfig.FrontHaloPadding,
                        y = radius - iconSize / 2 - SphereConfig.FrontHaloPadding,
                    )
                    .size(iconSize + SphereConfig.FrontHaloPadding * 2)
                    .liquidGlass(CircleShape, tint = 0f, edge = palette.glassLine),
            )
            if (count == 0) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = palette.foreground,
                    modifier = Modifier
                        .offset(x = radius - ringRadius - iconSize / 2, y = radius - iconSize / 2)
                        .size(iconSize)
                        .padding(SphereConfig.AddIconPadding),
                )
            }
            apps.forEachIndexed { index, app ->
                key(app.packageName) {
                    val openLabel = stringResource(R.string.open_app, app.name)
                    val isOnFrontFace = abs(loopOffset(index, frontIndex.toFloat(), count)) <= halfVisibleSlots
                    Image(
                        painter = rememberDrawablePainter(app.icon),
                        contentDescription = null,
                        modifier = Modifier
                            .size(iconSize)
                            .graphicsLayer {
                                val slot = positionOf(index, rotation.value, radius.toPx(), ringRadius.toPx())
                                translationX = slot.x - size.width / 2
                                translationY = slot.y - size.height / 2
                                scaleX = slot.scale()
                                scaleY = scaleX
                                alpha = slot.alpha()
                            }
                            // Screen readers get each app on the front face as a button.
                            .then(
                                if (isOnFrontFace) {
                                    Modifier.semantics {
                                        contentDescription = app.name
                                        role = Role.Button
                                        onClick(openLabel) {
                                            if (context.launchSafely(app.link, openLabel)) currentOnAppOpened(app)
                                            true
                                        }
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }

        // The front app's name, in a glass pill just left of it.
        val labelEnd = centerX - ringRadius - iconSize / 2 - SphereConfig.LabelGap
        AnimatedVisibility(
            visible = showLabel || count == 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .offset(y = centerY - SphereConfig.LabelHeight / 2)
                .width(labelEnd.coerceAtLeast(0.dp))
                .height(SphereConfig.LabelHeight),
        ) {
            Box(contentAlignment = Alignment.CenterEnd) {
                AnimatedContent(
                    targetState = apps.getOrNull(frontIndex)?.name ?: stringResource(R.string.pin_apps),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "frontAppName",
                ) { name ->
                    Text(
                        text = name,
                        color = palette.foreground,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .liquidGlass(
                                RoundedCornerShape(percent = 50),
                                tint = if (palette.isLight) SphereConfig.LABEL_GLASS_TINT_ON_LIGHT else SphereConfig.LABEL_GLASS_TINT,
                                base = palette.glassBase,
                                edge = palette.glassLine,
                            )
                            .padding(horizontal = SphereConfig.LabelHorizontalPadding, vertical = SphereConfig.LabelVerticalPadding),
                    )
                }
            }
        }
    }
}

/**
 * Degrees between neighbouring apps: [SphereConfig.ANGLE_STEP_DEGREES], widened for fewer pins
 * so a loop of [count] apps spans at least the visible half (180°). Its seam then sits at the
 * edge where apps are invisible, instead of an app jumping from bottom to top in plain view.
 */
internal fun loopStepDegrees(count: Int): Float =
    if (count < 2) SphereConfig.ANGLE_STEP_DEGREES else maxOf(SphereConfig.ANGLE_STEP_DEGREES, 180f / count)

/**
 * Slots from the front to app [index] around a ring of [count] apps, the short way round:
 * in [-count/2, count/2). With 8 apps at rotation 0, app 7 is at -1 (just above the front).
 */
internal fun loopOffset(index: Int, rotation: Float, count: Int): Float {
    if (count == 0) return 0f
    val half = count / 2f
    return ((index - rotation + half).mod(count.toFloat())) - half
}

/**
 * The sphere's fixed shading: the upper-left highlight, the rim shade, and the latitude-line
 * stroke. None of them depend on the rotation, so they're built once and rebuilt only when the
 * sphere's pixel size changes (e.g. a density change), rather than allocated on every turn frame.
 */
private class SphereShading {
    private var size: Size = Size.Unspecified
    var highlight: Brush = SolidColor(Color.Transparent)
        private set
    var rim: Brush = SolidColor(Color.Transparent)
        private set
    var latitudeStroke: Stroke = Stroke()
        private set

    fun update(newSize: Size, latitudeWidthPx: Float) {
        if (newSize == size) return
        size = newSize
        val r = newSize.minDimension / 2
        highlight = Brush.radialGradient(
            listOf(Color.White.copy(alpha = SphereConfig.HIGHLIGHT_ALPHA), Color.Transparent),
            center = Offset(r * 0.55f, r * 0.5f),
            radius = r * 0.95f,
        )
        rim = Brush.radialGradient(
            0.7f to Color.Transparent,
            1f to Color.Black.copy(alpha = SphereConfig.RIM_SHADE_ALPHA),
            center = Offset(r, r),
            radius = r,
        )
        latitudeStroke = Stroke(width = latitudeWidthPx)
    }
}

/** Where an app sits on the rim, in the sphere box's pixels; depth 1 = front, 0 = at/behind the poles. */
private data class SlotPosition(val x: Float, val y: Float, val depth: Float) {
    fun scale(): Float = SphereConfig.MIN_SCALE + (1 - SphereConfig.MIN_SCALE) * depth
    /** Fades faster than it shrinks, so apps near the poles are hints rather than targets. */
    fun alpha(): Float = depth * depth
}
