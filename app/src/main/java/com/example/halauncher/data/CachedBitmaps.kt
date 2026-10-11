package com.example.halauncher.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.math.min

/**
 * Helpers shared by the caches that keep small rendered images on disk ([ContactPhotoCache],
 * [FileThumbnailCache]).
 */
internal object CachedBitmaps {
    private const val TAG = "CachedBitmaps"

    /** One file per key; hashed, since keys can hold any character. */
    fun fileFor(dir: File, key: String, suffix: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) } + suffix)
    }

    /** The bitmap stored in [file], straight into graphics memory on Android 8+; null if there's none. */
    fun read(file: File): Bitmap? {
        if (!file.isFile) return null
        val options = BitmapFactory.Options().apply {
            // Decoded straight into graphics memory, skipping a heap copy.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) inPreferredConfig = Bitmap.Config.HARDWARE
        }
        return BitmapFactory.decodeFile(file.path, options)
    }

    /**
     * Writes [bitmap] to [file]: lossy WebP at [quality] from Android 11, PNG before (the only format
     * there that keeps transparent corners). The decoder tells them apart itself.
     */
    fun write(bitmap: Bitmap, file: File, quality: Int) {
        try {
            file.parentFile?.mkdirs()
            val (format, formatQuality) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY to quality
            } else {
                Bitmap.CompressFormat.PNG to 100
            }
            // Written aside and renamed, so a reader never sees a half-written file.
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.outputStream().use { bitmap.compress(format, formatQuality, it) }
            if (!temp.renameTo(file)) temp.delete()
        } catch (e: IOException) {
            // Only the disk copy is lost; it's rendered again next time.
            Log.w(TAG, "Couldn't cache ${file.name}", e)
        }
    }

    /** Hardware bitmaps (Android 8+) are kept only in graphics memory, with no heap copy. */
    fun toDisplayBitmap(bitmap: Bitmap): Bitmap {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || bitmap.config == Bitmap.Config.HARDWARE) return bitmap
        val hardware = bitmap.copy(Bitmap.Config.HARDWARE, false) ?: return bitmap
        bitmap.recycle()
        return hardware
    }

    /**
     * [image] scaled to cover a [size]-pixel square, centred, and cut to a rounded square with
     * corners of [cornerRadius] pixels (a circle at half of [size]).
     */
    fun cropToRoundedSquare(image: Bitmap, size: Int, cornerRadius: Float): Bitmap {
        // A software canvas can't draw from a hardware bitmap (what ImageDecoder gives by default).
        val isHardware = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && image.config == Bitmap.Config.HARDWARE
        val source = if (isHardware) image.copy(Bitmap.Config.ARGB_8888, false) else image
        val scale = size.toFloat() / min(source.width, source.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((size - source.width * scale) / 2f, (size - source.height * scale) / 2f)
        }
        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), cornerRadius, cornerRadius, paint)
        if (source !== image) source.recycle()
        return bitmap
    }
}
