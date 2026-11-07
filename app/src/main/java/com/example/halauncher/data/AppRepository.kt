package com.example.halauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.WorkerThread
import com.example.halauncher.R
import com.example.halauncher.model.AppInfo
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry
import com.example.halauncher.util.IntentFactory
import java.text.Collator

class AppRepository(private val context: Context) : SearchEntryProvider {

    override val category = SearchCategory.APP

    /** Small cached icons and labels, so a load doesn't open every app's resources again. */
    private val iconCache = AppIconCache(context)

    /** Apps from the latest [getInstalledApps], by AppInfo.key; lets the disk index rebuild app entries. */
    @Volatile
    private var appsByKey: Map<String, AppInfo> = emptyMap()

    /**
     * The app keyed [key] (AppInfo.key: a personal app's package name, or a work app's) from the
     * latest load, or null if it wasn't installed then.
     */
    fun cachedApp(key: String): AppInfo? = appsByKey[key]

    /**
     * The app keyed [key] (AppInfo.key) whose shortcuts these are: [cachedApp], or the launcher itself, which leaves itself
     * out of the app list but offers shortcuts of its own (its settings).
     */
    fun shortcutApp(key: String): AppInfo? =
        if (key == context.packageName) self else cachedApp(key)

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
     *
     * @param keys AppInfo keys: package names, and work apps' keys.
     */
    @WorkerThread
    fun invalidate(keys: Collection<String>) = keys.forEach(iconCache::invalidate)

    /**
     * Every app with a launcher activity, sorted by label: the personal ones, then each work app
     * as an entry of its own (see [WorkProfiles]). Loads icons, so call off the main thread.
     */
    @WorkerThread
    fun getInstalledApps(): List<AppInfo> {
        val started = SystemClock.elapsedRealtime()
        val pm = context.packageManager
        val collator = Collator.getInstance()
        return (personalApps(pm) + workApps())
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
            .toList()
            .also { apps ->
                appsByKey = apps.associateBy { it.key }
                iconCache.retainOnly(appsByKey.keys)
            }
            .also { apps -> Log.d("Perf", "Loaded ${apps.size} apps in ${SystemClock.elapsedRealtime() - started} ms") }
    }

    private fun personalApps(pm: PackageManager): Sequence<AppInfo> =
        queryLauncherActivities(pm)
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

    /**
     * The apps in the work profile, each named "… (work)" with the work badge on its icon. Listed
     * while work apps are paused too: opening one then asks to turn them back on.
     */
    private fun workApps(): Sequence<AppInfo> {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptySequence()
        return WorkProfiles.all(context).asSequence().flatMap { profile ->
            val activities = try {
                launcherApps.getActivityList(null, profile)
            } catch (e: SecurityException) {
                // The profile went away while loading.
                emptyList()
            }
            activities.asSequence()
                .distinctBy { it.componentName.packageName }
                .map { info ->
                    val packageName = info.componentName.packageName
                    val key = AppInfo.keyFor(packageName, isWork = true)
                    AppInfo(
                        name = context.getString(R.string.work_app_name, iconCache.label(info, key)),
                        packageName = packageName,
                        icon = iconCache.badgedIcon(info, key),
                        link = IntentFactory.workAppPlaceholderIntent(context),
                        workProfile = profile,
                    )
                }
        }
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
