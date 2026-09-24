package com.example.halauncher.data

import android.content.Context
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
    fun label(info: ResolveInfo, pm: PackageManager): String {
        val packageName = info.activityInfo.packageName
        val key = labelKey(info)
        val all = loadedLabels()
        all[packageName]?.takeIf { it.key == key }?.let { return it.text }
        val text = info.loadLabel(pm).toString()
        all[packageName] = Label(key, text)
        labelsDirty = true
        return text
    }

    /** The app's icon at launcher size, from memory or disk when still valid, else rendered now. */
    @WorkerThread
    fun icon(info: ResolveInfo, pm: PackageManager): Drawable {
        val packageName = info.activityInfo.packageName
        val key = iconKey(info)
        icons[packageName]?.takeIf { it.key == key }?.let { return it.drawable }
        val file = iconFile(packageName, key)
        val bitmap = readIcon(file) ?: render(info, pm).also { writeIcon(it, packageName, file) }
        return BitmapDrawable(resources, toDisplayBitmap(bitmap))
            .also { icons[packageName] = Icon(key, it) }
    }

    /** Forgets [packageName]'s icon and label, so the next load renders them again. */
    fun invalidate(packageName: String) {
        icons.remove(packageName)
        if (loadedLabels().remove(packageName) != null) labelsDirty = true
        dir.listFiles { file -> file.name.startsWith("$packageName$SEPARATOR") }?.forEach(File::delete)
    }

    /**
     * Drops everything for apps not in [packageNames] (uninstalled since), and saves the label
     * index if it changed. Call after each full load of the app list.
     */
    @WorkerThread
    fun retainOnly(packageNames: Set<String>) {
        icons.keys.retainAll(packageNames)
        if (loadedLabels().keys.retainAll(packageNames)) labelsDirty = true
        // Keeps only the current file of each app; older versions of its icon go too.
        val wanted = icons.map { (packageName, icon) -> iconFile(packageName, icon.key).name }.toSet()
        dir.listFiles { file -> file.name.endsWith(ICON_SUFFIX) && file.name !in wanted }?.forEach(File::delete)
        if (labelsDirty) saveLabels()
    }

    private fun iconKey(info: ResolveInfo): String {
        val app = info.activityInfo.applicationInfo
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return "${app.sourceDir}|${File(app.sourceDir).lastModified()}|${info.activityInfo.name}|" +
            "$sizePx|${resources.displayMetrics.densityDpi}|$night"
    }

    private fun labelKey(info: ResolveInfo): String {
        val app = info.activityInfo.applicationInfo
        return "${app.sourceDir}|${File(app.sourceDir).lastModified()}|${info.activityInfo.name}|" +
            resources.configuration.locales.toLanguageTags()
    }

    /** One file per app and key version; the key is hashed to keep the name short. */
    private fun iconFile(packageName: String, key: String) =
        File(dir, "$packageName$SEPARATOR${Integer.toHexString(key.hashCode())}$ICON_SUFFIX")

    private fun render(info: ResolveInfo, pm: PackageManager): Bitmap {
        val drawable = info.loadIcon(pm)
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

    private fun writeIcon(bitmap: Bitmap, packageName: String, file: File) {
        try {
            dir.mkdirs()
            // Earlier versions of this app's icon are now stale.
            dir.listFiles { old -> old.name.startsWith("$packageName$SEPARATOR") }?.forEach(File::delete)
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSLESS
            } else {
                Bitmap.CompressFormat.PNG
            }
            file.outputStream().use { bitmap.compress(format, 100, it) }
        } catch (e: IOException) {
            // Only the disk copy is lost; it's rendered again next time.
            Log.w(TAG, "Couldn't cache the icon of $packageName", e)
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
            val text = loadedLabels().entries.joinToString("\n") { (packageName, label) ->
                // Neither separator may appear inside a field.
                val key = label.key.replace(FIELD_SEPARATOR, ' ').replace('\n', ' ')
                val name = label.text.replace(FIELD_SEPARATOR, ' ').replace('\n', ' ')
                "$packageName$FIELD_SEPARATOR$key$FIELD_SEPARATOR$name"
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
