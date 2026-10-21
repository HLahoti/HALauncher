package com.example.halauncher.search

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import com.example.halauncher.model.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TrieTest {
    // Placeholders; the stub android.jar can construct these with unitTests.isReturnDefaultValues.
    private val icon = ColorDrawable()
    private val intent = Intent()

    private fun app(name: String) = AppInfo(name, "pkg.$name", icon, intent)

    private val calendar = app("Calendar")
    private val camera = app("Camera")
    private val chrome = app("Chrome")

    private fun trieOf(vararg apps: AppInfo) = Trie<AppInfo>().apply { apps.forEach { insert(it.name, it) } }

    @Test
    fun emptyTrie() {
        val trie = Trie<AppInfo>()
        assertTrue(trie.isEmpty())
        assertEquals(emptyList<AppInfo>(), trie.searchPrefix("a"))
    }

    @Test
    fun moveToFrontPutsTheValueFirstAlongItsPathOnly() {
        val trie = trieOf(calendar, camera, chrome)
        val before = trie.searchPrefix("c")

        assertTrue(trie.moveToFront(chrome.name, chrome) { it === chrome })
        assertEquals(listOf(chrome, calendar, camera), trie.searchPrefix(""))
        assertEquals(listOf(chrome, calendar, camera), trie.searchPrefix("c"))
        assertEquals(listOf(calendar, camera), trie.searchPrefix("ca"))
        assertEquals(listOf(chrome), trie.searchPrefix("chrome"))
        // Lists handed out earlier don't change.
        assertEquals(listOf(calendar, camera, chrome), before)
        // Already first: nothing to do.
        assertFalse(trie.moveToFront(chrome.name, chrome) { it === chrome })

        // A value not in the trie yet is added in front.
        val clock = app("Clock")
        assertTrue(trie.moveToFront(clock.name, clock) { it === clock })
        assertEquals(listOf(clock, chrome, calendar, camera), trie.searchPrefix("c"))
        assertEquals(listOf(clock), trie.searchPrefix("cl"))
    }

    @Test
    fun prefixNarrowsResultsInInsertionOrder() {
        val trie = trieOf(calendar, camera, chrome)
        assertEquals(listOf(calendar, camera, chrome), trie.searchPrefix("c"))
        assertEquals(listOf(calendar, camera), trie.searchPrefix("ca"))
        assertEquals(listOf(camera), trie.searchPrefix("cam"))
        assertEquals(listOf(chrome), trie.searchPrefix("chrome"))
    }

    @Test
    fun searchIsCaseInsensitive() {
        val trie = trieOf(chrome)
        assertEquals(listOf(chrome), trie.searchPrefix("CHr"))
    }

    @Test
    fun noMatchAndOverlongPrefix() {
        val trie = trieOf(chrome)
        assertEquals(emptyList<AppInfo>(), trie.searchPrefix("x"))
        assertEquals(emptyList<AppInfo>(), trie.searchPrefix("chromes"))
    }

    @Test
    fun emptyPrefixMatchesAll() {
        val trie = trieOf(calendar, chrome)
        assertEquals(listOf(calendar, chrome), trie.searchPrefix(""))
    }

    @Test
    fun insertIntoAPopulatedTrieUpdatesEveryPrefix() {
        val trie = trieOf(calendar, chrome)
        trie.insert(camera.name, camera)
        assertEquals(listOf(calendar, chrome, camera), trie.searchPrefix("c"))
        assertEquals(listOf(calendar, camera), trie.searchPrefix("ca"))
        assertEquals(listOf(camera), trie.searchPrefix("camera"))
    }

    @Test
    fun containsFindsValuesUnderAPrefix() {
        val trie = trieOf(calendar)
        assertTrue(trie.contains("Calendar") { it == calendar })
        assertTrue(trie.contains("cal") { it == calendar })
        assertFalse(trie.contains("Calendars") { it == calendar })
        assertFalse(trie.contains("Calendar") { it == chrome })
    }

    @Test
    fun readsReturnTheSharedListWithoutCopying() {
        val trie = trieOf(calendar, camera)
        assertSame(trie.searchPrefix("ca"), trie.searchPrefix("ca"))
        assertSame(trie.searchPrefix("ca"), trie.cursor().search("ca"))
    }

    @Test
    fun aListAlreadyReturnedIsUnchangedByLaterInserts() {
        val trie = trieOf(calendar)
        val before = trie.searchPrefix("c")
        trie.insert(chrome.name, chrome)
        assertEquals(listOf(calendar), before)
        assertEquals(listOf(calendar, chrome), trie.searchPrefix("c"))
    }

    @Test
    fun surroundingWhitespaceIsIgnored() {
        val trie = trieOf(chrome)
        assertEquals(listOf(chrome), trie.searchPrefix("  chr "))
        assertEquals(listOf(chrome), trie.cursor().search("\tchr  "))
    }

    @Test
    fun cursorFollowsTypingBackspaceAndEdits() {
        val trie = trieOf(calendar, camera, chrome)
        val cursor = trie.cursor()
        assertEquals(listOf(calendar, camera, chrome), cursor.search("c"))
        assertEquals(listOf(calendar, camera), cursor.search("ca"))
        assertEquals(listOf(camera), cursor.search("cam"))
        assertEquals(listOf(calendar, camera), cursor.search("ca")) // backspace
        assertEquals(listOf(chrome), cursor.search("ch")) // edit mid-word
        assertEquals(listOf(calendar, camera, chrome), cursor.search("")) // cleared
    }

    @Test
    fun cursorStaysEmptyPastADeadEndAndRecoversOnBackspace() {
        val trie = trieOf(chrome)
        val cursor = trie.cursor()
        assertEquals(emptyList<AppInfo>(), cursor.search("chx"))
        assertEquals(emptyList<AppInfo>(), cursor.search("chxyz"))
        assertEquals(listOf(chrome), cursor.search("ch"))
    }

    @Test
    fun cursorSeesEntriesInsertedAfterItsLastWalk() {
        val trie = trieOf(chrome)
        val cursor = trie.cursor()
        assertEquals(emptyList<AppInfo>(), cursor.search("cam"))
        trie.insert(camera.name, camera)
        assertEquals(listOf(camera), cursor.search("cam"))
    }

    @Test
    fun cursorAlwaysAgreesWithAFreshWalk() {
        val trie = trieOf(calendar, camera, chrome, app("Cal Sync"), app("Maps"))
        val cursor = trie.cursor()
        val random = java.util.Random(7)
        val alphabet = "calmeroshynp "
        repeat(2_000) {
            val query = buildString { repeat(random.nextInt(8)) { append(alphabet[random.nextInt(alphabet.length)]) } }
            assertEquals("query '$query'", trie.searchPrefix(query), cursor.search(query))
        }
    }

    @Test
    fun anyWordOfTheNameMatches() {
        val spotify = app("Spotify Music")
        val sheets = app("Files by Google")
        val trie = trieOf(spotify, sheets, app("WhatsApp-Business"), app("Quarterly_Report_2026.pdf"))
        assertEquals(listOf(spotify), trie.searchPrefix("music"))
        assertEquals(listOf(sheets), trie.searchPrefix("goo"))
        assertEquals(listOf("WhatsApp-Business"), trie.searchPrefix("busi").map { it.name })
        assertEquals(listOf("Quarterly_Report_2026.pdf"), trie.searchPrefix("2026").map { it.name })
        // Only word starts, not the middle of a word.
        assertEquals(emptyList<AppInfo>(), trie.searchPrefix("usic"))
    }

    @Test
    fun everyQueryWordMustStartSomeWordInAnyOrder() {
        val spotify = app("Spotify Music")
        val musixmatch = app("Musixmatch")
        val trie = trieOf(spotify, musixmatch)
        assertEquals(listOf(spotify), trie.searchPrefix("spo mu"))
        assertEquals(listOf(spotify), trie.searchPrefix("mu spo"))
        assertEquals(listOf(spotify), trie.searchPrefix("spotify-mus"))
        assertEquals(emptyList<AppInfo>(), trie.searchPrefix("spo x"))
        // Trailing separators add no word.
        assertSame(trie.searchPrefix("mu"), trie.searchPrefix("mu - "))
    }

    @Test
    fun namesStartingWithTheQueryComeFirst() {
        val spotify = app("Spotify Music")
        val musixmatch = app("Musixmatch")
        val trie = trieOf(spotify, musixmatch)
        assertEquals(listOf(musixmatch, spotify), trie.searchPrefix("mus"))
        assertEquals(listOf(spotify, musixmatch), trie.searchPrefix(""))
    }

    @Test
    fun aValueIsListedOnceEvenWhenSeveralOfItsWordsMatch() {
        val mega = app("Mega Music Maker")
        val maps = app("Maps")
        val trie = trieOf(mega, maps)
        assertEquals(listOf(mega, maps), trie.searchPrefix("m"))
        assertEquals(listOf(mega), trie.searchPrefix("mu"))
        // Two query words may start the same name word, as on disk.
        assertEquals(listOf(mega, maps), trie.searchPrefix("m m"))
    }

    @Test
    fun moveToFrontAlsoLeadsUnderLaterWords() {
        val spotify = app("Spotify Music")
        val musixmatch = app("Musixmatch")
        val player = app("Music Player")
        val trie = trieOf(musixmatch, player, spotify)
        assertTrue(trie.moveToFront(spotify.name, spotify) { it === spotify })
        // Still behind the names that start with "mu", but first among the rest.
        assertEquals(listOf(musixmatch, player, spotify), trie.searchPrefix("mu"))
        assertTrue(trie.moveToFront(player.name, player) { it === player })
        assertEquals(listOf(player, musixmatch, spotify), trie.searchPrefix("mu"))
        assertEquals(listOf(player, spotify), trie.searchPrefix("music"))
        assertEquals(listOf(player), trie.searchPrefix("pla"))
        assertEquals(listOf(player, spotify, musixmatch), trie.searchPrefix(""))
    }

    @Test
    fun punctuationAloneMatchesNothingButBlankMatchesAll() {
        val trie = trieOf(chrome)
        assertEquals(emptyList<AppInfo>(), trie.searchPrefix("-"))
        assertEquals(emptyList<AppInfo>(), trie.cursor().search("-"))
        assertEquals(listOf(chrome), trie.searchPrefix("   "))
        assertEquals(listOf(chrome), trie.cursor().search("   "))
    }

    @Test
    fun matchesExactlyWhatTheDiskIndexWould() {
        val names = listOf("Spotify Music", "Musixmatch", "Files by Google", "Café Crème", "Mega Music Maker", "Maps", "Zoë Mapleton", "Quarterly_Report_2026.pdf", "Cal Sync")
        val apps = names.map(::app)
        val trie = Trie<AppInfo>().apply { apps.forEach { insert(it.name, it) } }
        val cursor = trie.cursor()
        val random = java.util.Random(23)
        val alphabet = "smucafgoepl2 -é"
        repeat(3_000) {
            val query = buildString { repeat(random.nextInt(9)) { append(alphabet[random.nextInt(alphabet.length)]) } }
            val terms = searchTerms(query)
            val expected = when {
                query.isBlank() -> apps.toSet()
                // A query with no words has no disk results either.
                terms.isEmpty() -> emptySet()
                else -> apps.filter { a -> terms.all { t -> searchTerms(a.name).any { it.startsWith(t) } } }.toSet()
            }
            assertEquals("query '$query'", expected, trie.searchPrefix(query).toSet())
            assertEquals("query '$query'", trie.searchPrefix(query).size, trie.searchPrefix(query).toSet().size)
            assertEquals("query '$query'", trie.searchPrefix(query), cursor.search(query))
        }
    }

    @Test
    fun accentsAreIgnoredBothWays() {
        val cafe = app("Café")
        val plain = app("resume")
        val trie = trieOf(cafe, plain)
        assertEquals(listOf(cafe), trie.searchPrefix("cafe"))
        assertEquals(listOf(cafe), trie.searchPrefix("CAFÉ"))
        assertEquals(listOf(plain), trie.searchPrefix("résumé"))
        // A decomposed accent (e + combining acute) folds the same as the precomposed one.
        assertEquals(listOf(cafe), trie.searchPrefix("cafe\u0301"))
        assertTrue(trie.contains("cafe") { it == cafe })
    }

    @Test
    fun cursorTreatsAccentedAndPlainTypingAsTheSameWalk() {
        val cafe = app("Café Racer")
        val trie = trieOf(cafe, chrome)
        val cursor = trie.cursor()
        assertEquals(listOf(cafe), cursor.search("caf"))
        assertEquals(listOf(cafe), cursor.search("café")) // é folds to e: one step on from "caf"
        assertEquals(listOf(cafe), cursor.search("cafe\u0301")) // the trailing accent folds away
        assertEquals(listOf(cafe), cursor.search("cafe r"))
    }

    @Test
    fun cursorAgreesWithAFreshWalkOnAccentedText() {
        val trie = trieOf(app("Café"), app("Crème Brûlée"), app("Ñandú"), app("cafe noir"), chrome)
        val cursor = trie.cursor()
        val random = java.util.Random(11)
        val alphabet = "caféèrmbuûlñnd\u0301 "
        repeat(2_000) {
            val query = buildString { repeat(random.nextInt(8)) { append(alphabet[random.nextInt(alphabet.length)]) } }
            assertEquals("query '$query'", trie.searchPrefix(query), cursor.search(query))
        }
    }
}
