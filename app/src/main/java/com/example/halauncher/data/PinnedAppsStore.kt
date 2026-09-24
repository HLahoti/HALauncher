package com.example.halauncher.data

import android.content.Context
import androidx.core.content.edit

/**
 * The apps pinned to the home-screen sphere, as package names in the order they were pinned.
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

    fun save(packages: List<String>) {
        prefs.edit { putString(KEY_PACKAGES, packages.joinToString(SEPARATOR)) }
    }

    private companion object {
        const val PREFS_NAME = "pinned_apps"
        const val KEY_PACKAGES = "packages"
        /** Package names can't contain a newline. */
        const val SEPARATOR = "\n"
    }
}
