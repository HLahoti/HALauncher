package com.example.halauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.annotation.WorkerThread
import com.example.halauncher.config.IconCacheConfig
import com.example.halauncher.model.ContactEntry
import java.io.File
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Contact photos, rendered once at launcher size and remembered, the way [AppIconCache] keeps app
 * icons. Unlike app icons they're loaded only when a contact is about to be shown: a phone can
 * hold thousands of contacts, and search shows a handful at a time.
 *
 * - **Small.** Each photo is cropped to a circle and drawn once into a bitmap exactly as big as
 *   search ever shows it ([IconCacheConfig.ContactPhotoSize]); the full-size photo is decoded
 *   subsampled, never whole. On Android 8+ it's kept as a hardware bitmap (graphics memory only).
 * - **Bounded in memory.** Only the most recently shown photos stay in memory, up to
 *   [IconCacheConfig.CONTACT_PHOTO_MEMORY_BYTES]; the rest are read back from disk when needed.
 * - **On disk.** Each rendered photo is also written as a small WebP in the cache directory, so
 *   showing a contact again reads one file instead of asking the contacts database. The system
 *   may clear the cache directory; anything missing is simply rendered again.
 * - **Fresh.** An entry is keyed by what it was made from: the contact, its photo URI and when it
 *   last changed, and the size. A changed key renders a new entry; contacts that no longer have
 *   that photo are dropped by [retainOnly].
 * - **Gentle.** At most [IconCacheConfig.CONTACT_PHOTO_LOAD_PARALLELISM] photos load at once.
 *
 * Safe to use from any thread.
 */
class ContactPhotoCache(context: Context) {
    private val appContext = context.applicationContext
    private val resources = appContext.resources
    private val dir = File(appContext.cacheDir, DIR_NAME)
    /** Read each time: the display density can change (Settings > Display size). */
    private val sizePx: Int get() = ceil(IconCacheConfig.ContactPhotoSize.value * resources.displayMetrics.density).toInt()

    private val memory = object : LruCache<String, BitmapDrawable>(IconCacheConfig.CONTACT_PHOTO_MEMORY_BYTES) {
        override fun sizeOf(key: String, value: BitmapDrawable): Int = value.bitmap.allocationByteCount
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val loadDispatcher = Dispatchers.IO.limitedParallelism(IconCacheConfig.CONTACT_PHOTO_LOAD_PARALLELISM)

    /** [contact]'s photo if it's already in memory, else null; cheap enough to call while composing. */
    fun cached(contact: ContactEntry): Drawable? = keyFor(contact)?.let(memory::get)

    /**
     * [contact]'s photo at launcher size, from memory or disk when still valid, else rendered now;
     * null when the contact has no photo or it can't be read.
     */
    suspend fun load(contact: ContactEntry): Drawable? {
        val key = keyFor(contact) ?: return null
        memory.get(key)?.let { return it }
        val photoUri = contact.photoUri ?: return null
        return withContext(loadDispatcher) {
            // Another caller may have loaded it while this one waited for a slot.
            memory.get(key) ?: run {
                val file = photoFile(key)
                val bitmap = CachedBitmaps.read(file)
                    ?: render(photoUri)?.also { CachedBitmaps.write(it, file, IconCacheConfig.CONTACT_PHOTO_QUALITY) }
                    ?: return@run null
                BitmapDrawable(resources, CachedBitmaps.toDisplayBitmap(bitmap)).also { memory.put(key, it) }
            }
        }
    }

    /**
     * Drops the disk copies of photos not belonging to [contacts] (deleted contacts, changed or
     * removed photos). Call after each full load of the contacts.
     */
    @WorkerThread
    fun retainOnly(contacts: Collection<ContactEntry>) {
        val wanted = contacts.mapNotNullTo(HashSet()) { contact -> keyFor(contact)?.let { photoFile(it).name } }
        dir.listFiles { file -> file.name.endsWith(PHOTO_SUFFIX) && file.name !in wanted }?.forEach(File::delete)
    }

    private fun keyFor(contact: ContactEntry): String? {
        val photoUri = contact.photoUri ?: return null
        return "${contact.lookupKey}|$photoUri|${contact.photoVersion}|$sizePx"
    }

    private fun photoFile(key: String): File = CachedBitmaps.fileFor(dir, key, PHOTO_SUFFIX)

    /** Decodes the photo just big enough for [sizePx], then crops it to a centred circle. */
    private fun render(photoUri: Uri): Bitmap? {
        val size = sizePx
        val resolver = appContext.contentResolver
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // Reads only the size (decodeStream returns null for this pass), to pick a subsample.
            resolver.openInputStream(photoUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val source = resolver.openInputStream(photoUri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return null
            CachedBitmaps.cropToRoundedSquare(source, size, cornerRadius = size / 2f).also { source.recycle() }
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't read the contact photo $photoUri", e)
            null
        } catch (e: SecurityException) {
            // Contacts permission revoked since the contact was loaded.
            null
        }
    }

    private companion object {
        const val TAG = "ContactPhotoCache"
        const val DIR_NAME = "contact_photos"
        /** WebP from Android 11, PNG before; the decoder tells them apart itself. */
        const val PHOTO_SUFFIX = ".photo"
    }
}
