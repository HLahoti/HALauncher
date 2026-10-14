package com.example.halauncher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import com.example.halauncher.config.IconCacheConfig
import com.example.halauncher.model.ShortcutEntry
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App shortcut icons, loaded only when a shortcut is about to be shown and rendered once at
 * launcher size ([IconCacheConfig.ShortcutIconSize]). The most recently shown stay in memory, up
 * to [IconCacheConfig.SHORTCUT_ICON_MEMORY_BYTES]; the rest are asked of the system again, which
 * keeps them for every launcher anyway, so nothing is written to disk.
 *
 * An entry is keyed by the shortcut, when the app last changed it, and the size, so a new icon
 * renders a new entry.
 *
 * Safe to use from any thread.
 *
 * @param find the shortcut's current info, or null if it's gone.
 */
class ShortcutIconCache(
    context: Context,
    private val find: (ShortcutEntry) -> ShortcutInfo?,
) {
    private val appContext = context.applicationContext
    private val resources = appContext.resources
    private val launcherApps: LauncherApps? = appContext.getSystemService(LauncherApps::class.java)
    /** Read each time: the display density can change (Settings > Display size). */
    private val sizePx: Int get() = ceil(IconCacheConfig.ShortcutIconSize.value * resources.displayMetrics.density).toInt()

    private val memory = object : LruCache<String, BitmapDrawable>(IconCacheConfig.SHORTCUT_ICON_MEMORY_BYTES) {
        override fun sizeOf(key: String, value: BitmapDrawable): Int = value.bitmap.allocationByteCount
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val loadDispatcher = Dispatchers.IO.limitedParallelism(IconCacheConfig.SHORTCUT_ICON_LOAD_PARALLELISM)

    /** [shortcut]'s icon if it's already in memory, else null; cheap enough to call while composing. */
    fun cached(shortcut: ShortcutEntry): Drawable? = memory.get(keyFor(shortcut))

    /** [shortcut]'s icon at launcher size, from memory, else rendered now; null if it has none. */
    suspend fun load(shortcut: ShortcutEntry): Drawable? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return null
        val key = keyFor(shortcut)
        memory.get(key)?.let { return it }
        return withContext(loadDispatcher) {
            // Another caller may have loaded it while this one waited for a slot.
            memory.get(key) ?: run {
                val info = find(shortcut) ?: return@run null
                val drawable = try {
                    launcherApps?.getShortcutIconDrawable(info, resources.displayMetrics.densityDpi)
                } catch (e: SecurityException) {
                    // No longer the default home app.
                    null
                } catch (e: IllegalStateException) {
                    // The user's storage is still locked.
                    null
                } ?: return@run null
                BitmapDrawable(resources, CachedBitmaps.toDisplayBitmap(render(drawable))).also { memory.put(key, it) }
            }
        }
    }

    private fun keyFor(shortcut: ShortcutEntry): String = "${shortcut.id}|${shortcut.iconVersion}|$sizePx"

    private fun render(drawable: Drawable): Bitmap {
        val size = sizePx
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }
}
