package com.example.halauncher.model

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.UserHandle

/**
 * An installed app that has a launcher entry point: in the personal profile, or in the work
 * profile ([workProfile] set), where it's a separate entry from any personal copy of the same app,
 * named "… (work)" with the work badge on its icon.
 *
 * @property link the app's launch intent, resolved when the app list is loaded so the UI
 * never has to query PackageManager while composing. It names one activity, which can go stale
 * (see [launchDirectly]). A work app's can't start it in the work profile, so it's one that
 * nothing handles: it only opens through [launchDirectly], never as the personal copy.
 * @property workProfile the work profile it's installed in; null for a personal app.
 */
data class AppInfo(
    override val name: String,
    val packageName: String,
    override val icon: Drawable,
    override val link: Intent,
    val workProfile: UserHandle? = null,
) : SearchEntry {
    val isWork: Boolean get() = workProfile != null

    /**
     * Identifies the app wherever it's remembered (pins, quick actions, the index, the icon
     * cache): its package name, or for a work app [WORK_KEY_PREFIX] + package name.
     */
    val key: String get() = keyFor(packageName, isWork)

    override val id: String get() = "app:$key"
    override val category: SearchCategory get() = SearchCategory.APP

    /**
     * Opens the app's launch activity as it is now, not as it was when [link] was made. Apps that
     * change their icon (festive icons, say) do it by switching which of their launcher
     * activity-aliases is enabled, so the activity [link] names may since have been disabled, and
     * starting it would fail. Falls back to [link] only if the app has no launch activity now.
     *
     * A work app starts in the work profile; if work apps are paused, the system asks whether to
     * turn them on.
     */
    override fun launchDirectly(context: Context): Boolean =
        workProfile?.let { launchInProfile(context, packageName, it) } ?: launchCurrent(context, packageName)

    companion object {
        /** What a work app's [key] starts with. Package names can't contain a colon. */
        const val WORK_KEY_PREFIX = "work:"

        fun keyFor(packageName: String, isWork: Boolean): String =
            if (isWork) "$WORK_KEY_PREFIX$packageName" else packageName

        /**
         * Starts [packageName]'s launch activity as it is now (see [launchDirectly]).
         *
         * @return false if the app has no launch activity or it wouldn't start.
         */
        fun launchCurrent(context: Context, packageName: String): Boolean {
            val current = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            return try {
                context.startActivity(current.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (e: ActivityNotFoundException) {
                false
            } catch (e: SecurityException) {
                false
            }
        }

        /** Starts [packageName]'s launch activity in [profile]; false if it has none or wouldn't start. */
        fun launchInProfile(context: Context, packageName: String, profile: UserHandle): Boolean {
            val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return false
            return try {
                val activity = launcherApps.getActivityList(packageName, profile).firstOrNull() ?: return false
                launcherApps.startMainActivity(activity.componentName, profile, null, null)
                true
            } catch (e: ActivityNotFoundException) {
                false
            } catch (e: SecurityException) {
                // The profile was removed, or is locked.
                false
            } catch (e: IllegalStateException) {
                false
            }
        }

        /** Opens [packageName]'s App info page in [profile]; false if it couldn't. */
        fun openDetailsInProfile(context: Context, packageName: String, profile: UserHandle): Boolean {
            val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return false
            return try {
                val activity = launcherApps.getActivityList(packageName, profile).firstOrNull() ?: return false
                launcherApps.startAppDetailsActivity(activity.componentName, profile, null, null)
                true
            } catch (e: ActivityNotFoundException) {
                false
            } catch (e: SecurityException) {
                false
            } catch (e: IllegalStateException) {
                false
            }
        }
    }
}
