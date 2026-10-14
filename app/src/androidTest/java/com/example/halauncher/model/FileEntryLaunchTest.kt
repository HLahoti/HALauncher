package com.example.halauncher.model

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.halauncher.ui.components.firstHandledIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks, against the real apps on the device, how a file entry opens. Uses made-up files;
 * nothing is read from or written to the device's storage.
 */
@RunWith(AndroidJUnit4::class)
class FileEntryLaunchTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val apk = FileEntry(
        mediaId = 1L,
        name = "Example.apk",
        uri = Uri.parse("content://media/external/file/1"),
        mimeType = "application/vnd.android.package-archive",
        sizeBytes = 1L,
        modifiedMillis = 0L,
        folder = "Download/Apps/",
    )

    @Test
    fun apkOpensItsFolderInTheFileManager() {
        val link = apk.link
        assertEquals(Intent.ACTION_VIEW, link.action)
        assertEquals(DocumentsContract.Document.MIME_TYPE_DIR, link.type)
        assertEquals("primary:Download/Apps", DocumentsContract.getDocumentId(link.data))
        assertNotNull("no file manager opens the folder", context.firstHandledIntent(listOf(link)))
    }

    @Test
    fun apkNeverGoesStraightToTheInstaller() {
        (listOf(apk.link) + apk.fallbackLinks).forEach { assertTrue(it.data != apk.uri) }
    }

    @Test
    fun apkIsRecognisedByNameWhenTheTypeIsMissing() {
        assertTrue(apk.copy(mimeType = null).isApk)
        assertFalse(apk.copy(name = "Example.pdf", mimeType = "application/pdf").isApk)
    }

    @Test
    fun apkWithoutAKnownFolderStillOpensAFileManager() {
        val noFolder = apk.copy(folder = null)
        assertNotNull(context.firstHandledIntent(listOf(noFolder.link) + noFolder.fallbackLinks))
    }

    @Test
    fun otherFilesStillOpenThemselves() {
        val pdf = apk.copy(name = "Example.pdf", mimeType = "application/pdf")
        assertEquals(pdf.uri, pdf.link.data)
        assertEquals("application/pdf", pdf.link.type)
    }
}
