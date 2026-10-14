package com.example.halauncher.ui.home

import com.example.halauncher.model.SearchCategory
import com.example.halauncher.search.TestEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SearchResultsTest {
    private val mom = TestEntry("Mom", SearchCategory.CONTACT)
    private val sam = TestEntry("Sam", SearchCategory.CONTACT)
    private val priya = TestEntry("Priya", SearchCategory.CONTACT)

    @Test
    fun anEntryStaysInTheFirstGroupThatHasIt() {
        val results = SearchResults(
            frequent = listOf(mom),
            suggestions = listOf(
                Suggestions(SuggestionKind.FAVOURITES, listOf(mom, sam)),
                Suggestions(SuggestionKind.RECENTLY_UPDATED, listOf(priya, sam, mom)),
            ),
        ).exclusive()

        assertEquals(listOf(mom), results.frequent)
        assertEquals(
            listOf(
                Suggestions(SuggestionKind.FAVOURITES, listOf(sam)),
                Suggestions(SuggestionKind.RECENTLY_UPDATED, listOf(priya)),
            ),
            results.suggestions,
        )
    }

    @Test
    fun aGroupLeftEmptyIsDropped() {
        val results = SearchResults(
            frequent = listOf(mom, sam),
            more = listOf(sam),
            suggestions = listOf(Suggestions(SuggestionKind.FAVOURITES, listOf(mom))),
        ).exclusive()

        assertEquals(emptyList<TestEntry>(), results.more)
        assertEquals(emptyList<Suggestions>(), results.suggestions)
    }

    @Test
    fun recentsAloneAreLeftAsTheyAre() {
        val results = SearchResults(frequent = listOf(mom, sam))
        assertSame(results, results.exclusive())
    }

    @Test
    fun matchCountsAreTheRecentsUntilTheDiskAnswers() {
        val maps = TestEntry("Maps")
        val waiting = SearchResults(frequent = listOf(mom, maps), isSearchingDisk = true)
        assertEquals(mapOf(SearchCategory.CONTACT to 1, SearchCategory.APP to 1), waiting.matchCounts)

        val notes = TestEntry("Notes.pdf", SearchCategory.FILE)
        val answered = SearchResults(frequent = listOf(mom, maps), more = listOf(sam, priya, notes))
        assertEquals(
            mapOf(SearchCategory.CONTACT to 3, SearchCategory.APP to 1, SearchCategory.FILE to 1),
            answered.matchCounts,
        )
    }

    @Test
    fun matchCountsLeaveOutSuggestions() {
        val results = SearchResults(suggestions = listOf(Suggestions(SuggestionKind.FAVOURITES, listOf(mom))))
        assertEquals(emptyMap<SearchCategory, Int>(), results.matchCounts)
    }
}
