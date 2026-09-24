package com.example.halauncher.search.index

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.halauncher.search.TestApp
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InteractionLogPruneTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val day = TimeUnit.DAYS.toMillis(1)
    private val start = 1_790_244_000_000L

    private lateinit var database: SearchDatabase
    private lateinit var log: InteractionLog

    @Before
    fun setUp() {
        database = SearchDatabase(context, name = null)
        log = InteractionLog(database, retentionDays = 10, pruneIntervalMillis = day)
    }

    @After
    fun tearDown() = database.close()

    private fun rows(): Int =
        database.readableDatabase.rawQuery("SELECT COUNT(*) FROM ${SearchDatabase.INTERACTIONS}", null)
            .use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun prunesAtMostOncePerInterval() {
        // The first open prunes (nothing to delete yet) and starts the interval.
        log.record(TestApp.entry("old"), start)
        // 11 days later "old" has expired and a prune is due, so it goes.
        log.record(TestApp.entry("a"), start + 11 * day)
        assertEquals(1, rows())

        // Within the next day nothing is deleted, even once "a" expires on paper.
        log.record(TestApp.entry("b"), start + 11 * day + 1)
        assertFalse(log.pruneIfDue(start + 11 * day + day / 2))
        assertTrue(log.pruneIfDue(start + 12 * day))
    }

    @Test
    fun expiredHistoryNeverCountsEvenBeforeItIsPruned() {
        log.record(TestApp.entry("x"), start)
        log.record(TestApp.entry("x"), start + 1)
        // Still on disk (no prune due yet), but past retention by now.
        val later = start + 10 * day + 2
        assertEquals(2, rows())
        assertTrue(log.allHistories(later).isEmpty())
        assertTrue(log.historiesFor(listOf(TestApp.entry("x").id), later).isEmpty())
        assertEquals(2, log.allHistories(start + 2).getValue(TestApp.entry("x").id).openCount)
    }

    @Test
    fun lastPruneIsRememberedAcrossInstances() {
        log.record(TestApp.entry("x"), start)
        val reopened = InteractionLog(database, retentionDays = 10, pruneIntervalMillis = day)
        assertFalse(reopened.pruneIfDue(start + day / 2))
        assertTrue(reopened.pruneIfDue(start + day))
    }
}
