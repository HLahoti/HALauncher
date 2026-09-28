package com.example.halauncher.config

import android.os.Process
import android.os.VibrationEffect
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.halauncher.model.SearchCategory

/*
 * Every tunable look-and-feel value in the launcher, in one place.
 *
 * Units: `dp` for sizes and distances, `sp` for text (scales with the user's font size),
 * alpha values from 0f (invisible) to 1f (opaque), durations in milliseconds.
 *
 * Integration details that aren't preferences (clock app package names, Samsung's tab
 * codes) live in IntentFactory instead.
 */

/** Colours shared across the whole launcher. */
object ThemeConfig {
    /** Text and icon colour drawn over the wallpaper. */
    val Foreground = Color.White

    /** Shadow under home-screen text so it stays legible on light wallpapers. */
    val LegibilityShadow = Shadow(
        color = Color.Black.copy(alpha = 0.45f),
        offset = Offset(0f, 2f),
        blurRadius = 6f,
    )
}

/** Layout of the bare home screen (clock column and quick actions). */
object HomeScreenConfig {
    /** Left margin of the clock column. */
    val StartMargin = 24.dp

    /** Gap from the top of the screen (below the status bar) to the time. */
    val TopMargin = 96.dp

    /** Gap between the date and the quick actions. */
    val QuickActionsSpacing = 32.dp

    /**
     * Size of the quick-action glyphs (the camera, the tick for reminders, the phone for the
     * dialer). Their touch targets are always 48dp.
     */
    val QuickActionIconSize = 28.dp

    /**
     * How far a finger travels upwards on the home screen to bring search fully in. Search
     * rises this far with the finger, fading in as it goes.
     */
    val SearchRevealDistance = 240.dp

    /**
     * Released part-way, search finishes opening past this fraction of [SearchRevealDistance]
     * and falls back below it, unless the finger was flicking faster than
     * [SearchRevealFlingVelocity] (per second), which wins either way.
     */
    const val SEARCH_REVEAL_OPEN_FRACTION = 0.5f
    val SearchRevealFlingVelocity = 600.dp

    /** Springiness of search settling into place after the finger lifts. */
    const val SEARCH_REVEAL_STIFFNESS = 500f

    /**
     * Gap between the touch targets in the 2×2 grid of quick actions (camera, reminders, phone,
     * money), both across and down.
     */
    val QuickActionSpacing = 4.dp

    /** Size of the currency symbol on the money button, sized to sit with the other glyphs. */
    val MoneySymbolFontSize = 24.sp

    /**
     * Symbol for the money button when the device locale has no country, or its currency symbol
     * is a multi-letter code (like "US$") too wide for the button. Otherwise the locale's own
     * symbol is used (₹, $, €, £, …).
     */
    const val MONEY_SYMBOL_FALLBACK = "$"

    /**
     * Blur applied to the home content (time, date, quick actions, sphere) behind the search
     * overlay and the pin picker (Android 12+ only; older versions show it unblurred). Only
     * the launcher's own content can be blurred; the wallpaper is drawn by the system and
     * stays sharp. 0.dp turns the effect off. Gaussian blur cost grows with the radius, so
     * this stays modest.
     */
    val BlurBehindOverlay = 16.dp

    /**
     * Step the blur radius is snapped to while a swipe drives it. A finger-tracked reveal would
     * otherwise ask the GPU for a slightly different blur every frame; rounding to this step means
     * it re-blurs at only a handful of radii across the gesture, for the same look at less cost.
     * A radius under half a step rounds to 0 (no effect), so a barely-open swipe isn't blurred at
     * all. 0.dp turns snapping off (a fresh radius every frame).
     */
    val BlurRadiusQuantum = 3.dp
}

/** The time and date at the top of the home screen. */
object ClockConfig {
    /** Font size of the time. */
    val TimeFontSize = 72.sp

    /** Font size of the date line. */
    val DateFontSize = 19.sp

    /** Extra vertical space around the date, which also enlarges its tap area. */
    val DateVerticalPadding = 4.dp

    /** Time format when the device is set to 24-hour time (SimpleDateFormat pattern). */
    const val TIME_PATTERN_24H = "HH:mm"

