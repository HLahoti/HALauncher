package com.example.halauncher.search

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.halauncher.model.AppInfo
import com.example.halauncher.model.ContactEntry
import com.example.halauncher.model.FileEntry
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry
import com.example.halauncher.search.index.AppEntryCodec
import com.example.halauncher.search.index.ContactEntryCodec
import com.example.halauncher.search.index.FileEntryCodec
import com.example.halauncher.search.index.InteractionLog
import com.example.halauncher.search.index.SearchDatabase
import com.example.halauncher.search.index.SearchEntryCodecs
import com.example.halauncher.search.index.SearchIndex
import com.example.halauncher.search.ranking.ContextMatchRanker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The disk layer and the engine against the device's real SQLite (FTS4, unicode61), on an
 * in-memory database so the launcher's own search.db is untouched. All data is made up.
 */
@RunWith(AndroidJUnit4::class)
class TwoLayerSearchTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var now = 1_790_244_000_000L

    private fun app(name: String, pkg: String) = AppInfo(name, pkg, ColorDrawable(), Intent())
    private val maps = app("Maps", "test.maps")
    private val mail = app("Mail", "test.mail")
    private val music = app("Music Player", "test.music")
    private val apps = listOf(maps, mail, music).associateBy { it.packageName }

    private val contact = ContactEntry(7L, "lookup-7", "Zoë Mapleton", "5550100")
    private val report = FileEntry(
        mediaId = 42L,
        name = "Quarterly_Report_2026.pdf",
        uri = Uri.parse("content://media/external/file/42"),
        mimeType = "application/pdf",
        sizeBytes = 1234L,
        modifiedMillis = 1_700_000_000_000L,
        folder = "Documents/",
    )

    private lateinit var database: SearchDatabase
    private lateinit var index: SearchIndex
    private lateinit var engine: SearchEngine

    @Before
    fun setUp() {
        database = SearchDatabase(context, name = null)
        val codecs = SearchEntryCodecs(listOf(AppEntryCodec(apps::get), ContactEntryCodec, FileEntryCodec))
        index = SearchIndex(database, codecs)
        engine = SearchEngine(index, InteractionLog(database, retentionDays = 120), ContextMatchRanker(), clock = { now })
        engine.sync(SearchCategory.APP, apps.values.toList())
        engine.sync(SearchCategory.CONTACT, listOf(contact))
        engine.sync(SearchCategory.FILE, listOf(report))
    }

    @After
    fun tearDown() = database.close()

    private fun matchIds(query: String, category: SearchCategory? = null) =
        index.match(FtsQuery.prefixMatch(searchTerms(query))!!, category, limit = 100).map { it.id }.toSet()

    @Test
    fun prefixMatchesAnyWordOfTheName() {
        assertEquals(setOf(maps.id, contact.id), matchIds("map"))
        assertEquals(setOf(report.id), matchIds("report"))
        assertEquals(setOf(report.id), matchIds("2026"))
        assertEquals(setOf(music.id), matchIds("pla mu"))
    }

    @Test
    fun accentsAndCaseAreIgnored() {
        assertEquals(setOf(contact.id), matchIds("ZOE"))
    }

    @Test
    fun categoryFilterLimitsMatches() {
        assertEquals(setOf(contact.id), matchIds("map", SearchCategory.CONTACT))
    }

    @Test
    fun everyCategoryRoundTripsThroughTheIndex() {
        val stored = index.byIds(listOf(maps.id, contact.id, report.id))
        assertEquals(maps, stored[maps.id])
        assertEquals(contact, stored[contact.id])
        assertEquals(report, stored[report.id])
    }

    @Test
    fun replacingACategoryLeavesOthersAlone() {
        engine.sync(SearchCategory.FILE, emptyList())
        assertEquals(listOf(SearchCategory.APP, SearchCategory.CONTACT), index.categories())
        assertEquals(setOf(maps.id, contact.id), matchIds("map"))
    }

    @Test
    fun uninstalledAppsAreDroppedWhenDecoding() {
        val onlyMaps = SearchIndex(database, SearchEntryCodecs(listOf(AppEntryCodec { if (it == maps.packageName) maps else null }, ContactEntryCodec, FileEntryCodec)))
        assertEquals(setOf(maps.id), onlyMaps.all(SearchCategory.APP, limit = 100).map { it.id }.toSet())
    }

    @Test
    fun frequentLayerHoldsWhatWasOpenedMostFirst() {
        repeat(3) { engine.recordOpen(report) }
        engine.recordOpen(contact)
        val layer = engine.buildFrequentLayer(seed = emptyList())
        assertEquals(listOf(report.id, contact.id), layer.searchPrefix("").map { it.id })
    }

    @Test
    fun frequentLayerIsToppedUpFromTheSeed() {
        engine.recordOpen(contact)
        val layer = engine.buildFrequentLayer(seed = listOf(maps, mail))
        assertEquals(listOf(contact.id, maps.id, mail.id), layer.searchPrefix("").map { it.id })
    }

    @Test
    fun deepSearchSkipsWhatTheFrequentLayerShowed() {
        val more = engine.deepSearch("map", category = null, excludeIds = setOf(maps.id))
        assertEquals(listOf(contact.id), more.map { it.id })
    }

    @Test
    fun deepSearchRanksExactNameFirst() {
        val mapsFile = report.copy(mediaId = 43L, name = "Maps backup.zip")
        engine.sync(SearchCategory.FILE, listOf(report, mapsFile))
        val ranked = engine.deepSearch("maps", category = null, excludeIds = emptySet()).map { it.id }
        assertEquals(maps.id, ranked.first())
        assertTrue(mapsFile.id in ranked)
    }

    @Test
    fun emptyDeepSearchShowsNothingFromDisk() {
        assertTrue(engine.deepSearch("", category = null, excludeIds = emptySet()).isEmpty())
        assertTrue(engine.deepSearch("  ", category = SearchCategory.FILE, excludeIds = emptySet()).isEmpty())
    }

    @Test
    fun deepSearchDecodesOnlyWhatItReturns() {
        // Ranked from names alone; the result is still fully decoded entries, best first.
        val ranked = engine.deepSearch("map", category = null, excludeIds = emptySet())
        assertEquals(maps.id, ranked.first().id)
        // Payloads are read after ranking; the contact comes back whole (number included).
        assertEquals(contact, ranked.single { it.id == contact.id })
    }

    @Test
    fun syncWritesOnlyTheDifference() {
        val renamed = report.copy(name = "Annual_Summary_2026.pdf")
        val added = report.copy(mediaId = 44L, name = "Notes.txt", mimeType = "text/plain")
        assertEquals(
            SearchIndex.SyncResult(inserted = 1, updated = 1, removed = 0, unchanged = 0),
            engine.sync(SearchCategory.FILE, listOf(renamed, added)),
        )
        // The renamed file is found by its new name only.
        assertEquals(setOf(report.id), matchIds("annual"))
        assertTrue(matchIds("quarterly").isEmpty())

        assertEquals(
            SearchIndex.SyncResult(inserted = 0, updated = 0, removed = 0, unchanged = 3),
            engine.sync(SearchCategory.APP, apps.values.toList()),
        )
        assertEquals(
            SearchIndex.SyncResult(inserted = 0, updated = 0, removed = 1, unchanged = 1),
            engine.sync(SearchCategory.FILE, listOf(added)),
        )
        assertEquals(emptySet<String>(), matchIds("annual"))
    }

    @Test
    fun openedEntryJoinsTheFrequentLayerAtOnce() {
        val layer = engine.buildFrequentLayer(seed = listOf(maps))
        val cursor = layer.cursor()
        assertTrue(engine.quickSearch(cursor, "quar", category = null).isEmpty())

        assertTrue(engine.addToFrequentLayer(layer, report))
        // Added in place: found by any prefix of its name, alongside what was there, even by a
        // cursor whose last walk had dead-ended on this very query.
        assertEquals(listOf(report.id), engine.quickSearch(cursor, "quar", category = null).map { it.id })
        assertEquals(listOf(maps.id, report.id), layer.searchPrefix("").map { it.id })
        // Adding it again changes nothing.
        assertFalse(engine.addToFrequentLayer(layer, report))
        assertEquals(listOf(maps.id, report.id), layer.searchPrefix("").map { it.id })
    }

    @Test
    fun pinnedEntriesSurviveARebuild() {
        val layer = engine.buildFrequentLayer(seed = emptyList(), pinned = listOf(report))
        assertEquals(listOf(report.id), layer.searchPrefix("").map { it.id })
    }

    @Test
    fun indexAndHistorySurviveReopeningTheDatabaseFile() {
        val fileName = "persistence-test.db"
        context.deleteDatabase(fileName)
        val codecs = SearchEntryCodecs(listOf(AppEntryCodec(apps::get), ContactEntryCodec, FileEntryCodec))
        try {
            // First "session": index some entries and open one.
            SearchDatabase(context, fileName).use { db ->
                val first = SearchEngine(SearchIndex(db, codecs), InteractionLog(db, 120), ContextMatchRanker(), clock = { now })
                first.sync(SearchCategory.FILE, listOf(report))
                first.sync(SearchCategory.CONTACT, listOf(contact))
                first.recordOpen(report)
                first.recordOpen(report)
            }
            // Second "session" (as after a process kill or reboot): nothing re-synced.
            SearchDatabase(context, fileName).use { db ->
                val index = SearchIndex(db, codecs)
                val second = SearchEngine(index, InteractionLog(db, 120), ContextMatchRanker(), clock = { now })
                assertEquals(listOf(SearchCategory.CONTACT, SearchCategory.FILE), index.categories())
                assertEquals(listOf(report.id), second.deepSearch("quarterly", null, emptySet()).map { it.id })
                assertEquals(listOf(report.id), second.buildFrequentLayer(seed = emptyList()).searchPrefix("").map { it.id })
                // An unchanged re-sync rewrites nothing.
                assertEquals(
                    SearchIndex.SyncResult(inserted = 0, updated = 0, removed = 0, unchanged = 1),
                    second.sync(SearchCategory.FILE, listOf(report)),
                )
            }
        } finally {
            context.deleteDatabase(fileName)
        }
    }

    @Test
    fun oldInteractionsArePrunedAsNewOnesArrive() {
        engine.recordOpen(report)
        now += 121L * 24 * 60 * 60 * 1000
        engine.recordOpen(contact)
        val layer = engine.buildFrequentLayer(seed = emptyList())
        assertEquals(listOf(contact.id), layer.searchPrefix("").map { it.id })
    }
}
