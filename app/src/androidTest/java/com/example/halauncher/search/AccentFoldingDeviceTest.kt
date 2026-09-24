package com.example.halauncher.search

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The JVM unit tests fold with the desktop JDK's Normalizer; this checks the device's own
 * (ICU-based) one gives the same results, and that the trie finds accented names with it.
 */
@RunWith(AndroidJUnit4::class)
class AccentFoldingDeviceTest {
    @Test
    fun foldMatchesOnDevice() {
        assertEquals('e', foldForSearch('É'))
        assertEquals('i', foldForSearch('İ'))
        assertEquals(FOLDED_AWAY, foldForSearch('́'))
        assertEquals('한', foldForSearch('한'))
        assertEquals("cafe creme", normalizeForSearch("Café Crème"))
    }

    @Test
    fun trieFindsAccentedNamesOnDevice() {
        val trie = Trie<String>().apply {
            insert("Café Racer", "cafe")
            insert("Zoë", "zoe")
        }
        assertEquals(listOf("cafe"), trie.searchPrefix("cafe r"))
        assertEquals(listOf("zoe"), trie.cursor().search("ZOE"))
    }
}
