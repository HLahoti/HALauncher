package com.example.halauncher.model

import android.content.Intent
import android.net.Uri

/**
 * A file on shared storage (Downloads, Documents, DCIM, …), found through MediaStore.
 *
 * Opens in whichever app handles its type, e.g. a PDF viewer for a PDF; if none does, any
 * app that accepts generic files. The receiving app gets temporary read access to this
 * one file only.
 */
data class FileEntry(
    val mediaId: Long,
    override val name: String,
    val uri: Uri,
    val mimeType: String?,
    val sizeBytes: Long,
    val modifiedMillis: Long,
    /** Folder relative to the storage root, e.g. "Download/"; null where Android doesn't report it. */
    val folder: String?,
) : SearchEntry {
    override val id: String get() = idFor(mediaId)
    override val category: SearchCategory get() = SearchCategory.FILE

    /**
     * The folder path (never the file name, which is already the title), e.g. "Android/media/…".
     * Shown in full up to the width of the row, which elides the middle only if it doesn't fit.
     */
    override val detail: String?
        get() = folder?.trim('/')?.takeIf { it.isNotEmpty() }

    override val link: Intent get() = viewIntent(mimeType ?: ANY_TYPE)

    override val fallbackLinks: List<Intent>
        get() = if (mimeType != null && mimeType != ANY_TYPE) listOf(viewIntent(ANY_TYPE)) else emptyList()

    private fun viewIntent(type: String): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    companion object {
        private const val ANY_TYPE = "*/*"

        /** The search-entry id of the file with MediaStore id [mediaId]. */
        fun idFor(mediaId: Long): String = "file:$mediaId"
    }
}