    /** Time format when the device is set to 12-hour time (SimpleDateFormat pattern). */
    const val TIME_PATTERN_12H = "h:mm"

    /**
     * Fields to show in the date. Android reorders them and adds punctuation for the
     * device's locale ("Wednesday, 23 September" in en-GB; "Wednesday, September 23" in
     * en-US). EEEE = weekday, d = day, MMMM = month name; e.g. "EEEdMMM" gives "Wed, 23 Sep".
     */
    const val DATE_SKELETON = "EEEEdMMMM"

    /**
     * How long the clock keeps listening after the screen stops observing it (e.g. screen off).
     * The time updates on the system's minute tick; the date at startup and at midnight.
     */
    const val STOP_TICKING_AFTER_MS = 5_000L
}

/** The app search overlay. */
object SearchConfig {
    /**
     * Darkness of the backdrop behind the search overlay. The home content under it is
     * blurred (HomeScreenConfig.BlurBehindOverlay), but the wallpaper can't be, so this is
     * what separates results from a busy wallpaper.
     */
    const val SCRIM_ALPHA = 0.72f

    /** Side margins of the search field and results. */
    val HorizontalPadding = 16.dp

    /** Darkness of the search field's glass. */
    const val FIELD_GLASS_TINT = 0.2f

    /** Opacity of the "Search apps and contacts" hint in the empty field. */
    const val PLACEHOLDER_ALPHA = 0.6f

    /** Gap above the search field (below the status bar). */
    val TopSpacing = 40.dp

    /** Gap below the results (above the keyboard, or the navigation bar when it's hidden). */
    val BottomSpacing = 24.dp

    /** Gap between the search field and the first result. */
    val ResultsSpacing = 8.dp

    /** App icon size in a result row. */
    val ResultIconSize = 40.dp

    /** Gap between a result's icon and its name. */
    val ResultIconTextGap = 16.dp

    /** Opacity of a result's secondary line (a contact's phone number, a file's folder). */
    const val RESULT_DETAIL_ALPHA = 0.6f

    /** Gap between a result's name and its secondary line. */
    val ResultDetailGap = 2.dp

    /** Inner padding of each result row. */
    val ResultRowPadding = 8.dp

    /** Opacity of the "More results" divider between frequent and disk results. */
    const val SECTION_LABEL_ALPHA = 0.6f

    /** Padding around that divider. */
    val SectionLabelPadding = 12.dp

    /** Darkness of the glass "Allow file search" prompt. */
    const val PROMPT_GLASS_TINT = 0.2f

    /** Size of the category icon (app, contact, file…) at the end of each result row. */
    val CategoryBadgeSize = 18.dp

    /** Opacity of that category icon. */
    const val CATEGORY_BADGE_ALPHA = 0.6f

    /**
     * Inset of the category icon drawn in place of an entry's own image when it has none
     * (e.g. a contact without a photo), so it looks balanced beside real app icons.
     */
    val PlaceholderIconPadding = 6.dp
}

/** How search is split between the in-memory frequent layer and the on-disk full index. */
object SearchLayersConfig {
    /**
     * How many entries the in-memory frequent layer (trie) holds. These answer instantly on
     * every keystroke; everything else waits for the disk layer.
     */
    const val FREQUENT_LAYER_SIZE = 60

    /**
     * While there isn't enough history to fill the frequent layer (e.g. a fresh install), top it
     * up with apps so typing still gets instant results. false keeps it strictly to history.
     */
    const val SEED_FREQUENT_LAYER = true

    /**
     * How long typing must pause, with nothing opened, before the disk layer is searched. Each
     * keystroke restarts the wait.
     */
    const val DISK_SEARCH_DELAY_MS = 300L

    /**
     * How many full-text matches are fetched from disk for ranking. Higher finds good matches
     * among very common prefixes at the cost of a little more work per search.
     */
    const val DISK_CANDIDATE_LIMIT = 1000

    /** How many ranked disk results are shown under "More results". */
    const val DISK_RESULT_LIMIT = 50

