package com.example.halauncher.search

import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchFilter
import com.example.halauncher.model.SearchFilter.APPS
import com.example.halauncher.model.SearchFilter.CONTACTS
import com.example.halauncher.model.SearchFilter.EVENTS
import com.example.halauncher.model.SearchFilter.FILES
import com.example.halauncher.model.SearchFilter.FOLDERS
import com.example.halauncher.model.SearchFilter.IMAGES
import com.example.halauncher.model.SearchFilter.PDFS
import com.example.halauncher.model.SearchFilter.SETTINGS
import com.example.halauncher.model.SearchFilter.SHORTCUTS
import com.example.halauncher.model.SearchFilter.VIDEOS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterSuggesterTest {
    private fun app(name: String = "Maps") = TestEntry(name, SearchCategory.APP)
    private fun contact(name: String = "Mom") = TestEntry(name, SearchCategory.CONTACT)
    private fun event(name: String = "Meeting") = TestEntry(name, SearchCategory.EVENT)
    private fun file(name: String) = TestEntry(name, SearchCategory.FILE)
    private fun folder(name: String = "Download") = TestEntry(name, SearchCategory.FOLDER)
    private fun setting(name: String = "Display") = TestEntry(name, SearchCategory.SETTING)
    private fun shortcut(name: String = "New tab") = TestEntry(name, SearchCategory.SHORTCUT)

    private fun suggest(
        listed: List<TestEntry>,
        query: String = "ma",
        tab: SearchCategory? = null,
        unlisted: Map<SearchFilter, Int> = emptyMap(),
        previous: List<SearchFilter> = emptyList(),
        provisional: Boolean = false,
    ) = FilterSuggester.suggest(tab, query, listed, unlisted, previous, provisional)

    @Test
    fun aFileKindIsToldByItsExtension() {
        assertTrue(PDFS.matches(file("Masala chai recipe.PDF")))
        assertTrue(IMAGES.matches(file("IMG_2041.jpeg")))
        assertTrue(VIDEOS.matches(file("clip.mp4")))
        assertFalse(PDFS.matches(file("notes.txt")))
        assertFalse(PDFS.matches(file("pdf")))
        assertTrue(FILES.matches(folder()))
        assertFalse(FOLDERS.matches(file("a.pdf")))
        assertEquals(listOf("%.pdf"), PDFS.nameSuffixes)
        assertEquals(listOf(SearchCategory.FILE), PDFS.searchedCategories)
        // Shortcuts come with the apps found, so they're searched for with them.
        assertTrue(SearchCategory.APP in SHORTCUTS.searchedCategories)
    }

    @Test
    fun nothingTypedSuggestsNothing() {
        assertTrue(suggest(listOf(app(), contact()), query = " ").isEmpty())
    }

    @Test
    fun aChipThatWouldShowEverythingIsLeftOut() {
        assertTrue(suggest(listOf(app("Maps"), app("Mail"))).isEmpty())
    }

    @Test
    fun chipsComeFromTheKindsListed_bestRankedFirst() {
        assertEquals(listOf(CONTACTS, APPS, FILES), suggest(listOf(contact(), app(), file("a.txt"), app("Mail"))))
    }

    @Test
    fun twoChipsThatWouldShowTheSameResults_onlyTheNarrowerShows() {
        // Every app-side match is a setting: "Apps" and "Settings" would show the same.
        val chips = suggest(listOf(setting("Do Not Disturb"), setting("DND access"), folder(), folder("Documents")), query = "do")
        assertTrue(APPS !in chips)
        assertTrue(SETTINGS in chips)
        // Likewise "Files" when every file is a folder.
        assertTrue(FILES !in chips)
        assertTrue(FOLDERS in chips)
    }

    @Test
    fun fileKindsAreOffered_andNarrowerWhenTheyCoverAllTheFiles() {
        val chips = suggest(listOf(app(), file("Masala chai recipe.pdf"), file("Tax return.pdf"), contact()), query = "re")
        assertTrue(PDFS in chips)
        assertTrue(FILES !in chips)
        val mixed = suggest(listOf(file("beach.jpg"), file("beach.mp4"), file("beach.pdf"), app("Beach Buggy")), query = "beach")
        assertTrue(FILES in mixed)
    }

    @Test
    fun onATabOnlyItsKindsAreOffered() {
        assertEquals(setOf(SETTINGS, SHORTCUTS), FilterSuggester.candidatesFor(SearchCategory.APP).toSet())
        assertEquals(setOf(FOLDERS, IMAGES, VIDEOS, PDFS), FilterSuggester.candidatesFor(SearchCategory.FILE).toSet())
        assertTrue(FilterSuggester.candidatesFor(SearchCategory.CONTACT).isEmpty())
        val chips = suggest(listOf(file("a.jpg"), file("b.png"), file("c.pdf")), tab = SearchCategory.FILE)
        assertEquals(listOf(IMAGES, PDFS), chips)
    }

    @Test
    fun diskMatchesThatDidNotMakeTheListStillCount() {
        val chips = suggest(listOf(app()), query = "pl", unlisted = mapOf(EVENTS to 4))
        assertEquals(setOf(APPS, EVENTS), chips.toSet())
    }

    @Test
    fun queryWordsLiftTheKindTheyHintAt() {
        val listed = listOf(app(), app("Mail"), contact(), setting())
        assertEquals(SETTINGS, suggest(listed, query = "wifi").first())
        assertEquals(SETTINGS, suggest(listed, query = "blue").first())
        assertEquals(IMAGES, suggest(listOf(app(), contact(), file("x.pdf"), file("beach.jpg")), query = "photo beach").first())
        // A hint never brings in a kind with no matches.
        assertTrue(FOLDERS !in suggest(listed, query = "downloads"))
    }

    @Test
    fun whileTyping_theChipsShowingKeepTheirPlaces() {
        val first = suggest(listOf(contact(), app(), file("a.txt"), event()))
        assertEquals(listOf(CONTACTS, APPS, FILES), first)
        // Mid-typing, a reordering doesn't move them.
        val next = suggest(listOf(app(), contact(), file("a.txt"), event()), query = "mar", previous = first, provisional = true)
        assertEquals(first, next)
    }

    @Test
    fun onceTheDiskAnswers_theBestLeads() {
        // Shortcuts was the only chip mid-typing; the disk's answer shows apps rank higher.
        val chips = suggest(
            listOf(app("Maps"), shortcut("Home"), shortcut("Work"), contact("Maya"), app("Mail")),
            previous = listOf(SHORTCUTS),
        )
        assertEquals(APPS, chips.first())
        assertTrue(SHORTCUTS in chips)
    }

    @Test
    fun aMuchStrongerNewcomerReplacesTheWeakest() {
        val first = listOf(CONTACTS, APPS, FILES)
        val chips = suggest(listOf(event(), contact(), app(), file("a.txt")), query = "meeting", previous = first)
        assertEquals(EVENTS, chips.first())
        assertEquals(3, chips.size)
    }

    @Test
    fun aChipThatNoLongerAppliesIsDropped() {
        assertEquals(listOf(APPS, FILES), suggest(listOf(app(), file("a.txt")), query = "mart", previous = listOf(CONTACTS, APPS)))
    }

    @Test
    fun whileTheDiskIsStillToAnswer_chipsShowingStay() {
        val chips = suggest(listOf(app(), file("a.txt")), query = "plan", previous = listOf(EVENTS, APPS), provisional = true)
        assertEquals(listOf(EVENTS, APPS), chips.take(2))
        assertEquals(listOf(EVENTS, APPS), suggest(emptyList(), query = "plann", previous = listOf(EVENTS, APPS), provisional = true))
    }
}
