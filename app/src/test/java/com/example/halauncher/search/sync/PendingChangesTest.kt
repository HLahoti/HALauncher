package com.example.halauncher.search.sync

import com.example.halauncher.config.IndexUpdaterConfig
import com.example.halauncher.model.SearchCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingChangesTest {
    private var now = 1_000L
    private val pending = PendingChanges(targetedLimit = 5, clock = { now })

    @Test
    fun manyEventsForACategoryBecomeOneFullSync() {
        repeat(50) { pending.requestFullSync(SearchCategory.CONTACT) }
        assertEquals(Batch(setOf(SearchCategory.CONTACT), emptySet(), urgent = false), pending.drain())
        assertNull(pending.drain())
    }

    @Test
    fun fileChangesAreBufferedAsDistinctIds() {
        pending.filesChanged(listOf(1, 2))
        pending.filesChanged(listOf(2, 3))
        assertEquals(setOf(1L, 2L, 3L), pending.drain()!!.fileIds)
    }

    @Test
    fun tooManyFileIdsEscalateToAFullFileSync() {
        pending.filesChanged((1L..6L).toList())
        val batch = pending.drain()!!
        assertEquals(setOf(SearchCategory.FILE), batch.fullSync)
        assertTrue(batch.fileIds.isEmpty())
    }

    @Test
    fun aPendingFullFileSyncAbsorbsLaterFileIds() {
        pending.requestFullSync(SearchCategory.FILE)
        pending.filesChanged(listOf(9))
        assertTrue(pending.drain()!!.fileIds.isEmpty())
    }

    @Test
    fun timingTracksTheBurst() {
        pending.requestFullSync(SearchCategory.APP)
        now = 1_500
        pending.filesChanged(listOf(1))
        assertEquals(BurstTiming(firstEventAt = 1_000, lastEventAt = 1_500, urgent = false), pending.timing())
        pending.drain()
        assertNull(pending.timing())
        now = 9_000
        pending.requestFullSync(SearchCategory.APP)
        assertEquals(9_000L, pending.timing()!!.firstEventAt)
    }

    @Test
    fun urgencyIsStickyUntilDrained() {
        pending.requestFullSync(SearchCategory.CONTACT, urgent = true)
        pending.filesChanged(listOf(1))
        assertTrue(pending.timing()!!.urgent)
        assertTrue(pending.drain()!!.urgent)
        pending.filesChanged(listOf(2))
        assertFalse(pending.timing()!!.urgent)
    }

    @Test
    fun restoredBatchMergesWithNewerChanges() {
        pending.requestFullSync(SearchCategory.CONTACT, urgent = true)
        pending.filesChanged(listOf(1))
        val batch = pending.drain()!!
        pending.filesChanged(listOf(2))
        pending.restore(batch)
        assertEquals(Batch(setOf(SearchCategory.CONTACT), setOf(2L, 1L), urgent = true), pending.drain())
    }

    @Test
    fun emptyFileListIsIgnored() {
        pending.filesChanged(emptyList())
        assertTrue(pending.isEmpty)
    }
}

class FlushDelayTest {
    private val quiet = IndexUpdaterConfig.QUIET_PERIOD_MS
    private val cap = IndexUpdaterConfig.MAX_BATCH_DELAY_MS
    private val slow = IndexUpdaterConfig.SLOW_MODE_DELAY_MULTIPLIER

    @Test
    fun waitsForTheQuietPeriodAfterTheLastEvent() {
        val burst = BurstTiming(firstEventAt = 0, lastEventAt = 500, urgent = false)
        assertEquals(500 + quiet - 1_000, flushDelayMillis(now = 1_000, burst, Throttle.NORMAL))
    }

    @Test
    fun neverWaitsPastTheCapFromTheFirstEvent() {
        // Events keep arriving just inside the quiet period...
        val burst = BurstTiming(firstEventAt = 0, lastEventAt = cap - 100, urgent = false)
        // ...but the cap from the first event still applies.
        assertEquals(100L, flushDelayMillis(now = cap - 100, burst, Throttle.NORMAL))
        assertEquals(0L, flushDelayMillis(now = cap + 1, burst, Throttle.NORMAL))
    }

    @Test
    fun slowModeStretchesBothWaits() {
        val burst = BurstTiming(firstEventAt = 0, lastEventAt = 0, urgent = false)
        assertEquals(quiet * slow, flushDelayMillis(now = 0, burst, Throttle.SLOW))
    }

    @Test
    fun urgentFlushesAtOnce() {
        val burst = BurstTiming(firstEventAt = 0, lastEventAt = 0, urgent = true)
        assertEquals(0L, flushDelayMillis(now = 0, burst, Throttle.SLOW))
    }
}

class ResourceStateTest {
    private val none = 0
    private val light = 1
    private val moderate = 2
    private val severe = 3
    private val critical = 4

    @Test
    fun coolAndOnNormalPowerRunsNormally() {
        assertEquals(Throttle.NORMAL, ResourceState(powerSaveMode = false, thermalStatus = none).throttle)
        assertEquals(Throttle.NORMAL, ResourceState(powerSaveMode = false, thermalStatus = light).throttle)
    }

    @Test
    fun moderateHeatSlowsDown() {
        assertEquals(Throttle.SLOW, ResourceState(powerSaveMode = false, thermalStatus = moderate).throttle)
    }

    @Test
    fun severeHeatOrWorsePauses() {
        assertEquals(Throttle.PAUSED, ResourceState(powerSaveMode = false, thermalStatus = severe).throttle)
        assertEquals(Throttle.PAUSED, ResourceState(powerSaveMode = false, thermalStatus = critical).throttle)
    }

    @Test
    fun batterySaverPauses() {
        assertEquals(Throttle.PAUSED, ResourceState(powerSaveMode = true, thermalStatus = none).throttle)
    }

    @Test
    fun urgentWorkMayRunUnderBatterySaverButNeverWhenHot() {
        val saver = ResourceState(powerSaveMode = true, thermalStatus = none)
        assertFalse(saver.allows(urgent = false))
        assertTrue(saver.allows(urgent = true))
        val hot = ResourceState(powerSaveMode = false, thermalStatus = severe)
        assertFalse(hot.allows(urgent = true))
    }

    @Test
    fun slowedWorkStillRuns() {
        assertTrue(ResourceState(powerSaveMode = false, thermalStatus = moderate).allows(urgent = false))
    }
}
