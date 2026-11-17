package com.example.halauncher.data

import com.example.halauncher.model.FolderEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderRulesTest {

    @Test
    fun aFolderComesWithEveryFolderAboveIt() {
        assertEquals(listOf("DCIM/", "DCIM/Camera/"), FolderRules.withAncestors("DCIM/Camera/"))
        assertEquals(listOf("Download/"), FolderRules.withAncestors("Download/"))
        assertEquals(emptyList<String>(), FolderRules.withAncestors(""))
    }

    @Test
    fun aDotAnywhereInThePathHidesTheFolder() {
        assertTrue(FolderRules.isHidden(".thumbnails/"))
        assertTrue(FolderRules.isHidden("Android/media/com.whatsapp/WhatsApp/.Shared/"))
        assertTrue(FolderRules.isHidden("DCIM/.thumbnails/2024/"))
        assertFalse(FolderRules.isHidden("Android/media/com.whatsapp/"))
        assertFalse(FolderRules.isHidden(""))
    }

    @Test
    fun reducingLeavesOutTheRootDenylistAndWhatsAppWeeks() {
        assertTrue(FolderRules.isLeftOut(""))
        // A denylisted folder itself, and anything in it.
        assertTrue(FolderRules.isLeftOut("Android/media/com.whatsapp/WhatsApp/Databases/"))
        assertTrue(FolderRules.isLeftOut("Android/media/com.whatsapp/WhatsApp/Backups/stickers/"))
        // WhatsApp's weekly folders, where it keeps them now and where older versions did.
        assertTrue(FolderRules.isLeftOut("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes/202336/"))
        assertTrue(FolderRules.isLeftOut("WhatsApp/Media/WhatsApp Video Notes/202401/"))

        assertFalse(FolderRules.isLeftOut("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes/"))
        assertFalse(FolderRules.isLeftOut("DCIM/202403/"))
        assertFalse(FolderRules.isLeftOut("DCIM/Camera/"))
    }

    @Test
    fun foldersAreNamedAndFoundIgnoringCase() {
        val camera = FolderEntry("DCIM/Camera/")
        assertEquals("Camera", camera.name)
        assertEquals("DCIM", camera.detail)
        assertNull(FolderEntry("DCIM/").detail)
        assertEquals(FolderEntry("dcim/camera/").id, camera.id)
    }
}
