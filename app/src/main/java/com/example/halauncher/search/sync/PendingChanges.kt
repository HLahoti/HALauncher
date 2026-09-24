package com.example.halauncher.search.sync

import com.example.halauncher.config.IndexUpdaterConfig
import com.example.halauncher.model.SearchCategory
import java.util.EnumSet

/**
 * What has changed since the last flush, collected from change events so that a burst of them
 * becomes one bulk update.
 *
 * - A category-wide change (a contacts edit, an app installed) marks the category for one full
 *   re-sync, however many events arrive; the sync itself writes only the differences.
 * - File changes arrive with MediaStore ids and are kept as ids, so a flush looks up just those
 *   files. Past [targetedLimit] ids that's no longer cheaper than a full file re-sync, so the
 *   buffer switches to that (and stops growing).
 *
 * Also tracks when the burst started and last changed, for [flushDelayMillis]. Thread-safe:
 * events arrive on binder and looper threads, and the updater drains on its own.
 */
class PendingChanges(
    private val targetedLimit: Int = IndexUpdaterConfig.TARGETED_UPDATE_LIMIT,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val fullSync: EnumSet<SearchCategory> = EnumSet.noneOf(SearchCategory::class.java)
    private val fileIds = LinkedHashSet<Long>()
    private val changedPackages = LinkedHashSet<String>()
    private var urgent = false
    private var firstEventAt = 0L
    private var lastEventAt = 0L

    /** A category needs a full re-sync. [urgent] (user-initiated) skips the debounce wait. */
    @Synchronized
    fun requestFullSync(category: SearchCategory, urgent: Boolean = false) {
        val wasEmpty = isEmpty
        fullSync += category
        // A full file sync covers any individual files already waiting.
        if (category == SearchCategory.FILE) fileIds.clear()
        noteEvent(wasEmpty, urgent)
    }

    /**
     * These apps were installed, updated, removed or changed: a full app re-sync, remembering
     * which ones so their cached icons and labels are rendered again.
     */
    @Synchronized
    fun appsChanged(packageNames: Collection<String>) {
        changedPackages += packageNames
        requestFullSync(SearchCategory.APP)
    }

    /**
     * Makes whatever is pending flush without waiting for the debounce, e.g. because the user is
     * about to search it. Does nothing when nothing is pending.
     */
    @Synchronized
    fun markUrgent() {
        if (!isEmpty) urgent = true
    }

    /** These MediaStore files were added, changed or removed. */
    @Synchronized
    fun filesChanged(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val wasEmpty = isEmpty
        if (SearchCategory.FILE !in fullSync) {
            fileIds += ids
            if (fileIds.size > targetedLimit) {
                fileIds.clear()
                fullSync += SearchCategory.FILE
            }
        }
        noteEvent(wasEmpty, urgent = false)
    }

    @get:Synchronized
    val isEmpty: Boolean get() = fullSync.isEmpty() && fileIds.isEmpty()

    /** Timing of the current burst, or null when nothing is pending. */
    @Synchronized
    fun timing(): BurstTiming? = if (isEmpty) null else BurstTiming(firstEventAt, lastEventAt, urgent)

    /** Takes everything pending and starts a new burst; null when nothing is pending. */
    @Synchronized
    fun drain(): Batch? {
        if (isEmpty) return null
        val batch = Batch(
            fullSync = if (fullSync.isEmpty()) emptySet() else EnumSet.copyOf(fullSync),
            fileIds = fileIds.toSet(),
            urgent = urgent,
            changedPackages = changedPackages.toSet(),
        )
        fullSync.clear()
        fileIds.clear()
        changedPackages.clear()
        urgent = false
        return batch
    }

    /** Puts back a batch that couldn't be finished (e.g. paused for heat), merged with anything newer. */
    @Synchronized
    fun restore(batch: Batch) {
        changedPackages += batch.changedPackages
        batch.fullSync.forEach { requestFullSync(it, batch.urgent) }
        filesChanged(batch.fileIds)
    }

    private fun noteEvent(startsBurst: Boolean, urgent: Boolean) {
        val now = clock()
        if (startsBurst) firstEventAt = now
        lastEventAt = now
        this.urgent = this.urgent || urgent
    }
}

/** When the pending burst started and last changed, and whether anything in it is urgent. */
data class BurstTiming(val firstEventAt: Long, val lastEventAt: Long, val urgent: Boolean)

/** One flush worth of work, taken from [PendingChanges.drain]. */
data class Batch(
    val fullSync: Set<SearchCategory>,
    val fileIds: Set<Long>,
    val urgent: Boolean,
    /** Apps whose cached icon and label must be rendered again (part of a full app re-sync). */
    val changedPackages: Set<String> = emptySet(),
)

/**
 * How long to keep buffering before flushing [burst]: until events have been quiet for
 * [IndexUpdaterConfig.QUIET_PERIOD_MS], but never longer than [IndexUpdaterConfig.MAX_BATCH_DELAY_MS]
 * after the burst began (so a constant trickle of events still gets flushed). Both stretch by
 * [IndexUpdaterConfig.SLOW_MODE_DELAY_MULTIPLIER] under [Throttle.SLOW]. Urgent bursts flush now.
 */
fun flushDelayMillis(now: Long, burst: BurstTiming, throttle: Throttle): Long {
    if (burst.urgent) return 0
    val stretch = if (throttle == Throttle.SLOW) IndexUpdaterConfig.SLOW_MODE_DELAY_MULTIPLIER else 1L
    val quietDue = burst.lastEventAt + IndexUpdaterConfig.QUIET_PERIOD_MS * stretch
    val latestDue = burst.firstEventAt + IndexUpdaterConfig.MAX_BATCH_DELAY_MS * stretch
    return (minOf(quietDue, latestDue) - now).coerceAtLeast(0)
}
