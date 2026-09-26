package com.example.halauncher.ui.home

import android.Manifest
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.halauncher.R
import com.example.halauncher.config.ClockConfig
import com.example.halauncher.config.HomeScreenConfig
import com.example.halauncher.config.ThemeConfig
import com.example.halauncher.model.AppInfo
import com.example.halauncher.model.LaunchableElement
import com.example.halauncher.ui.components.ClickToLaunch
import com.example.halauncher.ui.components.launchSafely
import com.example.halauncher.util.Haptics
import com.example.halauncher.util.IntentFactory
import com.example.halauncher.util.localCurrencySymbol
import kotlin.math.max
import kotlin.math.round

@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val time by viewModel.time.collectAsStateWithLifecycle()
    val date by viewModel.date.collectAsStateWithLifecycle()
    val isSearchOpen by viewModel.isSearchOpen.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchCategories by viewModel.availableCategories.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val showFilesPrompt by viewModel.showFilesPrompt.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val pinnedApps by viewModel.pinnedApps.collectAsStateWithLifecycle()
    val pinnedPackages by viewModel.pinnedPackages.collectAsStateWithLifecycle()
    val isPinPickerOpen by viewModel.isPinPickerOpen.collectAsStateWithLifecycle()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsStateWithLifecycle()
    val doubleTapToLock by viewModel.doubleTapToLock.collectAsStateWithLifecycle()
    val reduceIndexSize by viewModel.reduceIndexSize.collectAsStateWithLifecycle()
    val hapticIntensity by viewModel.hapticIntensity.collectAsStateWithLifecycle()
    val defaultSearchCategory by viewModel.defaultSearchCategory.collectAsStateWithLifecycle()
    val moneyApp by viewModel.moneyApp.collectAsStateWithLifecycle()
    val searchPurpose by viewModel.searchPurpose.collectAsStateWithLifecycle()
    val moneySymbol = remember { localCurrencySymbol() }

    val contactsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.onContactsPermissionResult() }
    val context = LocalContext.current
    val searchReveal = rememberSearchRevealState(
        isOpen = { viewModel.isSearchOpen.value },
        open = viewModel::openSearch,
        close = viewModel::closeSearch,
    )
    val searchShown by remember { derivedStateOf { searchReveal.value > 0f } }
    val searchFullyIn by remember { derivedStateOf { searchReveal.value >= 1f } }
    LaunchedEffect(isSearchOpen) { searchReveal.animateTo(isSearchOpen) }

    // Ask for contacts the first time search opens, when it's clear why they're wanted.
    val searchSettledOpen = isSearchOpen && searchFullyIn
    LaunchedEffect(searchSettledOpen) {
        if (searchSettledOpen && viewModel.shouldRequestContactsPermission()) {
            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    // Coming back from Settings (e.g. after allowing file access) re-checks permissions.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    val requestFilesAccess = rememberFilesAccessRequest(onResult = viewModel::onResume)

    BackHandler(enabled = isSearchOpen) { viewModel.closeSearch() }
    BackHandler(enabled = isPinPickerOpen) { viewModel.closePinPicker() }
    BackHandler(enabled = isSettingsOpen) { viewModel.closeSettings() }

    // Frosts the home content behind an overlay (Android 12+; a no-op before that), deepening
    // as search comes in. Once the overlay is fully in, the blurred content is frozen: it isn't
    // re-recorded, and the blur isn't changed, so it's neither redrawn nor blurred again until
    // the overlay starts to leave.
    val overlayOpen = isSearchOpen || isPinPickerOpen || isSettingsOpen
    val pinPickerBlur = animateFloatAsState(if (isPinPickerOpen) 1f else 0f, label = "pinPickerBlur")
    val settingsBlur = animateFloatAsState(if (isSettingsOpen) 1f else 0f, label = "settingsBlur")
    val currentOverlayOpen by rememberUpdatedState(overlayOpen)
    val homeLayer = rememberGraphicsLayer()
    val homeBlur = remember { HomeBlur() }

    // Double-tapping the empty home screen locks the phone (via the accessibility service), when
    // that setting is on; long-pressing it opens the settings panel with a haptic tick.
    val lock = rememberDoubleTapLock()
    val currentDoubleTapToLock by rememberUpdatedState(doubleTapToLock)
    val haptics = remember(context) { Haptics(context) }

    val searchLabel = stringResource(R.string.search_apps)
    val settingsLabel = stringResource(R.string.open_launcher_settings)
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Screen readers can't easily swipe or long-press, so search and settings are offered as actions too.
            .semantics {
                if (!overlayOpen) {
                    customActions = listOf(
                        CustomAccessibilityAction(searchLabel) { viewModel.openSearch(); true },
                        CustomAccessibilityAction(settingsLabel) { viewModel.openSettings(); true },
                    )
                }
            }
            // Swiping up anywhere on the bare home screen pulls search in with the finger; on
            // release it settles fully open or back out. Drags the sphere turns with are
            // consumed there first, so they never reach this.
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                // Only swipes that start on the bare home screen; drags inside an open overlay are its own.
                var active = false
                detectVerticalDragGestures(
                    onDragStart = {
                        active = !currentOverlayOpen
                        tracker.resetTracking()
                    },
                    onVerticalDrag = { change, dy ->
                        if (active) {
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            searchReveal.dragBy(dy)
                        }
                    },
                    onDragEnd = { if (active) searchReveal.settle(tracker.calculateVelocity().y) },
                    onDragCancel = { if (active) searchReveal.settle(0f) },
                )
            }
            // A double-tap on empty space locks the phone (when enabled); a long-press opens
            // settings with a haptic tick. Taps on the quick actions, sphere and clock are consumed
            // there first, so these only fire on the bare home screen; and they're held back while
            // an overlay is open, matching the swipe-to-search guard above.
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { if (!currentOverlayOpen && currentDoubleTapToLock) lock() },
                    onLongPress = {
                        if (!currentOverlayOpen) {
                            haptics.tick()
                            viewModel.openSettings()
                        }
                    },
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Read in the draw phase, not in composition, so a swipe redraws without recomposing.
                .drawWithContent {
                    val coverage = max(max(pinPickerBlur.value, settingsBlur.value), searchReveal.value)
                    val frozen = coverage >= 1f && homeBlur.recorded && homeLayer.size == size.toIntSize()
                    if (!frozen) {
                        homeLayer.record { this@drawWithContent.drawContent() }
                        homeBlur.recorded = true
                    }
                    val rawRadius = HomeScreenConfig.BlurBehindOverlay.toPx() * coverage
                    // Snap the finger-driven radius to a few steps, so the GPU re-blurs at a
                    // handful of radii across the gesture rather than a fresh one every frame.
                    val quantum = HomeScreenConfig.BlurRadiusQuantum.toPx()
                    val radius = if (quantum > 0f) round(rawRadius / quantum) * quantum else rawRadius
                    // Setting a new effect would make the blur run again, so only when it changes.
                    if (radius != homeBlur.radius) {
                        homeBlur.radius = radius
                        homeLayer.renderEffect = if (radius > 0f) BlurEffect(radius, radius, TileMode.Clamp) else null
                        homeLayer.clip = radius > 0f
                    }
                    drawLayer(homeLayer)
                }
                .systemBarsPadding()
                .padding(start = HomeScreenConfig.StartMargin, top = HomeScreenConfig.TopMargin),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top,
        ) {
            TimeDisplay(time)
            DateDisplay(date)
            Spacer(Modifier.height(HomeScreenConfig.QuickActionsSpacing))
            // Quick actions in a 2×2 grid: camera, reminders / phone, money. Search opens on swipe up.
            Column(
                // Buttons centre their glyph in a 48dp touch target; shift the grid left by the
                // padding that creates so the glyphs line up with the text above.
                modifier = Modifier.offset(x = -(TouchTarget - HomeScreenConfig.QuickActionIconSize) / 2),
                verticalArrangement = Arrangement.spacedBy(HomeScreenConfig.QuickActionSpacing),
            ) {
                // Resolving the installed reminders app queries PackageManager, so do it once.
                val remindersIntent = remember(context) { IntentFactory.remindersIntent(context) }
                Row(horizontalArrangement = Arrangement.spacedBy(HomeScreenConfig.QuickActionSpacing)) {
                    QuickActionButton(
                        ImageVector.vectorResource(R.drawable.ic_camera),
                        R.string.open_camera,
                        remember { IntentFactory.cameraIntent() },
                    )
                    QuickActionButton(Icons.Default.Check, R.string.open_reminders, remindersIntent)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(HomeScreenConfig.QuickActionSpacing)) {
                    QuickActionButton(Icons.Default.Phone, R.string.open_dialer, remember { IntentFactory.dialerIntent() })
                    MoneyButton(
                        symbol = moneySymbol,
                        app = moneyApp,
                        onChoose = viewModel::openMoneyAppPicker,
                        onOpened = viewModel::onEntryOpened,
                        onRemove = viewModel::clearMoneyApp,
                    )
                }
            }
        }

        // Hidden under overlays once they're fully in, so its touch area (and its exclusion from
        // the back gesture) can't intercept theirs.
        if (!isPinPickerOpen && !isSettingsOpen && !(isSearchOpen && searchFullyIn)) {
            PinnedAppsSphere(
                apps = pinnedApps,
                onAppOpened = viewModel::onEntryOpened,
                onEdit = viewModel::openPinPicker,
                // Makes way for search as it comes in.
                modifier = Modifier
                    .systemBarsPadding()
                    .graphicsLayer { alpha = (1f - 2f * searchReveal.value).coerceAtLeast(0f) },
            )
        }

        AnimatedVisibility(visible = isPinPickerOpen, enter = fadeIn(), exit = fadeOut()) {
            PinnedAppsPicker(
                apps = installedApps,
                pinnedPackages = pinnedPackages,
                onToggle = viewModel::togglePinned,
                onReorder = viewModel::reorderPinned,
                onDone = viewModel::closePinPicker,
            )
        }

        AnimatedVisibility(visible = isSettingsOpen, enter = fadeIn(), exit = fadeOut()) {
            SettingsPanel(
                doubleTapToLock = doubleTapToLock,
                reduceIndexSize = reduceIndexSize,
                hapticIntensity = hapticIntensity,
                defaultSearchCategory = defaultSearchCategory,
                onDoubleTapToLockChange = viewModel::setDoubleTapToLock,
                onReduceIndexSizeChange = viewModel::setReduceIndexSize,
                onHapticIntensityChange = viewModel::setHapticIntensity,
                onDefaultSearchCategoryChange = viewModel::setDefaultSearchCategory,
                onDone = viewModel::closeSettings,
            )
        }

        // Always composed, so a swipe only has to move it; composing it mid-swipe stalls the
        // finger. While hidden it sits off screen (out of reach of touches) and out of the
        // accessibility tree.
        SearchOverlay(
            // Rises from below and fades in, in step with the finger.
            modifier = Modifier
                .graphicsLayer {
                    val reveal = searchReveal.value
                    alpha = reveal
                    translationY = if (reveal == 0f) {
                        size.height
                    } else {
                        (1f - reveal) * HomeScreenConfig.SearchRevealDistance.toPx()
                    }
                }
                .then(if (searchShown) Modifier else Modifier.clearAndSetSemantics { }),
            // The keyboard waits until search has settled, rather than jumping up mid-swipe.
            isFullyIn = searchSettledOpen,
            reveal = searchReveal,
            query = query,
            results = results,
            categories = searchCategories,
            categoryFilter = categoryFilter,
            showFilesPrompt = showFilesPrompt,
            onQueryChange = viewModel::onSearchQueryChanged,
            onCategoryFilterChange = viewModel::onCategoryFilterChanged,
            onEntryOpened = viewModel::onEntryOpened,
            onRequestFilesAccess = requestFilesAccess,
            onDismissFilesPrompt = viewModel::dismissFilesPrompt,
            pickTitle = if (searchPurpose == SearchPurpose.PICK_MONEY_APP) {
                stringResource(R.string.pick_money_app_title, moneySymbol)
            } else {
                null
            },
            onEntryPicked = viewModel::onEntryPicked,
        )
    }
}

