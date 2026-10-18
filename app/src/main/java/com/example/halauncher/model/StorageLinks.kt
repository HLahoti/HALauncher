package com.example.halauncher.model

import android.content.Intent
import android.provider.DocumentsContract

/** Links that open a folder of shared storage in the system file manager. */
internal object StorageLinks {
    /** The system provider for shared storage, and its id for internal storage. */
    private const val STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val PRIMARY_ROOT = "primary"

    /**
     * Where to send the user to show folder [path] (relative to the storage root, e.g. "DCIM/Camera/";
     * blank or null for the root itself), best first: that folder in the system file manager, else
     * the top of internal storage, else whichever file manager opens from the app drawer. The folder
     * is assumed to be on internal storage.
     */
    fun folder(path: String?): List<Intent> = listOfNotNull(
        path?.trim('/')?.takeIf { it.isNotEmpty() }?.let { folder ->
            Intent(Intent.ACTION_VIEW).setDataAndType(
                DocumentsContract.buildDocumentUri(STORAGE_AUTHORITY, "$PRIMARY_ROOT:$folder"),
                DocumentsContract.Document.MIME_TYPE_DIR,
            )
        },
        Intent(Intent.ACTION_VIEW).setDataAndType(
            DocumentsContract.buildRootUri(STORAGE_AUTHORITY, PRIMARY_ROOT),
            DocumentsContract.Root.MIME_TYPE_ITEM,
        ),
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_FILES),
    )
}
