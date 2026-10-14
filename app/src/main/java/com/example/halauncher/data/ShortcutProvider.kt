package com.example.halauncher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import com.example.halauncher.model.AppInfo
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry
import com.example.halauncher.model.ShortcutEntry

/**
 * App shortcuts: the actions apps offer on a long press of their icon (manifest and dynamic
 * shortcuts), plus the conversations they've cached for notifications on Android 11+. Needs no
 * permission, but only the default home app may read them, so this returns nothing on any other.
 *
 * @param findApp the installed app with this package from the latest app load; a shortcut whose
 * app isn't there (e.g. it has no launcher entry) is left out.
 */
class ShortcutProvider(
    private val context: Context,
    private val findApp: (packageName: String) -> AppInfo?,
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
        return query(ShortcutQuery().setQueryFlags(queryFlags()))
            .asSequence()
            .filter { it.isEnabled && it.`package` != context.packageName }
            .mapNotNull(::toEntry)
            // A shortcut can be both dynamic and cached, for instance.
            .distinctBy { it.id }
            .toList()
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun toEntry(info: ShortcutInfo): ShortcutEntry? {
        val app = findApp(info.`package`) ?: return null
        // The long label where there is one: the short one is written to sit under an icon, so it
        // can be too vague to search for on its own ("Start" for "Start stopwatch").
        val label = listOf(info.longLabel, info.shortLabel)
            .firstNotNullOfOrNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
            ?: return null
        return ShortcutEntry(
            packageName = info.`package`,
            shortcutId = info.id,
            name = label,
            appName = app.name,
            link = app.link,
            iconVersion = info.lastChangedTimestamp,
        )
    }

    /** What a query for [entry]'s shortcut returns: its current info, or null if it's gone. */
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun find(entry: ShortcutEntry): ShortcutInfo? {
        val query = ShortcutQuery()
            .setPackage(entry.packageName)
            .setShortcutIds(listOf(entry.shortcutId))
            .setQueryFlags(queryFlags())
        return query(query).firstOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun query(query: ShortcutQuery): List<ShortcutInfo> = try {
        launcherApps?.getShortcuts(query, Process.myUserHandle()).orEmpty()
    } catch (e: SecurityException) {
        // No longer the default home app.
        emptyList()
    } catch (e: IllegalStateException) {
        // The user's storage is still locked.
        emptyList()
    }

    private companion object {
        @RequiresApi(Build.VERSION_CODES.N_MR1)
        fun queryFlags(): Int {
            val flags = ShortcutQuery.FLAG_MATCH_MANIFEST or ShortcutQuery.FLAG_MATCH_DYNAMIC
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) flags or ShortcutQuery.FLAG_MATCH_CACHED else flags
        }
    }
}
