package com.example.halauncher.model

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

/**
 * An installed app that has a launcher entry point.
 *
 * @property link the app's launch intent, resolved when the app list is loaded so the UI
 * never has to query PackageManager while composing. It names one activity, which can go stale
 * (see [launchDirectly]).
 */
data class AppInfo(
    override val name: String,
    val packageName: String,
    override val icon: Drawable,
    override val link: Intent,
) : SearchEntry {
    override val id: String get() = "app:$packageName"
    override val category: SearchCategory get() = SearchCategory.APP

    /**
     * Opens the app's launch activity as it is now, not as it was when [link] was made. Apps that
     * change their icon (festive icons, say) do it by switching which of their launcher
     * activity-aliases is enabled, so the activity [link] names may since have been disabled, and
     * starting it would fail. Falls back to [link] only if the app has no launch activity now.
     */
    override fun launchDirectly(context: Context): Boolean = launchCurrent(context, packageName)

    companion object {
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
    }
}
