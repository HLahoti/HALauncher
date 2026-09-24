package com.example.halauncher.search

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.halauncher.model.ContactEntry
import com.example.halauncher.model.FileEntry
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.search.index.AppEntryCodec
import com.example.halauncher.search.index.ContactEntryCodec
import com.example.halauncher.search.index.FileEntryCodec
import com.example.halauncher.search.index.IndexChange
import com.example.halauncher.search.index.SearchDatabase
import com.example.halauncher.search.index.SearchEntryCodecs
import com.example.halauncher.search.index.SearchIndex
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The updater's bulk writes on the device's real SQLite: several kinds of change in one
 * transaction, and searches reading concurrently while a write transaction is open (WAL).
 * Uses its own database file (WAL needs a file) and made-up data.
 */
@RunWith(AndroidJUnit4::class)
class BulkUpdateTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fileName = "bulk-update-test.db"
    private lateinit var database: SearchDatabase
    private lateinit var index: SearchIndex

    private fun file(id: Long, name: String) = FileEntry(
        mediaId = id,
        name = name,
        uri = Uri.parse("content://media/external/file/$id"),
        mimeType = "text/plain",
        sizeBytes = 1,
        modifiedMillis = 0,
        folder = "Download/",
    )

    private val contact = ContactEntry(1, "lookup-1", "Ada Lovelace", null)

    @Before
    fun setUp() {
        context.deleteDatabase(fileName)
        database = SearchDatabase(context, fileName)
        index = SearchIndex(database, SearchEntryCodecs(listOf(AppEntryCodec { null }, ContactEntryCodec, FileEntryCodec)))
        index.syncCategory(SearchCategory.FILE, listOf(file(1, "alpha.txt"), file(2, "beta.txt")))
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(fileName)
    }

    private fun names(category: SearchCategory) = index.all(category, limit = 100).map { it.name }

    @Test
    fun mixedChangesApplyTogether() {
        val results = index.applyChanges(
            listOf(
                IndexChange.Upsert(SearchCategory.FILE, listOf(file(2, "beta-renamed.txt"), file(3, "gamma.txt"))),
                IndexChange.Remove(SearchCategory.FILE, listOf(FileEntry.idFor(1))),
                IndexChange.ReplaceAll(SearchCategory.CONTACT, listOf(contact)),
            ),
        )
        assertEquals(listOf("beta-renamed.txt", "gamma.txt"), names(SearchCategory.FILE))
        assertEquals(listOf("Ada Lovelace"), names(SearchCategory.CONTACT))
        assertEquals(SearchIndex.SyncResult(inserted = 1, updated = 1, removed = 1, unchanged = 0), results[SearchCategory.FILE])
        assertEquals(SearchIndex.SyncResult(inserted = 1, updated = 0, removed = 0, unchanged = 0), results[SearchCategory.CONTACT])
    }

    @Test
    fun upsertingUnchangedEntriesWritesNothing() {
        val results = index.applyChanges(listOf(IndexChange.Upsert(SearchCategory.FILE, listOf(file(1, "alpha.txt")))))
        assertEquals(SearchIndex.SyncResult(0, 0, 0, unchanged = 1), results[SearchCategory.FILE])
    }

    @Test
    fun removingUnknownIdsIsHarmless() {
        val results = index.applyChanges(listOf(IndexChange.Remove(SearchCategory.FILE, listOf(FileEntry.idFor(99)))))
        assertEquals(0, results.getValue(SearchCategory.FILE).removed)
        assertEquals(listOf("alpha.txt", "beta.txt"), names(SearchCategory.FILE))
    }

    @Test
    fun searchesReadWithoutWaitingWhileABulkWriteIsOpen() {
        val writerInside = CountDownLatch(1)
        val readerDone = CountDownLatch(1)
        var seenDuringWrite = emptyList<String>()

        // A background "bulk update" holds its transaction open with uncommitted changes...
        val writer = thread {
            val db = database.writableDatabase
            db.beginTransactionNonExclusive()
            try {
                db.execSQL("DELETE FROM ${SearchDatabase.ENTRIES}")
                writerInside.countDown()
                // ...until the reader has finished (or 5 s, if reads were blocked).
                readerDone.await(5, TimeUnit.SECONDS)
            } finally {
                db.endTransaction() // rolled back: never marked successful
            }
        }
        assertTrue(writerInside.await(5, TimeUnit.SECONDS))

        // A search on another thread reads the last committed state straight away.
        val started = System.nanoTime()
        thread {
            seenDuringWrite = names(SearchCategory.FILE)
            readerDone.countDown()
        }
        assertTrue("read blocked by the write", readerDone.await(2, TimeUnit.SECONDS))
        val tookMs = (System.nanoTime() - started) / 1_000_000
        writer.join()

        assertEquals(listOf("alpha.txt", "beta.txt"), seenDuringWrite)
        assertTrue("read took $tookMs ms", tookMs < 1_000)
    }

    @Test
    fun databaseIsInWriteAheadLogMode() {
        database.readableDatabase.rawQuery("PRAGMA journal_mode", null).use {
            it.moveToFirst()
            assertEquals("wal", it.getString(0).lowercase())
        }
    }
}
