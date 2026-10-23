package com.example.halauncher.model

import android.content.Intent

/**
 * A screen of the system Settings app, such as Wi-Fi or Display, opened by its standard intent
 * action. Only screens this device was found to open are indexed (see SettingsProvider).
 *
 * @property key stable name of the screen in the catalog ("wifi"), independent of language.
 * @property action the intent action that opens it.
 * @property packageName the system app found to handle [action], so another app that claims the
 * same action can't take it over.
 * @property detail the line shown under the name ("Settings").
 */
data class SettingEntry(
    val key: String,
    override val name: String,
    val action: String,
    val packageName: String,
    override val detail: String?,
    override val keywords: String? = null,
) : SearchEntry {
    override val id: String get() = idFor(key)
    override val category: SearchCategory get() = SearchCategory.SETTING

    override val link: Intent get() = Intent(action).setPackage(packageName)

    companion object {
        /** The [id] of the settings screen [key]. */
        fun idFor(key: String): String = "setting:$key"
    }
}
