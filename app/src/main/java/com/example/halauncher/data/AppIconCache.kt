package com.example.halauncher.data

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.annotation.WorkerThread
import com.example.halauncher.config.IconCacheConfig
import java.io.File
import java.io.IOException
import kotlin.math.ceil

/**
 * App icons and labels, rendered once and remembered, so loading the app list doesn't have to
 * open every app's resources through PackageManager each time.
 *
 * - **Small.** Each icon is drawn once into a bitmap exactly as big as the launcher ever shows it
 *   ([IconCacheConfig.IconSize]), instead of keeping the app's full-resolution drawable. On
 *   Android 8+ it's kept as a hardware bitmap, so it lives only in graphics memory rather than
 *   also on the heap.
 * - **On disk.** Each icon is also written as a lossless WebP (a few KB) in the cache directory,
 *   and every label in one small index file, so a restart reads files instead of loading icons.
 *   The system may clear the cache directory; anything missing is simply rendered again.
 * - **Fresh.** An entry is keyed by what it was made from: the app's APK path and modification
 *   time (an update installs a new APK), the activity, the icon size, density and night mode,
 *   and for labels the locales. A changed key renders a new entry; [invalidate] forces one (e.g.
 *   the app reported a change without a new APK); apps no longer installed are dropped by [retainOnly].
 *
 * Not thread-safe; use it from one thread (the app list is only loaded on the index updater's).
 */
class AppIconCache(context: Context) {
    private val appContext = context.applicationContext
    private val resources = appContext.resources
    private val dir = File(appContext.cacheDir, DIR_NAME)
    private val labelIndex = File(dir, LABEL_INDEX)
    /** Read each time: the display density can change (Settings > Display size). */
    private val sizePx: Int get() = ceil(IconCacheConfig.IconSize.value * resources.displayMetrics.density).toInt()

    private class Icon(val key: String, val drawable: Drawable)
    private class Label(val key: String, val text: String)

    private val icons = HashMap<String, Icon>()
    private var labels: MutableMap<String, Label>? = null
    private var labelsDirty = false

    /** The app's label, from the cache when still valid. */
    @WorkerThread
    fun label(info: ResolveInfo, pm: PackageManager): String =
        label(info.activityInfo.packageName, labelKey(info.activityInfo)) { info.loadLabel(pm).toString() }

    /** The app's icon at launcher size, from memory or disk when still valid, else rendered now. */
    @WorkerThread
    fun icon(info: ResolveInfo, pm: PackageManager): Drawable =
        icon(info.activityInfo.packageName, iconKey(info.activityInfo)) { info.loadIcon(pm) }

    /** A work app's label (without "(work)"), cached under its [appKey] (AppInfo.key). */
    @WorkerThread
    fun label(info: LauncherActivityInfo, appKey: String): String =
        label(appKey, labelKey(info.applicationInfo, info.name)) { info.label.toString() }

    /**
     * A work app's icon at launcher size with the work badge in its corner, cached under its
     * [appKey] (AppInfo.key) apart from any personal copy of the app.
     */
    @WorkerThread
    fun badgedIcon(info: LauncherActivityInfo, appKey: String): Drawable =
        icon(appKey, iconKey(info.applicationInfo, info.name) + "|badged") {
            info.getBadgedIcon(resources.displayMetrics.densityDpi)
        }

    private fun label(appKey: String, key: String, load: () -> String): String {
        val all = loadedLabels()
        all[appKey]?.takeIf { it.key == key }?.let { return it.text }
        val text = load()
        all[appKey] = Label(key, text)
        labelsDirty = true
        return text
    }

    private fun icon(appKey: String, key: String, load: () -> Drawable): Drawable {
        icons[appKey]?.takeIf { it.key == key }?.let { return it.drawable }
        val file = iconFile(appKey, key)
        val bitmap = readIcon(file) ?: render(load()).also { writeIcon(it, appKey, file) }
        return BitmapDrawable(resources, toDisplayBitmap(bitmap))
            .also { icons[appKey] = Icon(key, it) }
    }

    /** Forgets the icon and label of the app keyed [appKey] (AppInfo.key), so the next load renders them again. */
    fun invalidate(appKey: String) {
        icons.remove(appKey)
        if (loadedLabels().remove(appKey) != null) labelsDirty = true
        dir.listFiles { file -> file.name.startsWith("$appKey$SEPARATOR") }?.forEach(File::delete)
    }

