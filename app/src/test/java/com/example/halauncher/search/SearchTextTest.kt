package com.example.halauncher.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchTextTest {
    @Test
    fun normalizeFoldsCaseAndAccents() {
        assertEquals("cafe creme", normalizeForSearch("Café Crème"))
    }

    @Test
    fun termsSplitOnAnythingButLettersAndDigits() {
        assertEquals(listOf("img", "2024", "09", "jpg"), searchTerms("IMG_2024-09.jpg"))
        assertEquals(listOf("google", "maps"), searchTerms("  Google   Maps "))
    }

    @Test
    fun prefixMatchMakesEveryTermAPrefix() {
        assertEquals("spo* mu*", FtsQuery.prefixMatch(listOf("spo", "mu")))
    }

    @Test
    fun ftsOperatorsAndSyntaxCannotLeakIn() {
        // Quotes, minus and parentheses are split away; operators come out lower case.
        assertEquals("a* or* b* not* c*", FtsQuery.prefixMatch(searchTerms("\"A\" OR (b) -NOT c")))
    }

    @Test
    fun emptyQueryHasNoMatchExpression() {
        assertNull(FtsQuery.prefixMatch(searchTerms("  -- ")))
    }

    @Test
    fun foldStripsAccentsAndCase() {
        assertEquals('e', foldForSearch('É'))
        assertEquals('n', foldForSearch('ñ'))
        assertEquals('o', foldForSearch('Ö'))
        assertEquals('a', foldForSearch('A'))
        assertEquals('7', foldForSearch('7'))
        assertEquals(' ', foldForSearch(' '))
    }

    @Test
    fun turkishDottedCapitalIFoldsToPlainI() {
        assertEquals('i', foldForSearch('İ'))
    }

    @Test
    fun loneCombiningAccentFoldsAway() {
        assertEquals(FOLDED_AWAY, foldForSearch('\u0301'))
    }

    @Test
    fun charactersWithoutASingleBaseLetterAreOnlyLowerCased() {
        // A Hangul syllable decomposes into several letters; it stays whole.
        assertEquals('한', foldForSearch('한'))
        assertEquals("한국", normalizeForSearch("한국"))
    }

    @Test
    fun precomposedAndDecomposedAccentsNormalizeAlike() {
        assertEquals(normalizeForSearch("Café"), normalizeForSearch("Cafe\u0301"))
        assertEquals("cafe", normalizeForSearch("Cafe\u0301"))
    }
}