    /** Days of open history kept. Older opens stop counting and are deleted. */
    const val INTERACTION_RETENTION_DAYS = 120

    /**
     * How often open history older than [INTERACTION_RETENTION_DAYS] is deleted, in hours. It's
     * ignored by ranking from the moment it expires; this only decides when it leaves the disk.
     */
    const val INTERACTION_PRUNE_INTERVAL_HOURS = 24L
}

/**
 * The background index updater: a low-priority thread that watches apps, contacts and files for
 * changes and folds bursts of them into single bulk updates of the disk index.
 */
object IndexUpdaterConfig {
    /**
     * Scheduling priority of the updater's thread (android.os.Process values: higher numbers are
     * lower priority). THREAD_PRIORITY_BACKGROUND (10) lets the UI and other apps go first;
     * THREAD_PRIORITY_LOWEST (19) yields even more but makes updates slower on a busy phone.
     */
    const val THREAD_PRIORITY = Process.THREAD_PRIORITY_BACKGROUND

    // --- What to watch ---

    /** Re-index apps when any is installed, updated, removed, enabled or disabled. */
    const val WATCH_APPS = true

    /** Re-index contacts when the contacts database changes (needs the contacts permission). */
    const val WATCH_CONTACTS = true

    /** Re-index files when MediaStore reports changes (needs All files access). */
    const val WATCH_FILES = true

    // --- Debouncing and batching ---

    /**
     * How long changes must stop arriving before they're written. Each new change restarts the
     * wait, so a burst (a camera series, a contacts sync) becomes one update.
     */
    const val QUIET_PERIOD_MS = 2_000L

    /**
     * The longest a change waits, however busy things are, measured from the first change in
     * the burst. Stops a steady trickle of changes from postponing updates forever.
     */
    const val MAX_BATCH_DELAY_MS = 15_000L

    /**
     * Up to this many changed files are looked up individually. Beyond it a full (differential)
     * file re-sync is cheaper, so the batch switches to that.
     */
    const val TARGETED_UPDATE_LIMIT = 300

    // --- Battery and heat guardrails ---

    /**
     * Battery saver pauses indexing entirely (true) or only slows it down (false). Changes keep
     * being collected while paused and are written once battery saver turns off.
     */
    const val PAUSE_ON_BATTERY_SAVER = true

    /**
     * Updates you trigger yourself (e.g. right after granting a permission) still run under
     * battery saver. Heat pauses them regardless.
     */
    const val URGENT_BYPASSES_BATTERY_SAVER = true

    /**
     * Thermal status (PowerManager.THERMAL_STATUS_*, Android 10+) from which indexing slows down.
     * 2 = MODERATE. (0 none, 1 light, 2 moderate, 3 severe, 4 critical, 5 emergency, 6 shutdown.)
     */
    const val THERMAL_SLOW_FROM = 2

    /** Thermal status from which indexing pauses until the phone cools. 3 = SEVERE. */
    const val THERMAL_PAUSE_FROM = 3

    /** How much longer the debounce waits are when slowed down (by heat or battery saver). */
    const val SLOW_MODE_DELAY_MULTIPLIER = 4L

    /** When slowed down, a rest between reading each source (apps, contacts, files). */
    const val SLOW_MODE_STEP_PAUSE_MS = 500L

    // --- Database (write-ahead log) ---

    /**
     * SQLite folds the write-ahead log back into the database once it reaches this many pages
     * (4 KB each). Larger means fewer, bigger checkpoints.
     */
    const val WAL_AUTOCHECKPOINT_PAGES = 1000

    /**
     * Fold the write-ahead log into the database (a passive checkpoint, which never blocks reads
     * or writes) when the launcher goes to the background, rather than after every update. Only
     * [WAL_AUTOCHECKPOINT_PAGES] still checkpoints in the foreground, as a cap on the log's size.
     */
    const val CHECKPOINT_ON_BACKGROUND = true
}

/**
 * Weights for the default search ranking (ContextMatchRanker). A result's score is:
 *
 *   TEXT_WEIGHT × text match  +  FREQUENCY_WEIGHT × frequency  +  RECENCY_WEIGHT × recency
 *   +  TIME_OF_DAY_WEIGHT × time-of-day fit  +  category prior
 *
 * Each part is 0–1 (text match up to MATCH_EXACT + SHORT_NAME_BONUS). The same scoring with an
 * empty query (text match 0) picks which entries the frequent layer keeps.
 */
