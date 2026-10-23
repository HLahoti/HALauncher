package com.example.halauncher.model

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process

/**
 * An app shortcut: one of the actions an app offers on a long press of its icon, such as "New
 * incognito tab" or a recent chat. Only the default launcher may read and start them.
 *
 * Opens through LauncherApps ([launchDirectly]); if the app has since removed or disabled the
 * shortcut, opens the app itself instead, through its current launch activity, else [link].
 *
 * @property appName the app it belongs to, shown under the shortcut's name.
 * @property link the app's launch intent when the shortcut was loaded.
 * @property iconVersion when the app last changed the shortcut, so a replaced icon isn't mistaken
 * for the cached one.
 * @property keywords the Settings screen's keywords, when the shortcut stands in for one (see
 * SettingsDuplicates).
 */
data class ShortcutEntry(
    val packageName: String,
    val shortcutId: String,
    override val name: String,
    val appName: String,
    override val link: Intent,
    val iconVersion: Long = 0L,
    override val keywords: String? = null,
) : SearchEntry {
    override val id: String get() = idFor(packageName, shortcutId)
    override val category: SearchCategory get() = SearchCategory.SHORTCUT

    override val detail: String? get() = appName

    /** Starts the shortcut, or failing that the app as it is now (see [AppInfo.launchDirectly]). */
    override fun launchDirectly(context: Context): Boolean =
        startShortcut(context) || AppInfo.launchCurrent(context, packageName)

    private fun startShortcut(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return false
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return false
        return try {
            launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
            true
        } catch (e: ActivityNotFoundException) {
            // The shortcut was removed or disabled.
            false
        } catch (e: IllegalStateException) {
            // The user's storage is still locked.
            false
        } catch (e: SecurityException) {
            // No longer the default launcher.
            false
        }
    }

    companion object {
        /** The [id] of shortcut [shortcutId] of app [packageName]. */
        fun idFor(packageName: String, shortcutId: String): String = "shortcut:$packageName/$shortcutId"
    }
}
