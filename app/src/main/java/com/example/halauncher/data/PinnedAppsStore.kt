package com.example.halauncher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.halauncher.model.AppInfo
import com.example.halauncher.model.SearchEntry
import com.example.halauncher.model.ShortcutEntry

/**
 * What's pinned to the home-screen sphere, as keys in pin order (see [keyOf]): an app's key
 * (its package name, or "work:" and it for a work app), or an app shortcut's id (added through "Add to home screen", see PinShortcutActivity).
 * Kept in the launcher's preferences, so it survives restarts. A pinned app that's since been
 * uninstalled stays in the list (it's hidden, not forgotten) and returns if it's reinstalled.
 */
class PinnedAppsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<String> =
        prefs.getString(KEY_PACKAGES, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun save(keys: List<String>) {
        prefs.edit { putString(KEY_PACKAGES, keys.joinToString(SEPARATOR)) }
    }

    /**
     * Calls [onChange] whenever the pins are saved, from anywhere in the launcher (the pin request
     * activity saves them while the home screen isn't showing). Keep the returned listener for as
     * long as it should be called (preferences only hold it weakly), and pass it to [unwatch].
     */
    fun watch(onChange: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == KEY_PACKAGES) onChange() }
            .also(prefs::registerOnSharedPreferenceChangeListener)

    fun unwatch(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        private const val PREFS_NAME = "pinned_apps"
        /** Named for when only apps could be pinned; kept so existing pins carry over. */
        private const val KEY_PACKAGES = "packages"
        /** Neither package names nor shortcut keys can contain a newline. */
        private const val SEPARATOR = "\n"

        /**
         * The key [entry] is pinned under: an app's AppInfo.key, a shortcut's id (which starts
         * "shortcut:", and package names can't contain a colon); null for anything else, which
         * can't be pinned.
         */
        fun keyOf(entry: SearchEntry): String? = when (entry) {
            is AppInfo -> entry.key
            is ShortcutEntry -> entry.id
            else -> null
        }

        /** The AppInfo.key of the app a shortcut [key] belongs to; null if [key] is an app's. */
        fun appKeyOfShortcutKey(key: String): String? = ShortcutEntry.appKeyOf(key)
    }
}
