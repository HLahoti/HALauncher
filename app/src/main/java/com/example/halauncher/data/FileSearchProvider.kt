package com.example.halauncher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import com.example.halauncher.model.FileEntry
import com.example.halauncher.model.FolderEntry
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry
import java.io.File

/**
 * Every file on shared storage, listed through MediaStore.
 *
 * Seeing non-media files (PDFs, documents, APKs, …) needs "All files access" on
 * Android 11+, which the user grants in a Settings screen; before Android 11 it's the
 * ordinary storage permission. Returns nothing without it.
 */
/**
 * Result of a targeted [FileSearchProvider.loadByIds] lookup: the [entries] to index and the ids of
 * every requested file that still exists (denylisted ones included, so they aren't mistaken for
 * deletions).
 */
data class TargetedFiles(
    val entries: List<FileEntry>,
    val existingIds: Set<Long>,
)

/**
 * @param denylistEnabled whether [FilePathDenylist] folders are left out of the index right now
 *   (the "Reduce index size" setting). Read on each load, so flipping it and re-syncing takes effect.
 */
class FileSearchProvider(
    private val context: Context,
    private val denylistEnabled: () -> Boolean = { false },
) : SearchEntryProvider {

    override val category = SearchCategory.FILE

    /** Preview thumbnails of the files, loaded as search shows them. */
    val thumbnails = FileThumbnailCache(context)

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
            // A full sync leaves denylisted folders out from the start, when the setting is on.
            queryFiles(excludeDenylisted = denylistEnabled())
        } catch (e: SecurityException) {
            // Access revoked between the check and the query.
            emptyList()
        }
    }

    /**
     * The files with these MediaStore [ids], for targeted index updates.
     *
     * [TargetedFiles.entries] are the ones to index, with denylisted folders ([FilePathDenylist])
     * removed. [TargetedFiles.existingIds] are *all* the requested ids that still exist in
     * MediaStore, denylisted or not, so the caller can tell a denylisted file (leave it alone) from
     * a deleted one (drop it from the index) without knowing the denylist. Ids in neither are gone.
     */
    @WorkerThread
    fun loadByIds(ids: Collection<Long>): TargetedFiles {
        if (!hasAccess() || ids.isEmpty()) return TargetedFiles(emptyList(), emptySet())
        return try {
            // Query without the denylist so denylisted-but-present files still count as existing.
            val found = ids.chunked(MAX_IDS_PER_QUERY).flatMap { chunk ->
                queryFiles(
                    excludeDenylisted = false,
                    extraSelection = "${MediaStore.MediaColumns._ID} IN (${chunk.joinToString(",") { "?" }})",
                    extraArgs = chunk.map(Long::toString),
                )
            }
            TargetedFiles(
                entries = if (denylistEnabled()) found.filterNot { FilePathDenylist.matches(it.folder) } else found,
                existingIds = found.mapTo(HashSet()) { it.mediaId },
            )
        } catch (e: SecurityException) {
            TargetedFiles(emptyList(), emptySet())
        }
    }

    /**
     * The [limit] most recently added or modified files, newest first, leaving out the same files
     * a full sync would (hidden ones, and denylisted folders while that setting is on). A file
     * copied or restored onto the device can keep an old modified time, so the time MediaStore
     * first saw it counts too.
     */
    @WorkerThread
    fun loadRecentlyAdded(limit: Int): List<FileEntry> {
        if (!hasAccess()) return emptyList()
        return try {
            queryFiles(
                excludeDenylisted = denylistEnabled(),
                sortOrder = "MAX(${MediaStore.MediaColumns.DATE_ADDED}, ${MediaStore.MediaColumns.DATE_MODIFIED}) DESC",
                limit = limit,
            )
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /**
     * Every folder of internal storage that search lists (see [FolderRules]): hidden ones never, and
     * those [FolderRules.leftOutWhenReduced] while "Reduce index size" is on. Android 10+ only, as
     * older versions don't report where files are.
     *
     * MediaStore has a row for most folders, empty ones included, but not every one: a folder can
     * hold indexed files without a row of its own (WhatsApp's "Pictures/WhatsApp", say). So the
     * folders the files are in, and the folders above those, are listed too. A folder row can also
     * outlive its folder, so rows are checked against storage.
     *
     * @param fileFolders the folders of every file ([FileEntry.folder]) when they've just been
     *   loaded anyway, as in a full file sync; otherwise they're read from MediaStore.
     */
    @WorkerThread
    fun loadFolders(fileFolders: Collection<String?>? = null): List<FolderEntry> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !hasAccess()) return emptyList()
        return try {
            val folders = LinkedHashMap<String, FolderEntry>()
            for (folder in queryFolderRows()) folders.putIfAbsent(folder.id, folder)
            for (path in (fileFolders ?: queryFileFolders()).filterNotNullTo(HashSet())) {
                for (ancestor in FolderRules.withAncestors(path)) {
                    FolderEntry(ancestor).let { folders.putIfAbsent(it.id, it) }
                }
            }
            folders.values.filter(::isListed)
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /**
     * The folders [files] are in, and the folders above those, as [loadFolders] would list them: to
     * tell whether new files have turned up somewhere search doesn't list yet.
     */
    fun foldersOf(files: Collection<FileEntry>): List<FolderEntry> =
        files.mapNotNullTo(HashSet()) { it.folder }
            .flatMap(FolderRules::withAncestors)
            .map(::FolderEntry)
            .distinctBy { it.id }
            .filter(::isListed)

    private fun isListed(folder: FolderEntry): Boolean =
        !FolderRules.isHidden(folder.path) && !(denylistEnabled() && FolderRules.leftOutWhenReduced(folder.path))

    /**
     * MediaStore's folder rows on internal storage. Their paths come from the full path in DATA,
     * which (unlike RELATIVE_PATH) also tells the storage root apart from a top-level folder.
     * Folders on other volumes (an SD card) are left out: they can't be opened the same way.
     */
    private fun queryFolderRows(): List<FolderEntry> {
        @Suppress("DEPRECATION") // DATA is still filled in, and readable with all-files access.
        val dataColumn = MediaStore.MediaColumns.DATA
        @Suppress("DEPRECATION")
        val root = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
        val folders = mutableListOf<FolderEntry>()
        context.contentResolver.query(
            MediaStore.Files.getContentUri(EXTERNAL_VOLUME),
            arrayOf(dataColumn, MediaStore.MediaColumns.DISPLAY_NAME),
            "$FORMAT_COLUMN = $FORMAT_FOLDER",
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val data = cursor.getString(0)?.trimEnd('/') ?: continue
                // A deleted folder's row can linger until MediaStore's next scan; check it's still there.
                if (!File(data).isDirectory) continue
                val path = when {
                    data.equals(root, ignoreCase = true) -> ""
                    data.startsWith("$root/", ignoreCase = true) -> data.substring(root.length + 1) + "/"
                    else -> continue
                }
                val name = cursor.getString(1)?.trim()?.takeIf { it.isNotEmpty() } ?: FolderEntry.nameOf(path)
                folders += FolderEntry(path, name)
            }
        }
        return folders
    }

    /** The distinct folders that files are in (RELATIVE_PATH), read straight from MediaStore. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun queryFileFolders(): Set<String> {
        val folders = HashSet<String>()
        context.contentResolver.query(
            MediaStore.Files.getContentUri(EXTERNAL_VOLUME),
            arrayOf(MediaStore.MediaColumns.RELATIVE_PATH),
            "${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL",
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) cursor.getString(0)?.let(folders::add)
        }
        return folders
    }

    /**
     * @param sortOrder MediaStore's row order; with a [limit], reading stops once that many files
     *   are kept, so only the first rows are ever read (LIMIT itself isn't accepted the same way on
     *   every Android version, and hidden files are skipped after the query anyway).
     */
    private fun queryFiles(
        excludeDenylisted: Boolean,
        extraSelection: String? = null,
        extraArgs: List<String> = emptyList(),
        sortOrder: String? = null,
        limit: Int = Int.MAX_VALUE,
    ): List<FileEntry> {
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
        val selectionParts = mutableListOf("${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL")
        val argsList = mutableListOf<String>()
        // The denylist filters on RELATIVE_PATH, which only exists on Android Q+.
        if (excludeDenylisted && hasRelativePath) {
            val (clause, denyArgs) = FilePathDenylist.sqlExclusion(MediaStore.MediaColumns.RELATIVE_PATH)
            selectionParts += clause
            argsList += denyArgs
        }
        if (extraSelection != null) {
            selectionParts += extraSelection
            argsList += extraArgs
        }
        val selection = selectionParts.joinToString(" AND ")

        val files = mutableListOf<FileEntry>()
        val args = argsList.takeIf { it.isNotEmpty() }?.toTypedArray()
        context.contentResolver.query(collection, projection, selection, args, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val pathCol = if (hasRelativePath) cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH) else -1
            while (files.size < limit && cursor.moveToNext()) {
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

        /**
         * MediaStore's MTP format column, and its code for a folder ("association"). Folders are
         * the rows with no MIME type, but so is the odd file MediaStore couldn't type.
         */
        const val FORMAT_COLUMN = "format"
        const val FORMAT_FOLDER = 0x3001
    }
}
