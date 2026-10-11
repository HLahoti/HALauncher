package com.example.halauncher.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import android.util.Size
import com.example.halauncher.config.IconCacheConfig
import com.example.halauncher.model.FileEntry
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Preview thumbnails of image, video, audio (album art) and PDF files, for search's file results.
 * Like [ContactPhotoCache], each one is loaded only when its file is about to be shown, and as
 * cheaply as possible:
 *
 * - **Borrowed where it can be.** Images, videos and audio already have thumbnails the system
 *   makes once and keeps (MediaStore), so those are asked for at search's size and not stored
 *   again. Only what this cache renders itself is written to disk: PDFs (the first page), and
 *   album art before Android 10, when the system doesn't provide it.
 * - **Small.** Each thumbnail is drawn once into a rounded square exactly as big as search shows
 *   it ([IconCacheConfig.FileThumbnailSize]); on Android 8+ it's kept as a hardware bitmap.
 * - **Bounded.** Only the most recently shown thumbnails stay in memory, up to
 *   [IconCacheConfig.FILE_THUMBNAIL_MEMORY_BYTES]. The disk copies are capped at
 *   [IconCacheConfig.FILE_THUMBNAIL_DISK_FILES], dropping the oldest first.
 * - **Never asked twice.** A file that has no thumbnail (a song without album art, a
 *   password-protected PDF) is remembered as such, in memory and with an empty marker on disk, so
 *   showing it again does no work.
 * - **Fresh.** An entry is keyed by the file, when it last changed, its size and the thumbnail
 *   size, so an edited file gets a new thumbnail.
 * - **Gentle.** At most [IconCacheConfig.FILE_THUMBNAIL_LOAD_PARALLELISM] thumbnails load at once,
 *   and one PDF at a time. A load whose row scrolls away is cancelled, and the system stops making
 *   a thumbnail it was still working on.
 *
 * Safe to use from any thread.
 */
class FileThumbnailCache(context: Context) {
    private val appContext = context.applicationContext
    private val resources = appContext.resources
    private val dir = File(appContext.cacheDir, DIR_NAME)
    /** Read each time: the display density can change (Settings > Display size). */
    private val sizePx: Int get() = ceil(IconCacheConfig.FileThumbnailSize.value * resources.displayMetrics.density).toInt()
    private val cornerRadiusPx: Float get() = IconCacheConfig.FileThumbnailCornerRadius.value * resources.displayMetrics.density

    private val memory = object : LruCache<String, BitmapDrawable>(IconCacheConfig.FILE_THUMBNAIL_MEMORY_BYTES) {
        override fun sizeOf(key: String, value: BitmapDrawable): Int = value.bitmap.allocationByteCount
    }

    /** Keys of files known to have no thumbnail, so they aren't tried again. */
    private val missing = LruCache<String, Boolean>(MISSING_IN_MEMORY)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val loadDispatcher = Dispatchers.IO.limitedParallelism(IconCacheConfig.FILE_THUMBNAIL_LOAD_PARALLELISM)

    /** PdfRenderer is heavy, and older versions misbehave with several open at once. */
    private val pdfLock = Mutex()

    /** Disk writes since the disk copies were last trimmed; trimmed on the first write, then every so often. */
    private val writesSinceTrim = AtomicInteger(TRIM_EVERY_WRITES)

    /** Whether [file] is a kind that can have a thumbnail at all; cheap enough to call while composing. */
    fun canHaveThumbnail(file: FileEntry): Boolean = kindOf(file) != null

    /** [file]'s thumbnail if it's already in memory, else null; cheap enough to call while composing. */
    fun cached(file: FileEntry): Drawable? = if (canHaveThumbnail(file)) memory.get(keyFor(file)) else null

