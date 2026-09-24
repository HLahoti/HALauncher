package com.example.halauncher.data

import android.content.Context
import androidx.core.content.edit

/**
 * Which app the user assigned to each assignable home-screen button (by package name), kept in
 * the launcher's preferences so it survives restarts. An assigned app that's since been
 * uninstalled is simply not found, and the button asks for a new one.
 */
class QuickActionAppStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(slot: String): String? = prefs.getString(slot, null)

    fun save(slot: String, packageName: String) = prefs.edit { putString(slot, packageName) }

    fun clear(slot: String) = prefs.edit { remove(slot) }

    companion object {
        private const val PREFS_NAME = "quick_actions"

        /** The money / payments button. */
        const val MONEY_SLOT = "money"
    }
}