object RankingConfig {
    /** How much the text match counts. Keep it the largest so relevance beats habit. */
    const val TEXT_WEIGHT = 1.0

    /*
     * The three usage weights below add up to 0.45 at most. That lets a heavily used entry
     * overtake the next text-match tier up (e.g. a favourite "Maps Go" above a never-opened
     * exact "Maps"), but never jump two tiers. Raising them makes habit count for more.
     */

    /** How much opening an entry often counts. */
    const val FREQUENCY_WEIGHT = 0.2

    /** How much opening an entry recently counts. */
    const val RECENCY_WEIGHT = 0.15

    /** How much "usually opened around this time of day" counts. */
    const val TIME_OF_DAY_WEIGHT = 0.1

    // --- Text match tiers (the best one that applies is used) ---

    /** The name is exactly the query. */
    const val MATCH_EXACT = 1.0

    /** The name starts with the query ("spo" → "Spotify"). */
    const val MATCH_NAME_PREFIX = 0.85

    /** Each query word starts a word of the name, in order ("go ma" → "Google Maps"). */
    const val MATCH_WORD_PREFIX_IN_ORDER = 0.65

    /** Each query word starts a word of the name, in any order ("ma go" → "Google Maps"). */
    const val MATCH_WORD_PREFIX = 0.55

    /** Any other full-text match. */
    const val MATCH_OTHER = 0.3

    /** Up to this much extra when the query covers most of the name (favours shorter names). */
    const val SHORT_NAME_BONUS = 0.1

    // --- Usage ---

    /**
     * Opens at which frequency reaches ~63% of its maximum; it approaches 1 more slowly after.
     * Higher values keep separating heavy users' favourites for longer.
     */
    const val FREQUENCY_SATURATION = 10.0

    /** Days for the recency score to halve after the last open. */
    const val RECENCY_HALF_LIFE_DAYS = 7.0

    /** Opens within this many hours of the current time of day count as "around now". */
    const val TIME_OF_DAY_WINDOW_HOURS = 1.5

    /** Opens needed before time of day counts at all, so one open isn't a pattern. */
    const val TIME_OF_DAY_MIN_OPENS = 3

    /** Constant boost per category, e.g. to favour apps over similarly named files. */
    val CATEGORY_PRIORS: Map<SearchCategory, Double> = mapOf(
        SearchCategory.APP to 0.1,
        SearchCategory.CONTACT to 0.05,
        SearchCategory.FILE to 0.0,
    )
}

/** The glass category filter (All / Apps / Contacts / …) under the search field. */
object CategoryFilterConfig {
    /** Width of each segment. The bar is this times the number of segments. */
    val SegmentWidth = 56.dp

    /** Height of the bar. */
    val Height = 40.dp

    /** Size of the category icons. */
    val IconSize = 20.dp

    /** Font size of the "All" label. */
    val AllLabelFontSize = 13.sp

    /** Opacity of icons and labels that aren't selected. */
    const val UNSELECTED_ALPHA = 0.6f

    /** Gap between the sliding thumb and the bar's edge. */
    val ThumbInset = 3.dp

    /** Extra white on the thumb so it reads as a brighter piece of glass. */
    const val THUMB_SHEEN_ALPHA = 0.18f

    /** Gap between the search field and the bar, and between the bar and the results. */
    val VerticalSpacing = 10.dp

    /**
     * How far a sideways swipe elsewhere in search (off the bar) must travel to step the filter
     * to the next or previous category. Each swipe steps once.
     */
    val SwipeStepDistance = 48.dp

    /** Spring the thumb uses to glide to a tapped segment, or settle after a drag. */
    val SettleSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow,
    )
}

/** The small copies of app icons the launcher keeps in memory and on disk (AppIconCache). */
object IconCacheConfig {
    /**
     * Size icons are stored at: the largest the launcher draws them (the front of the pinned
     * sphere, or a search result), so they're never scaled up.
     */
    val IconSize get() = maxOf(SphereConfig.IconSize, SearchConfig.ResultIconSize)
}

