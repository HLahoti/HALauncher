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
}
