package com.example.halauncher

import android.app.Activity
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.example.halauncher.config.PinPickerConfig
import com.example.halauncher.data.PinnedAppsStore
import com.example.halauncher.data.WorkProfiles
import com.example.halauncher.model.AppInfo
import com.example.halauncher.model.ShortcutEntry

/**
 * Where "Add to home screen" lands (a website from Chrome, a contact, a playlist...): Android
 * asks the default home app to confirm an app's request to pin a shortcut. A work app's request
 * pins its work shortcut, kept apart from the personal copy's. HALauncher puts it on
 * the sphere, at the end, if there's room for it ([PinPickerConfig.MAX_PINNED], as when pinning
 * an app), and says so in a toast; with no room it turns the request down and says why. Requests
 * for widgets, which the launcher doesn't show, are turned down.
 *
 * Shows nothing of its own and finishes at once, so the app that asked stays on screen. Runs in a
 * task of its own (see the manifest), so starting it doesn't bring the home screen forward.
 */
class PinShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) handleRequest()
        finish()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleRequest() {
        val launcherApps = getSystemService(LauncherApps::class.java) ?: return
        val request = try {
            launcherApps.getPinItemRequest(intent)
        } catch (e: IllegalArgumentException) {
            null
        } ?: return
        // Not accepting a request turns it down: it's dropped when this activity finishes.
        if (request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT || !request.isValid) return
        val info = request.shortcutInfo ?: return
        // From a work app, it's the work copy's shortcut. Other profiles (Private Space, clones)
        // aren't shown by the launcher, so their requests are turned down.
        val isWork = info.userHandle != Process.myUserHandle()
        if (isWork && info.userHandle !in WorkProfiles.all(this)) {
            Toast.makeText(this, getString(R.string.pin_shortcut_unsupported_profile, labelOf(info)), Toast.LENGTH_SHORT).show()
            return
        }
        val key = ShortcutEntry.idFor(info.`package`, info.id, isWork)
        val label = if (isWork) getString(R.string.work_app_name, labelOf(info)) else labelOf(info)
        val store = PinnedAppsStore(this)
        val keys = store.load()

        val message = when {
            // Pinned again (e.g. after the app was reinstalled): it keeps its place.
            key in keys -> if (accept(request)) getString(R.string.pin_shortcut_already, label) else null
            shownPinCount(launcherApps, keys) >= PinPickerConfig.MAX_PINNED -> getString(R.string.pin_shortcut_full, label)
            accept(request) -> {
                store.save(keys + key)
                getString(R.string.pin_shortcut_added, label)
            }
            else -> null
        }
        message?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun accept(request: LauncherApps.PinItemRequest): Boolean = try {
        request.accept()
    } catch (e: IllegalStateException) {
        // Already accepted, or no longer valid.
        false
    } catch (e: SecurityException) {
        // No longer the default home app.
        false
    }

    /**
     * How many of [keys] the sphere would show, as HomeViewModel.pinnedItems counts them: apps
     * (personal or work) still installed with a launcher entry, and shortcuts (personal or work)
     * still pinned. Pins of uninstalled apps, or of a removed work profile, are kept but don't take
     * up room.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun shownPinCount(launcherApps: LauncherApps, keys: List<String>): Int {
        val workProfile by lazy { WorkProfiles.first(this) }
        /** The package and profile of the app keyed [appKey] (AppInfo.key); null if its profile is gone. */
        fun locate(appKey: String): Pair<String, UserHandle>? =
            if (appKey.startsWith(AppInfo.WORK_KEY_PREFIX)) {
                workProfile?.let { appKey.removePrefix(AppInfo.WORK_KEY_PREFIX) to it }
            } else {
                appKey to Process.myUserHandle()
            }

        val pinnedShortcuts = keys.mapNotNull(PinnedAppsStore::appKeyOfShortcutKey).distinct().flatMapTo(HashSet()) { appKey ->
            val (pkg, profile) = locate(appKey) ?: return@flatMapTo emptyList()
            try {
                launcherApps.getShortcuts(ShortcutQuery().setPackage(pkg).setQueryFlags(ShortcutQuery.FLAG_MATCH_PINNED), profile)
                    .orEmpty()
                    .filter { it.isEnabled }
                    .map { ShortcutEntry.idFor(it.`package`, it.id, isWork = profile != Process.myUserHandle()) }
            } catch (e: SecurityException) {
                emptyList()
            } catch (e: IllegalStateException) {
                emptyList()
            }
        }
        return keys.count { key ->
            if (PinnedAppsStore.appKeyOfShortcutKey(key) != null) {
                key in pinnedShortcuts
            } else {
                locate(key)?.let { (pkg, profile) -> hasLauncherEntry(launcherApps, pkg, profile) } ?: false
            }
        }
    }

    private fun hasLauncherEntry(launcherApps: LauncherApps, packageName: String, profile: UserHandle): Boolean = try {
        launcherApps.getActivityList(packageName, profile).isNotEmpty()
    } catch (e: SecurityException) {
        false
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun labelOf(info: ShortcutInfo): String =
        listOf(info.shortLabel, info.longLabel).firstNotNullOfOrNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
            ?: getString(R.string.pin_shortcut_unnamed)
}
