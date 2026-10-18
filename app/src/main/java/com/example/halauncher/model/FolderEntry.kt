package com.example.halauncher.model

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import com.example.halauncher.R
import java.io.File
import java.util.Locale

/**
 * A folder on shared storage (internal storage), such as "DCIM/Camera". Opens in the system file
 * manager; if no app will show it there, its path is copied to the clipboard instead.
 *
 * @property path the folder relative to the storage root, with a trailing slash ("DCIM/Camera/"),
 * the same form as MediaStore's RELATIVE_PATH; "" for the root itself.
 */
data class FolderEntry(
    val path: String,
    override val name: String = nameOf(path),
) : SearchEntry {
    override val id: String get() = idFor(path)
    override val category: SearchCategory get() = SearchCategory.FOLDER

    /** The folder it's in, e.g. "DCIM" for "DCIM/Camera/"; null at the top of storage. */
    override val detail: String?
        get() = path.trimEnd('/').substringBeforeLast('/', missingDelimiterValue = "").takeIf { it.isNotEmpty() }

    /** The folder in the system file manager. */
    override val link: Intent get() = StorageLinks.folder(path).first()

    /**
     * Opens [link]; if no app will show the folder, copies its full path to the clipboard instead
     * (with a message saying so), so the user can find it some other way. Either way it's handled,
     * so there's nothing else to try.
     */
    override fun launchDirectly(context: Context): Boolean {
        val opened = try {
            context.startActivity(Intent(link).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
        if (!opened) {
            @Suppress("DEPRECATION") // Still the path of internal storage, and what file managers show.
            val fullPath = File(Environment.getExternalStorageDirectory(), path).absolutePath
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText(name, fullPath))
            Toast.makeText(context, context.getString(R.string.folder_path_copied, name), Toast.LENGTH_LONG).show()
        }
        return true
    }

    companion object {
        /**
         * The search-entry id of the folder at [path]. Shared storage ignores case, so "Download/"
         * and "download/" are the same folder and get the same id.
         */
        fun idFor(path: String): String = "folder:" + path.lowercase(Locale.ROOT)

        /** The last part of [path]: "Camera" for "DCIM/Camera/". */
        fun nameOf(path: String): String = path.trimEnd('/').substringAfterLast('/')
    }
}
