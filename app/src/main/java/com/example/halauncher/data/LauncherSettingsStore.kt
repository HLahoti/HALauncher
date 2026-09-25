package com.example.halauncher.data

import android.content.Context
import androidx.core.content.edit
import com.example.halauncher.config.HapticsConfig

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

    private companion object {
        const val PREFS_NAME = "launcher_settings"
        const val KEY_DOUBLE_TAP_LOCK = "double_tap_to_lock"
        const val KEY_REDUCE_INDEX_SIZE = "reduce_index_size"
        const val KEY_HAPTIC_INTENSITY = "haptic_intensity"
    }
}