/** The glass half-sphere of pinned apps on the right edge of the home screen. */
object SphereConfig {
    /** Radius of the sphere. About half of it shows; the rest is off the right edge. */
    val Radius = 150.dp

    /** How far inside the sphere's outline the apps sit (the rim they roll along). */
    val IconRingInset = 34.dp

    /** Size of an app at the front. Apps shrink from this as they roll away. */
    val IconSize = 46.dp

    /**
     * Angle between neighbouring apps on the rim. Smaller packs more apps onto the visible
     * face (90° ÷ this many above and below the front); larger spaces them out. With few pins
     * the spacing widens to 180° ÷ pins, so the loop from last back to first always closes at
     * the sphere's edge, out of sight.
     */
    const val ANGLE_STEP_DEGREES = 26f

    /** Size of an app at the top/bottom pole relative to the front (1f = no shrinking). */
    const val MIN_SCALE = 0.45f

    /** Moves the sphere up (negative) or down from the vertical centre of the screen. */
    val VerticalOffset = 0.dp

    /** Darkness of the sphere's glass. */
    const val GLASS_TINT = 0.14f

    /** Brightness of the light reflection on the upper left. */
    const val HIGHLIGHT_ALPHA = 0.2f

    /** Darkness at the sphere's rim, for a sense of volume. */
    const val RIM_SHADE_ALPHA = 0.25f

    /** Opacity of the latitude lines that roll across the sphere as it turns. 0f hides them. */
    const val LATITUDE_ALPHA = 0.14f

    /** How open the latitude ellipses are (0f = flat lines, 1f = circles). */
    const val LATITUDE_TILT = 0.2f

    /** Stroke width of the latitude lines. */
    val LatitudeWidth = 1.dp

    /** Extra size of the glass halo around the front (selected) app. */
    val FrontHaloPadding = 6.dp

    /** Inset of the "+" shown while nothing is pinned. */
    val AddIconPadding = 10.dp

    /**
     * How far a flick carries on: the release speed times this many seconds, in apps. Higher
     * makes flicks travel further.
     */
    const val FLING_SECONDS = 0.18f

    /** Spring that settles an app onto the front after the finger lifts. */
    val SettleSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessLow,
    )

    // --- The front app's name label ---

    /** Gap between the label and the front app. */
    val LabelGap = 10.dp

    /** Height of the label's row (the pill is centred in it). */
    val LabelHeight = 40.dp

    /** Padding inside the label pill. */
    val LabelHorizontalPadding = 14.dp
    val LabelVerticalPadding = 8.dp

    /** Darkness of the label's glass. */
    const val LABEL_GLASS_TINT = 0.25f

    /** How long the label stays after the sphere stops turning. */
    const val LABEL_LINGER_MS = 1200L
}

/** The list for choosing which apps are pinned to the sphere (long-press the sphere). */
object PinPickerConfig {
    /**
     * Most apps the sphere holds. Once this many are pinned, the rest of the list is disabled
     * until one is unpinned. Uninstalled pins don't count.
     */
    const val MAX_PINNED = 8

    /** Opacity of apps that can't be pinned because the sphere is full. */
    const val DISABLED_ALPHA = 0.4f

    /** Left padding of the title in the header pill. */
    val HeaderStartPadding = 20.dp

    /** Opacity of the empty checkbox on unpinned apps. */
    const val UNCHECKED_ALPHA = 0.6f

    /** Opacity of the drag handle on pinned apps. */
    const val DRAG_HANDLE_ALPHA = 0.6f

    /** Size of the drag handle icon on pinned apps. */
    val DragHandleIconSize = 24.dp

    /** Touch area of the drag handle; unpinned apps leave the same gap so checkboxes line up. */
    val DragHandleTouchSize = 48.dp

    /** How much a pinned app grows while it's being dragged. */
    const val DRAGGED_SCALE = 1.03f

    /** Opacity of the white wash behind a pinned app while it's being dragged. */
    const val DRAGGED_BACKGROUND_ALPHA = 0.12f