@Composable
private fun TimeDisplay(time: String) {
    val context = LocalContext.current
    val label = stringResource(R.string.open_alarms)
    // Resolving the clock app queries PackageManager, so do it once rather than every minute.
    val alarmIntent = remember(context) { IntentFactory.alarmIntent(context) }
    val element = remember(time, label, alarmIntent) {
        LaunchableElement(
            displayContent = {
                Text(
                    text = time,
                    color = ThemeConfig.Foreground,
                    style = TextStyle(
                        fontSize = ClockConfig.TimeFontSize,
                        fontWeight = FontWeight.Bold,
                        shadow = ThemeConfig.LegibilityShadow,
                    ),
                )
            },
            launchIntent = alarmIntent,
            actionLabel = label,
        )
    }
    ClickToLaunch(element)
}

/** What the home content's blur layer last had: whether it was recorded, and its blur radius. */
private class HomeBlur {
    var recorded = false
    var radius = 0f
}

/** Material's minimum touch target, which IconButton also uses. */
private val TouchTarget = 48.dp

/**
 * A small glyph in the quick-action grid that opens [intent], with a 48dp touch target and a
 * round ripple. [labelRes] is read out by screen readers.
 */
@Composable
private fun QuickActionButton(icon: ImageVector, @StringRes labelRes: Int, intent: Intent) {
    val label = stringResource(labelRes)
    val element = remember(icon, label, intent) {
        LaunchableElement(
            displayContent = {
                Box(modifier = Modifier.size(TouchTarget), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        // The click label already says what it does.
                        contentDescription = null,
                        tint = ThemeConfig.Foreground,
                        modifier = Modifier.size(HomeScreenConfig.QuickActionIconSize),
                    )
                }
            },
            launchIntent = intent,
            actionLabel = label,
        )
    }
    ClickToLaunch(element, modifier = Modifier.clip(CircleShape))
}

