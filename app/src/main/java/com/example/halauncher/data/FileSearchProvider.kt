package com.example.halauncher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import com.example.halauncher.model.FileEntry
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry

/**
 * Every file on shared storage, listed through MediaStore.
 *
 * Seeing non-media files (PDFs, documents, APKs, …) needs "All files access" on
 * Android 11+, which the user grants in a Settings screen; before Android 11 it's the
 * ordinary storage permission. Returns nothing without it.
 */
class FileSearchProvider(private val context: Context) : SearchEntryProvider {

    override val category = SearchCategory.FILE

    fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    @WorkerThread
    override fun loadEntries(): List<SearchEntry> {
        if (!hasAccess()) return emptyList()
        return try {
            queryFiles()
        } catch (e: SecurityException) {
            // Access revoked between the check and the query.
            emptyList()
        }
    }

    /**
     * Just the files with these MediaStore [ids], for targeted index updates; ids that no longer
     * exist (deleted files) are simply absent from the result.
     */
    @WorkerThread
    fun loadByIds(ids: Collection<Long>): List<FileEntry> {
        if (!hasAccess() || ids.isEmpty()) return emptyList()
        return try {
            ids.chunked(MAX_IDS_PER_QUERY).flatMap { chunk ->
                queryFiles(
                    extraSelection = "${MediaStore.MediaColumns._ID} IN (${chunk.joinToString(",") { "?" }})",
                    extraArgs = chunk.map(Long::toString),
                )
            }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    private fun queryFiles(extraSelection: String? = null, extraArgs: List<String> = emptyList()): List<FileEntry> {
        val collection = MediaStore.Files.getContentUri(EXTERNAL_VOLUME)
        val hasRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            if (hasRelativePath) add(MediaStore.MediaColumns.RELATIVE_PATH)
        }.toTypedArray()
        // Folders are rows too, but have no MIME type.
        val selection = buildString {
            append("${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL")
            if (extraSelection != null) append(" AND ").append(extraSelection)
        }

        val files = mutableListOf<FileEntry>()
        val args = extraArgs.takeIf { it.isNotEmpty() }?.toTypedArray()
        context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val pathCol = if (hasRelativePath) cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH) else -1
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol)?.trim()
                // Skip unnamed rows and hidden (dot) files.
                if (name.isNullOrEmpty() || name.startsWith('.')) continue
                val id = cursor.getLong(idCol)
                files += FileEntry(
                    mediaId = id,
                    name = name,
                    uri = ContentUris.withAppendedId(collection, id),
                    mimeType = cursor.getString(mimeCol),
                    sizeBytes = cursor.getLong(sizeCol),
                    // MediaStore stores seconds.
                    modifiedMillis = cursor.getLong(modifiedCol) * 1000,
                    folder = if (pathCol >= 0) cursor.getString(pathCol) else null,
                )
            }
        }
        return files
    }

    private companion object {
        /** MediaStore.VOLUME_EXTERNAL, spelled out because the constant is Android 10+. */
        const val EXTERNAL_VOLUME = "external"

        /** Keeps each targeted query under SQLite's bound-parameter limit. */
        const val MAX_IDS_PER_QUERY = 500
    }
}