    /**
     * [file]'s thumbnail at search's size, from memory, the system, or disk when still valid, else
     * rendered now; null when its kind has none, the file has none, or it can't be read.
     */
    suspend fun load(file: FileEntry): Drawable? {
        val kind = kindOf(file) ?: return null
        val key = keyFor(file)
        memory.get(key)?.let { return it }
        if (missing.get(key) != null) return null
        return withContext(loadDispatcher) {
            // Another caller may have loaded it while this one waited for a slot.
            memory.get(key) ?: run {
                val noneMarker = CachedBitmaps.fileFor(dir, key, NONE_SUFFIX)
                if (noneMarker.exists()) {
                    missing.put(key, true)
                    return@run null
                }
                val storesOnDisk = kind.rendersItself
                val thumbFile = CachedBitmaps.fileFor(dir, key, THUMB_SUFFIX)
                val bitmap = (if (storesOnDisk) CachedBitmaps.read(thumbFile) else null)
                    ?: when (val outcome = render(file, kind)) {
                        is Outcome.Rendered -> outcome.bitmap.also {
                            if (storesOnDisk) {
                                CachedBitmaps.write(it, thumbFile, IconCacheConfig.FILE_THUMBNAIL_QUALITY)
                                afterDiskWrite()
                            }
                        }
                        Outcome.None -> {
                            missing.put(key, true)
                            markNone(noneMarker)
                            return@run null
                        }
                        Outcome.Unavailable -> return@run null
                    }
                BitmapDrawable(resources, CachedBitmaps.toDisplayBitmap(bitmap)).also { memory.put(key, it) }
            }
        }
    }

    private fun keyFor(file: FileEntry): String = "${file.mediaId}|${file.modifiedMillis}|${file.sizeBytes}|$sizePx"

    private suspend fun render(file: FileEntry, kind: Kind): Outcome = try {
        val size = sizePx
        when {
            kind == Kind.PDF -> pdfLock.withLock { renderPdf(file, size) }
            kind == Kind.AUDIO && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> fitted(legacyAlbumArt(file, size), size)
            else -> fitted(systemThumbnail(file, kind, size), size)
        }
    } catch (e: OperationCanceledException) {
        // The row scrolled away; nothing is known about this file yet.
        Outcome.Unavailable
    } catch (e: SecurityException) {
        // All files access revoked since the file was indexed.
        Outcome.Unavailable
    } catch (e: IOException) {
        // The system has no thumbnail for it (e.g. a song without album art) or can't make one.
        Outcome.None
    } catch (e: RuntimeException) {
        // A file the decoders choke on, which some report with unchecked exceptions.
        Log.w(TAG, "Couldn't make a thumbnail of ${file.name}", e)
        Outcome.None
    }

    /** [source] cut to search's rounded square, or [Outcome.None] when there's no image. */
    private fun fitted(source: Bitmap?, size: Int): Outcome {
        source ?: return Outcome.None
        return Outcome.Rendered(CachedBitmaps.cropToRoundedSquare(source, size, cornerRadiusPx).also { source.recycle() })
    }

