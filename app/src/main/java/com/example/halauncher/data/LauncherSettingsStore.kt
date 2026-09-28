package com.example.halauncher.data

import android.content.Context
import androidx.core.content.edit
import com.example.halauncher.config.HapticsConfig
import com.example.halauncher.model.SearchCategory

/**
 * The launcher's preferences, set from the long-press settings panel and kept in shared
 * preferences so they survive restarts. Reads are cheap enough to do on demand (e.g. the file
 * indexer checks [reduceIndexSize] each time it queries, and [Haptics] reads [hapticIntensity] on
 * each tick), so this exposes plain properties rather than flows; the ViewModel mirrors them into
 * UI state.
 *
 * The two toggles default off: they're opt-in features. [hapticIntensity] defaults to the
 * launcher's standard tick strength ([HapticsConfig.DEFAULT_TICK_INTENSITY]).
 */
class LauncherSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Whether the first-run setup (default home + the permissions search uses) has been shown once
     * already. It comes up by itself the first time the launcher opens, ahead of the how-to card;
     * after that it's only reachable from settings ("Set-up & permissions").
     */
    var setupSeen: Boolean
        get() = prefs.getBoolean(KEY_SETUP_SEEN, false)
        set(value) = prefs.edit { putBoolean(KEY_SETUP_SEEN, value) }

    /**
     * Whether the "How to use this app" card has been shown once already. It comes up by itself the
     * first time the launcher opens (just after setup); after that it's only reachable from settings.
     */
    var onboardingSeen: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_SEEN, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING_SEEN, value) }

    /**
     * Whether settings are locked: the other settings can't be changed, and the reminders/money
     * buttons and app sphere hide their customisation. Guards against accidental changes; defaults off.
     */
    var settingsLocked: Boolean
        get() = prefs.getBoolean(KEY_SETTINGS_LOCKED, false)
        set(value) = prefs.edit { putBoolean(KEY_SETTINGS_LOCKED, value) }

    /** Whether double-tapping the empty home screen locks the phone (needs the accessibility service). */
    var doubleTapToLock: Boolean
        get() = prefs.getBoolean(KEY_DOUBLE_TAP_LOCK, false)
        set(value) = prefs.edit { putBoolean(KEY_DOUBLE_TAP_LOCK, value) }

    /** Whether the file index leaves out denylisted folders ([FilePathDenylist]) to stay small. */
    var reduceIndexSize: Boolean
        get() = prefs.getBoolean(KEY_REDUCE_INDEX_SIZE, false)
        set(value) = prefs.edit { putBoolean(KEY_REDUCE_INDEX_SIZE, value) }

    /** Strength of the haptic tick, from 0f (off) to 1f (strongest). Stored clamped to that range. */
    var hapticIntensity: Float
        get() = prefs.getFloat(KEY_HAPTIC_INTENSITY, HapticsConfig.DEFAULT_TICK_INTENSITY)
        set(value) = prefs.edit { putFloat(KEY_HAPTIC_INTENSITY, value.coerceIn(0f, 1f)) }

    /**
     * The category the search view starts filtered to each time it opens, or null for "All".
     * Stored by enum name, with [ALL_VALUE] standing in for null; defaults to [SearchCategory.APP]
     * (the launcher's original fixed default) so existing behaviour is unchanged.
     */
    var defaultSearchCategory: SearchCategory?
        get() = when (val stored = prefs.getString(KEY_DEFAULT_SEARCH_CATEGORY, null)) {
            null -> SearchCategory.APP
            ALL_VALUE -> null
            else -> SearchCategory.entries.firstOrNull { it.name == stored } ?: SearchCategory.APP
        }
        set(value) = prefs.edit { putString(KEY_DEFAULT_SEARCH_CATEGORY, value?.name ?: ALL_VALUE) }

    private companion object {
        const val PREFS_NAME = "launcher_settings"
        const val KEY_SETUP_SEEN = "setup_seen"
        const val KEY_ONBOARDING_SEEN = "onboarding_seen"
        const val KEY_SETTINGS_LOCKED = "settings_locked"
        const val KEY_DOUBLE_TAP_LOCK = "double_tap_to_lock"
        const val KEY_REDUCE_INDEX_SIZE = "reduce_index_size"
        const val KEY_HAPTIC_INTENSITY = "haptic_intensity"
        const val KEY_DEFAULT_SEARCH_CATEGORY = "default_search_category"

        /** Sentinel stored for the "All" default, since null can't be a preference value. */
        const val ALL_VALUE = "ALL"
    }
}
