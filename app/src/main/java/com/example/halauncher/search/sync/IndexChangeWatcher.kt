package com.example.halauncher.search.sync

import android.content.Context
import android.content.pm.LauncherApps
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.UserHandle
import android.provider.ContactsContract
import android.provider.MediaStore
import com.example.halauncher.config.IndexUpdaterConfig
import com.example.halauncher.model.SearchCategory

/**
 * Turns system change notifications into [PendingChanges] entries, then calls [onChange] so the
 * updater wakes up. Nothing is read or written here; that waits for the debounced flush.
 *
 * - Apps: the launcher callback for installs, updates, removals and apps becoming (un)available.
 * - Contacts: an observer on the contacts provider (it doesn't say what changed, so it's a full
 *   re-sync, which only writes differences).
 * - Files: an observer on MediaStore, which reports each changed file's id.
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
    private val onChange: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)

    private var watchingApps = false
    private var contactsObserver: ContentObserver? = null
    private var filesObserver: ContentObserver? = null

    private val appsCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = appsChanged(listOf(packageName))
        override fun onPackageRemoved(packageName: String, user: UserHandle) = appsChanged(listOf(packageName))
        override fun onPackageChanged(packageName: String, user: UserHandle) = appsChanged(listOf(packageName))
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            appsChanged(packageNames.asList())
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            appsChanged(packageNames.asList())
    }

    fun start() {
        if (IndexUpdaterConfig.WATCH_APPS && launcherApps != null && !watchingApps) {
            launcherApps.registerCallback(appsCallback, handler)
            watchingApps = true
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
        contactsObserver?.let(appContext.contentResolver::unregisterContentObserver)
        filesObserver?.let(appContext.contentResolver::unregisterContentObserver)
        contactsObserver = null
        filesObserver = null
    }

    private fun appsChanged(packageNames: Collection<String>) {
        pending.appsChanged(packageNames)
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