    /**
     * Drops everything for apps not in [appKeys] (AppInfo.key; uninstalled since), and saves the
     * label index if it changed. Call after each full load of the app list.
     */
    @WorkerThread
    fun retainOnly(appKeys: Set<String>) {
        icons.keys.retainAll(appKeys)
        if (loadedLabels().keys.retainAll(appKeys)) labelsDirty = true
        // Keeps only the current file of each app; older versions of its icon go too.
        val wanted = icons.map { (appKey, icon) -> iconFile(appKey, icon.key).name }.toSet()
        dir.listFiles { file -> file.name.endsWith(ICON_SUFFIX) && file.name !in wanted }?.forEach(File::delete)
        if (labelsDirty) saveLabels()
    }

    private fun iconKey(activity: ActivityInfo): String = iconKey(activity.applicationInfo, activity.name)

    private fun iconKey(app: ApplicationInfo, activityName: String): String {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return "${app.sourceDir}|${File(app.sourceDir).lastModified()}|$activityName|" +
            "$sizePx|${resources.displayMetrics.densityDpi}|$night"
    }

    private fun labelKey(activity: ActivityInfo): String = labelKey(activity.applicationInfo, activity.name)

    private fun labelKey(app: ApplicationInfo, activityName: String): String =
        "${app.sourceDir}|${File(app.sourceDir).lastModified()}|$activityName|" +
            resources.configuration.locales.toLanguageTags()

    /** One file per app and key version; the key is hashed to keep the name short. */
    private fun iconFile(appKey: String, key: String) =
        File(dir, "$appKey$SEPARATOR${Integer.toHexString(key.hashCode())}$ICON_SUFFIX")

    private fun render(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun readIcon(file: File): Bitmap? {
        if (!file.isFile) return null
        val options = BitmapFactory.Options().apply {
            // Decoded straight into graphics memory, skipping a heap copy.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) inPreferredConfig = Bitmap.Config.HARDWARE
        }
        return BitmapFactory.decodeFile(file.path, options)
    }

    private fun writeIcon(bitmap: Bitmap, appKey: String, file: File) {
        try {
            dir.mkdirs()
            // Earlier versions of this app's icon are now stale.
            dir.listFiles { old -> old.name.startsWith("$appKey$SEPARATOR") }?.forEach(File::delete)
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSLESS
            } else {
                Bitmap.CompressFormat.PNG
            }
            file.outputStream().use { bitmap.compress(format, 100, it) }
        } catch (e: IOException) {
            // Only the disk copy is lost; it's rendered again next time.
            Log.w(TAG, "Couldn't cache the icon of $appKey", e)
        }
    }

    /** Hardware bitmaps (Android 8+) are kept only in graphics memory, with no heap copy. */
    private fun toDisplayBitmap(bitmap: Bitmap): Bitmap {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || bitmap.config == Bitmap.Config.HARDWARE) return bitmap
        val hardware = bitmap.copy(Bitmap.Config.HARDWARE, false) ?: return bitmap
        bitmap.recycle()
        return hardware
    }

    private fun loadedLabels(): MutableMap<String, Label> = labels ?: readLabels().also { labels = it }

    /** The label index: one line per app, "package TAB key TAB label". */
    private fun readLabels(): MutableMap<String, Label> {
        val result = HashMap<String, Label>()
        if (!labelIndex.isFile) return result
        try {
            labelIndex.forEachLine { line ->
                val parts = line.split(FIELD_SEPARATOR, limit = 3)
                if (parts.size == 3) result[parts[0]] = Label(parts[1], parts[2])
            }
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't read the label cache", e)
        }
        return result
    }

    private fun saveLabels() {
        try {
            dir.mkdirs()
            val text = loadedLabels().entries.joinToString("\n") { (appKey, label) ->
                // Neither separator may appear inside a field.
                val key = label.key.replace(FIELD_SEPARATOR, ' ').replace('\n', ' ')
                val name = label.text.replace(FIELD_SEPARATOR, ' ').replace('\n', ' ')
                "$appKey$FIELD_SEPARATOR$key$FIELD_SEPARATOR$name"
            }
            // Written aside and renamed, so a crash mid-write can't leave a half file.
            val temp = File(dir, "$LABEL_INDEX.tmp")
            temp.writeText(text)
            if (!temp.renameTo(labelIndex)) temp.delete()
            labelsDirty = false
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't save the label cache", e)
        }
    }

    private companion object {
        const val TAG = "AppIconCache"
        const val DIR_NAME = "app_icons"
        const val LABEL_INDEX = "labels.tsv"
        /** WebP from Android 11, PNG before; the decoder tells them apart itself. */
        const val ICON_SUFFIX = ".icon"
        /** Package names can't contain '@', so it safely ends the package part of a file name. */
        const val SEPARATOR = "@"
        const val FIELD_SEPARATOR = '\t'
    }
}
