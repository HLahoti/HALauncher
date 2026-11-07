package com.example.halauncher.model

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserHandle

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
 * @property icon the shortcut's icon, already loaded: set only for shortcuts pinned to the sphere,
 * which draws icons straight away. Search loads the rest as it shows them (ShortcutIconCache).
 * @property workProfile the work profile its app is in, for a work app's shortcut (named "… (work)",
 * badged, and opened in the work profile); null for a personal app's.
 */
data class ShortcutEntry(
    val packageName: String,
    val shortcutId: String,
    override val name: String,
    val appName: String,
    override val link: Intent,
    val iconVersion: Long = 0L,
    override val keywords: String? = null,
    override val icon: Drawable? = null,
    val workProfile: UserHandle? = null,
) : SearchEntry {
    val isWork: Boolean get() = workProfile != null

    /** The AppInfo.key of the app it belongs to: the work copy's for a work shortcut. */
    val appKey: String get() = AppInfo.keyFor(packageName, isWork)

    override val id: String get() = idFor(packageName, shortcutId, isWork)
    override val category: SearchCategory get() = SearchCategory.SHORTCUT

    override val detail: String? get() = appName

    /**
     * Starts the shortcut, or failing that the app as it is now (see [AppInfo.launchDirectly]): in
     * the work profile for a work shortcut, never the personal copy of the app.
     */
    override fun launchDirectly(context: Context): Boolean =
        startShortcut(context) ||
            (workProfile?.let { AppInfo.launchInProfile(context, packageName, it) } ?: AppInfo.launchCurrent(context, packageName))

    private fun startShortcut(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return false
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return false
        return try {
            launcherApps.startShortcut(packageName, shortcutId, null, null, workProfile ?: Process.myUserHandle())
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
        /**
         * The [id] of shortcut [shortcutId] of app [packageName] (its work copy if [isWork]):
         * [ID_PREFIX], the app's AppInfo.key, "/" and the shortcut's id.
         */
        fun idFor(packageName: String, shortcutId: String, isWork: Boolean = false): String =
            "$ID_PREFIX${AppInfo.keyFor(packageName, isWork)}/$shortcutId"

        /** The AppInfo.key of the app a shortcut [id] belongs to; null if it isn't a shortcut's id. */
        fun appKeyOf(id: String): String? =
            if (id.startsWith(ID_PREFIX)) id.removePrefix(ID_PREFIX).substringBefore('/') else null

        /** What every shortcut's [id] starts with. */
        const val ID_PREFIX = "shortcut:"
    }
}
