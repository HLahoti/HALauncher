package com.example.halauncher.data

import android.content.Context
import androidx.core.content.edit

/**
 * The launcher's on/off preferences, set from the long-press settings panel and kept in shared
 * preferences so they survive restarts. Reads are cheap enough to do on demand (e.g. the file
 * indexer checks [reduceIndexSize] each time it queries), so this exposes plain properties rather
 * than flows; the ViewModel mirrors them into UI state.
 *
 * Both default off: they're opt-in features, and neither changes behaviour until switched on.
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

    private companion object {
        const val PREFS_NAME = "launcher_settings"
        const val KEY_DOUBLE_TAP_LOCK = "double_tap_to_lock"
        const val KEY_REDUCE_INDEX_SIZE = "reduce_index_size"
    }
}
