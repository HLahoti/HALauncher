package com.example.halauncher.ui.home

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
    val showPermissionsPrompt by viewModel.showPermissionsPrompt.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val pinnedApps by viewModel.pinnedApps.collectAsStateWithLifecycle()
    val pinnedPackages by viewModel.pinnedPackages.collectAsStateWithLifecycle()
    val isPinPickerOpen by viewModel.isPinPickerOpen.collectAsStateWithLifecycle()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsStateWithLifecycle()
    val isHowToOpen by viewModel.isHowToOpen.collectAsStateWithLifecycle()
    val isSetupOpen by viewModel.isSetupOpen.collectAsStateWithLifecycle()
    val isDefaultHome by viewModel.isDefaultHome.collectAsStateWithLifecycle()
    val isOnboarding by viewModel.isOnboarding.collectAsStateWithLifecycle()
    val contactsAccess by viewModel.contactsAccess.collectAsStateWithLifecycle()
    val filesAccess by viewModel.filesAccess.collectAsStateWithLifecycle()
    val settingsLocked by viewModel.settingsLocked.collectAsStateWithLifecycle()
    val doubleTapToLock by viewModel.doubleTapToLock.collectAsStateWithLifecycle()
    val reduceIndexSize by viewModel.reduceIndexSize.collectAsStateWithLifecycle()
    val hapticIntensity by viewModel.hapticIntensity.collectAsStateWithLifecycle()
    val defaultSearchCategory by viewModel.defaultSearchCategory.collectAsStateWithLifecycle()
    val moneyApp by viewModel.moneyApp.collectAsStateWithLifecycle()
    val galleryApp by viewModel.galleryApp.collectAsStateWithLifecycle()
    val messagesApp by viewModel.messagesApp.collectAsStateWithLifecycle()
    val calendarApp by viewModel.calendarApp.collectAsStateWithLifecycle()
    val searchPurpose by viewModel.searchPurpose.collectAsStateWithLifecycle()
    val calcResult by viewModel.calcResult.collectAsStateWithLifecycle()
    val moneySymbol = remember { localCurrencySymbol() }

    val requestContactsAccess = rememberContactsAccessRequest(
        requestedBefore = viewModel::hasRequestedContacts,
        onResult = viewModel::onContactsPermissionResult,
    )
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
            requestContactsAccess()
        }
    }

    // Coming back from Settings (e.g. after allowing file access) re-checks permissions.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    val requestFilesAccess = rememberFilesAccessRequest(
        requestedBefore = viewModel::hasRequestedFiles,
        onRequested = viewModel::onFilesAccessRequested,
        onResult = viewModel::onResume,
    )
    val requestDefaultHome = rememberDefaultHomeRequest(onResult = viewModel::onResume)

    BackHandler(enabled = isSearchOpen) { viewModel.closeSearch() }
    BackHandler(enabled = isPinPickerOpen) { viewModel.closePinPicker() }
    BackHandler(enabled = isSettingsOpen) { viewModel.closeSettings() }
    BackHandler(enabled = isHowToOpen) { viewModel.closeHowTo() }
    BackHandler(enabled = isSetupOpen) { viewModel.finishSetup() }

    // Frosts the home content behind an overlay (Android 12+; a no-op before that), deepening
    // as search comes in. Once the overlay is fully in, the blurred content is frozen: it isn't
    // re-recorded, and the blur isn't changed, so it's neither redrawn nor blurred again until
    // the overlay starts to leave.
    val overlayOpen = isSearchOpen || isPinPickerOpen || isSettingsOpen || isHowToOpen || isSetupOpen
    val pinPickerBlur = animateFloatAsState(if (isPinPickerOpen) 1f else 0f, label = "pinPickerBlur")
    val settingsBlur = animateFloatAsState(if (isSettingsOpen) 1f else 0f, label = "settingsBlur")
    val howToBlur = animateFloatAsState(if (isHowToOpen) 1f else 0f, label = "howToBlur")
    val setupBlur = animateFloatAsState(if (isSetupOpen) 1f else 0f, label = "setupBlur")
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
                    val coverage = max(max(max(max(pinPickerBlur.value, settingsBlur.value), howToBlur.value), setupBlur.value), searchReveal.value)
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
            DateDisplay(
                date = date,
                app = calendarApp,
                locked = settingsLocked,
                onChoose = viewModel::openCalendarAppPicker,
                onOpened = viewModel::onEntryOpened,
                onRemove = viewModel::clearCalendarApp,
            )
            Spacer(Modifier.height(HomeScreenConfig.QuickActionsSpacing))
            // Quick actions in a 3×2 grid of universal needs: camera / gallery, calls / messages,
            // payments / internet. All but payments open the device's default app for that category;
            // payments has no such default, so its button lets the user choose. Search opens on swipe up.
            Column(
                // Buttons centre their glyph in a 48dp touch target; shift the grid left by the
                // padding that creates so the glyphs line up with the text above.
                modifier = Modifier.offset(x = -(TouchTarget - HomeScreenConfig.QuickActionIconSize) / 2),
                verticalArrangement = Arrangement.spacedBy(HomeScreenConfig.QuickActionSpacing),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(HomeScreenConfig.QuickActionSpacing)) {
                    QuickActionButton(
                        ImageVector.vectorResource(R.drawable.ic_camera),
                        R.string.open_camera,
                        remember { IntentFactory.cameraIntent() },
                    )
                    ChosenAppButton(
                        app = galleryApp,
                        defaultIntent = remember { IntentFactory.galleryIntent() },
                        namedDescriptionRes = R.string.gallery_app_named,
                        defaultDescriptionRes = R.string.gallery_app_default,
                        defaultOpenLabelRes = R.string.open_gallery,
                        optionsLabelRes = R.string.gallery_app_options,
                        chooseLabelRes = R.string.choose_gallery_app,
                        locked = settingsLocked,
                        modifier = QuickActionButtonShape,
                        onChoose = viewModel::openGalleryAppPicker,
                        onOpened = viewModel::onEntryOpened,
                        onRemove = viewModel::clearGalleryApp,
                    ) {
                        QuickActionGlyph(ImageVector.vectorResource(R.drawable.ic_gallery))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(HomeScreenConfig.QuickActionSpacing)) {
                    QuickActionButton(Icons.Default.Phone, R.string.open_dialer, remember { IntentFactory.dialerIntent() })
                    ChosenAppButton(
                        app = messagesApp,
                        defaultIntent = remember { IntentFactory.messagesIntent() },
                        namedDescriptionRes = R.string.messages_app_named,
                        defaultDescriptionRes = R.string.messages_app_default,
                        defaultOpenLabelRes = R.string.open_messages,
                        optionsLabelRes = R.string.messages_app_options,
                        chooseLabelRes = R.string.choose_messages_app,
                        locked = settingsLocked,
                        modifier = QuickActionButtonShape,
                        onChoose = viewModel::openMessagesAppPicker,
                        onOpened = viewModel::onEntryOpened,
                        onRemove = viewModel::clearMessagesApp,
                    ) {
                        QuickActionGlyph(ImageVector.vectorResource(R.drawable.ic_messages))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(HomeScreenConfig.QuickActionSpacing)) {
                    ChosenAppButton(
                        app = moneyApp,
                        // Payments has no sensible system default, so with none chosen the button
                        // asks the user to pick one instead of opening something.
                        defaultIntent = null,
                        namedDescriptionRes = R.string.money_app_named,
                        defaultDescriptionRes = R.string.money_app_not_set,
                        defaultOpenLabelRes = R.string.choose_money_app,
                        optionsLabelRes = R.string.money_app_options,
                        chooseLabelRes = R.string.choose_money_app,
                        locked = settingsLocked,
                        modifier = QuickActionButtonShape,
                        onChoose = viewModel::openMoneyAppPicker,
                        onOpened = viewModel::onEntryOpened,
                        onRemove = viewModel::clearMoneyApp,
                    ) {
                        Text(
                            text = moneySymbol,
                            color = ThemeConfig.Foreground,
                            fontSize = HomeScreenConfig.MoneySymbolFontSize,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    QuickActionButton(
                        ImageVector.vectorResource(R.drawable.ic_internet),
                        R.string.open_internet,
                        remember { IntentFactory.internetIntent() },
                    )
                }
            }
        }

        // Hidden under overlays once they're fully in, so its touch area (and its exclusion from
        // the back gesture) can't intercept theirs.
        if (!isPinPickerOpen && !isSettingsOpen && !isHowToOpen && !isSetupOpen && !(isSearchOpen && searchFullyIn)) {
            PinnedAppsSphere(
                apps = pinnedApps,
                onAppOpened = viewModel::onEntryOpened,
                onEdit = viewModel::openPinPicker,
                locked = settingsLocked,
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
                settingsLocked = settingsLocked,
                doubleTapToLock = doubleTapToLock,
                reduceIndexSize = reduceIndexSize,
                hapticIntensity = hapticIntensity,
                defaultSearchCategory = defaultSearchCategory,
                onSettingsLockedChange = viewModel::setSettingsLocked,
                onDoubleTapToLockChange = viewModel::setDoubleTapToLock,
                onReduceIndexSizeChange = viewModel::setReduceIndexSize,
                onHapticIntensityChange = viewModel::setHapticIntensity,
                onDefaultSearchCategoryChange = viewModel::setDefaultSearchCategory,
                onHowToUse = viewModel::openHowTo,
                onOpenSetup = viewModel::openSetup,
                contactsAccess = contactsAccess,
                filesAccess = filesAccess,
                onDone = viewModel::closeSettings,
            )
        }

        AnimatedVisibility(visible = isHowToOpen, enter = fadeIn(), exit = fadeOut()) {
            HowToUse(isOnboarding = isOnboarding, onDone = viewModel::resetHome)
        }

        AnimatedVisibility(visible = isSetupOpen, enter = fadeIn(), exit = fadeOut()) {
            SetupScreen(
                isDefaultHome = isDefaultHome,
                contactsAccess = contactsAccess,
                filesAccess = filesAccess,
                isOnboarding = isOnboarding,
                onSetDefaultHome = requestDefaultHome,
                onGrantContacts = requestContactsAccess,
                onGrantFiles = requestFilesAccess,
                onDone = viewModel::finishSetup,
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
            onQueryChange = viewModel::onSearchQueryChanged,
            onCategoryFilterChange = viewModel::onCategoryFilterChanged,
            onEntryOpened = viewModel::onEntryOpened,
            pickTitle = when (searchPurpose) {
                SearchPurpose.PICK_MONEY_APP -> stringResource(R.string.pick_money_app_title, moneySymbol)
                SearchPurpose.PICK_GALLERY_APP -> stringResource(R.string.pick_gallery_app_title)
                SearchPurpose.PICK_MESSAGES_APP -> stringResource(R.string.pick_messages_app_title)
                SearchPurpose.PICK_CALENDAR_APP -> stringResource(R.string.pick_calendar_app_title)
                SearchPurpose.FIND -> null
            },
            onEntryPicked = viewModel::onEntryPicked,
            calcResult = calcResult,
            onCalcResultSelected = { result ->
                viewModel.copyCalcResult(result)
                // Android 13+ shows its own clipboard confirmation, so a toast would stack on
                // top of it. On older versions nothing is shown by the system, so this covers
                // the gap.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.calc_result_copied, result),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            permissions = SearchPermissions(
                contacts = contactsAccess,
                files = filesAccess,
                showSummary = showPermissionsPrompt,
                onRequestContacts = requestContactsAccess,
                onRequestFiles = requestFilesAccess,
                onReview = viewModel::openSetup,
                onDismissSummary = viewModel::dismissPermissionsPrompt,
            ),
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

/** The size and round ripple of a quick-action button. */
private val QuickActionButtonShape = Modifier.size(TouchTarget).clip(CircleShape)

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

/** A quick-action grid glyph: [icon] at the standard size and tint, centred in the touch target. */
@Composable
private fun QuickActionGlyph(icon: ImageVector) {
    Icon(
        imageVector = icon,
        // The button's own click label already says what it does.
        contentDescription = null,
        tint = ThemeConfig.Foreground,
        modifier = Modifier.size(HomeScreenConfig.QuickActionIconSize),
    )
}

/**
 * A home-screen target whose app the user can choose (payments, gallery, messages, the date's
 * calendar). Tapping opens
 * the chosen app; with none chosen it opens [defaultIntent], or — when there's no sensible default
 * (payments), so [defaultIntent] is null — asks the user to choose one. Long-press (firmer haptic)
 * opens a menu to choose, change or remove the app. While [locked], it only opens: no menu, and no
 * choosing when none is set.
 *
 * The description, labels and menu wording come from the given string resources; [glyph] draws the
 * button's face (an icon, the currency symbol for payments, or the date text); [modifier] sizes and
 * shapes it. A face that shows text (the date) passes it as [spokenText], read before the
 * description, since the face itself is hidden from screen readers.
 */
@Composable
private fun ChosenAppButton(
    app: AppInfo?,
    defaultIntent: Intent?,
    @StringRes namedDescriptionRes: Int,
    @StringRes defaultDescriptionRes: Int,
    @StringRes defaultOpenLabelRes: Int,
    @StringRes optionsLabelRes: Int,
    @StringRes chooseLabelRes: Int,
    locked: Boolean,
    onChoose: () -> Unit,
    onOpened: (AppInfo) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    spokenText: String? = null,
    glyph: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val haptics = remember(context) { Haptics(context) }
    var menuOpen by remember { mutableStateOf(false) }
    val appDescription = if (app != null) stringResource(namedDescriptionRes, app.name) else stringResource(defaultDescriptionRes)
    val description = if (spokenText != null) "$spokenText, $appDescription" else appDescription
    val openLabel = if (app != null) stringResource(R.string.open_app, app.name) else stringResource(defaultOpenLabelRes)
    val optionsLabel = stringResource(optionsLabelRes)

    // Opens the chosen app (recording it), else the default, else — with no default — asks to choose.
    val open = {
        when {
            app != null -> if (context.launchSafely(app.link, openLabel)) onOpened(app)
            defaultIntent != null -> context.launchSafely(defaultIntent, openLabel)
            !locked -> onChoose()
        }
    }

    Box {
        Box(
            modifier = modifier
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = openLabel,
                    // No options menu while settings are locked.
                    onLongClickLabel = if (locked) null else optionsLabel,
                    onLongClick = {
                        if (!locked) {
                            haptics.longPress()
                            menuOpen = true
                        }
                    },
                    onClick = { open() },
                )
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                    onClick(openLabel) {
                        open()
                        true
                    }
                    if (!locked) {
                        onLongClick(optionsLabel) {
                            menuOpen = true
                            true
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            glyph()
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (app != null) {
                // Which app it opens now, as a non-interactive first line.
                DropdownMenuItem(text = { Text(app.name) }, onClick = {}, enabled = false)
            }
            DropdownMenuItem(
                text = { Text(stringResource(if (app != null) R.string.change_app else chooseLabelRes)) },
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

/**
 * Today's date. Tapping opens the chosen calendar app, else the device's default calendar at "now";
 * long-press chooses, changes or removes the app, like the customisable quick-action buttons.
 */
@Composable
private fun DateDisplay(
    date: String,
    app: AppInfo?,
    locked: Boolean,
    onChoose: () -> Unit,
    onOpened: (AppInfo) -> Unit,
    onRemove: () -> Unit,
) {
    ChosenAppButton(
        app = app,
        // Rebuilt when the date changes, so it opens the calendar at "now".
        defaultIntent = remember(date) { IntentFactory.calendarIntent() },
        namedDescriptionRes = R.string.calendar_app_named,
        defaultDescriptionRes = R.string.calendar_app_default,
        defaultOpenLabelRes = R.string.open_calendar,
        optionsLabelRes = R.string.calendar_app_options,
        chooseLabelRes = R.string.choose_calendar_app,
        locked = locked,
        onChoose = onChoose,
        onOpened = onOpened,
        onRemove = onRemove,
        modifier = Modifier.padding(vertical = ClockConfig.DateVerticalPadding),
        spokenText = date,
    ) {
        Text(
            text = date,
            color = ThemeConfig.Foreground,
            style = TextStyle(fontSize = ClockConfig.DateFontSize, shadow = ThemeConfig.LegibilityShadow),
        )
    }
}
