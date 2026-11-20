package com.example.halauncher.search

import com.example.halauncher.model.SearchFilter
import com.example.halauncher.model.SearchFilter.APPS
import com.example.halauncher.model.SearchFilter.CONTACTS
import com.example.halauncher.model.SearchFilter.FILES
import com.example.halauncher.model.SearchFilter.FOLDERS
import com.example.halauncher.model.SearchFilter.IMAGES
import com.example.halauncher.model.SearchFilter.PDFS
import com.example.halauncher.model.SearchFilter.SETTINGS
import com.example.halauncher.model.SearchFilter.SHORTCUTS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypedFilterTest {
    private val labels = mapOf(
        APPS to "Apps", CONTACTS to "Contacts", SearchFilter.EVENTS to "Events", FILES to "Files",
        SETTINGS to "Settings", SHORTCUTS to "Shortcuts", FOLDERS to "Folders", IMAGES to "Images",
        SearchFilter.VIDEOS to "Videos", PDFS to "PDFs",
    )

    @Test
    fun aFilterWordIsTakenOutOfWhatsSearched() {
        assertEquals(TypedFilter.Parsed("", "pdf"), TypedFilter.parse("!pdf"))
        assertEquals(TypedFilter.Parsed("tax return", "pdf"), TypedFilter.parse("tax !PDF return"))
        assertEquals(TypedFilter.Parsed("tax", "im"), TypedFilter.parse("!im tax"))
        assertEquals(TypedFilter.Parsed("", ""), TypedFilter.parse("!"))
    }

    @Test
    fun aPrefixInsideOrAtTheEndOfAWordIsJustText() {
        assertNull(TypedFilter.parse("Hi!").typed)
        assertNull(TypedFilter.parse("Yahoo! Mail").typed)
        assertEquals("Yahoo! Mail", TypedFilter.parse("Yahoo! Mail").rest)
        assertNull(TypedFilter.parse("maps").typed)
    }

    @Test
    fun aSlashIsJustText() {
        // "/" was the prefix once; it's an ordinary character now ("8/2" is a sum).
        assertNull(TypedFilter.parse("/pdf").typed)
        assertNull(TypedFilter.parse("8/2").typed)
    }

    @Test
    fun theStartOfANameCallsUpItsFilters() {
        assertEquals(listOf(PDFS), TypedFilter.matching("pd", labels))
        assertEquals(listOf(IMAGES), TypedFilter.matching("im", labels))
        assertEquals(listOf(IMAGES), TypedFilter.matching("photo", labels))
        // "s" starts Settings and Shortcuts.
        assertEquals(setOf(SETTINGS, SHORTCUTS), TypedFilter.matching("s", labels).toSet())
        assertTrue(TypedFilter.matching("xyz", labels).isEmpty())
    }

    @Test
    fun aNameInFullComesFirst() {
        assertEquals(FOLDERS, TypedFilter.matching("folder", labels).first())
        assertEquals(FILES, TypedFilter.matching("files", labels).first())
    }

    @Test
    fun aLonePrefixCallsUpEveryFilter() {
        assertEquals(SearchFilter.entries, TypedFilter.matching("", labels))
    }

    @Test
    fun theChipsShownNameMatchesToo() {
        // A translated name ("Fotos") works alongside the English ones.
        assertEquals(listOf(IMAGES), TypedFilter.matching("fot", labels + (IMAGES to "Fotos")))
    }
}
