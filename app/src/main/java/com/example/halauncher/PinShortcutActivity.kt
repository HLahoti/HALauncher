package com.example.halauncher

import android.app.Activity
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.example.halauncher.config.PinPickerConfig
import com.example.halauncher.data.PinnedAppsStore
import com.example.halauncher.model.ShortcutEntry

/**
 * Where "Add to home screen" lands (a website from Chrome, a contact, a playlist...): Android
 * asks the default home app to confirm an app's request to pin a shortcut. HALauncher puts it on
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
        val key = ShortcutEntry.idFor(info.`package`, info.id)
        val label = labelOf(info)
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
     * still installed with a launcher entry, and shortcuts still pinned. Pins of uninstalled apps
     * are kept but don't take up room.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun shownPinCount(launcherApps: LauncherApps, keys: List<String>): Int {
        val user = Process.myUserHandle()
        val shortcutPackages = keys.mapNotNull(PinnedAppsStore::packageOfShortcutKey).distinct()
        val pinnedShortcuts = shortcutPackages.flatMapTo(HashSet()) { pkg ->
            try {
                launcherApps.getShortcuts(ShortcutQuery().setPackage(pkg).setQueryFlags(ShortcutQuery.FLAG_MATCH_PINNED), user)
                    .orEmpty()
                    .filter { it.isEnabled }
                    .map { ShortcutEntry.idFor(it.`package`, it.id) }
            } catch (e: SecurityException) {
                emptyList()
            } catch (e: IllegalStateException) {
                emptyList()
            }
        }
        return keys.count { key ->
            if (PinnedAppsStore.packageOfShortcutKey(key) != null) {
                key in pinnedShortcuts
            } else {
                launcherApps.getActivityList(key, user).isNotEmpty()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun labelOf(info: ShortcutInfo): String =
        listOf(info.shortLabel, info.longLabel).firstNotNullOfOrNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
            ?: getString(R.string.pin_shortcut_unnamed)
}