    /** Corner radius of that wash. */
    val DraggedCornerRadius = 16.dp
}

/** The imitation "liquid glass" surface used by the sphere, search and pickers. */
object GlassConfig {
    /** Default darkness of the glass base; surfaces can override it (e.g. SphereConfig.GLASS_TINT). */
    const val DEFAULT_TINT = 0.12f

    /** White sheen from top to middle to bottom. */
    const val SHEEN_TOP_ALPHA = 0.26f
    const val SHEEN_MIDDLE_ALPHA = 0.12f
    const val SHEEN_BOTTOM_ALPHA = 0.16f

    /** Width of the light-catching edge. */
    val EdgeWidth = 1.dp

    /** Brightness of the edge from top to middle to bottom. */
    const val EDGE_TOP_ALPHA = 0.55f
    const val EDGE_MIDDLE_ALPHA = 0.12f
    const val EDGE_BOTTOM_ALPHA = 0.28f
}

/** The settings panel opened by long-pressing the home screen. */
object SettingsPanelConfig {
    /** Corner radius of each glass setting card. */
    val CardCornerRadius = 20.dp

    /** Inner padding of a card, and the gap between its text and its switch. */
    val CardPadding = 16.dp

    /** Gap between the setting cards. */
    val CardSpacing = 12.dp

    /** Darkness of a card's glass, matched to the search field. */
    const val GlassTint = 0.2f

    /** Gap between a card's title and its description. */
    val TitleDescriptionGap = 4.dp

    /** Opacity of the explanatory description under each title. */
    const val DESCRIPTION_ALPHA = 0.7f

    /** Opacity of the switch's thumb and outline when the setting is off. */
    const val SWITCH_OFF_ALPHA = 0.6f

    /** Gap between the choice chips of a multiple-choice setting, and above the chip row. */
    val ChoiceChipSpacing = 8.dp

    /** Inner vertical padding of a choice chip. */
    val ChoiceChipVerticalPadding = 10.dp

    /** Opacity of an unselected choice chip's label and outline. */
    const val CHOICE_UNSELECTED_ALPHA = 0.6f

    /**
     * Selectable stops between off and full on the vibration-intensity slider (the Slider `steps`
     * value: 9 gives 0%, 10%, … 100%). Discrete stops keep the live haptic preview from firing on
     * every pixel of a drag.
     */
    const val HAPTIC_INTENSITY_STEPS = 9

    /** Opacity of the intensity slider's inactive (unfilled) track. */
    const val SLIDER_INACTIVE_TRACK_ALPHA = 0.3f
}

/** Vibration used for the sphere's tick as each app reaches the front. */
object HapticsConfig {
    /**
     * The vibrator's own tuned haptic primitive used for the tick on Android 11+, which covers
     * most phones: a `VibrationEffect.Composition.PRIMITIVE_*` value. PRIMITIVE_CLICK is a firm
     * tick, PRIMITIVE_TICK a lighter one. Its strength is scaled by the user's vibration intensity
     * (the settings slider). If the device doesn't offer this primitive, the tick falls back to
     * the paths in Haptics.
     */
    const val TICK_PRIMITIVE = VibrationEffect.Composition.PRIMITIVE_CLICK

    /**
     * The vibration intensity a fresh install starts at, from 0f (off) to 1f (strongest), until
     * the user changes it with the settings slider. It scales the tick: the primitive's strength
     * on Android 11+, and the one-shot amplitude on Android 8–10.
     */
    const val DEFAULT_TICK_INTENSITY = 1.0f

    /**
     * Length in milliseconds of the fallback one-shot tick on Android 8–10, which have no scalable
     * primitive. Its strength comes from the chosen intensity (as an amplitude) on devices with
     * amplitude control; others buzz at a fixed strength for this long.
     */
    const val FALLBACK_TICK_MS = 14L

    /** Long-press confirmation on older phones (Android 10+ uses the built-in heavy click). */
    const val FALLBACK_LONG_PRESS_MS = 30L
    const val FALLBACK_LONG_PRESS_AMPLITUDE = 180
}