    /**
     * The system's own thumbnail of a media file, which it makes once and keeps. Asked for through
     * the image, video or audio collection, since the generic files collection doesn't serve them.
     */
    private suspend fun systemThumbnail(file: FileEntry, kind: Kind, size: Int): Bitmap? {
        val resolver = appContext.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = when (kind) {
                Kind.IMAGE -> MediaStore.Images.Media.getContentUri(EXTERNAL_VOLUME)
                Kind.VIDEO -> MediaStore.Video.Media.getContentUri(EXTERNAL_VOLUME)
                else -> MediaStore.Audio.Media.getContentUri(EXTERNAL_VOLUME)
            }
            val uri = ContentUris.withAppendedId(collection, file.mediaId)
            // At least this big on both sides; the system decodes its stored copy down to about that.
            return withCancellationSignal { signal -> resolver.loadThumbnail(uri, Size(size, size), signal) }
        }
        // The pre-Android 10 thumbnails (about 512 × 384), also made once and kept by the system.
        @Suppress("DEPRECATION")
        return when (kind) {
            Kind.IMAGE -> MediaStore.Images.Thumbnails.getThumbnail(resolver, file.mediaId, MediaStore.Images.Thumbnails.MINI_KIND, null)
            else -> MediaStore.Video.Thumbnails.getThumbnail(resolver, file.mediaId, MediaStore.Video.Thumbnails.MINI_KIND, null)
        }
    }

    /** A song's embedded album art before Android 10, decoded just big enough for [size]. */
    private fun legacyAlbumArt(file: FileEntry, size: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        val art = try {
            retriever.setDataSource(appContext, file.uri)
            retriever.embeddedPicture
        } finally {
            retriever.release()
        } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(art, 0, art.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        return BitmapFactory.decodeByteArray(art, 0, art.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /**
     * The top of a PDF's first page, on white (pages are transparent), filling a [size]-pixel
     * square: the page's width fills it for a portrait page, and its centre for a landscape one.
     */
    private fun renderPdf(file: FileEntry, size: Int): Outcome {
        val descriptor = appContext.contentResolver.openFileDescriptor(file.uri, "r") ?: return Outcome.Unavailable
        // The renderer owns the descriptor from here on, and closes it.
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (e: SecurityException) {
            // Password-protected.
            descriptor.close()
            return Outcome.None
        } catch (e: IOException) {
            descriptor.close()
            return Outcome.None
        }
        renderer.use {
            if (renderer.pageCount == 0) return Outcome.None
            renderer.openPage(0).use { page ->
                if (page.width <= 0 || page.height <= 0) return Outcome.None
                val scale = size.toFloat() / min(page.width, page.height)
                val matrix = Matrix().apply {
                    setScale(scale, scale)
                    postTranslate((size - page.width * scale) / 2f, 0f)
                }
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return Outcome.Rendered(CachedBitmaps.cropToRoundedSquare(bitmap, size, cornerRadiusPx).also { bitmap.recycle() })
            }
        }
    }

    /** Runs the blocking [block], cancelling the signal it's given if this coroutine is cancelled meanwhile. */
    private suspend fun <T> withCancellationSignal(block: (CancellationSignal) -> T): T = coroutineScope {
        val signal = CancellationSignal()
        // Unconfined, so it reacts to cancellation at once even while every load slot is busy.
        val watcher = launch(Dispatchers.Unconfined) {
            try {
                awaitCancellation()
            } finally {
                signal.cancel()
            }
        }
        try {
            block(signal)
        } finally {
            watcher.cancel()
        }
    }

    private fun markNone(marker: File) {
        try {
            dir.mkdirs()
            marker.createNewFile()
            afterDiskWrite()
        } catch (e: IOException) {
            // Only means it's tried again next time.
        }
    }

    /** Keeps the disk copies under their cap, checking only every so often since listing the folder costs. */
    private fun afterDiskWrite() {
        if (writesSinceTrim.getAndIncrement() < TRIM_EVERY_WRITES) return
        writesSinceTrim.set(0)
        val files = dir.listFiles() ?: return
        val excess = files.size - IconCacheConfig.FILE_THUMBNAIL_DISK_FILES
        if (excess <= 0) return
        // Down to three quarters of the cap, so trimming doesn't recur with every few new files.
        val toDelete = excess + IconCacheConfig.FILE_THUMBNAIL_DISK_FILES / 4
        files.sortedBy(File::lastModified).take(toDelete).forEach(File::delete)
    }

    private fun kindOf(file: FileEntry): Kind? {
        val type = file.mimeType ?: return null
        return when {
            type.startsWith("image/") -> Kind.IMAGE
            type.startsWith("video/") -> Kind.VIDEO
            type.startsWith("audio/") -> Kind.AUDIO
            type == "application/pdf" -> Kind.PDF
            else -> null
        }
    }

    private enum class Kind {
        IMAGE, VIDEO, AUDIO, PDF;

        /** Whether this cache makes these thumbnails itself (and so keeps them), rather than the system. */
        val rendersItself: Boolean
            get() = this == PDF || (this == AUDIO && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
    }

    private sealed interface Outcome {
        class Rendered(val bitmap: Bitmap) : Outcome
        /** The file has no thumbnail; remembered, so it isn't tried again. */
        data object None : Outcome
        /** Couldn't tell this time (cancelled, or access lost); tried again next time. */
        data object Unavailable : Outcome
    }

    private companion object {
        const val TAG = "FileThumbnailCache"
        const val DIR_NAME = "file_thumbnails"
        /** WebP from Android 11, PNG before; the decoder tells them apart itself. */
        const val THUMB_SUFFIX = ".thumb"
        /** An empty file marking a file that has no thumbnail. */
        const val NONE_SUFFIX = ".none"
        /** MediaStore.VOLUME_EXTERNAL, spelled out because the constant is Android 10+. */
        const val EXTERNAL_VOLUME = "external"
        /** How many files known to have no thumbnail are remembered in memory. */
        const val MISSING_IN_MEMORY = 1024
        const val TRIM_EVERY_WRITES = 32
    }
}
