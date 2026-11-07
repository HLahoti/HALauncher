package com.example.halauncher.search.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Process
import android.os.UserHandle
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.halauncher.config.IndexUpdaterConfig
import com.example.halauncher.model.AppInfo
import com.example.halauncher.model.SearchCategory

/**
 * Turns system change notifications into [PendingChanges] entries, then calls [onChange] so the
 * updater wakes up. Nothing is read or written here; that waits for the debounced flush.
 *
 * - Apps: the launcher callback for installs, updates, removals and apps becoming (un)available.
 * - Contacts: an observer on the contacts provider (it doesn't say what changed, so it's a full
 *   re-sync, which only writes differences).
 * - Files: an observer on MediaStore, which reports each changed file's id.
 * - App shortcuts: the same launcher callback, when an app changes its shortcuts (a full re-sync,
 *   since it reports the app's whole list). A change to an app re-syncs them too.
 *
 * Contacts and files are only observed while the launcher can read them; call [refresh] after a
 * permission changes. Callbacks arrive on [handler]'s thread.
 */
class IndexChangeWatcher(
    context: Context,
    private val handler: Handler,
    private val pending: PendingChanges,
    private val canReadContacts: () -> Boolean,
    private val canReadFiles: () -> Boolean,
    private val canReadShortcuts: () -> Boolean,
    private val onChange: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)

    private var watchingApps = false
    private var contactsObserver: ContentObserver? = null
    private var filesObserver: ContentObserver? = null

    private val appsCallback = object : LauncherApps.Callback() {
        // Reported for the work profile's apps too, which are keyed apart from personal copies.
        override fun onPackageAdded(packageName: String, user: UserHandle) = appsChanged(listOf(packageName), user)
        override fun onPackageRemoved(packageName: String, user: UserHandle) = appsChanged(listOf(packageName), user)
        override fun onPackageChanged(packageName: String, user: UserHandle) = appsChanged(listOf(packageName), user)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            appsChanged(packageNames.asList(), user)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            appsChanged(packageNames.asList(), user)
        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) {
            if (!IndexUpdaterConfig.WATCH_SHORTCUTS || !canReadShortcuts()) return
            pending.requestFullSync(SearchCategory.SHORTCUT)
            onChange()
        }
    }

    fun start() {
        val wantsCallback = IndexUpdaterConfig.WATCH_APPS || IndexUpdaterConfig.WATCH_SHORTCUTS
        if (wantsCallback && launcherApps != null && !watchingApps) {
            launcherApps.registerCallback(appsCallback, handler)
            watchingApps = true
        }
        if (IndexUpdaterConfig.WATCH_APPS && !watchingProfiles) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
                addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            }
            // Sent by the system only, to registered receivers.
            ContextCompat.registerReceiver(appContext, profilesReceiver, filter, null, handler, ContextCompat.RECEIVER_NOT_EXPORTED)
            watchingProfiles = true
        }
        refresh()
    }

    /** Starts or stops the contacts and files observers to match current permissions. */
    fun refresh() {
        contactsObserver = updateObserver(
            current = contactsObserver,
            wanted = IndexUpdaterConfig.WATCH_CONTACTS && canReadContacts(),
            uri = ContactsContract.Contacts.CONTENT_URI,
        ) { _ -> pending.requestFullSync(SearchCategory.CONTACT) }

        filesObserver = updateObserver(
            current = filesObserver,
            wanted = IndexUpdaterConfig.WATCH_FILES && canReadFiles(),
            uri = MediaStore.Files.getContentUri(EXTERNAL_VOLUME),
        ) { uri ->
            // Per-file notifications end in the MediaStore id; anything else (a whole volume or
            // collection changed) needs a full file re-sync.
            val id = uri?.lastPathSegment?.toLongOrNull()
            if (id != null) pending.filesChanged(listOf(id)) else pending.requestFullSync(SearchCategory.FILE)
        }
    }

    fun stop() {
        if (watchingApps) {
            launcherApps?.unregisterCallback(appsCallback)
            watchingApps = false
        }
        if (watchingProfiles) {
            appContext.unregisterReceiver(profilesReceiver)
            watchingProfiles = false
        }
        contactsObserver?.let(appContext.contentResolver::unregisterContentObserver)
        filesObserver?.let(appContext.contentResolver::unregisterContentObserver)
        contactsObserver = null
        filesObserver = null
    }

    /**
     * A work profile was added or removed, taking its apps with it. (Pausing and resuming work apps
     * doesn't change the list: paused, they stay listed and ask to be turned back on when opened.)
     */
    private val profilesReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = appsChanged(emptyList(), Process.myUserHandle())
    }
    private var watchingProfiles = false

    private fun appsChanged(packageNames: Collection<String>, user: UserHandle) {
        val isWork = user != Process.myUserHandle()
        val keys = packageNames.map { AppInfo.keyFor(it, isWork) }
        if (IndexUpdaterConfig.WATCH_APPS) pending.appsChanged(keys)
        // An app installed, updated or removed brings, changes or takes its shortcuts with it.
        if (IndexUpdaterConfig.WATCH_SHORTCUTS && canReadShortcuts()) pending.requestFullSync(SearchCategory.SHORTCUT)
        if (pending.isEmpty) return
        onChange()
    }

    private fun updateObserver(
        current: ContentObserver?,
        wanted: Boolean,
        uri: Uri,
        record: (Uri?) -> Unit,
    ): ContentObserver? {
        if (wanted == (current != null)) return current
        if (!wanted) {
            appContext.contentResolver.unregisterContentObserver(current!!)
            return null
        }
        val observer = object : ContentObserver(handler) {
            // Android 11+ delivers each changed URI through this overload too, one call per URI.
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                record(uri)
                onChange()
            }
        }
        return try {
            appContext.contentResolver.registerContentObserver(uri, true, observer)
            observer
        } catch (e: SecurityException) {
            // Access was revoked between the check and registering.
            null
        }
    }

    private companion object {
        /** MediaStore.VOLUME_EXTERNAL, spelled out because the constant is Android 10+. */
        const val EXTERNAL_VOLUME = "external"
    }
}
