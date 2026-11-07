package com.example.halauncher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Process
import android.os.UserHandle
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import com.example.halauncher.R
import com.example.halauncher.model.AppInfo
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry
import com.example.halauncher.model.ShortcutEntry

/**
 * App shortcuts: the actions apps offer on a long press of their icon (manifest and dynamic
 * shortcuts), plus the conversations they've cached for notifications on Android 11+. Needs no
 * permission, but only the default home app may read them, so this returns nothing on any other.
 *
 * The launcher's own shortcuts (res/xml/shortcuts.xml, such as its settings) are included too, and
 * so are the shortcuts pinned to the sphere through "Add to home screen" (see PinShortcutActivity).
 *
 * Work apps' shortcuts are read from the work profile (see [WorkProfiles]) and kept apart from the
 * personal copy's: named "… (work)", badged, and opened in the work profile.
 *
 * @param findApp the installed app with this AppInfo.key from the latest app load; a shortcut whose
 * app isn't there (e.g. it has no launcher entry) is left out.
 */
class ShortcutProvider(
    private val context: Context,
    private val findApp: (appKey: String) -> AppInfo?,
) : SearchEntryProvider {

    override val category = SearchCategory.SHORTCUT

    private val launcherApps: LauncherApps? = context.getSystemService(LauncherApps::class.java)

    /** The shortcuts' icons, loaded as search shows them. */
    val icons = ShortcutIconCache(context) { entry ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) find(entry) else null
    }

    /** Whether shortcuts can be read: Android 7.1+ with the launcher set as the default home app. */
    fun hasAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return false
        return try {
            launcherApps?.hasShortcutHostPermission() == true
        } catch (e: IllegalStateException) {
            // The user's storage is still locked.
            false
        }
    }

    @WorkerThread
    override fun loadEntries(): List<SearchEntry> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1 || !hasAccess()) return emptyList()
        return profiles()
            .asSequence()
            .flatMap { profile -> query(ShortcutQuery().setQueryFlags(queryFlags()), profile).asSequence() }
            .filter { it.isEnabled }
            .mapNotNull(::toEntry)
            // A shortcut can be both dynamic and cached, for instance.
            .distinctBy { it.id }
            .toList()
    }

    /** The personal profile, then any work profiles. */
    private fun profiles(): List<UserHandle> = listOf(Process.myUserHandle()) + WorkProfiles.all(context)

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun toEntry(info: ShortcutInfo): ShortcutEntry? {
        val profile = info.userHandle.takeIf { it != Process.myUserHandle() }
        val app = findApp(AppInfo.keyFor(info.`package`, isWork = profile != null)) ?: return null
        // The long label where there is one: the short one is written to sit under an icon, so it
        // can be too vague to search for on its own ("Start" for "Start stopwatch").
        val label = listOf(info.longLabel, info.shortLabel)
            .firstNotNullOfOrNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
            ?: return null
        return ShortcutEntry(
            packageName = info.`package`,
            shortcutId = info.id,
            name = if (profile != null) context.getString(R.string.work_app_name, label) else label,
            appName = app.name,
            link = app.link,
            iconVersion = info.lastChangedTimestamp,
            workProfile = profile,
        )
    }

    /**
     * The shortcuts this launcher has pinned, out of [ids] (ShortcutEntry ids), each with its icon
     * loaded (the app's icon if it has none). Ones that are gone (removed by their app, or their app
     * uninstalled or without a launcher entry, or the work profile removed) are left out.
     */
    @WorkerThread
    suspend fun loadPinned(ids: Collection<String>): List<ShortcutEntry> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1 || ids.isEmpty() || !hasAccess()) return emptyList()
        val wanted = ids.toSet()
        return wanted.mapNotNull(ShortcutEntry::appKeyOf).distinct()
            .flatMap { appKey ->
                val app = findApp(appKey) ?: return@flatMap emptyList()
                query(
                    ShortcutQuery().setPackage(app.packageName).setQueryFlags(ShortcutQuery.FLAG_MATCH_PINNED),
                    app.workProfile ?: Process.myUserHandle(),
                )
            }
            .asSequence()
            .filter { it.isEnabled }
            .mapNotNull(::toEntry)
            .filter { it.id in wanted }
            .distinctBy { it.id }
            .toList()
            .mapNotNull { entry -> (icons.load(entry) ?: findApp(entry.appKey)?.icon)?.let { entry.copy(icon = it) } }
    }

    /**
     * Unpins [entry] from this launcher, so its app knows it's no longer on the home screen (and
     * can stop keeping it). Its other pinned shortcuts stay pinned.
     */
    @WorkerThread
    fun unpin(entry: ShortcutEntry) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1 || !hasAccess()) return
        val profile = entry.workProfile ?: Process.myUserHandle()
        val stillPinned = query(ShortcutQuery().setPackage(entry.packageName).setQueryFlags(ShortcutQuery.FLAG_MATCH_PINNED), profile)
            .map { it.id }
            .filter { it != entry.shortcutId }
        try {
            launcherApps?.pinShortcuts(entry.packageName, stillPinned, profile)
        } catch (e: SecurityException) {
            // No longer the default home app, or the work profile is gone.
        } catch (e: IllegalStateException) {
            // The user's storage is still locked.
        }
    }

    /** What a query for [entry]'s shortcut returns: its current info, or null if it's gone. */
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun find(entry: ShortcutEntry): ShortcutInfo? {
        val query = ShortcutQuery()
            .setPackage(entry.packageName)
            .setShortcutIds(listOf(entry.shortcutId))
            .setQueryFlags(queryFlags())
        return query(query, entry.workProfile ?: Process.myUserHandle()).firstOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun query(query: ShortcutQuery, profile: UserHandle): List<ShortcutInfo> = try {
        launcherApps?.getShortcuts(query, profile).orEmpty()
    } catch (e: SecurityException) {
        // No longer the default home app, or the work profile went away.
        emptyList()
    } catch (e: IllegalStateException) {
        // The user's (or work profile's) storage is still locked.
        emptyList()
    }

    private companion object {
        @RequiresApi(Build.VERSION_CODES.N_MR1)
        fun queryFlags(): Int {
            val flags = ShortcutQuery.FLAG_MATCH_MANIFEST or ShortcutQuery.FLAG_MATCH_DYNAMIC or ShortcutQuery.FLAG_MATCH_PINNED
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) flags or ShortcutQuery.FLAG_MATCH_CACHED else flags
        }
    }
}