/**
 * The money button: a currency symbol that opens the user's chosen payments app. With none
 * chosen yet, tapping asks for one ([onChoose] opens the app picker). Long-press (with a
 * firmer haptic) opens a menu to change or remove it.
 */
@Composable
private fun MoneyButton(
    symbol: String,
    app: AppInfo?,
    onChoose: () -> Unit,
    onOpened: (AppInfo) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = remember(context) { Haptics(context) }
    var menuOpen by remember { mutableStateOf(false) }
    val description = if (app != null) {
        stringResource(R.string.money_app_named, app.name)
    } else {
        stringResource(R.string.money_app_not_set)
    }
    val openLabel = if (app != null) stringResource(R.string.open_app, app.name) else stringResource(R.string.choose_money_app)
    val optionsLabel = stringResource(R.string.money_app_options)

    Box {
        Box(
            modifier = Modifier
                .size(TouchTarget)
                .clip(CircleShape)
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = openLabel,
                    onLongClickLabel = optionsLabel,
                    onLongClick = {
                        haptics.longPress()
                        menuOpen = true
                    },
                    onClick = {
                        when {
                            app == null -> onChoose()
                            context.launchSafely(app.link, openLabel) -> onOpened(app)
                        }
                    },
                )
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                    onClick(openLabel) {
                        if (app == null) onChoose() else if (context.launchSafely(app.link, openLabel)) onOpened(app)
                        true
                    }
                    onLongClick(optionsLabel) {
                        menuOpen = true
                        true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = symbol,
                color = ThemeConfig.Foreground,
                fontSize = HomeScreenConfig.MoneySymbolFontSize,
                fontWeight = FontWeight.Medium,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (app != null) {
                // Which app it opens now, as a non-interactive first line.
                DropdownMenuItem(text = { Text(app.name) }, onClick = {}, enabled = false)
            }
            DropdownMenuItem(
                text = { Text(stringResource(if (app != null) R.string.change_app else R.string.choose_money_app)) },
                onClick = {
                    menuOpen = false
                    onChoose()
                },
            )
            if (app != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove)) },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    },
                )
            }
        }
    }
}

@Composable
private fun DateDisplay(date: String) {
    val label = stringResource(R.string.open_calendar)
    val element = remember(date, label) {
        LaunchableElement(
            displayContent = {
                Text(
                    text = date,
                    color = ThemeConfig.Foreground,
                    style = TextStyle(fontSize = ClockConfig.DateFontSize, shadow = ThemeConfig.LegibilityShadow),
                )
            },
            // Rebuilt when the date changes, so it opens the calendar at "now".
            launchIntent = IntentFactory.calendarIntent(),
            actionLabel = label,
        )
    }
    ClickToLaunch(element, modifier = Modifier.padding(vertical = ClockConfig.DateVerticalPadding))
}
