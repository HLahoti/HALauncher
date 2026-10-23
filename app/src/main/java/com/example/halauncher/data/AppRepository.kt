package com.example.halauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.WorkerThread
import com.example.halauncher.model.AppInfo
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry
import com.example.halauncher.util.IntentFactory
import java.text.Collator

class AppRepository(private val context: Context) : SearchEntryProvider {

    override val category = SearchCategory.APP

    /** Small cached icons and labels, so a load doesn't open every app's resources again. */
    private val iconCache = AppIconCache(context)

    /** Apps from the latest [getInstalledApps], by package; lets the disk index rebuild app entries. */
    @Volatile
    private var appsByPackage: Map<String, AppInfo> = emptyMap()

    /** The app from the latest load, or null if it wasn't installed then. */
    fun cachedApp(packageName: String): AppInfo? = appsByPackage[packageName]

    /**
     * The app whose shortcuts these are: [cachedApp], or the launcher itself, which leaves itself
     * out of the app list but offers shortcuts of its own (its settings).
     */
    fun shortcutApp(packageName: String): AppInfo? =
        if (packageName == context.packageName) self else cachedApp(packageName)

    private val self: AppInfo by lazy {
        val pm = context.packageManager
        AppInfo(
            name = context.applicationInfo.loadLabel(pm).toString(),
            packageName = context.packageName,
            icon = context.applicationInfo.loadIcon(pm),
            link = IntentFactory.launchAppIntent(context, context.packageName),
        )
    }

    /**
     * Makes the next [getInstalledApps] render these apps' icons and labels again instead of using
     * the cache (an app reported a change, which may be a new icon without a new APK).
     */
    @WorkerThread
    fun invalidate(packageNames: Collection<String>) = packageNames.forEach(iconCache::invalidate)

    /** Every app with a launcher activity, sorted by label. Loads icons, so call off the main thread. */
    @WorkerThread
    fun getInstalledApps(): List<AppInfo> {
        val started = SystemClock.elapsedRealtime()
        val pm = context.packageManager
        val collator = Collator.getInstance()
        return queryLauncherActivities(pm)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            // An app with several launcher activities is launched by package, so list it once.
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                AppInfo(
                    name = iconCache.label(info, pm),
                    packageName = info.activityInfo.packageName,
                    icon = iconCache.icon(info, pm),
                    link = IntentFactory.launchAppIntent(context, info.activityInfo.packageName),
                )
            }
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
            .toList()
            .also { apps ->
                appsByPackage = apps.associateBy { it.packageName }
                iconCache.retainOnly(appsByPackage.keys)
            }
            .also { apps -> Log.d("Perf", "Loaded ${apps.size} apps in ${SystemClock.elapsedRealtime() - started} ms") }
    }

    @WorkerThread
    override fun loadEntries(): List<SearchEntry> = getInstalledApps()

    private fun queryLauncherActivities(pm: PackageManager): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
    }
}
