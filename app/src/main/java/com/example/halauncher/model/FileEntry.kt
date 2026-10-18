package com.example.halauncher.model

import android.content.Intent
import android.net.Uri

/**
 * A file on shared storage (Downloads, Documents, DCIM, …), found through MediaStore.
 *
 * Opens in whichever app handles its type, e.g. a PDF viewer for a PDF; if none does, any
 * app that accepts generic files. The receiving app gets temporary read access to this
 * one file only. An APK is the exception: see [isApk].
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
     * Shown in full when it fits; otherwise the start is cut and as much of the end as fits is kept.
     */
    override val detail: String?
        get() = folder?.trim('/')?.takeIf { it.isNotEmpty() }

    /**
     * An app package. The installer only accepts one from an app that declares
     * REQUEST_INSTALL_PACKAGES, which the launcher doesn't (Google Play restricts it), so it
     * would close without a word. An APK opens its folder in the file manager instead, and
     * the user taps it there.
     */
    val isApk: Boolean
        get() = mimeType == APK_TYPE || name.endsWith(".apk", ignoreCase = true)

    override val link: Intent
        get() = if (isApk) apkLinks.first() else viewIntent(mimeType ?: ANY_TYPE)

    override val fallbackLinks: List<Intent>
        get() = when {
            isApk -> apkLinks.drop(1)
            mimeType != null && mimeType != ANY_TYPE -> listOf(viewIntent(ANY_TYPE))
            else -> emptyList()
        }

    /**
     * Where an APK sends the user: its folder in the system file manager, or failing that the top of
     * storage or the file manager (see [StorageLinks.folder]). Android only reports the folder from 10 on.
     */
    private val apkLinks: List<Intent>
        get() = StorageLinks.folder(folder)

    private fun viewIntent(type: String): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    companion object {
        private const val ANY_TYPE = "*/*"
        private const val APK_TYPE = "application/vnd.android.package-archive"

        /** The search-entry id of the file with MediaStore id [mediaId]. */
        fun idFor(mediaId: Long): String = "file:$mediaId"
    }
}
